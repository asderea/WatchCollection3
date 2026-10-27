package com.watchcollection.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageLoader {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final int DISPLAY_MAX_SIDE = 2400;
    private static final int WEAR_PHOTO_MAX_SIDE = 3200;

    public static class CollectionPhotoImportResult {
        public final String originalPath;
        public final String displayPath;

        CollectionPhotoImportResult(String originalPath, String displayPath) {
            this.originalPath = originalPath;
            this.displayPath = displayPath;
        }
    }

    public static class StrapImageImportResult {
        public final String originalPath;
        public final String displayPath;
        public final boolean backgroundChanged;

        StrapImageImportResult(String originalPath, String displayPath, boolean backgroundChanged) {
            this.originalPath = originalPath;
            this.displayPath = displayPath;
            this.backgroundChanged = backgroundChanged;
        }
    }

    public static class WatchImageImportResult {
        public final String originalPath;
        public final String displayPath;
        public final String calendarPath;
        public final boolean backgroundChanged;
        public final double backgroundRatio;

        WatchImageImportResult(String originalPath, String displayPath, String calendarPath,
                               boolean backgroundChanged, double backgroundRatio) {
            this.originalPath = originalPath;
            this.displayPath = displayPath;
            this.calendarPath = calendarPath;
            this.backgroundChanged = backgroundChanged;
            this.backgroundRatio = backgroundRatio;
        }
    }

    public static class CasebackImageImportResult {
        public final String originalPath;
        public final String displayPath;
        public final boolean backgroundChanged;

        CasebackImageImportResult(String originalPath, String displayPath, boolean backgroundChanged) {
            this.originalPath = originalPath;
            this.displayPath = displayPath;
            this.backgroundChanged = backgroundChanged;
        }
    }

    private ImageLoader() {}

    public static void load(Context context, ImageView view, String location) {
        load(context, view, location, ImageView.ScaleType.CENTER_CROP);
    }

    public static void loadFit(Context context, ImageView view, String location) {
        load(context, view, location, ImageView.ScaleType.FIT_CENTER);
    }

    private static void load(Context context, ImageView view, String location, ImageView.ScaleType scaleType) {
        view.setImageDrawable(null);
        view.setScaleType(scaleType);
        if (location == null || location.trim().isEmpty()) return;

        String value = location.trim();
        view.setTag(value);
        EXECUTOR.execute(() -> {
            Bitmap bitmap = loadBitmap(context, value);
            view.post(() -> {
                Object tag = view.getTag();
                if (tag != null && tag.equals(value) && bitmap != null) view.setImageBitmap(bitmap);
            });
        });
    }

    /**
     * 사진을 불러올 때 EXIF 방향값을 실제 픽셀 회전에 반영합니다.
     * 과거 버전에서 원본 바이트 그대로 저장된 오착 사진도 이 경로를 통해 정상 방향으로 표시됩니다.
     */
    private static Bitmap loadBitmap(Context context, String value) {
        try {
            if (value.startsWith("http://") || value.startsWith("https://")) {
                File cache = new File(new File(context.getFilesDir(), "watch_images"), sha256(value) + ".img");
                if (!cache.exists() || cache.length() <= 0) {
                    File parent = cache.getParentFile();
                    if (parent != null) parent.mkdirs();
                    HttpURLConnection con = (HttpURLConnection) URI.create(value).toURL().openConnection();
                    con.setConnectTimeout(10000);
                    con.setReadTimeout(15000);
                    con.setInstanceFollowRedirects(true);
                    con.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) WatchCollection/1.23");
                    try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(cache)) {
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                    } finally {
                        con.disconnect();
                    }
                }
                return decodeFileRespectingExif(cache, DISPLAY_MAX_SIDE);
            }
            if (value.startsWith("content://")) {
                return decodeUriRespectingExif(context, Uri.parse(value), DISPLAY_MAX_SIDE);
            }
            return decodeFileRespectingExif(new File(value), DISPLAY_MAX_SIDE);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String importUri(Context context, Uri uri) throws Exception {
        return importStrapUriNormalized(context, uri).displayPath;
    }

    /**
     * 직접 선택한 스트랩 사진의 EXIF 방향을 적용한 뒤 배경을 앱 사진색(#FCFBF7)으로 정리합니다.
     */
    public static StrapImageImportResult importStrapUriNormalized(Context context, Uri uri) throws Exception {
        Bitmap bitmap = decodeUriRespectingExif(context, uri, DISPLAY_MAX_SIDE);
        if (bitmap == null) throw new IllegalStateException("스트랩 사진을 읽을 수 없습니다.");
        File original = saveBitmapAsPng(context, bitmap, "strap_images_original", "strap_original");
        bitmap.recycle();
        return normalizeStrapFile(context, original);
    }

    /** 기존에 저장된 스트랩 사진을 새 분리기로 다시 처리합니다. */
    public static StrapImageImportResult reprocessStrapImage(Context context, String location) throws Exception {
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("다시 처리할 스트랩 사진이 없습니다.");
        }
        Bitmap bitmap = loadBitmap(context, location.trim());
        if (bitmap == null) throw new IllegalStateException("기존 스트랩 사진을 읽을 수 없습니다.");
        File original = saveBitmapAsPng(context, bitmap, "strap_images_original", "strap_reprocess_original");
        bitmap.recycle();
        return normalizeStrapFile(context, original);
    }

    /**
     * 구매 페이지에서 찾은 원격 대표사진을 내려받아 방향을 보정하고 배경을 정리합니다.
     */
    public static StrapImageImportResult importRemoteStrapImageNormalized(
            Context context, String imageUrl, String refererUrl) throws Exception {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("대표사진 주소가 없습니다.");
        }
        File downloadDir = new File(context.getFilesDir(), "strap_images_download");
        downloadDir.mkdirs();
        File downloaded = new File(downloadDir,
                String.format(Locale.ROOT, "strap_download_%d.img", System.currentTimeMillis()));

        HttpURLConnection con = (HttpURLConnection) URI.create(imageUrl.trim()).toURL().openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(25000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36 WatchCollection/1.23");
        con.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
        if (refererUrl != null && !refererUrl.trim().isEmpty()) con.setRequestProperty("Referer", refererUrl.trim());
        int code = con.getResponseCode();
        if (code < 200 || code >= 400) {
            con.disconnect();
            throw new IllegalStateException("대표사진 응답 오류: HTTP " + code);
        }
        try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(downloaded)) {
            byte[] buffer = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                if (total > 20 * 1024 * 1024) throw new IllegalStateException("대표사진 용량이 너무 큽니다.");
                out.write(buffer, 0, n);
            }
        } finally {
            con.disconnect();
        }

        Bitmap bitmap = decodeFileRespectingExif(downloaded, DISPLAY_MAX_SIDE);
        if (bitmap == null) throw new IllegalStateException("대표사진 형식을 읽을 수 없습니다.");
        int imageWidth=bitmap.getWidth();
        int imageHeight=bitmap.getHeight();
        float ratio=imageWidth/(float)Math.max(1,imageHeight);
        if(imageWidth<220||imageHeight<160||ratio>4.2f||ratio<0.24f) {
            bitmap.recycle();
            throw new IllegalStateException("상품사진으로 보기 어려운 크기 또는 비율입니다.");
        }
        File original = saveBitmapAsPng(context, bitmap, "strap_images_original", "strap_original");
        bitmap.recycle();
        if (!downloaded.delete()) downloaded.deleteOnExit();
        return normalizeStrapFile(context, original);
    }

    private static StrapImageImportResult normalizeStrapFile(Context context, File original) throws Exception {
        File displayDir = new File(context.getFilesDir(), "strap_images_display");
        displayDir.mkdirs();
        File display = new File(displayDir,
                String.format(Locale.ROOT, "strap_%d_cutout.png", System.currentTimeMillis()));

        Bitmap source = decodeFileRespectingExif(original, DISPLAY_MAX_SIDE);
        if (source == null) {
            return new StrapImageImportResult(original.getAbsolutePath(), original.getAbsolutePath(), false);
        }

        StrapBackgroundProcessor.Result result;
        try {
            result = StrapBackgroundProcessor.process(source);
        } finally {
            source.recycle();
        }

        String displayPath = original.getAbsolutePath();
        boolean changed = false;
        if (result != null && result.bitmap != null) {
            try {
                if (result.changed && BackgroundNormalizer.savePng(result.bitmap, display)) {
                    displayPath = display.getAbsolutePath();
                    changed = true;
                }
            } finally {
                result.bitmap.recycle();
            }
        }
        return new StrapImageImportResult(original.getAbsolutePath(), displayPath, changed);
    }

    private static File saveBitmapAsPng(Context context, Bitmap bitmap, String folder, String prefix) throws Exception {
        File dir = new File(context.getFilesDir(), folder);
        dir.mkdirs();
        File outFile = new File(dir,
                String.format(Locale.ROOT, "%s_%d.png", prefix, System.currentTimeMillis()));
        try (FileOutputStream out = new FileOutputStream(outFile)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IllegalStateException("사진 저장에 실패했습니다.");
            }
        }
        return outFile;
    }

    /**
     * 오착 사진은 EXIF 방향을 픽셀에 반영한 뒤 JPEG로 다시 저장합니다.
     * 이후 다른 앱이나 갤러리에서도 방향 메타데이터에 의존하지 않고 항상 올바른 방향으로 보입니다.
     */
    public static String importWearPhoto(Context context, Uri uri) throws Exception {
        Bitmap bitmap = decodeUriRespectingExif(context, uri, WEAR_PHOTO_MAX_SIDE);
        if (bitmap == null) throw new IllegalStateException("오착 사진을 읽을 수 없습니다.");

        File dir = new File(context.getFilesDir(), "wear_photos");
        dir.mkdirs();
        File outFile = new File(dir, String.format(Locale.ROOT, "wear_%d.jpg", System.currentTimeMillis()));
        try (FileOutputStream out = new FileOutputStream(outFile)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                throw new IllegalStateException("오착 사진 저장에 실패했습니다.");
            }
        } finally {
            bitmap.recycle();
        }
        return outFile.getAbsolutePath();
    }

    /**
     * 원본을 별도 보관한 뒤 단색 배경을 #FCFBF7로 자동 통일하고,
     * 캘린더/위젯용으로 스트랩을 제외한 시계 헤드 이미지를 추가 생성합니다.
     */
    public static WatchImageImportResult importWatchUriNormalized(Context context, Uri uri) throws Exception {
        String original = importToFolder(context, uri, "watch_images_original", "watch_original", ".img");
        File normalizedDir = new File(context.getFilesDir(), "watch_images_manual");
        normalizedDir.mkdirs();
        File normalized = new File(normalizedDir, String.format(Locale.ROOT, "watch_%d_bg.png", System.currentTimeMillis()));

        BackgroundNormalizer.Result result = BackgroundNormalizer.normalize(new File(original));
        String displayPath = original;
        boolean changed = false;
        if (result.bitmap != null && result.changed && BackgroundNormalizer.savePng(result.bitmap, normalized)) {
            displayPath = normalized.getAbsolutePath();
            changed = true;
        }
        if (result.bitmap != null) result.bitmap.recycle();

        String calendarPath = ensureCalendarWatchImage(context, original, displayPath);
        return new WatchImageImportResult(original, displayPath, calendarPath, changed, result.backgroundRatio);
    }

    /**
     * 케이스백 사진은 EXIF 방향을 픽셀에 반영한 원본을 별도 저장하고,
     * 대표 시계 사진과 동일한 #FCFBF7 배경으로 정리한 표시용 PNG를 생성합니다.
     */
    public static CasebackImageImportResult importCasebackUriNormalized(Context context, Uri uri) throws Exception {
        Bitmap oriented = decodeUriRespectingExif(context, uri, DISPLAY_MAX_SIDE);
        if (oriented == null) throw new IllegalStateException("케이스백 사진을 읽을 수 없습니다.");

        File originalDir = new File(context.getFilesDir(), "watch_caseback_original");
        originalDir.mkdirs();
        File originalFile = new File(originalDir,
                String.format(Locale.ROOT, "caseback_original_%d.png", System.currentTimeMillis()));
        try (FileOutputStream out = new FileOutputStream(originalFile)) {
            if (!oriented.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IllegalStateException("케이스백 원본 저장에 실패했습니다.");
            }
        } finally {
            oriented.recycle();
        }

        File displayDir = new File(context.getFilesDir(), "watch_caseback_display");
        displayDir.mkdirs();
        File displayFile = new File(displayDir,
                String.format(Locale.ROOT, "caseback_%d_bg.png", System.currentTimeMillis()));

        BackgroundNormalizer.Result result = BackgroundNormalizer.normalize(originalFile);
        String displayPath = originalFile.getAbsolutePath();
        boolean changed = false;
        if (result.bitmap != null && result.changed && BackgroundNormalizer.savePng(result.bitmap, displayFile)) {
            displayPath = displayFile.getAbsolutePath();
            changed = true;
        }
        if (result.bitmap != null) result.bitmap.recycle();
        return new CasebackImageImportResult(originalFile.getAbsolutePath(), displayPath, changed);
    }

    public static CollectionPhotoImportResult importCollectionPhoto(Context context, Uri uri) throws Exception {
        String original = importToFolder(context, uri, "collection_original", "collection_original", ".img");
        String display = CollectionPhotoProcessor.process(context, original);
        return new CollectionPhotoImportResult(original, display);
    }

    /** 기존 코드와의 호환용. 새 대표사진은 importWatchUriNormalized 사용을 권장합니다. */
    public static String importWatchUri(Context context, Uri uri) throws Exception {
        return importWatchUriNormalized(context, uri).displayPath;
    }

    public static String normalizeExistingWatchImage(Context context, String originalPath) throws Exception {
        if (originalPath == null || originalPath.trim().isEmpty()) return "";
        BackgroundNormalizer.Result result = BackgroundNormalizer.normalize(new File(originalPath));
        if (result.bitmap == null) return originalPath;
        if (!result.changed) { result.bitmap.recycle(); return originalPath; }
        File dir = new File(context.getFilesDir(), "watch_images_manual");
        dir.mkdirs();
        File normalized = new File(dir, String.format(Locale.ROOT, "watch_%d_bg.png", System.currentTimeMillis()));
        BackgroundNormalizer.savePng(result.bitmap, normalized);
        result.bitmap.recycle();
        return normalized.getAbsolutePath();
    }

    /**
     * 캘린더/위젯용 시계 헤드 이미지를 확보합니다.
     * 1) 원본 우선 시도
     * 2) 표시용 이미지(displayPath) 재시도
     * 3) 모두 실패하면 displayPath/원본을 fallback으로 사용
     */
    public static String ensureCalendarWatchImage(Context context, String originalPath, String displayPath) {
        try {
            String primary = safe(originalPath);
            if (!primary.isEmpty()) {
                String out = WatchHeadProcessor.process(context, primary);
                if (!safe(out).isEmpty()) return out;
            }
        } catch (Exception ignored) {}

        try {
            String secondary = safe(displayPath);
            if (!secondary.isEmpty()) {
                String out = WatchHeadProcessor.process(context, secondary);
                if (!safe(out).isEmpty()) return out;
            }
        } catch (Exception ignored) {}

        if (!safe(displayPath).isEmpty()) return displayPath;
        return safe(originalPath);
    }

    private static String importToFolder(Context context, Uri uri, String folder, String prefix, String extension) throws Exception {
        File dir = new File(context.getFilesDir(), folder);
        dir.mkdirs();
        String name = String.format(Locale.ROOT, "%s_%d%s", prefix, System.currentTimeMillis(), extension);
        File outFile = new File(dir, name);
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(outFile)) {
            if (in == null) throw new IllegalStateException("이미지 파일을 열 수 없습니다.");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        }
        return outFile.getAbsolutePath();
    }

    private static Bitmap decodeUriRespectingExif(Context context, Uri uri, int maxSide) throws Exception {
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream exifStream = context.getContentResolver().openInputStream(uri)) {
            if (exifStream != null) {
                ExifInterface exif = new ExifInterface(exifStream);
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (Exception ignored) {}

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("이미지 파일을 열 수 없습니다.");
            BitmapFactory.decodeStream(in, null, bounds);
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSide);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("이미지 파일을 열 수 없습니다.");
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        return applyExifOrientation(bitmap, orientation);
    }

    private static Bitmap decodeFileRespectingExif(File file, int maxSide) throws Exception {
        if (file == null || !file.exists() || file.length() <= 0) return null;
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception ignored) {}

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSide);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        return applyExifOrientation(bitmap, orientation);
    }

    private static int calculateInSampleSize(int width, int height, int maxSide) {
        int sample = 1;
        if (width <= 0 || height <= 0 || maxSide <= 0) return sample;
        while (Math.max(width / sample, height / sample) > maxSide) sample *= 2;
        return Math.max(1, sample);
    }

    private static Bitmap applyExifOrientation(Bitmap bitmap, int orientation) {
        if (bitmap == null) return null;
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f);
                break;
            default:
                return bitmap;
        }

        Bitmap corrected = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (corrected != bitmap) bitmap.recycle();
        return corrected;
    }

    private static String sha256(String text) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
