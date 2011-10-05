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
import android.widget.ImageView;
import android.widget.TextView;
import com.oci.example.todolist.provider.TodoListProvider;

/**
 * Cursor adapter that handles data from a todolist content provider
 */
public class TodoListCursorAdapter extends CursorAdapter {

    // Current application context
    private final Context context;

    /**
     * Constructor - builds an adapter for the supplied cursor in the specified context
     *
     * @param context current context
     * @param c       cursor to query the underlying content provider
     */
    public TodoListCursorAdapter(Context context, Cursor c) {
        super(context, c, 0);
        this.context = context;
    }

    /**
     * Makes a new view to hold the data pointed to by cursor. This is called the first
     * time the data the needs to displayed.
     *
     * @param context interface to application's global information
     * @param cursor  cursor from which to get the data. The cursor is
     *                already moved to the correct position.
     * @param parent  parent to which the new view is attached to
     * @return the newly created view
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(R.layout.entry_layout, parent, false);
    }


    /**
     * Bind an existing view to the data pointed to by cursor. This is called each time
     * the data in the cursor is updated
     *
     * @param view    existing view, returned earlier by newView
     * @param context interface to application's global information
     * @param cursor  cursor from which to get the data. The cursor is
     *                already moved to the correct position.
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {

        // Get the Indexes for the columns of interest.
        final int idIdx = cursor.getColumnIndex(TodoListProvider.Schema.Entries._ID);
        final int titleIdx = cursor.getColumnIndex(TodoListProvider.Schema.Entries.TITLE);
        final int completeIdx = cursor.getColumnIndex(TodoListProvider.Schema.Entries.COMPLETE);
        final int pendingUpdateIdx = cursor.getColumnIndex(TodoListProvider.Schema.Entries.PENDING_UPDATE);
        final int pendingDeleteIdx = cursor.getColumnIndex(TodoListProvider.Schema.Entries.PENDING_DELETE);

        // Set the values that are to be displayed in the view
        final int id = cursor.getInt(idIdx);
        final boolean complete = cursor.getInt(completeIdx) == 1;
        final String title = cursor.getString(titleIdx);
        final boolean dirty = ((cursor.getInt(pendingUpdateIdx) > 0)
                || (cursor.getInt(pendingDeleteIdx) > 0));

        // Get references to the view's components to display the data
        final CheckBox completeCheckBox = (CheckBox) view.findViewById(R.id.entry_complete);
        final TextView titleTextView = (TextView) view.findViewById(R.id.entry_title);
        final ImageView statusImageView = (ImageView) view.findViewById(R.id.entry_status);


        /**
         *  Set the value of the checkbox according to the entries complete flag. Also,
         *  tag the checkbox with the entry id so that the onClick handler can update the
         *  data store with a change in the complete flag value
         */
        completeCheckBox.setTag(id);
        if (completeCheckBox.isChecked() != complete)
            completeCheckBox.setChecked(complete);


        /**
         * Handler to respond to the action of clicking the checkbox
         */
        completeCheckBox.setOnClickListener(new View.OnClickListener() {
            /**
             * Handler callback for when the checkbox is clicked. Its job is to
             * update the content provider with the new state of the entry complete
             * flag.
             *
             * @param view view that was clicked.
             */
            public void onClick(View view) {
                // Cast the view to a CheckBox and get the Entry ID from the tag
                final CheckBox completeCheckBox = (CheckBox) view;
                final Integer tag = (Integer) completeCheckBox.getTag();

                // Build the entry URI from the retrieved tag
                final Uri entryUri =
                        ContentUris.withAppendedId(
                                TodoListProvider.Schema.Entries.CONTENT_ID_URI_BASE, tag);

                // Set the complete flag according to the
                ContentValues values = new ContentValues();
                values.put(TodoListProvider.Schema.Entries.COMPLETE, (completeCheckBox.isChecked() ? 1 : 0));

                //Update the entry
                TodoListCursorAdapter.this.context.getContentResolver().update(entryUri, values, null, null);
            }
        });

        // Set the entryTextView with the style specific to the complete flag
        prepareEntryText(titleTextView, complete);
        titleTextView.setText(title);

        // Draw an image that indicates whether or not the entry is dirty
        statusImageView.setImageDrawable(
                context.getResources().getDrawable(dirty ? R.drawable.ic_dirty : R.drawable.ic_synced));

    }

    /**
     * Dynamically applies a style to the entry text view based on whether the entry
     * is complete or not. The intent is to display a grayed out, italic, and strike-thru
     * entry when the complete checkbox is set
     *
     * @param textView textview displaying the entry title
     * @param complete flag indicating whether or not the entry is marked complete
     */
    private void prepareEntryText(TextView textView, boolean complete) {
        if (complete) {
            textView.setTextAppearance(context, R.style.todolist_entry_text_complete);
            textView.setPaintFlags(textView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            textView.setTextAppearance(context, R.style.todolist_entry_text);
            textView.setPaintFlags(textView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);

        }
    }
}