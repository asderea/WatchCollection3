package com.watchcollection.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 앱 DB를 한 시점의 JSON 자료와 Google 문서 본문으로 변환합니다.
 * 네트워크를 사용하지 않으며 API 키와 Google 인증 정보는 읽지 않습니다.
 */
public final class CollectionSnapshotExporter {
    public static final int EXPORT_VERSION = 1;
    public static final long MAX_IMAGE_BYTES = 20L * 1024L * 1024L;

    // 테이블 이름은 앱 코드가 관리하는 고정 목록으로만 제한합니다.
    private static final String[] TABLES = {
            "watches", "wish_watches", "straps", "wear_records", "accuracy_records",
            "timegrapher_records", "repair_records", "power_reserve_measurements",
            "collection_photos", "wear_photos", "heritage_entries", "heritage_photos",
            "forgotten_watch_exclusions", "wish_release_candidates", "collection_evaluations"
    };
    private static final String[] PROFILE_FIELDS = {
            "profile_version", "movement_preference", "preferred_colors", "disliked_colors",
            "collection_direction", "core_watches", "target_collection_size",
            "wishlist_priority", "long_term_plan", "metric_weights_percent"
    };

    private CollectionSnapshotExporter() {}

    public static final class ImageAsset {
        public final String key;
        public final String sourcePath;
        public final String mimeType;
        public final String name;
        public final String label;
        public final File file;

        private ImageAsset(String key, String sourcePath, String mimeType,
                           String name, String label, File file) {
            this.key = key;
            this.sourcePath = sourcePath;
            this.mimeType = mimeType;
            this.name = name;
            this.label = label;
            this.file = file;
        }
    }

    public static final class Export {
        public final JSONObject json;
        public final String fingerprint;
        public final List<ImageAsset> images;
        public final List<String> warnings;

        private Export(JSONObject json, String fingerprint, List<ImageAsset> images,
                       List<String> warnings) {
            this.json = json;
            this.fingerprint = fingerprint;
            this.images = Collections.unmodifiableList(new ArrayList<>(images));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }

