package com.oci.example.todolist.client;


import android.content.ContentValues;
import android.os.Bundle;
import android.util.Log;
import com.oci.example.todolist.provider.TodoList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

public class TodoListClient {
    private static final String TAG = "TodoListClient";

    private final RestClient client;
    private static final String ENTRIES_PATH="/todolist/entries";
    private static final String ENTRY_ID="id";
    private static final String ENTRY_TITLE="title";
    private static final String ENTRY_NOTES="notes";
    private static final String ENTRY_COMPLETE="complete";
    private static final String ENTRY_CREATED="created";
    private static final String ENTRY_MODIFIED="modified";

    public TodoListClient(RestClient client) {
        this.client = client;
    }

    public Bundle getEntries(){

        try {
            String responseContent = client.Get(ENTRIES_PATH,null,null,RestClient.ContentType.JSON);
            if (responseContent != null) {
                Bundle result=new Bundle();
                JSONArray entryList = new JSONArray(responseContent);
                for (int idx=0;idx<entryList.length();idx++){
                    JSONObject entry = entryList.getJSONObject(idx);
                    ContentValues entryValues = new ContentValues();

                    int id = entry.getInt(ENTRY_ID);
                    entryValues.put(TodoList.Entries.ID,id);
                    entryValues.put(TodoList.Entries.COMPLETE,entry.getInt(ENTRY_COMPLETE));
                    String title= entry.optString(ENTRY_TITLE);
                    if (!title.equals(""))
                        entryValues.put(TodoList.Entries.TITLE,title);
                    String notes= entry.optString(ENTRY_NOTES);
                    if (!title.equals(""))
                        entryValues.put(TodoList.Entries.NOTES,notes);

                    entryValues.put(TodoList.Entries.CREATED,(long)(entry.getDouble(ENTRY_CREATED)*1000));
                    entryValues.put(TodoList.Entries.MODIFIED,(long)(entry.getDouble(ENTRY_MODIFIED)*1000));

                    result.putParcelable(Integer.toString(id),entryValues);
                }
                return result;
            }

        } catch (JSONException e) {
            Log.e(TAG,"Invalid response: "+e.toString());
        }

        return null;
    }
}
