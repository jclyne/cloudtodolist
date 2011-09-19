package com.oci.example.todolist;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import com.oci.example.todolist.provider.TodoList;

public class TodoListActivity extends Activity {

    private static final String TAG = "TodoListActivity";
    private static final int NOTES_DIALOG = 1;

    private EditText newEntryBox;
    private ListView todoListView;
    private TodoListCursorAdapter todoListAdapter;



    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.todolist_layout);

        todoListView = (ListView) findViewById(R.id.todo_list);
        Cursor cur = getContentResolver().query(TodoList.Entries.CONTENT_URI, null, null, null, null);
        todoListAdapter=new TodoListCursorAdapter(this, cur);
        todoListView.setAdapter(todoListAdapter);
        registerForContextMenu(todoListView);

        newEntryBox = (EditText) findViewById(R.id.new_entry);
        newEntryBox.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    onNewEntry(newEntryBox.getText().toString());
                    newEntryBox.setText("");
                    return true;
                }
                return false;
            }
        });

        newEntryBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                    newEntryBox.setText("");
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.todolist_menu, menu);

        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.entry_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                Toast.makeText(this,R.string.refreshing_list,Toast.LENGTH_SHORT).show();
                startService(new Intent(TodoListSync.ACTION_TODOLIST_SYNC));
                return true;

            case R.id.menu_clear_selected:
                onClearCompleted();
                return true;

            case R.id.menu_settings:
                Intent intent = new Intent();
                intent.setClass(this, Settings.class);
                startActivity(intent);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case R.id.menu_edit_item:
                onEditEntry(info.id);
                return true;
            case R.id.menu_delete_item:
                onDeleteEntry(info.id);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
            case NOTES_DIALOG:
                return buildNotesDialog(args.getLong("entryId"));

            default:
                return super.onCreateDialog(id);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        todoListAdapter.onPause();
    }

    @Override
    protected void onResume() {
        todoListAdapter.onResume();
        super.onResume();
    }

    public void showNotes(View view) {
        Bundle args = new Bundle();
        args.putLong("entryId", (Integer)view.getTag());
        showDialog(NOTES_DIALOG, args);
    }

    public void onNewEntry(String title) {
        if (title.length() == 0)
            return;

        ContentValues values = new ContentValues();
        values.put(TodoList.Entries.COLUMN_NAME_TITLE, title);
        try {
            getContentResolver().insert(TodoList.Entries.CONTENT_URI, values);
            Toast.makeText(this, getString(R.string.entry_added), Toast.LENGTH_SHORT).show();

        } catch (IllegalArgumentException e) {
            Toast.makeText(this, getString(R.string.entry_invalid), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to add new entry: " + e.toString());
        }

    }

    public void onEditEntry(long entryId) {

        startActivity(new Intent(
                Intent.ACTION_EDIT,
                ContentUris.withAppendedId(TodoList.Entries.CONTENT_ID_URI_BASE, entryId)));
    }

    public void onDeleteEntry(long entryId) {
        final String where = TodoList.Entries._ID + " = ?";
        final String[] whereArgs = {Long.toString(entryId)};
        int rowsDeleted = getContentResolver().delete(
                TodoList.Entries.CONTENT_URI, where, whereArgs);

        if (rowsDeleted > 0)
            Toast.makeText(this, getString(R.string.entry_deleted), Toast.LENGTH_SHORT).show();
    }


    public void onClearCompleted() {
        final String where = TodoList.Entries.COLUMN_NAME_COMPLETE + " = ?";
        final String[] whereArgs = {Integer.toString(1)};
        int rowsDeleted = getContentResolver().delete(
                TodoList.Entries.CONTENT_URI, where, whereArgs);

        String msg = getResources().getQuantityString( R.plurals.clearedEntriesDeleted,rowsDeleted,rowsDeleted);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    public Dialog buildNotesDialog(long entryId) {
        Uri entryUri = ContentUris.withAppendedId(TodoList.Entries.CONTENT_ID_URI_BASE, entryId);
        final String[] what = {TodoList.Entries.COLUMN_NAME_NOTES,
                                TodoList.Entries.COLUMN_NAME_TITLE};

        Cursor cursor = getContentResolver().query(entryUri, what, null, null, null);
        cursor.moveToFirst();
        String title = cursor.getString(cursor.getColumnIndex(TodoList.Entries.COLUMN_NAME_TITLE));
        String notes = cursor.getString(cursor.getColumnIndex(TodoList.Entries.COLUMN_NAME_NOTES));
        if (notes.equals("")){
            notes=getString(R.string.empty_notes);
        }

        AlertDialog notesDialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(notes)
                .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                })
                .create();

        notesDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface dialogInterface) {
                removeDialog(NOTES_DIALOG);
            }
        });

        return notesDialog;
    }
}
