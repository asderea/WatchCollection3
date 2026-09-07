package com.watchcollection.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;

import java.io.File;
import java.util.Arrays;

/**
 * 시계 보관함 사진 전처리.
 * - 모서리 색을 기준으로 배경과 전경을 분리
 * - 전경의 4개 극점이 충분히 안정적이면 원근(사다리꼴) 보정
 * - 전경 PCA 주축으로 작은 회전 기울기를 자동 보정
 * - 배경을 Watch Collection의 사진 배경색으로 정리하고 중앙 배치
 */
public final class CollectionPhotoProcessor {
    private static final int TARGET_BG = UiKit.PHOTO_BG;
    private static final int MAX_INPUT_SIDE = 1500;
    private CollectionPhotoProcessor() {}

    private static class MaskInfo {
        boolean[] foreground;
        int width, height, count;
        int minX, minY, maxX, maxY;
        float[] quad; // tl, tr, br, bl
        double axisAngleDeg;
    }

    public static String process(Context context, String originalPath) throws Exception {
        if (originalPath == null || originalPath.trim().isEmpty()) return "";
        Bitmap src = decodeScaled(originalPath, MAX_INPUT_SIDE);
        if (src == null) return originalPath;

        // 먼저 기존 배경 정리기를 적용하고, 실패하더라도 원본 비트맵으로 후속 보정을 계속합니다.
        BackgroundNormalizer.Result bgResult = BackgroundNormalizer.normalize(new File(originalPath));
        Bitmap work = bgResult.bitmap != null ? bgResult.bitmap : src.copy(Bitmap.Config.ARGB_8888, true);
        if (bgResult.bitmap != null && src != work) src.recycle();

        MaskInfo info = analyzeForeground(work);
        if (info.count < work.getWidth() * work.getHeight() * 0.04) {
            Bitmap fitted = fitOnCanvas(work);
            if (fitted != work) work.recycle();
            return save(context, fitted);
        }

        // 먼저 보관함의 장축을 수평/수직에 맞춘 뒤, 회전 노이즈가 줄어든 상태에서
        // 사다리꼴 네 모서리를 직사각형으로 펴는 순서가 더 안정적입니다.
        Bitmap deskewed = deskew(work, info.axisAngleDeg);
        if (deskewed != work) work.recycle();
        work = deskewed;

        MaskInfo aligned = analyzeForeground(work);
        Bitmap perspective = perspectiveRectify(work, aligned);
        if (perspective != work) work.recycle();
        work = perspective;

        // 마지막 출력은 항상 고정 직사각형 캔버스에 배치합니다.
        Bitmap fitted = fitOnCanvas(work);
        if (fitted != work) work.recycle();
        return save(context, fitted);
    }

    private static String save(Context context, Bitmap bitmap) throws Exception {
        File dir = new File(context.getFilesDir(), "collection_display");
        dir.mkdirs();
        File out = new File(dir, "collection_" + System.currentTimeMillis() + ".png");
        BackgroundNormalizer.savePng(bitmap, out);
        bitmap.recycle();
        return out.getAbsolutePath();
    }

