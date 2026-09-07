package com.watchcollection.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * 대표 사진에서 스트랩/브레이슬릿을 최대한 제외하고
 * 케이스·러그·베젤·다이얼 중심의 시계 헤드 이미지를 잘라냅니다.
 *
 * 전제:
 * - 사용자가 주로 올리는 정면 제품 컷
 * - 배경은 단색 또는 모서리에서 비교적 균질한 배경
 */
public final class WatchHeadProcessor {
    private static final int MAX_SIDE = 1400;

    private WatchHeadProcessor() {}

    public static String process(Context context, String sourcePath) throws Exception {
        if (sourcePath == null || sourcePath.trim().isEmpty()) return "";
        Bitmap cropped = extractWatchHead(new File(sourcePath));
        if (cropped == null) return "";
        File dir = new File(context.getFilesDir(), "watch_images_calendar");
        dir.mkdirs();
        File out = new File(dir, String.format(Locale.ROOT, "watch_head_%d.png", System.currentTimeMillis()));
        try (FileOutputStream fos = new FileOutputStream(out)) {
            cropped.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        cropped.recycle();
        return out.getAbsolutePath();
    }

    private static Bitmap extractWatchHead(File input) {
        Bitmap src = decodeScaled(input.getAbsolutePath(), MAX_SIDE);
        if (src == null) return null;

        int w = src.getWidth();
        int h = src.getHeight();
        int n = w * h;
        int[] px = new int[n];
        src.getPixels(px, 0, w, 0, 0, w, h);

        int bg = estimateBackground(px, w, h);
        int tolerance = estimateTolerance(px, w, h, bg);
        boolean[] bgMask = buildBackgroundMask(px, w, h, bg, tolerance);

        int[] left = new int[h];
        int[] right = new int[h];
        int[] widths = new int[h];
        Arrays.fill(left, -1);
        Arrays.fill(right, -1);

        int subjectPixels = 0;
        for (int y = 0; y < h; y++) {
            int minX = w;
            int maxX = -1;
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (bgMask[idx]) continue;
                if (Color.alpha(px[idx]) < 32) continue;
                subjectPixels++;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
            }
            if (maxX >= minX) {
                left[y] = minX;
                right[y] = maxX;
                widths[y] = maxX - minX + 1;
            }
        }

        if (subjectPixels < (w * h) * 0.02) {
            src.recycle();
            return null;
        }

        int peakY = 0;
        int maxWidth = 0;
        for (int y = 0; y < h; y++) {
            if (widths[y] > maxWidth) {
                maxWidth = widths[y];
                peakY = y;
            }
        }
        if (maxWidth <= 0) {
            src.recycle();
            return null;
        }

        int strongThreshold = Math.max(12, (int) Math.round(maxWidth * 0.60));
        int softThreshold = Math.max(8, (int) Math.round(maxWidth * 0.32));

        int y1 = peakY;
        while (y1 > 0 && widths[y1 - 1] >= strongThreshold) y1--;
        while (y1 > 0 && widths[y1 - 1] >= softThreshold && (peakY - (y1 - 1)) < h * 0.30) y1--;

        int y2 = peakY;
        while (y2 + 1 < h && widths[y2 + 1] >= strongThreshold) y2++;
        while (y2 + 1 < h && widths[y2 + 1] >= softThreshold && ((y2 + 1) - peakY) < h * 0.30) y2++;

        // 너무 길게 내려가며 스트랩을 포함하는 상황을 방지
        int bandHeight = Math.max(1, y2 - y1 + 1);
        int maxBandHeight = (int) Math.round(Math.min(h * 0.62, Math.max(maxWidth * 1.10, h * 0.18)));
        if (bandHeight > maxBandHeight) {
            int center = (y1 + y2) / 2;
            int half = maxBandHeight / 2;
            y1 = Math.max(0, center - half);
            y2 = Math.min(h - 1, y1 + maxBandHeight - 1);
        }

        int minX = w;
        int maxX = -1;
        int minY = h;
        int maxY = -1;
        for (int y = y1; y <= y2; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (bgMask[idx]) continue;
                if (Color.alpha(px[idx]) < 32) continue;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }

        if (maxX < minX || maxY < minY) {
            src.recycle();
            return null;
        }

        int padX = Math.max(6, (int) Math.round((maxX - minX + 1) * 0.06));
        int padY = Math.max(6, (int) Math.round((maxY - minY + 1) * 0.08));
        minX = Math.max(0, minX - padX);
        maxX = Math.min(w - 1, maxX + padX);
        minY = Math.max(0, minY - padY);
        maxY = Math.min(h - 1, maxY + padY);

        int outW = maxX - minX + 1;
        int outH = maxY - minY + 1;
        if (outW < 10 || outH < 10) {
            src.recycle();
            return null;
        }

        Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        int[] outPx = new int[outW * outH];
        Arrays.fill(outPx, Color.TRANSPARENT);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int srcIdx = y * w + x;
                if (bgMask[srcIdx]) continue;
                int ox = x - minX;
                int oy = y - minY;
                outPx[oy * outW + ox] = px[srcIdx];
            }
        }
        out.setPixels(outPx, 0, outW, 0, 0, outW, outH);
        src.recycle();
        return out;
    }

    private static boolean[] buildBackgroundMask(int[] px, int w, int h, int bg, int tolerance) {
        int n = w * h;
        boolean[] mask = new boolean[n];
        int[] queue = new int[n];
        int head = 0;
        int tail = 0;

        int patch = Math.max(4, Math.min(w, h) / 40);
        int[][] corners = {{0, 0}, {w - patch, 0}, {0, h - patch}, {w - patch, h - patch}};
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
            if (x > 0) tail = enqueue(idx - 1, px, mask, queue, tail, bg, tolerance);
            if (x + 1 < w) tail = enqueue(idx + 1, px, mask, queue, tail, bg, tolerance);
            if (y > 0) tail = enqueue(idx - w, px, mask, queue, tail, bg, tolerance);
            if (y + 1 < h) tail = enqueue(idx + w, px, mask, queue, tail, bg, tolerance);
        }
        return mask;
    }

    private static int enqueue(int idx, int[] px, boolean[] mask, int[] queue, int tail, int bg, int tolerance) {
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
        Arrays.sort(rs);
        Arrays.sort(gs);
        Arrays.sort(bs);
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
        return Math.max(20, Math.min(52, p90 + 18));
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
