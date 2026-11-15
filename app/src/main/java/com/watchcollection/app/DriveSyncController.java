package com.watchcollection.app;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.ClearTokenRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Google 계정 동의, 앱 사용 중 갱신, 마지막 성공 상태를 한곳에서 관리합니다. */
public final class DriveSyncController {
    private static final int REQUEST_AUTHORIZATION = 2301;
    private static final long AUTO_SYNC_INTERVAL_MS = 60_000L;
    private static final long REMOTE_REFRESH_INTERVAL_MS = 60L * 60L * 1000L;
    private static final String DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file";
    private static final String PREFS_NAME = "watch_drive_sync";

    private final Activity activity;
    private final Context appContext;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean resumed;
    private boolean busy;
    private volatile boolean closed;
    private boolean interactiveRequest;
    private String progress = "";
    private CollectionSnapshotExporter.Export pendingSnapshot;
    private AlertDialog dialog;
    private TextView statusView;
    private Button syncButton, documentButton, copyButton, folderButton, stopButton;

    // 자동 갱신은 사용자가 연결한 뒤, 앱 화면이 열려 있을 때에만 실행합니다.
    private final Runnable automaticCheck = new Runnable() {
        @Override public void run() {
            if (closed || !resumed) return;
            if (prefs.getBoolean("enabled", false) && prefs.getBoolean("auto_sync", true)) {
                requestSync(false);
            }
            main.postDelayed(this, AUTO_SYNC_INTERVAL_MS);
        }
    };

