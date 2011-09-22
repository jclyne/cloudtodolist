package com.oci.example.todolist;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.*;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import com.oci.example.todolist.provider.TodoListSchema;

public class TodoListActivity extends FragmentActivity
                            implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "TodoListActivity";
    private static final int TODOLIST_LOADER = 1;
    private static final int NOTES_DIALOG = 1;

    private EditText newEntryBox;
    private TodoListCursorAdapter todoListAdapter;


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.todolist_layout);
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        final ListView todoListView = (ListView) findViewById(R.id.todo_list);

        getSupportLoaderManager().initLoader(TODOLIST_LOADER,null,
            new LoaderManager.LoaderCallbacks<Cursor>() {

                @Override
                public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
                    assert(id == TODOLIST_LOADER);
                    return new CursorLoader(getBaseContext(),
                                    TodoListSchema.Entries.CONTENT_URI,
                                    null, null, null, null);
                }

                @Override
                public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
                    todoListAdapter.swapCursor(cursor);
                }

                @Override
                public void onLoaderReset(Loader<Cursor> cursorLoader) {
                    todoListAdapter.swapCursor(null);
                }
        });

        todoListAdapter=new TodoListCursorAdapter(this, null);
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

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        requestSync(false);
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
                requestSync(true);
                return true;

            case R.id.menu_clear_selected:
                onClearCompleted();
                return true;

            case R.id.menu_settings:
                Intent intent = new Intent();
                intent.setClass(this, TodoListSettingsActivity.class);
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
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
         if (key.equals("server_address")){
            requestSync(true);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
    public void showNotes(View view) {
        Bundle args = new Bundle();
        args.putLong("entryId", (Integer)view.getTag());
        showDialog(NOTES_DIALOG, args);
    }

    public void onNewEntry(String title) {
        if (title.length() == 0)
            return;

        ContentValues values = new ContentValues();
        values.put(TodoListSchema.Entries.TITLE, title);
        try {
            getContentResolver().insert(TodoListSchema.Entries.CONTENT_URI, values);
            Toast.makeText(this, getString(R.string.entry_added), Toast.LENGTH_SHORT).show();

        } catch (IllegalArgumentException e) {
            Toast.makeText(this, getString(R.string.entry_invalid), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to add new entry: " + e.toString());
        }

    }

    public void onEditEntry(long entryId) {

        startActivity(new Intent(
                Intent.ACTION_EDIT,
                ContentUris.withAppendedId(TodoListSchema.Entries.CONTENT_ID_URI_BASE, entryId)));
    }

    public void onDeleteEntry(long entryId) {
        final String where = TodoListSchema.Entries._ID + " = ?";
        final String[] whereArgs = {Long.toString(entryId)};
        int rowsDeleted = getContentResolver().delete(
                TodoListSchema.Entries.CONTENT_URI, where, whereArgs);

        if (rowsDeleted > 0)
            Toast.makeText(this, getString(R.string.entry_deleted), Toast.LENGTH_SHORT).show();
    }


    public void onClearCompleted() {
        final String where = TodoListSchema.Entries.COMPLETE + " = ?";
        final String[] whereArgs = {Integer.toString(1)};
        int rowsDeleted = getContentResolver().delete(
                TodoListSchema.Entries.CONTENT_URI, where, whereArgs);

        String msg = getResources().getQuantityString( R.plurals.clearedEntriesDeleted,rowsDeleted,rowsDeleted);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void requestSync(boolean notify){
        if (notify)
            Toast.makeText(getBaseContext(), R.string.sync_initiated, Toast.LENGTH_SHORT).show();
        startService(new Intent(TodoListSyncService.ACTION_TODOLIST_SYNC));
    }


    public Dialog buildNotesDialog(long entryId) {
        Uri entryUri = ContentUris.withAppendedId(TodoListSchema.Entries.CONTENT_ID_URI_BASE, entryId);
        final String[] what = {TodoListSchema.Entries.NOTES,
                                TodoListSchema.Entries.TITLE};

        Cursor cursor = getContentResolver().query(entryUri, what, null, null, null);
        cursor.moveToFirst();
        String title = cursor.getString(cursor.getColumnIndex(TodoListSchema.Entries.TITLE));
        String notes = cursor.getString(cursor.getColumnIndex(TodoListSchema.Entries.NOTES));
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
