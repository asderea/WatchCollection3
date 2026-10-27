package com.watchcollection.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 컬렉션(원목 시계 보관함) 사진 전처리.
 *
 * 핵심 원칙
 * 1) 컬렉션 사진에서는 배경 제거를 하지 않는다. 원목 프레임이 배경으로 오인되어
 *    지워지는 문제를 막기 위해 원본 픽셀을 그대로 유지한다.
 * 2) 사진 가장자리에서 원목 재질을 추정한 뒤 보관함의 바깥 테두리 4개 선을 찾는다.
 * 3) 찾은 4개 선의 교점을 직사각형 네 모서리에 직접 매핑하므로 최종 결과는
 *    평행사변형/사다리꼴이 아니라 항상 수평·수직이 맞는 직사각형이다.
 * 4) 검출 신뢰도가 낮으면 과도한 보정을 하지 않고 원본 전체를 직사각형 캔버스에
 *    안전하게 맞춰 보관함 일부가 사라지는 것을 우선 방지한다.
 */
public final class CollectionPhotoProcessor {
    private static final int MAX_INPUT_SIDE = 1800;
    private static final int OUTPUT_W = 1440;
    private static final int OUTPUT_H = 900;

    private CollectionPhotoProcessor() {}

    private static class WoodProfile {
        int r, g, b;
        int fillColor;
        boolean reliable;
    }

    /** y = a*x + b */
    private static class HLine {
        double a, b;
        int points;
        boolean valid;
        double y(double x) { return a * x + b; }
    }

    /** x = a*y + b */
    private static class VLine {
        double a, b;
        int points;
        boolean valid;
        double x(double y) { return a * y + b; }
    }

    private static class PointF2 {
        double x, y;
        PointF2(double x, double y) { this.x=x; this.y=y; }
    }

    public static String process(Context context, String originalPath) throws Exception {
        if (originalPath == null || originalPath.trim().isEmpty()) return "";
        Bitmap src = decodeScaled(originalPath, MAX_INPUT_SIDE);
        if (src == null) return originalPath;

        // 원목 프레임 자체를 보존해야 하므로 BackgroundNormalizer는 사용하지 않습니다.
        WoodProfile wood = estimateWoodProfile(src);

        // 먼저 구조적인 수평/수직선으로 작은 카메라 회전만 바로잡습니다.
        double skew = estimateStructuralSkew(src);
        Bitmap aligned = rotatePreservingAll(src, -skew, wood.fillColor);
        if (aligned != src) src.recycle();

        // 원목 재질을 이용해 "보관함 바깥 테두리"를 직접 찾습니다.
        float[] quad = detectOuterWoodQuad(aligned, wood);
        Bitmap output;
        if (quad != null && validateQuad(quad, aligned.getWidth(), aligned.getHeight())) {
            // 보관함 네 모서리를 출력 직사각형 네 모서리에 직접 대응시킵니다.
            // 이 단계에서 최종 결과의 수평/수직이 강제로 고정됩니다.
            output = rectifyToRectangle(aligned, quad, OUTPUT_W, OUTPUT_H, wood.fillColor);
        } else {
            // 잘못된 사다리꼴 변환으로 원목 일부가 지워지는 것보다 전체 보존을 우선합니다.
            output = fitWholeImage(aligned, OUTPUT_W, OUTPUT_H, wood.fillColor);
        }

        if (aligned != output) aligned.recycle();
        return save(context, output);
    }

    private static String save(Context context, Bitmap bitmap) throws Exception {
        File dir = new File(context.getFilesDir(), "collection_display");
        dir.mkdirs();
        File out = new File(dir, "collection_" + System.currentTimeMillis() + ".png");
        BackgroundNormalizer.savePng(bitmap, out);
        bitmap.recycle();
        return out.getAbsolutePath();
    }

    // ---------------------------------------------------------------------
    // 1. 원목 재질 추정
    // ---------------------------------------------------------------------

