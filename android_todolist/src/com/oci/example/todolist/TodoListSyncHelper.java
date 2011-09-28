package com.oci.example.todolist;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.preference.PreferenceManager;

public class TodoListSyncHelper {

    private static Intent syncIntent = new Intent(TodoListSyncService.ACTION_TODOLIST_SYNC);
    private static Intent refreshIntent = new Intent(TodoListSyncService.ACTION_TODOLIST_REFRESH);
    private static final int LAZY_INTERVAL=5000;

    public static void requestSync(Context ctxt) {
        ctxt.startService(syncIntent);
    }

    public static void requestLazySync(Context ctxt){
        scheduleSyncAlarm(ctxt,syncIntent,LAZY_INTERVAL);
    }

    public static void requestRefresh(Context ctxt) {
        ctxt.startService(refreshIntent);
    }

    public static void scheduleSync(Context ctxt){
        Resources res = ctxt.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
        int syncInterval = Integer.parseInt(prefs.getString("sync_interval",
                                        res.getString(R.string.setting_sync_interval_default_value)));

        scheduleSyncAlarm(ctxt,syncIntent,syncInterval);
    }

    public static void cancelPendingSync(Context ctxt){
        AlarmManager alarmManager = (AlarmManager) ctxt.getSystemService(Context.ALARM_SERVICE);
        PendingIntent syncOperation = PendingIntent.getService(ctxt, 0, syncIntent, 0);

        alarmManager.cancel(syncOperation);
    }

    private static void scheduleSyncAlarm(Context ctxt, Intent action, int when) {
        AlarmManager alarmManager = (AlarmManager) ctxt.getSystemService(Context.ALARM_SERVICE);


        PendingIntent syncOperation = PendingIntent.getService(ctxt, 0, action, 0);

        alarmManager.cancel(syncOperation);
        alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + when, syncOperation);
    }
}
