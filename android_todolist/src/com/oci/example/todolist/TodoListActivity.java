package com.oci.example.todolist;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
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
import com.oci.example.todolist.provider.TodoListProvider;

public class TodoListActivity extends FragmentActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "TodoListActivity";
    private static final int TODOLIST_CURSOR_LOADER = 1;
    private static final int NOTES_DIALOG = 1;

    private EditText newEntryBox;
    private TodoListCursorAdapter todoListAdapter;


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "Created"+" ("+ Thread.currentThread().getName()+")");
        setContentView(R.layout.todolist_layout);
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        final ListView todoListView = (ListView) findViewById(R.id.todo_list);

        getSupportLoaderManager().initLoader(TODOLIST_CURSOR_LOADER, null,
                new LoaderManager.LoaderCallbacks<Cursor>() {

                    @Override
                    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
                        Log.i(TAG, "Cursor Loader Initialized"+" ("+ Thread.currentThread().getName()+")");
                        assert (id == TODOLIST_CURSOR_LOADER);
                        return new CursorLoader(getBaseContext(),
                                TodoListProvider.Schema.Entries.CONTENT_URI,
                                null, null, null, null);
                    }

                    @Override
                    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
                        Log.i(TAG, "Cursor Loader Finished"+" ("+ Thread.currentThread().getName()+")");
                        todoListAdapter.swapCursor(cursor);
                        TodoListSyncHelper.requestSync(getBaseContext());
                    }

                    @Override
                    public void onLoaderReset(Loader<Cursor> cursorLoader) {
                        Log.i(TAG, "Cursor Loader Reset"+" ("+ Thread.currentThread().getName()+")");
                        todoListAdapter.swapCursor(null);
                    }
                });

        todoListAdapter = new TodoListCursorAdapter(this, null);
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

        MenuItem notesItem = menu.getItem(0);

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        Uri entryUri = ContentUris.withAppendedId(TodoListProvider.Schema.Entries.CONTENT_ID_URI_BASE, info.id);
        final String[] what = {TodoListProvider.Schema.Entries.NOTES};
        Cursor cursor = getContentResolver().query(entryUri, what, null, null, null);
        cursor.moveToFirst();
        String notes = cursor.getString(cursor.getColumnIndex(TodoListProvider.Schema.Entries.NOTES));

        notesItem.setEnabled( (notes != null && !notes.isEmpty()) );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                Toast.makeText(this, R.string.sync_initiated, Toast.LENGTH_SHORT).show();
                TodoListSyncHelper.requestSync(this);
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
            case R.id.menu_notes_item:
                onShowNotes(info.id);
                return true;

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
        if (key.equals("server_address")) {
            // When the server address is changed, we need to delete all the entries in the
            TodoListSyncHelper.requestRefresh(this);
        } else if (key.equals("sync_interval")){
            TodoListSyncHelper.enableBackgroundSync(this);
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

    public void onShowNotes(long entryId) {
        Bundle args = new Bundle();
        args.putLong("entryId", entryId);
        showDialog(NOTES_DIALOG, args);
    }

    public void onNewEntry(String title) {
        if (title.length() == 0)
            return;

        ContentValues values = new ContentValues();
        values.put(TodoListProvider.Schema.Entries.TITLE, title);
        try {
            getContentResolver().insert(TodoListProvider.Schema.Entries.CONTENT_URI, values);
            Toast.makeText(this, getString(R.string.entry_added), Toast.LENGTH_SHORT).show();

        } catch (IllegalArgumentException e) {
            Toast.makeText(this, getString(R.string.entry_invalid), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to add new entry: " + e.toString());
        }

    }

    public void onEditEntry(long entryId) {

        startActivity(new Intent(
                Intent.ACTION_EDIT,
                ContentUris.withAppendedId(TodoListProvider.Schema.Entries.CONTENT_ID_URI_BASE, entryId)));
    }

    public void onDeleteEntry(long entryId) {
        final Uri entryUri =
                ContentUris.withAppendedId(
                        TodoListProvider.Schema.Entries.CONTENT_ID_URI_BASE, entryId);
        int rowsDeleted = getContentResolver().delete(entryUri, null, null);

        if (rowsDeleted > 0)
            Toast.makeText(this, getString(R.string.entry_deleted), Toast.LENGTH_SHORT).show();
    }


    public void onClearCompleted() {
        final String where = TodoListProvider.Schema.Entries.COMPLETE + " = ?";
        final String[] whereArgs = {Integer.toString(1)};
        int rowsDeleted = getContentResolver().delete(
                TodoListProvider.Schema.Entries.CONTENT_URI, where, whereArgs);

        String msg = getResources().getQuantityString(R.plurals.clearedEntriesDeleted, rowsDeleted, rowsDeleted);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }


    public Dialog buildNotesDialog(long entryId) {
        Uri entryUri = ContentUris.withAppendedId(TodoListProvider.Schema.Entries.CONTENT_ID_URI_BASE, entryId);
        final String[] what = {TodoListProvider.Schema.Entries.NOTES,
                TodoListProvider.Schema.Entries.TITLE};

        Cursor cursor = getContentResolver().query(entryUri, what, null, null, null);
        cursor.moveToFirst();
        String title = cursor.getString(cursor.getColumnIndex(TodoListProvider.Schema.Entries.TITLE));
        String notes = cursor.getString(cursor.getColumnIndex(TodoListProvider.Schema.Entries.NOTES));
        if (notes == null || notes.isEmpty()) {
            notes = getString(R.string.empty_notes);
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
