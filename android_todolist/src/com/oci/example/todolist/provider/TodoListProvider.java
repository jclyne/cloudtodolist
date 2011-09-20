package com.oci.example.todolist.provider;

import android.content.*;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                    + TodoList.Entries.ID + " INTEGER KEY,"
                    + TodoList.Entries.TITLE + " TEXT,"
                    + TodoList.Entries.NOTES + " TEXT,"
                    + TodoList.Entries.COMPLETE + " INTEGER,"
                    + TodoList.Entries.CREATE_TIME + " LONG,"
                    + TodoList.Entries.MODIFY_TIME + " LONG,"
                    + TodoList.Entries.PENDING_TX + " INTEGER DEFAULT 0,"
                    + TodoList.Entries.DIRTY + " INTEGER DEFAULT 0,"
                    + TodoList.Entries.DELETED + " INTEGER DEFAULT 0"
                    + ");");
        }


        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            // Logs that the database is being upgraded
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");

            // Kills the table and existing data
            db.execSQL("DROP TABLE IF EXISTS " + TodoList.Entries.TABLE_NAME);

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

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] what, String where, String[] whereArgs, String sortOrder) {

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        String orderBy;
        switch (uriMatcher.match(uri)) {

            case ENTRY_ID:
                qb.appendWhere(TodoList.Entries._ID + "=" +
                        uri.getPathSegments().get(TodoList.Entries.TODOLIST_ENTRY_ID_PATH_POSITION) + " AND ");
            case ENTRIES:
                qb.appendWhere(TodoList.Entries.DELETED + "=" + 0);
                qb.setTables(TodoList.Entries.TABLE_NAME);
                if (TextUtils.isEmpty(sortOrder)) {
                    orderBy = TodoList.Entries.DEFAULT_SORT_ORDER;
                } else {
                    orderBy = sortOrder;
                }
                break;

            default:
                // If the URI doesn't match any of the known patterns, throw an exception.
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor c = qb.query(db, what, where, whereArgs, null, null, orderBy);
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

                // All inserted entries have an ID of 0 until synced
                values.put(TodoList.Entries.ID, 0);

                // Set the Created and Modified times to NOW if not set
                Long now = System.currentTimeMillis();
                if (!values.containsKey(TodoList.Entries.CREATE_TIME))
                    values.put(TodoList.Entries.CREATE_TIME, now);

                if (!values.containsKey(TodoList.Entries.MODIFY_TIME))
                    values.put(TodoList.Entries.MODIFY_TIME, now);

                // If the values map doesn't contain a title, sets the value to the default title.
                if (!values.containsKey(TodoList.Entries.TITLE))
                    values.put(TodoList.Entries.TITLE,
                            Resources.getSystem().getString(android.R.string.untitled));

                // If the values map doesn't contain notes text, sets the value to an empty string.
                if (!values.containsKey(TodoList.Entries.NOTES))
                    values.put(TodoList.Entries.NOTES, "");

                // If the values map doesn't contain a completed flag
                if (!values.containsKey(TodoList.Entries.COMPLETE))
                    values.put(TodoList.Entries.COMPLETE, 0);

                // Mark the entry as pending a POST
                if (!values.containsKey(TodoList.Entries.PENDING_TX))
                    values.put(TodoList.Entries.PENDING_TX, 0);

                if (!values.containsKey(TodoList.Entries.DIRTY))
                    values.put(TodoList.Entries.DIRTY, 1);

                if (!values.containsKey(TodoList.Entries.DELETED))
                    values.put(TodoList.Entries.DELETED, 0);

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


        throw new IllegalArgumentException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count = 0;
        switch (uriMatcher.match(uri)) {

            case ENTRY_ID:
                where = appendEntryIdWhereClause(uri, where);
            case ENTRIES:
                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
                qb.setTables(TodoList.Entries.TABLE_NAME);
                final String[] what = {TodoList.Entries._ID,
                        TodoList.Entries.DELETED};

                db.beginTransaction();
                try {
                    Cursor cur = qb.query(db, what, where, whereArgs, null, null, null);
                    for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext()) {

                        final int entryId = cur.getInt(cur.getColumnIndex(TodoList.Entries._ID));
                        final boolean deletedFlag = cur.getInt(
                                cur.getColumnIndex(TodoList.Entries.DELETED)) == 1;

                        final String _where = TodoList.Entries._ID + " = " + "?";
                        final String[] _whereArgs = {Integer.toString(entryId)};

                        if (deletedFlag) {
                            count += db.delete(TodoList.Entries.TABLE_NAME, _where, _whereArgs);
                        } else {
                            ContentValues values = new ContentValues();
                            values.put(TodoList.Entries.DELETED, 1);
                            count += db.update(TodoList.Entries.TABLE_NAME, values, _where, _whereArgs);
                        }
                    }
                    cur.close();
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
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
                where = appendEntryDeletedWhereClause(where);
                // Set the Modified times to NOW if not set
                if (!values.containsKey(TodoList.Entries.MODIFY_TIME))
                    values.put(TodoList.Entries.MODIFY_TIME, System.currentTimeMillis());

                // Mark the entry as pending a POST
                if (!values.containsKey(TodoList.Entries.DIRTY))
                    values.put(TodoList.Entries.DIRTY, 1);

                tableName = TodoList.Entries.TABLE_NAME;
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

    public boolean checkNeedsUpdate(Cursor cur, ContentValues entryValues) {

        if ( (cur.getInt(cur.getColumnIndex(TodoList.Entries.DIRTY)) == 1)
          || (cur.getInt(cur.getColumnIndex(TodoList.Entries.DELETED)) == 1) )
            return false;

        if (cur.getInt(cur.getColumnIndex(TodoList.Entries.ID))
                != entryValues.getAsInteger(TodoList.Entries.ID) )
            return true;

        if (cur.getInt(cur.getColumnIndex(TodoList.Entries.COMPLETE))
                != entryValues.getAsInteger(TodoList.Entries.COMPLETE) )
            return true;

        if (! cur.getString(cur.getColumnIndex(TodoList.Entries.NOTES)).trim().equals(
                    entryValues.getAsString(TodoList.Entries.NOTES).trim()) )
            return true;

        if (! cur.getString(cur.getColumnIndex(TodoList.Entries.TITLE)).trim().equals(
                    entryValues.getAsString(TodoList.Entries.TITLE).trim()) )
            return true;

        return false;
    }

    public boolean performSync(Map<Integer, ContentValues> syncData) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String where = TodoList.Entries.ID + " = ?";
        boolean modified = false;

        db.beginTransaction();
        try {
            SQLiteQueryBuilder query = new SQLiteQueryBuilder();
            query.setTables(TodoList.Entries.TABLE_NAME);
            Cursor cur = query.query(db, null, null, null, null, null, null);
            for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext()) {
                int id = cur.getInt(cur.getColumnIndex(TodoList.Entries.ID));
                String[] whereArgs = {Integer.toString(id)};

                ContentValues entryValues = syncData.get(id);
                if (entryValues != null) {
                    if (checkNeedsUpdate(cur, entryValues)) {
                        modified = true;
                        db.update(TodoList.Entries.TABLE_NAME, entryValues, where, whereArgs);
                    }
                } else {
                    modified = true;
                    db.delete(TodoList.Entries.TABLE_NAME, TodoList.Entries.ID + " = " + id, null);
                }
                syncData.remove(id);
            }

            for (ContentValues entryValues : syncData.values()) {
                modified = true;
                db.insert(TodoList.Entries.TABLE_NAME, null, entryValues);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (modified) {
            getContext().getContentResolver().notifyChange(TodoList.Entries.CONTENT_URI, null);
        }

        return modified;
    }

    private static String appendEntryIdWhereClause(Uri uri, String where) {
        String entryId = uri.getPathSegments().get(TodoList.Entries.TODOLIST_ENTRY_ID_PATH_POSITION);
        String newWhere = TodoList.Entries._ID + " = " + entryId;
        if (where != null)
            newWhere += " AND " + where;

        return newWhere;

    }

    private static String appendEntryDeletedWhereClause(String where) {
        String newWhere = TodoList.Entries.DELETED + " = " + 0;
        if (where != null)
            newWhere += " AND " + where;

        return newWhere;

    }
}
