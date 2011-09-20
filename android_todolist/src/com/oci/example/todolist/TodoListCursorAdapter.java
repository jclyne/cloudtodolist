package com.oci.example.todolist;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.oci.example.todolist.provider.TodoList;

public class TodoListCursorAdapter extends CursorAdapter {

    private final Context context;
    private boolean paused = false;

    public TodoListCursorAdapter(Context context, Cursor c) {
        super(context, c, true);
        this.context = context;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View entryView = LayoutInflater.from(context).inflate(R.layout.entry_layout, parent, false);
        return entryView;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        final int id = cursor.getInt(cursor.getColumnIndex(TodoList.Entries._ID));
        final boolean complete = (cursor.getInt(cursor.getColumnIndex(TodoList.Entries.COMPLETE)) == 1);
        final String title = cursor.getString(cursor.getColumnIndex(TodoList.Entries.TITLE));
        final String notes = cursor.getString(cursor.getColumnIndex(TodoList.Entries.NOTES));

        final CheckBox completeCheckBox = (CheckBox) view.findViewById(R.id.entry_complete);
        final TextView titleTextView = (TextView) view.findViewById(R.id.entry_title);
        final Button notesButton = (Button) view.findViewById(R.id.notes_button);

        completeCheckBox.setTag(id);

        if (completeCheckBox.isChecked() != complete)
            completeCheckBox.setChecked(complete);

        prepareEntryText(titleTextView,complete);

        completeCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prepareEntryText(titleTextView,checked);
            }
        });

        completeCheckBox.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                CheckBox completeCheckBox = (CheckBox) view;
                final Integer tag = (Integer) completeCheckBox.getTag();

                final String where = TodoList.Entries._ID + " = " + "?";
                final String[] whereArgs = {tag.toString()};

                ContentValues values = new ContentValues();
                values.put(TodoList.Entries.COMPLETE, (completeCheckBox.isChecked() ? 1 : 0));
                TodoListCursorAdapter.this.context.getContentResolver().update(
                        TodoList.Entries.CONTENT_URI, values, where, whereArgs);
            }
        });

        titleTextView.setText(title);
        notesButton.setTag(id);
        notesButton.setVisibility( (notes.equals("") ? View.GONE : View.VISIBLE) );
    }

    @Override
    protected void onContentChanged() {
        if (!paused)
            super.onContentChanged();
    }

    public void prepareEntryText(TextView textView, boolean complete) {
        if (complete) {
            textView.setPaintFlags(textView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            textView.setPaintFlags(textView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        }
    }

    public void onPause() {
        paused = true;
    }

    public void onResume() {
        paused = false;
        onContentChanged();
    }

}
