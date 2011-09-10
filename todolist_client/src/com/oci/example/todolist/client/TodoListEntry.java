package com.oci.example.todolist.client;

import java.util.Comparator;

/**
 */

class TodoListEntry {
    private final int id;
    private String title;
    private String notes = "";
    private boolean complete = false;


    TodoListEntry(int id, String title, String notes, boolean complete) {
        this.id = id;
        this.title = title;
        this.notes = notes;
        this.complete = complete;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TodoListEntry that = (TodoListEntry) o;

        return id == that.id;

    }

    @Override
    public int hashCode() {
        return id;
    }

    static class CompareId implements Comparator<TodoListEntry> {

        public int compare(TodoListEntry e1, TodoListEntry e2) {
            return e1.getId() - e2.getId();
        }
    }
}


