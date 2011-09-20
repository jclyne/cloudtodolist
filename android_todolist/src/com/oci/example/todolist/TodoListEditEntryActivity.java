package com.oci.example.todolist;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import com.oci.example.todolist.provider.TodoList;

public class TodoListEditEntryActivity extends Activity {

    private EditText titleEditText;
    private EditText notesEditText;

    private String currentTitle;
    private String currentNotes;

    private Uri entryUri;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_entry_layout);
        titleEditText = (EditText)findViewById(R.id.edit_title);
        notesEditText = (EditText)findViewById(R.id.edit_notes);

        // Get the entry uri from the intent
        entryUri = getIntent().getData();

        // Get the current title and notes strings to pre-populate
        final String[] what = {TodoList.Entries.TITLE,
                                TodoList.Entries.NOTES};

        Cursor cursor = getContentResolver().query(entryUri,what,null,null,null);
        final int titleIndex = cursor.getColumnIndex(TodoList.Entries.TITLE);
        final int notesIndex = cursor.getColumnIndex(TodoList.Entries.NOTES);
        cursor.moveToFirst();

        currentTitle=cursor.getString(titleIndex);
        titleEditText.setText(currentTitle);

        currentNotes=cursor.getString(notesIndex);
        notesEditText.setText(currentNotes);
    }

    @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
    public void apply(View view) {
        ContentValues values = new ContentValues();

        String newTitle=titleEditText.getText().toString();
        if (!newTitle.equals(currentTitle))
            values.put(TodoList.Entries.TITLE,newTitle);

        String newNotes=notesEditText.getText().toString();
        if (!newNotes.equals(currentNotes))
            values.put(TodoList.Entries.NOTES,newNotes);

        if (values.size() > 0) {
            getContentResolver().update(entryUri,values,null,null);
            Toast.makeText(this, getString(R.string.entry_updated), Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
    public void cancel(View view) {
        finish();
    }
}

