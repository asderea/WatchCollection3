package com.watchcollection.app;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 일반 내 드라이브에 논리 DB, 읽기용 Google 문서, 사진을 내보냅니다.
 * 인증과 화면 처리는 호출자가 담당하며 액세스 토큰은 이 객체의 메모리에만 둡니다.
 * 모든 공개 메서드는 네트워크를 사용하므로 작업 스레드에서 호출해야 합니다.
 */
public final class GoogleDriveClient {
    private static final String API = "https://www.googleapis.com/drive/v3";
    private static final String UPLOAD_API = "https://www.googleapis.com/upload/drive/v3/files";
    private static final String FOLDER_MIME = "application/vnd.google-apps.folder";
    private static final String DOC_MIME = "application/vnd.google-apps.document";
    private static final String APP_MARKER = "watch_collection_android";
    private static final String FILE_FIELDS =
            "id,name,mimeType,trashed,ownedByMe,webViewLink,parents,appProperties,md5Checksum,size";
    private static final long MAX_IMAGE_BYTES = 20L * 1024L * 1024L;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int MAX_ERROR_BYTES = 16 * 1024;
    private static final Object SYNC_LOCK = new Object();

    private final String accessToken;
    private final ConnectionFactory connections;
    private JSONObject account;

    /** 실제 HTTP 통신 없이 실패 순서와 저장 규칙을 검증할 때 사용하는 주입 지점입니다. */
    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    public GoogleDriveClient(String accessToken) {
        this(accessToken, url -> (HttpURLConnection) url.openConnection());
    }

    GoogleDriveClient(String accessToken, ConnectionFactory connections) {
        if (accessToken == null || accessToken.trim().isEmpty()
                || accessToken.indexOf('\n') >= 0 || accessToken.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Google Drive 인증 토큰이 없습니다.");
        }
        this.accessToken = accessToken.trim();
        this.connections = connections;
    }

    public static class DriveException extends IOException {
        public final int statusCode;
        public final String reason;

        public DriveException(int statusCode, String reason, String message) {
            super(message);
            this.statusCode = statusCode;
            this.reason = reason == null ? "" : reason;
        }
    }

    public static final class AuthException extends DriveException {
        public AuthException() {
            super(401, "unauthorized", "Google Drive 인증이 만료되었습니다. 다시 동기화해 주세요.");
        }
    }

    public static final class SyncResult {
        public final String docId;
        public final String docUrl;
        public final String folderId;
        public final String folderUrl;
        public final long uploadedAt;
        public final List<String> warnings;

        SyncResult(String docId, String folderId, long uploadedAt, List<String> warnings) {
            this.docId = docId;
            this.docUrl = documentUrl(docId);
            this.folderId = folderId;
            this.folderUrl = folderUrl(folderId);
            this.uploadedAt = uploadedAt;
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }
    }

    /** 별도 프로필 권한 없이 Drive가 제공하는 현재 사용자의 식별 정보를 읽습니다. */
    public JSONObject getAccount() throws Exception {
        if (account == null) {
            JSONObject response = requestJson("GET", API + "/about?fields="
                    + encode("user(emailAddress,permissionId)"), null);
            JSONObject user = response.optJSONObject("user");
            if (user == null || (user.optString("permissionId").isEmpty()
                    && user.optString("emailAddress").isEmpty())) {
                throw new IOException("연결한 Google Drive 계정을 확인하지 못했습니다.");
            }
            account = new JSONObject(user.toString());
        }
        return new JSONObject(account.toString());
    }

