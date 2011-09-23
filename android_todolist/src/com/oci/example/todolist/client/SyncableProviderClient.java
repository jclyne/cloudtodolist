package com.oci.example.todolist.client;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;

import java.util.List;
import java.util.Map;



public interface SyncableProviderClient {

    public class InvalidRequest extends Exception {
        public InvalidRequest(String detailMessage) {
            super(detailMessage);
    }
}

    public class InvalidResponse extends Exception {
        public InvalidResponse(String detailMessage) {
            super(detailMessage);
        }
    }

    public class NetworkError extends Exception {
        public NetworkError(String detailMessage) {
            super(detailMessage);
        }
    }

    public SyncableEntry insert(final ContentValues entry)
            throws NetworkError, InvalidResponse, InvalidRequest;

    public SyncableEntry update(Object key, final ContentValues entry)
            throws NetworkError, InvalidRequest, InvalidResponse;

    public boolean delete(Object key)
            throws NetworkError, InvalidRequest;

    public SyncableEntryList getEntries(Map<String,String> args)
            throws NetworkError, InvalidRequest, InvalidResponse;
}


