package com.watchcollection.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
 * 케이스백 사진 전용 배경 제거기.
 *
 * <p>금속 케이스백은 배경색을 강하게 반사하고 가장자리에 그림자가 생기므로,
 * 대표사진용 단순 모서리 flood-fill만 사용하면 케이스 일부가 잘리거나 배경이 남기 쉽습니다.</p>
 *
 * <p>1차로 ML Kit Subject Segmentation을 사용하고, 모델 초기화 또는 인식이 실패하면
 * 다중 모서리 색상 모델과 경계 연결성을 이용한 로컬 분리기로 전환합니다.
 * 최종 결과는 앱 사진 배경색 위에 중앙 정렬합니다.</p>
 */
public final class CasebackBackgroundProcessor {
    private static final int MAX_PROCESS_SIDE = 1280;
    private static final int CANVAS_SIZE = 1200;
    private static final int TARGET_BACKGROUND = UiKit.PHOTO_BG;

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

    private CasebackBackgroundProcessor() {}

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
        Bitmap foreground = null;
        try {
            SubjectSegmenterOptions options = new SubjectSegmenterOptions.Builder()
                    .enableForegroundBitmap()
                    .build();
            segmenter = SubjectSegmentation.getClient(options);

            // 첫 실행에서는 Google Play services가 모델을 내려받을 수 있습니다.
            Tasks.await(segmenter.getInitTask(), 60, TimeUnit.SECONDS);
            SubjectSegmentationResult segmentation = Tasks.await(
                    segmenter.process(InputImage.fromBitmap(source, 0)),
                    45,
                    TimeUnit.SECONDS);
            foreground = segmentation.getForegroundBitmap();
            if (foreground == null) return null;

            Bitmap work = foreground.copy(Bitmap.Config.ARGB_8888, true);
            if (work == null) return null;
            refineMlForeground(work, source);

            double ratio = alphaRatio(work, 24);
            if (!isPlausibleForeground(work, ratio)) {
                work.recycle();
                return null;
            }

            Bitmap centered = cropAndCenterOnPhotoBackground(work);
            work.recycle();
            if (centered == null) return null;
            return new Result(centered, true, true, ratio);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (segmenter != null) segmenter.close();
            // ML Kit이 반환한 Bitmap의 수명은 호출 측에서 관리합니다.
            if (foreground != null && !foreground.isRecycled()) foreground.recycle();
        }
    }

    /**
     * 분리 결과에서 주변의 작은 물체를 제거하고 금속 반사로 생기는 작은 마스크 구멍과
     * 회색 halo를 완화합니다.
     */
    private static void refineMlForeground(Bitmap foreground, Bitmap source) {
        int w = foreground.getWidth();
        int h = foreground.getHeight();
        if (source.getWidth() != w || source.getHeight() != h) return;

        int n = w * h;
        int[] fg = new int[n];
        int[] src = new int[n];
        foreground.getPixels(fg, 0, w, 0, 0, w, h);
        source.getPixels(src, 0, w, 0, 0, w, h);

        boolean[] mask = new boolean[n];
        for (int i = 0; i < n; i++) mask[i] = Color.alpha(fg[i]) > 20;
        keepCentralComponents(mask, w, h);
        closeSinglePixelGaps(mask, w, h, 2);

        // 먼저 명확한 전경/배경을 확정합니다.
        for (int i = 0; i < n; i++) {
            if (!mask[i]) {
                fg[i] = Color.TRANSPARENT;
                continue;
            }
            int a = Color.alpha(fg[i]);
            if (a <= 18) {
                // 마스크가 유지한 작은 금속 하이라이트 구멍은 원본 픽셀로 복원합니다.
                fg[i] = Color.argb(190, Color.red(src[i]), Color.green(src[i]), Color.blue(src[i]));
            } else if (a < 95) {
                int adjusted = Math.min(170, Math.max(30, (a - 18) * 2));
                fg[i] = Color.argb(adjusted, Color.red(src[i]), Color.green(src[i]), Color.blue(src[i]));
            } else if (a > 232) {
                fg[i] = Color.argb(255, Color.red(src[i]), Color.green(src[i]), Color.blue(src[i]));
            } else {
                fg[i] = Color.argb(a, Color.red(src[i]), Color.green(src[i]), Color.blue(src[i]));
            }
        }

        decontaminateEdgeColors(fg, mask, w, h);
        foreground.setPixels(fg, 0, w, 0, 0, w, h);
    }

    /** 배경색이 섞인 반투명 경계 RGB를 인접한 불투명 전경색에 가깝게 보정합니다. */
    private static void decontaminateEdgeColors(int[] px, boolean[] mask, int w, int h) {
        int[] out = Arrays.copyOf(px, px.length);
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int idx = y * w + x;
                int alpha = Color.alpha(px[idx]);
                if (!mask[idx] || alpha <= 0 || alpha >= 235) continue;

                int rs = 0, gs = 0, bs = 0, count = 0;
                for (int yy = y - 1; yy <= y + 1; yy++) {
                    for (int xx = x - 1; xx <= x + 1; xx++) {
                        int neighbor = px[yy * w + xx];
                        if (Color.alpha(neighbor) < 220) continue;
                        rs += Color.red(neighbor);
                        gs += Color.green(neighbor);
                        bs += Color.blue(neighbor);
                        count++;
                    }
                }
                if (count > 0) {
                    out[idx] = Color.argb(alpha, rs / count, gs / count, bs / count);
                }
            }
        }
        System.arraycopy(out, 0, px, 0, px.length);
    }

    /** ML 마스크 내부의 한두 픽셀짜리 구멍만 닫아 각인과 브레이슬릿 틈은 유지합니다. */
    private static void closeSinglePixelGaps(boolean[] mask, int w, int h, int iterations) {
        for (int iteration = 0; iteration < iterations; iteration++) {
            boolean[] next = Arrays.copyOf(mask, mask.length);
            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    int idx = y * w + x;
                    if (mask[idx]) continue;
                    int neighbors = 0;
                    for (int yy = y - 1; yy <= y + 1; yy++) {
                        for (int xx = x - 1; xx <= x + 1; xx++) {
                            if (xx == x && yy == y) continue;
                            if (mask[yy * w + xx]) neighbors++;
                        }
                    }
                    if (neighbors >= 7) next[idx] = true;
                }
            }
            System.arraycopy(next, 0, mask, 0, mask.length);
        }
    }

    private static Result processWithAdaptiveFloodFill(Bitmap source) {
        Bitmap work = source.copy(Bitmap.Config.ARGB_8888, true);
        if (work == null) return new Result(null, false, false, 0.0);

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

        boolean[] foreground = new boolean[n];
        for (int i = 0; i < n; i++) {
            foreground[i] = !background[i] && Color.alpha(px[i]) > 20;
        }
        keepCentralComponents(foreground, w, h);
        closeSinglePixelGaps(foreground, w, h, 1);

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
        if (ratio < 0.012 || ratio > 0.90 || !hasCenterForeground(foreground, w, h)) {
            work.recycle();
            return new Result(null, false, false, ratio);
        }

        featherForegroundAlpha(px, foreground, w, h);
        work.setPixels(px, 0, w, 0, 0, w, h);
        if (!isPlausibleForeground(work, ratio)) {
            work.recycle();
            return new Result(null, false, false, ratio);
        }

        Bitmap centered = cropAndCenterOnPhotoBackground(work);
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
        if (Color.alpha(px[idx]) < 60 || nearestNormalizedDistance(px[idx], models) <= 1.14) {
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

        // 케이스백 촬영에서 자주 생기는 완만한 그림자와 밝기 그라데이션은 따라가되,
        // 금속 케이스의 선명한 경계에서는 전파가 멈추도록 제한합니다.
        boolean accept = global <= 1.0
                || (global <= 1.65 && local <= 16 && edge <= 42)
                || (global <= 2.10 && local <= 8 && edge <= 24);
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
            int tolerance = Math.max(18, Math.min(72, p90 + 18));

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
        if (models.isEmpty()) models.add(new ColorModel(Color.WHITE, 34));
        return models;
    }

    private static double nearestNormalizedDistance(int color, List<ColorModel> models) {
        double best = Double.MAX_VALUE;
        for (ColorModel model : models) {
            double normalized = perceptualDistance(color, model.color)
                    / (double) Math.max(1, model.tolerance);
            if (normalized < best) best = normalized;
        }
        return best;
    }

    /** 밝기보다 색차를 조금 더 크게 보는 YCbCr 기반 거리입니다. */
    private static int perceptualDistance(int a, int b) {
        double ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        double br = Color.red(b), bg = Color.green(b), bb = Color.blue(b);
        double ay = 0.299 * ar + 0.587 * ag + 0.114 * ab;
        double by = 0.299 * br + 0.587 * bg + 0.114 * bb;
        double acb = (ab - ay) * 0.565;
        double bcb = (bb - by) * 0.565;
        double acr = (ar - ay) * 0.713;
        double bcr = (br - by) * 0.713;
        double dy = (ay - by) * 0.52;
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

    /**
     * 화면 중앙의 주 피사체를 우선 보존합니다. 분리된 브레이슬릿/스트랩 조각은
     * 주 피사체와 가깝고 충분히 큰 경우 함께 남깁니다.
     */
    private static void keepCentralComponents(boolean[] foreground, int w, int h) {
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

        if (components.isEmpty()) {
            Arrays.fill(foreground, false);
            return;
        }

        final double cx = w / 2.0;
        final double cy = h / 2.0;
        final double maxDistance = Math.hypot(cx, cy);
        for (Component c : components) {
            double componentCx = (c.minX + c.maxX) / 2.0;
            double componentCy = (c.minY + c.maxY) / 2.0;
            double distance = Math.hypot(componentCx - cx, componentCy - cy);
            double centerWeight = 1.0 - Math.min(1.0, distance / Math.max(1.0, maxDistance));
            c.score = c.size * (0.58 + 0.42 * centerWeight);
        }
        components.sort(Comparator.comparingDouble((Component c) -> c.score).reversed());
        Component primary = components.get(0);

        boolean[] keepLabel = new boolean[nextLabel + 1];
        keepLabel[primary.label] = true;
        int retained = 1;
        int marginX = Math.max(8, (int) Math.round(w * 0.10));
        int marginY = Math.max(8, (int) Math.round(h * 0.16));
        int minSize = Math.max(80, Math.max(n / 5000, primary.size / 60));

        for (int i = 1; i < components.size() && retained < 7; i++) {
            Component c = components.get(i);
            if (c.size < minSize) continue;
            boolean overlapsExpandedPrimary = c.maxX >= primary.minX - marginX
                    && c.minX <= primary.maxX + marginX
                    && c.maxY >= primary.minY - marginY
                    && c.minY <= primary.maxY + marginY;
            double componentCx = (c.minX + c.maxX) / 2.0;
            double componentCy = (c.minY + c.maxY) / 2.0;
            boolean nearCenter = Math.abs(componentCx - cx) <= w * 0.38
                    && Math.abs(componentCy - cy) <= h * 0.44;
            if (overlapsExpandedPrimary || (nearCenter && c.size >= primary.size * 0.035)) {
                keepLabel[c.label] = true;
                retained++;
            }
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
        int[] alpha = new int[px.length];
        for (int i = 0; i < px.length; i++) alpha[i] = foreground[i] ? 255 : 0;

        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int idx = y * w + x;
                if (!foreground[idx]) continue;
                int backgroundNeighbors = 0;
                if (!foreground[idx - 1]) backgroundNeighbors++;
                if (!foreground[idx + 1]) backgroundNeighbors++;
                if (!foreground[idx - w]) backgroundNeighbors++;
                if (!foreground[idx + w]) backgroundNeighbors++;
                if (backgroundNeighbors > 0) alpha[idx] = 185 + (4 - backgroundNeighbors) * 15;
            }
        }
        for (int i = 0; i < px.length; i++) {
            if (alpha[i] == 0) px[i] = Color.TRANSPARENT;
            else px[i] = Color.argb(Math.min(255, alpha[i]),
                    Color.red(px[i]), Color.green(px[i]), Color.blue(px[i]));
        }
    }

    private static boolean hasCenterForeground(boolean[] mask, int w, int h) {
        int left = Math.max(0, (int) Math.round(w * 0.34));
        int right = Math.min(w, (int) Math.round(w * 0.66));
        int top = Math.max(0, (int) Math.round(h * 0.34));
        int bottom = Math.min(h, (int) Math.round(h * 0.66));
        int count = 0;
        int total = Math.max(1, (right - left) * (bottom - top));
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) if (mask[y * w + x]) count++;
        }
        return count / (double) total >= 0.025;
    }

    private static boolean isPlausibleForeground(Bitmap bitmap, double ratio) {
        if (ratio < 0.012 || ratio > 0.90) return false;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] px = new int[w * h];
        bitmap.getPixels(px, 0, w, 0, 0, w, h);
        int minX = w, minY = h, maxX = -1, maxY = -1;
        boolean[] mask = new boolean[px.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (Color.alpha(px[idx]) <= 24) continue;
                mask[idx] = true;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < minX || maxY < minY || !hasCenterForeground(mask, w, h)) return false;
        return (maxX - minX + 1) >= Math.max(12, w * 0.14)
                && (maxY - minY + 1) >= Math.max(12, h * 0.14);
    }

    private static Bitmap cropAndCenterOnPhotoBackground(Bitmap source) {
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
        int padX = Math.max(6, Math.round(bboxW * 0.05f));
        int padY = Math.max(6, Math.round(bboxH * 0.05f));
        minX = Math.max(0, minX - padX);
        minY = Math.max(0, minY - padY);
        maxX = Math.min(w - 1, maxX + padX);
        maxY = Math.min(h - 1, maxY + padY);

        Rect srcRect = new Rect(minX, minY, maxX + 1, maxY + 1);
        Bitmap output = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(TARGET_BACKGROUND);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        float maxW = CANVAS_SIZE * 0.88f;
        float maxH = CANVAS_SIZE * 0.88f;
        float scale = Math.min(maxW / srcRect.width(), maxH / srcRect.height());
        int drawW = Math.max(1, Math.round(srcRect.width() * scale));
        int drawH = Math.max(1, Math.round(srcRect.height() * scale));
        int left = (CANVAS_SIZE - drawW) / 2;
        int top = (CANVAS_SIZE - drawH) / 2;
        canvas.drawBitmap(source, srcRect, new Rect(left, top, left + drawW, top + drawH), paint);
        return output;
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
        double score;

        Component(int label) {
            this.label = label;
        }
    }
}
