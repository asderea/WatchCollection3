package com.watchcollection.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalendarWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_PREV = "com.watchcollection.app.WIDGET_PREV";
    private static final String ACTION_NEXT = "com.watchcollection.app.WIDGET_NEXT";
    private static final String PREFS = "watch_calendar_widget";
    private static final String KEY_OFFSET = "month_offset";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) updateWidget(context, appWidgetManager, appWidgetId);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_PREV.equals(action) || ACTION_NEXT.equals(action)) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            int offset = prefs.getInt(KEY_OFFSET, 0);
            offset += ACTION_PREV.equals(action) ? -1 : 1;
            prefs.edit().putInt(KEY_OFFSET, Math.max(-120, Math.min(120, offset))).apply();
            updateAll(context);
        }
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        WarrantyNotifier.scheduleNextCheck(context);
    }

    public static void updateAll(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(context, CalendarWidgetProvider.class));
        for (int id : ids) updateWidget(context, mgr, id);
    }

    private static void updateWidget(Context context, AppWidgetManager mgr, int appWidgetId) {
        RemoteViews root = new RemoteViews(context.getPackageName(), R.layout.widget_calendar);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int offset = prefs.getInt(KEY_OFFSET, 0);
        Calendar month = Calendar.getInstance();
        month.add(Calendar.MONTH, offset);
        month.set(Calendar.DAY_OF_MONTH, 1);
        root.setTextViewText(R.id.widgetTitle, String.format(Locale.KOREA, "%d년 %d월", month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1));

        Intent open = new Intent(context, MainActivity.class);
        open.putExtra(MainActivity.EXTRA_OPEN_TAB, "calendar");
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(context, 6000 + appWidgetId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        root.setOnClickPendingIntent(R.id.widgetRoot, openPi);

        Intent prev = new Intent(context, CalendarWidgetProvider.class).setAction(ACTION_PREV);
        Intent next = new Intent(context, CalendarWidgetProvider.class).setAction(ACTION_NEXT);
        root.setOnClickPendingIntent(R.id.widgetPrev, PendingIntent.getBroadcast(context, 6100 + appWidgetId, prev,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        root.setOnClickPendingIntent(R.id.widgetNext, PendingIntent.getBroadcast(context, 6200 + appWidgetId, next,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        buildWeekdays(context, root);
        buildMonth(context, root, month);
        mgr.updateAppWidget(appWidgetId, root);
    }

    private static void buildWeekdays(Context context, RemoteViews root) {
        root.removeAllViews(R.id.widgetWeekdays);
        String[] names = {"일", "월", "화", "수", "목", "금", "토"};
        for (int i = 0; i < names.length; i++) {
            RemoteViews cell = new RemoteViews(context.getPackageName(), R.layout.widget_weekday_cell);
            cell.setTextViewText(R.id.widgetWeekdayText, names[i]);
            if (i == 0) cell.setTextColor(R.id.widgetWeekdayText, UiKit.SUNDAY_RED);
            else if (i == 6) cell.setTextColor(R.id.widgetWeekdayText, UiKit.SATURDAY_BLUE);
            else cell.setTextColor(R.id.widgetWeekdayText, UiKit.MUTED);
            root.addView(R.id.widgetWeekdays, cell);
        }
    }

    private static void buildMonth(Context context, RemoteViews root, Calendar month) {
        root.removeAllViews(R.id.widgetRows);
        DatabaseHelper db = new DatabaseHelper(context);
        Calendar first = (Calendar) month.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        Calendar last = (Calendar) first.clone();
        last.set(Calendar.DAY_OF_MONTH, last.getActualMaximum(Calendar.DAY_OF_MONTH));
        Map<String, List<DatabaseHelper.WearRow>> records = db.getWearRange(
                DATE_FORMAT.format(first.getTime()), DATE_FORMAT.format(last.getTime()));

        int offset = first.get(Calendar.DAY_OF_WEEK) - 1;
        int total = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        int required = offset + total;
        int weeks = Math.max(4, (required + 6) / 7);
        String today = DATE_FORMAT.format(new Date());

        for (int rowIndex = 0; rowIndex < weeks; rowIndex++) {
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_calendar_row);
            for (int col = 0; col < 7; col++) {
                int cellIndex = rowIndex * 7 + col;
                int day = cellIndex - offset + 1;
                RemoteViews cell = new RemoteViews(context.getPackageName(), R.layout.widget_calendar_cell);
                if (day < 1 || day > total) {
                    cell.setTextViewText(R.id.widgetDay, "");
                    cell.setViewVisibility(R.id.widgetPhoto1, View.GONE);
                    cell.setViewVisibility(R.id.widgetPhoto2, View.GONE);
                } else {
                    Calendar cur = (Calendar) first.clone();
                    cur.set(Calendar.DAY_OF_MONTH, day);
                    String key = DATE_FORMAT.format(cur.getTime());
                    cell.setTextViewText(R.id.widgetDay, String.valueOf(day));
                    if (col == 0) cell.setTextColor(R.id.widgetDay, UiKit.SUNDAY_RED);
                    else if (col == 6) cell.setTextColor(R.id.widgetDay, UiKit.SATURDAY_BLUE);
                    else cell.setTextColor(R.id.widgetDay, UiKit.INK);
                    if (today.equals(key)) cell.setInt(R.id.widgetCell, "setBackgroundResource", R.drawable.widget_cell_today_bg);
                    List<DatabaseHelper.WearRow> wears = records.get(key);
                    applyWearImages(context, cell, wears);
                }
                row.addView(R.id.widgetRow, cell);
            }
            root.addView(R.id.widgetRows, row);
        }
        db.close();
    }

    private static void applyWearImages(Context context, RemoteViews cell, List<DatabaseHelper.WearRow> wears) {
        cell.setViewVisibility(R.id.widgetPhoto1, View.GONE);
        cell.setViewVisibility(R.id.widgetPhoto2, View.GONE);
        if (wears == null || wears.isEmpty()) return;
        Bitmap b1 = decodeSmall(context, wears.get(0).imageUrl, 48);
        if (b1 != null) {
            cell.setImageViewBitmap(R.id.widgetPhoto1, b1);
            cell.setViewVisibility(R.id.widgetPhoto1, View.VISIBLE);
        }
        if (wears.size() > 1) {
            Bitmap b2 = decodeSmall(context, wears.get(1).imageUrl, 48);
            if (b2 != null) {
                cell.setImageViewBitmap(R.id.widgetPhoto2, b2);
                cell.setViewVisibility(R.id.widgetPhoto2, View.VISIBLE);
            }
        }
    }

    private static Bitmap decodeSmall(Context context, String location, int maxSide) {
        try {
            if (location == null || location.trim().isEmpty()) return null;
            String value = location.trim();
            Bitmap src;
            if (value.startsWith("content://")) {
                try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(value))) {
                    src = BitmapFactory.decodeStream(in);
                }
            } else if (value.startsWith("http://") || value.startsWith("https://")) {
                return null;
            } else {
                src = BitmapFactory.decodeFile(value);
            }
            if (src == null) return null;
            int w = src.getWidth(), h = src.getHeight();
            float scale = Math.min(1f, maxSide / (float) Math.max(w, h));
            if (scale >= 0.999f) return src;
            Bitmap out = Bitmap.createScaledBitmap(src, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true);
            src.recycle();
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }
}
