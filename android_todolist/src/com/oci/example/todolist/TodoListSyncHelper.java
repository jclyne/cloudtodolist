package com.oci.example.todolist;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;

public class TodoListSyncHelper {

    private static Intent syncIntent = new Intent(TodoListSyncService.ACTION_TODOLIST_SYNC);
    private static Intent refreshIntent = new Intent(TodoListSyncService.ACTION_TODOLIST_REFRESH);
    private static boolean backgroundSyncEnabled = true;

    public static void requestSync(Context ctxt) {
        ctxt.startService(syncIntent);
        if (backgroundSyncEnabled)
            scheduleSyncAlarm(ctxt);
    }

    public static void requestRefresh(Context ctxt) {
        ctxt.startService(refreshIntent);
        if (backgroundSyncEnabled)
            scheduleSyncAlarm(ctxt);
    }

    public static void enableBackgroundSync(Context ctxt) {
        backgroundSyncEnabled = true;
        scheduleSyncAlarm(ctxt);
    }

    public static void disableBackgroundSync(Context ctxt) {
        backgroundSyncEnabled = false;

        AlarmManager alarmManager = (AlarmManager) ctxt.getSystemService(Context.ALARM_SERVICE);
        PendingIntent syncOperation = PendingIntent.getService(ctxt, 0,
                new Intent(TodoListSyncService.ACTION_TODOLIST_SYNC), 0);

        alarmManager.cancel(syncOperation);
    }

    private static void scheduleSyncAlarm(Context ctxt) {
        AlarmManager alarmManager = (AlarmManager) ctxt.getSystemService(Context.ALARM_SERVICE);
        Resources res = ctxt.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
        int syncInterval = Integer.parseInt(prefs.getString("sync_interval",
                                        res.getString(R.string.setting_sync_interval_default_value)));
        long alarmInterval;
        switch (syncInterval) {
            case 15:
                alarmInterval = AlarmManager.INTERVAL_FIFTEEN_MINUTES;
                break;
            case 30:
                alarmInterval = AlarmManager.INTERVAL_HALF_HOUR;
                break;
            case 60:
                alarmInterval = AlarmManager.INTERVAL_HOUR;
                break;
            default:
                alarmInterval = syncInterval*60*1000;
        }

        PendingIntent syncOperation = PendingIntent.getService(ctxt, 0,
                new Intent(TodoListSyncService.ACTION_TODOLIST_SYNC), 0);

        alarmManager.cancel(syncOperation);

        alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + alarmInterval,
                alarmInterval,
                syncOperation);
    }
}
