package com.oci.example.todolist;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

public class TodoListActivity extends Activity {

    private static final String TAG ="TodoListActivity";

    /**
     * Menus Items
     */
    private static final int MENU_EDIT_ENTRY = 0;
    private static final int MENU_CLEAR_SELECTED = 1;

    /**
     * Layout Items
     */
    private ListView listView;
    private EditText newEntryBox;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.todolist_layout);

        listView = (ListView) findViewById(R.id.todo_list);
        Cursor cur = getContentResolver().query(TodoList.Entries.CONTENT_URI, null, null, null, null);
        listView.setAdapter(new TodoListCursorAdapter(this, cur));
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int pos, long id) {
                onEditEntry(id);
                return false;
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
                if (hasFocus){
                    newEntryBox.setText("");
                } else {
                    onNewEntry(newEntryBox.getText().toString());
                    newEntryBox.setText(R.string.new_entry_help);
                }
            }
        });
    }

    public void onNewEntry(String title){
        if (title.length() == 0)
            return;

        ContentValues values = new ContentValues();
        values.put(TodoList.Entries.COLUMN_NAME_TITLE, title);
        try{
            getContentResolver().insert(TodoList.Entries.CONTENT_URI, values);
            Toast.makeText(this, "Entry Added", Toast.LENGTH_SHORT).show();

        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Invalid Entry", Toast.LENGTH_SHORT).show();
            Log.e(TAG,"Failed to add new entry: "+e.toString());
        }

    }

    public void onEditEntry(long entryId) {
         Toast.makeText(this, "onEditEntry: "+entryId, Toast.LENGTH_SHORT).show();
    }


    public void onClearCompleted(){
        final String  where = TodoList.Entries.COLUMN_NAME_COMPLETE + " = 1";
        int rowsDeleted = getContentResolver().delete(
                    TodoList.Entries.CONTENT_URI,where, null);

        Toast.makeText(this, "Deleted "+rowsDeleted+(rowsDeleted==1?" Entry":" Entries"), Toast.LENGTH_SHORT).show();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_EDIT_ENTRY, 0, R.string.menu_edit_entry);
        menu.add(0, MENU_CLEAR_SELECTED, 0, R.string.menu_clear_selected);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_EDIT_ENTRY:
                Toast.makeText(this, "Edit Entry", Toast.LENGTH_SHORT).show();
                return true;

            case MENU_CLEAR_SELECTED:
                onClearCompleted();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
