package com.oci.example.cloudtodolist.client;

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
        return this.complete == 1;
    }-*/;

    public final native boolean getDeleted() /*-{
        return this.deleted == 1;
    }-*/;

    public final native double getCreated() /*-{
        return this.created;
    }-*/;

    public final native double getModified() /*-{
        return this.modified;
    }-*/;


    static class CompareCreated implements Comparator<TodoListEntry> {

        public int compare(TodoListEntry e1, TodoListEntry e2) {
            return (int)(e1.getCreated() - e2.getCreated());
        }
    }
}


