package com.oci.example.todolist;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.test.ProviderTestCase2;
import android.test.mock.MockContentResolver;

import java.util.Calendar;
import java.util.GregorianCalendar;


/**
 * Tests for TodoListProvider content provider
 */

public class TodoListProviderTest extends ProviderTestCase2<TodoListProvider> {

    private static class EntryData {
        final String title;
        final String notes;
        final boolean complete;
        private long createTime;
        private long modifyTime;


        public void setModifyTime(long modifyTime) {
            this.modifyTime = modifyTime;
        }

        public void setCreateTime(long createTime) {
            this.createTime = createTime;
        }

        public EntryData(String title, String notes, Boolean complete) {
            this.title = title;
            this.notes = notes;
            this.complete = complete;
        }

        public ContentValues toContentValues() {

            ContentValues values = new ContentValues();
            values.put(TodoList.Entries.COLUMN_NAME_TITLE, title);
            values.put(TodoList.Entries.COLUMN_NAME_NOTES, notes);
            values.put(TodoList.Entries.COLUMN_NAME_COMPLETE, complete);
            values.put(TodoList.Entries.COLUMN_NAME_CREATE_TIME, createTime);
            values.put(TodoList.Entries.COLUMN_NAME_MODIFY_TIME, modifyTime);

            return values;
        }
    }

    private final EntryData[] TEST_ENTRIES = {
            new EntryData("Entry0", "This is entry 0", false),
            new EntryData("Entry1", "This is entry 1", false),
            new EntryData("Entry2", "This is entry 2", false),
            new EntryData("Entry3", "This is entry 3", false),
            new EntryData("Entry4", "This is entry 4", false),
            new EntryData("Entry5", "This is entry 5", false),
            new EntryData("Entry6", "This is entry 6", false),
            new EntryData("Entry7", "This is entry 7", false),
            new EntryData("Entry8", "This is entry 8", false),
            new EntryData("Entry9", "This is entry 9", false)
    };


    private MockContentResolver mockResolver;
    private SQLiteDatabase db;

    // Create a TEST Start Date and some standard time definitions
    private static final long ONE_DAY_MILLIS = 1000 * 60 * 60 * 24;
    private static final long ONE_WEEK_MILLIS = ONE_DAY_MILLIS * 7;
    private static final GregorianCalendar TEST_CALENDAR
            = new GregorianCalendar(2010, Calendar.JANUARY, 1, 0, 0, 0);
    private final static long START_DATE = TEST_CALENDAR.getTimeInMillis();


