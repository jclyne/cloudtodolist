package com.oci.example.todolist.client;


import android.content.ContentValues;
import android.os.Bundle;
import android.util.Log;
import com.oci.example.todolist.provider.TodoListSchema;
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
    private static final String ENTRY_CREATED = "created";
    private static final String ENTRY_MODIFIED = "modified";

    public TodoListClient(RestClient client) {
        this.client = client;
    }

    @Override
    public ContentValues insert(ContentValues entry) {

        String uri = ENTRIES_PATH;

        String[] validQueryValues = {
                TodoListSchema.Entries.TITLE,
                TodoListSchema.Entries.NOTES,
                TodoListSchema.Entries.COMPLETE};

        String uriQueryString = buildQueryString(entry, validQueryValues);

        try {
            RestClient.Response response = client.Post(uri, uriQueryString, RestClient.ContentType.JSON);
            if (response.succeeded()) {
                JSONObject jsonEntry = new JSONObject(response.getContent());
                ContentValues result = parseJsonEntry(jsonEntry);

                Log.i(TAG, "inserted entry: " + result.getAsInteger(TodoListSchema.Entries.ID));
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

        int id = entry.getAsInteger(TodoListSchema.Entries.ID);
        String uri = ENTRIES_PATH + "/" + entry.getAsInteger(TodoListSchema.Entries.ID);
        String[] validQueryValues = {
                TodoListSchema.Entries.TITLE,
                TodoListSchema.Entries.NOTES,
                TodoListSchema.Entries.COMPLETE};

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
        int id = entry.getAsInteger(TodoListSchema.Entries.ID);
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
                JSONArray entryList = new JSONArray(response.getContent());
                for (int idx = 0; idx < entryList.length(); idx++) {
                    ContentValues entryValues = parseJsonEntry(entryList.getJSONObject(idx));
                    result.putParcelable(
                            entryValues.getAsString(TodoListSchema.Entries.ID),
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
        entryValues.put(TodoListSchema.Entries.ID, id);
        entryValues.put(TodoListSchema.Entries.COMPLETE, entry.getInt(ENTRY_COMPLETE));
        String title = entry.optString(ENTRY_TITLE);
        if (!title.equals(""))
            entryValues.put(TodoListSchema.Entries.TITLE, title);
        String notes = entry.optString(ENTRY_NOTES);
        if (!title.equals(""))
            entryValues.put(TodoListSchema.Entries.NOTES, notes);

        entryValues.put(TodoListSchema.Entries.CREATED, (long) (entry.getDouble(ENTRY_CREATED) * 1000));
        entryValues.put(TodoListSchema.Entries.PENDING_UPDATE, (long) (entry.getDouble(ENTRY_MODIFIED) * 1000));

        return entryValues;

    }

}