    private static WoodProfile estimateWoodProfile(Bitmap src) {
        int w=src.getWidth(), h=src.getHeight();
        int step=Math.max(1,Math.min(w,h)/500);
        int band=Math.max(8,(int)(Math.min(w,h)*0.16));
        List<Integer> rs=new ArrayList<>(), gs=new ArrayList<>(), bs=new ArrayList<>();
        List<Integer> fallbackR=new ArrayList<>(), fallbackG=new ArrayList<>(), fallbackB=new ArrayList<>();

        for(int y=0;y<h;y+=step) {
            for(int x=0;x<w;x+=step) {
                if(!(x<band || x>=w-band || y<band || y>=h-band)) continue;
                int c=src.getPixel(x,y);
                int r=Color.red(c), g=Color.green(c), b=Color.blue(c);
                if(Color.alpha(c)<80 || Math.max(r,Math.max(g,b))<35) continue;
                fallbackR.add(r); fallbackG.add(g); fallbackB.add(b);
                if(isWarmWoodCandidate(r,g,b)) {
                    rs.add(r); gs.add(g); bs.add(b);
                }
            }
        }

        WoodProfile p=new WoodProfile();
        List<Integer> useR=rs.size()>=80?rs:fallbackR;
        List<Integer> useG=rs.size()>=80?gs:fallbackG;
        List<Integer> useB=rs.size()>=80?bs:fallbackB;
        if(useR.isEmpty()) {
            p.r=156; p.g=111; p.b=57; p.fillColor=Color.rgb(p.r,p.g,p.b); p.reliable=false;
            return p;
        }
        p.r=median(useR); p.g=median(useG); p.b=median(useB);
        p.fillColor=Color.rgb(p.r,p.g,p.b);
        p.reliable=rs.size()>=80;
        return p;
    }

    private static boolean isWarmWoodCandidate(int r,int g,int b) {
        int max=Math.max(r,Math.max(g,b));
        int min=Math.min(r,Math.min(g,b));
        if(max<55 || max-min<18) return false;
        // 일반적인 밝은/중간 갈색·호박색·원목 계열을 넓게 허용
        return r>=g*0.96 && g>=b*1.03 && r>b*1.10;
    }

    private static boolean isWoodLike(int color, WoodProfile p) {
        int r=Color.red(color), g=Color.green(color), b=Color.blue(color);
        if(Color.alpha(color)<80) return false;

        // RGB 절대차 + 색 비율을 함께 사용해 밝고 어두운 목재 무늬를 모두 허용합니다.
        double dr=r-p.r, dg=g-p.g, db=b-p.b;
        double dist=Math.sqrt(dr*dr+dg*dg+db*db);
        double lum=(r+g+b)/3.0;
        double pl=(p.r+p.g+p.b)/3.0;
        double chroma=Math.abs((r-g)-(p.r-p.g))+Math.abs((g-b)-(p.g-p.b));
        boolean warm=isWarmWoodCandidate(r,g,b);
        return warm && (dist<105 || (Math.abs(lum-pl)<95 && chroma<72));
    }

    // ---------------------------------------------------------------------
    // 2. 구조 회전 보정
    // ---------------------------------------------------------------------

