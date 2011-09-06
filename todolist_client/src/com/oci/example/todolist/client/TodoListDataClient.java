package com.oci.example.todolist.client;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayNumber;
import com.google.gwt.http.client.*;
import com.google.gwt.user.client.Window;

import java.util.*;


/*
 */

interface ClientRequestCallback {

    void onSuccess();

    void onError(String errStr);

}

class TodoListDataClient {

    private static final String TODOLIST_BASE_URL = "http://" + Window.Location.getHost() + "/todolist/api/";
    private static final String GET_ENTRY_URL = TODOLIST_BASE_URL + "getentry?";
    private static final String SET_ENTRY_URL = TODOLIST_BASE_URL + "setentry?";
    private static final String DEL_ENTRY_URL = TODOLIST_BASE_URL + "delentry?";

    /**
     * The list of data to display.
     */
    private static Map<Integer, TodoListEntry> entryDataCache = null;

    private final ClientRequestCallback clientCallback;

    public TodoListDataClient(ClientRequestCallback clientCallback) {
        entryDataCache = new HashMap<Integer, TodoListEntry>();
        this.clientCallback = clientCallback;
    }

    public List<TodoListEntry> getCurrentTodoListEntries() {
        List<TodoListEntry> list = new ArrayList<TodoListEntry>(entryDataCache.values());
        Collections.sort(list, new TodoListEntryComparator());
        return list;
    }

    public void refreshTodoListEntries() {
        makeEntryGetRequest(GET_ENTRY_URL,new GetRequestCallback());
    }

    public void createNewEntry(String title) {
        makeEntryPutRequest(SET_ENTRY_URL
                + "title=" + title,
                new SetRequestCallback());
    }

    public void createNewEntry(String title,boolean complete) {
        makeEntryPutRequest(SET_ENTRY_URL
                + "title=" + title
                + ";complete=" + Integer.toString(complete ? 1 : 0),
                new SetRequestCallback());
    }

    public void createNewEntry(String title,boolean complete,String notes) {
        makeEntryPutRequest(SET_ENTRY_URL
                + "title=" + title
                + ";complete=" + Integer.toString(complete ? 1 : 0)
                + ";notes=" + notes,
                new SetRequestCallback());
    }

    public void setEntryComplete(int id, boolean complete) {
        makeEntryPutRequest(SET_ENTRY_URL
                + "id=" + Integer.toString(id)
                + ";complete=" + Integer.toString(complete ? 1 : 0),
                new SetRequestCallback());
    }

    public void setEntryTitle(int id, String title) {
        makeEntryPutRequest(SET_ENTRY_URL
                + "id=" + Integer.toString(id)
                + ";title=" + title,
                new SetRequestCallback());
    }

    public void setEntryNotes(int id, String notes) {
        makeEntryPutRequest(SET_ENTRY_URL
                + "id=" + Integer.toString(id)
                + ";notes=" + notes,
                new SetRequestCallback());
    }

    public void clearCompletedEntries() {
        String urlString = DEL_ENTRY_URL;
        int count = 0;
        for (Map.Entry<Integer, TodoListEntry> mapEntry : entryDataCache.entrySet()) {
            TodoListEntry entry = mapEntry.getValue();
            if (entry.isComplete()) {
                if (count++ == 0) {
                    urlString += "id=" + Integer.toString(entry.getId());
                } else {
                    urlString += "+" + Integer.toString(entry.getId());
                }
            }
        }

        if (count > 0) {
            makeEntryPutRequest(urlString, new DeleteRequestCallback());
        }
    }

    private void makeEntryGetRequest(String urlString, RequestCallback callback) {
        String url = URL.encode(urlString);
        RequestBuilder builder = new RequestBuilder(RequestBuilder.GET, url);

        try {
            builder.sendRequest(null, callback);
        } catch (RequestException e) {
            clientCallback.onError("Server Error");
        }
    }

    private void makeEntryPutRequest(String urlString, RequestCallback callback) {
        String url = URL.encode(urlString);
        RequestBuilder builder = new RequestBuilder(RequestBuilder.PUT, url);

        try {
            builder.sendRequest(null, callback);
        } catch (RequestException e) {
            clientCallback.onError("Server Error");
        }
    }


    private class GetRequestCallback implements RequestCallback {
        public void onError(Request request, Throwable exception) {
            clientCallback.onError("Server Error");
        }

        private native JsArray<TodoListEntry> parseGetEntryResponse(String json) /*-{
            return eval(json);
        }-*/;

        public void onResponseReceived(Request request, Response response) {
            if (response.getStatusCode() == 200) {
                JsArray<TodoListEntry> entries = parseGetEntryResponse(response.getText());
                entryDataCache.clear();
                for (int i = 0; i < entries.length(); i++) {
                    TodoListEntry entry = entries.get(i);
                    entryDataCache.put(entry.getId(), entry);
                }
                clientCallback.onSuccess();
            } else {
                clientCallback.onError("Server Error (" + response.getStatusText() + ")");
            }
        }
    }

    private class SetRequestCallback implements RequestCallback {
        public void onError(Request request, Throwable exception) {
            clientCallback.onError("Server Error");
        }

        private native JsArray<TodoListEntry> parseSetEntryResponse(String json) /*-{
            return eval(json);
        }-*/;

        public void onResponseReceived(Request request, Response response) {
            if (response.getStatusCode() == 200) {
                JsArray<TodoListEntry> entries = parseSetEntryResponse(response.getText());
                for (int i = 0; i < entries.length(); i++) {
                    TodoListEntry entry = entries.get(i);
                    entryDataCache.put(entry.getId(), entry);
                }
                clientCallback.onSuccess();
            } else {
                clientCallback.onError("Server Error (" + response.getStatusText() + ")");
            }
        }
    }

    private class DeleteRequestCallback implements RequestCallback {
        public void onError(Request request, Throwable exception) {
            clientCallback.onError("Server Error");
        }

        private native JsArrayNumber parseDeleteEntryResponse(String json) /*-{
            return eval(json);
        }-*/;

        public void onResponseReceived(Request request, Response response) {
            if (response.getStatusCode() == 200) {
                JsArrayNumber deletedIdList = parseDeleteEntryResponse(response.getText());
                for (int i = 0; i < deletedIdList.length(); i++) {
                    entryDataCache.remove((int) deletedIdList.get(i));
                }
                clientCallback.onSuccess();
            } else {
                clientCallback.onError("Server Error (" + response.getStatusText() + ")");
            }
        }
    }
}


