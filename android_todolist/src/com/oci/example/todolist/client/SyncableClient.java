package com.oci.example.todolist.client;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;

public interface SyncableClient {


    public ContentValues insert(final ContentValues entry);

    public ContentValues update(final ContentValues entry);

    public boolean delete(final ContentValues entry);

    //public Bundle get(Cursor cursor);

    public Bundle getAll();
}
