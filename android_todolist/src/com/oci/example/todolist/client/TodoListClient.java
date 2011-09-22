package com.oci.example.todolist.client;


import android.content.ContentValues;
import android.os.Bundle;
import android.util.Log;
import com.oci.example.todolist.provider.TodoListProvider;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;

public final class TodoListClient implements SyncableClient {
    private static final String TAG = "TodoListClient";

    private final RestClient client;
    private static final String ENTRIES_PATH = "/todolist/entries";

    private static final String ENTRY_ID = "id";
    private static final String ENTRY_TITLE = "title";
    private static final String ENTRY_NOTES = "notes";
    private static final String ENTRY_COMPLETE = "complete";
    private static final String ENTRY_DELETED = "deleted";
    private static final String ENTRY_CREATED = "created";
    private static final String ENTRY_MODIFIED = "modified";

    public TodoListClient(RestClient client) {
        this.client = client;
    }

    @Override
    public ContentValues insert(ContentValues entry) {

        String uri = ENTRIES_PATH;

        String[] validQueryValues = {
                TodoListProvider.Schema.Entries.TITLE,
                TodoListProvider.Schema.Entries.NOTES,
                TodoListProvider.Schema.Entries.COMPLETE};

        String uriQueryString = buildQueryString(entry, validQueryValues);

        try {
            RestClient.Response response = client.Post(uri, uriQueryString, RestClient.ContentType.JSON);
            if (response.succeeded()) {
                JSONObject jsonEntry = new JSONObject(response.getContent());
                ContentValues result = parseJsonEntry(jsonEntry);

                Log.i(TAG, "inserted entry: " + result.getAsInteger(TodoListProvider.Schema.Entries.ID));
                return result;
            } else {
                Log.e(TAG, "insert failed: " + response.getStatusCode() + "- " + response.getContent());
            }

        } catch (IOException e) {
            Log.e(TAG, "insert network error: " + e.toString());

        } catch (URISyntaxException e) {
            Log.e(TAG, "insert invalid URI: " + e.toString());

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "insert error: " + e.toString());

        } catch (JSONException e) {
            Log.e(TAG, "insert unexpected response: " + e.toString());
        }

        return null;
    }

    @Override
    public ContentValues update(ContentValues entry) {

        int id = entry.getAsInteger(TodoListProvider.Schema.Entries.ID);
        String uri = ENTRIES_PATH + "/" + entry.getAsInteger(TodoListProvider.Schema.Entries.ID);
        String[] validQueryValues = {
                TodoListProvider.Schema.Entries.TITLE,
                TodoListProvider.Schema.Entries.NOTES,
                TodoListProvider.Schema.Entries.COMPLETE};

        String uriQueryString = buildQueryString(entry, validQueryValues);

        try {
            RestClient.Response response = client.Put(uri, uriQueryString, RestClient.ContentType.JSON);
            if (response.succeeded()) {
                JSONObject jsonEntry = new JSONObject(response.getContent());
                ContentValues result = parseJsonEntry(jsonEntry);

                Log.i(TAG, "updated entry: " + id);
                return result;
            } else {
                Log.e(TAG, "update failed: " + response.getStatusCode() + "- " + response.getContent());
            }

        } catch (IOException e) {
            Log.e(TAG, "update network error: " + e.toString());

        } catch (URISyntaxException e) {
            Log.e(TAG, "update invalid URI: " + e.toString());

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "update error: " + e.toString());

        } catch (JSONException e) {
            Log.e(TAG, "update unexpected response: " + e.toString());
        }

        return null;
    }

    @Override
    public boolean delete(ContentValues entry) {
        int id = entry.getAsInteger(TodoListProvider.Schema.Entries.ID);
        String uri = ENTRIES_PATH + "/" + id;

        try {
            RestClient.Response response = client.Delete(uri, null, RestClient.ContentType.JSON);
            if (response.succeeded()) {
                Log.i(TAG, "deleted entry: " + id);
                return true;
            } else {
                Log.e(TAG, "delete failed: " + response.getStatusCode() + "- " + response.getContent());
            }

        } catch (IOException e) {
            Log.e(TAG, "delete network error: " + e.toString());

        } catch (URISyntaxException e) {
            Log.e(TAG, "delete invalid URI: " + e.toString());

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "delete error: " + e.toString());

        }

        return false;
    }


    @Override
    public Bundle getAll() {
        try {
            RestClient.Response response = client.Get(ENTRIES_PATH, null, RestClient.ContentType.JSON);
            if (response.succeeded()) {
                Bundle result = new Bundle();
                JSONObject entryList = new JSONObject(response.getContent());
                double timestamp = entryList.getDouble("timestamp");
                JSONArray entries = entryList.getJSONArray("entries");
                for (int idx = 0; idx < entryList.length(); idx++) {
                    ContentValues entryValues = parseJsonEntry(entries.getJSONObject(idx));
                    result.putParcelable(
                            entryValues.getAsString(TodoListProvider.Schema.Entries.ID),
                            entryValues);
                }
                Log.i(TAG, "getAll retrieved " + Integer.toString(result.size()) + " entries");
                return result;
            } else {
                Log.e(TAG, "getAll failed: " + response.getStatusCode() + "- " + response.getContent());
            }


        } catch (IOException e) {
            Log.e(TAG, "getAll network error: " + e.toString());

        } catch (URISyntaxException e) {
            Log.e(TAG, "getAll invalid URI: " + e.toString());

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "getAll error: " + e.toString());

        } catch (JSONException e) {
            Log.e(TAG, "getAll unexpected response: " + e.toString());
        }


        return null;
    }

    private static String buildQueryString(ContentValues entry, String[] values) {
        String query = "";
        for (String value : values) {
            if (entry.containsKey(value)) {
                if (query.length() > 0) query += ";";
                query += value + "=" + entry.getAsString(value);
            }
        }
        return query;
    }

    private static ContentValues parseJsonEntry(JSONObject entry) throws JSONException {
        ContentValues entryValues = new ContentValues();

        int id = entry.getInt(ENTRY_ID);
        entryValues.put(TodoListProvider.Schema.Entries.ID, id);
        entryValues.put(TodoListProvider.Schema.Entries.COMPLETE, entry.getInt(ENTRY_COMPLETE));
        entryValues.put(ENTRY_DELETED, entry.getInt(ENTRY_COMPLETE));
        String title = entry.optString(ENTRY_TITLE);
        if (!title.equals(""))
            entryValues.put(TodoListProvider.Schema.Entries.TITLE, title);
        String notes = entry.optString(ENTRY_NOTES);
        if (!title.equals(""))
            entryValues.put(TodoListProvider.Schema.Entries.NOTES, notes);

        entryValues.put(TodoListProvider.Schema.Entries.CREATED, (long) (entry.getDouble(ENTRY_CREATED) * 1000));
        entryValues.put(TodoListProvider.Schema.Entries.MODIFIED, (long) (entry.getDouble(ENTRY_MODIFIED) * 1000));

        return entryValues;

    }

}
