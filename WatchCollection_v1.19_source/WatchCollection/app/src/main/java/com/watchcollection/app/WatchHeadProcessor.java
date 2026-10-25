package com.watchcollection.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * 캘린더/위젯용 시계 본체 이미지 생성기.
 * 정면 제품 사진에서 스트랩/브레이슬릿을 최대한 제외하고
 * 케이스 + 러그 + 베젤 + 다이얼 + 크라운/푸셔를 남긴 뒤
 * 투명 정사각형 캔버스 중앙에 배치합니다.
 */
public final class WatchHeadProcessor {
    private static final int MAX_SIDE = 1600;

    private WatchHeadProcessor() {}

    private static final String PREFS = "watch_head_processor";
    private static final String KEY_VERSION = "algorithm_version";
    private static final int ALGORITHM_VERSION = 3;

    public static boolean needsRegeneration(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_VERSION, 0) < ALGORITHM_VERSION;
    }

    public static void markRegenerated(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_VERSION, ALGORITHM_VERSION).apply();
    }


    public static String process(Context context, String sourcePath) throws Exception {
        if (sourcePath == null || sourcePath.trim().isEmpty()) return "";
        Bitmap result = extractWatchHead(new File(sourcePath));
        if (result == null) return "";

        File dir = new File(context.getFilesDir(), "watch_images_calendar");
        dir.mkdirs();
        File out = new File(dir, String.format(Locale.ROOT, "watch_head_%d.png", System.currentTimeMillis()));
        try (FileOutputStream fos = new FileOutputStream(out)) {
            result.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        result.recycle();
        return out.getAbsolutePath();
    }

    private static Bitmap extractWatchHead(File input) {
        Bitmap src = decodeScaled(input.getAbsolutePath(), MAX_SIDE);
        if (src == null) return null;

        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);

        int bg = estimateBackground(px, w, h);
        int tolerance = estimateTolerance(px, w, h, bg);
        boolean[] bgMask = buildBackgroundMask(px, w, h, bg, tolerance);

        int[] widths = new int[h];
        int[] rowLeft = new int[h];
        int[] rowRight = new int[h];
        Arrays.fill(rowLeft, -1);
        Arrays.fill(rowRight, -1);

        int foregroundCount = 0;
        for (int y = 0; y < h; y++) {
            int minX = w;
            int maxX = -1;
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (bgMask[idx] || Color.alpha(px[idx]) < 32) continue;
                foregroundCount++;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
            }
            if (maxX >= minX) {
                rowLeft[y] = minX;
                rowRight[y] = maxX;
                widths[y] = maxX - minX + 1;
            }
        }

        if (foregroundCount < w * h * 0.015) {
            src.recycle();
            return null;
        }

        double[] smooth = smoothWidths(widths, 5);
        int peakY = 0;
        double peakWidth = 0;
        for (int y = Math.max(0, h / 12); y < Math.min(h, h * 11 / 12); y++) {
            if (smooth[y] > peakWidth) {
                peakWidth = smooth[y];
                peakY = y;
            }
        }
        if (peakWidth < 10) {
            src.recycle();
            return null;
        }

        // 케이스 폭의 약 절반 이하로 급격히 좁아지는 지점을 스트랩 시작으로 간주합니다.
        double edgeThreshold = peakWidth * 0.54;
        int top = peakY;
        int belowThresholdRun = 0;
        for (int y = peakY - 1; y >= 0; y--) {
            if (smooth[y] >= edgeThreshold) {
                top = y;
                belowThresholdRun = 0;
            } else {
                belowThresholdRun++;
                if (belowThresholdRun >= 4) break;
            }
        }

        int bottom = peakY;
        belowThresholdRun = 0;
        for (int y = peakY + 1; y < h; y++) {
            if (smooth[y] >= edgeThreshold) {
                bottom = y;
                belowThresholdRun = 0;
            } else {
                belowThresholdRun++;
                if (belowThresholdRun >= 4) break;
            }
        }

        // 러그 끝은 살리고 스트랩은 다시 들어오지 않도록 케이스 폭 기준으로만 소량 확장합니다.
        int lugPad = Math.max(3, (int) Math.round(peakWidth * 0.03));
        top = Math.max(0, top - lugPad);
        bottom = Math.min(h - 1, bottom + lugPad);

        // 정면 시계 헤드의 세로 길이는 대체로 최대 폭의 0.85~1.35배 범위입니다.
        int maxHeadHeight = Math.min(h, Math.max((int) Math.round(peakWidth * 1.34), (int) Math.round(h * 0.18)));
        if (bottom - top + 1 > maxHeadHeight) {
            int center = (top + bottom) / 2;
            top = Math.max(0, center - maxHeadHeight / 2);
            bottom = Math.min(h - 1, top + maxHeadHeight - 1);
            if (bottom - top + 1 < maxHeadHeight) top = Math.max(0, bottom - maxHeadHeight + 1);
        }

        int minX = w;
        int maxX = -1;
        int minY = h;
        int maxY = -1;
        for (int y = top; y <= bottom; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (bgMask[idx] || Color.alpha(px[idx]) < 32) continue;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }

        if (maxX < minX || maxY < minY) {
            src.recycle();
            return null;
        }

        int subjectW = maxX - minX + 1;
        int subjectH = maxY - minY + 1;
        int padX = Math.max(5, (int) Math.round(subjectW * 0.035));
        int padY = Math.max(5, (int) Math.round(subjectH * 0.035));
        minX = Math.max(0, minX - padX);
        maxX = Math.min(w - 1, maxX + padX);
        minY = Math.max(0, minY - padY);
        maxY = Math.min(h - 1, maxY + padY);

        int cropW = maxX - minX + 1;
        int cropH = maxY - minY + 1;
        if (cropW < 12 || cropH < 12) {
            src.recycle();
            return null;
        }

        // 최종 이미지는 정사각형 + 투명 배경. 본체를 정확히 중앙 배치합니다.
        int side = (int) Math.ceil(Math.max(cropW, cropH) * 1.12);
        side = Math.max(side, 64);
        Bitmap out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        int[] outPx = new int[side * side];
        Arrays.fill(outPx, Color.TRANSPARENT);

        int offsetX = (side - cropW) / 2;
        int offsetY = (side - cropH) / 2;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int srcIdx = y * w + x;
                if (bgMask[srcIdx] || Color.alpha(px[srcIdx]) < 32) continue;
                int ox = offsetX + (x - minX);
                int oy = offsetY + (y - minY);
                outPx[oy * side + ox] = px[srcIdx];
            }
        }
        out.setPixels(outPx, 0, side, 0, 0, side, side);
        src.recycle();
        return out;
    }

    private static double[] smoothWidths(int[] widths, int radius) {
        double[] out = new double[widths.length];
        for (int i = 0; i < widths.length; i++) {
            int from = Math.max(0, i - radius);
            int to = Math.min(widths.length - 1, i + radius);
            int count = 0;
            int sum = 0;
            for (int j = from; j <= to; j++) {
                if (widths[j] <= 0) continue;
                sum += widths[j];
                count++;
            }
            out[i] = count == 0 ? 0 : sum / (double) count;
        }
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
        return Math.max(20, Math.min(50, p90 + 17));
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