    private static double estimateStructuralSkew(Bitmap src) {
        int w=src.getWidth(), h=src.getHeight();
        if(w<10||h<10) return 0;
        double[] hist=new double[49]; // -12.0 ~ +12.0, 0.5deg
        int step=Math.max(2,Math.min(w,h)/420);

        for(int y=2;y<h-2;y+=step) {
            for(int x=2;x<w-2;x+=step) {
                int l=luma(src.getPixel(x-2,y)), r=luma(src.getPixel(x+2,y));
                int u=luma(src.getPixel(x,y-2)), d=luma(src.getPixel(x,y+2));
                double gx=r-l, gy=d-u;
                double mag=Math.hypot(gx,gy);
                if(mag<34) continue;

                // gradient의 수직방향이 edge tangent이므로 +90도
                double tangent=Math.toDegrees(Math.atan2(gy,gx))+90.0;
                while(tangent>90) tangent-=180;
                while(tangent<=-90) tangent+=180;
                double dev;
                if(Math.abs(tangent)<=45) dev=tangent;
                else dev=tangent>0?tangent-90:tangent+90;
                if(Math.abs(dev)>12) continue;
                int idx=(int)Math.round((dev+12.0)/0.5);
                if(idx>=0&&idx<hist.length) hist[idx]+=mag;
            }
        }
        int best=24;
        for(int i=0;i<hist.length;i++) if(hist[i]>hist[best]) best=i;
        double deg=-12.0+best*0.5;
        return Math.abs(deg)<0.5?0:deg;
    }

