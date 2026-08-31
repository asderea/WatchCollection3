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

    private ImageLoader() {}

    public static void load(Context context, ImageView view, String location) {
        view.setImageDrawable(null);
        view.setBackgroundColor(UiKit.IMAGE_BG);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (location == null || location.trim().isEmpty()) return;

        String value = location.trim();
        view.setTag(value);
        EXECUTOR.execute(() -> {
            Bitmap bitmap = loadBitmap(context, value);
            view.post(() -> {
                Object tag = view.getTag();
                if (tag != null && tag.equals(value) && bitmap != null) {
                    view.setImageBitmap(bitmap);
                }
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
                con.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) WatchCollection/0.2");
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
        File dir = new File(context.getFilesDir(), "strap_images");
        dir.mkdirs();
        String name = String.format(Locale.ROOT, "strap_%d.jpg", System.currentTimeMillis());
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
