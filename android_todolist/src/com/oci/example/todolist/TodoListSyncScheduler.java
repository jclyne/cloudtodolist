package com.oci.example.todolist;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.util.Log;


public class TodoListSyncScheduler extends BroadcastReceiver {
    private static final String TAG = "TodoListSyncScheduler";

    public void onReceive(Context ctxt, Intent intent) {
        Log.d(TAG, intent.toString());

        String action = intent.getAction();

        if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
                Log.w(TAG, "Loss of Network connectivity detected, disabling sync");
            } else {
                Log.i(TAG, "Network connectivity detected, enabling sync");
                TodoListSyncHelper.requestSync(ctxt);
            }

        } else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.i(TAG, "Enabling boot time background sync");
            TodoListSyncHelper.requestSync(ctxt);
        }

    }
}
