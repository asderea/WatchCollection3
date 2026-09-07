package com.watchcollection.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
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
    private int collectionFilter = 0; // 0 전체, 1 기계식, 2 쿼츠
    private String collectionBrandFilter = ""; // 빈 문자열 = 전체 브랜드, __UNBRANDED__ = 브랜드 미지정
    private int monthlyWearPage = 0; // 0 = 1~5위, 1 = 6~10위
    private int wearRankingPeriod = 0; // 0 올해, 1 이번 달
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

    private TimegrapherEngine timegrapherEngine;
    private TimegrapherView timegrapherView;
    private Spinner timegrapherWatchSpinner;
    private EditText timegrapherLiftAngle;
    private Button timegrapherStartButton, timegrapherSaveButton;
    private TextView timegrapherStatus;
    private TimegrapherEngine.Snapshot latestTimegrapherSnapshot;
    private boolean pendingStartTimegrapher = false;
    private long accuracySelectedWatchId = -1;

    private static final int REQ_STRAP_IMAGE = 2201;
    private static final int REQ_WATCH_IMAGE = 2202;
    private static final int REQ_RECORD_AUDIO = 2301;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DatabaseHelper(this);
        initTimegrapherEngine();
        visibleMonth.set(Calendar.DAY_OF_MONTH, 1);
        buildShell();
        showCollection();
    }

    @Override protected void onDestroy() {
        if (timegrapherEngine != null && timegrapherEngine.isRunning()) timegrapherEngine.stop();
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
        navStraps.setOnClickListener(v -> showStraps(0));
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
        if (active != navAccuracy && timegrapherEngine != null && timegrapherEngine.isRunning()) {
            timegrapherEngine.stop();
        }
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

        if(!allWatches.isEmpty()) {
            p.addView(collectionStats(allWatches));
            p.addView(monthlyWearSummary());

            p.addView(UiKit.label(this,"무브먼트"));
            LinearLayout filterRow=UiKit.row(this);
            String[] labels={"전체","기계식","쿼츠"};
            for(int i=0;i<labels.length;i++) {
                final int idx=i;
                Button b=UiKit.chip(this,labels[i],collectionFilter==i);
                b.setOnClickListener(v->{collectionFilter=idx;showCollection();});
                filterRow.addView(b,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            }
            p.addView(filterRow);
            p.addView(UiKit.spacer(this,10));
            p.addView(brandFilterView(allWatches));
            p.addView(UiKit.spacer(this,12));
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
            if(shown==0) p.addView(emptyCard("해당 분류의 시계가 없습니다","무브먼트 정보가 비어 있다면 시계 수정에서 입력해 주세요."));
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
        TextView most=UiKit.muted(this,"가장 많이 착용한 시계 · "+db.getMostWornWatchName(),11);
        most.setTextColor(Color.rgb(205,211,219));
        most.setPadding(0,UiKit.dp(this,10),0,0);
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
        boolean hasSecondPage=entries.size()>5;
        if(monthlyWearPage>0 && !hasSecondPage) monthlyWearPage=0;

        LinearLayout pager=UiKit.row(this);
        Button prev=UiKit.secondaryButton(this,"‹");
        Button next=UiKit.secondaryButton(this,"›");
        prev.setEnabled(monthlyWearPage>0);
        next.setEnabled(monthlyWearPage==0 && hasSecondPage);
        prev.setOnClickListener(v->{monthlyWearPage=0;showCollection();});
        next.setOnClickListener(v->{monthlyWearPage=1;showCollection();});
        TextView pageLabel=UiKit.text(this,monthlyWearPage==0?"1–5위":"6–10위",13,true);
        pageLabel.setGravity(Gravity.CENTER);
        pager.addView(prev,new LinearLayout.LayoutParams(UiKit.dp(this,54),ViewGroup.LayoutParams.WRAP_CONTENT));
        pager.addView(pageLabel,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        pager.addView(next,new LinearLayout.LayoutParams(UiKit.dp(this,54),ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(pager);
        card.addView(UiKit.spacer(this,6));

        int from=monthlyWearPage*5;
        int to=Math.min(Math.min(from+5,10),entries.size());
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
            row.setOnClickListener(v->navigateToWatch(w.id));
            card.addView(row);
        }
        if(monthlyWearPage==1 && to<=from) {
            card.addView(UiKit.muted(this,"6위 이하 착용 기록이 아직 없습니다.",12));
        }
        return card;
    }

    private View brandFilterView(List<Watch> watches) {
        LinearLayout box=UiKit.column(this);
        box.addView(UiKit.label(this,"브랜드"));
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row=UiKit.row(this);

        Button all=UiKit.chip(this,"전체 브랜드",collectionBrandFilter.isEmpty());
        all.setOnClickListener(v->{collectionBrandFilter="";showCollection();});
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.setMargins(0,0,UiKit.dp(this,6),0);
        row.addView(all,ap);

        List<String> brands=new ArrayList<>();
        boolean hasUnbranded=false;
        for(Watch w:watches) {
            String brand=safeText(w.brand).trim();
            if(brand.isEmpty()) {
                hasUnbranded=true;
                continue;
            }
            boolean exists=false;
            for(String b:brands) if(b.equalsIgnoreCase(brand)) {exists=true;break;}
            if(!exists) brands.add(brand);
        }
        brands.sort(String.CASE_INSENSITIVE_ORDER);
        for(String brand:brands) {
            final String selectedBrand=brand;
            Button b=UiKit.chip(this,brand,brand.equalsIgnoreCase(collectionBrandFilter));
            b.setOnClickListener(v->{collectionBrandFilter=selectedBrand;showCollection();});
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.setMargins(0,0,UiKit.dp(this,6),0);
            row.addView(b,bp);
        }
        if(hasUnbranded) {
            Button b=UiKit.chip(this,"미지정","__UNBRANDED__".equals(collectionBrandFilter));
            b.setOnClickListener(v->{collectionBrandFilter="__UNBRANDED__";showCollection();});
            row.addView(b,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        scroll.addView(row);
        box.addView(scroll);
        return box;
    }

    private boolean matchesBrandFilter(Watch w) {
        if(collectionBrandFilter==null||collectionBrandFilter.isEmpty()) return true;
        String brand=safeText(w.brand).trim();
        if("__UNBRANDED__".equals(collectionBrandFilter)) return brand.isEmpty();
        return brand.equalsIgnoreCase(collectionBrandFilter);
    }

    private boolean matchesCollectionFilter(Watch w) {
        if(collectionFilter==0) return true;
        String m=w.movement==null?"":w.movement.toLowerCase(Locale.ROOT);
        if(collectionFilter==1) return m.contains("automatic")||m.contains("manual")||m.contains("mechanical")||m.contains("hand-wound")||m.contains("self-winding");
        return m.contains("quartz")||m.contains("solar");
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
        String spec=joinSpecs(w);
        if(!spec.isEmpty()) info.addView(UiKit.text(this,spec,12,false));

        double avg=db.averageSecPerDay(w.id);
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

        LinearLayout actions=UiKit.row(this);
        actions.setPadding(0,UiKit.dp(this,12),0,0);
        Button edit=UiKit.secondaryButton(this,"수정");
        Button source=UiKit.secondaryButton(this,"공식/출처");
        Button del=UiKit.secondaryButton(this,"삭제");
        edit.setOnClickListener(v->showWatchDialog(w));
        source.setEnabled(!w.sourceUrls.trim().isEmpty());
        source.setOnClickListener(v->openFirstSource(w.sourceUrls));
        del.setOnClickListener(v->confirmDeleteWatch(w));
        actions.addView(edit,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        actions.addView(source,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        actions.addView(del,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        card.addView(actions);
        return card;
    }

    private String joinSpecs(Watch w) {
        List<String>s=new ArrayList<>();
        if(!w.movement.isEmpty()) s.add(w.movement+(w.caliber.isEmpty()?"":" · "+w.caliber));
        if(!w.diameterMm.isEmpty()) s.add("Ø "+w.diameterMm+" mm");
        if(!w.lugWidthMm.isEmpty()) s.add("Lug "+w.lugWidthMm+" mm");
        if(!w.waterResistance.isEmpty()) s.add(w.waterResistance);
        return String.join("  |  ",s);
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
            preview.setImageDrawable(null);
            preview.setBackground(UiKit.rounded(UiKit.PHOTO_BG,Color.TRANSPARENT,16,this));
            if(pendingWatchPhotoStatus!=null) pendingWatchPhotoStatus.setText("대표 사진을 제거합니다.");
        });
        photoActions.addView(pickWatchPhoto,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        photoActions.addView(restoreWatchPhoto,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        photoActions.addView(clearWatchPhoto,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        form.addView(photoActions);
        pendingWatchPhotoStatus=UiKit.muted(this,"사진을 선택하면 배경을 캘린더 사진 색상(#FCFBF7)으로 자동 통일하고 원본은 별도 보관합니다.",11);
        pendingWatchPhotoStatus.setPadding(0,UiKit.dp(this,6),0,0);
        form.addView(pendingWatchPhotoStatus);

        form.addView(UiKit.spacer(this,12));
        EditText model=addField(form,"모델명 *","예: Murph 38 / BM1350-54A",base.modelName);
        EditText brand=addField(form,"브랜드 (선택 · 검색 정확도 향상)","예: Hamilton / Citizen",base.brand);
        EditText ref=addField(form,"레퍼런스 (가능하면 입력 권장)","예: H70405730 / BM1350-54A",base.referenceNo);
        Button lookup=UiKit.primaryButton(this,"스펙 정밀 검색");
        form.addView(lookup);
        TextView status=UiKit.muted(this,"정확성 우선 검색입니다. 공식 제조사 페이지를 최우선으로 쓰고, 공식에 없는 값은 신뢰 가능한 복수 출처가 일치할 때만 채웁니다. 사진은 직접 선택합니다.",12);
        status.setPadding(0,UiKit.dp(this,7),0,UiKit.dp(this,12));
        form.addView(status);

        EditText movement=addField(form,"무브먼트","Automatic",base.movement);
        EditText caliber=addField(form,"칼리버","H-10",base.caliber);
        EditText diameter=addField(form,"직경 mm","38",base.diameterMm);
        EditText l2l=addField(form,"러그투러그 mm","44.7",base.lugToLugMm);
        EditText thickness=addField(form,"두께 mm","11.1",base.thicknessMm);
        EditText lug=addField(form,"러그 폭 mm","20",base.lugWidthMm);
        EditText pr=addField(form,"파워리저브 h","80",base.powerReserveHours);
        EditText water=addField(form,"방수","100 m",base.waterResistance);
        EditText crystal=addField(form,"글라스","Sapphire",base.crystal);
        EditText material=addField(form,"케이스 소재","Stainless Steel",base.caseMaterial);
        EditText sources=addField(form,"검증된 출처 URL","공식 페이지/시계 전문 출처만 저장",base.sourceUrls);
        sources.setSingleLine(false); sources.setMinLines(2);
        EditText notes=addField(form,"메모","구매일, 특징 등",base.notes);

        lookup.setOnClickListener(v->{
            String q=val(model);
            if(q.isEmpty()){model.setError("모델명을 입력하세요.");return;}
            lookup.setEnabled(false);
            status.setText("정확한 모델 확인 → 공식 페이지 우선 → 레퍼런스 검증 → 출처 간 스펙 교차검증 중…");
            executor.execute(()->{
                try {
                    WebSpecLookup.Result r=WebSpecLookup.lookup(q,val(brand),val(ref));
                    runOnUiThread(()->{
                        setIf(brand,r.brand); setIf(ref,r.referenceNo); setIf(movement,r.movement); setIf(caliber,r.caliber);
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
            base.modelName=val(model); base.brand=val(brand); base.referenceNo=val(ref); base.movement=val(movement);
            base.caliber=val(caliber); base.diameterMm=val(diameter); base.lugToLugMm=val(l2l); base.thicknessMm=val(thickness);
            base.lugWidthMm=val(lug); base.powerReserveHours=val(pr); base.waterResistance=val(water); base.crystal=val(crystal);
            base.caseMaterial=val(material); base.sourceUrls=val(sources); base.notes=val(notes);
            if(pendingWatchImageProcessing) {
                Toast.makeText(this,"사진 배경을 처리 중입니다. 잠시 후 다시 저장해 주세요.",Toast.LENGTH_SHORT).show();
                return;
            }
            if(pendingWatchImageClear) {
                base.representativeImageUrl="";
                base.originalImageUrl="";
                base.imageSourceUrl="";
            } else if(!pendingWatchOriginalPath.isEmpty()) {
                base.originalImageUrl=pendingWatchOriginalPath;
                base.representativeImageUrl=pendingWatchRestoreOriginal?pendingWatchOriginalPath:
                        (pendingWatchProcessedPath.isEmpty()?pendingWatchOriginalPath:pendingWatchProcessedPath);
                base.imageSourceUrl=pendingWatchRestoreOriginal?"manual-original":"manual-normalized";
            } else if(pendingWatchRestoreOriginal && base.originalImageUrl!=null && !base.originalImageUrl.isEmpty()) {
                base.representativeImageUrl=base.originalImageUrl;
                base.imageSourceUrl="manual-original";
            }
            long savedId=db.saveWatch(base);
            pendingWatchPreview=null;
            pendingWatchPhotoStatus=null;
            dlg.dismiss();
            focusedWatchId=savedId;
            showCollection();
        }));
        dlg.show();
    }

    // ACCURACY / TIMEGRAPHER -------------------------------------------------

    private void initTimegrapherEngine() {
        timegrapherEngine = new TimegrapherEngine(new TimegrapherEngine.Listener() {
            @Override public void onSnapshot(TimegrapherEngine.Snapshot snapshot) {
                runOnUiThread(() -> updateTimegrapherUi(snapshot));
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    if (timegrapherStatus != null) timegrapherStatus.setText(message);
                    if (timegrapherStartButton != null) timegrapherStartButton.setText("Start");
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showAccuracy() {
        selectNav(navAccuracy);
        LinearLayout p=page();
        p.addView(UiKit.eyebrow(this,"TIMEGRAPHER"));
        p.addView(UiKit.text(this,"오차 측정",27,true));
        p.addView(UiKit.spacer(this,4));
        p.addView(UiKit.muted(this,"휴대폰 마이크로 기계식 시계의 틱/톡을 분석합니다. 수동 오차 입력은 제거했습니다.",13));
        p.addView(UiKit.spacer(this,12));

        List<Watch>watches=db.getWatches();
        if(watches.isEmpty()) {
            p.addView(emptyCard("먼저 시계를 등록하세요","타임그래퍼 측정값은 컬렉션의 시계와 연결되어 자세별로 저장됩니다."));
            setScrollable(p);
            return;
        }

        LinearLayout setup=UiKit.card(this);
        setup.addView(UiKit.eyebrow(this,"MEASUREMENT SETUP"));
        setup.addView(UiKit.label(this,"측정 시계"));
        timegrapherWatchSpinner=watchSpinner(watches);
        if(accuracySelectedWatchId>0) {
            int idx=indexOfWatch(watches,accuracySelectedWatchId);
            if(idx>=0) timegrapherWatchSpinner.setSelection(idx);
        } else if(!watches.isEmpty()) {
            accuracySelectedWatchId=watches.get(0).id;
        }
        setup.addView(timegrapherWatchSpinner);
        setup.addView(UiKit.spacer(this,8));

        timegrapherLiftAngle=UiKit.field(this,"예: 52");
        timegrapherLiftAngle.setText("52");
        timegrapherLiftAngle.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        setup.addView(UiKit.label(this,"Lift angle (°) · amplitude 계산에 사용"));
        setup.addView(timegrapherLiftAngle);

        TextView auto=UiKit.muted(this,"BPH AUTO 후보 · "+TimegrapherEngine.commonBphText()+" bph",11);
        auto.setPadding(0,0,0,UiKit.dp(this,4));
        setup.addView(auto);
        setup.addView(UiKit.muted(this,"조용한 곳에서 시계를 충분히 감고, 가능하면 크라운/케이스를 휴대폰 마이크 가까이에 두세요. 신호가 약하면 amplitude는 '-'로 남습니다.",11));
        p.addView(setup);

        LinearLayout meterCard=UiKit.card(this);
        timegrapherView=new TimegrapherView(this);
        meterCard.addView(timegrapherView,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,520)));
        timegrapherStatus=UiKit.muted(this,"Start를 눌러 측정을 시작하세요.",12);
        timegrapherStatus.setPadding(0,UiKit.dp(this,8),0,UiKit.dp(this,8));
        meterCard.addView(timegrapherStatus);
        LinearLayout controls=UiKit.row(this);
        timegrapherStartButton=UiKit.primaryButton(this,"Start");
        timegrapherSaveButton=UiKit.secondaryButton(this,"Save");
        timegrapherSaveButton.setEnabled(false);
        controls.addView(timegrapherStartButton,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        controls.addView(timegrapherSaveButton,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        meterCard.addView(controls);
        p.addView(meterCard);

        LinearLayout positionContainer=UiKit.column(this);
        p.addView(positionContainer);
        refreshPositionSummary(positionContainer,accuracySelectedWatchId);

        timegrapherWatchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(position>=0&&position<watches.size()) {
                    accuracySelectedWatchId=watches.get(position).id;
                    refreshPositionSummary(positionContainer,accuracySelectedWatchId);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        timegrapherStartButton.setOnClickListener(v->{
            if(timegrapherEngine.isRunning()) {
                timegrapherEngine.stop();
                timegrapherStartButton.setText("Start");
                return;
            }
            startTimegrapherMeasurement();
        });

        timegrapherSaveButton.setOnClickListener(v->{
            if(timegrapherEngine.isRunning()) timegrapherEngine.stop();
            TimegrapherEngine.Snapshot snap=timegrapherEngine.getLatest();
            if(snap==null||!snap.isUsable()) {
                Toast.makeText(this,"아직 저장할 만큼 안정적인 측정값이 없습니다.",Toast.LENGTH_SHORT).show();
                return;
            }
            showTimegrapherSaveDialog(watches,snap);
        });

        List<DatabaseHelper.TimegrapherRow> rows=db.getTimegrapherRows(24);
        p.addView(UiKit.text(this,"최근 측정",18,true));
        p.addView(UiKit.spacer(this,7));
        if(rows.isEmpty()) {
            p.addView(emptyCard("아직 측정 기록이 없습니다","Start → 신호 안정화 → Save 순서로 자세별 측정값을 저장할 수 있습니다."));
        } else {
            for(DatabaseHelper.TimegrapherRow r:rows) p.addView(timegrapherRecordCard(r));
        }

        TimegrapherEngine.Snapshot current=timegrapherEngine.getLatest();
        updateTimegrapherUi(current);
        setScrollable(p);
    }

    private void startTimegrapherMeasurement() {
        double lift=52.0;
        try {
            lift=Double.parseDouble(val(timegrapherLiftAngle));
            if(lift<30||lift>70) throw new IllegalArgumentException();
        } catch(Exception e) {
            timegrapherLiftAngle.setError("30~70° 범위로 입력하세요.");
            return;
        }
        timegrapherEngine.setLiftAngleDeg(lift);
        if(timegrapherView!=null) timegrapherView.setLiftAngle(lift);

        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            pendingStartTimegrapher=true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_RECORD_AUDIO);
            return;
        }
        pendingStartTimegrapher=false;
        if(timegrapherStatus!=null) timegrapherStatus.setText("틱/톡을 찾는 중…");
        if(timegrapherStartButton!=null) timegrapherStartButton.setText("Stop");
        timegrapherEngine.start();
    }

    private void updateTimegrapherUi(TimegrapherEngine.Snapshot snapshot) {
        if(snapshot==null) return;
        latestTimegrapherSnapshot=snapshot;
        if(timegrapherView!=null) {
            try {
                double lift=Double.parseDouble(val(timegrapherLiftAngle));
                timegrapherView.setLiftAngle(lift);
            } catch(Exception ignored) {}
            timegrapherView.setSnapshot(snapshot);
        }
        if(timegrapherStatus!=null) {
            String bph=snapshot.bph>0?String.format(Locale.KOREA,"%,d bph",snapshot.bph):"BPH AUTO";
            timegrapherStatus.setText(snapshot.status+" · "+bph+" · 신호 "+snapshot.qualityLabel());
        }
        if(timegrapherStartButton!=null) timegrapherStartButton.setText(snapshot.running?"Stop":"Start");
        if(timegrapherSaveButton!=null) timegrapherSaveButton.setEnabled(snapshot.isUsable());
    }

    private void showTimegrapherSaveDialog(List<Watch>watches,TimegrapherEngine.Snapshot snap) {
        LinearLayout f=UiKit.column(this);
        f.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,8));

        f.addView(UiKit.label(this,"Watch"));
        Spinner watch=watchSpinner(watches);
        int selected=indexOfWatch(watches,accuracySelectedWatchId);
        if(selected>=0) watch.setSelection(selected);
        f.addView(watch);
        f.addView(UiKit.spacer(this,10));

        LinearLayout metrics=UiKit.card(this);
        metrics.addView(metricLine("BPH",String.format(Locale.KOREA,"%,d",snap.bph)));
        metrics.addView(metricLine("Rate (s/day)",String.format(Locale.KOREA,"%+.1f",snap.rateSecDay)));
        metrics.addView(metricLine("Beat error (ms)",String.format(Locale.KOREA,"%.2f",snap.beatErrorMs)));
        metrics.addView(metricLine("Amplitude °",Double.isNaN(snap.amplitudeDeg)?"-":String.format(Locale.KOREA,"%.0f",snap.amplitudeDeg)));
        metrics.addView(metricLine("Signal",String.format(Locale.KOREA,"%s · %.0f%%",snap.qualityLabel(),snap.signalQuality)));
        f.addView(metrics);

        f.addView(UiKit.label(this,"측정 자세"));
        RadioGroup positions=new RadioGroup(this);
        positions.setOrientation(RadioGroup.VERTICAL);
        String[][] positionItems={
                {"Dial up","●  다이얼 위 (Dial up)"},
                {"Dial down","○  다이얼 아래 (Dial down)"},
                {"12 Up","12 ↑  12 Up"},
                {"9 Up","9 ↑  9 Up"},
                {"6 Up","6 ↑  6 Up"},
                {"3 Up","3 ↑  3 Up"}
        };
        for(int i=0;i<positionItems.length;i++) {
            RadioButton rb=new RadioButton(this);
            rb.setId(4100+i);
            rb.setText(positionItems[i][1]);
            rb.setTextColor(UiKit.INK);
            rb.setTextSize(15);
            rb.setTag(positionItems[i][0]);
            rb.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,4));
            positions.addView(rb);
        }
        positions.check(4100);
        f.addView(positions);
        f.addView(UiKit.spacer(this,8));

        EditText date=UiKit.field(this,"날짜");
        date.setText(dateFormat.format(new Date()));
        date.setFocusable(false);
        date.setOnClickListener(v->pickDate(val(date),date::setText));
        f.addView(UiKit.label(this,"측정 날짜"));
        f.addView(date);
        EditText note=UiKit.field(this,"예: 완전 감기 / 30분 안정화 후");
        f.addView(UiKit.label(this,"메모"));
        f.addView(note);

        ScrollView sc=new ScrollView(this); sc.addView(f);
        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle("측정값 저장")
                .setView(sc)
                .setNegativeButton("취소",null)
                .setPositiveButton("저장",null)
                .create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int pos=watch.getSelectedItemPosition();
            if(pos<0||pos>=watches.size()) return;
            Watch w=watches.get(pos);
            int checked=positions.getCheckedRadioButtonId();
            RadioButton rb=positions.findViewById(checked);
            String position=rb==null?"Dial up":String.valueOf(rb.getTag());
            double lift=52;
            try{lift=Double.parseDouble(val(timegrapherLiftAngle));}catch(Exception ignored){}
            db.addTimegrapherRecord(w.id,val(date),snap.bph,snap.rateSecDay,snap.beatErrorMs,
                    snap.amplitudeDeg,lift,position,snap.signalQuality,snap.elapsedSeconds,val(note));
            accuracySelectedWatchId=w.id;
            dlg.dismiss();
            Toast.makeText(this,""+positionLabel(position)+" 측정값을 저장했습니다.",Toast.LENGTH_SHORT).show();
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
        List<DatabaseHelper.TimegrapherRow> rows=db.getLatestPositionsForWatch(watchId);
        LinearLayout card=UiKit.card(this);
        card.addView(UiKit.eyebrow(this,"POSITIONAL RATE"));
        Watch w=db.getWatch(watchId);
        card.addView(UiKit.text(this,(w==null?"시계":w.modelName)+" · 자세별 최근값",17,true));
        card.addView(UiKit.spacer(this,6));
        String[] positions={"Dial up","Dial down","12 Up","9 Up","6 Up","3 Up"};
        for(String p:positions) {
            DatabaseHelper.TimegrapherRow found=null;
            for(DatabaseHelper.TimegrapherRow r:rows) if(p.equals(r.position)){found=r;break;}
            LinearLayout line=UiKit.row(this);
            TextView name=UiKit.text(this,positionLabel(p),12,true);
            line.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            String value=found==null?"—":String.format(Locale.KOREA,"%+.1f s/d · %.2f ms%s",
                    found.rateSecDay,found.beatErrorMs,Double.isNaN(found.amplitudeDeg)?"":" · "+String.format(Locale.KOREA,"%.0f°",found.amplitudeDeg));
            TextView val=UiKit.text(this,value,11,found!=null); val.setTextColor(found==null?UiKit.MUTED:UiKit.NAVY_2);
            line.addView(val);
            line.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,4));
            card.addView(line);
        }
        container.addView(card);
    }

    private View timegrapherRecordCard(DatabaseHelper.TimegrapherRow r) {
        LinearLayout card=UiKit.card(this);
        LinearLayout head=UiKit.row(this);
        LinearLayout names=UiKit.column(this);
        names.addView(UiKit.text(this,r.watchName,15,true));
        names.addView(UiKit.muted(this,r.date+" · "+positionLabel(r.position),11));
        head.addView(names,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView rate=UiKit.text(this,String.format(Locale.KOREA,"%+.1f s/d",r.rateSecDay),17,true);
        rate.setTextColor(r.rateSecDay>0?Color.rgb(154,87,64):UiKit.NAVY_2);
        head.addView(rate);
        card.addView(head);
        String amp=Double.isNaN(r.amplitudeDeg)?"-":String.format(Locale.KOREA,"%.0f°",r.amplitudeDeg);
        card.addView(UiKit.muted(this,String.format(Locale.KOREA,"%,d bph · Beat %.2f ms · Amp %s · Lift %.0f° · Signal %.0f%%",
                r.bph,r.beatErrorMs,amp,r.liftAngleDeg,r.signalQuality),11));
        if(!r.note.isEmpty()) card.addView(UiKit.muted(this,r.note,11));
        return card;
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
                r.setOnClickListener(v->navigateToWatch(s.watchId));
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
                dlg.dismiss();
                showCalendar();
            });
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                db.clearWearDate(date);
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
        for(String d:days) {
            TextView tv=UiKit.muted(this,d,11); tv.setGravity(Gravity.CENTER);
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

        for(int cell=0;cell<42;cell++) {
            LinearLayout box=UiKit.column(this);
            box.setGravity(Gravity.CENTER);
            box.setPadding(UiKit.dp(this,1),UiKit.dp(this,4),UiKit.dp(this,1),UiKit.dp(this,4));
            GridLayout.LayoutParams gp=new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED,1f),GridLayout.spec(GridLayout.UNDEFINED,1f));
            gp.width=0; gp.height=UiKit.dp(this,78); box.setLayoutParams(gp);
            int day=cell-offset+1;
            if(day>=1&&day<=total) {
                Calendar c=(Calendar)first.clone(); c.set(Calendar.DAY_OF_MONTH,day);
                String key=dateFormat.format(c.getTime());
                TextView dt=UiKit.text(this,String.valueOf(day),11,key.equals(today));
                dt.setGravity(Gravity.CENTER);
                if(key.equals(selectedCalendarDate)) dt.setTextColor(UiKit.GOLD);
                box.addView(dt);

                List<DatabaseHelper.WearRow> rows=records.get(key);
                if(rows==null||rows.isEmpty()) {
                    ImageView empty=watchThumb("",42);
                    box.addView(empty,new LinearLayout.LayoutParams(UiKit.dp(this,42),UiKit.dp(this,42)));
                } else {
                    LinearLayout photos=UiKit.row(this); photos.setGravity(Gravity.CENTER);
                    int size=rows.size()>=2?22:42;
                    for(int i=0;i<Math.min(2,rows.size());i++) {
                        DatabaseHelper.WearRow wr=rows.get(i);
                        ImageView im=watchThumb(wr.imageUrl,42);
                        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(UiKit.dp(this,size),UiKit.dp(this,42));
                        if(i>0) pp.setMargins(UiKit.dp(this,1),0,0,0);
                        photos.addView(im,pp);
                        im.setOnClickListener(v->navigateToWatch(wr.watchId));
                    }
                    box.addView(photos);
                }
                box.setOnClickListener(v->showWearDialog(key));
            }
            grid.addView(box);
        }
        return grid;
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
        p.addView(UiKit.spacer(this,12));

        HorizontalScrollView filterScroll=new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filters=UiKit.row(this);
        int[] widths={0,18,19,20,21,22,-1};
        for(int w:widths) {
            String label=w==0?"전체":(w<0?"기타":w+" mm");
            Button b=UiKit.chip(this,label,filterWidth==w);
            b.setOnClickListener(v->showStraps(w));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.setMargins(0,0,UiKit.dp(this,6),0);
            filters.addView(b,bp);
        }
        filterScroll.addView(filters);
        p.addView(filterScroll);
        p.addView(UiKit.spacer(this,14));

        if(filterWidth>0) {
            p.addView(compatibleWatchesPanel(filterWidth));
            p.addView(UiKit.spacer(this,2));
        } else if(filterWidth<0) {
            p.addView(emptyCard("기타 폭 스트랩","18 / 19 / 20 / 21 / 22 mm 외 규격의 스트랩을 모아두는 영역입니다. 폭이 특정되지 않으므로 자동 호환 시계는 표시하지 않습니다."));
        }

        List<Strap> straps=db.getStraps(filterWidth);
        if(straps.isEmpty()) {
            p.addView(emptyCard("등록된 스트랩이 없습니다","제작사, 이름, 폭, 색상, 재질과 사진을 함께 저장할 수 있습니다."));
        } else {
            for(Strap s:straps) p.addView(strapCard(s));
        }
        setScrollable(p);
    }

    private View compatibleWatchesPanel(int widthMm) {
        LinearLayout card=UiKit.card(this);
        card.addView(UiKit.eyebrow(this,widthMm+" MM COMPATIBILITY"));
        card.addView(UiKit.text(this,widthMm+" mm 러그 폭 시계",17,true));
        card.addView(UiKit.muted(this,"사진이나 이름을 누르면 해당 시계의 컬렉션 카드로 이동합니다.",11));
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
            item.setOnClickListener(v->navigateToWatch(w.id));
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
        String desc=String.join(" · ",nonEmpty(s.color,s.material,s.buckle));
        if(!desc.isEmpty()) info.addView(UiKit.muted(this,desc,12));
        List<Watch> compatible=s.widthMm>0?db.getCompatibleWatches(s.widthMm):new ArrayList<>();
        info.addView(UiKit.spacer(this,7));
        if(s.widthMm<0) info.addView(UiKit.muted(this,"기타 폭 · 자동 호환성 미지정",11));
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
        String[] ws={"18 mm","19 mm","20 mm","21 mm","22 mm","기타"};
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
        EditText material=addField(f,"재질","Leather / Rubber / NATO / Mesh",base.material);
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
            base.material=val(material); base.buckle=val(buckle); base.notes=val(notes);
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
            if(pendingWatchPhotoStatus!=null) pendingWatchPhotoStatus.setText("사진을 가져오고 캘린더 배경색으로 자동 통일하는 중…");
            Uri selected=pendingWatchImageUri;
            executor.execute(()->{
                try {
                    ImageLoader.WatchImageImportResult result=ImageLoader.importWatchUriNormalized(this,selected);
                    runOnUiThread(()->{
                        pendingWatchImageProcessing=false;
                        pendingWatchOriginalPath=result.originalPath==null?"":result.originalPath;
                        pendingWatchProcessedPath=result.displayPath==null?"":result.displayPath;
                        if(pendingWatchPreview!=null) ImageLoader.load(this,pendingWatchPreview,pendingWatchProcessedPath);
                        if(pendingWatchPhotoStatus!=null) {
                            pendingWatchPhotoStatus.setText(result.backgroundChanged
                                    ? "배경을 #FCFBF7로 통일했습니다. 원본도 별도 보관되어 '원본 복구'가 가능합니다."
                                    : "복잡한 배경으로 판단되어 원본을 유지했습니다. 원본은 별도 보관됩니다.");
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

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_RECORD_AUDIO) {
            boolean granted=grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED;
            if(granted&&pendingStartTimegrapher) {
                pendingStartTimegrapher=false;
                startTimegrapherMeasurement();
            } else if(!granted) {
                pendingStartTimegrapher=false;
                Toast.makeText(this,"오차 측정을 위해 마이크 권한이 필요합니다.",Toast.LENGTH_LONG).show();
            }
        }
    }

    private String strapWidthLabel(int widthMm) {
        return widthMm<0?"기타":widthMm+" mm";
    }

    // HELPERS ----------------------------------------------------------------

    private EditText addField(LinearLayout parent,String label,String hint,String value) {
        parent.addView(UiKit.label(this,label));
        EditText e=UiKit.field(this,hint);
        e.setText(value==null?"":value);
        parent.addView(e);
        return e;
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
