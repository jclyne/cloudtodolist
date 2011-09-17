package com.oci.example.todolist;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.TextView;

public class TodoListCursorAdapter extends CursorAdapter {

    final Context context;
    public TodoListCursorAdapter(Context context, Cursor c){
        super(context,c,true);
        this.context = context;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(R.layout.todolist_entry,parent,false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        final int idColumn = cursor.getColumnIndex(TodoList.Entries._ID);
        final int completeColumn = cursor.getColumnIndex(TodoList.Entries.COLUMN_NAME_COMPLETE);
        final int titleColumn = cursor.getColumnIndex(TodoList.Entries.COLUMN_NAME_TITLE);

        final int id = cursor.getInt(idColumn);
        final boolean complete = (cursor.getInt(completeColumn)==1);
        final String title = cursor.getString(titleColumn);

        final CheckBox completeCheckBox = (CheckBox) view.findViewById(R.id.entry_complete);
        final TextView titleTextView = (TextView) view.findViewById(R.id.entry_title);

        completeCheckBox.setTag(id);

        if (completeCheckBox.isChecked() != complete)
            completeCheckBox.setChecked(complete);

        completeCheckBox.setOnClickListener( new View.OnClickListener() {
            public void onClick(View view) {
                CheckBox completeCheckBox = (CheckBox)view;
                final Integer tag = (Integer)completeCheckBox.getTag();

                final String  where = TodoList.Entries._ID + " = " + "?";
                final String[] whereArgs = {tag.toString()};

                ContentValues values = new ContentValues();
                values.put(TodoList.Entries.COLUMN_NAME_COMPLETE, (completeCheckBox.isChecked() ? 1 : 0) );
                TodoListCursorAdapter.this.context.getContentResolver().update(
                        TodoList.Entries.CONTENT_URI, values, where, whereArgs);
            }
        });

        titleTextView.setText(title);

    }

}
