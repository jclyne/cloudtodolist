package com.oci.example.todolist;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.*;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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

    private static ConnectivityManager connManager;
    private static SharedPreferences prefs;
    private static final int TODOLIST_CURSOR_LOADER = 1;
    private static final int TODOLIST_PENDING_TX_LOADER = 2;
    private static final int NOTES_DIALOG = 1;

    private EditText newEntryBox;
    private TodoListCursorAdapter todoListAdapter;
    private BroadcastReceiver broadcastReceiver;


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Created" + " (" + Thread.currentThread().getName() + ")");

        connManager = (ConnectivityManager) getBaseContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        setContentView(R.layout.todolist_layout);
        setWindowTitle();

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // This adapter is initially created with a null cursor. The  TODOLIST_CURSOR_LOADER we defined
        //  will take care of swapping in loaded cursors in the LoaderCallbacks. This will take care of
        //  making sure that the "requery" is not on the UI thread, and that it will not requery when the
        //  the activity is paused.
        todoListAdapter = new TodoListCursorAdapter(this, null);

        // Now associate the adapter with the list view
        final ListView todoListView = (ListView) findViewById(R.id.todo_list);
        todoListView.setAdapter(todoListAdapter);

        // Register the listview as having a context menu. This sets the long-clickable attribute
        // and will call this activity's onCreateContextMenu
        registerForContextMenu(todoListView);

        // Register the TODOLIST_CURSOR_LOADER. The LoaderCallbacks will handle creation of
        //  the CursorLoader as well as setting the proper cursor in the todoListAdapter,
        //  based on the activity and data state.
        getSupportLoaderManager().initLoader(TODOLIST_CURSOR_LOADER, null,
                new LoaderManager.LoaderCallbacks<Cursor>() {

                    @Override
                    public CursorLoader onCreateLoader(int id, Bundle bundle) {
                        // Create and return a CursorLoader that will take care of
                        // creating a Cursor for the data being displayed.
                        Log.d(TAG, "TodoList Cursor Loader Initialized");
                        return new CursorLoader(getBaseContext(),
                                TodoListProvider.Schema.Entries.CONTENT_URI,
                                null, null, null, null);
                    }

                    @Override
                    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
                        // Swap the new cursor in.  (The framework will take care of closing the
                        // old cursor once we return.)
                        Log.d(TAG, "TodoList Cursor Loader Finished" + " (" + cursor.getCount() + ")");
                        todoListAdapter.swapCursor(cursor);
                    }

                    @Override
                    public void onLoaderReset(Loader<Cursor> cursorLoader) {
                        // This is called when the last Cursor provided to onLoadFinished()
                        // above is about to be closed.  We need to make sure we are no
                        // longer using it.
                        Log.d(TAG, "TodoList Cursor Loader Reset");
                        todoListAdapter.swapCursor(null);
                    }
                });


        getSupportLoaderManager().initLoader(TODOLIST_PENDING_TX_LOADER, null,
                new LoaderManager.LoaderCallbacks<Cursor>() {

                    @Override
                    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
                        return new CursorLoader(getBaseContext(),
                                TodoListProvider.Schema.Entries.CONTENT_URI,
                                new String[]{TodoListProvider.Schema.Entries.PENDING_TX},
                                TodoListProvider.Schema.Entries.PENDING_TX + "=1", null, null);
                    }

                    @Override
                    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
                        Log.i(TAG, "Pending TX Loader is Finished loading: " + cursor.getCount());
                    }

                    @Override
                    public void onLoaderReset(Loader<Cursor> cursorLoader) {
                    }
                });

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


        prefs.registerOnSharedPreferenceChangeListener(this);

        IntentFilter broadcastFilter = new IntentFilter();
        broadcastFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        broadcastFilter.addAction(ConnectivityManager.ACTION_BACKGROUND_DATA_SETTING_CHANGED);
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                setWindowTitle();
            }
        };

        registerReceiver(broadcastReceiver, broadcastFilter);

        TodoListSyncHelper.requestSync(this);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(broadcastReceiver);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals("offline_mode")) {
            setWindowTitle();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.todolist_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.getItem(0).setEnabled(!prefs.getBoolean("offline_mode", false));

        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.entry_menu, menu);

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        Uri entryUri = ContentUris.withAppendedId(TodoListProvider.Schema.Entries.CONTENT_ID_URI_BASE, info.id);
        final String[] what = {TodoListProvider.Schema.Entries.NOTES};
        Cursor cursor = getContentResolver().query(entryUri, what, null, null, null);
        cursor.moveToFirst();
        String notes = cursor.getString(cursor.getColumnIndex(TodoListProvider.Schema.Entries.NOTES));

        menu.getItem(0).setEnabled((notes != null && !notes.isEmpty()));
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


    private void setWindowTitle() {

        String title = getString(R.string.app_name);
        NetworkInfo netInfo = connManager.getActiveNetworkInfo();
        if (prefs.getBoolean("offline_mode", false)) {
            title += " - Offline Mode";
        } else if (!connManager.getBackgroundDataSetting()) {
            title += " - Background data disabled";
        } else if (netInfo == null || !netInfo.isConnected()) {
            title += " - Network Unavailable";
        } else {
            title += " - Online";
        }

        setTitle(title);
    }

    private Dialog buildNotesDialog(long entryId) {
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
