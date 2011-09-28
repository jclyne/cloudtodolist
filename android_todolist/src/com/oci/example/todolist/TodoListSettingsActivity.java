package com.oci.example.todolist;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class TodoListSettingsActivity extends PreferenceActivity
                implements SharedPreferences.OnSharedPreferenceChangeListener {

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("server_address")) {
            // When the server address is changed, we need to delete all the entries in the
            TodoListSyncHelper.requestRefresh(this);
        } else if (key.equals("sync_interval")){
            TodoListSyncHelper.scheduleSync(this);
        }
    }
}