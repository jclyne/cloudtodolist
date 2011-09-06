package com.oci.example.todolist.client;

import com.google.gwt.core.client.JavaScriptObject;
import java.util.Comparator;

/**
 */

class TodoListEntry extends JavaScriptObject {

    protected TodoListEntry() {
    }
    public final native int getId() /*-{
        return this.id;
    }-*/;

    public final native String getTitle() /*-{
        return this.title;
    }-*/;

    public final native String getNotes() /*-{
        return this.notes;
    }-*/;

    public final native boolean isComplete() /*-{
        return this.complete;
    }-*/;
}

class TodoListEntryComparator implements Comparator<TodoListEntry> {

     public int compare(TodoListEntry e1, TodoListEntry e2) {
        return e1.getId() - e2.getId();
     }
}
