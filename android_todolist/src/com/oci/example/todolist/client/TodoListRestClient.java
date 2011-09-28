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

@SuppressWarnings({"NullableProblems"})
public final class TodoListRestClient {
    private static final String TAG = "TodoListRestClient";

    private final HttpRestClient client;
    private static final String ENTRIES_PATH = "/todolist/entries";

    public static final String ENTRY_ID = "id";
    public static final String ENTRY_TITLE = "title";
    public static final String ENTRY_NOTES = "notes";
    public static final String ENTRY_COMPLETE = "complete";
    public static final String ENTRY_DELETED = "deleted";
    public static final String ENTRY_CREATED = "created";
    public static final String ENTRY_MODIFIED = "modified";
    public static final String ENTRY_LIST_TIMESTAMP = "timestamp";
    public static final String ENTRY_LIST_ENTRIES = "entries";



    public class Response {
        public static final int SUCCESS_OK = 200;
        public static final int SUCCESS_ADDED = 201;
        public static final int FAILED_BAD_REQUEST = 400;
        public static final int FAILED_INVALID_RESOURCE = 410;

        private final HttpRestClient.Response response;

        public Response(HttpRestClient.Response response) {
            this.response = response;
        }

        public HttpRestClient.Response getResponse() {
            return response;
        }
    }

    public class EntryObjectResponse extends Response {
        private final JSONObject entryObject;

        public EntryObjectResponse(HttpRestClient.Response response, JSONObject entryObject) {
            super(response);
            this.entryObject = entryObject;
        }

        public JSONObject getEntryObject() {
            return entryObject;
        }
    }

    public class EntryListResponse extends Response {
        private final double timestamp;
        private final List<JSONObject> entryList = new ArrayList<JSONObject>();

        public EntryListResponse(HttpRestClient.Response response, JSONObject entryList)
                  throws JSONException {
            super(response);
            this.timestamp = entryList.getDouble(ENTRY_LIST_TIMESTAMP);
            JSONArray entryArray = entryList.getJSONArray(ENTRY_LIST_ENTRIES);
            for (int idx=0;idx < entryArray.length();idx++)
                this.entryList.add(entryArray.getJSONObject(idx));
        }

        public double getTimestamp() {
            return timestamp;
        }

        public List<JSONObject> getEntryList() {
            return entryList;
        }
    }

    public TodoListRestClient(HttpRestClient client) {
        this.client = client;
    }

    private static String buildQueryString(String[] keys, ContentValues values) {
        String queryString = "";
        if (values != null) {
            for (String key : keys) {
                if (values.containsKey(key)) {
                    if (!queryString.isEmpty()) queryString += ";";
                    queryString += key + "=" + values.getAsString(key);
                }
            }
        }
        return (queryString.isEmpty() ? null : queryString);
    }

    public EntryObjectResponse postEntry(ContentValues values)
            throws IOException, URISyntaxException, JSONException {

        String uri = ENTRIES_PATH;
        String[] validParams = {ENTRY_TITLE, ENTRY_NOTES, ENTRY_COMPLETE};

        HttpRestClient.Response response = client.Post(uri,
                buildQueryString(validParams, values),
                HttpRestClient.ContentType.JSON);
        JSONObject resonseObject = null;
        if (response.succeeded()) {
            resonseObject = new JSONObject(response.getContent());

            Log.i(TAG, "post entry: " + resonseObject.getInt(ENTRY_ID));
        } else {
            Log.e(TAG, "post failed: " + response.getStatusCode() + "- " + response.getContent());
        }

        return new EntryObjectResponse(response, resonseObject);
    }

    public EntryObjectResponse putEntry(int id, ContentValues values)
            throws IOException, URISyntaxException, JSONException {

        String uri = ENTRIES_PATH + "/" + id;
        String[] validParams = {ENTRY_TITLE, ENTRY_NOTES, ENTRY_COMPLETE};

        HttpRestClient.Response response = client.Put(uri,
                buildQueryString(validParams, values),
                HttpRestClient.ContentType.JSON);
        JSONObject resonseObject = null;
        if (response.succeeded()) {
            resonseObject = new JSONObject(response.getContent());

            Log.i(TAG, "post entry: " + resonseObject.getInt(ENTRY_ID));
        } else {
            Log.e(TAG, "post failed: " + response.getStatusCode() + "- " + response.getContent());
        }

        return new EntryObjectResponse(response, resonseObject);
    }

    public Response deleteEntry(int id) throws IOException, URISyntaxException {
        String uri = ENTRIES_PATH + "/" + id;

        HttpRestClient.Response response = client.Delete(uri, null, HttpRestClient.ContentType.JSON);
        if (response.succeeded()) {
            Log.i(TAG, "deleted entry: " + id);
        } else {
            Log.e(TAG, "delete failed: " + response.getStatusCode() + "- " + response.getContent());
        }


        return new Response(response);
    }

    public EntryListResponse getEntries()
            throws IOException, URISyntaxException, JSONException {
        return getEntries(null);

    }
    public EntryListResponse getEntries(Double modified)
            throws IOException, URISyntaxException, JSONException {

        String queryString =null;
        if (modified != null)
            queryString=String.format("%s=%f",ENTRY_MODIFIED,modified);

        HttpRestClient.Response response = client.Get(ENTRIES_PATH,queryString,HttpRestClient.ContentType.JSON);
        if (response.succeeded()) {
            EntryListResponse resp = new EntryListResponse(response, new JSONObject(response.getContent()));
            Log.i(TAG, "getEntries retrieved " + resp.getEntryList().size()  + " entries");
            return resp;
        } else {
            Log.e(TAG, "getEntries failed: " + response.getStatusCode() + "- " + response.getContent());
        }
        return new EntryListResponse(response, null);
    }
}