    private static MaskInfo analyzeForeground(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        int bg = estimateBackground(px, w, h);
        int tol = estimateTolerance(px, w, h, bg);

        boolean[] raw = new boolean[w * h];
        int rawCount=0;
        for (int i = 0; i < raw.length; i++) {
            int c = px[i];
            raw[i] = Color.alpha(c) > 80 && colorDistance(c, bg) > tol;
            if(raw[i]) rawCount++;
        }

        // 보관함 밖의 작은 물체/그림자를 네 모서리 추정에 쓰지 않도록
        // 가장 큰 연결 전경을 우선 사용합니다. 다만 보관함이 여러 구획으로 끊겨
        // 최대 component가 전체 전경의 절반도 안 되면 원래 mask를 유지합니다.
        boolean[] foreground = largestConnectedComponent(raw, w, h);
        int kept = 0;
        for (boolean v : foreground) if (v) kept++;
        if (kept < w * h * 0.025 || kept < rawCount * 0.55) foreground = raw;

        MaskInfo out = new MaskInfo();
        out.width = w; out.height = h;
        out.foreground = foreground;
        out.minX = w; out.minY = h; out.maxX = -1; out.maxY = -1;

        double sx = 0, sy = 0;
        float minSum = Float.MAX_VALUE, maxSum = -Float.MAX_VALUE;
        float minDiff = Float.MAX_VALUE, maxDiff = -Float.MAX_VALUE;
        float tlx=0,tly=0,trx=0,try_=0,brx=0,bry=0,blx=0,bly=0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (!foreground[idx]) continue;
                out.count++;
                sx += x; sy += y;
                if (x < out.minX) out.minX = x;
                if (x > out.maxX) out.maxX = x;
                if (y < out.minY) out.minY = y;
                if (y > out.maxY) out.maxY = y;
                float sum = x + y;
                float diff = x - y;
                if (sum < minSum) { minSum=sum; tlx=x; tly=y; }
                if (sum > maxSum) { maxSum=sum; brx=x; bry=y; }
                if (diff > maxDiff) { maxDiff=diff; trx=x; try_=y; }
                if (diff < minDiff) { minDiff=diff; blx=x; bly=y; }
            }
        }
        out.quad = new float[]{tlx,tly,trx,try_,brx,bry,blx,bly};
        if (out.count > 8) {
            double mx = sx / out.count, my = sy / out.count;
            double cxx=0, cyy=0, cxy=0;
            // 내부 무늬보다 외곽 기울기에 더 민감하도록 경계 픽셀의 가중치를 높입니다.
            for (int y=1;y<h-1;y++) for(int x=1;x<w-1;x++) {
                int idx=y*w+x;
                if(!foreground[idx]) continue;
                boolean boundary=!foreground[idx-1]||!foreground[idx+1]||!foreground[idx-w]||!foreground[idx+w];
                double weight=boundary?3.0:1.0;
                double dx=x-mx, dy=y-my;
                cxx += dx*dx*weight; cyy += dy*dy*weight; cxy += dx*dy*weight;
            }
            out.axisAngleDeg = Math.toDegrees(0.5 * Math.atan2(2*cxy, cxx-cyy));
        }
        return out;
    }

    private static boolean[] largestConnectedComponent(boolean[] raw, int w, int h) {
        int n=w*h;
        boolean[] seen=new boolean[n];
        int[] queue=new int[n];
        int[] best=new int[n];
        int bestCount=0;
        for(int seed=0;seed<n;seed++) {
            if(!raw[seed]||seen[seed]) continue;
            int head=0,tail=0;
            queue[tail++]=seed;
            seen[seed]=true;
            while(head<tail) {
                int idx=queue[head++];
                int x=idx%w;
                int y=idx/w;
                if(x>0) tail=enqueueComponent(idx-1,raw,seen,queue,tail);
                if(x+1<w) tail=enqueueComponent(idx+1,raw,seen,queue,tail);
                if(y>0) tail=enqueueComponent(idx-w,raw,seen,queue,tail);
                if(y+1<h) tail=enqueueComponent(idx+w,raw,seen,queue,tail);
            }
            if(tail>bestCount) {
                bestCount=tail;
                System.arraycopy(queue,0,best,0,tail);
            }
        }
        boolean[] out=new boolean[n];
        for(int i=0;i<bestCount;i++) out[best[i]]=true;
        return out;
    }

    private static int enqueueComponent(int idx, boolean[] raw, boolean[] seen, int[] queue, int tail) {
        if(!seen[idx]&&raw[idx]) {
            seen[idx]=true;
            queue[tail++]=idx;
        }
        return tail;
    }

    private static Bitmap perspectiveRectify(Bitmap src, MaskInfo info) {
        if (info.count <= 0 || info.quad == null) return src;
        float[] q = info.quad;
        double area = polygonArea(q);
        double imageArea = src.getWidth() * (double) src.getHeight();
        if (area < imageArea * 0.10) return src;

        double top = dist(q[0],q[1],q[2],q[3]);
        double right = dist(q[2],q[3],q[4],q[5]);
        double bottom = dist(q[4],q[5],q[6],q[7]);
        double left = dist(q[6],q[7],q[0],q[1]);
        if (Math.min(Math.min(top,bottom), Math.min(left,right)) < Math.min(src.getWidth(),src.getHeight()) * 0.12) return src;

        // 사다리꼴/원근 왜곡이 거의 없고 회전만 있는 경우는 PCA 단계에서 처리합니다.
        double widthAsym = Math.abs(top-bottom) / Math.max(top,bottom);
        double heightAsym = Math.abs(left-right) / Math.max(left,right);
        if (widthAsym < 0.025 && heightAsym < 0.025) return src;

        int dw = (int)Math.round(Math.max(top,bottom));
        int dh = (int)Math.round(Math.max(left,right));
        int max = 1500;
        double scale = Math.min(1.0, max / (double)Math.max(dw,dh));
        dw = Math.max(200,(int)Math.round(dw*scale));
        dh = Math.max(160,(int)Math.round(dh*scale));

        float[] dst = {0,0, dw-1,0, dw-1,dh-1, 0,dh-1};
        Matrix m = new Matrix();
        if (!m.setPolyToPoly(q,0,dst,0,4)) return src;
        Bitmap out = Bitmap.createBitmap(dw,dh,Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(TARGET_BG);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src,m,p);
        return out;
    }

    private static Bitmap deskew(Bitmap src, double axisDeg) {
        // 주축을 0도 또는 90도 중 더 가까운 축으로 맞춥니다.
        double target = Math.abs(axisDeg) <= 45 ? 0 : (axisDeg > 0 ? 90 : -90);
        double error = axisDeg - target;
        if (Math.abs(error) < 0.6 || Math.abs(error) > 22.0) return src;

        Matrix rotate = new Matrix();
        rotate.postRotate((float)-error);
        Bitmap rotated = Bitmap.createBitmap(src,0,0,src.getWidth(),src.getHeight(),rotate,true);
        Bitmap withBg = Bitmap.createBitmap(rotated.getWidth(),rotated.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(withBg);
        c.drawColor(TARGET_BG);
        c.drawBitmap(rotated,0,0,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
        rotated.recycle();
        return withBg;
    }

    private static Bitmap fitOnCanvas(Bitmap src) {
        MaskInfo info = analyzeForeground(src);
        if (info.count <= 0 || info.maxX <= info.minX || info.maxY <= info.minY) return src;
        int padX = Math.max(10,(info.maxX-info.minX)/15);
        int padY = Math.max(10,(info.maxY-info.minY)/15);
        int left = Math.max(0,info.minX-padX), top=Math.max(0,info.minY-padY);
        int right=Math.min(src.getWidth()-1,info.maxX+padX), bottom=Math.min(src.getHeight()-1,info.maxY+padY);
        Rect srcRect = new Rect(left,top,right+1,bottom+1);

        int cw=1440,ch=900;
        Bitmap out=Bitmap.createBitmap(cw,ch,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(out);
        canvas.drawColor(TARGET_BG);
        float scale=Math.min((cw*0.94f)/srcRect.width(),(ch*0.92f)/srcRect.height());
        int dw=Math.max(1,Math.round(srcRect.width()*scale));
        int dh=Math.max(1,Math.round(srcRect.height()*scale));
        int dx=(cw-dw)/2,dy=(ch-dh)/2;
        Rect dst=new Rect(dx,dy,dx+dw,dy+dh);
        canvas.drawBitmap(src,srcRect,dst,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    private static int estimateBackground(int[] px,int w,int h) {
        int patch=Math.max(4,Math.min(w,h)/32);
        int count=patch*patch*4;
        int[] rs=new int[count],gs=new int[count],bs=new int[count];
        int k=0;
        int[][] corners={{0,0},{w-patch,0},{0,h-patch},{w-patch,h-patch}};
        for(int[] c:corners) for(int y=c[1];y<c[1]+patch;y++) for(int x=c[0];x<c[0]+patch;x++) {
            int p=px[y*w+x]; rs[k]=Color.red(p); gs[k]=Color.green(p); bs[k]=Color.blue(p); k++;
        }
        Arrays.sort(rs); Arrays.sort(gs); Arrays.sort(bs);
        return Color.rgb(rs[count/2],gs[count/2],bs[count/2]);
    }

    private static int estimateTolerance(int[] px,int w,int h,int bg) {
        int patch=Math.max(4,Math.min(w,h)/32);
        int[] ds=new int[patch*patch*4]; int k=0;
        int[][] corners={{0,0},{w-patch,0},{0,h-patch},{w-patch,h-patch}};
        for(int[] c:corners) for(int y=c[1];y<c[1]+patch;y++) for(int x=c[0];x<c[0]+patch;x++) ds[k++]=colorDistance(px[y*w+x],bg);
        Arrays.sort(ds);
        int p90=ds[Math.min(ds.length-1,(int)(ds.length*0.9))];
        return Math.max(34,Math.min(78,p90+26));
    }

    private static int colorDistance(int a,int b) {
        int dr=Color.red(a)-Color.red(b), dg=Color.green(a)-Color.green(b), db=Color.blue(a)-Color.blue(b);
        return (int)Math.sqrt(dr*dr+dg*dg+db*db);
    }

    private static double polygonArea(float[] q) {
        double sum=0;
        for(int i=0;i<4;i++) {
            int j=(i+1)%4;
            sum += q[i*2]*q[j*2+1]-q[j*2]*q[i*2+1];
        }
        return Math.abs(sum)*0.5;
    }

    private static double dist(float x1,float y1,float x2,float y2) {
        double dx=x2-x1,dy=y2-y1; return Math.sqrt(dx*dx+dy*dy);
    }

    private static Bitmap decodeScaled(String path,int maxSide) {
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true; BitmapFactory.decodeFile(path,bounds);
        int sample=1;
        while(Math.max(bounds.outWidth/sample,bounds.outHeight/sample)>maxSide) sample*=2;
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inSampleSize=Math.max(1,sample); opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(path,opts);
    }
}
