package com.watchcollection.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private int wearRankingPeriod = 0; // 0 올해, 1 이번 달
    private int strapWidthFilter = 0; // 0 All, 18~22 mm, -1 Extra
    private String strapMaterialFilter = "All";
    private long focusedWatchId = -1;
    private Uri pendingStrapImageUri;
    private ImageView pendingStrapPreview;
    private Uri pendingWatchImageUri;
    private ImageView pendingWatchPreview;
    private TextView pendingWatchPhotoStatus;
    private boolean pendingWatchImageClear = false;
    private boolean pendingWatchImageProcessing = false;
    private boolean pendingWatchRestoreOriginal = false;
    private String pendingWatchProcessedPath = "";
    private String pendingWatchOriginalPath = "";
    private String pendingWatchCalendarPath = "";

    private Spinner accuracyWatchSpinner;
    private long accuracySelectedWatchId = -1;
    private boolean pendingWatchAccuracyMeterImport = false;
    private long pendingWatchAccuracyMeterWatchId = -1;
    private static final String WATCH_ACCURACY_METER_PACKAGE = "com.watchaccuracymeter.app";
    public static final String EXTRA_OPEN_TAB = "open_tab";

    private int collectionPhotoIndex = 0;
    private long pendingCollectionPhotoReplaceId = -1;

    private static final int REQ_STRAP_IMAGE = 2201;
    private static final int REQ_WATCH_IMAGE = 2202;
    private static final int REQ_COLLECTION_IMAGE_ADD = 2203;
    private static final int REQ_COLLECTION_IMAGE_REPLACE = 2204;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DatabaseHelper(this);
        visibleMonth.set(Calendar.DAY_OF_MONTH, 1);
        buildShell();
        requestNotificationPermissionIfNeeded();
        WarrantyNotifier.scheduleNextCheck(this);
        ensureCalendarImagesAsync();
        openRequestedTab(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openRequestedTab(intent);
    }

    private void openRequestedTab(Intent intent) {
        String tab=intent==null?"":safeText(intent.getStringExtra(EXTRA_OPEN_TAB));
        if("calendar".equalsIgnoreCase(tab)) showCalendar();
        else if("accuracy".equalsIgnoreCase(tab)) showAccuracy();
        else if("straps".equalsIgnoreCase(tab)) showStraps(0);
        else showCollection();
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
        TextView mark = UiKit.text(this,"◌",30,false);
        mark.setTextColor(UiKit.GOLD);
        top.addView(mark);
        root.addView(top);

        content = new FrameLayout(this);
        root.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        LinearLayout nav = UiKit.row(this);
        nav.setPadding(UiKit.dp(this,4),UiKit.dp(this,5),UiKit.dp(this,4),UiKit.dp(this,4));
        nav.setBackgroundColor(UiKit.PAPER);
        navCollection = navButton("컬렉션", R.drawable.ic_nav_collection);
        navAccuracy = navButton("오차", R.drawable.ic_nav_accuracy);
        navCalendar = navButton("캘린더", R.drawable.ic_nav_calendar);
        navStraps = navButton("스트랩", R.drawable.ic_nav_strap);
        nav.addView(navCollection,new LinearLayout.LayoutParams(0,UiKit.dp(this,66),1));
        nav.addView(navAccuracy,new LinearLayout.LayoutParams(0,UiKit.dp(this,66),1));
        nav.addView(navCalendar,new LinearLayout.LayoutParams(0,UiKit.dp(this,66),1));
        nav.addView(navStraps,new LinearLayout.LayoutParams(0,UiKit.dp(this,66),1));
        root.addView(nav);

        navCollection.setOnClickListener(v -> { focusedWatchId=-1; showCollection(); });
        navAccuracy.setOnClickListener(v -> showAccuracy());
        navCalendar.setOnClickListener(v -> showCalendar());
        navStraps.setOnClickListener(v -> { strapWidthFilter=0; strapMaterialFilter="All"; showStraps(0); });
        setContentView(root);
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
        Button add=UiKit.primaryButton(this,"+ 추가");
        add.setOnClickListener(v->showWatchDialog(null));
        head.addView(add);
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
        }
        setScrollable(p);
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
        TextView t=UiKit.text(this,"COLLECTION AT A GLANCE",11,true);
        t.setTextColor(UiKit.GOLD);
        card.addView(t);
        card.addView(UiKit.spacer(this,10));
        LinearLayout r=UiKit.row(this);
        String mm=monthFormat.format(new Date());
        r.addView(statBox(String.valueOf(watches.size()),"보유 시계"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        r.addView(statBox(String.valueOf(db.countWearInMonth(mm)),"이번 달 착용일"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        r.addView(statBox(String.valueOf(db.getStraps(0).size()),"보유 스트랩"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        card.addView(r);

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
        ImageLoader.load(this,image,w.representativeImageUrl);
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
        body.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        card.addView(body);
        card.setOnClickListener(v->showWatchDetailPopup(w));
        return card;
    }

    private View buildWatchSummarySpecs(Watch w) {
        LinearLayout box=UiKit.column(this);
        int shown=0;
        String power=normalizePower(w.movement);
        if(!power.isEmpty()) { box.addView(boldValueLine("Power", power, 12)); shown++; }
        if(!w.caliber.isEmpty()) { box.addView(boldValueLine("Movement", w.caliber, 12)); shown++; }

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
                .setMessage(w.modelName+"을 삭제할까요? 연결된 착용/오차 기록도 삭제됩니다.")
                .setNegativeButton("취소",null)
                .setPositiveButton("삭제",(d,x)->{db.deleteWatch(w.id);focusedWatchId=-1;showCollection();})
                .show();
    }

    private void showWatchDialog(Watch existing) {
        boolean editing=existing!=null;
        Watch base=editing?existing:new Watch();
        pendingWatchImageUri=null;
        pendingWatchImageClear=false;
        pendingWatchImageProcessing=false;
        pendingWatchRestoreOriginal=false;
        pendingWatchProcessedPath="";
        pendingWatchOriginalPath="";
        pendingWatchCalendarPath="";
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

        form.addView(UiKit.spacer(this,12));
        EditText model=addField(form,"Model *","예: Murph 38 / BM1350-54A",base.modelName);
        EditText brand=addField(form,"Brand","예: Hamilton / Citizen",base.brand);
        EditText ref=addField(form,"Reference","예: H70405730 / BM1350-54A",base.referenceNo);
        Button lookup=UiKit.primaryButton(this,"스펙 정밀 검색");
        form.addView(lookup);
        form.addView(UiKit.spacer(this,14));
        TextView status=UiKit.muted(this,"",12);
        status.setVisibility(View.GONE);

        form.addView(UiKit.label(this,"작동방식"));
        Spinner power=powerSpinner(base.movement);
        form.addView(power);
        EditText caliber=addField(form,"무브먼트","예: H-10 / 6R54 / E168",base.caliber);
        EditText diameter=addField(form,"Diameter (mm)","38",base.diameterMm);
        EditText l2l=addField(form,"Lug-to-lug (mm)","44.7",base.lugToLugMm);
        EditText thickness=addField(form,"Thickness (mm)","11.1",base.thicknessMm);
        EditText lug=addField(form,"Lug width (mm)","20",base.lugWidthMm);
        EditText pr=addField(form,"Power reserve (h)","80",base.powerReserveHours);
        EditText water=addField(form,"Water Resistance","예: 100 m",base.waterResistance);
        EditText crystal=addField(form,"Crystal","Sapphire",base.crystal);
        EditText material=addField(form,"Case material","Stainless Steel",base.caseMaterial);

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
            lookup.setEnabled(false);
            status.setText("정확한 모델 확인 → 공식 페이지 우선 → 레퍼런스 검증 → 출처 간 스펙 교차검증 중…");
            executor.execute(()->{
                try {
                    WebSpecLookup.Result r=WebSpecLookup.lookup(q,val(brand),val(ref));
                    runOnUiThread(()->{
                        setIf(brand,r.brand); setIf(ref,r.referenceNo); setPowerSelection(power,r.movement); setIf(caliber,r.caliber);
                        setIf(diameter,r.diameterMm); setIf(l2l,r.lugToLugMm); setIf(thickness,r.thicknessMm); setIf(lug,r.lugWidthMm);
                        setIf(pr,r.powerReserveHours); setIf(water,r.waterResistance); setIf(crystal,r.crystal); setIf(material,r.caseMaterial);
                        if(!r.sourceUrls.isEmpty()) sources.setText(r.sourceUrls);
                        status.setText(r.message);
                        lookup.setEnabled(true);
                    });
                } catch(Exception e) {
                    runOnUiThread(()->{status.setText("검색 실패: "+e.getMessage());lookup.setEnabled(true);});
                }
            });
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
            base.caseMaterial=val(material); String warrantyValue=val(warranty); base.warrantyEndDate=warrantyValue.startsWith("~")?warrantyValue.substring(1):warrantyValue; base.purchasePrice=val(purchasePrice); base.sourceUrls=val(sources); base.notes=val(notes);
            if(pendingWatchImageProcessing) {
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
            long savedId=db.saveWatch(base);
            CalendarWidgetProvider.updateAll(this);
            pendingWatchPreview=null;
            pendingWatchPhotoStatus=null;
            dlg.dismiss();
            focusedWatchId=savedId;
            WarrantyNotifier.scheduleNextCheck(this);
            showCollection();
        }));
        dlg.show();
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
        p.addView(UiKit.eyebrow(this,"ACCURACY"));
        p.addView(UiKit.text(this,"오차 측정",27,true));
        p.addView(UiKit.spacer(this,12));

        List<Watch>watches=getWatchesOrderedByWear();
        if(watches.isEmpty()) {
            p.addView(emptyCard("먼저 시계를 등록하세요","Watch Accuracy Meter 측정값은 컬렉션의 시계와 연결되어 자세별로 저장됩니다."));
            setScrollable(p);
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
        accuracyWatchSpinner=watchSpinner(watches);
        if(accuracySelectedWatchId>0) {
            int idx=indexOfWatch(watches,accuracySelectedWatchId);
            if(idx>=0) accuracyWatchSpinner.setSelection(idx);
        } else {
            accuracySelectedWatchId=watches.get(0).id;
        }
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

        openWam.setOnClickListener(v->{
            int idx=accuracyWatchSpinner==null?-1:accuracyWatchSpinner.getSelectedItemPosition();
            if(idx<0||idx>=watches.size()) return;
            Watch w=watches.get(idx);
            accuracySelectedWatchId=w.id;
            openWatchAccuracyMeter(w.id);
        });
        enterWam.setOnClickListener(v->{
            int idx=accuracyWatchSpinner==null?-1:accuracyWatchSpinner.getSelectedItemPosition();
            long watchId=(idx>=0&&idx<watches.size())?watches.get(idx).id:accuracySelectedWatchId;
            showWatchAccuracyMeterResultDialog(watchId);
        });

        LinearLayout positionContainer=UiKit.column(this);
        p.addView(positionContainer);
        refreshPositionSummary(positionContainer,accuracySelectedWatchId);

        accuracyWatchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(position>=0&&position<watches.size()) {
                    accuracySelectedWatchId=watches.get(position).id;
                    refreshPositionSummary(positionContainer,accuracySelectedWatchId);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        List<DatabaseHelper.TimegrapherRow> rows=db.getTimegrapherRows(24);
        p.addView(UiKit.text(this,"최근 측정",18,true));
        p.addView(UiKit.spacer(this,7));
        if(rows.isEmpty()) {
            p.addView(emptyCard("아직 측정 기록이 없습니다","Watch Accuracy Meter에서 측정한 뒤 결과를 입력하면 자세별 평균에 반영됩니다."));
        } else {
            for(DatabaseHelper.TimegrapherRow r:rows) p.addView(timegrapherRecordCard(r));
        }
        setScrollable(p);
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
        if(watchId<=0) return;
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
        if(watches.isEmpty()) {
            p.addView(emptyCard("먼저 시계를 등록하세요","등록한 대표사진이 캘린더 날짜마다 표시됩니다."));
            setScrollable(p);
            return;
        }

        // 캘린더와 동일한 전체 폭의 랭킹 카드. 좌우 버튼으로 올해/이번 달을 전환합니다.
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

        p.addView(UiKit.spacer(this,4));
        p.addView(UiKit.muted(this,"날짜를 누르면 착용 시계 선택 창이 팝업으로 열립니다. 하루 1개는 1회, 2개는 각 0.5회로 집계됩니다.",11));
        setScrollable(p);
    }

    private View buildTopWearRankingCard() {
        LinearLayout card=UiKit.card(this);
        card.addView(UiKit.eyebrow(this,"MOST WORN"));

        LinearLayout switcher=UiKit.row(this);
        Button left=UiKit.secondaryButton(this,"‹");
        Button right=UiKit.secondaryButton(this,"›");
        String periodTitle=wearRankingPeriod==0?"올해":"이번 달";
        TextView title=UiKit.text(this,periodTitle+" TOP 3",18,true);
        title.setGravity(Gravity.CENTER);
        switcher.addView(left);
        switcher.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        switcher.addView(right);
        card.addView(switcher);
        card.addView(UiKit.spacer(this,8));

        List<DatabaseHelper.WearStat> stats=wearRankingPeriod==0
                ? db.getTopWorn(currentYearStart(),currentYearEnd(),3)
                : db.getTopWorn(currentMonthStart(),currentMonthEnd(),3);
        if(stats.isEmpty()) {
            card.addView(UiKit.muted(this,"착용 기록이 없습니다.",12));
        } else {
            int rank=1;
            for(DatabaseHelper.WearStat stat:stats) {
                final DatabaseHelper.WearStat s=stat;
                LinearLayout r=UiKit.row(this);
                TextView no=UiKit.text(this,String.valueOf(rank),15,true); no.setTextColor(UiKit.GOLD);
                r.addView(no,new LinearLayout.LayoutParams(UiKit.dp(this,22),ViewGroup.LayoutParams.WRAP_CONTENT));
                ImageView im=watchThumb(s.imageUrl,48);
                r.addView(im,new LinearLayout.LayoutParams(UiKit.dp(this,48),UiKit.dp(this,48)));
                LinearLayout info=UiKit.column(this); info.setPadding(UiKit.dp(this,10),0,0,0);
                TextView name=UiKit.text(this,s.watchName,13,true); name.setMaxLines(2);
                TextView score=UiKit.text(this,formatWearScore(s.score),12,true); score.setTextColor(UiKit.GOLD);
                info.addView(name); info.addView(score);
                r.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
                r.setPadding(0,UiKit.dp(this,5),0,UiKit.dp(this,5));
                r.setOnClickListener(v->{ Watch watch=db.getWatch(s.watchId); if(watch!=null) showWatchDetailPopup(watch); });
                card.addView(r);
                rank++;
            }
        }
        View.OnClickListener toggle=v->{wearRankingPeriod=wearRankingPeriod==0?1:0;showCalendar();};
        left.setOnClickListener(toggle);
        right.setOnClickListener(toggle);
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
            double each=dayRows.size()>=2?0.5:1.0;
            for(DatabaseHelper.WearRow wr:dayRows) f.addView(selectedWearRow(wr,each));
        }
        f.addView(UiKit.spacer(this,10));
        f.addView(UiKit.label(this,"첫 번째 시계"));
        Spinner first=watchSpinnerWithNone(watches,"— 첫 번째 시계 선택 —");
        f.addView(first);
        f.addView(UiKit.spacer(this,8));
        f.addView(UiKit.label(this,"두 번째 시계 (선택)"));
        Spinner second=watchSpinnerWithNone(watches,"— 두 번째 시계 없음 —");
        f.addView(second);
        f.addView(UiKit.muted(this,"1개 착용 시 1회, 2개 착용 시 각 시계를 0.5회로 계산합니다.",11));
        f.addView(UiKit.spacer(this,8));
        if(dayRows.size()>0) setWatchSpinnerSelection(first,watches,dayRows.get(0).watchId);
        if(dayRows.size()>1) setWatchSpinnerSelection(second,watches,dayRows.get(1).watchId);
        EditText memo=UiKit.field(this,"메모: 스트랩, 장소, 행사 등");
        if(!dayRows.isEmpty()) memo.setText(dayRows.get(0).note);
        f.addView(memo);

        ScrollView sc=new ScrollView(this); sc.addView(f);
        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle("착용 시계 선택")
                .setView(sc)
                .setNegativeButton("취소",null)
                .setNeutralButton("전체 삭제",null)
                .setPositiveButton("저장",null)
                .create();
        dlg.setOnShowListener(x->{
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                Watch w1=selectedWatch(first,watches);
                Watch w2=selectedWatch(second,watches);
                if(w1==null&&w2==null) {
                    Toast.makeText(this,"착용한 시계를 하나 이상 선택하세요.",Toast.LENGTH_SHORT).show();
                    return;
                }
                if(w1!=null&&w2!=null&&w1.id==w2.id) {
                    Toast.makeText(this,"같은 시계를 두 번 선택할 수 없습니다.",Toast.LENGTH_SHORT).show();
                    return;
                }
                db.clearWearDate(date);
                int slot=1;
                if(w1!=null) db.saveWear(w1.id,date,slot++,val(memo));
                if(w2!=null) db.saveWear(w2.id,date,slot,val(memo));
                CalendarWidgetProvider.updateAll(this);
                dlg.dismiss();
                showCalendar();
            });
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                db.clearWearDate(date);
                CalendarWidgetProvider.updateAll(this);
                dlg.dismiss();
                showCalendar();
            });
        });
        dlg.show();
    }

    private View selectedWearRow(DatabaseHelper.WearRow wr,double score) {
        LinearLayout row=UiKit.row(this);
        ImageView im=watchThumb(wr.imageUrl,58);
        row.addView(im,new LinearLayout.LayoutParams(UiKit.dp(this,58),UiKit.dp(this,58)));
        LinearLayout info=UiKit.column(this); info.setPadding(UiKit.dp(this,10),0,0,0);
        info.addView(UiKit.text(this,wr.watchName,14,true));
        TextView count=UiKit.text(this,score==0.5?"이 날짜 0.5회":"이 날짜 1회",11,true); count.setTextColor(UiKit.GOLD);
        info.addView(count);
        row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        row.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,4));
        row.setOnClickListener(v->navigateToWatch(wr.watchId));
        return row;
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

        // 필요한 주 수만 생성해서 5주짜리 달에 불필요한 빈 6번째 행이 생기지 않도록 합니다.
        int requiredCells = offset + total;
        int weekRows = Math.max(4, (requiredCells + 6) / 7);
        int cellCount = weekRows * 7;
        for(int cell=0;cell<cellCount;cell++) {
            LinearLayout box=UiKit.column(this);
            box.setGravity(Gravity.CENTER);
            box.setPadding(UiKit.dp(this,1),UiKit.dp(this,2),UiKit.dp(this,1),UiKit.dp(this,2));
            GridLayout.LayoutParams gp=new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED,1f),GridLayout.spec(GridLayout.UNDEFINED,1f));
            gp.width=0; gp.height=UiKit.dp(this,70); box.setLayoutParams(gp);
            int day=cell-offset+1;
            if(day>=1&&day<=total) {
                Calendar c=(Calendar)first.clone(); c.set(Calendar.DAY_OF_MONTH,day);
                String key=dateFormat.format(c.getTime());
                TextView dt=UiKit.text(this,String.valueOf(day),11,key.equals(today));
                dt.setGravity(Gravity.CENTER);
                int weekday=cell%7;
                if(weekday==0) dt.setTextColor(UiKit.SUNDAY_RED);
                else if(weekday==6) dt.setTextColor(UiKit.SATURDAY_BLUE);
                else if(key.equals(selectedCalendarDate)) dt.setTextColor(UiKit.GOLD);
                box.addView(dt);

                List<DatabaseHelper.WearRow> rows=records.get(key);
                if(rows==null||rows.isEmpty()) {
                    ImageView empty=calendarWatchThumb("",42);
                    box.addView(empty,new LinearLayout.LayoutParams(UiKit.dp(this,42),UiKit.dp(this,42)));
                } else {
                    LinearLayout photos=UiKit.row(this); photos.setGravity(Gravity.CENTER);
                    int size=rows.size()>=2?22:42;
                    for(int i=0;i<Math.min(2,rows.size());i++) {
                        DatabaseHelper.WearRow wr=rows.get(i);
                        ImageView im=calendarWatchThumb(wr.imageUrl,size);
                        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(UiKit.dp(this,size),UiKit.dp(this,size));
                        if(i>0) pp.setMargins(UiKit.dp(this,1),0,0,0);
                        photos.addView(im,pp);
                        // 착용 기록이 있는 날짜의 사진을 눌러도 날짜 선택 팝업이 열리도록
                        // 이미지 자체에는 상세 페이지 이동 리스너를 두지 않습니다.
                        im.setClickable(false);
                    }
                    box.addView(photos);
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
            p.addView(compatibleWatchesPanel(filterWidth));
            p.addView(UiKit.spacer(this,2));
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
        LinearLayout card=UiKit.card(this);
        card.addView(UiKit.eyebrow(this,widthMm+" MM COMPATIBILITY"));
        card.addView(UiKit.text(this,widthMm+" mm Lug Width Watch",17,true));
        card.addView(UiKit.spacer(this,9));
        List<Watch> watches=db.getCompatibleWatches(widthMm);
        if(watches.isEmpty()) {
            card.addView(UiKit.muted(this,"현재 컬렉션에 "+widthMm+" mm 러그 폭 시계가 없습니다.",12));
            return card;
        }
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row=UiKit.row(this);
        for(Watch w:watches) {
            LinearLayout item=UiKit.column(this);
            item.setGravity(Gravity.CENTER);
            ImageView im=watchThumb(w.representativeImageUrl,82);
            item.addView(im,new LinearLayout.LayoutParams(UiKit.dp(this,82),UiKit.dp(this,82)));
            TextView name=UiKit.text(this,w.modelName,11,true);
            name.setGravity(Gravity.CENTER); name.setMaxLines(2);
            LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(UiKit.dp(this,104),ViewGroup.LayoutParams.WRAP_CONTENT);
            np.setMargins(0,UiKit.dp(this,5),0,0);
            item.addView(name,np);
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(UiKit.dp(this,112),ViewGroup.LayoutParams.WRAP_CONTENT);
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
        im.setScaleType(ImageView.ScaleType.CENTER_CROP);
        im.setBackground(UiKit.rounded(UiKit.IMAGE_BG,Color.TRANSPARENT,14,this));
        im.setClipToOutline(true);
        im.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        ImageLoader.load(this,im,s.imagePath);
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
        if(s.widthMm<0) info.addView(UiKit.muted(this,"Extra 폭 · 자동 호환성 미지정",11));
        else if(compatible.isEmpty()) info.addView(UiKit.muted(this,"현재 호환 시계 없음",11));
        else info.addView(UiKit.muted(this,"호환 시계 "+compatible.size()+"점 · 위의 "+s.widthMm+" mm 목록에서 확인",11));
        row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        card.addView(row);

        LinearLayout actions=UiKit.row(this);
        actions.setPadding(0,UiKit.dp(this,10),0,0);
        Button edit=UiKit.secondaryButton(this,"수정");
        Button del=UiKit.secondaryButton(this,"삭제");
        edit.setOnClickListener(v->showStrapDialog(s));
        del.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("스트랩 삭제")
                .setMessage(s.name+"을 삭제할까요?")
                .setNegativeButton("취소",null)
                .setPositiveButton("삭제",(d,x)->{db.deleteStrap(s.id);showStraps(s.widthMm);})
                .show());
        actions.addView(edit,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        actions.addView(del,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        card.addView(actions);
        return card;
    }

    private void showStrapDialog(Strap existing) {
        Strap base=existing==null?new Strap():existing;
        pendingStrapImageUri=null;
        LinearLayout f=UiKit.column(this);
        f.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,8));

        // 스트랩 정보의 첫 항목은 제작사입니다.
        EditText maker=addField(f,"스트랩 제작사","예: Hirsch / Delugs / Accurate Form",base.maker);
        EditText name=addField(f,"스트랩 이름 *","예: 네이비 카프 레더",base.name);
        f.addView(UiKit.label(this,"러그 폭"));
        Spinner width=new Spinner(this);
        String[] ws={"18 mm","19 mm","20 mm","21 mm","22 mm","Extra"};
        width.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ws));
        int widthSelection=(base.widthMm>=18&&base.widthMm<=22)?base.widthMm-18:5;
        width.setSelection(widthSelection);
        f.addView(width);
        f.addView(UiKit.spacer(this,10));

        ImageView preview=new ImageView(this);
        pendingStrapPreview=preview;
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(UiKit.rounded(UiKit.IMAGE_BG,Color.TRANSPARENT,16,this));
        preview.setClipToOutline(true);
        preview.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        ImageLoader.load(this,preview,base.imagePath);
        f.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,180)));
        Button pick=UiKit.secondaryButton(this,"사진 선택");
        pick.setOnClickListener(v->openStrapImagePicker());
        f.addView(pick);
        f.addView(UiKit.spacer(this,10));

        EditText color=addField(f,"색상","Navy / Brown / Black",base.color);
        f.addView(UiKit.label(this,"재질"));
        Spinner material=new Spinner(this);
        String[] materialItems={"Leather","Bracelet","Rubber","Nato","Mesh","Extra"};
        material.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,materialItems));
        decorateSpinner(material);
        String currentMaterial=normalizeStrapMaterial(base.material);
        int materialSelection=5;
        for(int i=0;i<materialItems.length;i++) if(materialItems[i].equalsIgnoreCase(currentMaterial)){materialSelection=i;break;}
        material.setSelection(materialSelection);
        f.addView(material);
        EditText buckle=addField(f,"버클/특징","Deployant / Pin buckle",base.buckle);
        EditText notes=addField(f,"메모","구매처, 길이, 특징 등",base.notes);

        ScrollView sc=new ScrollView(this); sc.addView(f);
        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle(existing==null?"스트랩 추가":"스트랩 수정")
                .setView(sc)
                .setNegativeButton("취소",null)
                .setPositiveButton("저장",null)
                .create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(val(name).isEmpty()){name.setError("이름은 필수입니다.");return;}
            base.maker=val(maker);
            base.name=val(name);
            base.widthMm=width.getSelectedItemPosition()==5?-1:18+width.getSelectedItemPosition();
            base.color=val(color);
            base.material=String.valueOf(material.getSelectedItem()); base.buckle=val(buckle); base.notes=val(notes);
            if(pendingStrapImageUri!=null) {
                try { base.imagePath=ImageLoader.importUri(this,pendingStrapImageUri); }
                catch(Exception e) { Toast.makeText(this,"사진 저장 실패: "+e.getMessage(),Toast.LENGTH_SHORT).show(); }
            }
            db.saveStrap(base);
            pendingStrapPreview=null;
            dlg.dismiss();
            showStraps(base.widthMm);
        }));
        dlg.show();
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

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
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
            if(pendingStrapPreview!=null) ImageLoader.load(this,pendingStrapPreview,pendingStrapImageUri.toString());
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

    private Button overlayArrowButton(String text) {
        Button b=new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(22);
        b.setAllCaps(false);
        b.setMinWidth(0); b.setMinHeight(0);
        b.setPadding(0,0,0,UiKit.dp(this,2));
        b.setBackground(UiKit.rounded(Color.argb(200,22,32,51),Color.argb(110,255,255,255),14,this));
        return b;
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
        LinearLayout body=UiKit.column(this);
        body.setPadding(UiKit.dp(this,16),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,16));

        LinearLayout hero=UiKit.card(this);
        hero.setBackground(UiKit.rounded(UiKit.PHOTO_BG,UiKit.LINE,18,this));
        ImageView image=new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,16,this));
        image.setClipToOutline(true);
        image.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        ImageLoader.loadFit(this,image,w.representativeImageUrl);
        hero.addView(image,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,238)));
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
            if(!w.powerReserveHours.isEmpty()) essential.addView(detailSpecRow("Power reserve",w.powerReserveHours+" h"));
            body.addView(essential);
        }

        if(!w.diameterMm.isEmpty()||!w.lugToLugMm.isEmpty()||!w.thicknessMm.isEmpty()||!w.lugWidthMm.isEmpty()) {
            LinearLayout dimensions=detailSection("DIMENSIONS");
            if(!w.diameterMm.isEmpty()) dimensions.addView(detailSpecRow("Diameter",w.diameterMm+" mm"));
            if(!w.lugToLugMm.isEmpty()) dimensions.addView(detailSpecRow("Lug-to-lug",w.lugToLugMm+" mm"));
            if(!w.thicknessMm.isEmpty()) dimensions.addView(detailSpecRow("Thickness",w.thicknessMm+" mm"));
            if(!w.lugWidthMm.isEmpty()) dimensions.addView(detailSpecRow("Lug width",w.lugWidthMm+" mm"));
            body.addView(dimensions);
        }

        if(!w.waterResistance.isEmpty()||!w.crystal.isEmpty()||!w.caseMaterial.isEmpty()) {
            LinearLayout build=detailSection("BUILD");
            if(!w.waterResistance.isEmpty()) build.addView(detailSpecRow("Water Resistance",formatWr(w.waterResistance)));
            if(!w.crystal.isEmpty()) build.addView(detailSpecRow("Crystal",w.crystal));
            if(!w.caseMaterial.isEmpty()) build.addView(detailSpecRow("Case material",w.caseMaterial));
            body.addView(build);
        }

        if(!w.warrantyEndDate.isEmpty()||!w.purchasePrice.isEmpty()) {
            LinearLayout ownership=detailSection("OWNERSHIP");
            if(!w.warrantyEndDate.isEmpty()) ownership.addView(detailSpecRow("Warranty","~"+w.warrantyEndDate));
            if(!w.purchasePrice.isEmpty()) ownership.addView(detailSpecRow("Purchase price",w.purchasePrice));
            body.addView(ownership);
        }

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
        Button source=UiKit.secondaryButton(this,"출처");
        Button delete=UiKit.secondaryButton(this,"삭제");
        source.setEnabled(!w.sourceUrls.trim().isEmpty());
        actions.addView(edit,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        actions.addView(source,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        actions.addView(delete,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        body.addView(actions);

        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        final AlertDialog[] detailDialog=new AlertDialog[1];
        detailDialog[0]=new AlertDialog.Builder(this)
                .setTitle("Watch Details")
                .setView(scroll)
                .setPositiveButton("닫기",null)
                .create();
        edit.setOnClickListener(v->{
            if(detailDialog[0]!=null) detailDialog[0].dismiss();
            showWatchDialog(w);
        });
        source.setOnClickListener(v->openFirstSource(w.sourceUrls));
        delete.setOnClickListener(v->{
            if(detailDialog[0]!=null) detailDialog[0].dismiss();
            confirmDeleteWatch(w);
        });
        detailDialog[0].show();
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
        return String.format(Locale.KOREA,"%.1f회",score);
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
