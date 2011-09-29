package com.oci.example.todolist;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

public class TodoListSettingsActivity extends PreferenceActivity
                implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "TodoListSettings";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals("server_address")) {
            // When the server address is changed, we need to delete all the entries in the
            TodoListSyncHelper.requestRefresh(this);
        } else if (key.equals("sync_interval")){
            TodoListSyncHelper.scheduleSync(this);
        }  else if (key.equals("offline_mode")){
            boolean enabled = prefs.getBoolean(key, false);
            Log.i(TAG, "Offline mode " + (enabled ? "enabled" : "disabled"));
            if (!enabled)
                TodoListSyncHelper.requestSync(getBaseContext());
        }
    }
}