    public DriveSyncController(Activity activity) {
        this.activity = activity;
        this.appContext = activity.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void onResume() {
        resumed = true;
        main.removeCallbacks(automaticCheck);
        main.postDelayed(automaticCheck, 3_000L);
    }

    public void onPause() {
        resumed = false;
        main.removeCallbacks(automaticCheck);
    }

    public void close() {
        closed = true;
        resumed = false;
        main.removeCallbacksAndMessages(null);
        // 실행 중인 네트워크 처리는 제한시간 안에 끝나며, 각 요청도 인터럽트를 확인합니다.
        executor.shutdownNow();
        pendingSnapshot = null;
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    public void showDialog() {
        if (closed || activity.isFinishing()) return;
        LinearLayout body = UiKit.column(activity);
        body.setPadding(UiKit.dp(activity, 20), UiKit.dp(activity, 10),
                UiKit.dp(activity, 20), UiKit.dp(activity, 16));
        body.addView(UiKit.muted(activity,
                "앱의 컬렉션·Wish·스트랩·착용 기록을 내 Drive의 최신 문서로 갱신합니다. "
                + "시계와 스트랩의 저장된 사진도 함께 올립니다.", 13));
        body.addView(UiKit.spacer(activity, 14));
        statusView = UiKit.text(activity, "", 12, false);
        statusView.setTextIsSelectable(true);
        body.addView(statusView);
        body.addView(UiKit.spacer(activity, 12));

        CheckBox automatic = new CheckBox(activity);
        automatic.setText("앱 사용 중 자동 갱신 · 1분 간격");
        automatic.setTextSize(13);
        automatic.setChecked(prefs.getBoolean("auto_sync", true));
        automatic.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("auto_sync", checked).apply();
            if (checked && prefs.getBoolean("enabled", false)) requestSync(false);
        });
        body.addView(automatic);
        body.addView(UiKit.spacer(activity, 8));

        syncButton = UiKit.primaryButton(activity, "");
        syncButton.setOnClickListener(v -> requestSync(true));
        addButton(body, syncButton);
        documentButton = UiKit.secondaryButton(activity, "최신 문서 열기");
        documentButton.setOnClickListener(v -> openUrl(prefs.getString("drive_doc_url", "")));
        addButton(body, documentButton);
        copyButton = UiKit.secondaryButton(activity, "다른 채팅에 사용할 문구 복사");
        copyButton.setOnClickListener(v -> copyChatReference());
        addButton(body, copyButton);
        folderButton = UiKit.secondaryButton(activity, "Drive 폴더 · 사진 열기");
        folderButton.setOnClickListener(v -> openUrl(prefs.getString("drive_folder_url", "")));
        addButton(body, folderButton);
        stopButton = UiKit.secondaryButton(activity, "연동 중지");
        stopButton.setOnClickListener(v -> {
            prefs.edit().putBoolean("enabled", false).remove("last_error").apply();
            progress = "앱의 자동 갱신을 중지했습니다. 저장된 Drive 자료는 유지됩니다.";
            refreshDialog();
        });
        addButton(body, stopButton);

        body.addView(UiKit.spacer(activity, 10));
        body.addView(UiKit.muted(activity,
                "처음 연결할 때 Google Cloud에서 이 앱의 Android OAuth 설정이 필요합니다. "
                + "ChatGPT에서도 같은 Google 계정의 Drive를 연결한 뒤 문서 링크로 조회하세요.", 11));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);
        dialog = new AlertDialog.Builder(activity)
                .setTitle("Google Drive 연동")
                .setView(scroll)
                .setPositiveButton("닫기", null)
                .create();
        dialog.setOnDismissListener(d -> {
            statusView = null;
            syncButton = documentButton = copyButton = folderButton = stopButton = null;
        });
        dialog.show();
        refreshDialog();
    }

    private void addButton(LinearLayout body, Button button) {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.setMargins(0, UiKit.dp(activity, 7), 0, 0);
        body.addView(button, layout);
    }

    private void requestSync(boolean interactive) {
        if (closed || busy || (!interactive && !prefs.getBoolean("enabled", false))) return;
        busy = true;
        interactiveRequest = interactive;
        progress = "앱 데이터를 확인하는 중…";
        refreshDialog();
        executor.execute(() -> {
            try {
                CollectionSnapshotExporter.Export snapshot = CollectionSnapshotExporter.capture(appContext);
                // 원격 삭제·권한 철회도 발견할 수 있도록 내용이 같아도 한 시간마다 다시 확인합니다.
                long successAge = System.currentTimeMillis() - prefs.getLong("drive_last_success_at", 0L);
                boolean unchanged = !interactive
                        && snapshot.fingerprint.equals(prefs.getString("drive_last_success_hash", ""))
                        && !prefs.getString("drive_doc_url", "").isEmpty()
                        && prefs.getLong("drive_last_success_at", 0L) > 0L
                        && successAge >= 0L && successAge < REMOTE_REFRESH_INTERVAL_MS
                        && prefs.getString("last_error", "").isEmpty()
                        && !prefs.getBoolean("drive_needs_retry", false);
                main.post(() -> {
                    if (closed) return;
                    if (unchanged) {
                        finish("앱 데이터 변경 없음", false);
                        return;
                    }
                    pendingSnapshot = snapshot;
                    authorize();
                });
            } catch (Exception error) {
                main.post(() -> fail(error));
            }
        });
    }

    // 토큰은 메모리에서만 전달하며 DB, 백업 JSON, SharedPreferences에 저장하지 않습니다.
    private void authorize() {
        if (closed) return;
        progress = "Google Drive 연결을 확인하는 중…";
        refreshDialog();
        AuthorizationRequest.Builder builder = AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(DRIVE_FILE_SCOPE)));
        String email = prefs.getString("drive_account_email", "");
        if (prefs.getBoolean("enabled", false) && email != null && !email.isEmpty()) {
            builder.setAccount(new Account(email, "com.google"));
        }
        Identity.getAuthorizationClient(activity).authorize(builder.build())
                .addOnSuccessListener(result -> {
                    if (closed) return;
                    if (result.hasResolution()) {
                        if (!interactiveRequest) {
                            fail(new Exception("Google 권한 확인이 필요합니다. Drive 연동에서 지금 동기화를 눌러 주세요."));
                            return;
                        }
                        try {
                            if (result.getPendingIntent() == null) {
                                throw new Exception("Google 계정 동의 창을 열 수 없습니다.");
                            }
                            activity.startIntentSenderForResult(result.getPendingIntent().getIntentSender(),
                                    REQUEST_AUTHORIZATION, null, 0, 0, 0);
                        } catch (Exception error) {
                            fail(error);
                        }
                    } else {
                        useAuthorization(result);
                    }
                })
                .addOnFailureListener(this::fail);
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_AUTHORIZATION) return false;
        if (closed) return true;
        if (resultCode != Activity.RESULT_OK || data == null) {
            finish("Google Drive 연결이 취소되었습니다.", false);
            return true;
        }
        // 동의 창을 띄운 동안 화면이 재생성되었어도 결과를 처리할 수 있습니다.
        busy = true;
        interactiveRequest = true;
        try {
            useAuthorization(Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data));
        } catch (Exception error) {
            fail(error);
        }
        return true;
    }

    private void useAuthorization(AuthorizationResult result) {
        String token = result.getAccessToken();
        if (token == null || token.trim().isEmpty()) {
            fail(new Exception("Google Drive 접근 토큰을 받지 못했습니다. 다시 연결해 주세요."));
            return;
        }
        progress = "Drive 문서와 사진을 갱신하는 중…";
        refreshDialog();
        CollectionSnapshotExporter.Export captured = pendingSnapshot;
        pendingSnapshot = null;
        executor.execute(() -> {
            try {
                CollectionSnapshotExporter.Export snapshot = captured == null
                        ? CollectionSnapshotExporter.capture(appContext) : captured;
                GoogleDriveClient.SyncResult synced = new GoogleDriveClient(token).sync(snapshot, prefs);
                // 새 계정의 첫 업로드까지 성공해야 연결을 확정합니다.
                // 계정 조회 실패 시 이전 계정으로 자동 재연결되는 일을 막습니다.
                prefs.edit().putBoolean("enabled", true).remove("last_error")
                        .putString("last_warnings", String.join("\n", synced.warnings)).apply();
                main.post(() -> {
                    if (closed) return;
                    finish(synced.warnings.isEmpty()
                            ? "Drive 문서와 데이터를 갱신했습니다."
                            : "데이터를 갱신했습니다. 사진 관련 확인사항이 있습니다.", interactiveRequest);
                });
            } catch (Exception error) {
                if (error instanceof GoogleDriveClient.DriveException
                        && ((GoogleDriveClient.DriveException) error).statusCode == 401) {
                    // 만료된 캐시 토큰은 비우고, 다음 요청에서 새 권한 토큰을 받습니다.
                    main.post(() -> {
                        if (closed) return;
                        Identity.getAuthorizationClient(activity)
                                .clearToken(ClearTokenRequest.builder().setToken(token).build())
                                .addOnCompleteListener(ignored -> fail(new Exception(
                                        "Google 연결이 만료되었습니다. 지금 동기화를 다시 눌러 주세요.")));
                    });
                } else {
                    main.post(() -> fail(error));
                }
            }
        });
    }

    private void fail(Exception error) {
        if (closed) return;
        String message;
        if (error instanceof ApiException && ((ApiException) error).getStatusCode() == 10) {
            message = "Android OAuth 설정을 확인해 주세요. 패키지명 com.watchcollection.app과 "
                    + "이 APK 서명키의 SHA-1을 Google Cloud에 등록해야 합니다.";
        } else {
            message = error.getMessage();
            if (message == null || message.trim().isEmpty()) message = "연결 또는 업로드에 실패했습니다.";
        }
        if (message.length() > 400) message = message.substring(0, 400);
        prefs.edit().putString("last_error", message).apply();
        finish("이번 동기화에 실패했습니다.", interactiveRequest);
    }

    private void finish(String message, boolean notify) {
        if (closed) return;
        busy = false;
        pendingSnapshot = null;
        progress = message;
        refreshDialog();
        if (notify) Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    private void refreshDialog() {
        if (closed || statusView == null) return;
        boolean enabled = prefs.getBoolean("enabled", false);
        String email = prefs.getString("drive_account_email", "");
        long lastSuccess = prefs.getLong("drive_last_success_at", 0L);
        StringBuilder status = new StringBuilder(enabled ? "연동 사용 중" : "연동 꺼짐");
        if (!email.isEmpty()) status.append("\n계정: ").append(email);
        status.append("\n마지막 성공: ").append(lastSuccess == 0L ? "아직 없음"
                : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(new Date(lastSuccess)));
        if (!progress.isEmpty()) status.append("\n").append(progress);
        String error = prefs.getString("last_error", "");
        if (!error.isEmpty()) status.append("\n확인사항: ").append(error);
        String warnings = prefs.getString("last_warnings", "");
        if (!warnings.isEmpty()) status.append("\n사진 확인사항:\n").append(warnings);
        statusView.setText(status.toString());
        syncButton.setText(busy ? "동기화 중…" : enabled ? "지금 동기화" : "Google Drive 연결");
        syncButton.setEnabled(!busy);
        boolean hasDocument = lastSuccess > 0L && !prefs.getString("drive_doc_url", "").isEmpty();
        documentButton.setEnabled(hasDocument);
        copyButton.setEnabled(hasDocument);
        folderButton.setEnabled(lastSuccess > 0L && !prefs.getString("drive_folder_url", "").isEmpty());
        stopButton.setEnabled(enabled && !busy);
    }

    private void openUrl(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception error) {
            Toast.makeText(activity, "링크를 열 수 있는 앱을 확인해 주세요.", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyChatReference() {
        String url = prefs.getString("drive_doc_url", "");
        if (url.isEmpty()) return;
        String text = "Google Drive의 다음 문서를 읽고 최종 갱신 시각을 확인한 뒤, "
                + "내 시계 컬렉션·위시리스트·스트랩·착용 기록의 기준 자료로 사용해줘.\n" + url
                + "\n현재 보유, Wish, 방출 후 남은 착용 기록을 구분해줘. "
                + "시계 관련 질문에 답할 때에는 이 문서를 다시 조회해서 최신 자료인지 확인해줘. "
                + "스트랩 합성에 필요한 실제 사진은 문서의 사진 링크에서 접근되는지 먼저 확인해줘.";
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Watch Collection Drive", text));
            Toast.makeText(activity, "두 시계 채팅에 붙여넣을 문구를 복사했습니다.", Toast.LENGTH_SHORT).show();
        }
    }
}