    /**
     * 사진 → JSON → Google 문서 순서로 저장합니다.
     * 최종 문서가 성공한 경우에만 마지막 성공 시각과 데이터 지문을 함께 확정합니다.
     */
    public SyncResult sync(CollectionSnapshotExporter.Export snapshot,
                           SharedPreferences prefs) throws Exception {
        if (snapshot == null || prefs == null) {
            throw new IllegalArgumentException("동기화할 데이터가 없습니다.");
        }
        // 다른 화면이나 클라이언트 인스턴스가 동일 파일에 교차 기록하지 않게 합니다.
        synchronized (SYNC_LOCK) {
            checkInterrupted();
            JSONObject user = getAccount();
            String identity = user.optString("permissionId");
            if (identity.isEmpty()) identity = user.optString("emailAddress").toLowerCase(Locale.ROOT);
            String accountKey = sha256(identity.getBytes(StandardCharsets.UTF_8));
            String prefix = "drive_cache_" + accountKey + "_";
            activateAccount(prefs, accountKey, user.optString("emailAddress"));

            String revision = UUID.randomUUID().toString();
            String preparedAt = Instant.now().toString();
            String rootId = ensureFolder(prefs, prefix, "root", "main", "Watch Collection", null);
            String imageFolderId = ensureFolder(prefs, prefix, "image_folder", "main", "images", rootId);
            Map<String, String> imageLinks = new LinkedHashMap<>();
            Map<String, JSONObject> uploadedImages = new HashMap<>();
            List<String> uploadWarnings = new ArrayList<>();
            JSONArray imageFiles = new JSONArray();
            boolean needsRetry = false;

            for (CollectionSnapshotExporter.ImageAsset asset : snapshot.images) {
                checkInterrupted();
                JSONObject manifest = new JSONObject();
                manifest.put("source_path", asset.sourcePath);
                manifest.put("sha256", asset.key);
                manifest.put("name", asset.name);
                manifest.put("mime_type", asset.mimeType);
                try {
                    // 스냅샷 이후 변경된 사진을 이전 내용으로 잘못 식별하지 않게 검증합니다.
                    byte[] bytes = readVerifiedPhoto(asset);
                    JSONObject photo = uploadedImages.get(asset.key);
                    if (photo == null) {
                        photo = uploadPhoto(prefs, prefix, asset, bytes, imageFolderId);
                        uploadedImages.put(asset.key, photo);
                    }
                    String fileId = requireId(photo);
                    String url = fileUrl(fileId);
                    imageLinks.put(asset.sourcePath, url);
                    manifest.put("status", "available");
                    manifest.put("file_id", fileId);
                    manifest.put("url", url);
                } catch (AuthException e) {
                    // 만료된 인증은 사진 누락 경고로 덮지 않고 전체 작업을 중단합니다.
                    throw e;
                } catch (InterruptedIOException e) {
                    throw e;
                } catch (PhotoSkippedException e) {
                    needsRetry |= e.retryable;
                    String warning = photoLabel(asset) + ": " + e.getMessage();
                    uploadWarnings.add(warning);
                    manifest.put("status", "unavailable");
                    manifest.put("warning", e.getMessage());
                } catch (Exception e) {
                    needsRetry |= retryable(e);
                    String warning = photoLabel(asset) + ": " + safeFailure(e);
                    uploadWarnings.add(warning);
                    manifest.put("status", "upload_failed");
                    manifest.put("warning", safeFailure(e));
                }
                imageFiles.put(manifest);
            }

            // 원본 스냅샷을 변경하지 않고 Drive 전용 매핑만 별도 사본에 추가합니다.
            JSONObject export = new JSONObject(snapshot.json.toString());
            export.put("image_files", imageFiles);
            JSONObject syncInfo = new JSONObject();
            syncInfo.put("revision", revision);
            syncInfo.put("prepared_at", preparedAt);
            syncInfo.put("fingerprint", snapshot.fingerprint);
            syncInfo.put("folder_url", folderUrl(rootId));
            syncInfo.put("direction", "app_to_drive");
            export.put("drive_sync", syncInfo);
            List<String> allWarnings = mergeWarnings(snapshot.warnings, uploadWarnings);
            export.put("export_warnings", new JSONArray(allWarnings));

            JSONObject jsonFile = uploadStableFile(prefs, prefix, "snapshot", "main",
                    "WatchCollection_latest.json", "application/json", "application/json",
                    export.toString(2).getBytes(StandardCharsets.UTF_8), rootId, revision);
            String jsonUrl = fileUrl(requireId(jsonFile));
            String document = snapshot.toDocument(imageLinks, uploadWarnings)
                    + "\n\nDrive 내보내기 정보\n"
                    + "작성 시각(UTC): " + preparedAt + "\n"
                    + "내보내기 리비전: " + revision + "\n"
                    + "데이터 지문: " + snapshot.fingerprint + "\n"
                    + "논리 DB JSON: " + jsonUrl + "\n"
                    + "JSON과 이 문서의 리비전이 같을 때 같은 내보내기입니다.\n"
                    + "이 문서는 앱에서 갱신합니다. 문서 편집 내용은 앱 DB에 반영되지 않습니다.\n";

            // Google 문서 내용은 마지막에 교체하여 앞 단계 오류로 기존 문서를 지우지 않습니다.
            JSONObject doc = uploadStableFile(prefs, prefix, "document", "main",
                    "Watch Collection · 최신 컬렉션", DOC_MIME, "text/plain",
                    document.getBytes(StandardCharsets.UTF_8), rootId, revision);
            String docId = requireId(doc);
            long uploadedAt = System.currentTimeMillis();
            boolean persisted = prefs.edit()
                    .putString("drive_doc_id", docId)
                    .putString("drive_doc_url", documentUrl(docId))
                    .putString("drive_folder_id", rootId)
                    .putString("drive_folder_url", folderUrl(rootId))
                    .putLong("drive_last_success_at", uploadedAt)
                    .putString("drive_last_success_revision", revision)
                    .putString("drive_last_success_hash", snapshot.fingerprint)
                    .putBoolean("drive_needs_retry", needsRetry)
                    .commit();
            if (!persisted) {
                throw new IOException("Drive 저장은 끝났지만 기기에 동기화 상태를 기록하지 못했습니다. 다시 확인해 주세요.");
            }
            return new SyncResult(docId, rootId, uploadedAt, allWarnings);
        }
    }

