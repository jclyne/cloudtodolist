package com.oci.example.todolist.client;

import android.os.Bundle;

import java.util.List;

public class SyncableEntryList {

    private final Bundle metaData;
    private final List<SyncableEntry> entries;

    public SyncableEntryList(List<SyncableEntry> entries, Bundle metaData) {
        this.metaData = metaData;
        this.entries = entries;
    }

    public SyncableEntryList(List<SyncableEntry> entries) {
        this.entries = entries;
        this.metaData = null;
    }

    public Bundle getMetaData() {
        return metaData;
    }

    public List<SyncableEntry> getEntries() {
        return entries;
    }
}