        /** 업로드된 이미지 링크를 원래 파일 경로와 연결해 읽기용 본문을 만듭니다. */
        public String toDocument(Map<String, String> imageLinks, List<String> uploadWarnings) {
            Map<String, String> links = imageLinks == null ? Collections.emptyMap() : imageLinks;
            JSONObject tables = json.optJSONObject("tables");
            if (tables == null) tables = new JSONObject();
            JSONArray watches = rows(tables, "watches");
            JSONArray wishes = rows(tables, "wish_watches");
            JSONArray straps = rows(tables, "straps");
            JSONArray wears = rows(tables, "wear_records");
            Map<Long, JSONObject> owned = byId(watches);
            Set<Long> releaseCandidates = new LinkedHashSet<>();
            JSONArray candidates = rows(tables, "wish_release_candidates");
            for (int i = 0; i < candidates.length(); i++)
                releaseCandidates.add(candidates.optJSONObject(i).optLong("watch_id", -1));

            StringBuilder out = new StringBuilder(16384);
            out.append("Watch Collection · 최신 컬렉션\n\n");
            line(out, "앱 데이터 취득 시각", text(json, "exported_at"));
            line(out, "시간대", text(json, "timezone"));
            line(out, "앱 버전", shown(json, "app_version"));
            line(out, "데이터 지문", fingerprint);
            line(out, "DB 버전", String.valueOf(json.optInt("database_version")));
            out.append("현재 보유 ").append(watches.length()).append("점 / 위시 ")
                    .append(wishes.length()).append("점 / 스트랩 ").append(straps.length())
                    .append("개 / 착용 기록 ").append(wears.length()).append("건\n\n");
            out.append("자료 기준\n")
                    .append("• 현재 보유는 앱의 watches 목록만 기준으로 한다. 위시와 과거 착용 기록을 보유 시계에 합산하지 않는다.\n")
                    .append("• 현재 목록에 없는 시계의 과거 기록은 별도 보존된다. 방출·삭제의 구체적 이유는 DB에 기록되어 있지 않다.\n")
                    .append("• 평가 프로필의 핵심 시계·장기 계획은 선호 정보이며 현재 소유 여부를 입증하지 않는다.\n")
                    .append("• 미입력 값은 추정하지 않는다. 실제 사진은 아래 Drive 링크를 열어 확인해야 한다.\n\n");

            section(out, "1. 현재 보유 시계");
            if (watches.length() == 0) out.append("등록된 보유 시계가 없습니다.\n");
            for (int i = 0; i < watches.length(); i++) {
                JSONObject w = watches.optJSONObject(i);
                out.append("\n[").append(i + 1).append("] ").append(watchTitle(w))
                        .append(" · watch_id=").append(w.optLong("id")).append("\n");
                line(out, "상태", releaseCandidates.contains(w.optLong("id"))
                        ? "현재 보유 / 방출 후보로 지정됨(방출 완료 아님)" : "현재 보유");
                appendWatchSpecs(out, w);
                line(out, "구매일", shown(w, "purchase_date"));
                line(out, "구매 가격", shown(w, "purchase_price"));
                line(out, "보증 종료일", shown(w, "warranty_end_date"));
                line(out, "스펙 출처", shown(w, "source_urls"));
                line(out, "이미지 출처", shown(w, "image_source_url"));
                line(out, "메모", shown(w, "notes"));
                appendImage(out, "시계 원본 사진", text(w, "original_image_url"), links);
                appendImage(out, "시계 표시 사진", text(w, "representative_image_url"), links);
                appendImage(out, "케이스백 원본", text(w, "caseback_original_image_url"), links);
                appendImage(out, "케이스백 표시 사진", text(w, "caseback_image_url"), links);
            }

            section(out, "2. 위시리스트");
            if (wishes.length() == 0) out.append("등록된 위시 시계가 없습니다.\n");
            for (int i = 0; i < wishes.length(); i++) {
                JSONObject w = wishes.optJSONObject(i);
                out.append("\n[").append(i + 1).append("] ").append(watchTitle(w))
                        .append(" · wish_id=").append(w.optLong("id")).append("\n");
                line(out, "상태", "위시(현재 보유 아님)");
                appendWatchSpecs(out, w);
                line(out, "우선순위", shown(w, "priority"));
                line(out, "목표 가격", shown(w, "target_price"));
                line(out, "목표 구매일", shown(w, "target_purchase_date"));
                long replacement = w.optLong("replace_watch_id", -1);
                line(out, "교체 대상으로 연결한 보유 시계", replacement <= 0 ? "연결 없음"
                        : owned.containsKey(replacement) ? watchTitle(owned.get(replacement)) + " (watch_id=" + replacement + ")"
                        : "watch_id=" + replacement + " (현재 보유 목록에 없음)");
                line(out, "출처", shown(w, "source_url"));
                line(out, "메모", shown(w, "notes"));
                appendImage(out, "위시 원본 사진", text(w, "original_image_url"), links);
                appendImage(out, "위시 표시 사진", text(w, "representative_image_url"), links);
            }

            section(out, "3. 보유 스트랩");
            out.append("러그폭 일치는 스트랩 선택의 참고 조건이다. 곡선형 엔드링크·스프링바 등 실제 장착 적합성은 별도 확인한다.\n");
            if (straps.length() == 0) out.append("등록된 스트랩이 없습니다.\n");
            for (int i = 0; i < straps.length(); i++) {
                JSONObject s = straps.optJSONObject(i);
                out.append("\n[").append(i + 1).append("] ").append(shown(s, "name"))
                        .append(" · strap_id=").append(s.optLong("id")).append("\n");
                line(out, "제조사", shown(s, "maker"));
                line(out, "러그폭(mm)", shown(s, "width_mm"));
                line(out, "색상", shown(s, "color"));
                line(out, "소재", shown(s, "material"));
                line(out, "버클", shown(s, "buckle"));
                line(out, "구매 링크", shown(s, "purchase_url"));
                line(out, "메모", shown(s, "notes"));
                appendImage(out, "스트랩 사진", text(s, "image_path"), links);
            }

            section(out, "4. " + text(json, "ranking_month") + " 월간 착용 순위");
            out.append("집계는 해당 월에 저장된 wear_ratio의 합계이다. 하루에 두 시계를 기록한 경우 저장된 비율을 그대로 합산한다.\n")
                    .append("순서는 합계 내림차순, 기록에 저장된 시계명, 시계 ID 순이며 동률도 연속 순번으로 표시한다.\n");
            JSONArray ranking = json.optJSONArray("monthly_ranking");
            if (ranking == null || ranking.length() == 0) out.append("해당 월에 저장된 착용 기록이 없습니다.\n");
            else for (int i = 0; i < ranking.length(); i++) {
                JSONObject r = ranking.optJSONObject(i);
                long id = r.optLong("watch_id");
                out.append(i + 1).append("위 · ").append(text(r, "watch_name"))
                        .append(" · ").append(decimal(r.optDouble("score", 0))).append("회 환산")
                        .append(" · watch_id=").append(id).append(" · ")
                        .append(owned.containsKey(id) ? "현재 보유" : "현재 컬렉션에 없음(과거 기록)").append("\n");
            }

            section(out, "5. 전체 착용 기록");
            List<JSONObject> orderedWears = objects(wears);
            orderedWears.sort(Comparator.comparing((JSONObject w) -> text(w, "wear_date")).reversed()
                    .thenComparingInt(w -> w.optInt("slot", 1)).thenComparingLong(w -> w.optLong("id")));
            if (orderedWears.isEmpty()) out.append("저장된 착용 기록이 없습니다.\n");
            for (JSONObject w : orderedWears) {
                long id = w.optLong("watch_id");
                String name = text(w, "watch_name_snapshot");
                if (name.isEmpty()) name = owned.containsKey(id) ? watchTitle(owned.get(id)) : "이름 미저장 시계";
                out.append(text(w, "wear_date")).append(" · 슬롯 ").append(w.optInt("slot", 1))
                        .append(" · ").append(name).append(" · 비율 ")
                        .append(decimal(w.optDouble("wear_ratio", 1)))
                        .append(" · watch_id=").append(id).append(" / record_id=").append(w.optLong("id"))
                        .append(owned.containsKey(id) ? " · 현재 보유" : " · 현재 컬렉션에 없음");
                if (!text(w, "note").isEmpty()) out.append(" · 메모: ").append(text(w, "note"));
                out.append("\n");
            }

            section(out, "6. 앱에 저장된 평가 프로필");
            JSONObject profile = json.optJSONObject("profile");
            if (profile == null || profile.length() == 0) out.append("저장된 평가 프로필이 없습니다. 기본값을 대신 채우지 않았습니다.\n");
            else {
                String[][] fields = {{"작동 방식 선호", "movement_preference"}, {"선호 색상", "preferred_colors"},
                        {"비선호 색상", "disliked_colors"}, {"컬렉션 방향", "collection_direction"},
                        {"핵심 시계(선호 정보)", "core_watches"}, {"목표 보유 수", "target_collection_size"},
                        {"위시 우선순위(계획)", "wishlist_priority"}, {"장기 계획", "long_term_plan"}};
                for (String[] field : fields) line(out, field[0], shown(profile, field[1]));
            }

            section(out, "7. 추가 기록 및 사진 범위");
            out.append("JSON에는 아래 기록의 전체 행과 각 테이블의 스키마가 포함된다. JSON을 앱에 자동 복원하는 기능은 제공하지 않는다.\n");
            String[][] recordTypes = {{"오차 측정", "accuracy_records"}, {"타임그래퍼", "timegrapher_records"},
                    {"수리", "repair_records"}, {"파워리저브 측정", "power_reserve_measurements"},
                    {"컬렉션 사진", "collection_photos"}, {"착용 사진", "wear_photos"},
                    {"헤리티지", "heritage_entries"}, {"헤리티지 사진", "heritage_photos"},
                    {"장기 미착용 추천 제외", "forgotten_watch_exclusions"}, {"방출 후보", "wish_release_candidates"},
                    {"저장된 AI 평가", "collection_evaluations"}};
            for (String[] type : recordTypes)
                line(out, type[0], rows(tables, type[1]).length() + "건");
            out.append("사진 파일 업로드 범위는 현재 보유·위시 시계의 원본/표시 사진, 케이스백 사진, 스트랩 사진이다.\n")
                    .append("컬렉션 단체 사진·착용 사진·헤리티지 사진·캘린더 전용 썸네일은 경로 정보만 JSON에 기록된다. 전체 사진 백업이 아니다.\n")
                    .append("이미지 URL만 저장된 항목은 외부 링크로 남긴다. 휴대전화 내부 경로는 다른 기기나 ChatGPT에서 직접 열 수 없다.\n")
                    .append("동기화 대상 로컬 사진 ").append(images.size()).append("개 중 이번 문서에 연결한 사진 ")
                    .append(countLinkedImages(images, links)).append("개\n");

            Set<String> allWarnings = new LinkedHashSet<>(warnings);
            if (uploadWarnings != null) allWarnings.addAll(uploadWarnings);
            if (!allWarnings.isEmpty()) {
                section(out, "사진 및 내보내기 확인 사항");
                for (String warning : allWarnings) out.append("• ").append(warning).append("\n");
            }
            return out.toString();
        }
    }

