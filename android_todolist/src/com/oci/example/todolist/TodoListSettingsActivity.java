package com.oci.example.todolist;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class TodoListSettingsActivity extends PreferenceActivity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
    }
}