    private static void activateAccount(SharedPreferences prefs, String accountKey, String email)
            throws IOException {
        SharedPreferences.Editor edit = prefs.edit()
                .putString("drive_account_key", accountKey)
                .putString("drive_account_email", email);
        if (!accountKey.equals(prefs.getString("drive_account_key", ""))) {
            // 계정별 내부 ID 캐시는 남기되, 이전 계정의 성공 링크를 새 계정에 표시하지 않습니다.
            edit.remove("drive_doc_id").remove("drive_doc_url")
                    .remove("drive_folder_id").remove("drive_folder_url")
                    .remove("drive_last_success_at").remove("drive_last_success_revision")
                    .remove("drive_last_success_hash").remove("drive_needs_retry")
                    .remove("last_warnings");
        }
        if (!edit.commit()) throw new IOException("연결 계정을 기기에 기록하지 못했습니다.");
    }

    /** 앱이 생성한 파일 표식과 현재 부모 폴더를 모두 검사합니다. */
    private JSONObject findManagedFile(SharedPreferences prefs, String prefix, String kind,
                                       String key, String mimeType, String parentId) throws Exception {
        String preferenceKey = cacheKey(prefix, kind, key);
        String cachedId = prefs.getString(preferenceKey, "");
        if (!cachedId.isEmpty()) {
            JSONObject cached = metadataOrNull(cachedId);
            if (matches(cached, kind, key, mimeType, parentId)) return cached;
            prefs.edit().remove(preferenceKey).apply();
        }

        String query = "trashed = false and 'me' in owners and mimeType = '" + escapeQuery(mimeType)
                + "' and appProperties has { key='wc_app' and value='" + APP_MARKER + "' }"
                + " and appProperties has { key='wc_kind' and value='" + escapeQuery(kind) + "' }"
                + " and appProperties has { key='wc_key' and value='" + escapeQuery(key) + "' }";
        if (parentId != null) query += " and '" + escapeQuery(parentId) + "' in parents";
        String page = "";
        do {
            String url = API + "/files?spaces=drive&pageSize=100&orderBy="
                    + encode("createdTime desc") + "&q=" + encode(query)
                    + "&fields=" + encode("nextPageToken,files(" + FILE_FIELDS + ")");
            if (!page.isEmpty()) url += "&pageToken=" + encode(page);
            JSONObject response = requestJson("GET", url, null);
            JSONArray files = response.optJSONArray("files");
            if (files != null) {
                for (int i = 0; i < files.length(); i++) {
                    JSONObject candidate = files.optJSONObject(i);
                    if (matches(candidate, kind, key, mimeType, parentId)) {
                        prefs.edit().putString(preferenceKey, requireId(candidate)).apply();
                        return candidate;
                    }
                }
            }
            page = response.optString("nextPageToken");
        } while (!page.isEmpty());
        return null;
    }

