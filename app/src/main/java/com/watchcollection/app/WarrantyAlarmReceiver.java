package com.watchcollection.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WarrantyAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        WarrantyNotifier.runCheck(context);
    }
}
