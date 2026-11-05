package com.watchcollection.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class NotificationUtils {
    private NotificationUtils() {}

    public static void notifyWarranty(Context context, int id, String message) {
        Intent launch = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, id, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, WarrantyNotifier.CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setContentTitle("Watch Collection")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_watch)
                .setContentIntent(pi)
                .setAutoCancel(true);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id, builder.build());
    }
}
