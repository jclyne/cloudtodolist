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
import com.oci.example.todolist.provider.TodoListSchema;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 * Service that handles sync and refresh request intents and handles
 * requesting the sync from the TodoList provider on a background
 * thread.
 * This will handle backing off requests when there are network errors
 * as well honoring the offline mode preference and system wide
 * background data setting
 */
public class TodoListSyncService extends IntentService {

    // Log Tag
    private static final String TAG = "TodoListSyncService";

    // Definitions of the supported intents
    private static final String INTENT_BASE = "com.oci.example.todolist";
    public static final String ACTION_TODOLIST_SYNC = INTENT_BASE + ".SYNC";
    public static final String ACTION_TODOLIST_REFRESH = INTENT_BASE + ".REFRESH";

    // Reference to the system wide ConnectivityManager
    private ConnectivityManager connManager;

    // Reference to the application SharedPreferences
    private SharedPreferences prefs;

    // Reference to the content provider to sync
    private TodoListProvider provider;

    // HttpRest client to provide to the provider for sync
    private HttpRestClient client;

    // Socket timeout setting configured in the REST client
    @SuppressWarnings({"FieldCanBeLocal"})
    private final int SOCKET_TIMEOUT = 1000;


    /**
     * Default constructor
     */
    public TodoListSyncService() {
        super("TodoListSyncService");
    }

    /**
     * Called by the system when the service is first created..
     */
    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize the ConnectivityManager reference
        connManager = (ConnectivityManager) getBaseContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        // Initialize the PreferenceManager reference
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        // Build a new http client
        HttpClient httpClient = new DefaultHttpClient();
        httpClient.getParams().setParameter("http.socket.timeout", SOCKET_TIMEOUT);

        // Build a new rest client with the http client
        client = new HttpRestClient(httpClient, getString(R.string.app_host_name), true);


        // Initialize the TodoListProvider reference
        provider = (TodoListProvider) getContentResolver()
                .acquireContentProviderClient(TodoListSchema.AUTHORITY)
                .getLocalContentProvider();

        Log.d(TAG, "Service Created" + " (" + Thread.currentThread().getName() + ")");
    }

    /**
     * Called by the system every time a client explicitly starts the service
     *
     * @param intent intent supplied to startService
     * @param flags additional data about this start request.
     * @param startId  unique integer representing this specific request to start.
     * @return indicates what semantics the system should use for the service's
     * current started state. should be values from START_CONTINUATION_MASK
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service Started, startId:" + startId + " (" + Thread.currentThread().getName() + ")");
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Called by the system to notify a Service that it is no longer used and is being removed.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service Destroyed" + " (" + Thread.currentThread().getName() + ")");
    }


    /**
     * This method is invoked on the worker thread with a request to process. Only one
     * Intent is processed at a time, but the processing happens on a worker thread
     * that runs independently from other application logic. So, if this code takes a
     * long time, it will hold up other requests to the same IntentService, but it will
     * not hold up anything else. When all requests have been handled,
     * the IntentService stops itself
     *
     * This implementation will call onPerformSync on the TodoListProvider if we are
     * currently online. This handles an ACTION_TODOLIST_SYNC and ACTION_TODOLIST_REFRESH
     * intents. It will also schedule another periodic sync regardless of the whether
     * onPerformSync was called or succeeded/failed.
     *
     * @param intent  intent supplied to startService
     */
    @Override
    protected void onHandleIntent(Intent intent) {

        String action = intent.getAction();
        Log.d(TAG, "onHandleIntent: Action = " + action + " (" + Thread.currentThread().getName() + ")");
        if (action.equals(ACTION_TODOLIST_SYNC)) {
            // Todo: Implement sync result and retry/quit/exponential backoff
            if (doOnPerformSync())
                provider.onPerformSync(client, false);
            TodoListSyncHelper.scheduleSync(getBaseContext());

        } else if (action.equals(ACTION_TODOLIST_REFRESH)) {

            if (doOnPerformSync())
                provider.onPerformSync(client, true);
            TodoListSyncHelper.scheduleSync(getBaseContext());
        }
    }

    /**
     * Determines whether or not we are currently online and should call the
     * provider's onPerformSync. This will look at the 'offline_mode' preference,
     * the Background Data Settings, and the state of the active network.
     *
     * @return flag indicating whether to do an onPerformSync
     */
    private boolean doOnPerformSync() {
        final NetworkInfo netInfo = connManager.getActiveNetworkInfo();
        return !prefs.getBoolean("offline_mode", false) &&
                connManager.getBackgroundDataSetting() &&
                netInfo != null &&
                netInfo.isConnected();
    }
}