    /** 별도 DB 연결의 짧은 트랜잭션에서 읽어 여러 테이블의 시점을 맞춥니다. */
    public static Export capture(Context context) throws Exception {
        Context app = context.getApplicationContext();
        ZonedDateTime capturedAt = ZonedDateTime.now(ZoneId.systemDefault());
        YearMonth month = YearMonth.from(capturedAt);
        JSONObject json = new JSONObject();
        JSONObject tables = new JSONObject();
        JSONArray schema = new JSONArray();
        JSONArray ranking = new JSONArray();
        List<String> warnings = new ArrayList<>();
        DatabaseHelper helper = new DatabaseHelper(app);
        try {
            SQLiteDatabase database = helper.getReadableDatabase();
            database.beginTransactionNonExclusive();
            try {
                for (String table : TABLES) {
                    String order = table.equals("collection_evaluations") ? "mode"
                            : table.equals("forgotten_watch_exclusions") || table.equals("wish_release_candidates") ? "watch_id" : "id";
                    tables.put(table, readRows(database, "SELECT * FROM " + table + " ORDER BY " + order, null));
                    JSONArray definitions = readRows(database,
                            "SELECT type,name,tbl_name,sql FROM sqlite_master WHERE tbl_name=? AND sql IS NOT NULL ORDER BY type,name",
                            new String[]{table});
                    for (int i = 0; i < definitions.length(); i++) schema.put(definitions.get(i));
                }
                json.put("database_version", database.getVersion());
                json.put("sqlite_sequence", readRows(database, "SELECT name,seq FROM sqlite_sequence ORDER BY name", null));
                List<DatabaseHelper.WearStat> stats = helper.getTopWorn(
                        month.atDay(1).toString(), month.atEndOfMonth().toString(), Integer.MAX_VALUE);
                stats.sort((a, b) -> {
                    int scoreOrder = Double.compare(b.score, a.score);
                    if (scoreOrder != 0) return scoreOrder;
                    int nameOrder = clean(a.watchName).compareTo(clean(b.watchName));
                    return nameOrder != 0 ? nameOrder : Long.compare(a.watchId, b.watchId);
                });
                for (DatabaseHelper.WearStat stat : stats) {
                    JSONObject row = new JSONObject();
                    row.put("rank", ranking.length() + 1);
                    row.put("watch_id", stat.watchId);
                    row.put("watch_name", clean(stat.watchName));
                    row.put("score", stat.score);
                    ranking.put(row);
                }
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        } finally {
            helper.close();
        }
        json.put("export_format", "watch-collection-logical-snapshot");
        json.put("export_version", EXPORT_VERSION);
        json.put("application_id", app.getPackageName());
        try {
            json.put("app_version", app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName);
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            json.put("app_version", JSONObject.NULL);
        }
        json.put("tables", tables);
        json.put("schema", schema);
        json.put("ranking_month", month.toString());
        json.put("monthly_ranking", ranking);
        json.put("timezone", capturedAt.getZone().getId());
        json.put("profile", readSavedProfile(app, json, warnings));
        json.put("photo_scope", "Current/wishlist watch originals and display images, casebacks, and strap images only. Other photo files are not uploaded; database paths alone are not a complete photo backup.");
        json.put("restore_supported", false);

        // 큰 사진 파일을 읽는 동안 DB 쓰기를 막지 않도록 트랜잭션 밖에서 처리합니다.
        List<ImageAsset> images = new ArrayList<>();
        JSONArray media = new JSONArray();
        collectImages(app, tables, images, media, warnings);
        json.put("media", media);
        json.put("warnings", new JSONArray(warnings));
        String fingerprint = sha256(canonical(json).getBytes(StandardCharsets.UTF_8));
        json.put("exported_at", capturedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        json.put("data_fingerprint", fingerprint);
        return new Export(json, fingerprint, images, warnings);
    }

    private static JSONArray readRows(SQLiteDatabase db, String sql, String[] args) throws Exception {
        JSONArray rows = new JSONArray();
        try (Cursor cursor = db.rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    Object value;
                    switch (cursor.getType(i)) {
                        case Cursor.FIELD_TYPE_NULL: value = JSONObject.NULL; break;
                        case Cursor.FIELD_TYPE_INTEGER: value = cursor.getLong(i); break;
                        case Cursor.FIELD_TYPE_FLOAT: value = cursor.getDouble(i); break;
                        case Cursor.FIELD_TYPE_BLOB:
                            value = new JSONObject().put("encoding", "base64")
                                    .put("data", Base64.encodeToString(cursor.getBlob(i), Base64.NO_WRAP));
                            break;
                        default: value = cursor.getString(i);
                    }
                    row.put(cursor.getColumnName(i), value);
                }
                rows.put(row);
            }
        }
        return rows;
    }

