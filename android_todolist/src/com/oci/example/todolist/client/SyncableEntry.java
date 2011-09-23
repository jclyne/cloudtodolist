package com.oci.example.todolist.client;


import android.content.ContentValues;

public class SyncableEntry {
    private final boolean deleted;
    private final ContentValues values;

    public SyncableEntry(boolean deleted, ContentValues values) {
        this.deleted = deleted;
        this.values = values;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public ContentValues getValues() {
        return values;
    }
}
