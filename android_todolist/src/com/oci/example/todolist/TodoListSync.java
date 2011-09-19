package com.oci.example.todolist;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import com.oci.example.todolist.client.RestClient;
import com.oci.example.todolist.client.TodoListClient;
import org.json.JSONArray;
import org.json.JSONObject;

public class TodoListSync extends IntentService {
    private static final String TAG = "TodoListSyncService";

    static final String INTENT_BASE="com.oci.example.todolist";
    static final String ACTION_TODOLIST_SYNC = INTENT_BASE+".SYNC";

    private TodoListClient client;
    private NotificationManager notificationManager;

    public TodoListSync() {
        super("TodoListSync");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        client = new TodoListClient(new RestClient(settings.getString("server_address","")));

        Log.i(TAG, "Service Created"+" ("+ Thread.currentThread().getName()+")");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service Started, startId:" + startId+" ("+ Thread.currentThread().getName()+")");
        return super.onStartCommand(intent, flags, startId);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service Destroyed"+" ("+ Thread.currentThread().getName()+")");
    }


    @Override
    protected void onHandleIntent(Intent intent) {

        String action = intent.getAction();
        Log.d(TAG, "onHandleIntent: Action = " + action + " (" + Thread.currentThread().getName() + ")");
        if (action.equals(ACTION_TODOLIST_SYNC)){
            onHandleSync();
        }

    }

    private void onHandleSync() {
        // Get full list of entries, making sure to not update

        // Find list of items needing POST and atomically mark as TX in progress
        // Send POST for each item, handle result with a content update, clearing
        //  all status flags
        //  NOTE: this requires an ID update

        // Find list of items needing POST and atomically mark as TX in progress
        // Send POST for each item, handle result with a content update, clearing
        //  all status flags

        // Find list of items needing DELETE and atomically mark as TX in progress
        // Send DELETE for each item, handle result with DELETE from pending table

        performFullRefresh();
    }

    private void performSyncStaging() {


    }

    private void performPostSync() {

    }

    private void performPutSync() {

    }

    private void performDeleteSync() {

    }

    private void performIncrementalUpdate() {

    }

    private void performFullRefresh() {

    }

    private void showUpdateNotification() {

    }
}
