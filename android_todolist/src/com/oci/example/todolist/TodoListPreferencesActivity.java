package com.oci.example.todolist;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Activity to handle editing shared preferences for the todolist application
 */
public class TodoListPreferencesActivity extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    // Log tag
    private static final String TAG = "TodoListSettings";

    /**
     * Called when the activity is starting. Inflates the preferences UI
     * from the settings xml and binds the view with the shared
     * preferences data. Also registers a onSharedPreferenceChanged
     * listener that handles required system updates on changes
     *
     * @param savedInstanceState saved instance state
     */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);

        // Register the change listener
        PreferenceManager.getDefaultSharedPreferences(this)
                         .registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * Called when a shared preference is changed, added, or removed.
     *
     * @param prefs SharedPreferences that received the change.
     * @param key key of the preference that was changed, added, or removed.
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

        // On sync_interval change, reschedule a periodic sync
        if (key.equals("sync_interval")) {
            TodoListSyncHelper.scheduleSync(this);
        } else if (key.equals("offline_mode")) {
            /**
             * If offline mode is turned off, schedule an immediate sync.
             * This setting does not affect scheduled syncs, the sync service
             * will determine whether to request a sync based on the value of
             *  preference and other factors.
             */
            boolean enabled = prefs.getBoolean(key, false);
            Log.i(TAG, "Offline mode " + (enabled ? "enabled" : "disabled"));
            if (!enabled)
                TodoListSyncHelper.requestSync(getBaseContext());
        }
    }
}