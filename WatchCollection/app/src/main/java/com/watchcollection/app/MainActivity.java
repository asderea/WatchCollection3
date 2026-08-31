package com.watchcollection.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private long focusedWatchId = -1;
    private Uri pendingStrapImageUri;
    private ImageView pendingStrapPreview;
    private Uri pendingWatchImageUri;
    private ImageView pendingWatchPreview;
    private boolean pendingWatchImageClear = false;
    private static final int REQ_STRAP_IMAGE = 2201;
    private static final int REQ_WATCH_IMAGE = 2202;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DatabaseHelper(this);
        visibleMonth.set(Calendar.DAY_OF_MONTH, 1);
        buildShell();
        showCollection();
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
            LinearLayout filterRow=UiKit.row(this);
            String[] labels={"전체","기계식","쿼츠"};
            for(int i=0;i<labels.length;i++) {
                final int idx=i;
                Button b=UiKit.chip(this,labels[i],collectionFilter==i);
                b.setOnClickListener(v->{collectionFilter=idx;showCollection();});
                filterRow.addView(b,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            }
            p.addView(filterRow);
            p.addView(UiKit.spacer(this,12));
        }

        if(allWatches.isEmpty()) {
            p.addView(emptyCard("첫 시계를 등록해보세요","모델명과 레퍼런스를 입력하면 공식 페이지와 시계 전문 출처를 교차검증해 스펙만 정리합니다. 대표 사진은 직접 선택할 수 있습니다."));
        } else {
            List<Watch> ordered = new ArrayList<>(allWatches);
            if (focusedWatchId > 0) {
                Watch target = null;
                for (Watch w : ordered) if (w.id == focusedWatchId) { target=w; break; }
                if (target != null) { ordered.remove(target); ordered.add(0,target); }
            }
            int shown=0;
            for(Watch w:ordered) {
                if(matchesCollectionFilter(w)) {
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
        card.addView(UiKit.text(this,monthFormat.format(new Date())+" 착용 현황",17,true));
        card.addView(UiKit.spacer(this,8));
        if(counts.isEmpty()) {
            card.addView(UiKit.muted(this,"이번 달 착용 기록이 아직 없습니다.",12));
            return card;
        }
        for(Map.Entry<Long,Double> e:counts.entrySet()) {
            Watch w=db.getWatch(e.getKey());
            if(w==null) continue;
            LinearLayout row=UiKit.row(this);
            TextView name=UiKit.text(this,(w.brand.isEmpty()?"":w.brand+" · ")+w.modelName,12,true);
            row.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            TextView c=UiKit.text(this,formatWearScore(e.getValue()),12,true);
            c.setTextColor(UiKit.GOLD);
            row.addView(c);
            row.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,4));
            row.setOnClickListener(v->navigateToWatch(w.id));
            card.addView(row);
        }
        return card;
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
        image.setBackground(UiKit.rounded(UiKit.IMAGE_BG,Color.TRANSPARENT,15,this));
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
        LinearLayout form=UiKit.column(this);
        form.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),UiKit.dp(this,8));

        ImageView preview=new ImageView(this);
        preview.setBackground(UiKit.rounded(UiKit.IMAGE_BG,Color.TRANSPARENT,16,this));
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setClipToOutline(true);
        preview.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        pendingWatchPreview=preview;
        ImageLoader.load(this,preview,base.representativeImageUrl);
        form.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,210)));

        LinearLayout photoActions=UiKit.row(this);
        Button pickWatchPhoto=UiKit.secondaryButton(this,"대표 사진 선택");
        Button clearWatchPhoto=UiKit.secondaryButton(this,"사진 제거");
        pickWatchPhoto.setOnClickListener(v->openWatchImagePicker());
        clearWatchPhoto.setOnClickListener(v->{
            pendingWatchImageUri=null;
            pendingWatchImageClear=true;
            preview.setImageDrawable(null);
            preview.setBackground(UiKit.rounded(UiKit.IMAGE_BG,Color.TRANSPARENT,16,this));
        });
        photoActions.addView(pickWatchPhoto,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        photoActions.addView(clearWatchPhoto,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        form.addView(photoActions);

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
            if(pendingWatchImageClear) {
                base.representativeImageUrl="";
                base.imageSourceUrl="";
            } else if(pendingWatchImageUri!=null) {
                try {
                    base.representativeImageUrl=ImageLoader.importWatchUri(this,pendingWatchImageUri);
                    base.imageSourceUrl="manual";
                } catch(Exception e) {
                    Toast.makeText(this,"대표 사진 저장 실패: "+e.getMessage(),Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            long savedId=db.saveWatch(base);
            pendingWatchPreview=null;
            dlg.dismiss();
            focusedWatchId=savedId;
            showCollection();
        }));
        dlg.show();
    }

    // ACCURACY ---------------------------------------------------------------

    private void showAccuracy() {
        selectNav(navAccuracy);
        LinearLayout p=page();
        p.addView(UiKit.eyebrow(this,"TIMEKEEPING"));
        p.addView(UiKit.text(this,"오차 기록",27,true));
        p.addView(UiKit.spacer(this,4));
        p.addView(UiKit.muted(this,"측정 오차와 경과 시간을 입력하면 초/일(s/day)로 자동 환산합니다.",13));
        p.addView(UiKit.spacer(this,14));
        List<Watch>watches=db.getWatches();
        if(watches.isEmpty()) {
            p.addView(emptyCard("먼저 시계를 등록하세요","오차 기록은 컬렉션에 저장된 시계와 연결됩니다."));
            setScrollable(p);
            return;
        }

        LinearLayout c=UiKit.card(this);
        Spinner spinner=watchSpinner(watches);
        c.addView(UiKit.label(this,"시계")); c.addView(spinner);
        EditText date=UiKit.field(this,"날짜"); date.setText(dateFormat.format(new Date())); date.setFocusable(false);
        date.setOnClickListener(v->pickDate(val(date),date::setText));
        c.addView(UiKit.label(this,"측정 날짜")); c.addView(date);
        EditText dev=UiKit.field(this,"예: +12 또는 -8");
        dev.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        c.addView(UiKit.label(this,"측정 오차 (초)")); c.addView(dev);
        EditText hours=UiKit.field(this,"예: 72"); hours.setText("24");
        hours.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        c.addView(UiKit.label(this,"측정 간격 (시간)")); c.addView(hours);
        EditText note=UiKit.field(this,"착용/보관 상태 등");
        c.addView(UiKit.label(this,"메모")); c.addView(note);
        Button save=UiKit.primaryButton(this,"오차 기록 저장"); c.addView(save); p.addView(c);

        save.setOnClickListener(v->{
            try {
                Watch w=watches.get(spinner.getSelectedItemPosition());
                double d=Double.parseDouble(val(dev)); double h=Double.parseDouble(val(hours));
                db.addAccuracy(w.id,val(date),d,h,val(note));
                Toast.makeText(this,"오차 기록을 저장했습니다.",Toast.LENGTH_SHORT).show();
                showAccuracy();
            } catch(Exception e) {
                Toast.makeText(this,"오차와 측정 시간을 숫자로 입력하세요.",Toast.LENGTH_SHORT).show();
            }
        });

        DatabaseHelper.AccuracyRow latest=null;
        List<DatabaseHelper.AccuracyRow> rows=db.getAccuracyRows(80);
        if(!rows.isEmpty()) latest=rows.get(0);
        if(latest!=null) {
            List<DatabaseHelper.AccuracyRow> chartRows=db.getAccuracyRowsForWatch(latest.watchId,40);
            LinearLayout chartCard=UiKit.card(this);
            chartCard.addView(UiKit.text(this,latest.watchName+" · 오차 추이",17,true));
            List<Double> vals=new ArrayList<>();
            for(DatabaseHelper.AccuracyRow r:chartRows) vals.add(r.secPerDay);
            AccuracyChartView chart=new AccuracyChartView(this); chart.setValues(vals);
            chartCard.addView(chart,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,170)));
            p.addView(chartCard);
        }

        p.addView(UiKit.text(this,"최근 기록",18,true));
        p.addView(UiKit.spacer(this,7));
        for(DatabaseHelper.AccuracyRow r:rows) {
            LinearLayout rc=UiKit.card(this);
            rc.addView(UiKit.text(this,r.watchName,15,true));
            TextView x=UiKit.text(this,String.format(Locale.KOREA,"%s   %+.2f s/day",r.date,r.secPerDay),14,true);
            x.setTextColor(r.secPerDay>0?Color.rgb(154,87,64):UiKit.NAVY_2);
            rc.addView(x);
            if(!r.note.isEmpty()) rc.addView(UiKit.muted(this,r.note,12));
            p.addView(rc);
        }
        setScrollable(p);
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
            p.addView(emptyCard("먼저 시계를 등록하세요","등록된 대표사진이 캘린더 날짜마다 표시됩니다."));
            setScrollable(p);
            return;
        }

        p.addView(buildTopWearCarousel());
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

        List<DatabaseHelper.WearRow> dayRows=db.getWears(selectedCalendarDate);
        LinearLayout edit=UiKit.card(this);
        edit.addView(UiKit.eyebrow(this,"SELECTED DAY"));
        edit.addView(UiKit.text(this,selectedCalendarDate,19,true));
        if(!dayRows.isEmpty()) {
            edit.addView(UiKit.spacer(this,8));
            double each = dayRows.size() >= 2 ? 0.5 : 1.0;
            for(DatabaseHelper.WearRow wr:dayRows) edit.addView(selectedWearRow(wr,each));
        }
        edit.addView(UiKit.spacer(this,10));

        edit.addView(UiKit.label(this,"첫 번째 시계"));
        Spinner first=watchSpinnerWithNone(watches,"— 첫 번째 시계 선택 —");
        edit.addView(first);
        edit.addView(UiKit.spacer(this,8));
        edit.addView(UiKit.label(this,"두 번째 시계 (선택)"));
        Spinner second=watchSpinnerWithNone(watches,"— 두 번째 시계 없음 —");
        edit.addView(second);
        edit.addView(UiKit.muted(this,"하루에 시계 1개를 기록하면 1회, 2개를 기록하면 각 시계를 0.5회로 집계합니다.",11));
        edit.addView(UiKit.spacer(this,8));

        if(dayRows.size()>0) setWatchSpinnerSelection(first,watches,dayRows.get(0).watchId);
        if(dayRows.size()>1) setWatchSpinnerSelection(second,watches,dayRows.get(1).watchId);
        EditText memo=UiKit.field(this,"메모: 스트랩, 장소, 행사 등");
        if(!dayRows.isEmpty()) memo.setText(dayRows.get(0).note);
        edit.addView(memo);

        LinearLayout ar=UiKit.row(this);
        Button save=UiKit.primaryButton(this,"이 날짜에 저장");
        Button remove=UiKit.secondaryButton(this,"전체 삭제");
        ar.addView(save,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,2));
        ar.addView(remove,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        edit.addView(ar);
        p.addView(edit);

        save.setOnClickListener(v->{
            Watch w1=selectedWatch(first,watches);
            Watch w2=selectedWatch(second,watches);
            if(w1==null && w2==null) {
                Toast.makeText(this,"착용한 시계를 하나 이상 선택하세요.",Toast.LENGTH_SHORT).show();
                return;
            }
            if(w1!=null && w2!=null && w1.id==w2.id) {
                Toast.makeText(this,"같은 시계는 두 번 선택할 필요가 없습니다. 한 개만 선택하면 1회로 계산됩니다.",Toast.LENGTH_LONG).show();
                return;
            }
            db.clearWearDate(selectedCalendarDate);
            int slot=1;
            if(w1!=null) db.saveWear(w1.id,selectedCalendarDate,slot++,val(memo));
            if(w2!=null) db.saveWear(w2.id,selectedCalendarDate,slot,val(memo));
            showCalendar();
        });
        remove.setOnClickListener(v->{db.clearWearDate(selectedCalendarDate);showCalendar();});
        setScrollable(p);
    }

    private View buildTopWearCarousel() {
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row=UiKit.row(this);
        row.setPadding(0,0,UiKit.dp(this,12),0);
        List<DatabaseHelper.WearStat> year=db.getTopWorn(currentYearStart(),currentYearEnd(),3);
        List<DatabaseHelper.WearStat> month=db.getTopWorn(currentMonthStart(),currentMonthEnd(),3);
        row.addView(topWearCard("올해 TOP 3",year),fixedCardWidth());
        row.addView(topWearCard("이번 달 TOP 3",month),fixedCardWidth());
        scroll.addView(row);
        return scroll;
    }

    private LinearLayout.LayoutParams fixedCardWidth() {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(UiKit.dp(this,300),ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,UiKit.dp(this,12),0);
        return lp;
    }

    private View topWearCard(String title,List<DatabaseHelper.WearStat> stats) {
        LinearLayout card=UiKit.card(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(UiKit.dp(this,300),ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(UiKit.eyebrow(this,"MOST WORN"));
        card.addView(UiKit.text(this,title,17,true));
        card.addView(UiKit.spacer(this,8));
        if(stats.isEmpty()) {
            card.addView(UiKit.muted(this,"착용 기록이 없습니다.",12));
            return card;
        }
        int rank=1;
        for(DatabaseHelper.WearStat s:stats) {
            LinearLayout r=UiKit.row(this);
            TextView no=UiKit.text(this,String.valueOf(rank),15,true); no.setTextColor(UiKit.GOLD);
            r.addView(no,new LinearLayout.LayoutParams(UiKit.dp(this,22),ViewGroup.LayoutParams.WRAP_CONTENT));
            ImageView im=watchThumb(s.imageUrl,44);
            r.addView(im,new LinearLayout.LayoutParams(UiKit.dp(this,44),UiKit.dp(this,44)));
            LinearLayout info=UiKit.column(this); info.setPadding(UiKit.dp(this,9),0,0,0);
            TextView name=UiKit.text(this,s.watchName,12,true); name.setMaxLines(2);
            TextView score=UiKit.text(this,formatWearScore(s.score),11,true); score.setTextColor(UiKit.GOLD);
            info.addView(name); info.addView(score);
            r.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            r.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,4));
            r.setOnClickListener(v->navigateToWatch(s.watchId));
            card.addView(r);
            rank++;
        }
        return card;
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
        String[] days={"월","화","수","목","금","토","일"};
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
        int offset=(dow+5)%7;
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
                box.setOnClickListener(v->{selectedCalendarDate=key;showCalendar();});
            }
            grid.addView(box);
        }
        return grid;
    }

    private ImageView watchThumb(String imageUrl,int sizeDp) {
        ImageView im=new ImageView(this);
        im.setScaleType(ImageView.ScaleType.CENTER_CROP);
        im.setBackground(UiKit.rounded(UiKit.IMAGE_BG,Color.TRANSPARENT,10,this));
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

        LinearLayout filters=UiKit.row(this);
        int[] widths={0,18,19,20,21,22};
        for(int w:widths) {
            Button b=UiKit.chip(this,w==0?"전체":w+" mm",filterWidth==w);
            b.setOnClickListener(v->showStraps(w));
            filters.addView(b,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        }
        p.addView(filters);
        p.addView(UiKit.spacer(this,14));

        if(filterWidth>0) {
            p.addView(compatibleWatchesPanel(filterWidth));
            p.addView(UiKit.spacer(this,2));
        }

        List<Strap> straps=db.getStraps(filterWidth);
        if(straps.isEmpty()) {
            p.addView(emptyCard("등록된 스트랩이 없습니다","18–22 mm 스트랩을 사진, 색상, 재질과 함께 저장할 수 있습니다."));
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
        info.addView(UiKit.eyebrow(this,s.widthMm+" MM"));
        info.addView(UiKit.text(this,s.name,18,true));
        String desc=String.join(" · ",nonEmpty(s.color,s.material,s.buckle));
        if(!desc.isEmpty()) info.addView(UiKit.muted(this,desc,12));
        List<Watch> compatible=db.getCompatibleWatches(s.widthMm);
        info.addView(UiKit.spacer(this,7));
        if(compatible.isEmpty()) info.addView(UiKit.muted(this,"현재 호환 시계 없음",11));
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
        EditText name=addField(f,"이름 *","예: 네이비 카프 레더",base.name);
        f.addView(UiKit.label(this,"러그 폭"));
        Spinner width=new Spinner(this);
        String[] ws={"18 mm","19 mm","20 mm","21 mm","22 mm"};
        width.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ws));
        width.setSelection(Math.max(0,Math.min(4,base.widthMm-18)));
        f.addView(width);
        f.addView(UiKit.spacer(this,10));
        EditText color=addField(f,"색상","Navy / Brown / Black",base.color);
        EditText material=addField(f,"재질","Leather / Rubber / NATO / Mesh",base.material);
        EditText buckle=addField(f,"버클/특징","Deployant / Pin buckle",base.buckle);
        EditText notes=addField(f,"메모","브랜드, 구매처 등",base.notes);

        ScrollView sc=new ScrollView(this); sc.addView(f);
        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle(existing==null?"스트랩 추가":"스트랩 수정")
                .setView(sc)
                .setNegativeButton("취소",null)
                .setPositiveButton("저장",null)
                .create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(val(name).isEmpty()){name.setError("이름은 필수입니다.");return;}
            base.name=val(name); base.widthMm=18+width.getSelectedItemPosition(); base.color=val(color);
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
            if(pendingWatchPreview!=null) ImageLoader.load(this,pendingWatchPreview,pendingWatchImageUri.toString());
        }
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
