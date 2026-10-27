package com.watchcollection.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.Rect;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 스트랩 상품 사진 전용 배경 제거기.
 *
 * 1차: ML Kit Subject Segmentation으로 스트랩/버클을 객체 단위로 분리
 * 2차: 모델이 아직 내려받아지지 않았거나 인식에 실패하면 모서리 다중 색상 모델과
 *      경계 연결성 기반의 로컬 분리기로 fallback
 * 3차: 분리된 피사체를 투명 PNG 캔버스 중앙에 정렬
 */
public final class StrapBackgroundProcessor {
    private static final int CANVAS_W = 1000;
    private static final int CANVAS_H = 1200;
    private static final int MAX_PROCESS_SIDE = 1600;

    public static final class Result {
        public final Bitmap bitmap;
        public final boolean changed;
        public final boolean usedMl;
        public final double foregroundRatio;

        Result(Bitmap bitmap, boolean changed, boolean usedMl, double foregroundRatio) {
            this.bitmap = bitmap;
            this.changed = changed;
            this.usedMl = usedMl;
            this.foregroundRatio = foregroundRatio;
        }
    }

    private StrapBackgroundProcessor() {}

    public static Result process(Bitmap source) {
        if (source == null) return new Result(null, false, false, 0.0);

        Bitmap scaled = scaleDown(source, MAX_PROCESS_SIDE);
        Result ml = processWithMlKit(scaled);
        if (ml != null && ml.bitmap != null) {
            if (scaled != source && !scaled.isRecycled()) scaled.recycle();
            return ml;
        }

        Result fallback = processWithAdaptiveFloodFill(scaled);
        if (scaled != source && !scaled.isRecycled()) scaled.recycle();
        return fallback;
    }

    private static Result processWithMlKit(Bitmap source) {
        SubjectSegmenter segmenter = null;
        try {
            SubjectSegmenterOptions options = new SubjectSegmenterOptions.Builder()
                    .enableForegroundBitmap()
                    .build();
            segmenter = SubjectSegmentation.getClient(options);
            // 첫 실행에서는 Google Play services가 모델을 내려받아야 할 수 있습니다.
            // 초기화가 실패하거나 지연되면 아래 catch에서 로컬 fallback으로 전환합니다.
            Tasks.await(segmenter.getInitTask(), 60, TimeUnit.SECONDS);
            SubjectSegmentationResult segmentation = Tasks.await(
                    segmenter.process(InputImage.fromBitmap(source, 0)),
                    45,
                    TimeUnit.SECONDS);
            Bitmap foreground = segmentation.getForegroundBitmap();
            if (foreground == null) return null;

            Bitmap copy = foreground.copy(Bitmap.Config.ARGB_8888, true);
            double ratio = alphaRatio(copy, 24);
            if (ratio < 0.008 || ratio > 0.92) {
                copy.recycle();
                return null;
            }

            softenMlAlpha(copy);
            Bitmap centered = cropAndCenter(copy);
            copy.recycle();
            if (centered == null) return null;
            return new Result(centered, true, true, ratio);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (segmenter != null) segmenter.close();
        }
    }