    private JSONObject metadataOrNull(String id) throws Exception {
        if (!validId(id)) return null;
        try {
            JSONObject file = requestJson("GET", API + "/files/" + id
                    + "?fields=" + encode(FILE_FIELDS), null);
            return file.optBoolean("trashed") ? null : file;
        } catch (DriveException e) {
            if (e.statusCode == 404) return null;
            // 403은 삭제로 간주하지 않습니다. 권한 오류를 새 파일 생성으로 덮지 않습니다.
            throw e;
        }
    }

    private static boolean matches(JSONObject file, String kind, String key,
                                   String mimeType, String parentId) {
        if (file == null || file.optBoolean("trashed") || !file.optBoolean("ownedByMe")
                || !validId(file.optString("id")) || !mimeType.equals(file.optString("mimeType"))) {
            return false;
        }
        JSONObject properties = file.optJSONObject("appProperties");
        if (properties == null || !APP_MARKER.equals(properties.optString("wc_app"))
                || !kind.equals(properties.optString("wc_kind"))
                || !key.equals(properties.optString("wc_key"))) return false;
        if (parentId == null) return true;
        JSONArray parents = file.optJSONArray("parents");
        if (parents != null) {
            for (int i = 0; i < parents.length(); i++) {
                if (parentId.equals(parents.optString(i))) return true;
            }
        }
        return false;
    }

    private String ensureFolder(SharedPreferences prefs, String prefix, String kind, String key,
                                String name, String parentId) throws Exception {
        JSONObject existing = findManagedFile(prefs, prefix, kind, key, FOLDER_MIME, parentId);
        if (existing != null) return requireId(existing);
        JSONObject body = fileMetadata(kind, key, name, FOLDER_MIME, "", "");
        if (parentId != null) body.put("parents", new JSONArray().put(parentId));
        JSONObject created = requestJson("POST", API + "/files?fields=" + encode(FILE_FIELDS), body);
        String id = requireId(created);
        prefs.edit().putString(cacheKey(prefix, kind, key), id).apply();
        return id;
    }

    /** 같은 내용의 이미지는 Drive의 실제 MD5까지 비교한 뒤 재전송을 생략합니다. */
    private JSONObject uploadPhoto(SharedPreferences prefs, String prefix,
                                   CollectionSnapshotExporter.ImageAsset asset, byte[] bytes,
                                   String parentId) throws Exception {
        JSONObject existing = findManagedFile(prefs, prefix, "image", asset.key, asset.mimeType, parentId);
        String expectedMd5 = digest("MD5", bytes);
        if (existing != null && expectedMd5.equalsIgnoreCase(existing.optString("md5Checksum"))) {
            return existing;
        }
        // 해시가 달라지면 새 이미지가 되므로 이전 문서가 가리키던 사진을 유지합니다.
        String name = "image_" + asset.key + extension(asset.mimeType);
        return uploadStableFile(prefs, prefix, "image", asset.key, name, asset.mimeType,
                asset.mimeType, bytes, parentId, "");
    }

    private JSONObject uploadStableFile(SharedPreferences prefs, String prefix, String kind,
                                        String key, String name, String targetMime, String uploadMime,
                                        byte[] data, String parentId, String revision) throws Exception {
        String preferenceKey = cacheKey(prefix, kind, key);
        for (int attempt = 0; attempt < 2; attempt++) {
            JSONObject existing = findManagedFile(prefs, prefix, kind, key, targetMime, parentId);
            String fileId = existing == null ? null : requireId(existing);
            JSONObject body = fileMetadata(kind, key, name, targetMime, sha256(data), revision);
            if (fileId == null) body.put("parents", new JSONArray().put(parentId));
            try {
                JSONObject saved = uploadResumable(fileId, body, uploadMime, data);
                String savedId = requireId(saved);
                prefs.edit().putString(preferenceKey, savedId).apply();
                return saved;
            } catch (DriveException e) {
                // 조회 직후 사용자가 파일을 삭제한 경우 한 번 재탐색하여 다시 만듭니다.
                // 부모 삭제나 권한 오류는 전체 동기화를 실패시켜 다음 실행에서 복구합니다.
                if (e.statusCode == 404 && fileId != null && attempt == 0) {
                    prefs.edit().remove(preferenceKey).apply();
                    continue;
                }
                throw e;
            }
        }
        throw new IOException("Google Drive 파일을 저장하지 못했습니다.");
    }

