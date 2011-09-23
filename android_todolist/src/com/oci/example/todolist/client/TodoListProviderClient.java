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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TodoListProviderClient implements SyncableProviderClient {
    private static final String TAG = "TodoListProviderClient";


    private final RestClient client;
    private static final String ENTRIES_PATH = "/todolist/entries";

    private static final String ENTRY_ID = "id";
    private static final String ENTRY_TITLE = "title";
    private static final String ENTRY_NOTES = "notes";
    private static final String ENTRY_COMPLETE = "complete";
    private static final String ENTRY_DELETED = "deleted";
    private static final String ENTRY_CREATED = "created";
    private static final String ENTRY_MODIFIED = "modified";

    public TodoListProviderClient(RestClient client) {
        this.client = client;
    }

    @Override
    public SyncableEntry insert(ContentValues entry) throws NetworkError, InvalidResponse, InvalidRequest {

        String uri = ENTRIES_PATH;

        String[] validQueryParams = {
                TodoListProvider.Schema.Entries.TITLE,
                TodoListProvider.Schema.Entries.NOTES,
                TodoListProvider.Schema.Entries.COMPLETE};

        String uriQueryString = buildQueryString(validQueryParams, entry);

        try {
            RestClient.Response response = client.Post(uri, uriQueryString, RestClient.ContentType.JSON);
            if (response.succeeded()) {
                JSONObject jsonEntry = new JSONObject(response.getContent());
                SyncableEntry result = parseJsonEntry(jsonEntry);

                Log.i(TAG, "inserted entry: " + result.getValues().getAsInteger(TodoListProvider.Schema.Entries.ID));
                return result;
            } else {
                Log.e(TAG, "insert failed: " + response.getStatusCode() + "- " + response.getContent());
            }

        } catch (IOException e) {
            Log.e(TAG, "insert network error: " + e.toString());
            throw new NetworkError(e.getMessage());

        } catch (URISyntaxException e) {
            Log.e(TAG, "insert invalid URI: " + e.toString());
            throw new InvalidRequest(e.getMessage());

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "insert error: " + e.toString());
            throw new InvalidRequest(e.getMessage());

        } catch (JSONException e) {
            Log.e(TAG, "insert unexpected response: " + e.toString());
            throw new InvalidResponse(e.getMessage());
        }

        return null;
    }

    @Override
    public SyncableEntry update(Object id, ContentValues entry) throws NetworkError, InvalidRequest, InvalidResponse {

        String uri = ENTRIES_PATH + "/" + id;
        String[] validQueryParams = {
                TodoListProvider.Schema.Entries.TITLE,
                TodoListProvider.Schema.Entries.NOTES,
                TodoListProvider.Schema.Entries.COMPLETE};

        String uriQueryString = buildQueryString(validQueryParams, entry);

        try {
            RestClient.Response response = client.Put(uri, uriQueryString, RestClient.ContentType.JSON);
            if (response.succeeded()) {
                JSONObject jsonEntry = new JSONObject(response.getContent());
                SyncableEntry result = parseJsonEntry(jsonEntry);

                Log.i(TAG, "updated entry: " + id);
                return result;
            } else {
                Log.e(TAG, "update failed: " + response.getStatusCode() + "- " + response.getContent());
            }

         } catch (IOException e) {
            Log.e(TAG, "update network error: " + e.toString());
            throw new NetworkError(e.getMessage());

        } catch (URISyntaxException e) {
            Log.e(TAG, "update invalid URI: " + e.toString());
            throw new InvalidRequest(e.getMessage());

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "update error: " + e.toString());
            throw new InvalidRequest(e.getMessage());

        } catch (JSONException e) {
            Log.e(TAG, "update unexpected response: " + e.toString());
            throw new InvalidResponse(e.getMessage());
        }

        return null;
    }

    @Override
    public boolean delete(Object id) throws NetworkError, InvalidRequest {
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
            throw new NetworkError(e.getMessage());

        } catch (URISyntaxException e) {
            Log.e(TAG, "delete invalid URI: " + e.toString());
            throw new InvalidRequest(e.getMessage());

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "delete error: " + e.toString());
            throw new InvalidRequest(e.getMessage());
        }

        return false;
    }

    @Override
    public SyncableEntryList getEntries(Map<String,String> args) throws NetworkError, InvalidRequest, InvalidResponse {

        String[] validQueryParams = {
                TodoListProvider.Schema.Entries.ID,
                TodoListProvider.Schema.Entries.MODIFIED};

        try {
            RestClient.Response response = client.Get(
                    ENTRIES_PATH,
                    buildQueryString(validQueryParams,args),
                    RestClient.ContentType.JSON);
            if (response.succeeded()) {
                List<SyncableEntry> entries = new ArrayList<SyncableEntry>();
                Bundle metaData= new Bundle();

                JSONObject jsonEntryList = new JSONObject(response.getContent());
                metaData.putDouble("timestamp",jsonEntryList.getDouble("timestamp"));
                JSONArray jsonEntries = jsonEntryList.getJSONArray("entries");
                for (int idx = 0; idx < jsonEntries.length(); idx++)
                    entries.add(parseJsonEntry(jsonEntries.getJSONObject(idx)));

                Log.i(TAG, "getEntries retrieved " + Integer.toString(entries.size()) + " entries");

                return new SyncableEntryList(entries, metaData);
            } else {
                Log.e(TAG, "getEntries failed: " + response.getStatusCode() + "- " + response.getContent());
            }


       } catch (IOException e) {
            Log.e(TAG, "getEntries network error: " + e.toString());
            throw new NetworkError(e.getMessage());

        } catch (URISyntaxException e) {
            Log.e(TAG, "getEntries invalid URI: " + e.toString());
            throw new InvalidRequest(e.getMessage());

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "getEntries error: " + e.toString());
            throw new InvalidRequest(e.getMessage());

        } catch (JSONException e) {
            Log.e(TAG, "getEntries unexpected response: " + e.toString());
            throw new InvalidResponse(e.getMessage());
        }


        return null;
    }

    private String buildQueryString(String[] keys, Map<String,String> values){
        String queryString = "";
        if (values != null){
            for (String key : keys) {
                if (values.containsKey(key)) {
                    if (!queryString.isEmpty()) queryString += ";";
                    queryString += key + "=" + values.get(key);
                }
            }
        }
        return (queryString.isEmpty() ? null : queryString);
    }

    private static String buildQueryString(String[] keys, ContentValues values) {
        String queryString = "";
        if (values != null){
            for (String key : keys) {
                if (values.containsKey(key)) {
                    if (!queryString.isEmpty()) queryString += ";";
                    queryString += key + "=" + values.getAsString(key);
                }
            }
        }
        return (queryString.isEmpty() ? null : queryString);
    }

    private static SyncableEntry parseJsonEntry(JSONObject entry) throws JSONException {
        ContentValues entryValues = new ContentValues();

        entryValues.put(TodoListProvider.Schema.Entries.ID, entry.getInt(ENTRY_ID));
        entryValues.put(TodoListProvider.Schema.Entries.COMPLETE, entry.getInt(ENTRY_COMPLETE));
        String title = entry.optString(ENTRY_TITLE);
        if (!title.equals(""))
            entryValues.put(TodoListProvider.Schema.Entries.TITLE, title);
        String notes = entry.optString(ENTRY_NOTES);
        if (!title.equals(""))
            entryValues.put(TodoListProvider.Schema.Entries.NOTES, notes);

        entryValues.put(TodoListProvider.Schema.Entries.CREATED, (long) (entry.getDouble(ENTRY_CREATED) * 1000));
        entryValues.put(TodoListProvider.Schema.Entries.MODIFIED, (long) (entry.getDouble(ENTRY_MODIFIED) * 1000));

        return new SyncableEntry(entry.getInt(ENTRY_DELETED)==1,entryValues);

    }

}