    /** 기본값을 채우지 않고 사용자의 기기에 저장된 평가 항목만 읽습니다. */
    private static JSONObject readSavedProfile(Context context, JSONObject metadata, List<String> warnings) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("collection_evaluation_profile", Context.MODE_PRIVATE);
        String raw = prefs.getString("profile_json", "");
        JSONObject profile = new JSONObject();
        if (raw == null || raw.trim().isEmpty()) {
            metadata.put("profile_status", "not_saved");
            return profile;
        }
        try {
            JSONObject saved = new JSONObject(raw);
            for (String field : PROFILE_FIELDS) if (saved.has(field)) profile.put(field, saved.get(field));
            metadata.put("profile_status", "saved");
        } catch (Exception error) {
            metadata.put("profile_status", "invalid");
            warnings.add("저장된 평가 프로필을 읽지 못해 해당 항목을 내보내지 않았습니다.");
        }
        return profile;
    }

    private static void collectImages(Context context, JSONObject tables, List<ImageAsset> images,
                                      JSONArray media, List<String> warnings) throws Exception {
        String[][] fields = {
                {"watches", "original_image_url", "시계 원본"}, {"watches", "representative_image_url", "시계 표시"},
                {"watches", "caseback_original_image_url", "케이스백 원본"}, {"watches", "caseback_image_url", "케이스백 표시"},
                {"wish_watches", "original_image_url", "위시 원본"}, {"wish_watches", "representative_image_url", "위시 표시"},
                {"straps", "image_path", "스트랩"}
        };
        Set<String> seenPaths = new LinkedHashSet<>();
        String privateRoot = context.getFilesDir().getCanonicalPath() + File.separator;
        for (String[] field : fields) {
            JSONArray tableRows = rows(tables, field[0]);
            for (int i = 0; i < tableRows.length(); i++) {
                JSONObject row = tableRows.optJSONObject(i);
                String path = text(row, field[1]);
                if (path.isEmpty() || !seenPaths.add(path)) continue;
                String label = field[2] + " · " + (field[0].equals("straps") ? shown(row, "name") : watchTitle(row))
                        + " (id=" + row.optLong("id") + ")";
                JSONObject item = new JSONObject().put("source_path", path).put("label", label);
                if (isWebUrl(path)) {
                    item.put("status", "external_url");
                    media.put(item);
                    continue;
                }
                try {
                    String filePath = path.startsWith("file://") ? Uri.parse(path).getPath() : path;
                    File file = filePath == null ? null : new File(filePath).getCanonicalFile();
                    if (file == null || !file.isAbsolute() || !file.getPath().startsWith(privateRoot))
                        throw new IllegalArgumentException("앱 내부 사진 파일이 아니어서 별도 업로드하지 않음");
                    if (!file.isFile() || !file.canRead()) throw new IllegalArgumentException("사진 파일을 찾거나 읽을 수 없음");
                    if (file.length() > MAX_IMAGE_BYTES) throw new IllegalArgumentException("사진이 20 MiB를 초과하여 업로드 대상에서 제외됨");
                    long originalSize = file.length();
                    long originalModified = file.lastModified();
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] header = new byte[12];
                    int headerLength;
                    try (FileInputStream input = new FileInputStream(file)) {
                        headerLength = input.read(header);
                        if (headerLength <= 0) throw new IllegalArgumentException("빈 사진 파일");
                        digest.update(header, 0, headerLength);
                        byte[] buffer = new byte[64 * 1024];
                        int length;
                        while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
                    }
                    String mime = imageMime(header, headerLength);
                    if (mime.isEmpty()) throw new IllegalArgumentException("지원하는 이미지 형식(PNG/JPEG/WebP/GIF)이 아님");
                    if (file.length() != originalSize || file.lastModified() != originalModified)
                        throw new IllegalArgumentException("사진 파일이 변경 중이므로 다음 동기화에서 다시 확인 필요");
                    String hash = hex(digest.digest());
                    String extension = mime.equals("image/jpeg") ? "jpg" : mime.substring("image/".length());
                    images.add(new ImageAsset(hash, path, mime, "image_" + hash + "." + extension, label, file));
                    item.put("status", "local_ready").put("sha256", hash).put("mime_type", mime).put("size_bytes", originalSize);
                } catch (Exception error) {
                    item.put("status", "unavailable");
                    String reason = error instanceof IllegalArgumentException ? error.getMessage() : "사진 파일을 읽지 못함";
                    item.put("reason", reason);
                    warnings.add(label + ": " + reason);
                }
                media.put(item);
            }
        }
    }

    private static String imageMime(byte[] h, int n) {
        if (n >= 8 && (h[0] & 255) == 137 && h[1] == 80 && h[2] == 78 && h[3] == 71
                && h[4] == 13 && h[5] == 10 && h[6] == 26 && h[7] == 10) return "image/png";
        if (n >= 3 && (h[0] & 255) == 255 && (h[1] & 255) == 216 && (h[2] & 255) == 255) return "image/jpeg";
        if (n >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8'
                && (h[4] == '7' || h[4] == '9') && h[5] == 'a') return "image/gif";
        if (n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "image/webp";
        return "";
    }

    private static void appendWatchSpecs(StringBuilder out, JSONObject w) {
        String[][] fields = {{"브랜드", "brand"}, {"모델", "model_name"}, {"레퍼런스", "reference_no"},
                {"작동 방식", "movement"}, {"칼리버", "caliber"}, {"지름(mm)", "diameter_mm"},
                {"러그 투 러그(mm)", "lug_to_lug_mm"}, {"두께(mm)", "thickness_mm"}, {"러그폭(mm)", "lug_width_mm"},
                {"파워리저브(시간)", "power_reserve_hours"}, {"방수", "water_resistance"},
                {"글라스", "crystal"}, {"케이스 소재", "case_material"}, {"메인 역할", "main_role"},
                {"보조 역할", "secondary_role"}};
        for (String[] field : fields) line(out, field[0], shown(w, field[1]));
    }

    private static void appendImage(StringBuilder out, String label, String path, Map<String, String> links) {
        if (path.isEmpty()) { line(out, label, "미등록"); return; }
        String link = links.get(path);
        if (link != null && !link.trim().isEmpty()) line(out, label, link);
        else if (isWebUrl(path)) line(out, label, path + " (외부 URL · 별도 파일 업로드 없음)");
        else line(out, label, "로컬 사진은 등록되어 있으나 이번 동기화에서 접근 가능한 Drive 링크를 만들지 못함");
    }

    private static int countLinkedImages(List<ImageAsset> images, Map<String, String> links) {
        int count = 0;
        for (ImageAsset image : images) if (links.containsKey(image.sourcePath)
                && links.get(image.sourcePath) != null && !links.get(image.sourcePath).trim().isEmpty()) count++;
        return count;
    }

    private static JSONArray rows(JSONObject tables, String name) {
        JSONArray value = tables.optJSONArray(name);
        return value == null ? new JSONArray() : value;
    }

    private static List<JSONObject> objects(JSONArray array) {
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) if (array.optJSONObject(i) != null) result.add(array.optJSONObject(i));
        return result;
    }

    private static Map<Long, JSONObject> byId(JSONArray array) {
        Map<Long, JSONObject> result = new LinkedHashMap<>();
        for (JSONObject row : objects(array)) result.put(row.optLong("id"), row);
        return result;
    }

    private static void line(StringBuilder out, String label, String value) {
        out.append(label).append(": ").append(value).append("\n");
    }

    private static void section(StringBuilder out, String title) {
        out.append("\n\n").append(title).append("\n");
    }

    private static String shown(JSONObject json, String name) {
        String value = text(json, name);
        return value.isEmpty() ? "미입력" : value;
    }

    private static String watchTitle(JSONObject watch) {
        String brand = text(watch, "brand");
        String model = text(watch, "model_name");
        return (brand + " " + (model.isEmpty() ? "모델명 미입력" : model)).trim();
    }

    private static String text(JSONObject object, String key) {
        Object value = object == null ? null : object.opt(key);
        return value == null || value == JSONObject.NULL ? "" : String.valueOf(value).trim();
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private static boolean isWebUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private static String decimal(double value) {
        if (value == Math.rint(value)) return String.format(Locale.ROOT, "%.0f", value);
        return Double.toString(value);
    }

    // JSONObject의 내부 키 순서에 의존하지 않고 같은 자료에 같은 지문을 부여합니다.
    private static String canonical(Object value) throws Exception {
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            List<String> keys = new ArrayList<>();
            Iterator<String> names = object.keys();
            while (names.hasNext()) keys.add(names.next());
            Collections.sort(keys);
            StringBuilder out = new StringBuilder("{");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) out.append(',');
                out.append(JSONObject.quote(keys.get(i))).append(':').append(canonical(object.get(keys.get(i))));
            }
            return out.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) out.append(',');
                out.append(canonical(array.get(i)));
            }
            return out.append(']').toString();
        }
        if (value instanceof Number) return JSONObject.numberToString((Number) value);
        if (value instanceof Boolean) return value.toString();
        return JSONObject.quote(String.valueOf(value));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
}
