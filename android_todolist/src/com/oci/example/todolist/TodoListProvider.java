package com.oci.example.todolist;

import android.content.*;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides access to a database of todolist entries. Each entry has an id, a title, notes,
 * and a completed flag
 */
public class TodoListProvider extends ContentProvider {

    // Used for debugging and logging
    private static final String TAG = "TodoListProvider";

    /**
     * The database that the provider uses as its underlying data store
     */
    private static final String DATABASE_NAME = "todolist.db";

    /**
     * The database version
     */
    private static final int DATABASE_VERSION = 1;


    /*
     * Constants used by the Uri matcher to choose an action based on the pattern
     * of the incoming URI
     */
    private static final int ENTRIES = 1;
    private static final int ENTRY_ID = 2;
    private static final int DELETE_PENDING_ENTRIES = 3;
    private static final int DELETE_PENDING_ENTRY_ID = 4;

    /**
     * A UriMatcher instance
     */
    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    // Handle to a new DatabaseHelper.
    private DatabaseHelper dbHelper;

    static {
        // Add the URIs to the matcher
        uriMatcher.addURI(TodoList.AUTHORITY, TodoList.Entries.PATH_TODOLIST_ENTRIES, ENTRIES);
        uriMatcher.addURI(TodoList.AUTHORITY, TodoList.Entries.PATH_TODOLIST_ENTRY_ID + "#", ENTRY_ID);
        uriMatcher.addURI(TodoList.AUTHORITY,
                TodoList.DeletePendingEntries.PATH_TODOLIST_DELETE_PENDING_ENTRIES,
                DELETE_PENDING_ENTRIES);
        uriMatcher.addURI(TodoList.AUTHORITY,
                TodoList.DeletePendingEntries.PATH_TODOLIST_DELETE_PENDING_ENTRY_ID + "#",
                DELETE_PENDING_ENTRY_ID);
    }