    private static JSONObject fileMetadata(String kind, String key, String name,
                                           String mime, String hash, String revision) throws JSONException {
        JSONObject properties = new JSONObject();
        properties.put("wc_app", APP_MARKER);
        properties.put("wc_kind", kind);
        properties.put("wc_key", key);
        if (!hash.isEmpty()) properties.put("wc_hash", hash);
        if (!revision.isEmpty()) properties.put("wc_revision", revision);
        return new JSONObject().put("name", name).put("mimeType", mime).put("appProperties", properties);
    }

    /**
     * 최대 20 MiB 사진도 임시 업로드 세션에 보낸 뒤 완성된 파일만 사용합니다.
     * Google 문서 변환도 같은 경로로 처리하며 공개 공유 권한은 만들지 않습니다.
     */
    private JSONObject uploadResumable(String existingId, JSONObject metadata,
                                       String mime, byte[] data) throws Exception {
        String url = UPLOAD_API + (existingId == null ? "" : "/" + existingId)
                + "?uploadType=resumable&fields=" + encode(FILE_FIELDS);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json; charset=UTF-8");
        headers.put("X-Upload-Content-Type", mime);
        headers.put("X-Upload-Content-Length", String.valueOf(data.length));
        Response initiated = request(existingId == null ? "POST" : "PATCH", url,
                headers, metadata.toString().getBytes(StandardCharsets.UTF_8));
        String location = initiated.location;
        if (location == null || location.isEmpty()) {
            throw new IOException("Google Drive 업로드 위치를 받지 못했습니다.");
        }
        validateGoogleUrl(location);
        Map<String, String> uploadHeaders = new HashMap<>();
        uploadHeaders.put("Content-Type", mime);
        uploadHeaders.put("Content-Range", "bytes 0-" + (data.length - 1) + "/" + data.length);
        Response uploaded = request("PUT", location, uploadHeaders, data);
        return parseObject(uploaded.body);
    }

    private JSONObject requestJson(String method, String url, JSONObject body) throws Exception {
        Map<String, String> headers = new HashMap<>();
        byte[] bytes = null;
        if (body != null) {
            headers.put("Content-Type", "application/json; charset=UTF-8");
            bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        }
        return parseObject(request(method, url, headers, bytes).body);
    }

    private static final class Response {
        final String body;
        final String location;

        Response(String body, String location) {
            this.body = body;
            this.location = location;
        }
    }

