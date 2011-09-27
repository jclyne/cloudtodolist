package com.oci.example.todolist;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import com.oci.example.todolist.client.HttpRestClient;
import com.oci.example.todolist.provider.TodoListProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

public class TodoListSyncService extends IntentService {
    private static final String TAG = "TodoListSyncService";

    static final String INTENT_BASE="com.oci.example.todolist";
    public static final String ACTION_TODOLIST_SYNC = INTENT_BASE+".SYNC";

    TodoListProvider provider;
    private HttpRestClient client;
    private int SOCKET_TIMEOUT = 1000;

    public TodoListSyncService() {
        super("TodoListSyncService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        HttpClient httpClient = new DefaultHttpClient();
        httpClient.getParams().setParameter("http.socket.timeout", SOCKET_TIMEOUT);
        client =  new HttpRestClient(httpClient,settings.getString("server_address",""));

        provider = (TodoListProvider)getContentResolver()
                        .acquireContentProviderClient(TodoListProvider.Schema.AUTHORITY)
                        .getLocalContentProvider();

        Log.i(TAG, "Service Created"+" ("+ Thread.currentThread().getName()+")");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service Started, startId:" + startId+" ("+ Thread.currentThread().getName()+")");
        return super.onStartCommand(intent, flags, startId);    //To change body of overridden methods use File | TodoListSettingsActivity | File Templates.
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
            handleSyncIntent();
        }

    }

    private void handleSyncIntent() {
       provider.onPerformSync(client);
    }

}