    static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {

            // calls the super constructor, requesting the default cursor factory.
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }


        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TodoList.Entries.TABLE_NAME + " ("
                    + TodoList.Entries._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + TodoList.Entries.COLUMN_NAME_TITLE + " TEXT,"
                    + TodoList.Entries.COLUMN_NAME_NOTES + " TEXT,"
                    + TodoList.Entries.COLUMN_NAME_COMPLETE + " INTEGER,"
                    + TodoList.Entries.COLUMN_NAME_CREATE_TIME + " LONG,"
                    + TodoList.Entries.COLUMN_NAME_MODIFY_TIME + " LONG,"
                    + TodoList.Entries.COLUMN_NAME_PENDING_TX + " INTEGER,"
                    + TodoList.Entries.COLUMN_NAME_PENDING_INSERT + " INTEGER,"
                    + TodoList.Entries.COLUMN_NAME_PENDING_UPDATE + " INTEGER"
                    + ");");

            db.execSQL("CREATE TABLE " + TodoList.DeletePendingEntries.TABLE_NAME + " ("
                    + TodoList.Entries._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + TodoList.Entries.COLUMN_NAME_PENDING_TX + " INTEGER"
                    + ");");
        }


        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            // Logs that the database is being upgraded
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");

            // Kills the table and existing data
            db.execSQL("DROP TABLE IF EXISTS " + TodoList.Entries.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + TodoList.DeletePendingEntries.TABLE_NAME);

            // Recreates the database with a new version
            onCreate(db);
        }
    }

    /**
     * This is for unit tests
     *
     * @return A writable SQLite database object for the data being managed by the provider
     */
    SQLiteDatabase getWritableDatabase() {
        return dbHelper.getWritableDatabase();
    }

    @Override
    public boolean onCreate() {
        // Creates a new helper object. Note that the database itself isn't opened until
        // something tries to access it, and it's only created if it doesn't already exist.
        dbHelper = new DatabaseHelper(getContext());

        // Assumes that any failures will be reported by a thrown exception.
        return true;
    }

    @Override
    public String getType(Uri uri) {
        /**
         * Return the MIME type based on the incoming URI pattern
         */
        switch (uriMatcher.match(uri)) {

            case ENTRIES:
                return TodoList.Entries.CONTENT_TYPE;

            case ENTRY_ID:
                return TodoList.Entries.CONTENT_ITEM_TYPE;

            case DELETE_PENDING_ENTRIES:
                return TodoList.DeletePendingEntries.CONTENT_TYPE;

            case DELETE_PENDING_ENTRY_ID:
                return TodoList.DeletePendingEntries.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        String orderBy;
        switch (uriMatcher.match(uri)) {

            case ENTRY_ID:
                qb.appendWhere(TodoList.Entries._ID + "=" +
                        uri.getPathSegments().get(TodoList.Entries.TODOLIST_ENTRY_ID_PATH_POSITION));
            case ENTRIES:
                qb.setTables(TodoList.Entries.TABLE_NAME);
                if (TextUtils.isEmpty(sortOrder)) {
                    orderBy = TodoList.Entries.DEFAULT_SORT_ORDER;
                } else {
                    orderBy = sortOrder;
                }
                break;

            case DELETE_PENDING_ENTRY_ID:
                qb.appendWhere(TodoList.DeletePendingEntries._ID + "=" +
                        uri.getPathSegments().get(
                                TodoList.DeletePendingEntries.TODOLIST_DELETE_PENDING_ENTRY_ID_PATH_POSITION));
            case DELETE_PENDING_ENTRIES:
                qb.setTables(TodoList.DeletePendingEntries.TABLE_NAME);
                if (TextUtils.isEmpty(sortOrder)) {
                    orderBy = TodoList.DeletePendingEntries.DEFAULT_SORT_ORDER;
                } else {
                    orderBy = sortOrder;
                }
                break;

            default:
                // If the URI doesn't match any of the known patterns, throw an exception.
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
        c.setNotificationUri(getContext().getContentResolver(), uri);

        return c;
    }


    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {

        // Initialize a new ContentValues object to whatever was passed in
        ContentValues values = new ContentValues();
        if (contentValues != null)
            values.putAll(contentValues);

        switch (uriMatcher.match(uri)) {
            case ENTRIES:
                // Set the Created and Modified times to NOW if not set
                Long now = System.currentTimeMillis();
                if (!values.containsKey(TodoList.Entries.COLUMN_NAME_CREATE_TIME))
                    values.put(TodoList.Entries.COLUMN_NAME_CREATE_TIME, now);

                if (!values.containsKey(TodoList.Entries.COLUMN_NAME_MODIFY_TIME))
                    values.put(TodoList.Entries.COLUMN_NAME_MODIFY_TIME, now);

                // If the values map doesn't contain a title, sets the value to the default title.
                if (!values.containsKey(TodoList.Entries.COLUMN_NAME_TITLE))
                    values.put(TodoList.Entries.COLUMN_NAME_TITLE,
                            Resources.getSystem().getString(android.R.string.untitled));

                // If the values map doesn't contain notes text, sets the value to an empty string.
                if (!values.containsKey(TodoList.Entries.COLUMN_NAME_NOTES))
                    values.put(TodoList.Entries.COLUMN_NAME_NOTES, "");

                // If the values map doesn't contain a completed flag
                if (!values.containsKey(TodoList.Entries.COLUMN_NAME_COMPLETE))
                    values.put(TodoList.Entries.COLUMN_NAME_COMPLETE, 0);

                // Mark the entry as pending a POST
                if (!values.containsKey(TodoList.Entries.COLUMN_NAME_PENDING_TX))
                    values.put(TodoList.Entries.COLUMN_NAME_PENDING_TX, 0);

                if (!values.containsKey(TodoList.Entries.COLUMN_NAME_PENDING_INSERT))
                    values.put(TodoList.Entries.COLUMN_NAME_PENDING_INSERT, 1);

                if (!values.containsKey(TodoList.Entries.COLUMN_NAME_PENDING_UPDATE))
                    values.put(TodoList.Entries.COLUMN_NAME_PENDING_UPDATE, 0);

                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long id = db.insert(TodoList.Entries.TABLE_NAME, null, values);

        if (id > 0) {
            Uri entryUri = ContentUris.withAppendedId(TodoList.Entries.CONTENT_ID_URI_BASE, id);
            getContext().getContentResolver().notifyChange(entryUri, null);
            return entryUri;
        }


        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count;
        switch (uriMatcher.match(uri)) {

            case ENTRY_ID:
                where = appendEntryIdWhereClause(uri, where);
            case ENTRIES:
                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
                qb.setTables(TodoList.Entries.TABLE_NAME);
                final String[] idProjection = {TodoList.Entries._ID};

                db.beginTransaction();
                try {
                    Cursor cur = qb.query(db, idProjection, where, whereArgs, null, null, null);
                    cur.moveToFirst();
                    while (cur.isAfterLast() == false) {
                        ContentValues values = new ContentValues();
                        values.put(TodoList.Entries._ID, cur.getInt(0));
                        values.put(TodoList.Entries.COLUMN_NAME_PENDING_TX, 0);
                        long id = db.insert(TodoList.DeletePendingEntries.TABLE_NAME, null, values);
                        if (id <= 0)
                            throw new SQLException("Failed to delete row from " + uri);

                        cur.moveToNext();
                    }
                    cur.close();
                    count = db.delete(TodoList.Entries.TABLE_NAME, where, whereArgs);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                break;

            case DELETE_PENDING_ENTRY_ID:
                where = appendEntryIdWhereClause(uri, where);
            case DELETE_PENDING_ENTRIES:
                count = db.delete(TodoList.DeletePendingEntries.TABLE_NAME, where, whereArgs);
                break;

            // If the incoming pattern is invalid, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String where, String[] whereArgs) {

        // Initialize a new ContentValues object to whatever was passed in
        ContentValues values = new ContentValues();
        if (contentValues != null)
            values.putAll(contentValues);

        String tableName;

        switch (uriMatcher.match(uri)) {

            case ENTRY_ID:
                where = appendEntryIdWhereClause(uri, where);
            case ENTRIES:
                // Set the Modified times to NOW if not set
                if (!values.containsKey(TodoList.Entries.COLUMN_NAME_MODIFY_TIME))
                    values.put(TodoList.Entries.COLUMN_NAME_MODIFY_TIME, System.currentTimeMillis());

                // Mark the entry as pending a POST
                if (!values.containsKey(TodoList.Entries.COLUMN_NAME_PENDING_UPDATE))
                    values.put(TodoList.Entries.COLUMN_NAME_PENDING_UPDATE, 1);

                tableName = TodoList.Entries.TABLE_NAME;
                break;

            case DELETE_PENDING_ENTRY_ID:
                where = appendEntryIdWhereClause(uri, where);
            case DELETE_PENDING_ENTRIES:
                tableName = TodoList.DeletePendingEntries.TABLE_NAME;
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count = db.update(tableName, values, where, whereArgs);

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations) {

        ContentProviderResult[] backRefs = new ContentProviderResult[operations.size() - 1];
        List<ContentProviderResult> results = new ArrayList<ContentProviderResult>(operations.size());

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Make this a transaction buy starting a SQLite transaction and returning all or
        //  none ContentProviderResults
        db.beginTransaction();
        try {
            for (ContentProviderOperation operation : operations)
                results.add(operation.apply(this, results.toArray(backRefs), results.size()));

            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Batch Operation failed: " + e.toString());
            results.clear();
        } finally {
            db.endTransaction();
        }

        return results.toArray(new ContentProviderResult[results.size()]);

    }

    private String appendEntryIdWhereClause(Uri uri, String where) {
        String entryId = uri.getPathSegments().get(TodoList.Entries.TODOLIST_ENTRY_ID_PATH_POSITION);
        String newWhere = TodoList.Entries._ID + " = " + entryId;
        if (where != null)
            newWhere += " AND " + where;

        return newWhere;

    }
}
