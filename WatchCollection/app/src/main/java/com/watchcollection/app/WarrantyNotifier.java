package com.watchcollection.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class WarrantyNotifier {
    public static final String CHANNEL_ID = "warranty_alerts";
    private static final String PREFS = "warranty_alert_state";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);

    private WarrantyNotifier() {}

    public static void scheduleNextCheck(Context context) {
        createChannel(context);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, WarrantyAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 7001, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, 9);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) next.add(Calendar.DAY_OF_YEAR, 1);
        if (am != null) am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
        CalendarWidgetProvider.updateAll(context);
    }

    static void runCheck(Context context) {
        createChannel(context);
        DatabaseHelper db = new DatabaseHelper(context);
        List<Watch> watches = db.getWatches();
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        for (Watch w : watches) {
            String label = remainingLabel(today, w.warrantyEndDate);
            if (label.isEmpty()) continue;
            String key = w.id + "_" + label + "_" + w.warrantyEndDate;
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (prefs.getBoolean(key, false)) continue;
            NotificationUtils.notifyWarranty(context, (int) w.id + label.hashCode(), w.modelName + "의 보증 기간이 " + label + " 남았습니다");
            prefs.edit().putBoolean(key, true).apply();
        }
        db.close();
        CalendarWidgetProvider.updateAll(context);
    }

    private static String remainingLabel(Calendar today, String endDate) {
        try {
            if (endDate == null || endDate.trim().isEmpty()) return "";
            Date end = DATE_FORMAT.parse(endDate.trim());
            if (end == null) return "";
            Calendar target = Calendar.getInstance();
            target.setTime(end);

            Calendar c1 = (Calendar) today.clone(); c1.add(Calendar.YEAR, 1);
            Calendar c6 = (Calendar) today.clone(); c6.add(Calendar.MONTH, 6);
            Calendar cM = (Calendar) today.clone(); cM.add(Calendar.MONTH, 1);
            Calendar cW = (Calendar) today.clone(); cW.add(Calendar.DAY_OF_YEAR, 7);
            if (sameDay(target, c1)) return "1년";
            if (sameDay(target, c6)) return "6달";
            if (sameDay(target, cM)) return "1달";
            if (sameDay(target, cW)) return "1주일";
        } catch (Exception ignored) {}
        return "";
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
                && a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH);
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Warranty Alerts", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("시계 보증 기간 만료 전 알림");
        nm.createNotificationChannel(channel);
    }
}
