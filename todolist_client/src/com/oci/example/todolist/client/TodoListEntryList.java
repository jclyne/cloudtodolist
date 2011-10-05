package com.oci.example.todolist.client;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;

class TodoListEntryList extends JavaScriptObject {
    protected TodoListEntryList() {
    }

    public final native double getTimeStamp() /*-{
        return this.timestamp;
    }-*/;

    public final native JsArray<TodoListEntry> getEntries() /*-{
        return this.entries;
    }-*/;

}