    private Response request(String method, String url, Map<String, String> headers, byte[] data)
            throws IOException {
        checkInterrupted();
        URL parsedUrl = validateGoogleUrl(url);
        HttpURLConnection connection = null;
        try {
            connection = connections.open(parsedUrl);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(45000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            // 일부 Android HTTP 구현의 PATCH 제한을 Google 공식 override 방식으로 처리합니다.
            connection.setRequestMethod("PATCH".equals(method) ? "POST" : method);
            if ("PATCH".equals(method)) connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestProperty("Accept", "application/json");
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
            if (data != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream output = connection.getOutputStream()) {
                    for (int offset = 0; offset < data.length; offset += 32768) {
                        checkInterrupted();
                        output.write(data, offset, Math.min(32768, data.length - offset));
                    }
                }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                String error = readBounded(connection.getErrorStream(), MAX_ERROR_BYTES, false);
                throw apiFailure(status, error);
            }
            String body = readBounded(connection.getInputStream(), MAX_RESPONSE_BYTES, true);
            return new Response(body, connection.getHeaderField("Location"));
        } catch (DriveException e) {
            throw e;
        } catch (InterruptedIOException e) {
            if (Thread.currentThread().isInterrupted()) throw e;
            throw new DriveException(0, "networkTimeout", "Google Drive 응답 시간이 초과되었습니다. 다시 시도해 주세요.");
        } catch (IOException e) {
            // 네트워크 예외에 포함될 수 있는 URL·세션·토큰을 화면이나 로그에 전달하지 않습니다.
            throw new DriveException(0, "networkError", "Google Drive 통신에 실패했습니다. 연결 상태를 확인해 주세요.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static URL validateGoogleUrl(String address) throws IOException {
        URL url = new URL(address);
        String host = url.getHost();
        boolean trustedHost = "www.googleapis.com".equals(host) || "content.googleapis.com".equals(host);
        boolean trustedPath = url.getPath().startsWith("/drive/v3/")
                || url.getPath().startsWith("/upload/drive/v3/");
        if (!"https".equals(url.getProtocol()) || !trustedHost || !trustedPath
                || url.getUserInfo() != null || url.getRef() != null
                || (url.getPort() != -1 && url.getPort() != 443)) {
            throw new IOException("Google Drive 응답의 업로드 주소를 확인하지 못했습니다.");
        }
        return url;
    }

    private static String readBounded(InputStream input, int limit, boolean rejectOversize)
            throws IOException {
        if (input == null) return "";
        try (InputStream stream = input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int remaining = limit;
            while (remaining > 0) {
                checkInterrupted();
                int count = stream.read(buffer, 0, Math.min(buffer.length, remaining));
                if (count < 0) return bytes.toString(StandardCharsets.UTF_8.name());
                if (count == 0) continue;
                bytes.write(buffer, 0, count);
                remaining -= count;
            }
            if (rejectOversize && stream.read() != -1) {
                throw new IOException("Google Drive 응답이 허용 크기를 넘었습니다.");
            }
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** 오류 원문을 표시하지 않고 짧은 상태 코드와 알려진 사유만 사용합니다. */
    private static DriveException apiFailure(int status, String errorBody) {
        if (status == 401) return new AuthException();
        String reason = "";
        try {
            JSONObject error = new JSONObject(errorBody).optJSONObject("error");
            if (error != null) {
                JSONArray details = error.optJSONArray("errors");
                if (details != null && details.length() > 0 && details.optJSONObject(0) != null) {
                    reason = details.optJSONObject(0).optString("reason");
                }
                if (reason.isEmpty()) reason = error.optString("status");
            }
        } catch (JSONException ignored) {
            // 응답이 잘리거나 JSON이 아니어도 서버 원문을 로그로 남기지 않습니다.
        }
        // 서버의 임의 문자열(토큰 등이 섞일 수 있음)을 사유 코드로 노출하지 않습니다.
        switch (reason) {
            case "accessNotConfigured": case "SERVICE_DISABLED":
            case "storageQuotaExceeded": case "rateLimitExceeded":
            case "userRateLimitExceeded": case "dailyLimitExceeded":
            case "insufficientPermissions": case "insufficientFilePermissions":
            case "notFound": case "forbidden": case "authError":
            case "badRequest": case "invalid": case "backendError": case "internalError":
            case "PERMISSION_DENIED": case "UNAUTHENTICATED": case "NOT_FOUND":
            case "RESOURCE_EXHAUSTED": case "INVALID_ARGUMENT": case "INTERNAL":
            case "UNAVAILABLE":
                break;
            default:
                reason = "";
        }
        String detail;
        if ("accessNotConfigured".equals(reason) || "SERVICE_DISABLED".equals(reason)) {
            detail = "Google Cloud 프로젝트의 Drive API 사용 설정을 확인해 주세요.";
        } else if ("storageQuotaExceeded".equals(reason)) {
            detail = "Google Drive의 저장 공간이 부족합니다.";
        } else if (status == 404) {
            detail = "Google Drive 파일 또는 폴더를 찾지 못했습니다.";
        } else if (status == 403) {
            detail = "Google Drive 접근 권한 또는 사용 한도를 확인해 주세요.";
        } else if (status == 429 || status >= 500 || status == 308) {
            detail = "Google Drive 저장이 완료되지 않았습니다. 잠시 후 다시 시도해 주세요.";
        } else {
            detail = "Google Drive 요청을 완료하지 못했습니다.";
        }
        return new DriveException(status, reason, detail + " (HTTP " + status
                + (reason.isEmpty() ? "" : ", " + reason) + ")");
    }

    private static JSONObject parseObject(String body) throws IOException {
        try {
            return new JSONObject(body);
        } catch (JSONException e) {
            throw new IOException("Google Drive 응답 형식을 확인하지 못했습니다.");
        }
    }

    private static final class PhotoSkippedException extends IOException {
        final boolean retryable;

        PhotoSkippedException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }
    }

    /** 사진은 한 장씩 제한된 메모리에 고정해 파일 변경과 업로드 사이의 경합을 막습니다. */
    private static byte[] readVerifiedPhoto(CollectionSnapshotExporter.ImageAsset asset)
            throws IOException {
        File file = asset.file;
        if (file == null || !file.isFile()) {
            throw new PhotoSkippedException("기기의 사진 파일을 찾지 못했습니다.", false);
        }
        if (!supportedImageMime(asset.mimeType)) {
            throw new PhotoSkippedException("PNG, JPEG, WebP, GIF 사진만 내보낼 수 있습니다.", false);
        }
        long length = file.length();
        if (length > MAX_IMAGE_BYTES) {
            throw new PhotoSkippedException("사진이 20 MiB를 초과하여 제외했습니다.", false);
        }
        if (length <= 0) {
            throw new PhotoSkippedException("사진이 변경 중이거나 비어 있습니다. 다음 동기화에서 다시 확인합니다.", true);
        }
        byte[] bytes = new byte[(int) length];
        try (InputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                checkInterrupted();
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) throw new PhotoSkippedException("사진이 변경되어 이번 내보내기에서 제외했습니다.", true);
                if (count == 0) continue;
                offset += count;
            }
            if (input.read() != -1) {
                throw new PhotoSkippedException("사진이 변경되어 이번 내보내기에서 제외했습니다.", true);
            }
        }
        if (asset.key == null || !asset.key.equalsIgnoreCase(sha256(bytes))) {
            throw new PhotoSkippedException("사진이 변경되어 이번 내보내기에서 제외했습니다.", true);
        }
        return bytes;
    }

    private static boolean supportedImageMime(String mime) {
        return "image/png".equals(mime) || "image/jpeg".equals(mime)
                || "image/webp".equals(mime) || "image/gif".equals(mime);
    }

    private static String extension(String mime) {
        if ("image/png".equals(mime)) return ".png";
        if ("image/webp".equals(mime)) return ".webp";
        if ("image/gif".equals(mime)) return ".gif";
        return ".jpg";
    }

    private static boolean retryable(Exception e) {
        if (e instanceof DriveException) {
            DriveException error = (DriveException) e;
            return error.statusCode == 0 || error.statusCode == 408 || error.statusCode == 429
                    || error.statusCode == 308 || error.statusCode >= 500
                    || "rateLimitExceeded".equals(error.reason)
                    || "userRateLimitExceeded".equals(error.reason);
        }
        return e instanceof IOException;
    }

    private static String safeFailure(Exception e) {
        if (e instanceof DriveException) return e.getMessage();
        return "사진을 올리지 못했습니다. 다음 동기화에서 다시 확인해 주세요.";
    }

    private static String photoLabel(CollectionSnapshotExporter.ImageAsset asset) {
        String label = asset.label == null || asset.label.trim().isEmpty() ? asset.name : asset.label;
        if (label == null || label.trim().isEmpty()) return "사진";
        return label.length() > 160 ? label.substring(0, 160) : label;
    }

    private static List<String> mergeWarnings(List<String> first, List<String> second) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (first != null) result.addAll(first);
        if (second != null) result.addAll(second);
        return new ArrayList<>(result);
    }

    private static String sha256(byte[] bytes) throws IOException {
        return digest("SHA-256", bytes);
    }

    private static String digest(String algorithm, byte[] bytes) throws IOException {
        try {
            byte[] hash = MessageDigest.getInstance(algorithm).digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            String digits = "0123456789abcdef";
            for (byte value : hash) hex.append(digits.charAt((value & 255) >>> 4)).append(digits.charAt(value & 15));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("사진의 무결성 확인을 지원하지 않는 기기입니다.");
        }
    }

    private static String cacheKey(String prefix, String kind, String key) {
        return prefix + kind + "_" + key;
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,200}");
    }

    private static String requireId(JSONObject file) throws IOException {
        String id = file.optString("id");
        if (!validId(id)) throw new IOException("Google Drive 파일 ID를 확인하지 못했습니다.");
        return id;
    }

    private static String escapeQuery(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static String fileUrl(String id) {
        return "https://drive.google.com/file/d/" + id + "/view";
    }

    private static String documentUrl(String id) {
        return "https://docs.google.com/document/d/" + id + "/edit";
    }

    private static String folderUrl(String id) {
        return "https://drive.google.com/drive/folders/" + id;
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Google Drive 동기화가 중단되었습니다.");
        }
    }
}