    private static Bitmap rotatePreservingAll(Bitmap src,double degrees,int fillColor) {
        if(Math.abs(degrees)<0.01) return src;
        Matrix m=new Matrix();
        m.postRotate((float)degrees);
        Bitmap rotated=Bitmap.createBitmap(src,0,0,src.getWidth(),src.getHeight(),m,true);
        Bitmap out=Bitmap.createBitmap(rotated.getWidth(),rotated.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        c.drawColor(fillColor);
        c.drawBitmap(rotated,0,0,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
        rotated.recycle();
        return out;
    }

    // ---------------------------------------------------------------------
    // 3. 원목 외곽 4개 선 검출
    // ---------------------------------------------------------------------

    private static float[] detectOuterWoodQuad(Bitmap src,WoodProfile wood) {
        int w=src.getWidth(), h=src.getHeight();
        int scanY=Math.max(20,(int)(h*0.28));
        int scanX=Math.max(20,(int)(w*0.22));
        int run=Math.max(3,Math.min(w,h)/260);
        int step=Math.max(2,Math.min(w,h)/500);

        List<PointF2> topPts=new ArrayList<>(), bottomPts=new ArrayList<>();
        for(int x=0;x<w;x+=step) {
            int top=findWoodRunY(src,x,0,scanY,1,run,wood);
            if(top>=0) topPts.add(new PointF2(x,top));
            int bottom=findWoodRunY(src,x,h-1,h-1-scanY,-1,run,wood);
            if(bottom>=0) bottomPts.add(new PointF2(x,bottom));
        }

        List<PointF2> leftPts=new ArrayList<>(), rightPts=new ArrayList<>();
        for(int y=0;y<h;y+=step) {
            int left=findWoodRunX(src,y,0,scanX,1,run,wood);
            if(left>=0) leftPts.add(new PointF2(left,y));
            int right=findWoodRunX(src,y,w-1,w-1-scanX,-1,run,wood);
            if(right>=0) rightPts.add(new PointF2(right,y));
        }

        HLine top=fitHLineRobust(topPts,w,h);
        HLine bottom=fitHLineRobust(bottomPts,w,h);
        VLine left=fitVLineRobust(leftPts,w,h);
        VLine right=fitVLineRobust(rightPts,w,h);

        if(!top.valid||!bottom.valid||!left.valid||!right.valid) return null;
        int minH=Math.max(18,w/(step*8));
        int minV=Math.max(18,h/(step*8));
        if(top.points<minH||bottom.points<minH||left.points<minV||right.points<minV) return null;

        PointF2 tl=intersect(top,left);
        PointF2 tr=intersect(top,right);
        PointF2 br=intersect(bottom,right);
        PointF2 bl=intersect(bottom,left);
        if(tl==null||tr==null||br==null||bl==null) return null;

        // 검출된 외곽선이 프레임 안쪽으로 살짝 들어온 경우를 대비해 0.6% 정도만 확장합니다.
        // 큰 확장은 오히려 보관함 일부를 잘라낼 수 있으므로 매우 보수적으로 적용합니다.
        double cx=(tl.x+tr.x+br.x+bl.x)/4.0;
        double cy=(tl.y+tr.y+br.y+bl.y)/4.0;
        double expand=1.012;
        tl=expandFrom(tl,cx,cy,expand,w,h);
        tr=expandFrom(tr,cx,cy,expand,w,h);
        br=expandFrom(br,cx,cy,expand,w,h);
        bl=expandFrom(bl,cx,cy,expand,w,h);

        return new float[]{(float)tl.x,(float)tl.y,(float)tr.x,(float)tr.y,
                (float)br.x,(float)br.y,(float)bl.x,(float)bl.y};
    }

    private static int findWoodRunY(Bitmap src,int x,int start,int end,int dir,int run,WoodProfile wood) {
        int h=src.getHeight();
        start=Math.max(0,Math.min(h-1,start)); end=Math.max(0,Math.min(h-1,end));
        for(int y=start; dir>0?y<=end:y>=end; y+=dir) {
            boolean ok=true;
            for(int k=0;k<run;k++) {
                int yy=y+dir*k;
                if(yy<0||yy>=h||!isWoodLike(src.getPixel(x,yy),wood)) {ok=false;break;}
            }
            if(ok) return y;
        }
        return -1;
    }

    private static int findWoodRunX(Bitmap src,int y,int start,int end,int dir,int run,WoodProfile wood) {
        int w=src.getWidth();
        start=Math.max(0,Math.min(w-1,start)); end=Math.max(0,Math.min(w-1,end));
        for(int x=start; dir>0?x<=end:x>=end; x+=dir) {
            boolean ok=true;
            for(int k=0;k<run;k++) {
                int xx=x+dir*k;
                if(xx<0||xx>=w||!isWoodLike(src.getPixel(xx,y),wood)) {ok=false;break;}
            }
            if(ok) return x;
        }
        return -1;
    }

    private static HLine fitHLineRobust(List<PointF2> pts,int w,int h) {
        HLine first=fitH(pts);
        if(!first.valid) return first;
        List<PointF2> kept=new ArrayList<>();
        double tol=Math.max(4,h*0.018);
        for(PointF2 p:pts) if(Math.abs(p.y-first.y(p.x))<=tol) kept.add(p);
        return fitH(kept);
    }

    private static VLine fitVLineRobust(List<PointF2> pts,int w,int h) {
        VLine first=fitV(pts);
        if(!first.valid) return first;
        List<PointF2> kept=new ArrayList<>();
        double tol=Math.max(4,w*0.018);
        for(PointF2 p:pts) if(Math.abs(p.x-first.x(p.y))<=tol) kept.add(p);
        return fitV(kept);
    }

    private static HLine fitH(List<PointF2> pts) {
        HLine line=new HLine(); line.points=pts.size();
        if(pts.size()<8) return line;
        double sx=0,sy=0,sxx=0,sxy=0;
        for(PointF2 p:pts){sx+=p.x;sy+=p.y;sxx+=p.x*p.x;sxy+=p.x*p.y;}
        double n=pts.size(), den=n*sxx-sx*sx;
        if(Math.abs(den)<1e-9) return line;
        line.a=(n*sxy-sx*sy)/den;
        line.b=(sy-line.a*sx)/n;
        line.valid=Math.abs(line.a)<0.35;
        return line;
    }

    private static VLine fitV(List<PointF2> pts) {
        VLine line=new VLine(); line.points=pts.size();
        if(pts.size()<8) return line;
        double sy=0,sx=0,syy=0,syx=0;
        for(PointF2 p:pts){sy+=p.y;sx+=p.x;syy+=p.y*p.y;syx+=p.y*p.x;}
        double n=pts.size(), den=n*syy-sy*sy;
        if(Math.abs(den)<1e-9) return line;
        line.a=(n*syx-sy*sx)/den;
        line.b=(sx-line.a*sy)/n;
        line.valid=Math.abs(line.a)<0.35;
        return line;
    }

    private static PointF2 intersect(HLine h,VLine v) {
        // y = ha*x+hb, x = va*y+vb
        double den=1.0-h.a*v.a;
        if(Math.abs(den)<1e-6) return null;
        double y=(h.a*v.b+h.b)/den;
        double x=v.a*y+v.b;
        return new PointF2(x,y);
    }

    private static PointF2 expandFrom(PointF2 p,double cx,double cy,double factor,int w,int h) {
        double x=cx+(p.x-cx)*factor;
        double y=cy+(p.y-cy)*factor;
        x=Math.max(0,Math.min(w-1,x)); y=Math.max(0,Math.min(h-1,y));
        return new PointF2(x,y);
    }

    private static boolean validateQuad(float[] q,int w,int h) {
        if(q==null||q.length!=8) return false;
        double area=polygonArea(q);
        if(area<w*(double)h*0.55) return false;
        double top=dist(q[0],q[1],q[2],q[3]);
        double right=dist(q[2],q[3],q[4],q[5]);
        double bottom=dist(q[4],q[5],q[6],q[7]);
        double left=dist(q[6],q[7],q[0],q[1]);
        if(Math.min(top,bottom)<w*0.58 || Math.min(left,right)<h*0.48) return false;
        if(top<=0||bottom<=0||left<=0||right<=0) return false;
        return true;
    }

    // ---------------------------------------------------------------------
    // 4. 강제 직사각형 출력 / 전체 보존 fallback
    // ---------------------------------------------------------------------

    private static Bitmap rectifyToRectangle(Bitmap src,float[] quad,int outW,int outH,int fillColor) {
        float[] dst={0,0,outW-1,0,outW-1,outH-1,0,outH-1};
        Matrix m=new Matrix();
        if(!m.setPolyToPoly(quad,0,dst,0,4)) return fitWholeImage(src,outW,outH,fillColor);
        Bitmap out=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(out);
        canvas.drawColor(fillColor);
        canvas.drawBitmap(src,m,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    private static Bitmap fitWholeImage(Bitmap src,int outW,int outH,int fillColor) {
        Bitmap out=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(out);
        canvas.drawColor(fillColor);

        // 전체 보관함이 한 픽셀도 임의 삭제되지 않도록 FIT_CENTER 방식으로 전체 이미지를 넣습니다.
        // 원본과 출력 비율 차이는 가장자리의 얇은 원목색 여백으로만 처리합니다.
        float scale=Math.min(outW/(float)src.getWidth(),outH/(float)src.getHeight());
        int dw=Math.max(1,Math.round(src.getWidth()*scale));
        int dh=Math.max(1,Math.round(src.getHeight()*scale));
        float dx=(outW-dw)/2f,dy=(outH-dh)/2f;
        Matrix m=new Matrix();
        m.setScale(scale,scale);
        m.postTranslate(dx,dy);
        canvas.drawBitmap(src,m,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    private static int median(List<Integer> values) {
        int[] a=new int[values.size()];
        for(int i=0;i<a.length;i++) a[i]=values.get(i);
        Arrays.sort(a);
        return a[a.length/2];
    }

    private static int luma(int c) {
        return (int)(Color.red(c)*0.299+Color.green(c)*0.587+Color.blue(c)*0.114);
    }

    private static double polygonArea(float[] q) {
        double sum=0;
        for(int i=0;i<4;i++) {
            int j=(i+1)%4;
            sum+=q[i*2]*q[j*2+1]-q[j*2]*q[i*2+1];
        }
        return Math.abs(sum)*0.5;
    }

    private static double dist(float x1,float y1,float x2,float y2) {
        return Math.hypot(x2-x1,y2-y1);
    }

    private static Bitmap decodeScaled(String path,int maxSide) {
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(path,bounds);
        int sample=1;
        while(Math.max(bounds.outWidth/sample,bounds.outHeight/sample)>maxSide) sample*=2;
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inSampleSize=Math.max(1,sample);
        opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(path,opts);
    }
}