    /** ML Kit의 confidence edge에서 생기는 얇은 회색 halo를 줄입니다. */
    private static void softenMlAlpha(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] px = new int[w * h];
        bitmap.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int a = Color.alpha(px[i]);
            if (a <= 18) {
                px[i] = Color.TRANSPARENT;
            } else if (a < 90) {
                int adjusted = Math.max(0, (a - 18) * 2);
                px[i] = Color.argb(Math.min(140, adjusted), Color.red(px[i]), Color.green(px[i]), Color.blue(px[i]));
            } else if (a > 235) {
                px[i] = Color.argb(255, Color.red(px[i]), Color.green(px[i]), Color.blue(px[i]));
            }
        }
        bitmap.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static Result processWithAdaptiveFloodFill(Bitmap source) {
        Bitmap work = source.copy(Bitmap.Config.ARGB_8888, true);
        int w = work.getWidth();
        int h = work.getHeight();
        int n = w * h;
        int[] px = new int[n];
        work.getPixels(px, 0, w, 0, 0, w, h);

        List<ColorModel> models = buildCornerModels(px, w, h);
        boolean[] background = new boolean[n];
        int[] queue = new int[n];
        int head = 0;
        int tail = seedBorder(px, w, h, models, background, queue, 0);

        while (head < tail) {
            int idx = queue[head++];
            int x = idx % w;
            int y = idx / w;
            tail = tryAdaptiveNeighbor(idx, x - 1, y, px, w, h, models, background, queue, tail);
            tail = tryAdaptiveNeighbor(idx, x + 1, y, px, w, h, models, background, queue, tail);
            tail = tryAdaptiveNeighbor(idx, x, y - 1, px, w, h, models, background, queue, tail);
            tail = tryAdaptiveNeighbor(idx, x, y + 1, px, w, h, models, background, queue, tail);
        }

        // 작은 전경 잡티를 제거하되 두 조각 스트랩과 버클은 유지합니다.
        boolean[] foreground = new boolean[n];
        for (int i = 0; i < n; i++) foreground[i] = !background[i] && Color.alpha(px[i]) > 20;
        keepMeaningfulComponents(foreground, w, h);

        int fgCount = 0;
        for (int i = 0; i < n; i++) {
            if (!foreground[i]) {
                px[i] = Color.TRANSPARENT;
            } else {
                fgCount++;
                px[i] = Color.argb(255, Color.red(px[i]), Color.green(px[i]), Color.blue(px[i]));
            }
        }
        double ratio = fgCount / (double) n;
        if (ratio < 0.006 || ratio > 0.94) {
            work.recycle();
            return new Result(null, false, false, ratio);
        }

        featherForegroundAlpha(px, foreground, w, h);
        work.setPixels(px, 0, w, 0, 0, w, h);
        Bitmap centered = cropAndCenter(work);
        work.recycle();
        return new Result(centered, centered != null, false, ratio);
    }

    private static int seedBorder(int[] px, int w, int h, List<ColorModel> models,
                                  boolean[] background, int[] queue, int tail) {
        for (int x = 0; x < w; x++) {
            tail = seedIfBackground(x, px, models, background, queue, tail);
            tail = seedIfBackground((h - 1) * w + x, px, models, background, queue, tail);
        }
        for (int y = 1; y < h - 1; y++) {
            tail = seedIfBackground(y * w, px, models, background, queue, tail);
            tail = seedIfBackground(y * w + w - 1, px, models, background, queue, tail);
        }
        return tail;
    }

    private static int seedIfBackground(int idx, int[] px, List<ColorModel> models,
                                        boolean[] background, int[] queue, int tail) {
        if (background[idx]) return tail;
        if (Color.alpha(px[idx]) < 60 || nearestNormalizedDistance(px[idx], models) <= 1.08) {
            background[idx] = true;
            queue[tail++] = idx;
        }
        return tail;
    }

    private static int tryAdaptiveNeighbor(int parentIdx, int x, int y, int[] px, int w, int h,
                                           List<ColorModel> models, boolean[] background,
                                           int[] queue, int tail) {
        if (x < 0 || y < 0 || x >= w || y >= h) return tail;
        int idx = y * w + x;
        if (background[idx]) return tail;

        int candidate = px[idx];
        if (Color.alpha(candidate) < 60) {
            background[idx] = true;
            queue[tail++] = idx;
            return tail;
        }

        double global = nearestNormalizedDistance(candidate, models);
        int local = perceptualDistance(candidate, px[parentIdx]);
        int edge = localEdgeScore(px, x, y, w, h);

        boolean accept = global <= 1.0
                || (global <= 1.55 && local <= 15 && edge <= 38)
                || (global <= 1.85 && local <= 8 && edge <= 22);
        if (accept) {
            background[idx] = true;
            queue[tail++] = idx;
        }
        return tail;
    }

    private static List<ColorModel> buildCornerModels(int[] px, int w, int h) {
        int patch = Math.max(8, Math.min(w, h) / 28);
        int[][] origins = {
                {0, 0},
                {Math.max(0, w - patch), 0},
                {0, Math.max(0, h - patch)},
                {Math.max(0, w - patch), Math.max(0, h - patch)}
        };
        List<ColorModel> models = new ArrayList<>();
        for (int[] origin : origins) {
            int count = patch * patch;
            int[] rs = new int[count];
            int[] gs = new int[count];
            int[] bs = new int[count];
            int k = 0;
            for (int y = origin[1]; y < Math.min(h, origin[1] + patch); y++) {
                for (int x = origin[0]; x < Math.min(w, origin[0] + patch); x++) {
                    int color = px[y * w + x];
                    rs[k] = Color.red(color);
                    gs[k] = Color.green(color);
                    bs[k] = Color.blue(color);
                    k++;
                }
            }
            if (k == 0) continue;
            rs = Arrays.copyOf(rs, k);
            gs = Arrays.copyOf(gs, k);
            bs = Arrays.copyOf(bs, k);
            Arrays.sort(rs);
            Arrays.sort(gs);
            Arrays.sort(bs);
            int center = Color.rgb(rs[k / 2], gs[k / 2], bs[k / 2]);

            int[] distances = new int[k];
            int p = 0;
            for (int y = origin[1]; y < Math.min(h, origin[1] + patch); y++) {
                for (int x = origin[0]; x < Math.min(w, origin[0] + patch); x++) {
                    distances[p++] = perceptualDistance(px[y * w + x], center);
                }
            }
            Arrays.sort(distances);
            int p90 = distances[Math.min(distances.length - 1,
                    (int) Math.round((distances.length - 1) * 0.90))];
            int tolerance = Math.max(18, Math.min(68, p90 + 16));

            boolean duplicate = false;
            for (ColorModel existing : models) {
                if (perceptualDistance(existing.color, center) < 10) {
                    existing.tolerance = Math.max(existing.tolerance, tolerance);
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) models.add(new ColorModel(center, tolerance));
        }
        if (models.isEmpty()) models.add(new ColorModel(Color.WHITE, 32));
        return models;
    }

    private static double nearestNormalizedDistance(int color, List<ColorModel> models) {
        double best = Double.MAX_VALUE;
        for (ColorModel model : models) {
            double normalized = perceptualDistance(color, model.color) / (double) Math.max(1, model.tolerance);
            if (normalized < best) best = normalized;
        }
        return best;
    }

    /**
     * 밝기 차이보다 색차를 조금 더 크게 보는 YCbCr 기반 거리.
     * 흰/회색 배경의 약한 그림자와 그라데이션을 자연스럽게 배경으로 묶습니다.
     */
    private static int perceptualDistance(int a, int b) {
        double ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        double br = Color.red(b), bg = Color.green(b), bb = Color.blue(b);
        double ay = 0.299 * ar + 0.587 * ag + 0.114 * ab;
        double by = 0.299 * br + 0.587 * bg + 0.114 * bb;
        double acb = (ab - ay) * 0.565;
        double bcb = (bb - by) * 0.565;
        double acr = (ar - ay) * 0.713;
        double bcr = (br - by) * 0.713;
        double dy = (ay - by) * 0.55;
        double dcb = acb - bcb;
        double dcr = acr - bcr;
        return (int) Math.round(Math.sqrt(dy * dy + dcb * dcb + dcr * dcr));
    }

    private static int localEdgeScore(int[] px, int x, int y, int w, int h) {
        int center = px[y * w + x];
        int sum = 0;
        int count = 0;
        if (x > 0) { sum += perceptualDistance(center, px[y * w + x - 1]); count++; }
        if (x + 1 < w) { sum += perceptualDistance(center, px[y * w + x + 1]); count++; }
        if (y > 0) { sum += perceptualDistance(center, px[(y - 1) * w + x]); count++; }
        if (y + 1 < h) { sum += perceptualDistance(center, px[(y + 1) * w + x]); count++; }
        return count == 0 ? 0 : sum / count;
    }

    private static void keepMeaningfulComponents(boolean[] foreground, int w, int h) {
        int n = foreground.length;
        int[] labels = new int[n];
        int[] queue = new int[n];
        List<Component> components = new ArrayList<>();
        int nextLabel = 1;

        for (int i = 0; i < n; i++) {
            if (!foreground[i] || labels[i] != 0) continue;
            Component component = new Component(nextLabel);
            int head = 0;
            int tail = 0;
            labels[i] = nextLabel;
            queue[tail++] = i;

            while (head < tail) {
                int idx = queue[head++];
                component.size++;
                int x = idx % w;
                int y = idx / w;
                component.minX = Math.min(component.minX, x);
                component.maxX = Math.max(component.maxX, x);
                component.minY = Math.min(component.minY, y);
                component.maxY = Math.max(component.maxY, y);
                tail = labelNeighbor(idx - 1, x - 1, y, foreground, labels, nextLabel, w, h, queue, tail);
                tail = labelNeighbor(idx + 1, x + 1, y, foreground, labels, nextLabel, w, h, queue, tail);
                tail = labelNeighbor(idx - w, x, y - 1, foreground, labels, nextLabel, w, h, queue, tail);
                tail = labelNeighbor(idx + w, x, y + 1, foreground, labels, nextLabel, w, h, queue, tail);
            }
            components.add(component);
            nextLabel++;
        }

        components.sort(Comparator.comparingInt((Component c) -> c.size).reversed());
        int largest = components.isEmpty() ? 0 : components.get(0).size;
        int minSize = Math.max(80, Math.max((w * h) / 3500, largest / 90));
        int cx = w / 2;
        int cy = h / 2;
        boolean[] keepLabel = new boolean[nextLabel + 1];

        int retained = 0;
        for (Component component : components) {
            int componentCx = (component.minX + component.maxX) / 2;
            int componentCy = (component.minY + component.maxY) / 2;
            boolean nearCenter = Math.abs(componentCx - cx) <= w * 0.42
                    && Math.abs(componentCy - cy) <= h * 0.44;
            boolean meaningful = component.size >= minSize
                    && (nearCenter || component.size >= largest * 0.16);
            if (!meaningful) continue;
            keepLabel[component.label] = true;
            retained++;
            // 두 조각 스트랩 + 버클 등 여러 요소를 허용하되 작은 잡티는 제한
            if (retained >= 8) break;
        }

        for (int i = 0; i < n; i++) {
            int label = labels[i];
            foreground[i] = label > 0 && label < keepLabel.length && keepLabel[label];
        }
    }

    private static int labelNeighbor(int idx, int x, int y, boolean[] foreground,
                                     int[] labels, int label, int w, int h,
                                     int[] queue, int tail) {
        if (x < 0 || y < 0 || x >= w || y >= h || idx < 0 || idx >= foreground.length) return tail;
        if (!foreground[idx] || labels[idx] != 0) return tail;
        labels[idx] = label;
        queue[tail++] = idx;
        return tail;
    }

    private static void featherForegroundAlpha(int[] px, boolean[] foreground, int w, int h) {
        int[] alphas = new int[px.length];
        for (int i = 0; i < px.length; i++) alphas[i] = foreground[i] ? 255 : 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int idx = y * w + x;
                if (!foreground[idx]) continue;
                int backgroundNeighbors = 0;
                if (!foreground[idx - 1]) backgroundNeighbors++;
                if (!foreground[idx + 1]) backgroundNeighbors++;
                if (!foreground[idx - w]) backgroundNeighbors++;
                if (!foreground[idx + w]) backgroundNeighbors++;
                if (backgroundNeighbors > 0) alphas[idx] = 185 + (4 - backgroundNeighbors) * 15;
            }
        }
        for (int i = 0; i < px.length; i++) {
            if (alphas[i] == 0) px[i] = Color.TRANSPARENT;
            else px[i] = Color.argb(Math.min(255, alphas[i]), Color.red(px[i]), Color.green(px[i]), Color.blue(px[i]));
        }
    }

    private static Bitmap cropAndCenter(Bitmap source) {
        int w = source.getWidth();
        int h = source.getHeight();
        int[] px = new int[w * h];
        source.getPixels(px, 0, w, 0, 0, w, h);

        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = Color.alpha(px[y * w + x]);
                if (alpha <= 24) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < minX || maxY < minY) return null;

        int bboxW = maxX - minX + 1;
        int bboxH = maxY - minY + 1;
        int padX = Math.max(4, Math.round(bboxW * 0.03f));
        int padY = Math.max(4, Math.round(bboxH * 0.03f));
        minX = Math.max(0, minX - padX);
        minY = Math.max(0, minY - padY);
        maxX = Math.min(w - 1, maxX + padX);
        maxY = Math.min(h - 1, maxY + padY);

        Rect srcRect = new Rect(minX, minY, maxX + 1, maxY + 1);
        Bitmap canvasBitmap = Bitmap.createBitmap(CANVAS_W, CANVAS_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(canvasBitmap);
        canvas.drawColor(Color.TRANSPARENT);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        float maxW = CANVAS_W * 0.88f;
        float maxH = CANVAS_H * 0.90f;
        float scale = Math.min(maxW / srcRect.width(), maxH / srcRect.height());
        int drawW = Math.max(1, Math.round(srcRect.width() * scale));
        int drawH = Math.max(1, Math.round(srcRect.height() * scale));
        int left = (CANVAS_W - drawW) / 2;
        int top = (CANVAS_H - drawH) / 2;
        canvas.drawBitmap(source, srcRect, new Rect(left, top, left + drawW, top + drawH), paint);
        return canvasBitmap;
    }

    private static double alphaRatio(Bitmap bitmap, int threshold) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] px = new int[w * h];
        bitmap.getPixels(px, 0, w, 0, 0, w, h);
        int count = 0;
        for (int color : px) if (Color.alpha(color) > threshold) count++;
        return count / (double) px.length;
    }

    private static Bitmap scaleDown(Bitmap source, int maxSide) {
        int w = source.getWidth();
        int h = source.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxSide) return source;
        float scale = maxSide / (float) longest;
        return Bitmap.createScaledBitmap(source,
                Math.max(1, Math.round(w * scale)),
                Math.max(1, Math.round(h * scale)),
                true);
    }

    private static final class ColorModel {
        final int color;
        int tolerance;

        ColorModel(int color, int tolerance) {
            this.color = color;
            this.tolerance = tolerance;
        }
    }

    private static final class Component {
        final int label;
        int size;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        Component(int label) {
            this.label = label;
        }
    }
}