    public TodoListProviderTest() {
        super(TodoListProvider.class, TodoList.AUTHORITY);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mockResolver = getMockContentResolver();
        db = getProvider().getWritableDatabase();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private void insertData() {
        for (int index = 0; index < TEST_ENTRIES.length; index++) {
            TEST_ENTRIES[index].setCreateTime(START_DATE + (index * ONE_DAY_MILLIS));
            TEST_ENTRIES[index].setModifyTime(START_DATE + (index * ONE_WEEK_MILLIS));
            db.insertOrThrow(TodoList.Entries.TABLE_NAME, null, TEST_ENTRIES[index].toContentValues());
        }
    }


    public void testGetType() {
        // Tests the MIME type for the notes table URI.
        String mimeType = mockResolver.getType(TodoList.Entries.CONTENT_URI);
        assertEquals(TodoList.Entries.CONTENT_TYPE, mimeType);

        // Gets the note ID URI MIME type.
        Uri entryIdUri = ContentUris.withAppendedId(TodoList.Entries.CONTENT_ID_URI_BASE, 1);
        mimeType = mockResolver.getType(entryIdUri);
        assertEquals(TodoList.Entries.CONTENT_ITEM_TYPE, mimeType);

        // Tests an invalid URI. This should throw an IllegalArgumentException.
        Uri invalidUri = Uri.withAppendedPath(TodoList.Entries.CONTENT_URI, "dummy");
        mockResolver.getType(invalidUri);
    }


    public void testQueryEntriesEmpty() {

        Cursor cursor = mockResolver.query(
                TodoList.Entries.CONTENT_URI,  // the URI for the main data table
                null,                       // no projection, get all columns
                null,                       // no selection criteria, get all records
                null,                       // no selection arguments
                null                        // use default sort order
        );

        assertEquals(0, cursor.getCount());
    }

    public void testQueryEntries() {

        insertData();

        Cursor cursor = mockResolver.query(
                TodoList.Entries.CONTENT_URI,  // the URI for the main data table
                null,                       // no projection, get all columns
                null,                       // no selection criteria, get all records
                null,                       // no selection arguments
                null                        // use default sort order
        );

        assertEquals(TEST_ENTRIES.length, cursor.getCount());

    }

    public void testQueryEntriesWithProjection() {

        final String[] TEST_PROJECTION = {
                TodoList.Entries.COLUMN_NAME_TITLE,
                TodoList.Entries.COLUMN_NAME_NOTES,
                TodoList.Entries.COLUMN_NAME_COMPLETE
        };

        insertData();

        Cursor cursor = mockResolver.query(
                TodoList.Entries.CONTENT_URI,  // the URI for the main data table
                TEST_PROJECTION,            // get the title, note, and mod date columns
                null,                       // no selection columns, get all the records
                null,                       // no selection criteria
                null                        // use default the sort order
        );

        assertEquals(TEST_PROJECTION.length, cursor.getColumnCount());
        assertEquals(TEST_PROJECTION[0], cursor.getColumnName(0));
        assertEquals(TEST_PROJECTION[1], cursor.getColumnName(1));
        assertEquals(TEST_PROJECTION[2], cursor.getColumnName(2));
    }

    public void testQueryEntriesWithProjectionAndSelectionColumns() {

        final String[] TEST_PROJECTION = {
                TodoList.Entries.COLUMN_NAME_TITLE,
                TodoList.Entries.COLUMN_NAME_NOTES,
                TodoList.Entries.COLUMN_NAME_COMPLETE
        };

        final String TITLE_SELECTION = TodoList.Entries.COLUMN_NAME_TITLE + " = " + "?";

        String SELECTION_COLUMNS = TITLE_SELECTION;
        final String[] SELECTION_ARGS = {"Entry0", "Entry1", "Entry5"};
        for (int i = 1; i < SELECTION_ARGS.length; i++)
            SELECTION_COLUMNS += " OR " + TITLE_SELECTION;

        final String SORT_ORDER = TodoList.Entries.COLUMN_NAME_TITLE + " ASC";

        insertData();

        Cursor cursor = mockResolver.query(
                TodoList.Entries.CONTENT_URI, // the URI for the main data table
                TEST_PROJECTION,           // get the title, note, and mod date columns
                SELECTION_COLUMNS,         // select on the title column
                SELECTION_ARGS,            // select titles "Entry0", "Entry1", or "Entry5"
                SORT_ORDER                 // sort ascending on the title column
        );

        assertEquals(SELECTION_ARGS.length, cursor.getCount());

        int index = 0;

        while (cursor.moveToNext())
            assertEquals(SELECTION_ARGS[index++], cursor.getString(0));


        assertEquals(SELECTION_ARGS.length, index);
    }

    public void testQueryEntryEmpty() {
        Uri noteIdUri = ContentUris.withAppendedId(TodoList.Entries.CONTENT_ID_URI_BASE, 1);

        Cursor cursor = mockResolver.query(
                noteIdUri, // URI pointing to a single record
                null,      // no projection, get all the columns for each record
                null,      // no selection criteria, get all the records in the table
                null,      // no need for selection arguments
                null       // default sort, by ascending title
        );

        // Asserts that the cursor is null.
        assertEquals(0, cursor.getCount());
    }

    public void testQueryEntry() {
        final String SELECTION_COLUMNS = TodoList.Entries.COLUMN_NAME_TITLE + " = " + "?";
        final String[] SELECTION_ARGS = {"Entry1"};
        final String SORT_ORDER = TodoList.Entries.COLUMN_NAME_TITLE + " ASC";
        final String[] ENTRY_ID_PROJECTION = {TodoList.Entries._ID,};

        insertData();

        // Queries the table using the URI for the full table.
        Cursor cursor = mockResolver.query(
                TodoList.Entries.CONTENT_URI, // the base URI for the table
                ENTRY_ID_PROJECTION,        // returns the ID and title columns of rows
                SELECTION_COLUMNS,         // select based on the title column
                SELECTION_ARGS,            // select title of "Entry1"
                SORT_ORDER                 // sort order returned is by title, ascending
        );

        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToFirst());

        int insertedEntryId = cursor.getInt(0);
        Uri entryIdUri = ContentUris.withAppendedId(TodoList.Entries.CONTENT_ID_URI_BASE, insertedEntryId);


        cursor = mockResolver.query(entryIdUri, // the URI for a single note
                ENTRY_ID_PROJECTION,                 // same projection, get ID and title columns
                SELECTION_COLUMNS,                  // same selection, based on title column
                SELECTION_ARGS,                     // same selection arguments, title = "Entry1"
                SORT_ORDER                          // same sort order returned, by title, ascending
        );

        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToFirst());
        assertEquals(insertedEntryId, cursor.getInt(0));
    }

    public void testInsert() {
        EntryData entry = new EntryData("Entry30", "This is Entry30", false);
        entry.setCreateTime(START_DATE + (10 * ONE_DAY_MILLIS));
        entry.setModifyTime(START_DATE + (2 * ONE_WEEK_MILLIS));

        ContentValues values = entry.toContentValues();

        mockResolver.insert(TodoList.Entries.CONTENT_URI, values);

        Cursor cursor = mockResolver.query(
                TodoList.Entries.CONTENT_URI, // the main table URI
                null,                      // no projection, return all the columns
                null,                      // no selection criteria, return all the rows in the model
                null,                      // no selection arguments
                null                       // default sort order
        );

        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToFirst());

        int titleIndex = cursor.getColumnIndex(TodoList.Entries.COLUMN_NAME_TITLE);
        int notesIndex = cursor.getColumnIndex(TodoList.Entries.COLUMN_NAME_NOTES);
        int completeIndex = cursor.getColumnIndex(TodoList.Entries.COLUMN_NAME_COMPLETE);
        int createTimeIndex = cursor.getColumnIndex(TodoList.Entries.COLUMN_NAME_CREATE_TIME);
        int modifyTimeIndex = cursor.getColumnIndex(TodoList.Entries.COLUMN_NAME_MODIFY_TIME);

        assertEquals(entry.title, cursor.getString(titleIndex));
        assertEquals(entry.notes, cursor.getString(notesIndex));
        assertEquals(entry.complete, (cursor.getInt(completeIndex) == 1) );
        assertEquals(entry.createTime, cursor.getLong(createTimeIndex));
        assertEquals(entry.modifyTime, cursor.getLong(modifyTimeIndex));
    }

    public void testInsertExisting() {
        EntryData entry = new EntryData("Entry30", "This is Entry30", false);
        entry.setCreateTime(START_DATE + (10 * ONE_DAY_MILLIS));
        entry.setModifyTime(START_DATE + (2 * ONE_WEEK_MILLIS));

        ContentValues values = entry.toContentValues();

        Uri entryUri = mockResolver.insert(TodoList.Entries.CONTENT_URI, values);
        values.put(TodoList.Entries._ID, (int) ContentUris.parseId(entryUri));

        // Tries to insert this record into the table. This should fail and drop into the
        // catch block. If it succeeds, issue a failure message.
        try {
            mockResolver.insert(TodoList.Entries.CONTENT_URI, values);
            fail("Expected insert failure for existing record but insert succeeded.");
        } catch (Exception e) {
            // succeeded, so do nothing.
        }
    }

    public void testDeleteEmpty(){

        int rowsDeleted = mockResolver.delete(
            TodoList.Entries.CONTENT_URI, // the base URI of the table
            null,                        // all columns
            null                        //
        );

        assertEquals(0, rowsDeleted);
    }

    public void testDelete(){

        final String SELECTION_COLUMNS = TodoList.Entries.COLUMN_NAME_TITLE + " = " + "?";
        final String[] SELECTION_ARGS = { "Entry0" };


        insertData();

        int rowsDeleted = mockResolver.delete(
            TodoList.Entries.CONTENT_URI, // the base URI of the table
            SELECTION_COLUMNS,         // same selection column, "title"
            SELECTION_ARGS             // same selection arguments, title = "Entry0"
        );

        assertEquals(1, rowsDeleted);

        Cursor cursor = mockResolver.query(
            TodoList.Entries.CONTENT_URI, // the base URI of the table
            null,                      // no projection, return all columns
            SELECTION_COLUMNS,         // select based on the title column
            SELECTION_ARGS,            // select title = "Entry0"
            null                       // use the default sort order
        );

        assertEquals(0, cursor.getCount());
    }

    public void testUpdatesEmpty() {

        ContentValues values = new ContentValues();
        String newNote = "Testing an update with this string";
        values.put(TodoList.Entries.COLUMN_NAME_NOTES, newNote);

        int rowsUpdated = mockResolver.update(
            TodoList.Entries.CONTENT_URI,  // the URI of the data table
            values,                     // a map of the updates to do (column title and value)
            null,                       // select based on the title column
            null                        // select "title = Entry1"
        );

        assertEquals(0, rowsUpdated);
    }
    public void testUpdates() {

        final String[] NOTES_PROJECTION = { TodoList.Entries.COLUMN_NAME_NOTES };
        final String  SELECTION_COLUMNS = TodoList.Entries.COLUMN_NAME_TITLE + " = " + "?";
        final String[] SELECTION_ARGS = { "Entry1" };

        ContentValues values = new ContentValues();
        String newNote = "Testing an update with this string";
        values.put(TodoList.Entries.COLUMN_NAME_NOTES, newNote);

        insertData();

        int rowsUpdated = mockResolver.update(
            TodoList.Entries.CONTENT_URI,   // The URI of the data table
            values,                      // the same map of updates
            SELECTION_COLUMNS,            // same selection, based on the title column
            SELECTION_ARGS                // same selection argument, to select "title = Entry1"
        );

        assertEquals(1, rowsUpdated);

        Cursor cursor = mockResolver.query(
                TodoList.Entries.CONTENT_URI, // the base URI for the table
                NOTES_PROJECTION,        // returns the entry notes
                SELECTION_COLUMNS,         // select based on the title column
                SELECTION_ARGS,            // select title of "Entry1"
                null
        );

        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToFirst());
        int notesIndex = cursor.getColumnIndex(TodoList.Entries.COLUMN_NAME_NOTES);
        assertEquals(newNote, cursor.getString(notesIndex));

    }
}
