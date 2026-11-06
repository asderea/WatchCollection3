package com.watchcollection.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

/**
 * 제품 사진의 단색 배경을 모서리에서부터 flood-fill 방식으로 찾아
 * Watch Collection 캘린더용 사진 배경색(#FCFBF7)으로 바꿉니다.
 *
 * 시계 내부의 흰/검은 다이얼은 바꾸지 않고, 모서리와 연결된 배경만 교체합니다.
 * 배경이 복잡해 신뢰도가 낮으면 원본을 유지합니다.
 */
public final class BackgroundNormalizer {
    private static final int MAX_SIDE = 1400;
    private static final int TARGET = UiKit.PHOTO_BG;

    public static class Result {
        public final Bitmap bitmap;
        public final boolean changed;
        public final double backgroundRatio;

        Result(Bitmap bitmap, boolean changed, double backgroundRatio) {
            this.bitmap = bitmap;
            this.changed = changed;
            this.backgroundRatio = backgroundRatio;
        }
    }

    private BackgroundNormalizer() {}

    public static Result normalize(File input) {
        Bitmap src = decodeScaled(input.getAbsolutePath(), MAX_SIDE);
        if (src == null) return new Result(null, false, 0.0);

        Bitmap work = src.copy(Bitmap.Config.ARGB_8888, true);
        int w = work.getWidth();
        int h = work.getHeight();
        int n = w * h;
        int[] px = new int[n];
        work.getPixels(px, 0, w, 0, 0, w, h);

        int bg = estimateBackground(px, w, h);
        int tolerance = estimateTolerance(px, w, h, bg);
        boolean[] mask = new boolean[n];
        int[] queue = new int[n];
        int head = 0;
        int tail = 0;

        int patch = Math.max(4, Math.min(w, h) / 40);
        int[][] corners = {
                {0, 0}, {w - patch, 0}, {0, h - patch}, {w - patch, h - patch}
        };

        // 모서리 패치만 seed로 사용해 위/아래로 끝까지 닿는 어두운 스트랩을
        // 검은 배경으로 오인하는 위험을 낮춥니다.
        for (int[] c : corners) {
            for (int y = Math.max(0, c[1]); y < Math.min(h, c[1] + patch); y++) {
                for (int x = Math.max(0, c[0]); x < Math.min(w, c[0] + patch); x++) {
                    int idx = y * w + x;
                    if (!mask[idx] && isBackgroundLike(px[idx], bg, tolerance)) {
                        mask[idx] = true;
                        queue[tail++] = idx;
                    }
                }
            }
        }

        while (head < tail) {
            int idx = queue[head++];
            int x = idx % w;
            int y = idx / w;
            if (x > 0) tail = enqueueIfBackground(idx - 1, px, mask, queue, tail, bg, tolerance);
            if (x + 1 < w) tail = enqueueIfBackground(idx + 1, px, mask, queue, tail, bg, tolerance);
            if (y > 0) tail = enqueueIfBackground(idx - w, px, mask, queue, tail, bg, tolerance);
            if (y + 1 < h) tail = enqueueIfBackground(idx + w, px, mask, queue, tail, bg, tolerance);
        }

        int filled = tail;
        double ratio = filled / (double) n;
        if (ratio < 0.08 || ratio > 0.93) {
            work.recycle();
            return new Result(src, false, ratio);
        }

        int tr = Color.red(TARGET), tg = Color.green(TARGET), tb = Color.blue(TARGET);
        for (int i = 0; i < n; i++) {
            if (mask[i]) px[i] = Color.rgb(tr, tg, tb);
        }

        // 1픽셀 fringe를 부드럽게 섞어 검정/흰 halo를 줄입니다.
        boolean[] fringe = new boolean[n];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int idx = y * w + x;
                if (mask[idx]) continue;
                if (mask[idx - 1] || mask[idx + 1] || mask[idx - w] || mask[idx + w]) fringe[idx] = true;
            }
        }
        int fringeTolerance = Math.min(82, tolerance + 30);
        for (int i = 0; i < n; i++) {
            if (!fringe[i]) continue;
            int d = colorDistance(px[i], bg);
            if (d > fringeTolerance) continue;
            double blend = 0.72 * (1.0 - d / (double) fringeTolerance);
            int r = (int) Math.round(Color.red(px[i]) * (1 - blend) + tr * blend);
            int g = (int) Math.round(Color.green(px[i]) * (1 - blend) + tg * blend);
            int b = (int) Math.round(Color.blue(px[i]) * (1 - blend) + tb * blend);
            px[i] = Color.rgb(r, g, b);
        }

        work.setPixels(px, 0, w, 0, 0, w, h);
        src.recycle();
        return new Result(work, true, ratio);
    }

    public static boolean savePng(Bitmap bitmap, File out) throws Exception {
        File parent = out.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            return bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }

    private static int enqueueIfBackground(int idx, int[] px, boolean[] mask, int[] queue, int tail,
                                           int bg, int tolerance) {
        if (mask[idx]) return tail;
        if (isBackgroundLike(px[idx], bg, tolerance)) {
            mask[idx] = true;
            queue[tail++] = idx;
        }
        return tail;
    }

    private static boolean isBackgroundLike(int color, int bg, int tolerance) {
        if (Color.alpha(color) < 80) return true;
        return colorDistance(color, bg) <= tolerance;
    }

    private static int estimateBackground(int[] px, int w, int h) {
        int patch = Math.max(4, Math.min(w, h) / 35);
        int count = patch * patch * 4;
        int[] rs = new int[count];
        int[] gs = new int[count];
        int[] bs = new int[count];
        int k = 0;
        int[][] corners = {{0, 0}, {w - patch, 0}, {0, h - patch}, {w - patch, h - patch}};
        for (int[] c : corners) {
            for (int y = c[1]; y < c[1] + patch; y++) {
                for (int x = c[0]; x < c[0] + patch; x++) {
                    int p = px[y * w + x];
                    rs[k] = Color.red(p);
                    gs[k] = Color.green(p);
                    bs[k] = Color.blue(p);
                    k++;
                }
            }
        }
        Arrays.sort(rs); Arrays.sort(gs); Arrays.sort(bs);
        return Color.rgb(rs[count / 2], gs[count / 2], bs[count / 2]);
    }

    private static int estimateTolerance(int[] px, int w, int h, int bg) {
        int patch = Math.max(4, Math.min(w, h) / 35);
        int[] ds = new int[patch * patch * 4];
        int k = 0;
        int[][] corners = {{0, 0}, {w - patch, 0}, {0, h - patch}, {w - patch, h - patch}};
        for (int[] c : corners) {
            for (int y = c[1]; y < c[1] + patch; y++) {
                for (int x = c[0]; x < c[0] + patch; x++) ds[k++] = colorDistance(px[y * w + x], bg);
            }
        }
        Arrays.sort(ds);
        int p90 = ds[Math.min(ds.length - 1, (int) Math.round(ds.length * 0.90))];
        return Math.max(24, Math.min(48, p90 + 18));
    }

    private static int colorDistance(int a, int b) {
        int dr = Color.red(a) - Color.red(b);
        int dg = Color.green(a) - Color.green(b);
        int db = Color.blue(a) - Color.blue(b);
        return (int) Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private static Bitmap decodeScaled(String path, int maxSide) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > maxSide) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = Math.max(1, sample);
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(path, opts);
    }
}
