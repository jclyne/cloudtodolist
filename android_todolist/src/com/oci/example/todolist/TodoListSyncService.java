package com.oci.example.todolist;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
    public static final String ACTION_TODOLIST_REFRESH = INTENT_BASE+".REFRESH";

    private  ConnectivityManager connManager;
    private  SharedPreferences prefs;
    private  TodoListProvider provider;
    private HttpRestClient client;
    private int SOCKET_TIMEOUT = 1000;

    public TodoListSyncService() {
        super("TodoListSyncService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        connManager = (ConnectivityManager) getBaseContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        HttpClient httpClient = new DefaultHttpClient();
        httpClient.getParams().setParameter("http.socket.timeout", SOCKET_TIMEOUT);
        client =  new HttpRestClient(httpClient,prefs.getString("server_address",""));

        provider = (TodoListProvider)getContentResolver()
                        .acquireContentProviderClient(TodoListProvider.Schema.AUTHORITY)
                        .getLocalContentProvider();

        Log.d(TAG, "Service Created" + " (" + Thread.currentThread().getName() + ")");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service Started, startId:" + startId + " (" + Thread.currentThread().getName() + ")");
        return super.onStartCommand(intent, flags, startId);    //To change body of overridden methods use File | TodoListSettingsActivity | File Templates.
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service Destroyed" + " (" + Thread.currentThread().getName() + ")");
    }


    @Override
    protected void onHandleIntent(Intent intent) {

        String action = intent.getAction();
        Log.d(TAG, "onHandleIntent: Action = " + action + " (" + Thread.currentThread().getName() + ")");
        if (action.equals(ACTION_TODOLIST_SYNC)){

            if (!isOffline())
                provider.onPerformSync(client, false);
            TodoListSyncHelper.scheduleSync(getBaseContext());

        } else if (action.equals(ACTION_TODOLIST_REFRESH)){

            if (!isOffline())
                provider.onPerformSync(client, true);
            TodoListSyncHelper.scheduleSync(getBaseContext());
        }
    }

    private boolean isOffline() {
        final NetworkInfo netInfo = connManager.getActiveNetworkInfo();
        return  prefs.getBoolean("offline_mode",false) ||
                !connManager.getBackgroundDataSetting() ||
                netInfo == null ||
                !netInfo.isConnected();
    }
}
