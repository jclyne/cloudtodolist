package com.oci.example.todolist.client;


import android.content.ContentValues;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.util.Log;
import java.util.Map;
import java.util.TreeMap;

public class TodoListClient {
    private static final String TAG = "TodoListClient";

    private final RestClient client;
    private final String ENTRIES_PATH="/todolist/entries";

    public TodoListClient(RestClient client) {
        this.client = client;
    }

    public Map<Integer,ContentValues> getEntries(){
        Map<Integer,ContentValues> result=new TreeMap<Integer,ContentValues>();

        try {
            String responseContent = client.Get(ENTRIES_PATH,"","");
            if (responseContent != null) {
                JSONArray json = new JSONArray(client.Get(ENTRIES_PATH,"",""));
            }

        } catch (JSONException e) {
            Log.e(TAG,e.toString());
        }

        return result;
    }
}
