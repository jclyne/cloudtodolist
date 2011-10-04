package com.oci.example.todolist;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Paint;
import android.net.Uri;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.oci.example.todolist.provider.TodoListProvider;

public class TodoListCursorAdapter extends CursorAdapter {

    private final Context context;

    public TodoListCursorAdapter(Context context, Cursor c) {
        super(context, c, 0);
        this.context = context;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(R.layout.entry_layout, parent, false);
    }


    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        final int id = cursor.getInt(cursor.getColumnIndex(TodoListProvider.Schema.Entries._ID));
        final boolean complete = cursor.getInt(cursor.getColumnIndex(TodoListProvider.Schema.Entries.COMPLETE)) == 1;
        final String title = cursor.getString(cursor.getColumnIndex(TodoListProvider.Schema.Entries.TITLE));
        final boolean dirty = (
                (cursor.getInt(cursor.getColumnIndex(TodoListProvider.Schema.Entries.PENDING_UPDATE)) > 0)
                        || (cursor.getInt(cursor.getColumnIndex(TodoListProvider.Schema.Entries.PENDING_DELETE)) > 0));

        final CheckBox completeCheckBox = (CheckBox) view.findViewById(R.id.entry_complete);
        final TextView titleTextView = (TextView) view.findViewById(R.id.entry_title);
        final ImageView statusImageView = (ImageView) view.findViewById(R.id.entry_status);


        completeCheckBox.setTag(id);

        if (completeCheckBox.isChecked() != complete)
            completeCheckBox.setChecked(complete);

        prepareEntryText(titleTextView, complete);

        completeCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prepareEntryText(titleTextView, checked);
            }
        });

        completeCheckBox.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                CheckBox completeCheckBox = (CheckBox) view;
                final Integer tag = (Integer) completeCheckBox.getTag();

                final Uri entryUri =
                        ContentUris.withAppendedId(
                                TodoListProvider.Schema.Entries.CONTENT_ID_URI_BASE, tag);

                ContentValues values = new ContentValues();
                values.put(TodoListProvider.Schema.Entries.COMPLETE, (completeCheckBox.isChecked() ? 1 : 0));
                TodoListCursorAdapter.this.context.getContentResolver().update(entryUri, values, null, null);
            }
        });

        titleTextView.setText(title);
        statusImageView.setImageDrawable(
                context.getResources().getDrawable(dirty ? R.drawable.ic_dirty : R.drawable.ic_synced));

    }

    public void prepareEntryText(TextView textView, boolean complete) {
        if (complete) {
            textView.setTextAppearance(context, R.style.todolist_entry_text_complete);
            textView.setPaintFlags(textView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            textView.setTextAppearance(context, R.style.todolist_entry_text);
            textView.setPaintFlags(textView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);

        }
    }
}
