package com.watchcollection.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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


    public static class CollectionPhotoImportResult {
        public final String originalPath;
        public final String displayPath;

        CollectionPhotoImportResult(String originalPath, String displayPath) {
            this.originalPath = originalPath;
            this.displayPath = displayPath;
        }
    }
    public static class WatchImageImportResult {
        public final String originalPath;
        public final String displayPath;
        public final boolean backgroundChanged;
        public final double backgroundRatio;

        WatchImageImportResult(String originalPath, String displayPath, boolean backgroundChanged, double backgroundRatio) {
            this.originalPath = originalPath;
            this.displayPath = displayPath;
            this.backgroundChanged = backgroundChanged;
            this.backgroundRatio = backgroundRatio;
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

    private static Bitmap loadBitmap(Context context, String value) {
        try {
            if (value.startsWith("http://") || value.startsWith("https://")) {
                File cache = new File(new File(context.getFilesDir(), "watch_images"), sha256(value) + ".img");
                if (cache.exists() && cache.length() > 0) return BitmapFactory.decodeFile(cache.getAbsolutePath());
                File parent = cache.getParentFile();
                if (parent != null) parent.mkdirs();
                HttpURLConnection con = (HttpURLConnection) URI.create(value).toURL().openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(15000);
                con.setInstanceFollowRedirects(true);
                con.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) WatchCollection/0.7");
                try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(cache)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                } finally {
                    con.disconnect();
                }
                return BitmapFactory.decodeFile(cache.getAbsolutePath());
            }
            if (value.startsWith("content://")) {
                try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(value))) {
                    return BitmapFactory.decodeStream(in);
                }
            }
            return BitmapFactory.decodeFile(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String importUri(Context context, Uri uri) throws Exception {
        return importToFolder(context, uri, "strap_images", "strap", ".img");
    }

    /**
     * 원본을 별도 보관한 뒤 단색 배경을 #FCFBF7로 자동 통일합니다.
     * 복잡한 배경으로 판단되면 displayPath도 원본을 가리킵니다.
     */
    public static WatchImageImportResult importWatchUriNormalized(Context context, Uri uri) throws Exception {
        String original = importToFolder(context, uri, "watch_images_original", "watch_original", ".img");
        File normalizedDir = new File(context.getFilesDir(), "watch_images_manual");
        normalizedDir.mkdirs();
        File normalized = new File(normalizedDir, String.format(Locale.ROOT, "watch_%d_bg.png", System.currentTimeMillis()));

        BackgroundNormalizer.Result result = BackgroundNormalizer.normalize(new File(original));
        if (result.bitmap == null || !result.changed) {
            if (result.bitmap != null) result.bitmap.recycle();
            return new WatchImageImportResult(original, original, false, result.backgroundRatio);
        }
        if (!BackgroundNormalizer.savePng(result.bitmap, normalized)) {
            result.bitmap.recycle();
            return new WatchImageImportResult(original, original, false, result.backgroundRatio);
        }
        result.bitmap.recycle();
        return new WatchImageImportResult(original, normalized.getAbsolutePath(), true, result.backgroundRatio);
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

    private static String sha256(String text) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }
}
