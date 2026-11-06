package com.watchcollection.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private DatabaseHelper db;
    private FrameLayout content;
    private Button navCollection, navAccuracy, navCalendar, navStraps;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
    private final SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy-MM", Locale.KOREA);
    private Calendar visibleMonth = Calendar.getInstance();
    private String selectedCalendarDate = dateFormat.format(new Date());
    private int collectionFilter = 0; // 0 All, 1 Automatic, 2 Mechanical, 3 Quartz(+Solar-Quartz)
    private String collectionBrandFilter = ""; // 빈 문자열 = 전체 브랜드, __UNBRANDED__ = 브랜드 미지정
    private int monthlyWearPage = 0; // 컬렉션 규모와 관계없이 3개씩 페이지 표시
    private int wearRankingPeriod = 0; // 0 이번 달, 1 올해, 2 누적
    private int measurementMode = 0; // 0 오차 측정, 1 파워리저브 측정
    private int strapWidthFilter = 0; // 0 All, 18~22 mm, -1 Extra
    private String strapMaterialFilter = "All";
    private long focusedWatchId = -1;
    private Uri pendingStrapImageUri;
    private ImageView pendingStrapPreview;
    private TextView pendingStrapStatus;
    private Button pendingStrapReprocessButton;
    private boolean pendingStrapImageProcessing = false;
    private String pendingStrapOriginalPath = "";
    private String pendingStrapProcessedPath = "";
    private Uri pendingWatchImageUri;
    private ImageView pendingWatchPreview;
    private TextView pendingWatchPhotoStatus;
    private boolean pendingWatchImageClear = false;
    private boolean pendingWatchImageProcessing = false;
    private boolean pendingWatchRestoreOriginal = false;
    private String pendingWatchProcessedPath = "";
    private String pendingWatchOriginalPath = "";
    private String pendingWatchCalendarPath = "";

    private Uri pendingCasebackImageUri;
    private ImageView pendingCasebackPreview;
    private TextView pendingCasebackPhotoStatus;
    private boolean pendingCasebackImageClear = false;
    private boolean pendingCasebackImageProcessing = false;
    private boolean pendingCasebackRestoreOriginal = false;
    private String pendingCasebackProcessedPath = "";
    private String pendingCasebackOriginalPath = "";
    private long pendingDirectCasebackWatchId = -1;

    private Spinner accuracyWatchSpinner;
    private long accuracySelectedWatchId = -1;
    private boolean pendingWatchAccuracyMeterImport = false;
    private long pendingWatchAccuracyMeterWatchId = -1;
    private static final String WATCH_ACCURACY_METER_PACKAGE = "com.watchaccuracymeter.app";
    public static final String EXTRA_OPEN_TAB = "open_tab";
    private static final String EXTRA_SHARE_HANDLED = "com.watchcollection.app.share_handled";
    private SharedPageAnalyzer.Result pendingSharedWatchImport;

    private int collectionPhotoIndex = 0;
    private long pendingCollectionPhotoReplaceId = -1;

    private String pendingWearPhotoDate = "";
    private long pendingWearPhotoWatchId = -1;
    private String pendingWearPhotoWatchName = "";

    private final List<String> pendingHeritagePhotoPaths = new ArrayList<>();
    private LinearLayout pendingHeritagePhotoPreview;
    private boolean pendingHeritageSaved = false;
    private String heritageMonthFilter = "";

    private Uri pendingWishImageUri;
    private ImageView pendingWishPreview;
    private TextView pendingWishPhotoStatus;
    private boolean pendingWishImageProcessing = false;
    private boolean pendingWishImageClear = false;
    private String pendingWishProcessedPath = "";
    private String pendingWishOriginalPath = "";

    private boolean currentCollectionEvaluationRunning = false;
    private long currentCollectionEvaluationLastAttempt = 0L;
    private String currentCollectionEvaluationAttemptedSignature = "";

    private static final String AI_RECOMMENDATION_PREFS = "today_watch_recommendation";
    private static final String AI_REC_WATCH_ID = "watch_id";
    private static final String AI_REC_STRAP_ID = "strap_id";
    private static final String AI_REC_REASON = "reason";
    private static final String AI_REC_OUTFIT = "outfit";
    private static final String AI_REC_DATE = "date";

    private ApiCreditGaugeView apiCreditGauge;
    private AiBackendClient.CreditUsage apiCreditUsage;
    private String apiCreditUsageError = "";
    private boolean apiCreditUsageLoading = false;
    private long apiCreditLastRefreshMs = 0L;
    private static final long API_USAGE_REFRESH_INTERVAL_MS = 5L * 60L * 1000L;

    private static final int REQ_STRAP_IMAGE = 2201;
    private static final int REQ_WATCH_IMAGE = 2202;
    private static final int REQ_COLLECTION_IMAGE_ADD = 2203;
    private static final int REQ_COLLECTION_IMAGE_REPLACE = 2204;
    private static final int REQ_WEAR_PHOTOS = 2205;
    private static final int REQ_CASEBACK_IMAGE = 2206;
    private static final int REQ_CASEBACK_DIRECT = 2207;
    private static final int REQ_HERITAGE_PHOTOS = 2208;
    private static final int REQ_WISH_IMAGE = 2209;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DatabaseHelper(this);
        visibleMonth.set(Calendar.DAY_OF_MONTH, 1);
        buildShell();
        requestNotificationPermissionIfNeeded();
        WarrantyNotifier.scheduleNextCheck(this);
        ensureCalendarImagesAsync();
        if(!handleIncomingShare(getIntent())) openRequestedTab(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if(!handleIncomingShare(intent)) openRequestedTab(intent);
    }

    private void openRequestedTab(Intent intent) {
        String tab=intent==null?"":safeText(intent.getStringExtra(EXTRA_OPEN_TAB));
        if("calendar".equalsIgnoreCase(tab)) showCalendar();
        else if("accuracy".equalsIgnoreCase(tab)) showAccuracy();
        else if("straps".equalsIgnoreCase(tab)) showStraps(0);
        else showCollection();
    }

    private boolean handleIncomingShare(Intent intent) {
        if(intent==null || !Intent.ACTION_SEND.equals(intent.getAction())) return false;
        if(intent.getBooleanExtra(EXTRA_SHARE_HANDLED,false)) return false;
        String type=safeText(intent.getType());
        if(!type.isEmpty() && !type.startsWith("text/")) return false;

        CharSequence sharedSequence=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        CharSequence subjectSequence=intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
        String sharedText=sharedSequence==null?"":sharedSequence.toString();
        String subject=subjectSequence==null?"":subjectSequence.toString();
        String data=intent.getDataString()==null?"":intent.getDataString();
        List<String> clipParts=new ArrayList<>();
        ClipData clipData=intent.getClipData();
        if(clipData!=null) {
            for(int i=0;i<clipData.getItemCount();i++) {
                ClipData.Item item=clipData.getItemAt(i);
                if(item.getUri()!=null) clipParts.add(item.getUri().toString());
                CharSequence textPart=item.coerceToText(this);
                if(textPart!=null) clipParts.add(textPart.toString());
            }
        }
        String combined=joinNonEmpty("\n",subject,sharedText,data,String.join("\n",clipParts));
        String url=SharedPageAnalyzer.extractFirstUrl(combined);
        if(url.isEmpty()) return false;

        intent.putExtra(EXTRA_SHARE_HANDLED,true);
        showCollection();

        TextView progress=UiKit.muted(this,"공유 링크를 분석하는 중…",12);
        progress.setPadding(UiKit.dp(this,18),UiKit.dp(this,14),UiKit.dp(this,18),UiKit.dp(this,14));
        AlertDialog loading=new AlertDialog.Builder(this)
                .setTitle("공유 링크 가져오기")
                .setView(progress)
                .setNegativeButton("취소",null)
                .create();
        loading.setCanceledOnTouchOutside(false);
        loading.show();

        executor.execute(()->{
            try {
                SharedPageAnalyzer.Result page=SharedPageAnalyzer.analyze(url,combined,message->
                        runOnUiThread(()->progress.setText(message)));
                runOnUiThread(()->{
                    if(loading.isShowing()) loading.dismiss();
                    routeSharedPage(page);
                });
            } catch(Exception e) {
                runOnUiThread(()->{
                    if(loading.isShowing()) loading.dismiss();
                    showSharedTypeChooser(url,combined,null,"링크 분석 실패: "+e.getMessage());
                });
            }
        });
        return true;
    }

    private void routeSharedPage(SharedPageAnalyzer.Result page) {
        if(page==null) return;
        if(page.kind==SharedPageAnalyzer.Kind.STRAP) {
            showStraps(0);
            showStrapDialog(null,page);
        } else if(page.kind==SharedPageAnalyzer.Kind.WATCH) {
            pendingSharedWatchImport=page;
            showCollection();
            showWatchDialog(null);
        } else {
            showSharedTypeChooser(page.finalUrl,page.sharedTitle,page,
                    "이 링크가 시계인지 스트랩인지 자동 분류가 애매합니다.");
        }
    }

    private void showSharedTypeChooser(String url,String sharedText,SharedPageAnalyzer.Result page,String message) {
        new AlertDialog.Builder(this)
                .setTitle("어디에 추가할까요?")
                .setMessage(message)
                .setNegativeButton("취소",null)
                .setNeutralButton("스트랩에 추가",(d,x)->{
                    showStraps(0);
                    if(page!=null) showStrapDialog(null,page);
                    else showStrapDialog(null,url,sharedText,true);
                })
                .setPositiveButton("컬렉션에 시계 추가",(d,x)->{
                    SharedPageAnalyzer.Result target=page;
                    if(target==null) {
                        target=new SharedPageAnalyzer.Result();
                        target.finalUrl=url;
                        target.canonicalUrl=url;
                        target.sharedTitle=SharedPageAnalyzer.extractSharedTitleHint(sharedText);
                        target.productName=target.sharedTitle;
                    }
                    pendingSharedWatchImport=target;
                    showCollection();
                    showWatchDialog(null);
                })
                .show();
    }

    private void ensureCalendarImagesAsync() {
        executor.execute(() -> {
            boolean forceRegenerate = WatchHeadProcessor.needsRegeneration(this);
            boolean updated = false;
            List<Watch> watches = db.getWatches();
            for (Watch w : watches) {
                if (!forceRegenerate && !safeText(w.calendarImageUrl).trim().isEmpty()) continue;
                String sourceOriginal = safeText(w.originalImageUrl).trim();
                String sourceDisplay = safeText(w.representativeImageUrl).trim();
                if (sourceOriginal.isEmpty() && sourceDisplay.isEmpty()) continue;
                String generated = ImageLoader.ensureCalendarWatchImage(this, sourceOriginal, sourceDisplay);
                if (!safeText(generated).trim().isEmpty()) {
                    db.updateCalendarImagePath(w.id, generated);
                    updated = true;
                }
            }
            if (forceRegenerate) WatchHeadProcessor.markRegenerated(this);
            if (updated) runOnUiThread(() -> {
                CalendarWidgetProvider.updateAll(this);
                if (navCalendar != null && navCalendar.isSelected()) showCalendar();
            });
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (pendingWatchAccuracyMeterImport) {
            pendingWatchAccuracyMeterImport = false;
            final long watchId = pendingWatchAccuracyMeterWatchId;
            pendingWatchAccuracyMeterWatchId = -1;
            if (content != null) {
                content.postDelayed(() -> showWatchAccuracyMeterResultDialog(watchId), 350);
            }
        }
        refreshApiCreditUsage(false);
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        db.close();
        super.onDestroy();
    }

    private void buildShell() {
        getWindow().setStatusBarColor(UiKit.NAVY);
        getWindow().setNavigationBarColor(UiKit.NAVY);

        LinearLayout root = UiKit.column(this);
        root.setBackgroundColor(UiKit.CREAM);
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets b = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(0, b.top, 0, b.bottom);
                return insets;
            });
        }

        LinearLayout top = UiKit.row(this);
        top.setPadding(UiKit.dp(this,20),UiKit.dp(this,15),UiKit.dp(this,20),UiKit.dp(this,15));
        top.setBackgroundColor(UiKit.NAVY);
        LinearLayout titleBox = UiKit.column(this);
        TextView title = UiKit.text(this,"WATCH COLLECTION",20,true);
        title.setTextColor(Color.WHITE);
        title.setLetterSpacing(0.06f);
        TextView sub = UiKit.muted(this,"COLLECT · MEASURE · WEAR",10);
        sub.setTextColor(Color.rgb(190,197,207));
        sub.setLetterSpacing(0.12f);
        titleBox.addView(title);
        titleBox.addView(sub);
        top.addView(titleBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        apiCreditGauge = new ApiCreditGaugeView(this);
        apiCreditGauge.showUnconfigured();
        apiCreditGauge.setOnClickListener(v -> showApiCreditUsageDialog());
        LinearLayout.LayoutParams gaugeParams = new LinearLayout.LayoutParams(UiKit.dp(this,52),UiKit.dp(this,52));
        gaugeParams.setMargins(UiKit.dp(this,12),0,0,0);
        top.addView(apiCreditGauge,gaugeParams);
        root.addView(top);

        content = new FrameLayout(this);
        root.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        LinearLayout nav = UiKit.row(this);
        nav.setPadding(UiKit.dp(this,4),UiKit.dp(this,5),UiKit.dp(this,4),UiKit.dp(this,4));
        nav.setBackgroundColor(UiKit.PAPER);
        navCollection = navButton("컬렉션", R.drawable.ic_nav_collection);
        navAccuracy = navButton("측정", R.drawable.ic_nav_accuracy);
        navCalendar = navButton("캘린더", R.drawable.ic_nav_calendar);
        navStraps = navButton("스트랩", R.drawable.ic_nav_strap);
        nav.addView(navCollection,new LinearLayout.LayoutParams(0,UiKit.dp(this,66),1));
        nav.addView(navAccuracy,new LinearLayout.LayoutParams(0,UiKit.dp(this,66),1));
        nav.addView(navCalendar,new LinearLayout.LayoutParams(0,UiKit.dp(this,66),1));
        nav.addView(navStraps,new LinearLayout.LayoutParams(0,UiKit.dp(this,66),1));
        root.addView(nav);

        navCollection.setOnClickListener(v -> { focusedWatchId=-1; showCollection(); });
        navAccuracy.setOnClickListener(v -> { accuracySelectedWatchId=-1; showAccuracy(); });
        navCalendar.setOnClickListener(v -> showCalendar());
        navStraps.setOnClickListener(v -> { strapWidthFilter=0; strapMaterialFilter="All"; showStraps(0); });
        setContentView(root);
        refreshApiCreditUsage(false);
    }

    private void refreshApiCreditUsage(boolean force) {
        if(apiCreditGauge==null) return;

        if(!AiBackendClient.isConfigured(this)) {
            apiCreditUsage=null;
            apiCreditUsageError="";
            apiCreditUsageLoading=false;
            apiCreditGauge.showUnconfigured();
            return;
        }

        long now=System.currentTimeMillis();
        if(apiCreditUsageLoading) return;
        if(!force && apiCreditUsage!=null && now-apiCreditLastRefreshMs<API_USAGE_REFRESH_INTERVAL_MS) {
            renderApiCreditGauge(apiCreditUsage);
            return;
        }

        apiCreditUsageLoading=true;
        apiCreditGauge.showLoading();
        executor.execute(()->{
            try {
                AiBackendClient.CreditUsage usage=AiBackendClient.getCreditUsage(this,force);
                runOnUiThread(()->{
                    apiCreditUsage=usage;
                    apiCreditUsageError="";
                    apiCreditUsageLoading=false;
                    apiCreditLastRefreshMs=System.currentTimeMillis();
                    renderApiCreditGauge(usage);
                });
            } catch(Exception e) {
                runOnUiThread(()->{
                    apiCreditUsageError=e.getMessage()==null?"사용량을 조회하지 못했습니다.":e.getMessage();
                    apiCreditUsageLoading=false;
                    apiCreditLastRefreshMs=System.currentTimeMillis();
                    apiCreditGauge.showError();
                });
            }
        });
    }

    private void renderApiCreditGauge(AiBackendClient.CreditUsage usage) {
        if(apiCreditGauge==null) return;
        if(usage==null || !usage.configured) {
            apiCreditGauge.showUnconfigured();
            return;
        }
        boolean partiallyLimited=!usage.callsBlocked && (!usage.specSearchAllowed || !usage.recommendationAllowed);
        apiCreditGauge.showUsage(usage.remainingPercent,usage.callsBlocked,partiallyLimited);
    }

    private void showApiCreditUsageDialog() {
        if(!AiBackendClient.isConfigured(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("API 크레딧")
                    .setMessage("먼저 AI 서버 URL과 Watch App Token을 연결해야 합니다.")
                    .setNegativeButton("닫기",null)
                    .setPositiveButton("AI 연결 설정",(d,x)->showAiSettingsDialog(null))
                    .show();
            return;
        }

        if(apiCreditUsageLoading) {
            Toast.makeText(this,"API 크레딧을 조회하는 중입니다.",Toast.LENGTH_SHORT).show();
            return;
        }

        if(apiCreditUsage==null) {
            if(!apiCreditUsageError.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle("API 크레딧 조회 실패")
                        .setMessage(apiCreditUsageError)
                        .setNegativeButton("닫기",null)
                        .setPositiveButton("다시 조회",(d,x)->refreshApiCreditUsage(true))
                        .show();
            } else {
                refreshApiCreditUsage(true);
                Toast.makeText(this,"API 크레딧을 조회합니다.",Toast.LENGTH_SHORT).show();
            }
            return;
        }

        AiBackendClient.CreditUsage usage=apiCreditUsage;
        if(!usage.configured) {
            String message=usage.message.isEmpty()
                    ? "Cloudflare Worker의 크레딧 지급액·시작일·만료일·Admin API Key 설정이 필요합니다."
                    : usage.message;
            new AlertDialog.Builder(this)
                    .setTitle("API 크레딧 설정 필요")
                    .setMessage(message+"\n\nREADME_AI_SETUP_KO.md의 Worker 환경변수 설정 절차를 적용하세요.")
                    .setNegativeButton("닫기",null)
                    .setPositiveButton("다시 조회",(d,x)->refreshApiCreditUsage(true))
                    .show();
            return;
        }

        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,14));
        body.addView(UiKit.eyebrow(this,"ESTIMATED CREDIT"));

        TextView remaining=UiKit.text(this,formatUsd(usage.remainingUsd),30,true);
        remaining.setTextColor(usage.callsBlocked?UiKit.SUNDAY_RED:UiKit.GOLD);
        body.addView(remaining);

        TextView percent=UiKit.muted(this,
                String.format(Locale.KOREA,"전체 %s 중 %.1f%% 남음",formatUsd(usage.totalUsd),usage.remainingPercent),12);
        body.addView(percent);
        body.addView(UiKit.spacer(this,12));

        addApiUsageRow(body,"현재 상태",formatApiCreditStatus(usage));
        addApiUsageRow(body,"사용 비용",formatUsd(usage.usedUsd));
        addApiUsageRow(body,"지급 시작",formatApiTime(usage.startAt));
        addApiUsageRow(body,"만료",formatApiTime(usage.expiresAt)+" · "+usage.daysRemaining+"일 남음");
        addApiUsageRow(body,"스펙 정밀 검색",usage.specSearchAllowed?"사용 가능":"호출 차단");
        addApiUsageRow(body,"착장 추천",usage.recommendationAllowed?"사용 가능":"호출 차단");
        addApiUsageRow(body,"보호 기준",
                "공통 "+formatUsd(usage.reserveUsd)+" · 스펙 "+formatUsd(usage.specRequestReserveUsd)+" · 추천 "+formatUsd(usage.recommendRequestReserveUsd));
        addApiUsageRow(body,"비용 합산 범위",usage.projectFilterEnabled?"지정 프로젝트":"조직 전체");
        addApiUsageRow(body,"최종 조회",formatApiTime(usage.updatedAt));
        if(!usage.costDataEndAt.isEmpty()) addApiUsageRow(body,"비용 데이터 종료",formatApiTime(usage.costDataEndAt));

        body.addView(UiKit.spacer(this,10));
        TextView note=UiKit.muted(this,
                usage.note.isEmpty()
                        ? "Credit grants 실제 잔액을 직접 읽는 값이 아니라, 설정한 지급액에서 OpenAI Costs API 비용을 차감한 예상치입니다."
                        : usage.note,11);
        body.addView(note);

        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        new AlertDialog.Builder(this)
                .setTitle("API 크레딧")
                .setView(scroll)
                .setNegativeButton("AI 연결 설정",(d,x)->showAiSettingsDialog(null))
                .setNeutralButton("새로고침",(d,x)->refreshApiCreditUsage(true))
                .setPositiveButton("닫기",null)
                .show();
    }

    private void addApiUsageRow(LinearLayout body,String label,String value) {
        LinearLayout row=UiKit.row(this);
        row.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,4));
        TextView left=UiKit.muted(this,label,11);
        TextView right=UiKit.text(this,value,11,true);
        right.setGravity(Gravity.END);
        row.addView(left,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        row.addView(right,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        body.addView(row);
    }

    private String formatApiCreditStatus(AiBackendClient.CreditUsage usage) {
        if(!usage.guardEnabled) return "보호 기능 꺼짐";
        if("not_started".equals(usage.status)) return "지급 시작 전 · 호출 차단";
        if("expired".equals(usage.status)) return "지급 기간 만료 · 호출 차단";
        if("exhausted".equals(usage.status)) return "크레딧 소진 · 호출 차단";
        if(usage.callsBlocked) return "잔여량 부족 · 호출 차단";
        if(!usage.specSearchAllowed || !usage.recommendationAllowed) return "잔여량 낮음 · 일부 호출 제한";
        return "사용 가능 · 보호 기능 켜짐";
    }

    private String formatUsd(double value) {
        return String.format(Locale.US,"$%,.2f",Math.max(0.0,value));
    }

    private String formatApiTime(String value) {
        if(value==null || value.trim().isEmpty()) return "–";
        try {
            java.time.Instant instant=java.time.Instant.parse(value.trim());
            return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withLocale(Locale.KOREA)
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(instant);
        } catch(Exception ignored) {
            return value.replace("T"," ").replace("Z"," UTC");
        }
    }

    private Button navButton(String label, int iconRes) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(UiKit.MUTED);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setCompoundDrawablesWithIntrinsicBounds(0,iconRes,0,0);
        b.setCompoundDrawablePadding(UiKit.dp(this,3));
        b.setCompoundDrawableTintList(ColorStateList.valueOf(UiKit.MUTED));
        b.setPadding(UiKit.dp(this,2),UiKit.dp(this,3),UiKit.dp(this,2),0);
        return b;
    }

    private void selectNav(Button active) {
        Button[] bs = {navCollection,navAccuracy,navCalendar,navStraps};
        for (Button b : bs) {
            int color = b == active ? UiKit.NAVY : UiKit.MUTED;
            b.setTextColor(color);
            b.setCompoundDrawableTintList(ColorStateList.valueOf(color));
            b.setTypeface(null,b==active?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
        }
    }

    private LinearLayout page() {
        LinearLayout p=UiKit.column(this);
        p.setPadding(UiKit.dp(this,16),UiKit.dp(this,18),UiKit.dp(this,16),UiKit.dp(this,28));
        return p;
    }

    private void setScrollable(LinearLayout p) {
        ScrollView s=new ScrollView(this);
        s.setFillViewport(true);
        s.addView(p);
        content.removeAllViews();
        content.addView(s);
    }

    // COLLECTION -------------------------------------------------------------

    private void showCollection() {
        selectNav(navCollection);
        LinearLayout p=page();
        List<Watch> allWatches=db.getWatches();

        LinearLayout head=UiKit.row(this);
        LinearLayout hb=UiKit.column(this);
        hb.addView(UiKit.eyebrow(this,"MY COLLECTION"));
        hb.addView(UiKit.text(this,"내 시계",27,true));
        head.addView(hb,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        LinearLayout headActions=UiKit.row(this);
        Button wish=UiKit.secondaryButton(this,"Wish");
        Button heritage=UiKit.secondaryButton(this,"헤리티지");
        Button add=new Button(this);
        add.setText("+");
        add.setTextColor(Color.WHITE);
        add.setTextSize(22);
        add.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        add.setAllCaps(false);
        add.setMinWidth(0);
        add.setMinimumWidth(0);
        add.setMinHeight(0);
        add.setMinimumHeight(0);
        add.setPadding(0,0,0,UiKit.dp(this,2));
        add.setBackground(UiKit.rounded(UiKit.NAVY,Color.TRANSPARENT,22,this));
        wish.setTextSize(12);
        heritage.setTextSize(12);
        wish.setPadding(UiKit.dp(this,8),UiKit.dp(this,6),UiKit.dp(this,8),UiKit.dp(this,6));
        heritage.setPadding(UiKit.dp(this,8),UiKit.dp(this,6),UiKit.dp(this,8),UiKit.dp(this,6));
        wish.setOnClickListener(v->showWishlist());
        heritage.setOnClickListener(v->showHeritage());
        add.setOnClickListener(v->showWatchDialog(null));
        LinearLayout.LayoutParams smallActionLp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        smallActionLp.setMargins(0,0,UiKit.dp(this,5),0);
        headActions.addView(wish,smallActionLp);
        LinearLayout.LayoutParams heritageLp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        heritageLp.setMargins(0,0,UiKit.dp(this,6),0);
        headActions.addView(heritage,heritageLp);
        headActions.addView(add,new LinearLayout.LayoutParams(UiKit.dp(this,40),UiKit.dp(this,40)));
        head.addView(headActions);
        p.addView(head);
        p.addView(UiKit.spacer(this,14));
        p.addView(buildCollectionPhotosSection());

        if(!allWatches.isEmpty()) {
            p.addView(collectionStats(allWatches));
            p.addView(monthlyWearSummary());
            p.addView(collectionFilterRow(allWatches));
            p.addView(UiKit.spacer(this,10));
        }

        if(allWatches.isEmpty()) {
            p.addView(emptyCard("첫 시계를 등록해보세요","모델명과 레퍼런스를 입력하면 공식 페이지와 시계 전문 출처를 교차검증해 스펙만 정리합니다. 대표 사진은 직접 선택할 수 있습니다."));
        } else {
            List<Watch> ordered = new ArrayList<>(allWatches);
            Map<Long,Double> allTimeScores = db.getAllTimeWearScores();
            ordered.sort((a,b)->{
                double sa=allTimeScores.containsKey(a.id)?allTimeScores.get(a.id):0.0;
                double sb=allTimeScores.containsKey(b.id)?allTimeScores.get(b.id):0.0;
                int byWear=Double.compare(sb,sa);
                if(byWear!=0) return byWear;
                int byBrand=safeText(a.brand).compareToIgnoreCase(safeText(b.brand));
                if(byBrand!=0) return byBrand;
                return safeText(a.modelName).compareToIgnoreCase(safeText(b.modelName));
            });
            if (focusedWatchId > 0) {
                Watch target = null;
                for (Watch w : ordered) if (w.id == focusedWatchId) { target=w; break; }
                if (target != null) { ordered.remove(target); ordered.add(0,target); }
            }
            int shown=0;
            for(Watch w:ordered) {
                if(matchesCollectionFilter(w) && matchesBrandFilter(w)) {
                    p.addView(watchCard(w, w.id==focusedWatchId));
                    shown++;
                }
            }
            if(shown==0) p.addView(emptyCard("해당 분류의 시계가 없습니다","Power 정보를 확인하거나 필터를 변경해 주세요."));

            p.addView(UiKit.spacer(this,4));
            LinearLayout managementActions=UiKit.row(this);
            Button management=UiKit.primaryButton(this,"시계 관리");
            Button totalPrice=UiKit.secondaryButton(this,"구매가격 총합");
            management.setOnClickListener(v->showWatchManagementPopup());
            totalPrice.setOnClickListener(v->showPurchasePricePopup());
            managementActions.addView(management,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            managementActions.addView(totalPrice,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            p.addView(managementActions);
        }
        setScrollable(p);
        if(!allWatches.isEmpty()) maybeEvaluateCurrentCollection(allWatches);
    }

    private void navigateToWatch(long watchId) {
        focusedWatchId = watchId;
        collectionFilter = 0;
        collectionBrandFilter = "";
        showCollection();
    }

    private View collectionStats(List<Watch> watches) {
        LinearLayout card=UiKit.card(this);
        card.setBackground(UiKit.rounded(UiKit.NAVY,Color.TRANSPARENT,18,this));
        TextView title=UiKit.text(this,"COLLECTION AT A GLANCE",11,true);
        title.setTextColor(UiKit.GOLD);
        card.addView(title);
        card.addView(UiKit.spacer(this,10));
        LinearLayout row=UiKit.row(this);
        row.addView(statBox(String.valueOf(watches.size()),"보유 시계"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        row.addView(statBox(String.valueOf(db.getStraps(0).size()),"보유 스트랩"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

        String signature=getCurrentCollectionSignature(watches);
        DatabaseHelper.CollectionEvaluationRow evaluation=db.getCollectionEvaluation("current");
        boolean valid=evaluation!=null&&signature.equals(evaluation.signature);
        String scoreValue=valid?evaluation.score+"/100":currentCollectionEvaluationRunning?"…":"—";
        View scoreBox=statBox(scoreValue,"AI 컬렉션 점수");
        scoreBox.setClickable(true);
        scoreBox.setOnClickListener(v->{
            DatabaseHelper.CollectionEvaluationRow latest=db.getCollectionEvaluation("current");
            if(latest!=null&&signature.equals(latest.signature)) showCollectionEvaluationDialog(latest,"현재 컬렉션 평가",false);
            else runCurrentCollectionEvaluation(watches,signature,true);
        });
        row.addView(scoreBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        card.addView(row);

        Map<Long,Double> scores=db.getAllTimeWearScores();
        Watch mostWatch=null;
        double mostScore=0.0;
        for(Map.Entry<Long,Double> e:scores.entrySet()) {
            if(e.getValue()!=null && e.getValue()>0) {
                mostWatch=db.getWatch(e.getKey());
                mostScore=e.getValue();
                break;
            }
        }
        final Watch topWatch=mostWatch;
        String mostLabel=topWatch==null?"가장 많이 착용한 시계 · –":"가장 많이 착용한 시계 · "+topWatch.modelName+" · "+formatWearScore(mostScore);
        TextView most=UiKit.muted(this,mostLabel,11);
        most.setTextColor(Color.rgb(205,211,219));
        most.setPadding(0,UiKit.dp(this,10),0,0);
        if(topWatch!=null) {
            most.setClickable(true);
            most.setOnClickListener(v->showWatchDetailPopup(topWatch));
        }
        card.addView(most);
        return card;
    }

    private String getCurrentCollectionSignature(List<Watch> watches) {
        List<Long> ids=new ArrayList<>();
        for(Watch watch:watches) ids.add(watch.id);
        Collections.sort(ids);
        List<String> values=new ArrayList<>();
        for(Long id:ids) values.add(String.valueOf(id));
        return String.join(",",values);
    }

    private void maybeEvaluateCurrentCollection(List<Watch> watches) {
        String signature=getCurrentCollectionSignature(watches);
        DatabaseHelper.CollectionEvaluationRow saved=db.getCollectionEvaluation("current");
        if(saved!=null&&signature.equals(saved.signature)) return;
        if(currentCollectionEvaluationRunning) return;
        if(signature.equals(currentCollectionEvaluationAttemptedSignature)) return;
        if(!AiBackendClient.isConfigured(this)) return;
        currentCollectionEvaluationAttemptedSignature=signature;
        runCurrentCollectionEvaluation(new ArrayList<>(watches),signature,false);
    }

    private void runCurrentCollectionEvaluation(List<Watch> watches,String signature,boolean userInitiated) {
        if(watches==null||watches.isEmpty()) return;
        if(currentCollectionEvaluationRunning) {
            Toast.makeText(this,"컬렉션 평가가 진행 중입니다.",Toast.LENGTH_SHORT).show();
            return;
        }
        if(!AiBackendClient.isConfigured(this)) {
            showAiSettingsDialog(()->runCurrentCollectionEvaluation(watches,signature,userInitiated));
            return;
        }
        currentCollectionEvaluationRunning=true;
        currentCollectionEvaluationLastAttempt=System.currentTimeMillis();
        currentCollectionEvaluationAttemptedSignature=signature;
        final AlertDialog loading;
        final TextView progress;
        if(userInitiated) {
            progress=UiKit.muted(this,"컬렉션 구성을 정리합니다.",12);
            progress.setPadding(UiKit.dp(this,18),UiKit.dp(this,14),UiKit.dp(this,18),UiKit.dp(this,14));
            loading=new AlertDialog.Builder(this)
                    .setTitle("AI 컬렉션 평가")
                    .setView(progress)
                    .setNegativeButton("취소",null)
                    .create();
            loading.setCanceledOnTouchOutside(false);
            loading.show();
        } else {
            progress=null;
            loading=null;
        }
        executor.execute(()->{
            try {
                AiBackendClient.CollectionEvaluationResult result=AiBackendClient.evaluateCollection(
                        this,"current",buildCurrentCollectionEvaluationPayload(watches),new JSONArray(),new JSONArray(),
                        message->{if(progress!=null)runOnUiThread(()->progress.setText(message));});
                String metrics=serializeEvaluationMetrics(result.metrics);
                db.saveCollectionEvaluation("current",result.overallScore,result.summary,metrics,signature);
                runOnUiThread(()->{
                    currentCollectionEvaluationRunning=false;
                    if(loading!=null&&loading.isShowing()) loading.dismiss();
                    showCollection();
                    if(userInitiated) {
                        DatabaseHelper.CollectionEvaluationRow saved=db.getCollectionEvaluation("current");
                        if(saved!=null) showCollectionEvaluationDialog(saved,"현재 컬렉션 평가",false);
                    }
                });
            } catch(Exception e) {
                runOnUiThread(()->{
                    currentCollectionEvaluationRunning=false;
                    if(loading!=null&&loading.isShowing()) loading.dismiss();
                    if(userInitiated) new AlertDialog.Builder(this)
                            .setTitle("컬렉션 평가 실패")
                            .setMessage(e.getMessage())
                            .setNegativeButton("닫기",null)
                            .setPositiveButton("AI 설정",(d,x)->showAiSettingsDialog(null))
                            .show();
                });
            } finally {
                runOnUiThread(()->refreshApiCreditUsage(true));
            }
        });
    }

    private JSONArray buildCurrentCollectionEvaluationPayload(List<Watch> watches) throws Exception {
        JSONArray out=new JSONArray();
        List<Strap> straps=db.getStraps(0);
        for(Watch watch:watches) {
            JSONObject item=buildEvaluationWatchJson(watch);
            JSONArray compatible=new JSONArray();
            int lug=parseLugWidth(watch.lugWidthMm);
            for(Strap strap:straps) {
                if(lug<=0||strap.widthMm!=lug) continue;
                JSONObject strapJson=new JSONObject();
                strapJson.put("name",strap.name);
                strapJson.put("maker",strap.maker);
                strapJson.put("material",strap.material);
                strapJson.put("color",strap.color);
                compatible.put(strapJson);
            }
            item.put("compatible_straps",compatible);
            out.put(item);
        }
        return out;
    }

    private JSONObject buildEvaluationWatchJson(Watch watch) throws Exception {
        JSONObject item=new JSONObject();
        item.put("id",watch.id);
        item.put("brand",watch.brand);
        item.put("model",watch.modelName);
        item.put("reference",watch.referenceNo);
        item.put("power",normalizePower(watch.movement));
        item.put("movement",watch.caliber);
        item.put("diameter_mm",watch.diameterMm);
        item.put("lug_to_lug_mm",watch.lugToLugMm);
        item.put("thickness_mm",watch.thicknessMm);
        item.put("lug_width_mm",watch.lugWidthMm);
        item.put("power_reserve_hours",watch.powerReserveHours);
        item.put("water_resistance",watch.waterResistance);
        item.put("case_material",watch.caseMaterial);
        item.put("notes",watch.notes);
        return item;
    }

    private String serializeEvaluationMetrics(List<AiBackendClient.EvaluationMetric> metrics) throws Exception {
        JSONArray array=new JSONArray();
        for(AiBackendClient.EvaluationMetric metric:metrics) {
            JSONObject item=new JSONObject();
            item.put("name",metric.name);
            item.put("score",metric.score);
            item.put("reason",metric.reason);
            array.put(item);
        }
        return array.toString();
    }

    private void showCollectionEvaluationDialog(DatabaseHelper.CollectionEvaluationRow evaluation,
                                                String dialogTitle,boolean wishMode) {
        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,14));
        TextView score=UiKit.text(this,evaluation.score+"/100",32,true);
        score.setTextColor(UiKit.GOLD);
        body.addView(score);
        if(!evaluation.summary.isEmpty()) {
            body.addView(UiKit.text(this,evaluation.summary,14,true));
            body.addView(UiKit.spacer(this,10));
        }
        try {
            JSONArray metrics=new JSONArray(evaluation.metricsJson);
            for(int i=0;i<metrics.length();i++) {
                JSONObject metric=metrics.optJSONObject(i);
                if(metric==null) continue;
                LinearLayout card=UiKit.card(this);
                LinearLayout head=UiKit.row(this);
                head.addView(UiKit.text(this,metric.optString("name","평가 지표"),15,true),
                        new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
                TextView metricScore=UiKit.text(this,metric.optInt("score",0)+"/100",15,true);
                metricScore.setTextColor(UiKit.GOLD);
                head.addView(metricScore);
                card.addView(head);
                String reason=metric.optString("reason","");
                if(!reason.isEmpty()) {
                    card.addView(UiKit.spacer(this,5));
                    card.addView(UiKit.muted(this,reason,11));
                }
                body.addView(card);
            }
        } catch(Exception e) {
            body.addView(UiKit.muted(this,"세부 평가 결과를 읽지 못했습니다.",11));
        }
        body.addView(UiKit.muted(this,"평가 시각 · "+formatMeasurementTime(evaluation.evaluatedAt),10));
        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        new AlertDialog.Builder(this)
                .setTitle(dialogTitle)
                .setView(scroll)
                .setPositiveButton("닫기",null)
                .show();
    }

    private View monthlyWearSummary() {
        String start=currentMonthStart(), end=currentMonthEnd();
        Map<Long,Double> counts=db.getWearScores(start,end);
        LinearLayout card=UiKit.card(this);
        card.addView(UiKit.eyebrow(this,"MONTHLY WEAR"));
        card.addView(UiKit.text(this,monthFormat.format(new Date())+" 착용 순위",17,true));
        card.addView(UiKit.spacer(this,8));
        if(counts.isEmpty()) {
            monthlyWearPage=0;
            card.addView(UiKit.muted(this,"이번 달 착용 기록이 아직 없습니다.",12));
            return card;
        }

        List<Map.Entry<Long,Double>> entries=new ArrayList<>(counts.entrySet());
        int totalPages=Math.max(1,(entries.size()+2)/3);
        if(monthlyWearPage>=totalPages) monthlyWearPage=totalPages-1;
        if(monthlyWearPage<0) monthlyWearPage=0;

        LinearLayout pager=UiKit.row(this);
        Button prev=UiKit.secondaryButton(this,"‹");
        Button next=UiKit.secondaryButton(this,"›");
        prev.setEnabled(monthlyWearPage>0);
        next.setEnabled(monthlyWearPage<totalPages-1);
        prev.setOnClickListener(v->{if(monthlyWearPage>0){monthlyWearPage--;showCollection();}});
        next.setOnClickListener(v->{if(monthlyWearPage<totalPages-1){monthlyWearPage++;showCollection();}});
        int from=monthlyWearPage*3;
        int to=Math.min(from+3,entries.size());
        String pageLabel=to>from?(from+1)+"–"+to+"위":"—";
        TextView page=UiKit.text(this,pageLabel,13,true);
        page.setGravity(Gravity.CENTER);
        pager.addView(prev,new LinearLayout.LayoutParams(UiKit.dp(this,54),ViewGroup.LayoutParams.WRAP_CONTENT));
        pager.addView(page,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        pager.addView(next,new LinearLayout.LayoutParams(UiKit.dp(this,54),ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(pager);
        card.addView(UiKit.spacer(this,6));

        for(int i=from;i<to;i++) {
            Map.Entry<Long,Double> e=entries.get(i);
            Watch w=db.getWatch(e.getKey());
            if(w==null) continue;
            LinearLayout row=UiKit.row(this);
            TextView rank=UiKit.text(this,String.valueOf(i+1),12,true);
            rank.setTextColor(UiKit.GOLD);
            rank.setGravity(Gravity.CENTER);
            row.addView(rank,new LinearLayout.LayoutParams(UiKit.dp(this,28),ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView name=UiKit.text(this,(w.brand.isEmpty()?"":w.brand+" · ")+w.modelName,12,true);
            row.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            TextView c=UiKit.text(this,formatWearScore(e.getValue()),12,true);
            c.setTextColor(UiKit.GOLD);
            row.addView(c);
            row.setPadding(0,UiKit.dp(this,5),0,UiKit.dp(this,5));
            row.setOnClickListener(v->showWatchDetailPopup(w));
            card.addView(row);
        }
        return card;
    }

    private View collectionFilterRow(List<Watch> watches) {
        LinearLayout row=UiKit.row(this);

        LinearLayout powerBox=UiKit.column(this);
        powerBox.addView(UiKit.label(this,"Power"));
        Spinner power=new Spinner(this);
        String[] powerItems={"All","Automatic","Mechanical","Quartz"};
        power.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,powerItems));
        decorateSpinner(power);
        power.setSelection(Math.max(0,Math.min(collectionFilter,powerItems.length-1)));
        powerBox.addView(power);
        row.addView(powerBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

        LinearLayout brandBox=UiKit.column(this);
        brandBox.setPadding(UiKit.dp(this,8),0,0,0);
        brandBox.addView(UiKit.label(this,"Brand"));
        Spinner brand=new Spinner(this);
        List<String> brandItems=new ArrayList<>();
        brandItems.add("All");
        List<String> brands=new ArrayList<>();
        boolean hasUnbranded=false;
        for(Watch w:watches) {
            String b=safeText(w.brand).trim();
            if(b.isEmpty()) { hasUnbranded=true; continue; }
            boolean exists=false;
            for(String x:brands) if(x.equalsIgnoreCase(b)){exists=true;break;}
            if(!exists) brands.add(b);
        }
        brands.sort(String.CASE_INSENSITIVE_ORDER);
        brandItems.addAll(brands);
        if(hasUnbranded) brandItems.add("미지정");
        brand.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,brandItems));
        decorateSpinner(brand);
        int brandSelection=0;
        if("__UNBRANDED__".equals(collectionBrandFilter)) brandSelection=brandItems.indexOf("미지정");
        else if(collectionBrandFilter!=null&&!collectionBrandFilter.isEmpty()) {
            for(int i=1;i<brandItems.size();i++) if(brandItems.get(i).equalsIgnoreCase(collectionBrandFilter)){brandSelection=i;break;}
        }
        brand.setSelection(Math.max(0,brandSelection));
        brandBox.addView(brand);
        row.addView(brandBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

        power.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                if(position!=collectionFilter) {
                    collectionFilter=position;
                    showCollection();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        brand.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                String value=String.valueOf(parent.getItemAtPosition(position));
                String selected="All".equals(value)?"":("미지정".equals(value)?"__UNBRANDED__":value);
                if(!safeText(collectionBrandFilter).equalsIgnoreCase(selected)) {
                    collectionBrandFilter=selected;
                    showCollection();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        return row;
    }

    private boolean matchesBrandFilter(Watch w) {
        if(collectionBrandFilter==null||collectionBrandFilter.isEmpty()) return true;
        String brand=safeText(w.brand).trim();
        if("__UNBRANDED__".equals(collectionBrandFilter)) return brand.isEmpty();
        return brand.equalsIgnoreCase(collectionBrandFilter);
    }

    private boolean matchesCollectionFilter(Watch w) {
        if(collectionFilter==0) return true;
        String power=normalizePower(w.movement);
        if(collectionFilter==1) return "Automatic".equals(power);
        if(collectionFilter==2) return "Mechanical".equals(power);
        return "Quartz".equals(power)||"Solar-Quartz".equals(power);
    }

    private View statBox(String value,String label) {
        LinearLayout b=UiKit.column(this);
        b.setGravity(Gravity.CENTER);
        TextView v=UiKit.text(this,value,23,true); v.setTextColor(Color.WHITE);
        TextView l=UiKit.muted(this,label,11); l.setTextColor(Color.rgb(197,203,212));
        b.addView(v); b.addView(l);
        return b;
    }

    private View emptyCard(String title,String desc) {
        LinearLayout c=UiKit.card(this);
        c.addView(UiKit.text(this,title,18,true));
        c.addView(UiKit.spacer(this,5));
        c.addView(UiKit.muted(this,desc,13));
        return c;
    }

    private View watchCard(Watch w, boolean focused) {
        LinearLayout card=UiKit.card(this);
        if (focused) card.setBackground(UiKit.rounded(UiKit.PAPER,UiKit.GOLD,18,this));

        LinearLayout body=UiKit.row(this);
        ImageView image=new ImageView(this);
        image.setClipToOutline(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,15,this));
        image.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        ImageLoader.loadFit(this,image,w.representativeImageUrl);
        bindCollectionImageFlip(image,w);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(UiKit.dp(this,116),UiKit.dp(this,132));
        ip.setMargins(0,0,UiKit.dp(this,14),0);
        body.addView(image,ip);

        LinearLayout info=UiKit.column(this);
        info.addView(UiKit.eyebrow(this,w.brand.isEmpty()?"WATCH":w.brand));
        info.addView(UiKit.text(this,w.modelName,18,true));
        if(!w.referenceNo.isEmpty()) info.addView(UiKit.muted(this,"Ref. "+w.referenceNo,11));
        info.addView(UiKit.spacer(this,7));
        View specView=buildWatchSummarySpecs(w);
        if(specView!=null) info.addView(specView);

        double avg=db.averageTimegrapherRateForWatch(w.id);
        if(!Double.isNaN(avg)) {
            TextView a=UiKit.text(this,String.format(Locale.KOREA,"평균 %+.2f s/day",avg),12,true);
            a.setTextColor(UiKit.GOLD);
            info.addView(UiKit.spacer(this,7));
            info.addView(a);
        }

        double yearScore=db.getWearScoreForWatch(w.id,currentYearStart(),currentYearEnd());
        double monthScore=db.getWearScoreForWatch(w.id,currentMonthStart(),currentMonthEnd());
        TextView wear=UiKit.text(this,"올해 "+formatWearScore(yearScore)+" · 이번 달 "+formatWearScore(monthScore),12,true);
        wear.setTextColor(UiKit.NAVY_2);
        info.addView(UiKit.spacer(this,7));
        info.addView(wear);
        info.addView(UiKit.muted(this,"전체 "+formatWearScore(db.getAllTimeWearScoreForWatch(w.id)),11));

        if(!w.lugWidthMm.isEmpty()) {
            try {
                float lugWidth=Float.parseFloat(w.lugWidthMm);
                int width=Math.round(lugWidth);
                if(Math.abs(lugWidth-width)<0.1f) {
                    int matches=db.getStraps(width).size();
                    if(matches>0) info.addView(UiKit.muted(this,"호환 스트랩 "+matches+"개",11));
                }
            } catch(Exception ignored) {}
        }
        info.setOnClickListener(v->showWatchDetailPopup(w));
        body.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        card.addView(body);
        return card;
    }

    private View buildWatchSummarySpecs(Watch w) {
        LinearLayout box=UiKit.column(this);
        int shown=0;
        String power=normalizePower(w.movement);
        if(!power.isEmpty()) { box.addView(boldValueLine("Power", power, 12)); shown++; }

        String movement=w.caliber.isEmpty()?"":w.caliber;
        String reserve=w.powerReserveHours.isEmpty()?"":w.powerReserveHours+" h";
        if(!movement.isEmpty()) {
            box.addView(boldValueLine("Movement",movement,12));
            shown++;
        }
        if(!reserve.isEmpty()) {
            box.addView(boldValueLine("Power Reserve",reserve,12));
            shown++;
        }

        String diameter=w.diameterMm.isEmpty()?"":w.diameterMm+" mm";
        String l2l=w.lugToLugMm.isEmpty()?"":w.lugToLugMm+" mm";
        if(!diameter.isEmpty()||!l2l.isEmpty()) {
            box.addView(specPairRow("Φ",diameter,"L2L",l2l));
            shown++;
        }

        String lug=w.lugWidthMm.isEmpty()?"":w.lugWidthMm+" mm";
        String wr="";
        if(!w.waterResistance.isEmpty()) {
            String raw=w.waterResistance.trim();
            wr=raw.toUpperCase(Locale.ROOT).startsWith("WR")?raw.substring(Math.min(2,raw.length())).trim():raw;
        }
        if(!lug.isEmpty()||!wr.isEmpty()) {
            box.addView(specPairRow("Lug Width",lug,"WR",wr));
            shown++;
        }
        return shown==0?null:box;
    }

    private View specPairRow(String label1,String value1,String label2,String value2) {
        LinearLayout row=UiKit.row(this);
        if(value1!=null&&!value1.isEmpty()) {
            TextView left=boldValueLine(label1,value1,11);
            left.setSingleLine(true);
            row.addView(left,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        } else {
            row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        }
        if(value2!=null&&!value2.isEmpty()) {
            TextView right=boldValueLine(label2,value2,11);
            right.setSingleLine(true);
            right.setGravity(Gravity.END);
            row.addView(right,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        } else {
            row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        }
        return row;
    }

    private void confirmDeleteWatch(Watch w) {
        new AlertDialog.Builder(this)
                .setTitle("시계 삭제")
                .setMessage(w.modelName+"을 삭제할까요? 오차/수리 기록은 삭제되지만 착용 캘린더 로그·사진과 헤리티지는 유지됩니다.")
                .setNegativeButton("취소",null)
                .setPositiveButton("삭제",(d,x)->{db.deleteWatch(w.id);focusedWatchId=-1;showCollection();})
                .show();
    }

    // WISHLIST --------------------------------------------------------------

    private void showWishlist() {
        selectNav(navCollection);
        LinearLayout p=page();

        LinearLayout head=UiKit.row(this);
        LinearLayout titles=UiKit.column(this);
        titles.addView(UiKit.eyebrow(this,"WISHLIST"));
        titles.addView(UiKit.text(this,"위시리스트",27,true));
        head.addView(titles,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button back=UiKit.secondaryButton(this,"내 컬렉션");
        back.setTextSize(12);
        back.setOnClickListener(v->showCollection());
        Button add=new Button(this);
        add.setText("+");
        add.setTextColor(Color.WHITE);
        add.setTextSize(22);
        add.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        add.setAllCaps(false);
        add.setMinWidth(0);
        add.setMinimumWidth(0);
        add.setMinHeight(0);
        add.setMinimumHeight(0);
        add.setPadding(0,0,0,UiKit.dp(this,2));
        add.setBackground(UiKit.rounded(UiKit.NAVY,Color.TRANSPARENT,22,this));
        LinearLayout.LayoutParams backLp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        backLp.setMargins(0,0,UiKit.dp(this,7),0);
        head.addView(back,backLp);
        head.addView(add,new LinearLayout.LayoutParams(UiKit.dp(this,40),UiKit.dp(this,40)));
        add.setOnClickListener(v->showWishWatchDialog(null));
        p.addView(head);
        p.addView(UiKit.spacer(this,14));

        List<WishWatch> wishes=db.getWishWatches();
        if(wishes.isEmpty()) {
            p.addView(emptyCard("위시 시계를 추가하세요","목표 모델, 구매 우선순위와 현재 컬렉션에서 대체할 시계를 함께 관리할 수 있습니다."));
        } else {
            for(WishWatch wish:wishes) p.addView(wishWatchCard(wish));
        }

        p.addView(buildWishReleaseCandidateCard());

        String signature=getWishCollectionSignature();
        DatabaseHelper.CollectionEvaluationRow evaluation=db.getCollectionEvaluation("wish");
        boolean valid=evaluation!=null&&signature.equals(evaluation.signature);
        Button evaluate=UiKit.primaryButton(this,valid?"Wish 컬렉션 평가 · "+evaluation.score+"/100":"Wish 컬렉션 평가");
        evaluate.setOnClickListener(v->{
            DatabaseHelper.CollectionEvaluationRow latest=db.getCollectionEvaluation("wish");
            if(latest!=null&&signature.equals(latest.signature)) {
                showCollectionEvaluationDialog(latest,"Wish 컬렉션 평가",true);
            } else {
                runWishCollectionEvaluation(signature);
            }
        });
        p.addView(evaluate);
        setScrollable(p);
    }

    private View wishWatchCard(WishWatch wish) {
        LinearLayout card=UiKit.card(this);
        LinearLayout row=UiKit.row(this);
        ImageView image=new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,13,this));
        image.setClipToOutline(true);
        image.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        ImageLoader.loadFit(this,image,wish.representativeImageUrl);
        LinearLayout.LayoutParams imageLp=new LinearLayout.LayoutParams(UiKit.dp(this,92),UiKit.dp(this,104));
        imageLp.setMargins(0,0,UiKit.dp(this,12),0);
        row.addView(image,imageLp);

        LinearLayout info=UiKit.column(this);
        info.addView(UiKit.eyebrow(this,wish.priority.isEmpty()?"WISH":wish.priority));
        TextView name=UiKit.text(this,(wish.brand.isEmpty()?"":wish.brand+" · ")+wish.modelName,17,true);
        name.setMaxLines(2);
        info.addView(name);
        if(!wish.referenceNo.isEmpty()) info.addView(UiKit.muted(this,"Ref. "+wish.referenceNo,10));
        if(!wish.targetPrice.isEmpty()) {
            TextView price=UiKit.text(this,"목표 "+formatWon(wish.targetPrice),12,true);
            price.setTextColor(UiKit.GOLD);
            info.addView(price);
        }
        if(!wish.targetPurchaseDate.isEmpty()) info.addView(UiKit.muted(this,"목표 구매 · "+wish.targetPurchaseDate,10));
        Watch replacement=wish.replaceWatchId>0?db.getWatch(wish.replaceWatchId):null;
        String replacementLabel=replacement==null?"대체 시계 미지정":"대체 · "+replacement.modelName;
        info.addView(UiKit.muted(this,replacementLabel,10));
        row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        card.addView(row);
        card.setOnClickListener(v->showWishWatchDetail(wish.id));
        return card;
    }

    private void showWishWatchDialog(WishWatch existing) {
        boolean editing=existing!=null;
        WishWatch base=editing?existing:new WishWatch();
        pendingWishImageUri=null;
        pendingWishImageProcessing=false;
        pendingWishImageClear=false;
        pendingWishProcessedPath="";
        pendingWishOriginalPath="";

        LinearLayout form=UiKit.column(this);
        form.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,10));

        ImageView preview=new ImageView(this);
        preview.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,16,this));
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setClipToOutline(true);
        preview.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        pendingWishPreview=preview;
        ImageLoader.loadFit(this,preview,base.representativeImageUrl);
        form.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,190)));
        LinearLayout imageActions=UiKit.row(this);
        Button pick=UiKit.secondaryButton(this,"사진 선택");
        Button clear=UiKit.secondaryButton(this,"사진 제거");
        pick.setOnClickListener(v->openWishImagePicker());
        clear.setOnClickListener(v->{
            pendingWishImageClear=true;
            pendingWishProcessedPath="";
            pendingWishOriginalPath="";
            preview.setImageDrawable(null);
            if(pendingWishPhotoStatus!=null) pendingWishPhotoStatus.setText("사진을 제거합니다.");
        });
        imageActions.addView(pick,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        imageActions.addView(clear,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        form.addView(imageActions);
        pendingWishPhotoStatus=UiKit.muted(this,"",10);
        form.addView(pendingWishPhotoStatus);
        form.addView(UiKit.spacer(this,10));

        EditText model=addField(form,"Model *","예: Pelagos 42 Blue",base.modelName);
        EditText brand=addField(form,"Brand","예: Tudor",base.brand);
        EditText reference=addField(form,"Reference","예: M25600TB-0001",base.referenceNo);
        Button lookup=UiKit.primaryButton(this,"스펙 정밀 검색");
        form.addView(lookup);
        TextView searchStatus=UiKit.muted(this,"",10);
        searchStatus.setVisibility(View.GONE);
        form.addView(searchStatus);
        form.addView(UiKit.spacer(this,10));

        form.addView(UiKit.label(this,"작동방식"));
        Spinner power=powerSpinner(base.movement);
        form.addView(power);
        EditText caliber=addField(form,"무브먼트","예: MT5612",base.caliber);
        EditText diameter=addField(form,"Diameter (mm)","42",base.diameterMm);
        EditText l2l=addField(form,"Lug-to-lug (mm)","50",base.lugToLugMm);
        EditText thickness=addField(form,"Thickness (mm)","14.3",base.thicknessMm);
        EditText lug=addField(form,"Lug Width (mm)","22",base.lugWidthMm);
        EditText reserve=addField(form,"Power Reserve (h)","70",base.powerReserveHours);
        EditText water=addField(form,"Water Resistance","예: 500 m",base.waterResistance);
        EditText crystal=addField(form,"Crystal","Sapphire",base.crystal);
        EditText material=addField(form,"Case Material","Titanium",base.caseMaterial);
        EditText targetPrice=addField(form,"목표 구매가격","예: 4500000",base.targetPrice);
        targetPrice.setInputType(InputType.TYPE_CLASS_NUMBER);

        form.addView(UiKit.label(this,"우선순위"));
        Spinner priority=new Spinner(this);
        String[] priorities={"최우선","높음","보통","낮음"};
        priority.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,priorities));
        decorateSpinner(priority);
        for(int i=0;i<priorities.length;i++) if(priorities[i].equals(base.priority)){priority.setSelection(i);break;}
        form.addView(priority);
        form.addView(UiKit.spacer(this,8));

        form.addView(UiKit.label(this,"목표 구매일"));
        LinearLayout dateRow=UiKit.row(this);
        EditText targetDate=UiKit.field(this,"0000-00-00");
        targetDate.setFocusable(false);
        targetDate.setClickable(true);
        targetDate.setText(base.targetPurchaseDate);
        targetDate.setOnClickListener(v->pickDate(val(targetDate),targetDate::setText));
        Button clearDate=UiKit.secondaryButton(this,"Clear");
        clearDate.setOnClickListener(v->targetDate.setText(""));
        dateRow.addView(targetDate,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        dateRow.addView(clearDate,new LinearLayout.LayoutParams(UiKit.dp(this,82),ViewGroup.LayoutParams.WRAP_CONTENT));
        form.addView(dateRow);

        EditText source=addField(form,"제품/참고 URL","https://",base.sourceUrl);
        EditText notes=addField(form,"메모","구매 이유, 색상, 교체 계획 등",base.notes);
        notes.setSingleLine(false);
        notes.setMinLines(3);
        notes.setGravity(Gravity.TOP|Gravity.START);

        List<Watch> allWatches=db.getWatches();
        Set<Long> assigned=db.getAssignedReplacementWatchIds(base.id);
        List<Watch> replacementOptions=new ArrayList<>();
        for(Watch watch:allWatches) if(!assigned.contains(watch.id)||watch.id==base.replaceWatchId) replacementOptions.add(watch);
        form.addView(UiKit.label(this,"현재 컬렉션에서 대체할 시계"));
        Spinner replacement=watchSpinnerWithNone(replacementOptions,"— 대체 시계 미지정 —");
        if(base.replaceWatchId>0) setWatchSpinnerSelection(replacement,replacementOptions,base.replaceWatchId);
        form.addView(replacement);
        form.addView(UiKit.muted(this,"한 현재 시계는 하나의 Wish 항목에만 대체 대상으로 연결됩니다.",10));

        lookup.setOnClickListener(v->{
            String query=joinNonEmpty(" ",val(brand),val(model),val(reference));
            if(query.isEmpty()) {
                model.setError("검색할 모델명을 입력하세요.");
                return;
            }
            Runnable search=()->{
                lookup.setEnabled(false);
                searchStatus.setVisibility(View.VISIBLE);
                searchStatus.setText("AI 스펙 검색을 시작합니다.");
                executor.execute(()->{
                    try {
                        AiBackendClient.SpecResult result=AiBackendClient.searchWatchSpec(this,query,val(brand),val(reference),
                                message->runOnUiThread(()->searchStatus.setText(message)));
                        runOnUiThread(()->{
                            lookup.setEnabled(true);
                            setIf(model,result.model);
                            setIf(brand,result.brand);
                            setIf(reference,result.reference);
                            setPowerSelection(power,result.power);
                            setIf(caliber,result.movement);
                            setIf(diameter,result.diameterMm);
                            setIf(l2l,result.lugToLugMm);
                            setIf(thickness,result.thicknessMm);
                            setIf(lug,result.lugWidthMm);
                            setIf(reserve,result.powerReserveHours);
                            setIf(water,result.waterResistance);
                            setIf(crystal,result.crystal);
                            setIf(material,result.caseMaterial);
                            if(val(source).isEmpty()&&!result.sources.isEmpty()) source.setText(result.sources.get(0));
                            searchStatus.setText(result.summary);
                        });
                    } catch(Exception e) {
                        runOnUiThread(()->{
                            lookup.setEnabled(true);
                            searchStatus.setText("검색 실패: "+e.getMessage());
                        });
                    } finally {
                        runOnUiThread(()->refreshApiCreditUsage(true));
                    }
                });
            };
            if(!AiBackendClient.isConfigured(this)) showAiSettingsDialog(search);
            else search.run();
        });

        ScrollView scroll=new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(editing?"Wish 시계 수정":"Wish 시계 추가")
                .setView(scroll)
                .setNegativeButton("취소",null)
                .setPositiveButton("저장",null)
                .create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(val(model).isEmpty()) {
                model.setError("모델명을 입력하세요.");
                return;
            }
            if(pendingWishImageProcessing) {
                Toast.makeText(this,"사진 처리가 끝날 때까지 기다려 주세요.",Toast.LENGTH_SHORT).show();
                return;
            }
            base.modelName=val(model);
            base.brand=val(brand);
            base.referenceNo=val(reference);
            base.movement=String.valueOf(power.getSelectedItem());
            base.caliber=val(caliber);
            base.diameterMm=val(diameter);
            base.lugToLugMm=val(l2l);
            base.thicknessMm=val(thickness);
            base.lugWidthMm=val(lug);
            base.powerReserveHours=val(reserve);
            base.waterResistance=val(water);
            base.crystal=val(crystal);
            base.caseMaterial=val(material);
            base.targetPrice=val(targetPrice);
            base.priority=String.valueOf(priority.getSelectedItem());
            base.targetPurchaseDate=val(targetDate);
            base.sourceUrl=val(source);
            base.notes=val(notes);
            Watch selectedReplacement=selectedWatch(replacement,replacementOptions);
            base.replaceWatchId=selectedReplacement==null?-1L:selectedReplacement.id;
            if(pendingWishImageClear) {
                base.representativeImageUrl="";
                base.originalImageUrl="";
            } else if(!pendingWishProcessedPath.isEmpty()) {
                base.representativeImageUrl=pendingWishProcessedPath;
                base.originalImageUrl=pendingWishOriginalPath;
            }
            db.saveWishWatch(base);
            pendingWishPreview=null;
            pendingWishPhotoStatus=null;
            dialog.dismiss();
            showWishlist();
        }));
        dialog.show();
    }

    private void openWishImagePicker() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent,REQ_WISH_IMAGE);
    }

    private void showWishWatchDetail(long wishId) {
        WishWatch wish=db.getWishWatch(wishId);
        if(wish==null) return;
        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,12));
        ImageView image=new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,14,this));
        ImageLoader.loadFit(this,image,wish.representativeImageUrl);
        body.addView(image,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,180)));
        body.addView(UiKit.eyebrow(this,wish.brand.isEmpty()?"WISH":wish.brand));
        body.addView(UiKit.text(this,wish.modelName,22,true));
        if(!wish.referenceNo.isEmpty()) body.addView(UiKit.muted(this,"Ref. "+wish.referenceNo,11));
        body.addView(UiKit.spacer(this,8));
        if(!wish.priority.isEmpty()) body.addView(detailSpecRow("우선순위",wish.priority));
        if(!wish.targetPrice.isEmpty()) body.addView(detailSpecRow("목표 구매가격",formatWon(wish.targetPrice)));
        if(!wish.targetPurchaseDate.isEmpty()) body.addView(detailSpecRow("목표 구매일",wish.targetPurchaseDate));
        String power=normalizePower(wish.movement);
        if(!power.isEmpty()) body.addView(detailSpecRow("Power",power));
        if(!wish.caliber.isEmpty()) body.addView(detailSpecRow("Movement",wish.caliber));
        if(!wish.diameterMm.isEmpty()) body.addView(detailSpecRow("Diameter",wish.diameterMm+" mm"));
        if(!wish.lugToLugMm.isEmpty()) body.addView(detailSpecRow("Lug-to-lug",wish.lugToLugMm+" mm"));
        if(!wish.thicknessMm.isEmpty()) body.addView(detailSpecRow("Thickness",wish.thicknessMm+" mm"));
        if(!wish.lugWidthMm.isEmpty()) body.addView(detailSpecRow("Lug Width",wish.lugWidthMm+" mm"));
        if(!wish.powerReserveHours.isEmpty()) body.addView(detailSpecRow("Power Reserve",wish.powerReserveHours+" h"));
        if(!wish.waterResistance.isEmpty()) body.addView(detailSpecRow("Water Resistance",wish.waterResistance));
        if(!wish.crystal.isEmpty()) body.addView(detailSpecRow("Crystal",wish.crystal));
        if(!wish.caseMaterial.isEmpty()) body.addView(detailSpecRow("Case Material",wish.caseMaterial));
        Watch replacement=wish.replaceWatchId>0?db.getWatch(wish.replaceWatchId):null;
        body.addView(detailSpecRow("대체 대상",replacement==null?"미지정":replacement.modelName));
        if(!wish.notes.isEmpty()) {
            body.addView(UiKit.spacer(this,8));
            body.addView(UiKit.muted(this,wish.notes,12));
        }
        if(!wish.sourceUrl.isEmpty()) {
            Button open=UiKit.secondaryButton(this,"제품/참고 페이지 열기");
            open.setOnClickListener(v->{
                try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(wish.sourceUrl))); }
                catch(Exception e) { Toast.makeText(this,"URL을 열 수 없습니다.",Toast.LENGTH_SHORT).show(); }
            });
            body.addView(UiKit.spacer(this,8));
            body.addView(open);
        }
        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Wish 시계")
                .setView(scroll)
                .setNegativeButton("삭제",null)
                .setNeutralButton("수정",null)
                .setPositiveButton("닫기",null)
                .create();
        dialog.setOnShowListener(x->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{dialog.dismiss();showWishWatchDialog(wish);});
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->new AlertDialog.Builder(this)
                    .setTitle("Wish 시계 삭제")
                    .setMessage(wish.modelName+"을 위시리스트에서 삭제할까요?")
                    .setNegativeButton("취소",null)
                    .setPositiveButton("삭제",(d,which)->{
                        db.deleteWishWatch(wish.id);
                        dialog.dismiss();
                        showWishlist();
                    }).show());
        });
        dialog.show();
    }

    private View buildWishReleaseCandidateCard() {
        LinearLayout card=UiKit.card(this);
        card.addView(UiKit.eyebrow(this,"RELEASE CANDIDATES"));
        card.addView(UiKit.text(this,"방출 대상",18,true));
        card.addView(UiKit.muted(this,"Wish의 대체 대상으로 연결되지 않은 현재 시계 중 별도 방출 후보를 지정합니다.",10));
        card.addView(UiKit.spacer(this,7));
        Set<Long> selected=db.getWishReleaseCandidateIds();
        if(selected.isEmpty()) {
            card.addView(UiKit.muted(this,"지정된 방출 후보가 없습니다.",11));
        } else {
            for(Long id:selected) {
                Watch watch=db.getWatch(id);
                if(watch==null) continue;
                TextView row=UiKit.text(this,"• "+(watch.brand.isEmpty()?"":watch.brand+" · ")+watch.modelName,12,true);
                row.setPadding(0,UiKit.dp(this,3),0,UiKit.dp(this,3));
                row.setOnClickListener(v->showWatchDetailPopup(watch));
                card.addView(row);
            }
        }
        Button manage=UiKit.secondaryButton(this,"방출 대상 지정");
        manage.setOnClickListener(v->showWishReleaseCandidateDialog());
        card.addView(UiKit.spacer(this,7));
        card.addView(manage);
        return card;
    }

    private void showWishReleaseCandidateDialog() {
        List<Watch> watches=db.getWatches();
        Set<Long> assigned=db.getAssignedReplacementWatchIds(-1L);
        Set<Long> selected=db.getWishReleaseCandidateIds();
        List<Watch> available=new ArrayList<>();
        for(Watch watch:watches) if(!assigned.contains(watch.id)) available.add(watch);
        if(available.isEmpty()) {
            Toast.makeText(this,"모든 현재 시계가 Wish 대체 대상으로 연결되어 있습니다.",Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,16),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,12));
        Map<Long,CheckBox> boxes=new LinkedHashMap<>();
        for(Watch watch:available) {
            CheckBox box=new CheckBox(this);
            box.setText((watch.brand.isEmpty()?"":watch.brand+" · ")+watch.modelName);
            box.setTextColor(UiKit.INK);
            box.setTextSize(13);
            box.setChecked(selected.contains(watch.id));
            boxes.put(watch.id,box);
            body.addView(box);
        }
        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("방출 대상 지정")
                .setView(scroll)
                .setNegativeButton("취소",null)
                .setPositiveButton("저장",null)
                .create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            for(Map.Entry<Long,CheckBox> entry:boxes.entrySet()) {
                db.setWishReleaseCandidate(entry.getKey(),entry.getValue().isChecked());
            }
            dialog.dismiss();
            showWishlist();
        }));
        dialog.show();
    }

    private String getWishCollectionSignature() {
        List<String> parts=new ArrayList<>();
        List<Long> currentIds=new ArrayList<>();
        for(Watch watch:db.getWatches()) currentIds.add(watch.id);
        Collections.sort(currentIds);
        parts.add("current="+currentIds.toString());
        for(WishWatch wish:db.getWishWatches()) {
            parts.add("wish="+wish.id+":"+wish.replaceWatchId+":"+wish.modelName+":"+wish.referenceNo);
        }
        List<Long> releases=new ArrayList<>(db.getWishReleaseCandidateIds());
        Collections.sort(releases);
        parts.add("release="+releases.toString());
        return String.join("|",parts);
    }

    private void runWishCollectionEvaluation(String signature) {
        List<WishWatch> wishes=db.getWishWatches();
        if(wishes.isEmpty()) {
            Toast.makeText(this,"평가할 Wish 시계를 먼저 추가하세요.",Toast.LENGTH_LONG).show();
            return;
        }
        if(!AiBackendClient.isConfigured(this)) {
            showAiSettingsDialog(()->runWishCollectionEvaluation(signature));
            return;
        }
        TextView progress=UiKit.muted(this,"현재 컬렉션과 Wish 교체 계획을 정리합니다.",12);
        progress.setPadding(UiKit.dp(this,18),UiKit.dp(this,14),UiKit.dp(this,18),UiKit.dp(this,14));
        AlertDialog loading=new AlertDialog.Builder(this)
                .setTitle("Wish 컬렉션 평가")
                .setView(progress)
                .setNegativeButton("취소",null)
                .create();
        loading.setCanceledOnTouchOutside(false);
        loading.show();
        executor.execute(()->{
            try {
                AiBackendClient.CollectionEvaluationResult result=AiBackendClient.evaluateCollection(
                        this,"wish",buildCurrentCollectionEvaluationPayload(db.getWatches()),
                        buildWishCollectionEvaluationPayload(wishes),buildWishReleaseEvaluationPayload(),
                        message->runOnUiThread(()->progress.setText(message)));
                db.saveCollectionEvaluation("wish",result.overallScore,result.summary,
                        serializeEvaluationMetrics(result.metrics),signature);
                runOnUiThread(()->{
                    if(loading.isShowing()) loading.dismiss();
                    showWishlist();
                    DatabaseHelper.CollectionEvaluationRow saved=db.getCollectionEvaluation("wish");
                    if(saved!=null) showCollectionEvaluationDialog(saved,"Wish 컬렉션 평가",true);
                });
            } catch(Exception e) {
                runOnUiThread(()->{
                    if(loading.isShowing()) loading.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle("Wish 평가 실패")
                            .setMessage(e.getMessage())
                            .setNegativeButton("닫기",null)
                            .setPositiveButton("AI 설정",(d,x)->showAiSettingsDialog(null))
                            .show();
                });
            } finally {
                runOnUiThread(()->refreshApiCreditUsage(true));
            }
        });
    }

    private JSONArray buildWishCollectionEvaluationPayload(List<WishWatch> wishes) throws Exception {
        JSONArray out=new JSONArray();
        for(WishWatch wish:wishes) {
            JSONObject item=new JSONObject();
            item.put("id",wish.id);
            item.put("brand",wish.brand);
            item.put("model",wish.modelName);
            item.put("reference",wish.referenceNo);
            item.put("power",normalizePower(wish.movement));
            item.put("movement",wish.caliber);
            item.put("diameter_mm",wish.diameterMm);
            item.put("lug_to_lug_mm",wish.lugToLugMm);
            item.put("thickness_mm",wish.thicknessMm);
            item.put("lug_width_mm",wish.lugWidthMm);
            item.put("power_reserve_hours",wish.powerReserveHours);
            item.put("water_resistance",wish.waterResistance);
            item.put("case_material",wish.caseMaterial);
            item.put("priority",wish.priority);
            item.put("target_price",wish.targetPrice);
            item.put("replace_watch_id",wish.replaceWatchId);
            Watch replacement=wish.replaceWatchId>0?db.getWatch(wish.replaceWatchId):null;
            item.put("replace_watch_name",replacement==null?"":replacement.modelName);
            item.put("notes",wish.notes);
            out.put(item);
        }
        return out;
    }

    private JSONArray buildWishReleaseEvaluationPayload() throws Exception {
        JSONArray out=new JSONArray();
        for(Long id:db.getWishReleaseCandidateIds()) {
            Watch watch=db.getWatch(id);
            if(watch!=null) out.put(buildEvaluationWatchJson(watch));
        }
        return out;
    }


    private void showHeritage() {
        selectNav(navCollection);
        LinearLayout p=page();

        LinearLayout head=UiKit.row(this);
        Button back=UiKit.secondaryButton(this,"‹ 컬렉션");
        back.setOnClickListener(v->showCollection());
        head.addView(back);
        LinearLayout titleBox=UiKit.column(this);
        titleBox.setPadding(UiKit.dp(this,10),0,0,0);
        titleBox.addView(UiKit.eyebrow(this,"HERITAGE"));
        titleBox.addView(UiKit.text(this,"헤리티지",25,true));
        head.addView(titleBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button add=UiKit.primaryButton(this,"+ 추가");
        add.setOnClickListener(v->showHeritageAddDialog());
        head.addView(add);
        p.addView(head);
        p.addView(UiKit.spacer(this,12));

        LinearLayout periodRow=UiKit.row(this);
        Button period=UiKit.secondaryButton(this,heritageMonthFilter.isEmpty()?"전체 기간 · 연도/월 선택":formatHeritageMonth(heritageMonthFilter));
        period.setOnClickListener(v->showHeritageMonthPicker(heritageMonthFilter));
        periodRow.addView(period,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        if(!heritageMonthFilter.isEmpty()) {
            Button reset=UiKit.secondaryButton(this,"전체");
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(UiKit.dp(this,74),ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.setMargins(UiKit.dp(this,8),0,0,0);
            periodRow.addView(reset,rp);
            reset.setOnClickListener(v->{ heritageMonthFilter=""; showHeritage(); });
        }
        p.addView(periodRow);
        p.addView(UiKit.spacer(this,10));

        List<DatabaseHelper.HeritageEntryRow> source=db.getHeritageEntries();
        List<DatabaseHelper.HeritageEntryRow> entries=new ArrayList<>();
        for(DatabaseHelper.HeritageEntryRow entry:source) {
            String month=entry.eventDate!=null&&entry.eventDate.length()>=7?entry.eventDate.substring(0,7):"";
            if(heritageMonthFilter.isEmpty()||heritageMonthFilter.equals(month)) entries.add(entry);
        }

        if(entries.isEmpty()) {
            if(heritageMonthFilter.isEmpty()) {
                p.addView(emptyCard("첫 헤리티지를 기록해보세요","날짜와 컬렉션 시계 또는 직접 입력한 시계명을 선택하고 제목, 내용, 사진을 저장하면 월별 헤리티지로 정리됩니다."));
            } else {
                p.addView(emptyCard(formatHeritageMonth(heritageMonthFilter)+" 기록 없음","다른 연도·월을 선택하거나 새 헤리티지를 추가해 주세요."));
            }
        } else {
            String currentMonth="";
            for(DatabaseHelper.HeritageEntryRow entry:entries) {
                String month=entry.eventDate!=null&&entry.eventDate.length()>=7?entry.eventDate.substring(0,7):"날짜 미지정";
                if(!month.equals(currentMonth)) {
                    currentMonth=month;
                    TextView monthTitle=UiKit.text(this,formatHeritageMonth(month)+"  ›",18,true);
                    monthTitle.setTextColor(UiKit.NAVY);
                    monthTitle.setPadding(UiKit.dp(this,4),UiKit.dp(this,8),0,UiKit.dp(this,8));
                    final String selectedMonth=month;
                    monthTitle.setOnClickListener(v->showHeritageMonthPicker(selectedMonth));
                    p.addView(monthTitle);
                }
                p.addView(heritageTimelineCard(entry));
            }
        }
        setScrollable(p);
    }

    private void showHeritageMonthPicker(String initialMonth) {
        Calendar now=Calendar.getInstance();
        int initialYear=now.get(Calendar.YEAR);
        int initialMonthIndex=now.get(Calendar.MONTH);
        try {
            if(initialMonth!=null&&!initialMonth.isEmpty()) {
                String[] parts=initialMonth.split("-");
                initialYear=Integer.parseInt(parts[0]);
                initialMonthIndex=Math.max(0,Math.min(11,Integer.parseInt(parts[1])-1));
            }
        } catch(Exception ignored) {}

        List<Integer> years=new ArrayList<>();
        for(DatabaseHelper.HeritageEntryRow entry:db.getHeritageEntries()) {
            try {
                int y=Integer.parseInt(entry.eventDate.substring(0,4));
                if(!years.contains(y)) years.add(y);
            } catch(Exception ignored) {}
        }
        for(int y=now.get(Calendar.YEAR)-10;y<=now.get(Calendar.YEAR)+2;y++) if(!years.contains(y)) years.add(y);
        years.sort((a,b)->Integer.compare(b,a));

        LinearLayout form=UiKit.column(this);
        form.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,12));
        form.addView(UiKit.label(this,"연도"));
        Spinner yearSpinner=new Spinner(this);
        List<String> yearLabels=new ArrayList<>();
        int yearSelection=0;
        for(int i=0;i<years.size();i++) {
            yearLabels.add(years.get(i)+"년");
            if(years.get(i)==initialYear) yearSelection=i;
        }
        yearSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,yearLabels));
        decorateSpinner(yearSpinner);
        yearSpinner.setSelection(yearSelection);
        form.addView(yearSpinner);
        form.addView(UiKit.spacer(this,10));

        form.addView(UiKit.label(this,"월"));
        Spinner monthSpinner=new Spinner(this);
        String[] monthLabels={"1월","2월","3월","4월","5월","6월","7월","8월","9월","10월","11월","12월"};
        monthSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,monthLabels));
        decorateSpinner(monthSpinner);
        monthSpinner.setSelection(initialMonthIndex);
        form.addView(monthSpinner);

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("헤리티지 연도·월 선택")
                .setView(form)
                .setNeutralButton("전체 기간",null)
                .setNegativeButton("취소",null)
                .setPositiveButton("적용",null)
                .create();
        dialog.setOnShowListener(x->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                heritageMonthFilter="";
                dialog.dismiss();
                showHeritage();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                int y=years.get(Math.max(0,yearSpinner.getSelectedItemPosition()));
                int m=monthSpinner.getSelectedItemPosition()+1;
                heritageMonthFilter=String.format(Locale.KOREA,"%04d-%02d",y,m);
                dialog.dismiss();
                showHeritage();
            });
        });
        dialog.show();
    }

    private String formatHeritageMonth(String yyyyMm) {
        try {
            String[] parts=yyyyMm.split("-");
            return Integer.parseInt(parts[0])+"년 "+Integer.parseInt(parts[1])+"월";
        } catch(Exception ignored) { return yyyyMm; }
    }

    private View heritageTimelineCard(DatabaseHelper.HeritageEntryRow entry) {
        LinearLayout card=UiKit.card(this);
        LinearLayout row=UiKit.row(this);
        List<DatabaseHelper.HeritagePhotoRow> photos=db.getHeritagePhotos(entry.id);
        String imagePath=!photos.isEmpty()?photos.get(0).imagePath:entry.watchImage;
        ImageView image=new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,14,this));
        image.setClipToOutline(true);
        image.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        ImageLoader.loadFit(this,image,imagePath);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(UiKit.dp(this,112),UiKit.dp(this,112));
        ip.setMargins(0,0,UiKit.dp(this,12),0);
        row.addView(image,ip);

        LinearLayout info=UiKit.column(this);
        TextView date=UiKit.eyebrow(this,entry.eventDate);
        info.addView(date);
        info.addView(UiKit.text(this,entry.title,17,true));
        if(!entry.watchName.isEmpty()) info.addView(UiKit.muted(this,entry.watchName,11));
        if(!entry.content.trim().isEmpty()) {
            TextView content=UiKit.muted(this,entry.content.trim(),12);
            content.setMaxLines(2);
            content.setEllipsize(android.text.TextUtils.TruncateAt.END);
            content.setPadding(0,UiKit.dp(this,5),0,0);
            info.addView(content);
        }
        row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        card.addView(row);
        card.setOnClickListener(v->showHeritageDetailPopup(entry.id));
        return card;
    }

    private void showHeritageAddDialog() {
        List<Watch> watches=db.getWatches();
        pendingHeritagePhotoPaths.clear();
        pendingHeritageSaved=false;

        LinearLayout form=UiKit.column(this);
        form.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,12));

        EditText date=UiKit.field(this,"날짜");
        date.setText(dateFormat.format(new Date()));
        date.setFocusable(false);
        date.setClickable(true);
        date.setOnClickListener(v->pickDate(val(date),date::setText));
        form.addView(UiKit.label(this,"날짜"));
        form.addView(date);

        form.addView(UiKit.label(this,"시계"));
        Spinner watch=new Spinner(this);
        List<String> watchLabels=new ArrayList<>();
        for(Watch item:watches) {
            watchLabels.add((item.brand==null||item.brand.trim().isEmpty()?"":item.brand.trim()+" · ")+item.modelName);
        }
        final int manualWatchIndex=watchLabels.size();
        watchLabels.add("직접 시계명 입력");
        watch.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,watchLabels));
        decorateSpinner(watch);
        if(watches.isEmpty()) watch.setSelection(manualWatchIndex);
        form.addView(watch);

        EditText manualWatchName=addField(form,"직접 시계명","예: Omega Seamaster 300","");
        manualWatchName.setVisibility(watches.isEmpty()?View.VISIBLE:View.GONE);
        watch.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                manualWatchName.setVisibility(position==manualWatchIndex?View.VISIBLE:View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        EditText title=addField(form,"제목","예: 첫 기계식 시계, 여행의 동반자","" );
        form.addView(UiKit.label(this,"내용"));
        EditText content=UiKit.field(this,"헤리티지에 남길 내용을 적어주세요.");
        content.setSingleLine(false);
        content.setMinLines(4);
        form.addView(content);

        Button photo=UiKit.secondaryButton(this,"오착 사진 추가");
        photo.setOnClickListener(v->openHeritagePhotoPicker());
        form.addView(photo);
        form.addView(UiKit.spacer(this,7));
        HorizontalScrollView photoScroll=new HorizontalScrollView(this);
        photoScroll.setHorizontalScrollBarEnabled(false);
        pendingHeritagePhotoPreview=UiKit.row(this);
        photoScroll.addView(pendingHeritagePhotoPreview);
        form.addView(photoScroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,114)));
        refreshPendingHeritagePhotoPreview();

        ScrollView scroll=new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("헤리티지 추가")
                .setView(scroll)
                .setNegativeButton("취소",null)
                .setPositiveButton("저장",null)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(d->{ if(!pendingHeritageSaved) cleanupPendingHeritagePhotos(); });
        dialog.setOnShowListener(x->{
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{
                cleanupPendingHeritagePhotos();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(val(title).isEmpty()) { title.setError("제목은 필수입니다."); return; }
                int pos=watch.getSelectedItemPosition();
                Watch selected=pos>=0&&pos<watches.size()?watches.get(pos):null;
                String directName=val(manualWatchName);
                if(selected==null&&directName.isEmpty()) {
                    manualWatchName.setError("시계 이름을 입력하세요.");
                    return;
                }

                long entryId;
                if(selected!=null) {
                    entryId=db.addHeritageEntry(val(date),selected,val(title),val(content));
                } else {
                    entryId=db.addHeritageEntry(val(date),0,directName,"",val(title),val(content));
                }
                if(entryId<=0) {
                    Toast.makeText(this,"헤리티지 저장에 실패했습니다.",Toast.LENGTH_SHORT).show();
                    return;
                }
                for(int i=0;i<pendingHeritagePhotoPaths.size();i++) {
                    db.addHeritagePhoto(entryId,pendingHeritagePhotoPaths.get(i),i);
                }
                pendingHeritageSaved=true;
                pendingHeritagePhotoPaths.clear();
                pendingHeritagePhotoPreview=null;
                dialog.dismiss();
                showHeritage();
            });
        });
        dialog.show();
    }

    private void openHeritagePhotoPicker() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        startActivityForResult(intent,REQ_HERITAGE_PHOTOS);
    }

    private void refreshPendingHeritagePhotoPreview() {
        if(pendingHeritagePhotoPreview==null) return;
        pendingHeritagePhotoPreview.removeAllViews();
        if(pendingHeritagePhotoPaths.isEmpty()) {
            pendingHeritagePhotoPreview.addView(UiKit.muted(this,"선택한 사진이 없습니다.",11));
            return;
        }
        for(String path:pendingHeritagePhotoPaths) {
            ImageView image=new ImageView(this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,10,this));
            image.setClipToOutline(true);
            image.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            ImageLoader.loadFit(this,image,path);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(UiKit.dp(this,104),UiKit.dp(this,104));
            lp.setMargins(0,0,UiKit.dp(this,7),0);
            pendingHeritagePhotoPreview.addView(image,lp);
        }
    }

    private void cleanupPendingHeritagePhotos() {
        for(String path:pendingHeritagePhotoPaths) {
            try {
                java.io.File file=new java.io.File(path);
                if(file.exists()&&file.isFile()) file.delete();
            } catch(Exception ignored) {}
        }
        pendingHeritagePhotoPaths.clear();
        pendingHeritagePhotoPreview=null;
    }

    private void showHeritageDetailPopup(long entryId) {
        DatabaseHelper.HeritageEntryRow entry=db.getHeritageEntry(entryId);
        if(entry==null) return;
        List<DatabaseHelper.HeritagePhotoRow> photos=db.getHeritagePhotos(entryId);
        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,16),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,12));
        body.addView(UiKit.eyebrow(this,entry.eventDate));
        body.addView(UiKit.text(this,entry.title,21,true));
        if(!entry.watchName.isEmpty()) body.addView(UiKit.muted(this,entry.watchName,12));
        if(!entry.content.trim().isEmpty()) {
            TextView content=UiKit.text(this,entry.content.trim(),13,false);
            content.setPadding(0,UiKit.dp(this,10),0,UiKit.dp(this,10));
            body.addView(content);
        }
        if(!photos.isEmpty()) {
            HorizontalScrollView scrollPhotos=new HorizontalScrollView(this);
            scrollPhotos.setHorizontalScrollBarEnabled(false);
            LinearLayout row=UiKit.row(this);
            for(DatabaseHelper.HeritagePhotoRow photo:photos) {
                ImageView image=new ImageView(this);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,12,this));
                image.setClipToOutline(true);
                image.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
                ImageLoader.loadFit(this,image,photo.imagePath);
                image.setOnClickListener(v->showWearPhotoFullScreen(photo.imagePath,entry.title));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(UiKit.dp(this,232),UiKit.dp(this,232));
                lp.setMargins(0,0,UiKit.dp(this,8),0);
                row.addView(image,lp);
            }
            scrollPhotos.addView(row);
            body.addView(scrollPhotos);
        } else if(!entry.watchImage.isEmpty()) {
            ImageView image=new ImageView(this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,12,this));
            ImageLoader.loadFit(this,image,entry.watchImage);
            body.addView(image,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,210)));
        }

        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("헤리티지")
                .setView(scroll)
                .setNegativeButton("삭제",null)
                .setPositiveButton("닫기",null)
                .create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->
                new AlertDialog.Builder(this)
                        .setTitle("헤리티지 삭제")
                        .setMessage("이 헤리티지 기록과 사진을 삭제할까요?")
                        .setNegativeButton("취소",null)
                        .setPositiveButton("삭제",(d,which)->{
                            db.deleteHeritageEntry(entryId);
                            dialog.dismiss();
                            showHeritage();
                        }).show()));
        dialog.show();
    }

    private void showWatchDialog(Watch existing) {
        boolean editing=existing!=null;
        SharedPageAnalyzer.Result sharedImport=!editing?pendingSharedWatchImport:null;
        pendingSharedWatchImport=null;
        Watch base=editing?existing:new Watch();
        if(sharedImport!=null) {
            base.modelName=firstNonEmpty(sharedImport.productName,sharedImport.sharedTitle);
            base.brand=safeText(sharedImport.brand);
            base.referenceNo=safeText(sharedImport.referenceNo);
            base.sourceUrls=firstNonEmpty(sharedImport.canonicalUrl,sharedImport.finalUrl);
        }
        pendingWatchImageUri=null;
        pendingWatchImageClear=false;
        pendingWatchImageProcessing=false;
        pendingWatchRestoreOriginal=false;
        pendingWatchProcessedPath="";
        pendingWatchOriginalPath="";
        pendingWatchCalendarPath="";
        pendingCasebackImageUri=null;
        pendingCasebackPreview=null;
        pendingCasebackPhotoStatus=null;
        pendingCasebackImageClear=false;
        pendingCasebackImageProcessing=false;
        pendingCasebackRestoreOriginal=false;
        pendingCasebackProcessedPath="";
        pendingCasebackOriginalPath="";
        LinearLayout form=UiKit.column(this);
        form.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,8));

        ImageView preview=new ImageView(this);
        preview.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,16,this));
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setClipToOutline(true);
        preview.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        pendingWatchPreview=preview;
        ImageLoader.load(this,preview,base.representativeImageUrl);
        form.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,210)));

        LinearLayout photoActions=UiKit.row(this);
        Button pickWatchPhoto=UiKit.secondaryButton(this,"대표 사진 선택");
        Button restoreWatchPhoto=UiKit.secondaryButton(this,"원본 복구");
        Button clearWatchPhoto=UiKit.secondaryButton(this,"사진 제거");
        pickWatchPhoto.setOnClickListener(v->openWatchImagePicker());
        restoreWatchPhoto.setOnClickListener(v->{
            String original = !pendingWatchOriginalPath.isEmpty() ? pendingWatchOriginalPath : base.originalImageUrl;
            if(original==null||original.trim().isEmpty()) {
                Toast.makeText(this,"복구할 원본 사진이 없습니다.",Toast.LENGTH_SHORT).show();
                return;
            }
            pendingWatchRestoreOriginal=true;
            pendingWatchImageClear=false;
            ImageLoader.load(this,preview,original);
            if(pendingWatchPhotoStatus!=null) pendingWatchPhotoStatus.setText("원본 사진을 사용합니다. 저장하면 원본 상태로 유지됩니다.");
        });
        clearWatchPhoto.setOnClickListener(v->{
            pendingWatchImageUri=null;
            pendingWatchImageClear=true;
            pendingWatchRestoreOriginal=false;
            pendingWatchProcessedPath="";
            pendingWatchOriginalPath="";
            pendingWatchCalendarPath="";
            preview.setImageDrawable(null);
            preview.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,16,this));
            if(pendingWatchPhotoStatus!=null) pendingWatchPhotoStatus.setText("대표 사진을 제거합니다.");
        });
        photoActions.addView(pickWatchPhoto,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        photoActions.addView(restoreWatchPhoto,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        photoActions.addView(clearWatchPhoto,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        form.addView(photoActions);
        pendingWatchPhotoStatus=UiKit.muted(this,"",11);
        pendingWatchPhotoStatus.setVisibility(View.GONE);

        form.addView(UiKit.spacer(this,14));
        form.addView(UiKit.label(this,"케이스백 사진"));
        ImageView casebackPreview=new ImageView(this);
        casebackPreview.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,16,this));
        casebackPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        casebackPreview.setClipToOutline(true);
        casebackPreview.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        pendingCasebackPreview=casebackPreview;
        ImageLoader.loadFit(this,casebackPreview,base.casebackImageUrl);
        form.addView(casebackPreview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,190)));

        LinearLayout casebackActions=UiKit.row(this);
        Button pickCaseback=UiKit.secondaryButton(this,"케이스백 선택");
        Button restoreCaseback=UiKit.secondaryButton(this,"원본 복구");
        Button clearCaseback=UiKit.secondaryButton(this,"사진 제거");
        pickCaseback.setOnClickListener(v->openCasebackImagePicker(false,base.id));
        restoreCaseback.setOnClickListener(v->{
            String original=!pendingCasebackOriginalPath.isEmpty()?pendingCasebackOriginalPath:base.casebackOriginalImageUrl;
            if(original==null||original.trim().isEmpty()) {
                Toast.makeText(this,"복구할 케이스백 원본이 없습니다.",Toast.LENGTH_SHORT).show();
                return;
            }
            pendingCasebackRestoreOriginal=true;
            pendingCasebackImageClear=false;
            ImageLoader.loadFit(this,casebackPreview,original);
        });
        clearCaseback.setOnClickListener(v->{
            pendingCasebackImageUri=null;
            pendingCasebackImageClear=true;
            pendingCasebackRestoreOriginal=false;
            pendingCasebackProcessedPath="";
            pendingCasebackOriginalPath="";
            casebackPreview.setImageDrawable(null);
            casebackPreview.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,16,this));
        });
        casebackActions.addView(pickCaseback,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        casebackActions.addView(restoreCaseback,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        casebackActions.addView(clearCaseback,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        form.addView(casebackActions);
        pendingCasebackPhotoStatus=UiKit.muted(this,"",11);
        pendingCasebackPhotoStatus.setPadding(0,UiKit.dp(this,5),0,0);
        pendingCasebackPhotoStatus.setVisibility(View.GONE);
        form.addView(pendingCasebackPhotoStatus);

        form.addView(UiKit.spacer(this,12));
        EditText model=addField(form,"Model *","예: Murph 38 / BM1350-54A",base.modelName);
        EditText brand=addField(form,"Brand","예: Hamilton / Citizen",base.brand);
        EditText ref=addField(form,"Reference","예: H70405730 / BM1350-54A",base.referenceNo);
        Button lookup=UiKit.primaryButton(this,"스펙 정밀 검색");
        form.addView(lookup);
        form.addView(UiKit.spacer(this,10));
        TextView status=UiKit.muted(this,"",11);
        status.setPadding(UiKit.dp(this,10),UiKit.dp(this,9),UiKit.dp(this,10),UiKit.dp(this,9));
        status.setBackground(UiKit.rounded(UiKit.PAPER,UiKit.LINE,10,this));
        status.setVisibility(View.GONE);
        form.addView(status);
        form.addView(UiKit.spacer(this,14));
        List<String> progressLines=new ArrayList<>();

        form.addView(UiKit.label(this,"작동방식"));
        Spinner power=powerSpinner(base.movement);
        form.addView(power);
        EditText caliber=addField(form,"무브먼트","예: H-10 / 6R54 / E168",base.caliber);
        EditText diameter=addField(form,"Diameter (mm)","38",base.diameterMm);
        EditText l2l=addField(form,"Lug-to-lug (mm)","44.7",base.lugToLugMm);
        EditText thickness=addField(form,"Thickness (mm)","11.1",base.thicknessMm);
        EditText lug=addField(form,"Lug Width (mm)","20",base.lugWidthMm);
        EditText pr=addField(form,"Power Reserve (h)","80",base.powerReserveHours);
        EditText water=addField(form,"Water Resistance","예: 100 m",base.waterResistance);
        EditText crystal=addField(form,"Crystal","Sapphire",base.crystal);
        EditText material=addField(form,"Case Material","Stainless Steel",base.caseMaterial);

        form.addView(UiKit.label(this,"구매일자"));
        LinearLayout purchaseDateRow=UiKit.row(this);
        EditText purchaseDate=UiKit.field(this,"0000-00-00");
        purchaseDate.setFocusable(false);
        purchaseDate.setClickable(true);
        purchaseDate.setText(base.purchaseDate==null?"":base.purchaseDate);
        purchaseDate.setOnClickListener(v->pickDate(val(purchaseDate),purchaseDate::setText));
        Button clearPurchaseDate=UiKit.secondaryButton(this,"Clear");
        clearPurchaseDate.setOnClickListener(v->purchaseDate.setText(""));
        purchaseDateRow.addView(purchaseDate,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        purchaseDateRow.addView(clearPurchaseDate,new LinearLayout.LayoutParams(UiKit.dp(this,84),ViewGroup.LayoutParams.WRAP_CONTENT));
        form.addView(purchaseDateRow);

        form.addView(UiKit.label(this,"Warranty"));
        LinearLayout warrantyRow=UiKit.row(this);
        EditText warranty=UiKit.field(this,"~0000-00-00");
        warranty.setFocusable(false);
        warranty.setClickable(true);
        warranty.setText(base.warrantyEndDate==null||base.warrantyEndDate.isEmpty()?"":"~"+base.warrantyEndDate);
        warranty.setOnClickListener(v->{
            String cur=val(warranty);
            if(cur.startsWith("~")) cur=cur.substring(1);
            pickDate(cur,date->warranty.setText("~"+date));
        });
        Button clearWarranty=UiKit.secondaryButton(this,"Clear");
        clearWarranty.setOnClickListener(v->warranty.setText(""));
        warrantyRow.addView(warranty,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        warrantyRow.addView(clearWarranty,new LinearLayout.LayoutParams(UiKit.dp(this,84),ViewGroup.LayoutParams.WRAP_CONTENT));
        form.addView(warrantyRow);
        EditText purchasePrice=addField(form,"구매 가격","예: 1,250,000",base.purchasePrice);

        EditText sources=addField(form,"Source URLs","공식 페이지/시계 전문 출처만 저장",base.sourceUrls);
        sources.setSingleLine(false); sources.setMinLines(2);
        EditText notes=addField(form,"Notes","구매일, 특징 등",base.notes);

        lookup.setOnClickListener(v->{
            String q=val(model);
            if(q.isEmpty()){model.setError("모델명을 입력하세요.");return;}
            String brandValue=val(brand);
            String referenceValue=val(ref);
            Runnable startSearch=()->{
                lookup.setEnabled(false);
                progressLines.clear();
                status.setVisibility(View.VISIBLE);
                appendSpecProgress(status,progressLines,"AI 스펙 정밀 검색을 시작합니다.");
                executor.execute(()->{
                    try {
                        AiBackendClient.SpecResult r=AiBackendClient.searchWatchSpec(this,q,brandValue,referenceValue,
                                message->appendSpecProgress(status,progressLines,message));
                        runOnUiThread(()->{
                            setIf(model,r.model); setIf(brand,r.brand); setIf(ref,r.reference); setPowerSelection(power,r.power); setIf(caliber,r.movement);
                            setIf(diameter,r.diameterMm); setIf(l2l,r.lugToLugMm); setIf(thickness,r.thicknessMm); setIf(lug,r.lugWidthMm);
                            setIf(pr,r.powerReserveHours); setIf(water,r.waterResistance); setIf(crystal,r.crystal); setIf(material,r.caseMaterial);
                            if(!r.sources.isEmpty()) sources.setText(String.join("\n",r.sources));
                            appendSpecProgress(status,progressLines,r.summary);
                            lookup.setEnabled(true);
                        });
                    } catch(Exception aiError) {
                        appendSpecProgress(status,progressLines,"AI 검색 실패 · 무료 검색기로 전환합니다: "+aiError.getMessage());
                        try {
                            WebSpecLookup.Result fallback=WebSpecLookup.lookup(q,brandValue,referenceValue,
                                    message->appendSpecProgress(status,progressLines,message));
                            runOnUiThread(()->{
                                setIf(brand,fallback.brand); setIf(ref,fallback.referenceNo); setPowerSelection(power,fallback.movement); setIf(caliber,fallback.caliber);
                                setIf(diameter,fallback.diameterMm); setIf(l2l,fallback.lugToLugMm); setIf(thickness,fallback.thicknessMm); setIf(lug,fallback.lugWidthMm);
                                setIf(pr,fallback.powerReserveHours); setIf(water,fallback.waterResistance); setIf(crystal,fallback.crystal); setIf(material,fallback.caseMaterial);
                                if(!fallback.sourceUrls.isEmpty()) sources.setText(fallback.sourceUrls);
                                appendSpecProgress(status,progressLines,fallback.message);
                                lookup.setEnabled(true);
                            });
                        } catch(Exception fallbackError) {
                            appendSpecProgress(status,progressLines,"검색 실패: "+fallbackError.getMessage());
                            runOnUiThread(()->lookup.setEnabled(true));
                        }
                    } finally {
                        runOnUiThread(()->refreshApiCreditUsage(true));
                    }
                });
            };
            if(AiBackendClient.isConfigured(this)) startSearch.run();
            else showAiSettingsDialog(startSearch);
        });

        ScrollView sc=new ScrollView(this);
        sc.addView(form);
        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle(editing?"시계 수정":"시계 추가")
                .setView(sc)
                .setNegativeButton("취소",null)
                .setPositiveButton("저장",null)
                .create();

        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(val(model).isEmpty()){model.setError("모델명은 필수입니다.");return;}
            base.modelName=val(model); base.brand=val(brand); base.referenceNo=val(ref); base.movement=String.valueOf(power.getSelectedItem());
            base.caliber=val(caliber); base.diameterMm=val(diameter); base.lugToLugMm=val(l2l); base.thicknessMm=val(thickness);
            base.lugWidthMm=val(lug); base.powerReserveHours=val(pr); base.waterResistance=val(water); base.crystal=val(crystal);
            base.caseMaterial=val(material); base.purchaseDate=val(purchaseDate); String warrantyValue=val(warranty); base.warrantyEndDate=warrantyValue.startsWith("~")?warrantyValue.substring(1):warrantyValue; base.purchasePrice=val(purchasePrice); base.sourceUrls=val(sources); base.notes=val(notes);
            if(pendingWatchImageProcessing || pendingCasebackImageProcessing) {
                Toast.makeText(this,"사진 배경을 처리 중입니다. 잠시 후 다시 저장해 주세요.",Toast.LENGTH_SHORT).show();
                return;
            }
            if(pendingWatchImageClear) {
                base.representativeImageUrl="";
                base.originalImageUrl="";
                base.calendarImageUrl="";
                base.imageSourceUrl="";
            } else if(!pendingWatchOriginalPath.isEmpty()) {
                base.originalImageUrl=pendingWatchOriginalPath;
                base.representativeImageUrl=pendingWatchRestoreOriginal?pendingWatchOriginalPath:
                        (pendingWatchProcessedPath.isEmpty()?pendingWatchOriginalPath:pendingWatchProcessedPath);
                base.calendarImageUrl=pendingWatchCalendarPath.isEmpty()
                        ? safeText(base.representativeImageUrl)
                        : pendingWatchCalendarPath;
                base.imageSourceUrl=pendingWatchRestoreOriginal?"manual-original":"manual-normalized";
            } else if(pendingWatchRestoreOriginal && base.originalImageUrl!=null && !base.originalImageUrl.isEmpty()) {
                base.representativeImageUrl=base.originalImageUrl;
                if(base.calendarImageUrl==null || base.calendarImageUrl.isEmpty()) {
                    base.calendarImageUrl = ImageLoader.ensureCalendarWatchImage(this, base.originalImageUrl, base.representativeImageUrl);
                }
                base.imageSourceUrl="manual-original";
            }

            if(pendingCasebackImageClear) {
                base.casebackImageUrl="";
                base.casebackOriginalImageUrl="";
            } else if(!pendingCasebackOriginalPath.isEmpty()) {
                base.casebackOriginalImageUrl=pendingCasebackOriginalPath;
                base.casebackImageUrl=pendingCasebackRestoreOriginal?pendingCasebackOriginalPath:
                        (pendingCasebackProcessedPath.isEmpty()?pendingCasebackOriginalPath:pendingCasebackProcessedPath);
            } else if(pendingCasebackRestoreOriginal && base.casebackOriginalImageUrl!=null && !base.casebackOriginalImageUrl.isEmpty()) {
                base.casebackImageUrl=base.casebackOriginalImageUrl;
            }
            long savedId=db.saveWatch(base);
            CalendarWidgetProvider.updateAll(this);
            pendingWatchPreview=null;
            pendingWatchPhotoStatus=null;
            pendingCasebackPreview=null;
            pendingCasebackPhotoStatus=null;
            dlg.dismiss();
            focusedWatchId=savedId;
            WarrantyNotifier.scheduleNextCheck(this);
            showCollection();
        }));
        dlg.show();
        if(sharedImport!=null && !val(model).isEmpty()) {
            lookup.postDelayed(lookup::performClick,300);
        }
    }

    // ACCURACY / WATCH ACCURACY METER ---------------------------------------

    // 오차 측정용 시계 목록은 전체 착용 횟수가 많은 순서로 정렬합니다.
    private List<Watch> getWatchesOrderedByWear() {
        List<Watch> watches = new ArrayList<>(db.getWatches());
        Map<Long,Double> wear = db.getAllTimeWearScores();
        watches.sort((a,b)->{
            double wa = wear.containsKey(a.id) ? wear.get(a.id) : 0.0;
            double wb = wear.containsKey(b.id) ? wear.get(b.id) : 0.0;
            int byWear = Double.compare(wb, wa);
            if(byWear != 0) return byWear;
            int byBrand = safeText(a.brand).compareToIgnoreCase(safeText(b.brand));
            if(byBrand != 0) return byBrand;
            return safeText(a.modelName).compareToIgnoreCase(safeText(b.modelName));
        });
        return watches;
    }

    private void showAccuracy() {
        selectNav(navAccuracy);
        LinearLayout p=page();
        p.addView(UiKit.eyebrow(this,"MEASUREMENT"));
        p.addView(UiKit.text(this,"측정",27,true));
        p.addView(UiKit.spacer(this,8));

        DatabaseHelper.PowerReserveRow active=db.getActivePowerReserveMeasurement();
        if(active!=null) {
            LinearLayout banner=UiKit.card(this);
            banner.setBackground(UiKit.rounded(UiKit.NAVY,UiKit.GOLD,16,this));
            TextView running=UiKit.text(this,active.watchName+" 파워리저브 측정 중",16,true);
            running.setTextColor(Color.WHITE);
            banner.addView(running);
            TextView elapsed=UiKit.muted(this,"시작 "+formatMeasurementTime(active.startAt)+" · 경과 "+formatElapsedHours(active.startAt,System.currentTimeMillis()),11);
            elapsed.setTextColor(Color.rgb(205,211,219));
            banner.addView(elapsed);
            banner.setOnClickListener(v->{ measurementMode=1; showAccuracy(); });
            p.addView(banner);
        }

        LinearLayout tabs=UiKit.row(this);
        Button accuracyTab=UiKit.chip(this,"오차 측정",measurementMode==0);
        Button reserveTab=UiKit.chip(this,"파워리저브 측정",measurementMode==1);
        accuracyTab.setOnClickListener(v->{ if(measurementMode!=0){measurementMode=0;showAccuracy();} });
        reserveTab.setOnClickListener(v->{ if(measurementMode!=1){measurementMode=1;showAccuracy();} });
        LinearLayout.LayoutParams leftLp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        leftLp.setMargins(0,0,UiKit.dp(this,5),0);
        LinearLayout.LayoutParams rightLp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        rightLp.setMargins(UiKit.dp(this,5),0,0,0);
        tabs.addView(accuracyTab,leftLp);
        tabs.addView(reserveTab,rightLp);
        p.addView(tabs);
        p.addView(UiKit.spacer(this,12));

        if(measurementMode==0) addAccuracyMeasurementContent(p);
        else addPowerReserveMeasurementContent(p);
        setScrollable(p);
    }

    private void addAccuracyMeasurementContent(LinearLayout p) {
        List<Watch>watches=getWatchesOrderedByWear();
        if(watches.isEmpty()) {
            p.addView(emptyCard("먼저 시계를 등록하세요","Watch Accuracy Meter 측정값은 컬렉션의 시계와 연결되어 자세별로 저장됩니다."));
            return;
        }

        LinearLayout overview=UiKit.card(this);
        overview.addView(UiKit.eyebrow(this,"RATE OVERVIEW"));
        overview.addView(UiKit.text(this,"전체 시계 오차 평균",18,true));
        overview.addView(rateOverviewGraph(watches));
        p.addView(overview);

        LinearLayout setup=UiKit.card(this);
        setup.addView(UiKit.eyebrow(this,"MEASUREMENT SETUP"));
        setup.addView(UiKit.label(this,"Watch"));
        accuracyWatchSpinner=watchSpinnerWithNone(watches,"— 시계를 선택하세요 —");
        int initialSelection=0;
        if(accuracySelectedWatchId>0) {
            int idx=indexOfWatch(watches,accuracySelectedWatchId);
            if(idx>=0) initialSelection=idx+1;
            else accuracySelectedWatchId=-1;
        }
        accuracyWatchSpinner.setSelection(initialSelection);
        setup.addView(accuracyWatchSpinner);
        p.addView(setup);

        LinearLayout wamCard=UiKit.card(this);
        wamCard.addView(UiKit.eyebrow(this,"WATCH ACCURACY METER"));
        wamCard.addView(UiKit.text(this,"Watch Accuracy Meter",18,true));
        wamCard.addView(UiKit.spacer(this,8));
        LinearLayout wamButtons=UiKit.row(this);
        Button openWam=UiKit.primaryButton(this,"Watch Accuracy Meter로 측정");
        Button enterWam=UiKit.secondaryButton(this,"측정 결과 입력");
        wamButtons.addView(openWam,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        wamButtons.addView(enterWam,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        wamCard.addView(wamButtons);
        p.addView(wamCard);

        LinearLayout positionContainer=UiKit.column(this);
        p.addView(positionContainer);

        LinearLayout recentContainer=UiKit.column(this);
        p.addView(recentContainer);

        boolean hasInitial=accuracySelectedWatchId>0;
        openWam.setEnabled(hasInitial);
        enterWam.setEnabled(hasInitial);
        refreshPositionSummary(positionContainer,accuracySelectedWatchId);
        refreshRecentAccuracyRecords(recentContainer,accuracySelectedWatchId);

        openWam.setOnClickListener(v->{
            int pos=accuracyWatchSpinner==null?-1:accuracyWatchSpinner.getSelectedItemPosition();
            if(pos<=0||pos>watches.size()) {
                Toast.makeText(this,"먼저 시계를 선택하세요.",Toast.LENGTH_SHORT).show();
                return;
            }
            Watch w=watches.get(pos-1);
            accuracySelectedWatchId=w.id;
            openWatchAccuracyMeter(w.id);
        });
        enterWam.setOnClickListener(v->{
            int pos=accuracyWatchSpinner==null?-1:accuracyWatchSpinner.getSelectedItemPosition();
            if(pos<=0||pos>watches.size()) {
                Toast.makeText(this,"먼저 시계를 선택하세요.",Toast.LENGTH_SHORT).show();
                return;
            }
            Watch w=watches.get(pos-1);
            accuracySelectedWatchId=w.id;
            showWatchAccuracyMeterResultDialog(w.id);
        });

        accuracyWatchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(position<=0) {
                    accuracySelectedWatchId=-1;
                    openWam.setEnabled(false);
                    enterWam.setEnabled(false);
                } else if(position-1<watches.size()) {
                    accuracySelectedWatchId=watches.get(position-1).id;
                    openWam.setEnabled(true);
                    enterWam.setEnabled(true);
                }
                refreshPositionSummary(positionContainer,accuracySelectedWatchId);
                refreshRecentAccuracyRecords(recentContainer,accuracySelectedWatchId);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {
                accuracySelectedWatchId=-1;
                openWam.setEnabled(false);
                enterWam.setEnabled(false);
                refreshPositionSummary(positionContainer,-1);
                refreshRecentAccuracyRecords(recentContainer,-1);
            }
        });
    }

    private List<Watch> getPowerReserveEligibleWatches() {
        List<Watch> out=new ArrayList<>();
        for(Watch w:getWatchesOrderedByWear()) {
            String power=normalizePower(w.movement);
            if("Automatic".equals(power)||"Mechanical".equals(power)) out.add(w);
        }
        return out;
    }

    private void addPowerReserveMeasurementContent(LinearLayout p) {
        List<Watch> eligible=getPowerReserveEligibleWatches();
        DatabaseHelper.PowerReserveRow active=db.getActivePowerReserveMeasurement();

        if(active==null) {
            LinearLayout setup=UiKit.card(this);
            setup.addView(UiKit.eyebrow(this,"POWER RESERVE"));
            setup.addView(UiKit.text(this,"파워리저브 측정 시작",18,true));
            setup.addView(UiKit.spacer(this,7));
            setup.addView(UiKit.muted(this,"Automatic 또는 Mechanical 시계만 선택할 수 있습니다.",11));
            setup.addView(UiKit.spacer(this,8));
            if(eligible.isEmpty()) {
                setup.addView(UiKit.muted(this,"측정 가능한 기계식 시계가 없습니다.",12));
            } else {
                setup.addView(UiKit.label(this,"시계"));
                Spinner watch=watchSpinnerWithNone(eligible,"— 측정할 시계 선택 —");
                setup.addView(watch);
                setup.addView(UiKit.spacer(this,8));
                setup.addView(UiKit.label(this,"측정 시작 시 와인딩 상태"));
                Spinner winding=windingStateSpinner("완전 와인딩");
                setup.addView(winding);
                setup.addView(UiKit.spacer(this,10));
                Button start=UiKit.primaryButton(this,"측정 시작");
                start.setOnClickListener(v->{
                    int pos=watch.getSelectedItemPosition();
                    if(pos<=0||pos>eligible.size()) {
                        Toast.makeText(this,"측정할 시계를 선택하세요.",Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Watch selected=eligible.get(pos-1);
                    long id=db.startPowerReserveMeasurement(selected.id,String.valueOf(winding.getSelectedItem()));
                    if(id<=0) Toast.makeText(this,"이미 진행 중인 측정이 있습니다.",Toast.LENGTH_LONG).show();
                    else {
                        Toast.makeText(this,selected.modelName+" 측정을 시작했습니다.",Toast.LENGTH_SHORT).show();
                        showAccuracy();
                    }
                });
                setup.addView(start);
            }
            p.addView(setup);
        } else {
            LinearLayout running=UiKit.card(this);
            running.addView(UiKit.eyebrow(this,"MEASURING"));
            running.addView(UiKit.text(this,active.watchName,20,true));
            running.addView(UiKit.spacer(this,6));
            running.addView(metricLine("시작 시각",formatMeasurementTime(active.startAt)));
            running.addView(metricLine("현재 경과",formatElapsedHours(active.startAt,System.currentTimeMillis())));
            running.addView(metricLine("와인딩 상태",active.windingState.isEmpty()?"미지정":active.windingState));
            running.addView(UiKit.spacer(this,9));
            LinearLayout actions=UiKit.row(this);
            Button finish=UiKit.primaryButton(this,"측정 종료");
            Button cancel=UiKit.secondaryButton(this,"측정 취소");
            actions.addView(finish,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            actions.addView(cancel,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            finish.setOnClickListener(v->showFinishPowerReserveDialog(active));
            cancel.setOnClickListener(v->new AlertDialog.Builder(this)
                    .setTitle("파워리저브 측정 취소")
                    .setMessage("진행 중인 측정을 저장하지 않고 취소할까요?")
                    .setNegativeButton("계속 측정",null)
                    .setPositiveButton("취소",(d,x)->{db.cancelPowerReserveMeasurement(active.id);showAccuracy();})
                    .show());
            running.addView(actions);
            p.addView(running);
        }

        LinearLayout recent=UiKit.card(this);
        recent.addView(UiKit.eyebrow(this,"HISTORY"));
        recent.addView(UiKit.text(this,"파워리저브 측정 기록",18,true));
        recent.addView(UiKit.spacer(this,7));
        List<DatabaseHelper.PowerReserveRow> rows=db.getPowerReserveMeasurements(30);
        if(rows.isEmpty()) {
            recent.addView(UiKit.muted(this,"완료된 측정 기록이 없습니다.",12));
        } else {
            for(DatabaseHelper.PowerReserveRow row:rows) recent.addView(powerReserveRecordRow(row));
        }
        p.addView(recent);
    }

    private Spinner windingStateSpinner(String current) {
        Spinner sp=new Spinner(this);
        String[] items={"완전 와인딩","부분 와인딩","착용 직후","와인딩 상태 미상"};
        sp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,items));
        decorateSpinner(sp);
        for(int i=0;i<items.length;i++) if(items[i].equals(current)){sp.setSelection(i);break;}
        return sp;
    }

    private void showFinishPowerReserveDialog(DatabaseHelper.PowerReserveRow active) {
        long now=System.currentTimeMillis();
        double measured=Math.max(0.0,(now-active.startAt)/3600000.0);
        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,8));
        body.addView(UiKit.muted(this,"측정 시작부터 종료까지의 경과 시간을 최소 파워리저브로 입력했습니다. 필요한 경우 수정하세요.",11));
        body.addView(UiKit.spacer(this,8));
        body.addView(UiKit.label(this,"와인딩 상태"));
        Spinner winding=windingStateSpinner(active.windingState);
        body.addView(winding);
        EditText minimum=addField(body,"최소 파워리저브 (h)","예: 72.5",String.format(Locale.US,"%.1f",measured));
        minimum.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText note=addField(body,"메모","예: 초침 정지 확인, 착용 없이 보관","");
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(active.watchName+" 측정 종료")
                .setView(body)
                .setNegativeButton("취소",null)
                .setPositiveButton("저장",null)
                .create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            double hours;
            try { hours=Double.parseDouble(val(minimum)); }
            catch(Exception e) { minimum.setError("시간을 숫자로 입력하세요."); return; }
            if(hours<0) { minimum.setError("0 이상이어야 합니다."); return; }
            db.finishPowerReserveMeasurement(active.id,System.currentTimeMillis(),String.valueOf(winding.getSelectedItem()),hours,val(note));
            dialog.dismiss();
            Toast.makeText(this,"파워리저브 측정 결과를 저장했습니다.",Toast.LENGTH_SHORT).show();
            showAccuracy();
        }));
        dialog.show();
    }

    private View powerReserveRecordRow(DatabaseHelper.PowerReserveRow row) {
        LinearLayout box=UiKit.column(this);
        box.setPadding(0,UiKit.dp(this,7),0,UiKit.dp(this,7));
        LinearLayout head=UiKit.row(this);
        LinearLayout names=UiKit.column(this);
        names.addView(UiKit.text(this,row.watchName,13,true));
        names.addView(UiKit.muted(this,formatMeasurementTime(row.endAt)+" · "+row.windingState,10));
        head.addView(names,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView hours=UiKit.text(this,String.format(Locale.KOREA,"%.1f h",row.minimumHours),16,true);
        hours.setTextColor(UiKit.GOLD);
        head.addView(hours);
        Button del=UiKit.secondaryButton(this,"삭제");
        LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(UiKit.dp(this,58),ViewGroup.LayoutParams.WRAP_CONTENT);
        dp.setMargins(UiKit.dp(this,7),0,0,0);
        head.addView(del,dp);
        del.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("측정 기록 삭제")
                .setMessage(row.watchName+"의 파워리저브 기록을 삭제할까요?")
                .setNegativeButton("취소",null)
                .setPositiveButton("삭제",(d,x)->{db.deletePowerReserveMeasurement(row.id);showAccuracy();})
                .show());
        box.addView(head);
        if(!row.note.isEmpty()) box.addView(UiKit.muted(this,row.note,10));
        return box;
    }

    private String formatMeasurementTime(long millis) {
        if(millis<=0) return "—";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.KOREA).format(new Date(millis));
    }

    private String formatElapsedHours(long start,long end) {
        long diff=Math.max(0L,end-start);
        long hours=diff/(60L*60L*1000L);
        long minutes=(diff/(60L*1000L))%60L;
        return hours+"시간 "+minutes+"분";
    }

    private void refreshRecentAccuracyRecords(LinearLayout container,long watchId) {
        container.removeAllViews();
        container.addView(UiKit.text(this,"최근 측정",18,true));
        container.addView(UiKit.spacer(this,7));
        if(watchId<=0) {
            container.addView(emptyCard("시계를 선택하세요","선택한 시계의 최근 측정 기록만 표시됩니다."));
            return;
        }
        List<DatabaseHelper.TimegrapherRow> rows=db.getTimegrapherRowsForWatch(watchId,24);
        if(rows.isEmpty()) {
            Watch w=db.getWatch(watchId);
            String name=w==null?"선택한 시계":w.modelName;
            container.addView(emptyCard("아직 측정 기록이 없습니다",name+"의 Watch Accuracy Meter 측정값을 저장하면 여기에 표시됩니다."));
            return;
        }
        for(DatabaseHelper.TimegrapherRow r:rows) container.addView(timegrapherRecordCard(r));
    }

    private void openWatchAccuracyMeter(long watchId) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(WATCH_ACCURACY_METER_PACKAGE);
        if (launch != null) {
            pendingWatchAccuracyMeterImport = true;
            pendingWatchAccuracyMeterWatchId = watchId;
            try {
                startActivity(launch);
                return;
            } catch (Exception ignored) {
                pendingWatchAccuracyMeterImport = false;
                pendingWatchAccuracyMeterWatchId = -1;
            }
        }

        Toast.makeText(this,"Watch Accuracy Meter가 설치되어 있지 않아 Play Store를 엽니다.",Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + WATCH_ACCURACY_METER_PACKAGE)));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + WATCH_ACCURACY_METER_PACKAGE)));
        }
    }

    private void showWatchAccuracyMeterResultDialog(long defaultWatchId) {
        List<Watch> watches = getWatchesOrderedByWear();
        if (watches.isEmpty()) {
            Toast.makeText(this,"먼저 시계를 등록하세요.",Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout f=UiKit.column(this);
        f.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,8));
        f.addView(UiKit.muted(this,"Watch Accuracy Meter 화면에 표시된 값을 그대로 입력하세요. 이 기록도 자세별 평균과 시계별 오차 팝업에 함께 반영됩니다.",11));
        f.addView(UiKit.spacer(this,10));

        f.addView(UiKit.label(this,"시계"));
        Spinner watch=watchSpinner(watches);
        int defaultIndex=indexOfWatch(watches,defaultWatchId);
        if(defaultIndex>=0) watch.setSelection(defaultIndex);
        f.addView(watch);
        f.addView(UiKit.spacer(this,8));

        EditText bph=UiKit.field(this,"예: 21600");
        bph.setInputType(InputType.TYPE_CLASS_NUMBER);
        f.addView(UiKit.label(this,"BPH"));
        f.addView(bph);
        f.addView(UiKit.muted(this,"일반 후보: 18,000 / 19,800 / 21,600 / 25,200 / 28,800 / 36,000",10));

        EditText rate=UiKit.field(this,"예: +4.2 또는 -3.1");
        rate.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        f.addView(UiKit.label(this,"Rate (s/day)"));
        f.addView(rate);

        EditText beat=UiKit.field(this,"예: 0.3");
        beat.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        f.addView(UiKit.label(this,"Beat Error (ms)"));
        f.addView(beat);

        EditText amp=UiKit.field(this,"예: 268 · 표시되지 않으면 비워두기");
        amp.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        f.addView(UiKit.label(this,"Amplitude (°) · 선택"));
        f.addView(amp);

        f.addView(UiKit.spacer(this,8));
        f.addView(UiKit.label(this,"측정 자세"));
        RadioGroup positions=new RadioGroup(this);
        positions.setOrientation(RadioGroup.VERTICAL);
        String[][] positionItems={
                {"Dial up","다이얼 위 (Dial up)"},
                {"Dial down","다이얼 아래 (Dial down)"},
                {"12 Up","12 Up"},
                {"9 Up","9 Up"},
                {"6 Up","6 Up"},
                {"3 Up","3 Up"}
        };
        for(int i=0;i<positionItems.length;i++) {
            RadioButton rb=new RadioButton(this);
            rb.setId(5100+i);
            String key=positionItems[i][0];
            rb.setText(positionItems[i][1]);
            rb.setTag(key);
            rb.setTextColor(UiKit.INK);
            rb.setCompoundDrawablesWithIntrinsicBounds(positionIconRes(key),0,0,0);
            rb.setCompoundDrawablePadding(UiKit.dp(this,10));
            rb.setPadding(0,UiKit.dp(this,5),0,UiKit.dp(this,5));
            positions.addView(rb);
        }
        positions.check(5100);
        f.addView(positions);

        EditText date=UiKit.field(this,"날짜");
        date.setText(dateFormat.format(new Date()));
        date.setFocusable(false);
        date.setOnClickListener(v->pickDate(val(date),date::setText));
        f.addView(UiKit.label(this,"측정 날짜"));
        f.addView(date);

        EditText note=UiKit.field(this,"예: 완전 감기 / 측정 조건");
        f.addView(UiKit.label(this,"메모"));
        f.addView(note);

        ScrollView sc=new ScrollView(this);
        sc.addView(f);
        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle("Watch Accuracy Meter 결과 저장")
                .setView(sc)
                .setNegativeButton("취소",null)
                .setPositiveButton("저장",null)
                .create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int pos=watch.getSelectedItemPosition();
            if(pos<0||pos>=watches.size()) return;

            int bphValue;
            double rateValue, beatValue, ampValue=Double.NaN;
            try {
                bphValue=Integer.parseInt(val(bph).replace(",",""));
                if(bphValue<10000||bphValue>50000) throw new IllegalArgumentException();
            } catch(Exception e) {
                bph.setError("10,000~50,000 범위의 BPH를 입력하세요.");
                return;
            }
            try {
                rateValue=Double.parseDouble(val(rate));
                if(rateValue<-999||rateValue>999) throw new IllegalArgumentException();
            } catch(Exception e) {
                rate.setError("Rate를 초/일 단위 숫자로 입력하세요.");
                return;
            }
            try {
                beatValue=Double.parseDouble(val(beat));
                if(beatValue<0||beatValue>20) throw new IllegalArgumentException();
            } catch(Exception e) {
                beat.setError("0~20 ms 범위로 입력하세요.");
                return;
            }
            if(!val(amp).isEmpty()) {
                try {
                    ampValue=Double.parseDouble(val(amp));
                    if(ampValue<50||ampValue>500) throw new IllegalArgumentException();
                } catch(Exception e) {
                    amp.setError("Amplitude는 50~500° 범위로 입력하거나 비워두세요.");
                    return;
                }
            }

            RadioButton rb=positions.findViewById(positions.getCheckedRadioButtonId());
            String position=rb==null?"Dial up":String.valueOf(rb.getTag());
            Watch w=watches.get(pos);
            db.addWatchAccuracyMeterRecord(w.id,val(date),bphValue,rateValue,beatValue,ampValue,position,val(note));
            accuracySelectedWatchId=w.id;
            dlg.dismiss();
            Toast.makeText(this,"Watch Accuracy Meter 측정값을 저장했습니다.",Toast.LENGTH_SHORT).show();
            showAccuracy();
        }));
        dlg.show();
    }

    private View metricLine(String label,String value) {
        LinearLayout row=UiKit.row(this);
        row.addView(UiKit.text(this,label,14,false),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView v=UiKit.text(this,value,15,true); v.setTextColor(UiKit.NAVY);
        row.addView(v);
        row.setPadding(0,UiKit.dp(this,3),0,UiKit.dp(this,3));
        return row;
    }

    private void refreshPositionSummary(LinearLayout container,long watchId) {
        container.removeAllViews();
        if(watchId<=0) {
            container.addView(emptyCard("시계를 선택하세요","선택한 시계의 자세별 평균과 전체 평균이 표시됩니다."));
            return;
        }
        List<DatabaseHelper.PositionAverage> rows=db.getPositionAveragesForWatch(watchId);
        LinearLayout card=UiKit.card(this);
        card.addView(UiKit.eyebrow(this,"POSITIONAL RATE"));
        Watch w=db.getWatch(watchId);
        card.addView(UiKit.text(this,(w==null?"시계":w.modelName)+" · 자세별 평균",17,true));
        card.addView(UiKit.muted(this,"같은 자세로 여러 번 측정하면 저장된 전체 측정값의 평균으로 표시됩니다.",11));
        double meanPositionRate=averagePositionRates(rows);
        int measuredPositions=countPositionRates(rows);
        if(!Double.isNaN(meanPositionRate)) {
            TextView mean=UiKit.text(this,String.format(Locale.KOREA,"자세 평균 Rate %+.2f s/day · %d/6 자세",meanPositionRate,measuredPositions),12,true);
            mean.setTextColor(UiKit.GOLD);
            card.addView(UiKit.spacer(this,5));
            card.addView(mean);
        }
        card.addView(UiKit.spacer(this,6));
        String[] positions={"Dial up","Dial down","12 Up","9 Up","6 Up","3 Up"};
        for(String p:positions) {
            DatabaseHelper.PositionAverage found=null;
            for(DatabaseHelper.PositionAverage r:rows) if(p.equals(r.position)){found=r;break;}
            LinearLayout line=UiKit.row(this);
            TextView name=positionLabelView(p,12,true);
            line.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            String value;
            if(found==null) {
                value="—";
            } else {
                String amp=Double.isNaN(found.avgAmplitudeDeg)?"":" · "+String.format(Locale.KOREA,"%.0f°",found.avgAmplitudeDeg);
                value=String.format(Locale.KOREA,"%+.1f s/d · %.2f ms%s · 측정횟수 %d",
                        found.avgRateSecDay,found.avgBeatErrorMs,amp,found.count);
            }
            TextView val=UiKit.text(this,value,11,found!=null);
            val.setTextColor(found==null?UiKit.MUTED:UiKit.NAVY_2);
            line.addView(val);
            line.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,4));
            card.addView(line);
        }

        LinearLayout overallLine=UiKit.row(this);
        ImageView overallIcon=watchThumb(w==null?"":w.representativeImageUrl,34);
        LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(UiKit.dp(this,34),UiKit.dp(this,34));
        iconLp.setMargins(0,UiKit.dp(this,3),UiKit.dp(this,8),UiKit.dp(this,3));
        overallLine.addView(overallIcon,iconLp);
        TextView overallName=UiKit.text(this,"전체 평균",12,true);
        overallLine.addView(overallName,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        double overallRate=watchId>0?db.averageTimegrapherRateForWatch(watchId):Double.NaN;
        double overallBeat=watchId>0?db.averageBeatErrorForWatch(watchId):Double.NaN;
        double overallAmp=watchId>0?db.averageAmplitudeForWatch(watchId):Double.NaN;
        int overallCount=watchId>0?db.getTimegrapherRecordCountForWatch(watchId):0;
        String overallValue="—";
        if(!Double.isNaN(overallRate)) {
            String beatText=Double.isNaN(overallBeat)?"":" · "+String.format(Locale.KOREA,"%.2f ms",overallBeat);
            String ampText=Double.isNaN(overallAmp)?"":" · "+String.format(Locale.KOREA,"%.0f°",overallAmp);
            overallValue=String.format(Locale.KOREA,"%+.1f s/d",overallRate)+beatText+ampText+" · 측정횟수 "+overallCount;
        }
        TextView overallVal=UiKit.text(this,overallValue,11,!Double.isNaN(overallRate));
        overallVal.setTextColor(Double.isNaN(overallRate)?UiKit.MUTED:UiKit.GOLD);
        overallLine.addView(overallVal);
        overallLine.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,4));
        card.addView(overallLine);

        Button detail=UiKit.secondaryButton(this,"오차 한눈에 보기");
        detail.setOnClickListener(v->{ Watch watch=db.getWatch(watchId); if(watch!=null) showWatchAccuracyPopup(watch); });
        card.addView(UiKit.spacer(this,7));
        card.addView(detail);
        container.addView(card);
    }

    private void showAllWatchAccuracyPopup() {
        List<Watch> watches=db.getWatches();
        Map<Long,Double> wearScores=db.getAllTimeWearScores();
        watches.sort((a,b)->{
            double ra=db.averageTimegrapherRateForWatch(a.id);
            double rb=db.averageTimegrapherRateForWatch(b.id);
            boolean ma=Double.isNaN(ra), mb=Double.isNaN(rb);
            if(ma!=mb) return ma?1:-1;
            if(!ma) {
                int byError=Double.compare(Math.abs(ra),Math.abs(rb));
                if(byError!=0) return byError;
                int bySigned=Double.compare(ra,rb);
                if(bySigned!=0) return bySigned;
            } else {
                double wa=wearScores.containsKey(a.id)?wearScores.get(a.id):0.0;
                double wb=wearScores.containsKey(b.id)?wearScores.get(b.id):0.0;
                int byWear=Double.compare(wb,wa);
                if(byWear!=0) return byWear;
            }
            int byBrand=safeText(a.brand).compareToIgnoreCase(safeText(b.brand));
            if(byBrand!=0) return byBrand;
            return safeText(a.modelName).compareToIgnoreCase(safeText(b.modelName));
        });

        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,14),UiKit.dp(this,8),UiKit.dp(this,14),UiKit.dp(this,12));
        body.addView(rateOverviewGraph(watches));
        body.addView(UiKit.spacer(this,8));
        body.addView(UiKit.muted(this,"Rate 기록이 있는 시계는 |Rate|가 작은 순서로 정렬합니다. 기록이 없는 시계는 뒤에 배치하고, 그 안에서는 착용 횟수가 많은 순서입니다.",11));
        body.addView(UiKit.spacer(this,8));

        for(Watch w:watches) {
            LinearLayout card=UiKit.card(this);
            LinearLayout head=UiKit.row(this);
            ImageView image=watchThumb(w.representativeImageUrl,58);
            head.addView(image,new LinearLayout.LayoutParams(UiKit.dp(this,58),UiKit.dp(this,58)));
            LinearLayout info=UiKit.column(this);
            info.setPadding(UiKit.dp(this,10),0,0,0);
            info.addView(UiKit.eyebrow(this,w.brand.isEmpty()?"WATCH":w.brand));
            info.addView(UiKit.text(this,w.modelName,15,true));
            double overall=db.averageTimegrapherRateForWatch(w.id);
            List<DatabaseHelper.PositionAverage> positions=db.getPositionAveragesForWatch(w.id);
            double positionalAverage=averagePositionRates(positions);
            int positionCount=countPositionRates(positions);
            TextView rate=UiKit.text(this,Double.isNaN(overall)?"Rate 평균 —":String.format(Locale.KOREA,"Rate 평균 %+.2f s/day",overall),15,true);
            rate.setTextColor(UiKit.GOLD);
            info.addView(rate);
            info.addView(UiKit.muted(this,Double.isNaN(positionalAverage)?"자세 평균 —":String.format(Locale.KOREA,"자세 평균 %+.2f s/day · %d/6 자세",positionalAverage,positionCount),11));
            head.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            card.addView(head);
            card.addView(UiKit.spacer(this,7));

            String[] order={"Dial up","Dial down","12 Up","9 Up","6 Up","3 Up"};
            for(String pos:order) {
                DatabaseHelper.PositionAverage found=findPositionAverage(positions,pos);
                LinearLayout line=UiKit.row(this);
                line.addView(positionLabelView(pos,11,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
                String value=found==null?"—":String.format(Locale.KOREA,"%+.2f s/d",found.avgRateSecDay);
                TextView v=UiKit.text(this,value,11,found!=null);
                v.setTextColor(found==null?UiKit.MUTED:UiKit.NAVY_2);
                line.addView(v);
                line.setPadding(0,UiKit.dp(this,2),0,UiKit.dp(this,2));
                card.addView(line);
            }
            card.setOnClickListener(v->showWatchAccuracyPopup(w));
            body.addView(card);
        }

        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        new AlertDialog.Builder(this)
                .setTitle("전체 시계 Rate 평균")
                .setView(scroll)
                .setPositiveButton("닫기",null)
                .show();
    }

    private View rateOverviewGraph(List<Watch> watches) {
        LinearLayout card=UiKit.column(this);
        card.addView(UiKit.spacer(this,6));

        double maxAbs=1.0;
        for(Watch w:watches) {
            double r=db.averageTimegrapherRateForWatch(w.id);
            if(!Double.isNaN(r)) maxAbs=Math.max(maxAbs,Math.abs(r));
        }

        // 그래프는 이름 옆의 좁은 고정폭 칸이 아니라, 각 항목의 아래 전체 폭을 사용합니다.
        for(Watch w:watches) {
            double r=db.averageTimegrapherRateForWatch(w.id);
            LinearLayout item=UiKit.column(this);
            item.setPadding(0,UiKit.dp(this,5),0,UiKit.dp(this,7));

            LinearLayout head=UiKit.row(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            ImageView image=watchThumb(w.representativeImageUrl,30);
            head.addView(image,new LinearLayout.LayoutParams(UiKit.dp(this,30),UiKit.dp(this,30)));

            TextView name=UiKit.text(this,w.modelName,11,true);
            name.setMaxLines(1);
            LinearLayout.LayoutParams nameLp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
            nameLp.setMargins(UiKit.dp(this,7),0,UiKit.dp(this,8),0);
            head.addView(name,nameLp);

            TextView rate=UiKit.text(this,Double.isNaN(r)?"—":String.format(Locale.KOREA,"%+.1f s/d",r),11,true);
            rate.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
            rate.setTextColor(Double.isNaN(r)?UiKit.MUTED:UiKit.NAVY);
            head.addView(rate,new LinearLayout.LayoutParams(UiKit.dp(this,62),ViewGroup.LayoutParams.WRAP_CONTENT));
            item.addView(head);

            FrameLayout plot=new FrameLayout(this);
            LinearLayout.LayoutParams plotLp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,42));
            plotLp.setMargins(0,UiKit.dp(this,4),0,0);
            plot.setLayoutParams(plotLp);

            View axis=new View(this);
            axis.setBackgroundColor(UiKit.LINE);
            FrameLayout.LayoutParams axisLp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,1));
            axisLp.gravity=Gravity.CENTER_VERTICAL;
            plot.addView(axis,axisLp);

            // 폭이 확정된 뒤 중앙선과 막대를 배치해 화면 폭을 최대한 사용합니다.
            final double rateValue=r;
            final double scaleMax=maxAbs;
            plot.post(() -> {
                int width=plot.getWidth();
                if(width<=0) return;
                int half=width/2;

                View zero=new View(this);
                zero.setBackgroundColor(UiKit.MUTED);
                FrameLayout.LayoutParams zlp=new FrameLayout.LayoutParams(UiKit.dp(this,1),UiKit.dp(this,34));
                zlp.leftMargin=Math.max(0,half);
                zlp.gravity=Gravity.CENTER_VERTICAL;
                plot.addView(zero,zlp);

                if(!Double.isNaN(rateValue)) {
                    int usable=Math.max(UiKit.dp(this,12),half-UiKit.dp(this,6));
                    int barWidth=Math.max(UiKit.dp(this,3),(int)Math.round((Math.abs(rateValue)/scaleMax)*usable));
                    View bar=new View(this);
                    bar.setBackground(UiKit.rounded(rateValue>=0?UiKit.GOLD:UiKit.NAVY_2,Color.TRANSPARENT,5,this));
                    FrameLayout.LayoutParams blp=new FrameLayout.LayoutParams(barWidth,UiKit.dp(this,12));
                    blp.leftMargin=rateValue>=0?half:Math.max(0,half-barWidth);
                    blp.gravity=Gravity.CENTER_VERTICAL;
                    plot.addView(bar,blp);
                }
            });
            item.addView(plot);
            item.setOnClickListener(v->showWatchAccuracyPopup(w));
            card.addView(item);
        }
        return card;
    }

    private DatabaseHelper.PositionAverage findPositionAverage(List<DatabaseHelper.PositionAverage> rows,String position) {
        for(DatabaseHelper.PositionAverage r:rows) if(position.equals(r.position)) return r;
        return null;
    }

    private double averagePositionRates(List<DatabaseHelper.PositionAverage> rows) {
        double sum=0; int n=0;
        for(DatabaseHelper.PositionAverage r:rows) {
            if(!Double.isNaN(r.avgRateSecDay)) { sum+=r.avgRateSecDay; n++; }
        }
        return n==0?Double.NaN:sum/n;
    }

    private int countPositionRates(List<DatabaseHelper.PositionAverage> rows) {
        int n=0;
        for(DatabaseHelper.PositionAverage r:rows) if(!Double.isNaN(r.avgRateSecDay)) n++;
        return n;
    }

    private void showWatchAccuracyPopup(Watch w) {
        if(w==null) return;
        final AlertDialog[] detailDialog=new AlertDialog[1];
        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,18),UiKit.dp(this,10),UiKit.dp(this,18),UiKit.dp(this,12));

        int count=db.getTimegrapherRecordCountForWatch(w.id);
        int wamCount=db.getTimegrapherRecordCountForWatchBySource(w.id,"watch_accuracy_meter");
        double avgRate=db.averageTimegrapherRateForWatch(w.id);
        double avgBeat=db.averageBeatErrorForWatch(w.id);
        double avgAmp=db.averageAmplitudeForWatch(w.id);
        List<DatabaseHelper.PositionAverage> positionRows=db.getPositionAveragesForWatch(w.id);
        double avgPositionRate=averagePositionRates(positionRows);
        int avgPositionCount=countPositionRates(positionRows);

        LinearLayout overall=UiKit.card(this);
        overall.addView(UiKit.eyebrow(this,"ACCURACY AT A GLANCE"));
        overall.addView(UiKit.text(this,(w.brand.isEmpty()?"":w.brand+" · ")+w.modelName,18,true));
        overall.addView(UiKit.spacer(this,7));
        overall.addView(metricLine("전체 평균 Rate",Double.isNaN(avgRate)?"—":String.format(Locale.KOREA,"%+.2f s/day",avgRate)));
        overall.addView(metricLine("자세 평균 Rate",Double.isNaN(avgPositionRate)?"—":String.format(Locale.KOREA,"%+.2f s/day · %d/6 자세",avgPositionRate,avgPositionCount)));
        overall.addView(metricLine("평균 Beat Error",Double.isNaN(avgBeat)?"—":String.format(Locale.KOREA,"%.2f ms",avgBeat)));
        overall.addView(metricLine("평균 Amplitude",Double.isNaN(avgAmp)?"—":String.format(Locale.KOREA,"%.0f°",avgAmp)));
        overall.addView(metricLine("저장된 측정",count+"회"));
        overall.addView(metricLine("Watch Accuracy Meter",wamCount+"회"));
        body.addView(overall);

        LinearLayout positional=UiKit.card(this);
        positional.addView(UiKit.eyebrow(this,"POSITIONAL AVERAGE"));
        positional.addView(UiKit.text(this,"자세별 평균 오차",16,true));
        positional.addView(UiKit.spacer(this,6));
        List<DatabaseHelper.PositionAverage> rows=positionRows;
        String[] positions={"Dial up","Dial down","12 Up","9 Up","6 Up","3 Up"};
        for(String p:positions) {
            DatabaseHelper.PositionAverage found=null;
            for(DatabaseHelper.PositionAverage r:rows) if(p.equals(r.position)){found=r;break;}
            LinearLayout line=UiKit.row(this);
            line.addView(positionLabelView(p,12,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            String value="—";
            if(found!=null) {
                value=String.format(Locale.KOREA,"%+.2f s/d",found.avgRateSecDay)+
                        " · "+String.format(Locale.KOREA,"%.2f ms",found.avgBeatErrorMs)+
                        " · 측정횟수 "+found.count;
            }
            TextView v=UiKit.text(this,value,11,found!=null);
            v.setTextColor(found==null?UiKit.MUTED:UiKit.NAVY_2);
            line.addView(v);
            line.setPadding(0,UiKit.dp(this,5),0,UiKit.dp(this,5));
            positional.addView(line);
        }

        LinearLayout overallPosLine=UiKit.row(this);
        ImageView overallPosImage=watchThumb(w.representativeImageUrl,34);
        LinearLayout.LayoutParams overallPosImageLp=new LinearLayout.LayoutParams(UiKit.dp(this,34),UiKit.dp(this,34));
        overallPosImageLp.setMargins(0,UiKit.dp(this,3),UiKit.dp(this,8),UiKit.dp(this,3));
        overallPosLine.addView(overallPosImage,overallPosImageLp);
        overallPosLine.addView(UiKit.text(this,"전체 평균",12,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        String overallPosValue=Double.isNaN(avgRate)?"—":String.format(Locale.KOREA,"%+.2f s/d · 측정횟수 %d",avgRate,count);
        TextView overallPosRate=UiKit.text(this,overallPosValue,11,!Double.isNaN(avgRate));
        overallPosRate.setTextColor(Double.isNaN(avgRate)?UiKit.MUTED:UiKit.GOLD);
        overallPosLine.addView(overallPosRate);
        overallPosLine.setPadding(0,UiKit.dp(this,5),0,UiKit.dp(this,5));
        positional.addView(overallPosLine);
        body.addView(positional);

        List<DatabaseHelper.TimegrapherRow> recent=db.getTimegrapherRowsForWatch(w.id,6);
        LinearLayout latest=UiKit.card(this);
        latest.addView(UiKit.eyebrow(this,"RECENT"));
        latest.addView(UiKit.text(this,"최근 측정",16,true));
        if(recent.isEmpty()) {
            latest.addView(UiKit.muted(this,"아직 저장된 오차 측정값이 없습니다.",12));
        } else {
            for(DatabaseHelper.TimegrapherRow r:recent) {
                LinearLayout line=UiKit.row(this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                String srcLabel="watch_accuracy_meter".equals(r.source)?"WAM":"기존 기록";
                TextView left=UiKit.text(this,r.date+" · "+positionLabel(r.position)+" · "+srcLabel,11,false);
                line.addView(left,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
                TextView rate=UiKit.text(this,String.format(Locale.KOREA,"%+.1f s/d",r.rateSecDay),12,true);
                rate.setTextColor(UiKit.NAVY_2);
                line.addView(rate);
                Button del=UiKit.secondaryButton(this,"삭제");
                LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(UiKit.dp(this,60),ViewGroup.LayoutParams.WRAP_CONTENT);
                dp.setMargins(UiKit.dp(this,6),0,0,0);
                line.addView(del,dp);
                del.setOnClickListener(v->confirmDeleteAccuracyRecord(r,()->{
                    if(detailDialog[0]!=null) detailDialog[0].dismiss();
                    showWatchAccuracyPopup(w);
                }));
                line.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,4));
                latest.addView(line);
            }
        }
        body.addView(latest);

        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        detailDialog[0]=new AlertDialog.Builder(this)
                .setTitle("시계별 오차")
                .setView(scroll)
                .setPositiveButton("닫기",null)
                .create();
        detailDialog[0].show();
    }

    private View timegrapherRecordCard(DatabaseHelper.TimegrapherRow r) {
        LinearLayout card=UiKit.card(this);
        LinearLayout head=UiKit.row(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names=UiKit.column(this);
        names.addView(UiKit.text(this,r.watchName,15,true));
        boolean fromWam="watch_accuracy_meter".equals(r.source);
        names.addView(UiKit.muted(this,r.date+" · "+positionLabel(r.position)+" · "+(fromWam?"Watch Accuracy Meter":"기존 기록"),11));
        head.addView(names,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView rate=UiKit.text(this,String.format(Locale.KOREA,"%+.1f s/d",r.rateSecDay),17,true);
        rate.setTextColor(r.rateSecDay>0?Color.rgb(154,87,64):UiKit.NAVY_2);
        head.addView(rate);
        Button del=UiKit.secondaryButton(this,"삭제");
        LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(UiKit.dp(this,62),ViewGroup.LayoutParams.WRAP_CONTENT);
        dp.setMargins(UiKit.dp(this,7),0,0,0);
        head.addView(del,dp);
        del.setOnClickListener(v->confirmDeleteAccuracyRecord(r,this::showAccuracy));
        card.addView(head);
        String amp=Double.isNaN(r.amplitudeDeg)?"-":String.format(Locale.KOREA,"%.0f°",r.amplitudeDeg);
        card.addView(UiKit.muted(this,String.format(Locale.KOREA,"%,d bph · Beat %.2f ms · Amp %s",
                r.bph,r.beatErrorMs,amp),11));
        if(!r.note.isEmpty()) card.addView(UiKit.muted(this,r.note,11));
        return card;
    }

    private void confirmDeleteAccuracyRecord(DatabaseHelper.TimegrapherRow r,Runnable afterDelete) {
        new AlertDialog.Builder(this)
                .setTitle("오차 측정 기록 삭제")
                .setMessage(r.watchName+" · "+r.date+" · "+positionLabel(r.position)+" 기록을 삭제할까요?")
                .setNegativeButton("취소",null)
                .setPositiveButton("삭제",(d,x)->{
                    db.deleteTimegrapherRecord(r.id);
                    Toast.makeText(this,"오차 측정 기록을 삭제했습니다.",Toast.LENGTH_SHORT).show();
                    if(afterDelete!=null) afterDelete.run();
                })
                .show();
    }

    private String positionLabel(String position) {
        if("Dial up".equals(position)) return "다이얼 위";
        if("Dial down".equals(position)) return "다이얼 아래";
        return position==null||position.isEmpty()?"자세 미지정":position;
    }

    // CALENDAR ---------------------------------------------------------------

    private void showCalendar() {
        selectNav(navCalendar);
        LinearLayout p=page();
        LinearLayout head=UiKit.row(this);
        LinearLayout h=UiKit.column(this);
        h.addView(UiKit.eyebrow(this,"WRIST CALENDAR"));
        h.addView(UiKit.text(this,"착용 캘린더",27,true));
        head.addView(h,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        p.addView(head);
        p.addView(UiKit.spacer(this,12));

        List<Watch>watches=db.getWatches();

        LinearLayout recommendationRow=UiKit.row(this);
        recommendationRow.setGravity(Gravity.TOP);
        View todayCard=buildTodayWatchRecommendationCard();
        LinearLayout rightRecommendationColumn=UiKit.column(this);
        Button outfitComplete=UiKit.primaryButton(this,"착장 추천");
        outfitComplete.setTextSize(12);
        outfitComplete.setPadding(UiKit.dp(this,8),UiKit.dp(this,5),UiKit.dp(this,8),UiKit.dp(this,5));
        outfitComplete.setOnClickListener(v->showOutfitCompletionDialog());
        rightRecommendationColumn.addView(outfitComplete,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        rightRecommendationColumn.addView(UiKit.spacer(this,6));
        rightRecommendationColumn.addView(buildForgottenWatchMiniCard(),new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams todayLp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        todayLp.setMargins(0,0,UiKit.dp(this,5),0);
        LinearLayout.LayoutParams rightLp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        rightLp.setMargins(UiKit.dp(this,5),0,0,0);
        recommendationRow.addView(todayCard,todayLp);
        recommendationRow.addView(rightRecommendationColumn,rightLp);
        p.addView(recommendationRow);
        p.addView(UiKit.spacer(this,10));

        // 캘린더와 동일한 전체 폭의 랭킹 카드. 좌우 버튼으로 이번 달/올해/누적을 순환합니다.
        p.addView(buildTopWearRankingCard());
        p.addView(UiKit.spacer(this,2));

        LinearLayout calCard=UiKit.card(this);
        LinearLayout monthHead=UiKit.row(this);
        Button prev=UiKit.secondaryButton(this,"‹");
        Button next=UiKit.secondaryButton(this,"›");
        TextView monthTitle=UiKit.text(this,String.format(Locale.KOREA,"%d년 %d월",visibleMonth.get(Calendar.YEAR),visibleMonth.get(Calendar.MONTH)+1),19,true);
        monthTitle.setGravity(Gravity.CENTER);
        monthHead.addView(prev);
        monthHead.addView(monthTitle,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        monthHead.addView(next);
        calCard.addView(monthHead);
        calCard.addView(UiKit.spacer(this,10));
        calCard.addView(buildMonthGrid());
        p.addView(calCard);

        prev.setOnClickListener(v->{visibleMonth.add(Calendar.MONTH,-1);visibleMonth.set(Calendar.DAY_OF_MONTH,1);showCalendar();});
        next.setOnClickListener(v->{visibleMonth.add(Calendar.MONTH,1);visibleMonth.set(Calendar.DAY_OF_MONTH,1);showCalendar();});

        setScrollable(p);
    }

    private static class DefaultWatchRecommendation {
        Watch watch;
        long daysNotWorn;
        double monthlyWear;
    }

    private View buildTodayWatchRecommendationCard() {
        LinearLayout card=UiKit.column(this);
        card.setPadding(UiKit.dp(this,9),UiKit.dp(this,8),UiKit.dp(this,9),UiKit.dp(this,8));
        card.setBackground(UiKit.rounded(UiKit.PAPER,UiKit.LINE,15,this));
        card.setElevation(UiKit.dp(this,1));

        TextView label=UiKit.text(this,"TODAY'S WATCH",9,true);
        label.setTextColor(UiKit.GOLD);
        label.setLetterSpacing(0.04f);
        card.addView(label);
        card.addView(UiKit.spacer(this,5));

        SharedPreferences prefs=getSharedPreferences(AI_RECOMMENDATION_PREFS,MODE_PRIVATE);
        String today=dateFormat.format(new Date());
        boolean aiRecommendationForToday=today.equals(prefs.getString(AI_REC_DATE,""));
        long watchId=aiRecommendationForToday?prefs.getLong(AI_REC_WATCH_ID,-1L):-1L;
        long strapId=aiRecommendationForToday?prefs.getLong(AI_REC_STRAP_ID,-1L):-1L;
        Watch aiWatch=watchId>0?db.getWatch(watchId):null;
        Strap aiStrap=strapId>0?db.getStrap(strapId):null;

        if(aiWatch!=null&&aiStrap!=null) {
            LinearLayout row=UiKit.row(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView watchImage=watchThumb(aiWatch.representativeImageUrl,48);
            row.addView(watchImage,new LinearLayout.LayoutParams(UiKit.dp(this,48),UiKit.dp(this,52)));
            ImageView strapImage=new ImageView(this);
            strapImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            strapImage.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,9,this));
            strapImage.setClipToOutline(true);
            strapImage.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            ImageLoader.loadFit(this,strapImage,aiStrap.imagePath);
            LinearLayout.LayoutParams sip=new LinearLayout.LayoutParams(UiKit.dp(this,34),UiKit.dp(this,52));
            sip.setMargins(UiKit.dp(this,4),0,UiKit.dp(this,6),0);
            row.addView(strapImage,sip);

            LinearLayout info=UiKit.column(this);
            TextView name=UiKit.text(this,aiWatch.modelName,11,true);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(name);
            String strapName=(aiStrap.maker.isEmpty()?"":aiStrap.maker+" · ")+aiStrap.name;
            TextView strapView=UiKit.text(this,strapName,9,true);
            strapView.setTextColor(UiKit.NAVY_2);
            strapView.setMaxLines(2);
            strapView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(strapView);
            row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            card.addView(row);
        } else {
            DefaultWatchRecommendation recommendation=getDefaultTodayRecommendation();
            if(recommendation==null||recommendation.watch==null) {
                TextView none=UiKit.muted(this,"추천 가능한 시계 없음",11);
                none.setGravity(Gravity.CENTER);
                card.addView(none,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                Watch w=recommendation.watch;
                LinearLayout row=UiKit.row(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                ImageView image=watchThumb(w.representativeImageUrl,58);
                row.addView(image,new LinearLayout.LayoutParams(UiKit.dp(this,58),UiKit.dp(this,58)));
                LinearLayout info=UiKit.column(this);
                info.setPadding(UiKit.dp(this,7),0,0,0);
                TextView name=UiKit.text(this,w.modelName,11,true);
                name.setMaxLines(2);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                info.addView(name);
                TextView days=UiKit.text(this,recommendation.daysNotWorn+"일 미착용",10,true);
                days.setTextColor(UiKit.NAVY_2);
                info.addView(days);
                TextView month=UiKit.muted(this,"이번 달 "+formatWearScore(recommendation.monthlyWear),9);
                info.addView(month);
                row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
                card.addView(row);
            }
        }

        card.setOnClickListener(v->showTodayWatchRecommendationDialog());
        return card;
    }

    private void showTodayWatchRecommendationDialog() {
        List<Watch> watches=getOutfitRecommendationCandidateWatches();
        if(watches.isEmpty()) {
            Toast.makeText(this,"추천 가능한 시계가 없습니다. Forgotten Watch 제외 목록과 수리 상태를 확인하세요.",Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,10));
        body.addView(UiKit.eyebrow(this,"OUTFIT"));
        body.addView(UiKit.text(this,"오늘의 착장",19,true));
        body.addView(UiKit.spacer(this,7));
        EditText outfit=UiKit.field(this,"예: 밝은 녹색 맨투맨, 검정 바지, 흰 운동화");
        outfit.setSingleLine(false);
        outfit.setMinLines(3);
        outfit.setGravity(Gravity.TOP|Gravity.START);
        outfit.setText(getSharedPreferences(AI_RECOMMENDATION_PREFS,MODE_PRIVATE).getString(AI_REC_OUTFIT,""));
        body.addView(outfit);
        TextView guide=UiKit.muted(this,
                "착장과 색상·격식을 중심으로 추천합니다. 장기 미착용·이번 달 착용량·최근 연속 착용 여부는 반영하지 않으며, Forgotten Watch 제외 목록과 수리 중 상태만 적용됩니다.",11);
        body.addView(guide);
        body.addView(UiKit.spacer(this,9));
        Button config=UiKit.secondaryButton(this,"AI 연결 설정");
        body.addView(config);

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("오늘의 시계 추천")
                .setView(body)
                .setNegativeButton("취소",null)
                .setPositiveButton("추천받기",null)
                .create();
        config.setOnClickListener(v->showAiSettingsDialog(null));
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String outfitText=val(outfit);
            if(outfitText.isEmpty()) {
                outfit.setError("오늘의 착장을 입력하세요.");
                return;
            }
            if(db.getStraps(0).isEmpty()) {
                Toast.makeText(this,"착장 추천에 사용할 스트랩을 먼저 등록하세요.",Toast.LENGTH_LONG).show();
                return;
            }
            dialog.dismiss();
            if(!AiBackendClient.isConfigured(this)) {
                showAiSettingsDialog(()->runTodayWatchRecommendation(outfitText));
            } else {
                runTodayWatchRecommendation(outfitText);
            }
        }));
        dialog.show();
    }

    private void runTodayWatchRecommendation(String outfit) {
        LinearLayout loadingBody=UiKit.column(this);
        loadingBody.setPadding(UiKit.dp(this,18),UiKit.dp(this,12),UiKit.dp(this,18),UiKit.dp(this,14));
        TextView progress=UiKit.muted(this,"추천 데이터를 준비합니다.",12);
        loadingBody.addView(progress);
        AlertDialog loading=new AlertDialog.Builder(this)
                .setTitle("AI 추천 중")
                .setView(loadingBody)
                .setNegativeButton("취소",null)
                .create();
        loading.setCanceledOnTouchOutside(false);
        loading.show();

        executor.execute(()->{
            try {
                List<Watch> candidates=getOutfitRecommendationCandidateWatches();
                List<Strap> straps=db.getStraps(0);
                JSONArray watchPayload=buildRecommendationWatchPayload(candidates,straps);
                JSONArray strapPayload=buildRecommendationStrapPayload(straps);
                if(watchPayload.length()==0) throw new IllegalStateException("호환 스트랩이 있는 추천 대상 시계가 없습니다.");
                AiBackendClient.RecommendationResult result=AiBackendClient.recommend(this,outfit,watchPayload,strapPayload,
                        message->runOnUiThread(()->progress.setText(message)));
                List<AiBackendClient.Recommendation> normalized=normalizeRecommendations(result.recommendations,candidates,straps);
                if(normalized.isEmpty()) throw new IllegalStateException("유효한 시계·스트랩 조합을 만들지 못했습니다.");
                runOnUiThread(()->{
                    if(loading.isShowing()) loading.dismiss();
                    saveTopRecommendation(outfit,normalized.get(0));
                    showCalendar();
                    showRecommendationResultsPopup(result.outfitSummary,normalized);
                });
            } catch(Exception e) {
                runOnUiThread(()->{
                    if(loading.isShowing()) loading.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle("추천 실패")
                            .setMessage(e.getMessage())
                            .setNegativeButton("닫기",null)
                            .setPositiveButton("AI 설정",(d,x)->showAiSettingsDialog(null))
                            .show();
                });
            } finally {
                runOnUiThread(()->refreshApiCreditUsage(true));
            }
        });
    }

    private DefaultWatchRecommendation getDefaultTodayRecommendation() {
        List<Watch> candidates=getBaseRecommendationCandidateWatches();
        if(candidates.isEmpty()) return null;
        final String monthStart=currentMonthStart();
        final String monthEnd=currentMonthEnd();
        final String today=dateFormat.format(new Date());
        candidates.sort((a,b)->{
            long daysA=db.getDaysNotWornForWatch(a.id,firstNonEmpty(a.purchaseDate,today));
            long daysB=db.getDaysNotWornForWatch(b.id,firstNonEmpty(b.purchaseDate,today));
            double monthA=db.getWearScoreForWatch(a.id,monthStart,monthEnd);
            double monthB=db.getWearScoreForWatch(b.id,monthStart,monthEnd);
            double scoreA=daysA-(monthA*7.0);
            double scoreB=daysB-(monthB*7.0);
            int byScore=Double.compare(scoreB,scoreA);
            if(byScore!=0) return byScore;
            int byDays=Long.compare(daysB,daysA);
            if(byDays!=0) return byDays;
            int byMonth=Double.compare(monthA,monthB);
            if(byMonth!=0) return byMonth;
            return safeText(a.modelName).compareToIgnoreCase(safeText(b.modelName));
        });
        Watch top=candidates.get(0);
        DefaultWatchRecommendation result=new DefaultWatchRecommendation();
        result.watch=top;
        result.daysNotWorn=db.getDaysNotWornForWatch(top.id,firstNonEmpty(top.purchaseDate,today));
        result.monthlyWear=db.getWearScoreForWatch(top.id,monthStart,monthEnd);
        return result;
    }

    private List<Watch> getBaseRecommendationCandidateWatches() {
        return getRecommendationCandidates(true);
    }

    private List<Watch> getOutfitRecommendationCandidateWatches() {
        // 착장 추천에서는 최근 연속 착용 여부를 제외 조건으로 사용하지 않습니다.
        List<Watch> out=getRecommendationCandidates(false);
        out.sort((a,b)->{
            int byBrand=safeText(a.brand).compareToIgnoreCase(safeText(b.brand));
            if(byBrand!=0) return byBrand;
            return safeText(a.modelName).compareToIgnoreCase(safeText(b.modelName));
        });
        return out;
    }

    private List<Watch> getRecommendationCandidates(boolean excludeRecentConsecutiveWear) {
        Set<Long> excluded=db.getForgottenExcludedWatchIds();
        List<Watch> out=new ArrayList<>();
        for(Watch w:db.getWatches()) {
            if(excluded.contains(w.id)) continue;
            if(isWatchUnavailableForRecommendation(w)) continue;
            if(excludeRecentConsecutiveWear && db.getRecentConsecutiveWearDays(w.id)>=2) continue;
            out.add(w);
        }
        return out;
    }

    private boolean isWatchUnavailableForRecommendation(Watch w) {
        StringBuilder sb=new StringBuilder(safeText(w.notes).toLowerCase(Locale.ROOT));
        for(DatabaseHelper.RepairRow r:db.getRepairsForWatch(w.id)) {
            sb.append(' ').append(safeText(r.details).toLowerCase(Locale.ROOT));
        }
        String text=sb.toString();
        String[] markers={"수리 중","수리중","점검 중","점검중","보증 수리","보증수리","as 중","as중","서비스 중","서비스중","오버홀 중","오버홀중","입고 중","입고중"};
        for(String marker:markers) if(text.contains(marker)) return true;
        return false;
    }

    private JSONArray buildRecommendationWatchPayload(List<Watch> watches,List<Strap> straps) throws Exception {
        JSONArray out=new JSONArray();
        for(Watch w:watches) {
            int lug=parseLugWidth(w.lugWidthMm);
            if(lug<=0||!hasCompatibleStrap(lug,straps)) continue;
            JSONObject item=new JSONObject();
            item.put("id",w.id);
            item.put("name",(w.brand.isEmpty()?"":w.brand+" · ")+w.modelName);
            item.put("brand",w.brand);
            item.put("reference",w.referenceNo);
            item.put("power",normalizePower(w.movement));
            item.put("movement",w.caliber);
            item.put("lug_width_mm",lug);
            item.put("warranty_end_date",w.warrantyEndDate);
            item.put("notes",w.notes);
            out.put(item);
        }
        return out;
    }

    private JSONArray buildRecommendationStrapPayload(List<Strap> straps) throws Exception {
        JSONArray out=new JSONArray();
        for(Strap s:straps) {
            if(s.widthMm<=0) continue;
            JSONObject item=new JSONObject();
            item.put("id",s.id);
            item.put("name",(s.maker.isEmpty()?"":s.maker+" · ")+s.name);
            item.put("maker",s.maker);
            item.put("width_mm",s.widthMm);
            item.put("material",s.material);
            item.put("color",s.color);
            item.put("buckle",s.buckle);
            item.put("notes",s.notes);
            out.put(item);
        }
        return out;
    }

    private List<AiBackendClient.Recommendation> normalizeRecommendations(
            List<AiBackendClient.Recommendation> requested,List<Watch> watches,List<Strap> straps) {
        Map<Long,Watch> watchMap=new LinkedHashMap<>();
        for(Watch w:watches) watchMap.put(w.id,w);
        Map<Long,Strap> strapMap=new LinkedHashMap<>();
        for(Strap s:straps) strapMap.put(s.id,s);
        List<AiBackendClient.Recommendation> out=new ArrayList<>();
        Set<Long> usedWatchIds=new HashSet<>();
        if(requested!=null) {
            for(AiBackendClient.Recommendation r:requested) {
                Watch w=watchMap.get(r.watchId);
                Strap s=strapMap.get(r.strapId);
                if(w==null||s==null||usedWatchIds.contains(w.id)) continue;
                if(parseLugWidth(w.lugWidthMm)!=s.widthMm) continue;
                r.rank=out.size()+1;
                out.add(r);
                usedWatchIds.add(w.id);
                if(out.size()>=3) break;
            }
        }
        if(out.size()<3) {
            for(Watch w:watches) {
                if(usedWatchIds.contains(w.id)) continue;
                Strap compatible=firstCompatibleStrap(w,straps);
                if(compatible==null) continue;
                AiBackendClient.Recommendation r=new AiBackendClient.Recommendation();
                r.rank=out.size()+1;
                r.watchId=w.id;
                r.strapId=compatible.id;
                r.reason="착장 추천 후보 중 호환되는 저장 스트랩 조합입니다.";
                out.add(r);
                usedWatchIds.add(w.id);
                if(out.size()>=3) break;
            }
        }
        return out;
    }

    private boolean hasCompatibleStrap(int lug,List<Strap> straps) {
        for(Strap s:straps) if(s.widthMm==lug) return true;
        return false;
    }

    private Strap firstCompatibleStrap(Watch w,List<Strap> straps) {
        int lug=parseLugWidth(w.lugWidthMm);
        if(lug<=0) return null;
        for(Strap s:straps) if(s.widthMm==lug) return s;
        return null;
    }

    private int parseLugWidth(String value) {
        try { return Math.round(Float.parseFloat(safeText(value).replaceAll("[^0-9.]",""))); }
        catch(Exception ignored) { return -1; }
    }

    private void saveTopRecommendation(String outfit,AiBackendClient.Recommendation top) {
        getSharedPreferences(AI_RECOMMENDATION_PREFS,MODE_PRIVATE).edit()
                .putLong(AI_REC_WATCH_ID,top.watchId)
                .putLong(AI_REC_STRAP_ID,top.strapId)
                .putString(AI_REC_REASON,top.reason)
                .putString(AI_REC_OUTFIT,outfit)
                .putString(AI_REC_DATE,dateFormat.format(new Date()))
                .apply();
    }

    private void showRecommendationResultsPopup(String outfitSummary,List<AiBackendClient.Recommendation> recommendations) {
        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,16),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,14));
        if(outfitSummary!=null&&!outfitSummary.trim().isEmpty()) {
            body.addView(UiKit.muted(this,"착장 해석 · "+outfitSummary,11));
            body.addView(UiKit.spacer(this,8));
        }
        for(AiBackendClient.Recommendation r:recommendations) {
            Watch w=db.getWatch(r.watchId);
            Strap s=db.getStrap(r.strapId);
            if(w==null||s==null) continue;
            LinearLayout card=UiKit.card(this);
            LinearLayout rankRow=UiKit.row(this);
            TextView rank=UiKit.text(this,r.rank+"위",16,true);
            rank.setTextColor(UiKit.GOLD);
            rankRow.addView(rank,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            rankRow.addView(UiKit.muted(this,s.widthMm+" mm",10));
            card.addView(rankRow);
            card.addView(UiKit.spacer(this,6));
            LinearLayout row=UiKit.row(this);
            ImageView watchImage=watchThumb(w.representativeImageUrl,74);
            row.addView(watchImage,new LinearLayout.LayoutParams(UiKit.dp(this,74),UiKit.dp(this,74)));
            ImageView strapImage=new ImageView(this);
            strapImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            strapImage.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,10,this));
            ImageLoader.loadFit(this,strapImage,s.imagePath);
            LinearLayout.LayoutParams sip=new LinearLayout.LayoutParams(UiKit.dp(this,58),UiKit.dp(this,74));
            sip.setMargins(UiKit.dp(this,5),0,UiKit.dp(this,10),0);
            row.addView(strapImage,sip);
            LinearLayout info=UiKit.column(this);
            TextView watchName=UiKit.text(this,(w.brand.isEmpty()?"":w.brand+" · ")+w.modelName,14,true);
            watchName.setMaxLines(2);
            info.addView(watchName);
            TextView strapName=UiKit.text(this,(s.maker.isEmpty()?"":s.maker+" · ")+s.name,12,true);
            strapName.setTextColor(UiKit.NAVY_2);
            info.addView(strapName);
            TextView reason=UiKit.muted(this,r.reason,11);
            reason.setMaxLines(4);
            info.addView(reason);
            row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            card.addView(row);
            card.setOnClickListener(v->showWatchDetailPopup(w));
            body.addView(card);
        }
        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        new AlertDialog.Builder(this)
                .setTitle("오늘의 시계 추천 TOP 3")
                .setView(scroll)
                .setPositiveButton("닫기",null)
                .show();
    }

    private void showOutfitCompletionDialog() {
        List<Watch> watches=db.getWatches();
        List<Strap> allStraps=db.getStraps(0);
        if(watches.isEmpty()) {
            Toast.makeText(this,"먼저 컬렉션에 시계를 등록하세요.",Toast.LENGTH_LONG).show();
            return;
        }
        if(allStraps.isEmpty()) {
            Toast.makeText(this,"먼저 스트랩을 등록하세요.",Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,12));
        body.addView(UiKit.eyebrow(this,"OUTFIT COMPLETION"));
        body.addView(UiKit.text(this,"시계와 스트랩에 맞춘 착장",19,true));
        body.addView(UiKit.muted(this,"아는 항목만 입력하면 비어 있는 상의·하의·신발을 AI가 완성합니다.",11));
        body.addView(UiKit.spacer(this,8));

        EditText top=addField(body,"상의","예: 밝은 녹색 맨투맨","");
        EditText bottom=addField(body,"하의","예: 검정 슬랙스","");
        EditText shoes=addField(body,"신발","예: 흰색 스니커즈","");

        body.addView(UiKit.label(this,"시계"));
        Spinner watchSpinner=watchSpinnerWithNone(watches,"— 시계 선택 —");
        body.addView(watchSpinner);
        body.addView(UiKit.spacer(this,8));
        body.addView(UiKit.label(this,"스트랩"));
        Spinner strapSpinner=new Spinner(this);
        decorateSpinner(strapSpinner);
        body.addView(strapSpinner);

        final java.util.concurrent.atomic.AtomicReference<List<Strap>> compatibleHolder=
                new java.util.concurrent.atomic.AtomicReference<>(new ArrayList<>());
        updateOutfitStrapSpinner(strapSpinner,compatibleHolder.get());
        watchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                compatibleHolder.set(new ArrayList<>());
                if(position>0&&position-1<watches.size()) {
                    int lug=parseLugWidth(watches.get(position-1).lugWidthMm);
                    for(Strap strap:allStraps) if(strap.widthMm==lug) compatibleHolder.get().add(strap);
                }
                updateOutfitStrapSpinner(strapSpinner,compatibleHolder.get());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {
                compatibleHolder.set(new ArrayList<>());
                updateOutfitStrapSpinner(strapSpinner,compatibleHolder.get());
            }
        });

        LinearLayout resultContainer=UiKit.column(this);
        resultContainer.setVisibility(View.GONE);
        body.addView(UiKit.spacer(this,12));
        body.addView(resultContainer);

        EditText feedback=UiKit.field(this,"예: 더 캐주얼하게, 신발은 검정색 제외");
        feedback.setSingleLine(false);
        feedback.setMinLines(2);
        feedback.setGravity(Gravity.TOP|Gravity.START);
        feedback.setVisibility(View.GONE);
        TextView feedbackLabel=UiKit.label(this,"피드백");
        feedbackLabel.setVisibility(View.GONE);
        body.addView(feedbackLabel);
        body.addView(feedback);

        TextView status=UiKit.muted(this,"",11);
        status.setVisibility(View.GONE);
        body.addView(status);
        Button config=UiKit.secondaryButton(this,"AI 연결 설정");
        config.setOnClickListener(v->showAiSettingsDialog(null));
        body.addView(UiKit.spacer(this,7));
        body.addView(config);

        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("착장 추천")
                .setView(scroll)
                .setNegativeButton("닫기",null)
                .setPositiveButton("추천받기",null)
                .create();
        final JSONObject[] previous={new JSONObject()};
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int watchPosition=watchSpinner.getSelectedItemPosition();
            if(watchPosition<=0||watchPosition>watches.size()) {
                Toast.makeText(this,"시계를 선택하세요.",Toast.LENGTH_SHORT).show();
                return;
            }
            int strapPosition=strapSpinner.getSelectedItemPosition();
            if(strapPosition<=0||strapPosition>compatibleHolder.get().size()) {
                Toast.makeText(this,"선택한 시계와 호환되는 스트랩을 선택하세요.",Toast.LENGTH_SHORT).show();
                return;
            }
            Watch selectedWatch=watches.get(watchPosition-1);
            Strap selectedStrap=compatibleHolder.get().get(strapPosition-1);
            if(!AiBackendClient.isConfigured(this)) {
                showAiSettingsDialog(()->dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
                return;
            }
            Button action=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            action.setEnabled(false);
            status.setVisibility(View.VISIBLE);
            status.setText("AI가 착장을 구성하는 중…");
            executor.execute(()->{
                try {
                    AiBackendClient.OutfitCompletionResult result=AiBackendClient.completeOutfit(
                            this,val(top),val(bottom),val(shoes),
                            buildOutfitWatchJson(selectedWatch),buildOutfitStrapJson(selectedStrap),
                            val(feedback),previous[0],
                            message->runOnUiThread(()->status.setText(message)));
                    JSONObject saved=new JSONObject();
                    saved.put("top",result.top);
                    saved.put("bottom",result.bottom);
                    saved.put("shoes",result.shoes);
                    saved.put("summary",result.summary);
                    saved.put("reason",result.reason);
                    previous[0]=saved;
                    runOnUiThread(()->{
                        action.setEnabled(true);
                        action.setText("다시 추천");
                        status.setVisibility(View.GONE);
                        renderOutfitCompletionResult(resultContainer,result,selectedWatch,selectedStrap);
                        feedbackLabel.setVisibility(View.VISIBLE);
                        feedback.setVisibility(View.VISIBLE);
                        feedback.setText("");
                    });
                } catch(Exception e) {
                    runOnUiThread(()->{
                        action.setEnabled(true);
                        status.setVisibility(View.VISIBLE);
                        status.setText("추천 실패: "+e.getMessage());
                    });
                } finally {
                    runOnUiThread(()->refreshApiCreditUsage(true));
                }
            });
        }));
        dialog.show();
    }

    private void updateOutfitStrapSpinner(Spinner spinner,List<Strap> straps) {
        List<String> labels=new ArrayList<>();
        labels.add(straps.isEmpty()?"— 호환 스트랩 없음 —":"— 스트랩 선택 —");
        for(Strap strap:straps) labels.add((strap.maker.isEmpty()?"":strap.maker+" · ")+strap.name+" · "+strap.widthMm+" mm");
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        spinner.setSelection(0);
    }

    private JSONObject buildOutfitWatchJson(Watch watch) throws Exception {
        JSONObject item=new JSONObject();
        item.put("id",watch.id);
        item.put("brand",watch.brand);
        item.put("model",watch.modelName);
        item.put("reference",watch.referenceNo);
        item.put("power",normalizePower(watch.movement));
        item.put("diameter_mm",watch.diameterMm);
        item.put("case_material",watch.caseMaterial);
        item.put("water_resistance",watch.waterResistance);
        item.put("lug_width_mm",parseLugWidth(watch.lugWidthMm));
        item.put("notes",watch.notes);
        return item;
    }

    private JSONObject buildOutfitStrapJson(Strap strap) throws Exception {
        JSONObject item=new JSONObject();
        item.put("id",strap.id);
        item.put("name",strap.name);
        item.put("maker",strap.maker);
        item.put("width_mm",strap.widthMm);
        item.put("material",strap.material);
        item.put("color",strap.color);
        item.put("buckle",strap.buckle);
        item.put("notes",strap.notes);
        return item;
    }

    private void renderOutfitCompletionResult(LinearLayout container,
                                              AiBackendClient.OutfitCompletionResult result,
                                              Watch watch,Strap strap) {
        container.removeAllViews();
        container.setVisibility(View.VISIBLE);
        LinearLayout card=UiKit.card(this);
        card.addView(UiKit.eyebrow(this,"AI RECOMMENDATION"));
        card.addView(UiKit.text(this,result.summary,16,true));
        card.addView(UiKit.spacer(this,7));
        card.addView(metricLine("상의",result.top));
        card.addView(metricLine("하의",result.bottom));
        card.addView(metricLine("신발",result.shoes));
        card.addView(metricLine("시계",(watch.brand.isEmpty()?"":watch.brand+" · ")+watch.modelName));
        card.addView(metricLine("스트랩",(strap.maker.isEmpty()?"":strap.maker+" · ")+strap.name));
        if(!result.reason.trim().isEmpty()) {
            card.addView(UiKit.spacer(this,6));
            card.addView(UiKit.muted(this,result.reason,11));
        }
        container.addView(card);
    }

    private void showAiSettingsDialog(Runnable afterSave) {
        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,10));
        EditText server=addField(body,"AI 서버 URL","https://your-worker.workers.dev",AiBackendClient.getServerUrl(this));
        EditText token=addField(body,"Watch App Token","중계 서버에 설정한 토큰",AiBackendClient.getAccessToken(this));
        token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView status=UiKit.muted(this,"OpenAI API 키가 아니라 중계 서버 URL과 전용 접근 토큰을 입력합니다.",11);
        body.addView(status);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("AI 연결 설정")
                .setView(body)
                .setNegativeButton("취소",null)
                .setPositiveButton("저장 및 테스트",null)
                .create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String url=val(server);
            String accessToken=val(token);
            if(!url.startsWith("https://")) {
                server.setError("HTTPS 주소를 입력하세요.");
                return;
            }
            if(accessToken.isEmpty()) {
                token.setError("Watch App Token을 입력하세요.");
                return;
            }
            AiBackendClient.saveConfig(this,url,accessToken);
            status.setText("연결을 확인하는 중…");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            executor.execute(()->{
                try {
                    boolean ok=AiBackendClient.healthCheck(this);
                    runOnUiThread(()->{
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        if(ok) {
                            dialog.dismiss();
                            Toast.makeText(this,"AI 서버 연결을 확인했습니다.",Toast.LENGTH_SHORT).show();
                            refreshApiCreditUsage(true);
                            if(afterSave!=null) afterSave.run();
                        } else status.setText("서버 응답을 확인하지 못했습니다.");
                    });
                } catch(Exception e) {
                    runOnUiThread(()->{
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        status.setText("연결 실패: "+e.getMessage());
                    });
                }
            });
        }));
        dialog.show();
    }

    private View buildForgottenWatchMiniCard() {
        DatabaseHelper.ForgottenWatchRow forgotten=db.getForgottenWatch();
        LinearLayout card=UiKit.column(this);
        card.setPadding(UiKit.dp(this,9),UiKit.dp(this,8),UiKit.dp(this,9),UiKit.dp(this,8));
        card.setBackground(UiKit.rounded(UiKit.PAPER,UiKit.LINE,15,this));
        card.setElevation(UiKit.dp(this,1));
        TextView label=UiKit.text(this,"FORGOTTEN WATCH",9,true);
        label.setTextColor(UiKit.GOLD);
        label.setLetterSpacing(0.04f);
        card.addView(label);
        card.addView(UiKit.spacer(this,5));

        if(forgotten==null||forgotten.watch==null) {
            TextView none=UiKit.muted(this,"표시할 시계 없음",11);
            none.setGravity(Gravity.CENTER);
            card.addView(none,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            card.setOnClickListener(v->showForgottenWatchDialog(null));
            return card;
        }

        Watch w=forgotten.watch;
        LinearLayout row=UiKit.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView image=watchThumb(w.representativeImageUrl,58);
        row.addView(image,new LinearLayout.LayoutParams(UiKit.dp(this,58),UiKit.dp(this,58)));
        LinearLayout info=UiKit.column(this);
        info.setPadding(UiKit.dp(this,8),0,0,0);
        TextView name=UiKit.text(this,w.modelName,11,true);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setLineSpacing(0,1.0f);
        info.addView(name);
        TextView days=UiKit.text(this,forgotten.daysNotWorn+"일 미착용",10,true);
        days.setTextColor(UiKit.NAVY_2);
        days.setPadding(0,UiKit.dp(this,2),0,0);
        info.addView(days);
        String last=forgotten.neverWorn?"착용 기록 없음":"마지막 "+forgotten.lastWearDate;
        TextView lastView=UiKit.muted(this,last,9);
        lastView.setMaxLines(1);
        lastView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(lastView);
        row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        card.addView(row,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setOnClickListener(v->showForgottenWatchDialog(forgotten));
        return card;
    }

    private void showForgottenWatchDialog(DatabaseHelper.ForgottenWatchRow forgotten) {
        List<Watch> watches=db.getWatches();
        Set<Long> excluded=db.getForgottenExcludedWatchIds();
        Map<Long,CheckBox> boxes=new LinkedHashMap<>();

        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,16),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,14));

        if(forgotten!=null&&forgotten.watch!=null) {
            Watch w=forgotten.watch;
            LinearLayout summary=UiKit.card(this);
            LinearLayout row=UiKit.row(this);
            ImageView image=new ImageView(this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,13,this));
            image.setClipToOutline(true);
            image.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            ImageLoader.loadFit(this,image,w.representativeImageUrl);
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(UiKit.dp(this,104),UiKit.dp(this,118));
            ip.setMargins(0,0,UiKit.dp(this,12),0);
            row.addView(image,ip);

            LinearLayout info=UiKit.column(this);
            info.addView(UiKit.eyebrow(this,w.brand.isEmpty()?"WATCH":w.brand));
            TextView name=UiKit.text(this,w.modelName,18,true);
            info.addView(name);
            if(!w.referenceNo.isEmpty()) info.addView(UiKit.muted(this,"Ref. "+w.referenceNo,11));
            info.addView(UiKit.spacer(this,6));
            View specs=buildWatchSummarySpecs(w);
            if(specs!=null) info.addView(specs);
            info.addView(UiKit.spacer(this,6));
            String last=forgotten.neverWorn?"착용 기록 없음":"마지막 착용 "+forgotten.lastWearDate;
            TextView status=UiKit.text(this,last+" · "+forgotten.daysNotWorn+"일",11,true);
            status.setTextColor(UiKit.GOLD);
            info.addView(status);
            row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            summary.addView(row);
            summary.setOnClickListener(v->showWatchDetailPopup(w));
            body.addView(summary);
        } else {
            body.addView(emptyCard("Forgotten Watch 없음","현재 컬렉션의 모든 시계가 제외되어 있거나 등록된 시계가 없습니다."));
        }

        LinearLayout exclusion=UiKit.card(this);
        exclusion.addView(UiKit.eyebrow(this,"EXCLUSION"));
        exclusion.addView(UiKit.text(this,"Forgotten Watch에서 제외",16,true));
        exclusion.addView(UiKit.spacer(this,5));
        exclusion.addView(UiKit.muted(this,"체크한 시계는 가장 오래 미착용한 시계 계산에서 제외됩니다.",11));
        exclusion.addView(UiKit.spacer(this,7));
        for(Watch w:watches) {
            CheckBox box=new CheckBox(this);
            box.setText((w.brand.isEmpty()?"":w.brand+" · ")+w.modelName);
            box.setTextColor(UiKit.INK);
            box.setTextSize(13);
            box.setChecked(excluded.contains(w.id));
            box.setPadding(0,UiKit.dp(this,3),0,UiKit.dp(this,3));
            boxes.put(w.id,box);
            exclusion.addView(box);
        }
        body.addView(exclusion);

        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Forgotten Watch")
                .setView(scroll)
                .setNegativeButton("취소",null)
                .setPositiveButton("저장",null)
                .create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            for(Map.Entry<Long,CheckBox> entry:boxes.entrySet()) {
                db.setForgottenWatchExcluded(entry.getKey(),entry.getValue().isChecked());
            }
            dialog.dismiss();
            showCalendar();
        }));
        dialog.show();
    }

    private View buildTopWearRankingCard() {
        LinearLayout card=UiKit.card(this);
        card.addView(UiKit.eyebrow(this,"MOST WORN"));

        LinearLayout switcher=UiKit.row(this);
        Button left=UiKit.secondaryButton(this,"‹");
        Button right=UiKit.secondaryButton(this,"›");
        String periodTitle=wearRankingPeriod==0?"이번 달":wearRankingPeriod==1?"올해":"누적";
        TextView title=UiKit.text(this,periodTitle+" 착용 순위",18,true);
        title.setGravity(Gravity.CENTER);
        switcher.addView(left);
        switcher.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        switcher.addView(right);
        card.addView(switcher);
        TextView guide=UiKit.muted(this,"처음 3개를 표시하며 목록 안에서 스크롤하면 전체 순위를 볼 수 있습니다.",10);
        guide.setGravity(Gravity.CENTER);
        card.addView(guide);
        card.addView(UiKit.spacer(this,6));

        List<DatabaseHelper.WearStat> stats;
        if(wearRankingPeriod==0) stats=db.getTopWorn(currentMonthStart(),currentMonthEnd(),10000);
        else if(wearRankingPeriod==1) stats=db.getTopWorn(currentYearStart(),currentYearEnd(),10000);
        else stats=db.getTopWorn("0000-01-01","9999-12-31",10000);

        if(stats.isEmpty()) {
            card.addView(UiKit.muted(this,"착용 기록이 없습니다.",12));
        } else {
            LinearLayout list=UiKit.column(this);
            int rank=1;
            for(DatabaseHelper.WearStat stat:stats) {
                final DatabaseHelper.WearStat item=stat;
                LinearLayout row=UiKit.row(this);
                TextView no=UiKit.text(this,String.valueOf(rank),15,true);
                no.setTextColor(UiKit.GOLD);
                row.addView(no,new LinearLayout.LayoutParams(UiKit.dp(this,22),ViewGroup.LayoutParams.WRAP_CONTENT));
                ImageView image=watchThumb(item.imageUrl,48);
                row.addView(image,new LinearLayout.LayoutParams(UiKit.dp(this,48),UiKit.dp(this,48)));
                LinearLayout info=UiKit.column(this);
                info.setPadding(UiKit.dp(this,10),0,0,0);
                TextView name=UiKit.text(this,item.watchName,13,true);
                name.setMaxLines(2);
                TextView score=UiKit.text(this,formatWearScore(item.score),12,true);
                score.setTextColor(UiKit.GOLD);
                info.addView(name);
                info.addView(score);
                row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
                row.setPadding(0,UiKit.dp(this,5),0,UiKit.dp(this,5));
                row.setOnClickListener(v->{
                    Watch watch=db.getWatch(item.watchId);
                    if(watch!=null) showWatchDetailPopup(watch);
                    else Toast.makeText(this,"컬렉션에서 방출된 시계의 착용 기록입니다.",Toast.LENGTH_SHORT).show();
                });
                list.addView(row);
                rank++;
            }
            ScrollView rankingScroll=new ScrollView(this);
            rankingScroll.setFillViewport(false);
            rankingScroll.setNestedScrollingEnabled(true);
            // 페이지 전체 ScrollView 안에서도 랭킹 카드가 자체적으로 스크롤되도록 상위 터치 가로채기를 막습니다.
            rankingScroll.setOnTouchListener((view,event)->{
                view.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            });
            rankingScroll.addView(list);
            int visibleRows=Math.min(3,stats.size());
            card.addView(rankingScroll,new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,visibleRows*58)));
        }
        left.setOnClickListener(v->{wearRankingPeriod=(wearRankingPeriod+2)%3;showCalendar();});
        right.setOnClickListener(v->{wearRankingPeriod=(wearRankingPeriod+1)%3;showCalendar();});
        return card;
    }

    private void showWearDialog(String date) {
        selectedCalendarDate=date;
        List<Watch>watches=db.getWatches();
        List<DatabaseHelper.WearRow> dayRows=db.getWears(date);

        LinearLayout f=UiKit.column(this);
        f.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,8));
        f.addView(UiKit.eyebrow(this,"WEAR RECORD"));
        f.addView(UiKit.text(this,date,20,true));
        if(!dayRows.isEmpty()) {
            f.addView(UiKit.spacer(this,8));
            for(DatabaseHelper.WearRow wr:dayRows) f.addView(selectedWearRow(wr));
        }
        f.addView(UiKit.spacer(this,10));
        f.addView(UiKit.label(this,"첫 번째 시계"));
        Spinner first=watchSpinnerWithNone(watches,"— 첫 번째 시계 선택 —");
        f.addView(first);
        f.addView(UiKit.spacer(this,8));
        f.addView(UiKit.label(this,"두 번째 시계 (선택)"));
        Spinner second=watchSpinnerWithNone(watches,"— 두 번째 시계 없음 —");
        f.addView(second);

        f.addView(UiKit.label(this,"두 번째 시계 착용 비율 (0~1)"));
        EditText secondRatio=UiKit.field(this,"비워두면 0.5");
        secondRatio.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        secondRatio.setEnabled(false);
        f.addView(secondRatio);

        if(dayRows.size()>0) setWatchSpinnerSelection(first,watches,dayRows.get(0).watchId);
        if(dayRows.size()>1) {
            setWatchSpinnerSelection(second,watches,dayRows.get(1).watchId);
            secondRatio.setText(trimRatio(dayRows.get(1).wearRatio));
            secondRatio.setEnabled(true);
        }

        second.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                boolean enabled=position>0;
                secondRatio.setEnabled(enabled);
                if(!enabled) secondRatio.setText("");
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { secondRatio.setEnabled(false); }
        });

        EditText memo=UiKit.field(this,"메모: 스트랩, 장소, 행사 등");
        if(!dayRows.isEmpty()) memo.setText(dayRows.get(0).note);
        f.addView(memo);

        Button wearPhotoButton=UiKit.secondaryButton(this,"오착");
        f.addView(wearPhotoButton);

        ScrollView sc=new ScrollView(this); sc.addView(f);
        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle("착용 시계 선택")
                .setView(sc)
                .setNegativeButton("취소",null)
                .setNeutralButton("전체 삭제",null)
                .setPositiveButton("저장",null)
                .create();
        dlg.setOnShowListener(x->{
            wearPhotoButton.setOnClickListener(v->{
                List<DatabaseHelper.WearRow> rows=db.getWears(date);
                if(rows.isEmpty()) {
                    if(!saveWearDialogSelection(date,first,second,secondRatio,memo,watches)) return;
                    CalendarWidgetProvider.updateAll(this);
                    rows=db.getWears(date);
                }
                if(rows.isEmpty()) {
                    Toast.makeText(this,"먼저 착용 시계를 선택하세요.",Toast.LENGTH_SHORT).show();
                    return;
                }
                showWearPhotoTargetChooser(date,rows);
            });

            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(!saveWearDialogSelection(date,first,second,secondRatio,memo,watches)) return;
                CalendarWidgetProvider.updateAll(this);
                dlg.dismiss();
                showCalendar();
            });
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                db.clearWearDate(date);
                db.deleteWearPhotosForDate(date);
                CalendarWidgetProvider.updateAll(this);
                dlg.dismiss();
                showCalendar();
            });
        });
        dlg.show();
    }

    private boolean saveWearDialogSelection(String date,Spinner first,Spinner second,EditText secondRatio,
                                            EditText memo,List<Watch>watches) {
        Watch w1=selectedWatch(first,watches);
        Watch w2=selectedWatch(second,watches);
        if(w1==null&&w2==null) {
            Toast.makeText(this,"착용한 시계를 하나 이상 선택하세요.",Toast.LENGTH_SHORT).show();
            return false;
        }
        if(w1!=null&&w2!=null&&w1.id==w2.id) {
            Toast.makeText(this,"같은 시계를 두 번 선택할 수 없습니다.",Toast.LENGTH_SHORT).show();
            return false;
        }

        double ratio2=0.5;
        if(w1!=null&&w2!=null) {
            String raw=val(secondRatio);
            if(!raw.isEmpty()) {
                try {
                    ratio2=Double.parseDouble(raw);
                    if(ratio2<0.0||ratio2>1.0) throw new IllegalArgumentException();
                } catch(Exception e) {
                    secondRatio.setError("0~1 사이의 값을 입력하세요.");
                    return false;
                }
            }
        }

        db.clearWearDate(date);
        if(w1!=null&&w2!=null) {
            db.saveWear(w1.id,date,1,1.0-ratio2,val(memo));
            db.saveWear(w2.id,date,2,ratio2,val(memo));
        } else if(w1!=null) {
            db.saveWear(w1.id,date,1,1.0,val(memo));
        } else {
            db.saveWear(w2.id,date,1,1.0,val(memo));
        }
        return true;
    }

    private void showWearPhotoTargetChooser(String date,List<DatabaseHelper.WearRow> rows) {
        if(rows==null||rows.isEmpty()) return;
        if(rows.size()==1) {
            DatabaseHelper.WearRow row=rows.get(0);
            showWearPhotoManager(date,row.watchId,row.watchName);
            return;
        }
        int count=Math.min(2,rows.size());
        String[] names=new String[count];
        for(int i=0;i<count;i++) names[i]=rows.get(i).watchName;
        new AlertDialog.Builder(this)
                .setTitle("시계를 선택하세요")
                .setItems(names,(d,which)->{
                    DatabaseHelper.WearRow row=rows.get(which);
                    showWearPhotoManager(date,row.watchId,row.watchName);
                })
                .setNegativeButton("취소",null)
                .show();
    }

    private void showWearPhotoManager(String date,long watchId,String watchName) {
        final AlertDialog[] dialog=new AlertDialog[1];
        final int[] photoIndex={0};
        final List<DatabaseHelper.WearPhotoRow> photos=db.getWearPhotosForDateWatch(date,watchId);

        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,16),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,14));
        body.addView(UiKit.eyebrow(this,"WRIST SHOTS"));
        body.addView(UiKit.text(this,watchName,18,true));
        TextView dateText=UiKit.text(this,date,13,true);
        dateText.setTextColor(UiKit.GOLD);
        body.addView(dateText);
        body.addView(UiKit.spacer(this,10));

        FrameLayout stage=new FrameLayout(this);
        stage.setBackground(UiKit.rounded(UiKit.PHOTO_BG,UiKit.LINE,16,this));
        stage.setClipToOutline(true);
        stage.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        body.addView(stage,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,360)));

        ImageView image=new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(UiKit.PHOTO_BG);
        stage.addView(image,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        Button prevPhoto=overlayArrowButton("‹");
        Button nextPhoto=overlayArrowButton("›");
        FrameLayout.LayoutParams prevLp=new FrameLayout.LayoutParams(UiKit.dp(this,46),UiKit.dp(this,46));
        prevLp.gravity=Gravity.CENTER_VERTICAL|Gravity.START;
        prevLp.setMargins(UiKit.dp(this,8),0,0,0);
        FrameLayout.LayoutParams nextLp=new FrameLayout.LayoutParams(UiKit.dp(this,46),UiKit.dp(this,46));
        nextLp.gravity=Gravity.CENTER_VERTICAL|Gravity.END;
        nextLp.setMargins(0,0,UiKit.dp(this,8),0);
        stage.addView(prevPhoto,prevLp);
        stage.addView(nextPhoto,nextLp);

        TextView photoCount=UiKit.text(this,"",11,true);
        photoCount.setTextColor(UiKit.GOLD);
        photoCount.setBackground(UiKit.rounded(UiKit.PAPER,UiKit.LINE,12,this));
        photoCount.setPadding(UiKit.dp(this,8),UiKit.dp(this,4),UiKit.dp(this,8),UiKit.dp(this,4));
        FrameLayout.LayoutParams countLp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        countLp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
        countLp.setMargins(0,UiKit.dp(this,8),0,0);
        stage.addView(photoCount,countLp);

        TextView empty=UiKit.muted(this,"등록된 오착 사진이 없습니다.",13);
        empty.setGravity(Gravity.CENTER);
        stage.addView(empty,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout actions=UiKit.row(this);
        Button add=UiKit.primaryButton(this,"+ 오착 사진 추가");
        Button delete=UiKit.secondaryButton(this,"현재 사진 삭제");
        actions.addView(add,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        actions.addView(delete,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        body.addView(UiKit.spacer(this,10));
        body.addView(actions);

        final Runnable[] render=new Runnable[1];
        render[0]=()->{
            boolean has=!photos.isEmpty();
            if(!has) {
                image.setImageDrawable(null);
                image.setVisibility(View.GONE);
                empty.setVisibility(View.VISIBLE);
                prevPhoto.setVisibility(View.GONE);
                nextPhoto.setVisibility(View.GONE);
                photoCount.setVisibility(View.GONE);
                delete.setEnabled(false);
                return;
            }
            if(photoIndex[0]<0) photoIndex[0]=0;
            if(photoIndex[0]>=photos.size()) photoIndex[0]=photos.size()-1;
            DatabaseHelper.WearPhotoRow current=photos.get(photoIndex[0]);
            image.setVisibility(View.VISIBLE);
            empty.setVisibility(View.GONE);
            photoCount.setVisibility(View.VISIBLE);
            photoCount.setText((photoIndex[0]+1)+" / "+photos.size());
            prevPhoto.setVisibility(photos.size()>1?View.VISIBLE:View.GONE);
            nextPhoto.setVisibility(photos.size()>1?View.VISIBLE:View.GONE);
            prevPhoto.setEnabled(photoIndex[0]>0);
            nextPhoto.setEnabled(photoIndex[0]<photos.size()-1);
            delete.setEnabled(true);
            ImageLoader.loadFit(this,image,current.imagePath);
            image.setOnClickListener(v->showWearPhotoFullScreen(current.imagePath,date));
        };
        prevPhoto.setOnClickListener(v->{ if(photoIndex[0]>0){ photoIndex[0]--; render[0].run(); } });
        nextPhoto.setOnClickListener(v->{ if(photoIndex[0]<photos.size()-1){ photoIndex[0]++; render[0].run(); } });
        add.setOnClickListener(v->{
            if(dialog[0]!=null) dialog[0].dismiss();
            openWearPhotoPicker(date,watchId,watchName);
        });
        delete.setOnClickListener(v->{
            if(photos.isEmpty()) return;
            DatabaseHelper.WearPhotoRow current=photos.get(photoIndex[0]);
            new AlertDialog.Builder(this)
                    .setTitle("오착 사진 삭제")
                    .setMessage("현재 사진을 삭제할까요?")
                    .setNegativeButton("취소",null)
                    .setPositiveButton("삭제",(d,which)->{
                        db.deleteWearPhoto(current.id);
                        if(dialog[0]!=null) dialog[0].dismiss();
                        showWearPhotoManager(date,watchId,watchName);
                    }).show();
        });
        render[0].run();

        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        dialog[0]=new AlertDialog.Builder(this)
                .setTitle("오착")
                .setView(scroll)
                .setPositiveButton("닫기",null)
                .create();
        dialog[0].show();
    }

    private void openWearPhotoPicker(String date,long watchId,String watchName) {
        pendingWearPhotoDate=date;
        pendingWearPhotoWatchId=watchId;
        pendingWearPhotoWatchName=watchName==null?"":watchName;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        startActivityForResult(i,REQ_WEAR_PHOTOS);
    }

    private void showWearPhotoFullScreen(String path,String title) {
        ImageView image=new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(UiKit.PHOTO_BG);
        ImageLoader.loadFit(this,image,path);
        ScrollView scroll=new ScrollView(this);
        scroll.addView(image,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(title==null||title.isEmpty()?"오착":title)
                .setView(scroll)
                .setPositiveButton("닫기",null)
                .show();
    }

    private View selectedWearRow(DatabaseHelper.WearRow wr) {
        LinearLayout row=UiKit.row(this);
        ImageView im=watchThumb(wr.imageUrl,58);
        row.addView(im,new LinearLayout.LayoutParams(UiKit.dp(this,58),UiKit.dp(this,58)));
        LinearLayout info=UiKit.column(this); info.setPadding(UiKit.dp(this,10),0,0,0);
        info.addView(UiKit.text(this,wr.watchName,14,true));
        TextView count=UiKit.text(this,"이 날짜 "+formatWearScore(wr.wearRatio),11,true); count.setTextColor(UiKit.GOLD);
        info.addView(count);
        row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        row.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,4));
        if(db.getWatch(wr.watchId)!=null) row.setOnClickListener(v->navigateToWatch(wr.watchId));
        return row;
    }

    private String trimRatio(double value) {
        if(Math.abs(value-Math.rint(value))<0.0001) return String.format(Locale.KOREA,"%.0f",value);
        return String.format(Locale.KOREA,"%.2f",value).replaceAll("0+$","").replaceAll("\\.$","");
    }

    private View buildMonthGrid() {
        GridLayout grid=new GridLayout(this);
        grid.setColumnCount(7);
        String[] days={"일","월","화","수","목","금","토"};
        for(int i=0;i<days.length;i++) {
            TextView tv=UiKit.muted(this,days[i],11); tv.setGravity(Gravity.CENTER);
            if(i==0) tv.setTextColor(UiKit.SUNDAY_RED);
            else if(i==6) tv.setTextColor(UiKit.SATURDAY_BLUE);
            GridLayout.LayoutParams hp=new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED,1f),GridLayout.spec(GridLayout.UNDEFINED,1f));
            hp.width=0;
            grid.addView(tv,hp);
        }

        Calendar first=(Calendar)visibleMonth.clone(); first.set(Calendar.DAY_OF_MONTH,1);
        Calendar last=(Calendar)first.clone(); last.set(Calendar.DAY_OF_MONTH,last.getActualMaximum(Calendar.DAY_OF_MONTH));
        Map<String,List<DatabaseHelper.WearRow>> records=db.getWearRange(dateFormat.format(first.getTime()),dateFormat.format(last.getTime()));
        int dow=first.get(Calendar.DAY_OF_WEEK);
        int offset=dow-1;
        int total=first.getActualMaximum(Calendar.DAY_OF_MONTH);
        String today=dateFormat.format(new Date());

        int requiredCells = offset + total;
        int weekRows = Math.max(4, (requiredCells + 6) / 7);
        int cellCount = weekRows * 7;
        for(int cell=0;cell<cellCount;cell++) {
            LinearLayout box=UiKit.column(this);
            box.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            box.setPadding(UiKit.dp(this,1),UiKit.dp(this,2),UiKit.dp(this,1),UiKit.dp(this,2));
            GridLayout.LayoutParams gp=new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED,1f),GridLayout.spec(GridLayout.UNDEFINED,1f));
            gp.width=0; gp.height=UiKit.dp(this,70); box.setLayoutParams(gp);
            int day=cell-offset+1;
            if(day>=1&&day<=total) {
                Calendar c=(Calendar)first.clone(); c.set(Calendar.DAY_OF_MONTH,day);
                String key=dateFormat.format(c.getTime());
                TextView dt=UiKit.text(this,String.valueOf(day),11,key.equals(today));
                dt.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams dateLp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,18));
                box.addView(dt,dateLp);
                int weekday=cell%7;
                if(weekday==0) dt.setTextColor(UiKit.SUNDAY_RED);
                else if(weekday==6) dt.setTextColor(UiKit.SATURDAY_BLUE);
                else if(key.equals(selectedCalendarDate)) dt.setTextColor(UiKit.GOLD);

                FrameLayout photoArea=new FrameLayout(this);
                LinearLayout.LayoutParams areaLp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,46));
                box.addView(photoArea,areaLp);

                List<DatabaseHelper.WearRow> rows=records.get(key);
                if(rows!=null&&!rows.isEmpty()) {
                    if(rows.size()==1) {
                        ImageView im=calendarWatchThumb(rows.get(0).imageUrl,42);
                        FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(UiKit.dp(this,42),UiKit.dp(this,42));
                        ip.gravity=Gravity.CENTER;
                        photoArea.addView(im,ip);
                    } else {
                        LinearLayout photos=UiKit.row(this);
                        photos.setGravity(Gravity.CENTER);
                        int size=22;
                        for(int i=0;i<Math.min(2,rows.size());i++) {
                            DatabaseHelper.WearRow wr=rows.get(i);
                            ImageView im=calendarWatchThumb(wr.imageUrl,size);
                            LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(UiKit.dp(this,size),UiKit.dp(this,size));
                            if(i>0) pp.setMargins(UiKit.dp(this,1),0,0,0);
                            photos.addView(im,pp);
                        }
                        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
                        pp.gravity=Gravity.CENTER;
                        photoArea.addView(photos,pp);
                    }
                }
                box.setOnClickListener(v->showWearDialog(key));
            }
            grid.addView(box);
        }
        return grid;
    }

    private ImageView calendarWatchThumb(String imageUrl,int sizeDp) {
        ImageView im=new ImageView(this);
        im.setScaleType(ImageView.ScaleType.FIT_CENTER);
        im.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,8,this));
        im.setClipToOutline(true);
        im.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        ImageLoader.loadFit(this,im,imageUrl);
        return im;
    }

    private ImageView watchThumb(String imageUrl,int sizeDp) {
        ImageView im=new ImageView(this);
        im.setScaleType(ImageView.ScaleType.CENTER_CROP);
        im.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,10,this));
        im.setClipToOutline(true);
        im.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        ImageLoader.load(this,im,imageUrl);
        return im;
    }

    private Button overlayArrowButton(String label) {
        Button button=new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(24);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(0,0,0,UiKit.dp(this,2));
        button.setElevation(0);
        button.setBackground(UiKit.rounded(Color.argb(92,22,32,51),Color.argb(70,255,255,255),24,this));
        return button;
    }

    // STRAPS -----------------------------------------------------------------

    private void showStraps(int filterWidth) {
        strapWidthFilter=filterWidth;
        selectNav(navStraps);
        LinearLayout p=page();
        LinearLayout head=UiKit.row(this);
        LinearLayout h=UiKit.column(this);
        h.addView(UiKit.eyebrow(this,"STRAP LIBRARY"));
        h.addView(UiKit.text(this,"스트랩",27,true));
        head.addView(h,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button add=UiKit.primaryButton(this,"+ 스트랩");
        add.setOnClickListener(v->showStrapDialog(null));
        head.addView(add);
        p.addView(head);
        p.addView(UiKit.spacer(this,10));

        LinearLayout filters=UiKit.row(this);
        LinearLayout widthBox=UiKit.column(this);
        widthBox.addView(UiKit.label(this,"Lug width"));
        Spinner widthSpinner=new Spinner(this);
        String[] widthItems={"All","18 mm","19 mm","20 mm","21 mm","22 mm","Extra"};
        widthSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,widthItems));
        decorateSpinner(widthSpinner);
        int widthIndex=filterWidth==0?0:(filterWidth<0?6:Math.max(1,Math.min(5,filterWidth-17)));
        widthSpinner.setSelection(widthIndex);
        widthBox.addView(widthSpinner);
        filters.addView(widthBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

        LinearLayout materialBox=UiKit.column(this);
        materialBox.setPadding(UiKit.dp(this,8),0,0,0);
        materialBox.addView(UiKit.label(this,"Material"));
        Spinner materialSpinner=new Spinner(this);
        String[] materialItems={"All","Leather","Bracelet","Rubber","Nato","Mesh","Extra"};
        materialSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,materialItems));
        decorateSpinner(materialSpinner);
        int materialIndex=0;
        for(int i=0;i<materialItems.length;i++) if(materialItems[i].equalsIgnoreCase(strapMaterialFilter)){materialIndex=i;break;}
        materialSpinner.setSelection(materialIndex);
        materialBox.addView(materialSpinner);
        filters.addView(materialBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        p.addView(filters);
        p.addView(UiKit.spacer(this,14));

        widthSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                int selected=position==0?0:(position==6?-1:17+position);
                if(selected!=strapWidthFilter) showStraps(selected);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        materialSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                String selected=String.valueOf(parent.getItemAtPosition(position));
                if(!selected.equalsIgnoreCase(strapMaterialFilter)) {
                    strapMaterialFilter=selected;
                    showStraps(strapWidthFilter);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        if(filterWidth>0) {
            View compatiblePanel=compatibleWatchesPanel(filterWidth);
            if(compatiblePanel!=null) {
                p.addView(compatiblePanel);
                p.addView(UiKit.spacer(this,2));
            }
        } else if(filterWidth<0) {
            p.addView(emptyCard("Extra 폭 스트랩","18 / 19 / 20 / 21 / 22 mm 외 규격의 스트랩을 모아두는 영역입니다. 폭이 특정되지 않으므로 자동 호환 시계는 표시하지 않습니다."));
        }

        List<Strap> all=db.getStraps(filterWidth);
        List<Strap> straps=new ArrayList<>();
        for(Strap strap:all) if(matchesStrapMaterial(strap,strapMaterialFilter)) straps.add(strap);
        if(straps.isEmpty()) {
            p.addView(emptyCard("등록된 스트랩이 없습니다","현재 Lug width / Material 필터에 해당하는 스트랩이 없습니다."));
        } else {
            for(Strap strap:straps) p.addView(strapCard(strap));
        }
        setScrollable(p);
    }

    private boolean matchesStrapMaterial(Strap strap,String filter) {
        if(filter==null||"All".equalsIgnoreCase(filter)) return true;
        return normalizeStrapMaterial(strap==null?"":strap.material).equalsIgnoreCase(filter);
    }

    private String normalizeStrapMaterial(String raw) {
        String x=safeText(raw).trim().toLowerCase(Locale.ROOT);
        if(x.isEmpty()||"기타".equals(x)||"extra".equals(x)) return "Extra";
        if(x.contains("leather")||x.contains("가죽")||x.contains("calf")||x.contains("cowhide")) return "Leather";
        if(x.contains("rubber")||x.contains("러버")||x.contains("silicone")||x.contains("실리콘")) return "Rubber";
        if(x.contains("nato")||x.contains("나토")||x.contains("nylon")) return "Nato";
        if(x.contains("mesh")||x.contains("메쉬")||x.contains("milanese")) return "Mesh";
        if(x.contains("bracelet")||x.contains("브레이슬릿")||x.contains("steel")||x.contains("metal")) return "Bracelet";
        return "Extra";
    }

    private View compatibleWatchesPanel(int widthMm) {
        List<Watch> watches=db.getCompatibleWatches(widthMm);
        if(watches.isEmpty()) return null;

        LinearLayout card=UiKit.card(this);
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row=UiKit.row(this);
        for(Watch w:watches) {
            LinearLayout item=UiKit.column(this);
            item.setGravity(Gravity.CENTER);
            ImageView im=watchThumb(w.representativeImageUrl,82);
            item.addView(im,new LinearLayout.LayoutParams(UiKit.dp(this,82),UiKit.dp(this,82)));
            String displayName=(w.brand.isEmpty()?"":w.brand+" · ")+w.modelName;
            TextView name=UiKit.text(this,displayName,11,true);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(UiKit.dp(this,112),ViewGroup.LayoutParams.WRAP_CONTENT);
            np.setMargins(0,UiKit.dp(this,5),0,0);
            item.addView(name,np);
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(UiKit.dp(this,120),ViewGroup.LayoutParams.WRAP_CONTENT);
            ip.setMargins(0,0,UiKit.dp(this,8),0);
            row.addView(item,ip);
            item.setOnClickListener(v->showWatchDetailPopup(w));
        }
        scroll.addView(row);
        card.addView(scroll);
        return card;
    }

    private View strapCard(Strap s) {
        LinearLayout card=UiKit.card(this);
        LinearLayout row=UiKit.row(this);
        ImageView im=new ImageView(this);
        im.setScaleType(ImageView.ScaleType.FIT_CENTER);
        im.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,14,this));
        im.setClipToOutline(true);
        im.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        ImageLoader.loadFit(this,im,s.imagePath);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(UiKit.dp(this,96),UiKit.dp(this,112));
        ip.setMargins(0,0,UiKit.dp(this,14),0);
        row.addView(im,ip);

        LinearLayout info=UiKit.column(this);
        info.addView(UiKit.eyebrow(this,(s.maker.isEmpty()?"STRAP":s.maker.toUpperCase(Locale.ROOT))+" · "+strapWidthLabel(s.widthMm)));
        info.addView(UiKit.text(this,s.name,18,true));
        String desc=String.join(" · ",nonEmpty(s.color,normalizeStrapMaterial(s.material),s.buckle));
        if(!desc.isEmpty()) info.addView(UiKit.muted(this,desc,12));
        List<Watch> compatible=s.widthMm>0?db.getCompatibleWatches(s.widthMm):new ArrayList<>();
        info.addView(UiKit.spacer(this,7));
        if(s.widthMm<0) {
            info.addView(UiKit.muted(this,"Extra 폭",11));
        } else if(compatible.isEmpty()) {
            info.addView(UiKit.muted(this,"현재 호환 시계 없음",11));
        } else {
            List<String> compatibleNames=new ArrayList<>();
            for(Watch watch:compatible) {
                compatibleNames.add((watch.brand.isEmpty()?"":watch.brand+" · ")+watch.modelName);
            }
            TextView compatibleText=UiKit.muted(this,"호환 시계 · "+String.join(" · ",compatibleNames),11);
            compatibleText.setSingleLine(false);
            info.addView(compatibleText);
        }
        row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        card.addView(row);

        LinearLayout actions=UiKit.row(this);
        actions.setPadding(0,UiKit.dp(this,10),0,0);
        Button edit=UiKit.secondaryButton(this,"수정");
        Button link=UiKit.secondaryButton(this,"구매 링크");
        Button del=UiKit.secondaryButton(this,"삭제");
        edit.setOnClickListener(v->showStrapDialog(s));
        link.setEnabled(s.purchaseUrl!=null&&!s.purchaseUrl.trim().isEmpty());
        link.setOnClickListener(v->{
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(s.purchaseUrl)));
            } catch(Exception e) {
                Toast.makeText(this,"구매 링크를 열 수 없습니다.",Toast.LENGTH_SHORT).show();
            }
        });
        del.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("스트랩 삭제")
                .setMessage(s.name+"을 삭제할까요?")
                .setNegativeButton("취소",null)
                .setPositiveButton("삭제",(d,x)->{db.deleteStrap(s.id);showStraps(s.widthMm);})
                .show());
        actions.addView(edit,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        actions.addView(link,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        actions.addView(del,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        card.addView(actions);
        return card;
    }

    private void showStrapDialog(Strap existing) {
        showStrapDialogInternal(existing,null,"","",false);
    }

    private void showStrapDialog(Strap existing,SharedPageAnalyzer.Result sharedPage) {
        showStrapDialogInternal(existing,sharedPage,"","",false);
    }

    private void showStrapDialog(Strap existing,String sharedUrl,String sharedText,boolean autoLookup) {
        showStrapDialogInternal(existing,null,sharedUrl,sharedText,autoLookup);
    }

    private void showStrapDialogInternal(Strap existing,SharedPageAnalyzer.Result sharedPage,
                                         String sharedUrl,String sharedText,boolean autoLookup) {
        Strap base=existing==null?new Strap():existing;
        StrapProductLookup.Result sharedResult=sharedPage==null?null:StrapProductLookup.fromPage(sharedPage);
        if(existing==null&&sharedResult!=null) {
            base.purchaseUrl=firstNonEmpty(sharedResult.finalUrl,sharedPage.finalUrl);
            base.maker=safeText(sharedResult.maker);
            base.name=safeText(sharedResult.name);
            base.widthMm=sharedResult.widthMm>0?sharedResult.widthMm:-1;
            base.color=safeText(sharedResult.color);
            base.material=safeText(sharedResult.material).isEmpty()?"Extra":sharedResult.material;
            base.buckle=safeText(sharedResult.buckle);
            base.notes=safeText(sharedResult.description);
        } else if(existing==null&&!safeText(sharedUrl).isEmpty()) {
            base.purchaseUrl=sharedUrl;
        }
        pendingStrapImageUri=null;
        pendingStrapImageProcessing=false;
        pendingStrapOriginalPath="";
        pendingStrapProcessedPath="";
        pendingStrapStatus=null;
        pendingStrapReprocessButton=null;

        LinearLayout f=UiKit.column(this);
        f.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,8));

        // 구매 링크를 첫 항목으로 두고, 공개 상품 정보를 자동으로 채울 수 있게 합니다.
        EditText purchaseUrl=addField(f,"구매 링크","https://...",base.purchaseUrl);
        Button lookupLink=UiKit.primaryButton(this,"링크에서 정보 가져오기");
        f.addView(lookupLink);
        pendingStrapStatus=UiKit.muted(this,"",11);
        pendingStrapStatus.setVisibility(View.GONE);
        pendingStrapStatus.setPadding(0,UiKit.dp(this,7),0,UiKit.dp(this,12));
        f.addView(pendingStrapStatus);

        EditText maker=addField(f,"스트랩 제작사","예: Hirsch / Delugs / Accurate Form",base.maker);
        EditText name=addField(f,"스트랩 이름 *","예: 네이비 카프 레더",base.name);
        f.addView(UiKit.label(this,"러그 폭"));
        Spinner width=new Spinner(this);
        String[] ws={"18 mm","19 mm","20 mm","21 mm","22 mm","Extra"};
        width.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ws));
        decorateSpinner(width);
        setStrapWidthSelection(width,base.widthMm);
        f.addView(width);
        f.addView(UiKit.spacer(this,10));

        ImageView preview=new ImageView(this);
        pendingStrapPreview=preview;
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,16,this));
        preview.setClipToOutline(true);
        preview.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        ImageLoader.loadFit(this,preview,base.imagePath);
        f.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,190)));
        LinearLayout strapPhotoActions=UiKit.row(this);
        Button pick=UiKit.secondaryButton(this,"사진 직접 선택");
        Button reprocess=UiKit.secondaryButton(this,"배경 다시 제거");
        pendingStrapReprocessButton=reprocess;
        pick.setOnClickListener(v->openStrapImagePicker());
        reprocess.setEnabled(!safeText(base.imagePath).isEmpty());
        reprocess.setOnClickListener(v->{
            String source=!pendingStrapProcessedPath.isEmpty()?pendingStrapProcessedPath:safeText(base.imagePath);
            if(source.isEmpty()) {
                Toast.makeText(this,"다시 처리할 스트랩 사진이 없습니다.",Toast.LENGTH_SHORT).show();
                return;
            }
            pendingStrapImageProcessing=true;
            reprocess.setEnabled(false);
            showStrapStatus("기존 사진에서 스트랩과 버클을 다시 인식하고 배경을 제거하는 중…");
            executor.execute(()->{
                try {
                    ImageLoader.StrapImageImportResult result=ImageLoader.reprocessStrapImage(this,source);
                    runOnUiThread(()->{
                        pendingStrapImageProcessing=false;
                        pendingStrapOriginalPath=safeText(result.originalPath);
                        pendingStrapProcessedPath=safeText(result.displayPath);
                        ImageLoader.loadFit(this,preview,pendingStrapProcessedPath);
                        showStrapStatus(result.backgroundChanged
                                ? "새 분리기로 배경을 다시 제거했습니다."
                                : "피사체 분리가 불확실해 기존 사진을 유지했습니다.");
                        reprocess.setEnabled(true);
                    });
                } catch(Exception e) {
                    runOnUiThread(()->{
                        pendingStrapImageProcessing=false;
                        showStrapStatus("배경 재처리 실패: "+e.getMessage());
                        reprocess.setEnabled(true);
                    });
                }
            });
        });
        strapPhotoActions.addView(pick,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        strapPhotoActions.addView(reprocess,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        f.addView(strapPhotoActions);
        f.addView(UiKit.spacer(this,10));

        EditText color=addField(f,"색상","Navy / Brown / Black",base.color);
        f.addView(UiKit.label(this,"재질"));
        Spinner material=new Spinner(this);
        String[] materialItems={"Leather","Bracelet","Rubber","Nato","Mesh","Extra"};
        material.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,materialItems));
        decorateSpinner(material);
        setStrapMaterialSelection(material,base.material);
        f.addView(material);
        EditText buckle=addField(f,"버클/특징","Deployant / Pin buckle",base.buckle);
        EditText notes=addField(f,"메모","구매처, 길이, 특징 등",base.notes);
        notes.setSingleLine(false);
        notes.setMinLines(2);

        lookupLink.setOnClickListener(v->{
            String link=val(purchaseUrl);
            if(link.isEmpty()) {
                purchaseUrl.setError("구매 링크를 입력하세요.");
                return;
            }
            lookupLink.setEnabled(false);
            pendingStrapImageProcessing=true;
            showStrapStatus("구매 페이지를 확인하는 중…");
            executor.execute(()->{
                try {
                    StrapProductLookup.Result result=StrapProductLookup.lookup(link,sharedText,message->
                            runOnUiThread(()->showStrapStatus(message)));
                    String importedImage="";
                    String imageNote="";
                    if(!result.imageCandidates.isEmpty() || !safeText(result.imageUrl).isEmpty()) {
                        try {
                            ImageLoader.StrapImageImportResult imageResult=importBestStrapImage(result);
                            importedImage=imageResult.displayPath;
                            pendingStrapOriginalPath=imageResult.originalPath;
                            imageNote=imageResult.backgroundChanged
                                    ? "실제 상품사진을 가져와 피사체를 분리하고 배경을 제거했습니다."
                                    : "실제 상품사진을 가져왔지만 피사체 분리가 불확실해 원본을 유지했습니다.";
                        } catch(Exception imageError) {
                            imageNote="상품사진 자동 저장 실패: "+imageError.getMessage();
                        }
                    } else {
                        imageNote="상품 페이지에서 실제 상품사진 후보를 찾지 못했습니다.";
                    }
                    String finalImportedImage=importedImage;
                    String finalImageNote=imageNote;
                    runOnUiThread(()->{
                        pendingStrapImageProcessing=false;
                        if(!safeText(result.finalUrl).isEmpty()) purchaseUrl.setText(result.finalUrl);
                        if(!safeText(result.maker).isEmpty()) maker.setText(result.maker);
                        if(!safeText(result.name).isEmpty()) name.setText(result.name);
                        if(result.widthMm>0) setStrapWidthSelection(width,result.widthMm);
                        if(!safeText(result.color).isEmpty()) color.setText(result.color);
                        setStrapMaterialSelection(material,result.material);
                        if(!safeText(result.buckle).isEmpty()) buckle.setText(result.buckle);
                        if(val(notes).isEmpty()&&!safeText(result.description).isEmpty()) notes.setText(result.description);
                        if(!safeText(finalImportedImage).isEmpty()) {
                            pendingStrapProcessedPath=finalImportedImage;
                            ImageLoader.loadFit(this,preview,finalImportedImage);
                            if(pendingStrapReprocessButton!=null) pendingStrapReprocessButton.setEnabled(true);
                        }
                        showStrapStatus(result.message+"\n"+finalImageNote);
                        lookupLink.setEnabled(true);
                    });
                } catch(Exception e) {
                    runOnUiThread(()->{
                        pendingStrapImageProcessing=false;
                        showStrapStatus("링크 확인 실패: "+e.getMessage()+"\n수동으로 입력하거나 다른 상품 링크를 사용해 주세요.");
                        lookupLink.setEnabled(true);
                    });
                }
            });
        });

        ScrollView sc=new ScrollView(this); sc.addView(f);
        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle(existing==null?"스트랩 추가":"스트랩 수정")
                .setView(sc)
                .setNegativeButton("취소",null)
                .setPositiveButton("저장",null)
                .create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(val(name).isEmpty()){name.setError("이름은 필수입니다.");return;}
            if(pendingStrapImageProcessing) {
                Toast.makeText(this,"스트랩 사진을 처리 중입니다. 잠시 후 다시 저장해 주세요.",Toast.LENGTH_SHORT).show();
                return;
            }
            base.purchaseUrl=val(purchaseUrl);
            base.maker=val(maker);
            base.name=val(name);
            base.widthMm=width.getSelectedItemPosition()==5?-1:18+width.getSelectedItemPosition();
            base.color=val(color);
            base.material=String.valueOf(material.getSelectedItem());
            base.buckle=val(buckle);
            base.notes=val(notes);
            if(!pendingStrapProcessedPath.isEmpty()) base.imagePath=pendingStrapProcessedPath;
            db.saveStrap(base);
            pendingStrapPreview=null;
            pendingStrapStatus=null;
            pendingStrapReprocessButton=null;
            pendingStrapImageUri=null;
            pendingStrapOriginalPath="";
            pendingStrapProcessedPath="";
            dlg.dismiss();
            showStraps(base.widthMm);
        }));
        dlg.show();
        if(sharedResult!=null) {
            showStrapStatus(sharedResult.message);
            if(!sharedResult.imageCandidates.isEmpty() || !safeText(sharedResult.imageUrl).isEmpty()) {
                pendingStrapImageProcessing=true;
                executor.execute(()->{
                    try {
                        ImageLoader.StrapImageImportResult imageResult=importBestStrapImage(sharedResult);
                        runOnUiThread(()->{
                            pendingStrapImageProcessing=false;
                            pendingStrapOriginalPath=imageResult.originalPath;
                            pendingStrapProcessedPath=imageResult.displayPath;
                            ImageLoader.loadFit(this,preview,imageResult.displayPath);
                            if(pendingStrapReprocessButton!=null) pendingStrapReprocessButton.setEnabled(true);
                            showStrapStatus(sharedResult.message+"\n실제 상품사진을 가져와 미리보기에 반영했습니다.");
                        });
                    } catch(Exception e) {
                        runOnUiThread(()->{
                            pendingStrapImageProcessing=false;
                            showStrapStatus(sharedResult.message+"\n상품사진 자동 저장 실패: "+e.getMessage());
                        });
                    }
                });
            }
        } else if(autoLookup && !val(purchaseUrl).isEmpty()) {
            lookupLink.postDelayed(lookupLink::performClick,300);
        }
    }

    private ImageLoader.StrapImageImportResult importBestStrapImage(StrapProductLookup.Result result) throws Exception {
        List<String> candidates=new ArrayList<>();
        for(String url:result.imageCandidates) {
            if(!safeText(url).isEmpty()&&!candidates.contains(url)) candidates.add(url);
        }
        if(!safeText(result.imageUrl).isEmpty()&&!candidates.contains(result.imageUrl)) candidates.add(result.imageUrl);
        if(candidates.isEmpty()) throw new IllegalStateException("상품사진 후보가 없습니다.");

        Exception last=null;
        int limit=Math.min(6,candidates.size());
        for(int i=0;i<limit;i++) {
            String candidate=candidates.get(i);
            int index=i+1;
            runOnUiThread(()->showStrapStatus("상품사진 후보 "+index+" / "+limit+"을 확인하는 중…"));
            try {
                return ImageLoader.importRemoteStrapImageNormalized(this,candidate,result.finalUrl);
            } catch(Exception e) {
                last=e;
            }
        }
        throw last==null?new IllegalStateException("사용 가능한 상품사진을 찾지 못했습니다."):last;
    }

    private void showStrapStatus(String message) {
        if(pendingStrapStatus==null) return;
        pendingStrapStatus.setVisibility(View.VISIBLE);
        pendingStrapStatus.setText(message==null?"":message.trim());
    }

    private void setStrapWidthSelection(Spinner spinner,int widthMm) {
        int selection=(widthMm>=18&&widthMm<=22)?widthMm-18:5;
        spinner.setSelection(selection);
    }

    private void setStrapMaterialSelection(Spinner spinner,String raw) {
        String normalized=normalizeStrapMaterial(raw);
        String[] values={"Leather","Bracelet","Rubber","Nato","Mesh","Extra"};
        int selection=5;
        for(int i=0;i<values.length;i++) {
            if(values[i].equalsIgnoreCase(normalized)) { selection=i; break; }
        }
        spinner.setSelection(selection);
    }

    private void openWatchImagePicker() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i,REQ_WATCH_IMAGE);
    }

    private void openStrapImagePicker() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i,REQ_STRAP_IMAGE);
    }

    private void openCasebackImagePicker(boolean direct,long watchId) {
        pendingDirectCasebackWatchId=direct?watchId:-1;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i,direct?REQ_CASEBACK_DIRECT:REQ_CASEBACK_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_WISH_IMAGE&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null) {
            pendingWishImageUri=data.getData();
            pendingWishImageProcessing=true;
            pendingWishImageClear=false;
            pendingWishProcessedPath="";
            pendingWishOriginalPath="";
            if(pendingWishPhotoStatus!=null) pendingWishPhotoStatus.setText("Wish 시계 사진을 정리하는 중…");
            Uri selected=pendingWishImageUri;
            executor.execute(()->{
                try {
                    ImageLoader.WatchImageImportResult result=ImageLoader.importWatchUriNormalized(this,selected);
                    runOnUiThread(()->{
                        pendingWishImageProcessing=false;
                        pendingWishOriginalPath=result.originalPath==null?"":result.originalPath;
                        pendingWishProcessedPath=result.displayPath==null?"":result.displayPath;
                        if(pendingWishPreview!=null) ImageLoader.loadFit(this,pendingWishPreview,pendingWishProcessedPath);
                        if(pendingWishPhotoStatus!=null) pendingWishPhotoStatus.setText(result.backgroundChanged
                                ? "배경을 컬렉션 사진과 같은 색으로 정리했습니다."
                                : "원본을 유지해 Wish 사진으로 저장합니다.");
                    });
                } catch(Exception e) {
                    runOnUiThread(()->{
                        pendingWishImageProcessing=false;
                        if(pendingWishPhotoStatus!=null) pendingWishPhotoStatus.setText("사진 처리 실패: "+e.getMessage());
                        Toast.makeText(this,"Wish 사진 처리 실패: "+e.getMessage(),Toast.LENGTH_SHORT).show();
                    });
                }
            });
            return;
        }
        if(requestCode==REQ_HERITAGE_PHOTOS && resultCode==RESULT_OK && data!=null) {
            List<Uri> uris=new ArrayList<>();
            ClipData clip=data.getClipData();
            if(clip!=null) {
                for(int i=0;i<clip.getItemCount();i++) {
                    Uri uri=clip.getItemAt(i).getUri();
                    if(uri!=null) uris.add(uri);
                }
            } else if(data.getData()!=null) {
                uris.add(data.getData());
            }
            if(!uris.isEmpty()) {
                Toast.makeText(this,"헤리티지 사진을 가져오는 중입니다…",Toast.LENGTH_SHORT).show();
                executor.execute(()->{
                    int imported=0;
                    for(Uri uri:uris) {
                        try {
                            String path=ImageLoader.importWearPhoto(this,uri);
                            if(path!=null&&!path.isEmpty()) {
                                pendingHeritagePhotoPaths.add(path);
                                imported++;
                            }
                        } catch(Exception ignored) {}
                    }
                    final int count=imported;
                    runOnUiThread(()->{
                        refreshPendingHeritagePhotoPreview();
                        Toast.makeText(this,count+"장의 사진을 추가했습니다.",Toast.LENGTH_SHORT).show();
                    });
                });
            }
            return;
        }
        if((requestCode==REQ_CASEBACK_IMAGE||requestCode==REQ_CASEBACK_DIRECT)
                && resultCode==RESULT_OK&&data!=null&&data.getData()!=null) {
            Uri uri=data.getData();
            long directWatchId=pendingDirectCasebackWatchId;
            if(requestCode==REQ_CASEBACK_IMAGE) {
                pendingCasebackImageUri=uri;
                pendingCasebackImageClear=false;
                pendingCasebackRestoreOriginal=false;
                pendingCasebackImageProcessing=true;
                if(pendingCasebackPhotoStatus!=null) {
                    pendingCasebackPhotoStatus.setVisibility(View.VISIBLE);
                    pendingCasebackPhotoStatus.setText("케이스백 배경을 정리하는 중…");
                }
            }
            executor.execute(()->{
                try {
                    ImageLoader.CasebackImageImportResult result=ImageLoader.importCasebackUriNormalized(this,uri);
                    runOnUiThread(()->{
                        if(requestCode==REQ_CASEBACK_DIRECT) {
                            pendingDirectCasebackWatchId=-1;
                            if(directWatchId>0) {
                                db.updateCasebackImages(directWatchId,result.originalPath,result.displayPath);
                                Toast.makeText(this,"케이스백 사진을 저장했습니다.",Toast.LENGTH_SHORT).show();
                                Watch fresh=db.getWatch(directWatchId);
                                if(fresh!=null) showWatchDetailPopup(fresh);
                            }
                        } else {
                            pendingCasebackImageProcessing=false;
                            pendingCasebackOriginalPath=result.originalPath==null?"":result.originalPath;
                            pendingCasebackProcessedPath=result.displayPath==null?"":result.displayPath;
                            if(pendingCasebackPreview!=null) ImageLoader.loadFit(this,pendingCasebackPreview,pendingCasebackProcessedPath);
                            if(pendingCasebackPhotoStatus!=null) {
                                pendingCasebackPhotoStatus.setVisibility(View.VISIBLE);
                                pendingCasebackPhotoStatus.setText(result.backgroundChanged
                                        ? "케이스백 배경을 대표사진과 같은 색으로 통일했습니다."
                                        : "복잡한 배경으로 판단되어 방향만 보정하고 원본을 유지했습니다.");
                            }
                        }
                    });
                } catch(Exception e) {
                    runOnUiThread(()->{
                        pendingCasebackImageProcessing=false;
                        pendingDirectCasebackWatchId=-1;
                        Toast.makeText(this,"케이스백 처리 실패: "+e.getMessage(),Toast.LENGTH_SHORT).show();
                    });
                }
            });
            return;
        }
        if(requestCode==REQ_WEAR_PHOTOS && resultCode==RESULT_OK && data!=null) {
            final String wearDate=pendingWearPhotoDate;
            final long watchId=pendingWearPhotoWatchId;
            final String watchName=pendingWearPhotoWatchName;
            List<Uri> uris=new ArrayList<>();
            ClipData clip=data.getClipData();
            if(clip!=null) {
                for(int i=0;i<clip.getItemCount();i++) {
                    Uri uri=clip.getItemAt(i).getUri();
                    if(uri!=null) uris.add(uri);
                }
            } else if(data.getData()!=null) {
                uris.add(data.getData());
            }
            if(!uris.isEmpty() && watchId>0 && !wearDate.isEmpty()) {
                Toast.makeText(this,"오착 사진을 저장하는 중입니다…",Toast.LENGTH_SHORT).show();
                executor.execute(()->{
                    int saved=0;
                    for(Uri uri:uris) {
                        try {
                            String path=ImageLoader.importWearPhoto(this,uri);
                            if(path!=null&&!path.isEmpty()) {
                                db.addWearPhoto(watchId,watchName,wearDate,path);
                                saved++;
                            }
                        } catch(Exception ignored) {}
                    }
                    final int savedCount=saved;
                    runOnUiThread(()->{
                        pendingWearPhotoDate="";
                        pendingWearPhotoWatchId=-1;
                        pendingWearPhotoWatchName="";
                        Toast.makeText(this,savedCount+"장의 오착 사진을 저장했습니다.",Toast.LENGTH_SHORT).show();
                        showWearPhotoManager(wearDate,watchId,watchName);
                    });
                });
            }
            return;
        }
        if((requestCode==REQ_COLLECTION_IMAGE_ADD||requestCode==REQ_COLLECTION_IMAGE_REPLACE)
                && resultCode==RESULT_OK&&data!=null&&data.getData()!=null) {
            Uri uri=data.getData();
            long replaceId=pendingCollectionPhotoReplaceId;
            pendingCollectionPhotoReplaceId=-1;
            Toast.makeText(this,"컬렉션 사진을 처리하는 중입니다…",Toast.LENGTH_SHORT).show();
            executor.execute(() -> {
                try {
                    ImageLoader.CollectionPhotoImportResult result=ImageLoader.importCollectionPhoto(this,uri);
                    CollectionPhoto photo=new CollectionPhoto();
                    if(requestCode==REQ_COLLECTION_IMAGE_REPLACE && replaceId>0) {
                        DatabaseHelper.CollectionPhotoRow existing=db.getCollectionPhoto(replaceId);
                        photo.id=replaceId;
                        photo.displayOrder=existing==null?0:existing.displayOrder;
                    } else {
                        photo.displayOrder=db.nextCollectionPhotoOrder();
                    }
                    photo.originalPath=result.originalPath;
                    photo.displayPath=result.displayPath;
                    long saved=db.saveCollectionPhoto(photo);
                    List<DatabaseHelper.CollectionPhotoRow> rows=db.getCollectionPhotos();
                    int idx=0;
                    for(int i=0;i<rows.size();i++) if(rows.get(i).id==saved) { idx=i; break; }
                    int finalIdx=idx;
                    runOnUiThread(() -> {
                        collectionPhotoIndex=finalIdx;
                        Toast.makeText(this,"컬렉션 사진을 저장했습니다.",Toast.LENGTH_SHORT).show();
                        showCollection();
                    });
                } catch(Exception e) {
                    runOnUiThread(() -> Toast.makeText(this,"컬렉션 사진 처리 실패: "+e.getMessage(),Toast.LENGTH_SHORT).show());
                }
            });
            return;
        }
        if(requestCode==REQ_STRAP_IMAGE&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null) {
            pendingStrapImageUri=data.getData();
            pendingStrapImageProcessing=true;
            pendingStrapProcessedPath="";
            pendingStrapOriginalPath="";
            showStrapStatus("선택한 스트랩 사진의 피사체를 인식하고 배경을 제거하는 중…");
            Uri selected=pendingStrapImageUri;
            executor.execute(()->{
                try {
                    ImageLoader.StrapImageImportResult result=ImageLoader.importStrapUriNormalized(this,selected);
                    runOnUiThread(()->{
                        pendingStrapImageProcessing=false;
                        pendingStrapOriginalPath=result.originalPath==null?"":result.originalPath;
                        pendingStrapProcessedPath=result.displayPath==null?"":result.displayPath;
                        if(pendingStrapPreview!=null) ImageLoader.loadFit(this,pendingStrapPreview,pendingStrapProcessedPath);
                        if(pendingStrapReprocessButton!=null) pendingStrapReprocessButton.setEnabled(true);
                        showStrapStatus(result.backgroundChanged
                                ? "스트랩과 버클을 분리해 투명 배경으로 정리했습니다."
                                : "피사체 분리가 불확실해 원본을 유지했습니다.");
                    });
                } catch(Exception e) {
                    runOnUiThread(()->{
                        pendingStrapImageProcessing=false;
                        showStrapStatus("사진 처리 실패: "+e.getMessage());
                        Toast.makeText(this,"스트랩 사진 처리 실패: "+e.getMessage(),Toast.LENGTH_SHORT).show();
                    });
                }
            });
            return;
        }
        if(requestCode==REQ_WATCH_IMAGE&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null) {
            pendingWatchImageUri=data.getData();
            pendingWatchImageClear=false;
            pendingWatchRestoreOriginal=false;
            pendingWatchImageProcessing=true;
            pendingWatchProcessedPath="";
            pendingWatchOriginalPath="";
            pendingWatchCalendarPath="";
            if(pendingWatchPhotoStatus!=null) pendingWatchPhotoStatus.setText("사진을 가져오고 캘린더용 시계 본체 이미지까지 함께 만드는 중…");
            Uri selected=pendingWatchImageUri;
            executor.execute(()->{
                try {
                    ImageLoader.WatchImageImportResult result=ImageLoader.importWatchUriNormalized(this,selected);
                    runOnUiThread(()->{
                        pendingWatchImageProcessing=false;
                        pendingWatchOriginalPath=result.originalPath==null?"":result.originalPath;
                        pendingWatchProcessedPath=result.displayPath==null?"":result.displayPath;
                        pendingWatchCalendarPath=result.calendarPath==null?"":result.calendarPath;
                        if(pendingWatchPreview!=null) ImageLoader.load(this,pendingWatchPreview,pendingWatchProcessedPath);
                        if(pendingWatchPhotoStatus!=null) {
                            pendingWatchPhotoStatus.setText(result.backgroundChanged
                                    ? "배경을 #FCFBF7로 통일했고 캘린더용 시계 본체 이미지도 함께 만들었습니다. 원본 복구도 가능합니다."
                                    : "원본을 유지하되 캘린더용 시계 본체 이미지는 함께 만들었습니다. 원본은 별도 보관됩니다.");
                        }
                    });
                } catch(Exception e) {
                    runOnUiThread(()->{
                        pendingWatchImageProcessing=false;
                        if(pendingWatchPhotoStatus!=null) pendingWatchPhotoStatus.setText("사진 처리 실패: "+e.getMessage());
                        Toast.makeText(this,"대표 사진 처리 실패: "+e.getMessage(),Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }

    private String strapWidthLabel(int widthMm) {
        return widthMm<0?"Extra":widthMm+" mm";
    }

    private View buildCollectionPhotosSection() {
        LinearLayout card=UiKit.card(this);
        card.addView(UiKit.eyebrow(this,"MY WATCHES"));
        card.addView(UiKit.spacer(this,8));
        List<DatabaseHelper.CollectionPhotoRow> photos=db.getCollectionPhotos();
        if(photos.isEmpty()) {
            Button add=UiKit.secondaryButton(this,"컬렉션 첨부하기");
            add.setOnClickListener(v->openCollectionPhotoPicker(false,-1));
            card.addView(add);
            return card;
        }
        if(collectionPhotoIndex>=photos.size()) collectionPhotoIndex=photos.size()-1;
        if(collectionPhotoIndex<0) collectionPhotoIndex=0;
        DatabaseHelper.CollectionPhotoRow current=photos.get(collectionPhotoIndex);

        if(photos.size()>1) {
            LinearLayout nav=UiKit.row(this);
            Button prev=UiKit.secondaryButton(this,"‹");
            Button next=UiKit.secondaryButton(this,"›");
            TextView count=UiKit.text(this,(collectionPhotoIndex+1)+" / "+photos.size(),12,true);
            count.setGravity(Gravity.CENTER);
            prev.setEnabled(collectionPhotoIndex>0);
            next.setEnabled(collectionPhotoIndex<photos.size()-1);
            prev.setOnClickListener(v->{ if(collectionPhotoIndex>0){ collectionPhotoIndex--; showCollection(); } });
            next.setOnClickListener(v->{ if(collectionPhotoIndex<photos.size()-1){ collectionPhotoIndex++; showCollection(); } });
            nav.addView(prev,new LinearLayout.LayoutParams(UiKit.dp(this,46),ViewGroup.LayoutParams.WRAP_CONTENT));
            nav.addView(count,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            nav.addView(next,new LinearLayout.LayoutParams(UiKit.dp(this,46),ViewGroup.LayoutParams.WRAP_CONTENT));
            card.addView(nav);
            card.addView(UiKit.spacer(this,7));
        }

        FrameLayout frame=new FrameLayout(this);
        frame.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,16,this));
        frame.setClipToOutline(true);
        frame.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        LinearLayout.LayoutParams frameLp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,205));
        card.addView(frame,frameLp);

        ImageView image=new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(false);
        ImageLoader.loadFit(this,image,current.displayPath);
        frame.addView(image,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        image.setOnClickListener(v->showCollectionPhotoPopup());
        return card;
    }

    private void openCollectionPhotoPicker(boolean replace,long photoId) {
        pendingCollectionPhotoReplaceId=replace?photoId:-1;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, replace?REQ_COLLECTION_IMAGE_REPLACE:REQ_COLLECTION_IMAGE_ADD);
    }

    private void showCollectionPhotoPopup() {
        List<DatabaseHelper.CollectionPhotoRow> photos=db.getCollectionPhotos();
        if(photos.isEmpty()) return;
        if(collectionPhotoIndex>=photos.size()) collectionPhotoIndex=photos.size()-1;
        if(collectionPhotoIndex<0) collectionPhotoIndex=0;
        final AlertDialog[] dialog=new AlertDialog[1];

        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,16),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,10));
        LinearLayout nav=UiKit.row(this);
        TextView count=UiKit.text(this,(collectionPhotoIndex+1)+" / "+photos.size(),14,true);
        nav.addView(count,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button prev=UiKit.secondaryButton(this,"‹");
        Button next=UiKit.secondaryButton(this,"›");
        prev.setEnabled(collectionPhotoIndex>0);
        next.setEnabled(collectionPhotoIndex<photos.size()-1);
        nav.addView(prev);
        nav.addView(next);
        body.addView(nav);

        ImageView image=new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,16,this));
        image.setClipToOutline(true);
        image.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        ImageLoader.loadFit(this,image,photos.get(collectionPhotoIndex).displayPath);
        body.addView(image,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,360)));

        final float[] zoom={1.0f};
        LinearLayout zoomRow=UiKit.row(this);
        Button zoomOut=UiKit.secondaryButton(this,"축소 -");
        Button zoomIn=UiKit.secondaryButton(this,"확대 +");
        zoomOut.setOnClickListener(v->{ zoom[0]=Math.max(1.0f,zoom[0]-0.25f); image.setScaleX(zoom[0]); image.setScaleY(zoom[0]); });
        zoomIn.setOnClickListener(v->{ zoom[0]=Math.min(3.0f,zoom[0]+0.25f); image.setScaleX(zoom[0]); image.setScaleY(zoom[0]); });
        zoomRow.addView(zoomOut,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        zoomRow.addView(zoomIn,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        body.addView(UiKit.spacer(this,8));
        body.addView(zoomRow);

        LinearLayout actions=UiKit.row(this);
        Button add=UiKit.secondaryButton(this,"추가");
        Button replace=UiKit.secondaryButton(this,"변경");
        Button delete=UiKit.secondaryButton(this,"삭제");
        add.setOnClickListener(v->openCollectionPhotoPicker(false,-1));
        replace.setOnClickListener(v->openCollectionPhotoPicker(true,photos.get(collectionPhotoIndex).id));
        delete.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("컬렉션 사진 삭제")
                .setMessage("현재 사진을 삭제할까요?")
                .setNegativeButton("취소",null)
                .setPositiveButton("삭제",(d,x)->{
                    db.deleteCollectionPhoto(photos.get(collectionPhotoIndex).id);
                    if(collectionPhotoIndex>0&&collectionPhotoIndex>=db.getCollectionPhotos().size()) collectionPhotoIndex--;
                    if(dialog[0]!=null) dialog[0].dismiss();
                    showCollection();
                }).show());
        actions.addView(add,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        actions.addView(replace,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        actions.addView(delete,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        body.addView(UiKit.spacer(this,8));
        body.addView(actions);

        prev.setOnClickListener(v->{ if(collectionPhotoIndex>0){ collectionPhotoIndex--; if(dialog[0]!=null) dialog[0].dismiss(); showCollectionPhotoPopup(); } });
        next.setOnClickListener(v->{ if(collectionPhotoIndex<db.getCollectionPhotos().size()-1){ collectionPhotoIndex++; if(dialog[0]!=null) dialog[0].dismiss(); showCollectionPhotoPopup(); } });

        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        dialog[0]=new AlertDialog.Builder(this)
                .setTitle("컬렉션 사진")
                .setView(scroll)
                .setPositiveButton("닫기",null)
                .create();
        dialog[0].show();
    }

    private void showWatchDetailPopup(Watch w) {
        if(w==null) return;
        final AlertDialog[] detailDialog=new AlertDialog[1];
        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,16),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,16));

        LinearLayout hero=UiKit.card(this);
        hero.setBackground(UiKit.rounded(UiKit.PHOTO_BG,UiKit.LINE,18,this));

        FrameLayout photoStage=new FrameLayout(this);
        photoStage.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,16,this));
        photoStage.setClipToOutline(true);
        photoStage.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        hero.addView(photoStage,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,238)));

        ImageView image=new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(UiKit.PHOTO_BG);
        image.setCameraDistance(UiKit.dp(this,12000));
        ImageLoader.loadFit(this,image,w.representativeImageUrl);
        photoStage.addView(image,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        TextView photoSide=UiKit.text(this,"FRONT",10,true);
        photoSide.setTextColor(UiKit.GOLD);
        photoSide.setBackground(UiKit.rounded(UiKit.PAPER,UiKit.LINE,12,this));
        photoSide.setPadding(UiKit.dp(this,9),UiKit.dp(this,5),UiKit.dp(this,9),UiKit.dp(this,5));
        FrameLayout.LayoutParams sideLp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        sideLp.gravity=Gravity.TOP|Gravity.END;
        sideLp.setMargins(0,UiKit.dp(this,8),UiKit.dp(this,8),0);
        photoStage.addView(photoSide,sideLp);

        LinearLayout emptyCaseback=UiKit.column(this);
        emptyCaseback.setGravity(Gravity.CENTER);
        emptyCaseback.setPadding(UiKit.dp(this,20),UiKit.dp(this,20),UiKit.dp(this,20),UiKit.dp(this,20));
        emptyCaseback.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,16,this));
        emptyCaseback.setVisibility(View.GONE);
        TextView emptyTitle=UiKit.text(this,"케이스백 사진이 없습니다",16,true);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyCaseback.addView(emptyTitle);
        emptyCaseback.addView(UiKit.spacer(this,10));
        Button addCaseback=UiKit.primaryButton(this,"+ 케이스백 사진 추가");
        Button backToFront=UiKit.secondaryButton(this,"앞면으로 돌아가기");
        emptyCaseback.addView(addCaseback);
        emptyCaseback.addView(UiKit.spacer(this,7));
        emptyCaseback.addView(backToFront);
        photoStage.addView(emptyCaseback,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        final boolean[] showingBack={false};
        image.setOnClickListener(v->{
            if(showingBack[0]) {
                animateImageFlip(image,w.representativeImageUrl,()->{
                    showingBack[0]=false;
                    photoSide.setText("FRONT");
                });
            } else if(w.casebackImageUrl!=null&&!w.casebackImageUrl.trim().isEmpty()) {
                animateImageFlip(image,w.casebackImageUrl,()->{
                    showingBack[0]=true;
                    photoSide.setText("CASEBACK");
                });
            } else {
                image.animate().rotationY(90f).setDuration(150).withEndAction(()->{
                    image.setVisibility(View.GONE);
                    photoSide.setText("CASEBACK");
                    emptyCaseback.setRotationY(-90f);
                    emptyCaseback.setVisibility(View.VISIBLE);
                    emptyCaseback.animate().rotationY(0f).setDuration(150).start();
                    showingBack[0]=true;
                }).start();
            }
        });
        backToFront.setOnClickListener(v->{
            emptyCaseback.animate().rotationY(90f).setDuration(150).withEndAction(()->{
                emptyCaseback.setVisibility(View.GONE);
                image.setRotationY(-90f);
                image.setVisibility(View.VISIBLE);
                ImageLoader.loadFit(this,image,w.representativeImageUrl);
                image.animate().rotationY(0f).setDuration(150).start();
                photoSide.setText("FRONT");
                showingBack[0]=false;
            }).start();
        });
        addCaseback.setOnClickListener(v->{
            if(detailDialog[0]!=null) detailDialog[0].dismiss();
            openCasebackImagePicker(true,w.id);
        });

        hero.addView(UiKit.spacer(this,10));
        hero.addView(UiKit.eyebrow(this,w.brand.isEmpty()?"WATCH":w.brand));
        hero.addView(UiKit.text(this,w.modelName,22,true));
        if(!w.referenceNo.isEmpty()) {
            TextView ref=UiKit.muted(this,"Ref. "+w.referenceNo,11);
            ref.setPadding(0,UiKit.dp(this,3),0,0);
            hero.addView(ref);
        }
        body.addView(hero);

        String power=normalizePower(w.movement);
        if(!power.isEmpty()||!w.caliber.isEmpty()||!w.powerReserveHours.isEmpty()) {
            LinearLayout essential=detailSection("ESSENTIAL");
            if(!power.isEmpty()) essential.addView(detailSpecRow("Power",power));
            if(!w.caliber.isEmpty()) essential.addView(detailSpecRow("Movement",w.caliber));
            if(!w.powerReserveHours.isEmpty()) essential.addView(detailSpecRow("Power Reserve",w.powerReserveHours+" h"));
            body.addView(essential);
        }

        if(!w.diameterMm.isEmpty()||!w.lugToLugMm.isEmpty()||!w.thicknessMm.isEmpty()||!w.lugWidthMm.isEmpty()) {
            LinearLayout dimensions=detailSection("DIMENSIONS");
            if(!w.diameterMm.isEmpty()) dimensions.addView(detailSpecRow("Diameter",w.diameterMm+" mm"));
            if(!w.lugToLugMm.isEmpty()) dimensions.addView(detailSpecRow("Lug-to-Lug",w.lugToLugMm+" mm"));
            if(!w.thicknessMm.isEmpty()) dimensions.addView(detailSpecRow("Thickness",w.thicknessMm+" mm"));
            if(!w.lugWidthMm.isEmpty()) dimensions.addView(detailSpecRow("Lug Width",w.lugWidthMm+" mm"));
            body.addView(dimensions);
        }

        if(!w.waterResistance.isEmpty()||!w.crystal.isEmpty()||!w.caseMaterial.isEmpty()) {
            LinearLayout build=detailSection("BUILD");
            if(!w.waterResistance.isEmpty()) build.addView(detailSpecRow("Water Resistance",formatWr(w.waterResistance)));
            if(!w.crystal.isEmpty()) build.addView(detailSpecRow("Crystal",w.crystal));
            if(!w.caseMaterial.isEmpty()) build.addView(detailSpecRow("Case Material",w.caseMaterial));
            body.addView(build);
        }

        if(!w.purchaseDate.isEmpty()||!w.warrantyEndDate.isEmpty()||!w.purchasePrice.isEmpty()) {
            LinearLayout ownership=detailSection("OWNERSHIP");
            if(!w.purchaseDate.isEmpty()) ownership.addView(detailSpecRow("Purchase Date",w.purchaseDate));
            if(!w.warrantyEndDate.isEmpty()) ownership.addView(detailSpecRow("Warranty","~"+w.warrantyEndDate));
            if(!w.purchasePrice.isEmpty()) ownership.addView(detailSpecRow("Purchase Price",formatWon(w.purchasePrice)));
            body.addView(ownership);
        }

        LinearLayout repairs=detailSection("REPAIR HISTORY");
        LinearLayout repairTitle=UiKit.row(this);
        TextView repairHint=UiKit.muted(this,"수리 이력",12);
        repairTitle.addView(repairHint,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button addRepair=UiKit.secondaryButton(this,"+ 추가");
        repairTitle.addView(addRepair,new LinearLayout.LayoutParams(UiKit.dp(this,78),ViewGroup.LayoutParams.WRAP_CONTENT));
        repairs.addView(repairTitle);
        List<DatabaseHelper.RepairRow> repairRows=db.getRepairsForWatch(w.id);
        if(repairRows.isEmpty()) {
            repairs.addView(UiKit.muted(this,"저장된 수리 이력이 없습니다.",11));
        } else {
            for(DatabaseHelper.RepairRow r:repairRows) {
                LinearLayout line=UiKit.row(this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout info=UiKit.column(this);
                String type="OVERHAUL".equals(r.repairType)?"오버홀":"그 외";
                info.addView(UiKit.text(this,r.repairDate+" · "+type,12,true));
                String details="OVERHAUL".equals(r.repairType)?"오버홀 수리 진행":r.details;
                if(!details.isEmpty()) info.addView(UiKit.muted(this,details,11));
                line.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
                Button x=UiKit.secondaryButton(this,"×");
                x.setTextSize(16);
                LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(UiKit.dp(this,42),UiKit.dp(this,40));
                xp.setMargins(UiKit.dp(this,6),0,0,0);
                line.addView(x,xp);
                x.setOnClickListener(v->new AlertDialog.Builder(this)
                        .setTitle("수리 이력 삭제")
                        .setMessage(r.repairDate+" 수리 이력을 삭제할까요?")
                        .setNegativeButton("취소",null)
                        .setPositiveButton("삭제",(d,which)->{
                            db.deleteRepair(r.id);
                            if(detailDialog[0]!=null) detailDialog[0].dismiss();
                            Watch fresh=db.getWatch(w.id);
                            if(fresh!=null) showWatchDetailPopup(fresh);
                        }).show());
                line.setPadding(0,UiKit.dp(this,5),0,UiKit.dp(this,5));
                repairs.addView(line);
            }
        }
        body.addView(repairs);

        LinearLayout heritage=detailSection("HERITAGE");
        List<DatabaseHelper.HeritageEntryRow> heritageRows=db.getHeritageEntriesForWatch(w.id);
        if(heritageRows.isEmpty()) {
            heritage.addView(UiKit.muted(this,"연결된 헤리티지가 없습니다.",11));
        } else {
            for(DatabaseHelper.HeritageEntryRow entry:heritageRows) {
                LinearLayout line=UiKit.row(this);
                line.setGravity(Gravity.CENTER_VERTICAL);

                TextView heritageDate=UiKit.text(this,entry.eventDate,11,true);
                heritageDate.setTextColor(UiKit.GOLD);
                heritageDate.setPadding(0,UiKit.dp(this,5),UiKit.dp(this,10),UiKit.dp(this,5));
                line.addView(heritageDate,new LinearLayout.LayoutParams(UiKit.dp(this,92),ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView heritageTitle=UiKit.text(this,entry.title,12,true);
                heritageTitle.setMaxLines(2);
                heritageTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
                line.addView(heritageTitle,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

                View.OnClickListener openHeritage=v->{
                    if(detailDialog[0]!=null) detailDialog[0].dismiss();
                    openHeritageEntryFromWatchDetails(entry.id);
                };
                line.setOnClickListener(openHeritage);
                heritageDate.setOnClickListener(openHeritage);
                heritageTitle.setOnClickListener(openHeritage);
                heritage.addView(line);
            }
        }
        body.addView(heritage);

        if(!w.notes.isEmpty()) {
            LinearLayout notes=detailSection("NOTES");
            TextView n=UiKit.text(this,w.notes,13,false);
            n.setTextColor(UiKit.INK);
            n.setPadding(0,UiKit.dp(this,6),0,0);
            notes.addView(n);
            body.addView(notes);
        }

        LinearLayout actions=UiKit.row(this);
        Button edit=UiKit.secondaryButton(this,"수정");
        Button wearPhotos=UiKit.secondaryButton(this,"오착");
        Button delete=UiKit.secondaryButton(this,"삭제");
        actions.addView(edit,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        actions.addView(wearPhotos,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        actions.addView(delete,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        body.addView(actions);

        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        detailDialog[0]=new AlertDialog.Builder(this)
                .setTitle("Watch Details")
                .setView(scroll)
                .setPositiveButton("닫기",null)
                .create();
        addRepair.setOnClickListener(v->showRepairDialog(w,()->{
            if(detailDialog[0]!=null) detailDialog[0].dismiss();
            Watch fresh=db.getWatch(w.id);
            if(fresh!=null) showWatchDetailPopup(fresh);
        }));
        edit.setOnClickListener(v->{
            if(detailDialog[0]!=null) detailDialog[0].dismiss();
            showWatchDialog(w);
        });
        wearPhotos.setOnClickListener(v->{
            if(detailDialog[0]!=null) detailDialog[0].dismiss();
            showWatchWearPhotoTimeline(w);
        });
        delete.setOnClickListener(v->{
            if(detailDialog[0]!=null) detailDialog[0].dismiss();
            confirmDeleteWatch(w);
        });
        detailDialog[0].show();
    }

    private void openHeritageEntryFromWatchDetails(long entryId) {
        DatabaseHelper.HeritageEntryRow entry=db.getHeritageEntry(entryId);
        if(entry==null) return;
        if(entry.eventDate!=null&&entry.eventDate.length()>=7) {
            heritageMonthFilter=entry.eventDate.substring(0,7);
        }
        showHeritage();
        if(content!=null) {
            content.postDelayed(()->showHeritageDetailPopup(entryId),160);
        }
    }

    private void showWatchWearPhotoTimeline(Watch w) {
        showWatchWearPhotoTimeline(w,"");
    }

    private void showWatchWearPhotoTimeline(Watch w,String initialDate) {
        if(w==null) return;
        final AlertDialog[] dialog=new AlertDialog[1];
        final List<String> dates=db.getWearPhotoDatesForWatch(w.id);
        if(dates.isEmpty()) {
            LinearLayout body=UiKit.column(this);
            body.setPadding(UiKit.dp(this,16),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,14));
            body.addView(emptyCard("등록된 오착 사진이 없습니다","착용 캘린더의 날짜를 눌러 오착 사진을 추가할 수 있습니다."));
            dialog[0]=new AlertDialog.Builder(this)
                    .setTitle("오착")
                    .setView(body)
                    .setPositiveButton("닫기",null)
                    .create();
            dialog[0].show();
            return;
        }

        final int[] dateIndex={0};
        if(initialDate!=null&&!initialDate.isEmpty()) {
            int found=dates.indexOf(initialDate);
            if(found>=0) dateIndex[0]=found;
        }
        final int[] photoIndex={0};

        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,16),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,14));
        body.addView(UiKit.eyebrow(this,"WRIST SHOTS"));
        body.addView(UiKit.text(this,(w.brand.isEmpty()?"":w.brand+" · ")+w.modelName,19,true));
        body.addView(UiKit.spacer(this,10));

        LinearLayout dateNav=UiKit.row(this);
        Button newer=UiKit.secondaryButton(this,"‹");
        Button dateButton=UiKit.secondaryButton(this,"달력 · "+dates.get(dateIndex[0]));
        Button older=UiKit.secondaryButton(this,"›");
        dateButton.setTextSize(14);
        dateNav.addView(newer,new LinearLayout.LayoutParams(UiKit.dp(this,54),ViewGroup.LayoutParams.WRAP_CONTENT));
        dateNav.addView(dateButton,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        dateNav.addView(older,new LinearLayout.LayoutParams(UiKit.dp(this,54),ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(dateNav);
        body.addView(UiKit.spacer(this,9));

        FrameLayout stage=new FrameLayout(this);
        stage.setBackground(UiKit.rounded(UiKit.PHOTO_BG,UiKit.LINE,16,this));
        stage.setClipToOutline(true);
        stage.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        body.addView(stage,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,390)));

        ImageView image=new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(UiKit.PHOTO_BG);
        stage.addView(image,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        Button prevPhoto=overlayArrowButton("‹");
        Button nextPhoto=overlayArrowButton("›");
        FrameLayout.LayoutParams prevLp=new FrameLayout.LayoutParams(UiKit.dp(this,46),UiKit.dp(this,46));
        prevLp.gravity=Gravity.CENTER_VERTICAL|Gravity.START;
        prevLp.setMargins(UiKit.dp(this,8),0,0,0);
        FrameLayout.LayoutParams nextLp=new FrameLayout.LayoutParams(UiKit.dp(this,46),UiKit.dp(this,46));
        nextLp.gravity=Gravity.CENTER_VERTICAL|Gravity.END;
        nextLp.setMargins(0,0,UiKit.dp(this,8),0);
        stage.addView(prevPhoto,prevLp);
        stage.addView(nextPhoto,nextLp);

        TextView photoCount=UiKit.text(this,"",11,true);
        photoCount.setTextColor(UiKit.GOLD);
        photoCount.setBackground(UiKit.rounded(UiKit.PAPER,UiKit.LINE,12,this));
        photoCount.setPadding(UiKit.dp(this,8),UiKit.dp(this,4),UiKit.dp(this,8),UiKit.dp(this,4));
        FrameLayout.LayoutParams countLp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        countLp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
        countLp.setMargins(0,UiKit.dp(this,8),0,0);
        stage.addView(photoCount,countLp);

        Button delete=UiKit.secondaryButton(this,"현재 사진 삭제");
        body.addView(UiKit.spacer(this,10));
        body.addView(delete);

        final java.util.concurrent.atomic.AtomicReference<List<DatabaseHelper.WearPhotoRow>> currentPhotos=
                new java.util.concurrent.atomic.AtomicReference<>(new ArrayList<>());
        final Runnable[] render=new Runnable[1];
        render[0]=()->{
            if(dateIndex[0]<0) dateIndex[0]=0;
            if(dateIndex[0]>=dates.size()) dateIndex[0]=dates.size()-1;
            String date=dates.get(dateIndex[0]);
            dateButton.setText("달력 · "+date);
            newer.setEnabled(dateIndex[0]>0);
            older.setEnabled(dateIndex[0]<dates.size()-1);
            List<DatabaseHelper.WearPhotoRow> photos=db.getWearPhotosForDateWatch(date,w.id);
            currentPhotos.set(photos);
            if(photoIndex[0]<0) photoIndex[0]=0;
            if(photoIndex[0]>=photos.size()) photoIndex[0]=Math.max(0,photos.size()-1);
            if(photos.isEmpty()) {
                image.setImageDrawable(null);
                photoCount.setText("0 / 0");
                prevPhoto.setVisibility(View.GONE);
                nextPhoto.setVisibility(View.GONE);
                delete.setEnabled(false);
                return;
            }
            DatabaseHelper.WearPhotoRow current=photos.get(photoIndex[0]);
            photoCount.setText((photoIndex[0]+1)+" / "+photos.size());
            prevPhoto.setVisibility(photos.size()>1?View.VISIBLE:View.GONE);
            nextPhoto.setVisibility(photos.size()>1?View.VISIBLE:View.GONE);
            prevPhoto.setEnabled(photoIndex[0]>0);
            nextPhoto.setEnabled(photoIndex[0]<photos.size()-1);
            delete.setEnabled(true);
            ImageLoader.loadFit(this,image,current.imagePath);
            image.setOnClickListener(v->showWearPhotoFullScreen(current.imagePath,date));
        };

        newer.setOnClickListener(v->{ if(dateIndex[0]>0){ dateIndex[0]--; photoIndex[0]=0; render[0].run(); } });
        older.setOnClickListener(v->{ if(dateIndex[0]<dates.size()-1){ dateIndex[0]++; photoIndex[0]=0; render[0].run(); } });
        prevPhoto.setOnClickListener(v->{ if(photoIndex[0]>0){ photoIndex[0]--; render[0].run(); } });
        nextPhoto.setOnClickListener(v->{ if(photoIndex[0]<currentPhotos.get().size()-1){ photoIndex[0]++; render[0].run(); } });
        dateButton.setOnClickListener(v->showWearPhotoDateCalendar(w,dates,dates.get(dateIndex[0]),selected->{
            if(dialog[0]!=null) dialog[0].dismiss();
            showWatchWearPhotoTimeline(w,selected);
        }));
        delete.setOnClickListener(v->{
            if(currentPhotos.get().isEmpty()) return;
            DatabaseHelper.WearPhotoRow current=currentPhotos.get().get(photoIndex[0]);
            new AlertDialog.Builder(this)
                    .setTitle("오착 사진 삭제")
                    .setMessage("현재 사진을 삭제할까요?")
                    .setNegativeButton("취소",null)
                    .setPositiveButton("삭제",(d,which)->{
                        String fallback="";
                        if(currentPhotos.get().size()==1 && dates.size()>1) {
                            fallback=dateIndex[0]<dates.size()-1?dates.get(dateIndex[0]+1):dates.get(dateIndex[0]-1);
                        } else {
                            fallback=dates.get(dateIndex[0]);
                        }
                        db.deleteWearPhoto(current.id);
                        if(dialog[0]!=null) dialog[0].dismiss();
                        showWatchWearPhotoTimeline(w,fallback);
                    }).show();
        });
        render[0].run();

        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        dialog[0]=new AlertDialog.Builder(this)
                .setTitle("오착")
                .setView(scroll)
                .setPositiveButton("닫기",null)
                .create();
        dialog[0].show();
    }

    private void showWearPhotoDateCalendar(Watch w,List<String> dates,String selectedDate,DateConsumer consumer) {
        if(dates==null||dates.isEmpty()) {
            Toast.makeText(this,"오착 사진이 있는 날짜가 없습니다.",Toast.LENGTH_SHORT).show();
            return;
        }

        final AlertDialog[] dialog=new AlertDialog[1];
        final Set<String> activeDates=new HashSet<>(dates);
        final Calendar[] month={calendarFromDate(selectedDate)};
        month[0].set(Calendar.DAY_OF_MONTH,1);

        LinearLayout host=UiKit.column(this);
        host.setPadding(UiKit.dp(this,12),UiKit.dp(this,8),UiKit.dp(this,12),UiKit.dp(this,14));
        final Runnable[] render=new Runnable[1];
        render[0]=()->{
            host.removeAllViews();

            LinearLayout monthHead=UiKit.row(this);
            Button prev=UiKit.secondaryButton(this,"‹");
            Button next=UiKit.secondaryButton(this,"›");
            TextView title=UiKit.text(this,String.format(Locale.KOREA,"%d년 %d월",
                    month[0].get(Calendar.YEAR),month[0].get(Calendar.MONTH)+1),18,true);
            title.setGravity(Gravity.CENTER);
            monthHead.addView(prev,new LinearLayout.LayoutParams(UiKit.dp(this,52),ViewGroup.LayoutParams.WRAP_CONTENT));
            monthHead.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            monthHead.addView(next,new LinearLayout.LayoutParams(UiKit.dp(this,52),ViewGroup.LayoutParams.WRAP_CONTENT));
            host.addView(monthHead);
            host.addView(UiKit.spacer(this,10));

            LinearLayout weekdayRow=UiKit.row(this);
            String[] weekdays={"일","월","화","수","목","금","토"};
            for(int i=0;i<7;i++) {
                TextView day=UiKit.muted(this,weekdays[i],11);
                day.setGravity(Gravity.CENTER);
                if(i==0) day.setTextColor(UiKit.SUNDAY_RED);
                else if(i==6) day.setTextColor(UiKit.SATURDAY_BLUE);
                LinearLayout.LayoutParams dayLp=new LinearLayout.LayoutParams(0,UiKit.dp(this,28),1);
                weekdayRow.addView(day,dayLp);
            }
            host.addView(weekdayRow);

            Calendar first=(Calendar)month[0].clone();
            first.set(Calendar.DAY_OF_MONTH,1);
            int offset=first.get(Calendar.DAY_OF_WEEK)-1;
            int total=first.getActualMaximum(Calendar.DAY_OF_MONTH);
            int weeks=Math.max(4,(offset+total+6)/7);

            LinearLayout calendarRows=UiKit.column(this);
            for(int week=0;week<weeks;week++) {
                LinearLayout weekRow=UiKit.row(this);
                for(int col=0;col<7;col++) {
                    int cell=week*7+col;
                    int dayNumber=cell-offset+1;
                    TextView cellView=UiKit.text(this,"",13,false);
                    cellView.setGravity(Gravity.CENTER);
                    cellView.setSingleLine(true);
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,UiKit.dp(this,48),1);
                    lp.setMargins(UiKit.dp(this,2),UiKit.dp(this,2),UiKit.dp(this,2),UiKit.dp(this,2));
                    weekRow.addView(cellView,lp);

                    if(dayNumber<1||dayNumber>total) continue;

                    Calendar c=(Calendar)first.clone();
                    c.set(Calendar.DAY_OF_MONTH,dayNumber);
                    String key=dateFormat.format(c.getTime());
                    cellView.setText(String.valueOf(dayNumber));
                    boolean active=activeDates.contains(key);
                    if(!active) {
                        cellView.setTextColor(Color.rgb(184,184,184));
                        cellView.setEnabled(false);
                        cellView.setBackground(UiKit.rounded(Color.TRANSPARENT,UiKit.LINE,12,this));
                    } else {
                        cellView.setTypeface(null,android.graphics.Typeface.BOLD);
                        if(key.equals(selectedDate)) {
                            cellView.setTextColor(Color.WHITE);
                            cellView.setBackground(UiKit.rounded(UiKit.NAVY,UiKit.NAVY,12,this));
                        } else {
                            cellView.setTextColor(col==0?UiKit.SUNDAY_RED:(col==6?UiKit.SATURDAY_BLUE:UiKit.NAVY));
                            cellView.setBackground(UiKit.rounded(UiKit.GOLD_SOFT,UiKit.GOLD,12,this));
                        }
                        cellView.setOnClickListener(v->{
                            if(dialog[0]!=null) dialog[0].dismiss();
                            consumer.accept(key);
                        });
                    }
                }
                calendarRows.addView(weekRow);
            }
            host.addView(calendarRows);

            TextView hint=UiKit.muted(this,"오착 사진이 있는 날짜만 선택할 수 있습니다.",11);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0,UiKit.dp(this,9),0,0);
            host.addView(hint);

            prev.setOnClickListener(v->{ month[0].add(Calendar.MONTH,-1); render[0].run(); });
            next.setOnClickListener(v->{ month[0].add(Calendar.MONTH,1); render[0].run(); });
        };
        render[0].run();

        dialog[0]=new AlertDialog.Builder(this)
                .setTitle((w==null?"오착":w.modelName)+" · 오착 날짜")
                .setView(host)
                .setNegativeButton("취소",null)
                .create();
        dialog[0].setOnShowListener(x->{
            if(dialog[0].getWindow()!=null) {
                int width=(int)(getResources().getDisplayMetrics().widthPixels*0.94f);
                dialog[0].getWindow().setLayout(width,ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog[0].show();
    }

    private Calendar calendarFromDate(String date) {
        Calendar c=Calendar.getInstance();
        try { c.setTime(dateFormat.parse(date)); } catch(Exception ignored) {}
        return c;
    }

    private void showRepairDialog(Watch w,Runnable afterSave) {
        LinearLayout f=UiKit.column(this);
        f.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,8));

        f.addView(UiKit.label(this,"수리 날짜"));
        EditText date=UiKit.field(this,"0000-00-00");
        date.setText(dateFormat.format(new Date()));
        date.setFocusable(false);
        date.setClickable(true);
        date.setOnClickListener(v->pickDate(val(date),date::setText));
        f.addView(date);

        f.addView(UiKit.label(this,"수리 유형"));
        Spinner type=new Spinner(this);
        String[] types={"오버홀","그 외"};
        type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types));
        decorateSpinner(type);
        f.addView(type);
        f.addView(UiKit.spacer(this,8));

        f.addView(UiKit.label(this,"수리 내역"));
        EditText details=UiKit.field(this,"구체적인 수리 내역");
        details.setEnabled(false);
        details.setHint("오버홀 수리 진행");
        f.addView(details);
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                boolean other=position==1;
                details.setEnabled(other);
                details.setHint(other?"구체적인 수리 내역":"오버홀 수리 진행");
                if(!other) details.setText("");
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle("수리 이력 추가")
                .setView(f)
                .setNegativeButton("취소",null)
                .setPositiveButton("저장",null)
                .create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            boolean overhaul=type.getSelectedItemPosition()==0;
            String repairDetails=overhaul?"오버홀 수리 진행":val(details);
            if(!overhaul && repairDetails.isEmpty()) {
                details.setError("수리 내역을 입력하세요.");
                return;
            }
            db.addRepair(w.id,val(date),overhaul?"OVERHAUL":"OTHER",repairDetails);
            dlg.dismiss();
            if(afterSave!=null) afterSave.run();
        }));
        dlg.show();
    }

    private boolean isOverhaulRecommended(Watch w) {
        if(w==null) return false;
        String baseline=db.getLatestOverhaulDate(w.id);
        if(baseline.isEmpty()) baseline=safeText(w.purchaseDate).trim();
        if(baseline.isEmpty()) return false;
        try {
            Calendar base=Calendar.getInstance();
            base.setTime(dateFormat.parse(baseline));
            base.add(Calendar.YEAR,5);
            Calendar today=Calendar.getInstance();
            return !today.before(base);
        } catch(Exception ignored) { return false; }
    }

    private void showWatchManagementPopup() {
        List<Watch> watches=getWatchesOrderedByWear();
        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,14),UiKit.dp(this,8),UiKit.dp(this,14),UiKit.dp(this,12));
        for(Watch w:watches) {
            LinearLayout card=UiKit.card(this);
            LinearLayout row=UiKit.row(this);
            row.setGravity(Gravity.TOP);

            LinearLayout left=UiKit.column(this);
            ImageView image=new ImageView(this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,14,this));
            image.setClipToOutline(true);
            image.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            ImageLoader.loadFit(this,image,w.representativeImageUrl);
            left.addView(image,new LinearLayout.LayoutParams(UiKit.dp(this,116),UiKit.dp(this,108)));
            TextView purchase=UiKit.muted(this,"구매일자  "+(w.purchaseDate.isEmpty()?"—":w.purchaseDate),10);
            TextView warranty=UiKit.muted(this,"Warranty  "+(w.warrantyEndDate.isEmpty()?"—":"~"+w.warrantyEndDate),10);
            left.addView(purchase);
            left.addView(warranty);
            row.addView(left,new LinearLayout.LayoutParams(UiKit.dp(this,124),ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout right=UiKit.column(this);
            right.setPadding(UiKit.dp(this,10),0,0,0);
            LinearLayout top=UiKit.row(this);
            TextView name=UiKit.text(this,(w.brand.isEmpty()?"":w.brand+" · ")+w.modelName,15,true);
            top.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            if(isOverhaulRecommended(w)) {
                TextView badge=UiKit.text(this,"오버홀 권장",10,true);
                badge.setTextColor(UiKit.GOLD);
                badge.setPadding(UiKit.dp(this,7),UiKit.dp(this,4),UiKit.dp(this,7),UiKit.dp(this,4));
                badge.setBackground(UiKit.rounded(UiKit.GOLD_SOFT,UiKit.GOLD,10,this));
                top.addView(badge);
            }
            right.addView(top);
            right.addView(UiKit.spacer(this,6));
            List<DatabaseHelper.RepairRow> repairs=db.getRepairsForWatch(w.id);
            if(repairs.isEmpty()) {
                right.addView(UiKit.muted(this,"수리 이력 없음",11));
            } else {
                for(DatabaseHelper.RepairRow r:repairs) {
                    String type="OVERHAUL".equals(r.repairType)?"오버홀":"그 외";
                    String detail="OVERHAUL".equals(r.repairType)?"오버홀 수리 진행":r.details;
                    TextView rr=UiKit.muted(this,r.repairDate+" · "+type+" · "+detail,10);
                    rr.setMaxLines(2);
                    right.addView(rr);
                }
            }
            row.addView(right,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            card.addView(row);
            image.setOnClickListener(v->showWatchDetailPopup(w));
            name.setOnClickListener(v->showWatchDetailPopup(w));
            body.addView(card);
        }
        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        new AlertDialog.Builder(this)
                .setTitle("시계 관리")
                .setView(scroll)
                .setPositiveButton("닫기",null)
                .show();
    }

    private void showPurchasePricePopup() {
        List<Watch> watches=getWatchesOrderedByWear();
        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,14),UiKit.dp(this,8),UiKit.dp(this,14),UiKit.dp(this,12));
        long total=0L;
        for(Watch w:watches) {
            long price=parsePrice(w.purchasePrice);
            total+=price;
            LinearLayout row=UiKit.card(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView image=watchThumb(w.representativeImageUrl,58);
            row.addView(image,new LinearLayout.LayoutParams(UiKit.dp(this,58),UiKit.dp(this,58)));
            LinearLayout info=UiKit.column(this);
            info.setPadding(UiKit.dp(this,10),0,0,0);
            TextView name=UiKit.text(this,(w.brand.isEmpty()?"":w.brand+" · ")+w.modelName,14,true);
            info.addView(name);
            info.addView(UiKit.muted(this,price>0?formatWon(price):"—",12));
            row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            image.setOnClickListener(v->showWatchDetailPopup(w));
            name.setOnClickListener(v->showWatchDetailPopup(w));
            body.addView(row);
        }
        LinearLayout totalCard=UiKit.card(this);
        totalCard.addView(UiKit.eyebrow(this,"TOTAL PURCHASE PRICE"));
        TextView sum=UiKit.text(this,formatWon(total),24,true);
        sum.setTextColor(UiKit.GOLD);
        totalCard.addView(sum);
        body.addView(totalCard);
        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        new AlertDialog.Builder(this)
                .setTitle("구매가격 총합")
                .setView(scroll)
                .setPositiveButton("닫기",null)
                .show();
    }

    private long parsePrice(String raw) {
        String digits=safeText(raw).replaceAll("[^0-9]","");
        if(digits.isEmpty()) return 0L;
        try { return Long.parseLong(digits); } catch(Exception ignored) { return 0L; }
    }

    private String formatWon(String raw) { return formatWon(parsePrice(raw)); }

    private String formatWon(long value) {
        return "₩ "+NumberFormat.getIntegerInstance(Locale.KOREA).format(Math.max(0L,value));
    }

    private LinearLayout detailSection(String title) {
        LinearLayout section=UiKit.card(this);
        section.addView(UiKit.eyebrow(this,title));
        section.addView(UiKit.spacer(this,6));
        return section;
    }

    private View detailSpecRow(String label,String value) {
        LinearLayout row=UiKit.row(this);
        row.setGravity(Gravity.TOP);
        TextView l=UiKit.muted(this,label,12);
        l.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        row.addView(l,new LinearLayout.LayoutParams(UiKit.dp(this,118),ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView v=UiKit.text(this,value,13,true);
        v.setGravity(Gravity.END);
        v.setTextColor(UiKit.NAVY);
        row.addView(v,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        row.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,4));
        return row;
    }

    private String firstLine(String text) {
        String[] lines=safeText(text).split("\n");
        return lines.length==0?"":lines[0].trim();
    }

    private String formatWr(String raw) {
        String wr=safeText(raw).trim();
        if(wr.isEmpty()) return "";
        return wr.toUpperCase(Locale.ROOT).startsWith("WR")?wr:"WR "+wr;
    }

    private TextView boldValueLine(String label,String value,int sp) {
        String text=label+"  "+value;
        SpannableString ss=new SpannableString(text);
        ss.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),0,label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView tv=UiKit.text(this,text,sp,false);
        tv.setText(ss);
        tv.setPadding(0,UiKit.dp(this,2),0,UiKit.dp(this,2));
        return tv;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9001);
        }
    }

    private int positionIconRes(String position) {
        if("Dial up".equals(position)) return R.drawable.ic_pos_dial_up;
        if("Dial down".equals(position)) return R.drawable.ic_pos_dial_down;
        if("12 Up".equals(position)) return R.drawable.ic_pos_12_up;
        if("9 Up".equals(position)) return R.drawable.ic_pos_9_up;
        if("6 Up".equals(position)) return R.drawable.ic_pos_6_up;
        if("3 Up".equals(position)) return R.drawable.ic_pos_3_up;
        return R.drawable.ic_watch;
    }

    private TextView positionLabelView(String position,int sp,boolean bold) {
        TextView tv=UiKit.text(this,positionLabel(position),sp,bold);
        tv.setCompoundDrawablesWithIntrinsicBounds(positionIconRes(position),0,0,0);
        tv.setCompoundDrawablePadding(UiKit.dp(this,8));
        return tv;
    }

    private void appendSpecProgress(TextView status,List<String> lines,String message) {
        if(message==null||message.trim().isEmpty()) return;
        runOnUiThread(()->{
            lines.add("• "+message.trim());
            while(lines.size()>14) lines.remove(0);
            status.setVisibility(View.VISIBLE);
            status.setText(String.join("\n",lines));
        });
    }

    private void bindCollectionImageFlip(ImageView image,Watch w) {
        final boolean[] showingBack={false};
        image.setClickable(true);
        image.setFocusable(true);
        image.setCameraDistance(UiKit.dp(this,12000));
        image.setOnClickListener(v->{
            if(showingBack[0]) {
                animateImageFlip(image,w.representativeImageUrl,()->showingBack[0]=false);
            } else if(w.casebackImageUrl!=null&&!w.casebackImageUrl.trim().isEmpty()) {
                animateImageFlip(image,w.casebackImageUrl,()->showingBack[0]=true);
            } else {
                showWatchDetailPopup(w);
            }
        });
    }

    private void animateImageFlip(ImageView image,String nextPath,Runnable afterSwap) {
        image.animate().rotationY(90f).setDuration(150).withEndAction(()->{
            ImageLoader.loadFit(this,image,nextPath);
            image.setRotationY(-90f);
            image.animate().rotationY(0f).setDuration(150).withEndAction(()->{
                if(afterSwap!=null) afterSwap.run();
            }).start();
        }).start();
    }

    // HELPERS ----------------------------------------------------------------

    private EditText addField(LinearLayout parent,String label,String hint,String value) {
        parent.addView(UiKit.label(this,label));
        EditText e=UiKit.field(this,hint);
        e.setText(value==null?"":value);
        parent.addView(e);
        return e;
    }

    private Spinner powerSpinner(String current) {
        Spinner sp=new Spinner(this);
        String[] items={"Automatic","Mechanical","Quartz","Solar-Quartz"};
        sp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,items));
        decorateSpinner(sp);
        setPowerSelection(sp,current);
        return sp;
    }

    private void setPowerSelection(Spinner spinner,String raw) {
        if(spinner==null) return;
        String normalized=normalizePower(raw);
        String[] items={"Automatic","Mechanical","Quartz","Solar-Quartz"};
        for(int i=0;i<items.length;i++) {
            if(items[i].equals(normalized)) { spinner.setSelection(i); return; }
        }
        spinner.setSelection(0);
    }

    private String normalizePower(String raw) {
        String x=safeText(raw).trim().toLowerCase(Locale.ROOT);
        if(x.isEmpty()) return "";
        if(x.contains("solar")||x.contains("eco-drive")||x.contains("eco drive")||x.contains("light-powered")||x.contains("light powered")) return "Solar-Quartz";
        if(x.contains("quartz")) return "Quartz";
        if(x.contains("automatic")||x.contains("self-winding")||x.contains("self winding")||x.contains("auto")) return "Automatic";
        if(x.contains("manual")||x.contains("hand-wound")||x.contains("hand wound")||x.contains("mechanical")||x.contains("handwind")) return "Mechanical";
        if("solar-quartz".equalsIgnoreCase(raw)||"solar quartz".equalsIgnoreCase(raw)) return "Solar-Quartz";
        return raw;
    }

    private void setIf(EditText e,String value) { if(value!=null&&!value.isEmpty()) e.setText(value); }
    private String val(EditText e) { return e.getText().toString().trim(); }
    private String safeText(String value) { return value==null?"":value; }

    private String firstNonEmpty(String... values) {
        for(String value:values) if(value!=null&&!value.trim().isEmpty()) return value.trim();
        return "";
    }

    private String joinNonEmpty(String delimiter,String... values) {
        List<String> items=new ArrayList<>();
        for(String value:values) if(value!=null&&!value.trim().isEmpty()) items.add(value.trim());
        return String.join(delimiter,items);
    }

    private void openFirstSource(String sources) {
        String[] lines=sources.split("\\n");
        for(String x:lines) {
            String s=x.trim();
            if(s.startsWith("http://")||s.startsWith("https://")) {
                startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(s)));
                return;
            }
        }
    }

    private Spinner watchSpinner(List<Watch>watches) {
        Spinner sp=new Spinner(this);
        List<String>names=new ArrayList<>();
        for(Watch w:watches) names.add((w.brand.isEmpty()?"":w.brand+" · ")+w.modelName);
        sp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
        decorateSpinner(sp);
        return sp;
    }

    private Spinner watchSpinnerWithNone(List<Watch>watches,String noneLabel) {
        Spinner sp=new Spinner(this);
        List<String>names=new ArrayList<>();
        names.add(noneLabel);
        for(Watch w:watches) names.add((w.brand.isEmpty()?"":w.brand+" · ")+w.modelName);
        sp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
        decorateSpinner(sp);
        return sp;
    }

    private void decorateSpinner(Spinner sp) {
        sp.setBackground(UiKit.rounded(Color.WHITE,UiKit.LINE,11,this));
        sp.setPadding(UiKit.dp(this,8),UiKit.dp(this,7),UiKit.dp(this,8),UiKit.dp(this,7));
    }

    private void setWatchSpinnerSelection(Spinner sp,List<Watch>watches,long id) {
        int i=indexOfWatch(watches,id);
        if(i>=0) sp.setSelection(i+1);
    }

    private Watch selectedWatch(Spinner sp,List<Watch>watches) {
        int p=sp.getSelectedItemPosition();
        if(p<=0) return null;
        int index=p-1;
        return index>=0&&index<watches.size()?watches.get(index):null;
    }

    private int indexOfWatch(List<Watch>watches,long id) {
        for(int i=0;i<watches.size();i++) if(watches.get(i).id==id) return i;
        return -1;
    }

    private String[] nonEmpty(String... values) {
        List<String>x=new ArrayList<>();
        for(String v:values) if(v!=null&&!v.trim().isEmpty()) x.add(v.trim());
        return x.toArray(new String[0]);
    }

    private String formatWearScore(double score) {
        if (Math.abs(score-Math.rint(score)) < 0.001) return String.format(Locale.KOREA,"%.0f회",score);
        String v=String.format(Locale.KOREA,"%.2f",score).replaceAll("0+$","").replaceAll("\\.$","");
        return v+"회";
    }

    private String currentYearStart() {
        Calendar c=Calendar.getInstance();
        return String.format(Locale.KOREA,"%04d-01-01",c.get(Calendar.YEAR));
    }

    private String currentYearEnd() {
        Calendar c=Calendar.getInstance();
        return String.format(Locale.KOREA,"%04d-12-31",c.get(Calendar.YEAR));
    }

    private String currentMonthStart() {
        Calendar c=Calendar.getInstance();
        return String.format(Locale.KOREA,"%04d-%02d-01",c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1);
    }

    private String currentMonthEnd() {
        Calendar c=Calendar.getInstance();
        return String.format(Locale.KOREA,"%04d-%02d-31",c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1);
    }

    private interface DateConsumer { void accept(String date); }

    private void pickDate(String current,DateConsumer consumer) {
        Calendar c=Calendar.getInstance();
        try{c.setTime(dateFormat.parse(current));}catch(Exception ignored){}
        new DatePickerDialog(this,(v,y,m,d)->consumer.accept(String.format(Locale.KOREA,"%04d-%02d-%02d",y,m+1,d)),
                c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }
}
