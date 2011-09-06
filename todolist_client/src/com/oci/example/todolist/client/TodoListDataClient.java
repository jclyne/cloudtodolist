package com.oci.example.todolist.client;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.http.client.*;

import java.util.*;
import java.lang.String;
import com.google.gwt.user.client.Window;


/*
 */

interface ClientRequestCallback {
  void onSuccess();
  void onError(String errStr);
};

class TodoListDataClient {

    private static final String TODOLIST_BASE_URL = "http://"+ Window.Location.getHost()+"/todolist/api/";
    private static final String GET_ENTRY_URL = TODOLIST_BASE_URL + "getentry?";
    private static final String SET_ENTRY_URL = TODOLIST_BASE_URL + "setentry?";
    private static final String DEL_ENTRY_URL = TODOLIST_BASE_URL + "delentry?";

    /**
     * The list of data to display.
     */
    private static Map<Integer, TodoListEntry> entryDataCache = null;

    private final ClientRequestCallback callback;

    public TodoListDataClient(ClientRequestCallback callback) {
        entryDataCache = new HashMap<Integer, TodoListEntry>();
        this.callback=callback;
    }

    private native JsArray<TodoListEntry> parseGetEntryResponse(String json) /*-{
        return eval(json);
    }-*/;

    public void refreshTodoListEntries() {
        String url = URL.encode(GET_ENTRY_URL);
        RequestBuilder builder = new RequestBuilder(RequestBuilder.GET, url);

        try {
            builder.sendRequest(null, new RequestCallback() {
                public void onError(Request request, Throwable exception) {
                    callback.onError("Server Error");
                }

                public void onResponseReceived(Request request, Response response) {
                    if (response.getStatusCode() == 200) {
                        JsArray<TodoListEntry> entries = parseGetEntryResponse(response.getText());
                        entryDataCache.clear();
                        for (int i = 0; i < entries.length(); i++) {
                            TodoListEntry entry = entries.get(i);
                            entryDataCache.put(entry.getId(), entry);
                        }
                        callback.onSuccess();
                    } else {
                        callback.onError("Server Error (" + response.getStatusText()+ ")");
                    }
                }
            });
        } catch (RequestException e) {
            callback.onError("Server Error");
        }
    }

    public final List<TodoListEntry> getCurrentTodoListEntries() {
        List<TodoListEntry> list = new ArrayList<TodoListEntry>(entryDataCache.values());
        Collections.sort(list, new TodoListEntryComparator());
        return list;
    }

     public final void setEntryComplete(int id, boolean complete) {
        String urlString=SET_ENTRY_URL
                            +"id="+Integer.toString(id)
                            +";complete="+Integer.toString(complete?1:0);
        String url = URL.encode(urlString);
        RequestBuilder builder = new RequestBuilder(RequestBuilder.PUT, url);

        try {
            builder.sendRequest(null, new RequestCallback() {
                public void onError(Request request, Throwable exception) {
                    callback.onError("Server Error");
                }

                public void onResponseReceived(Request request, Response response) {
                    if (response.getStatusCode() == 200) {
                        JsArray<TodoListEntry> entries = parseGetEntryResponse(response.getText());
                        for (int i = 0; i < entries.length(); i++) {
                            TodoListEntry entry = entries.get(i);
                            entryDataCache.put(entry.getId(), entry);
                        }
                        callback.onSuccess();
                    } else {
                        callback.onError("Server Error (" + response.getStatusText()+ ")");
                    }
                }
            });
        } catch (RequestException e) {
            callback.onError("Server Error");
        }
    }

}
