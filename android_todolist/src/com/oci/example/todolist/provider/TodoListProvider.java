package com.oci.example.todolist.provider;

import android.content.*;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import com.oci.example.todolist.TodoListSyncService;
import com.oci.example.todolist.client.SyncableClient;
import com.oci.example.todolist.client.SyncableProvider;

import java.util.ArrayList;
import java.util.List;

import static android.database.DatabaseUtils.cursorIntToContentValues;
import static android.database.DatabaseUtils.cursorStringToContentValues;

/**
 * Provides access to a database of todolist entries. Each entry has an id, a title, notes,
 * and a completed flag
 */
public class TodoListProvider extends ContentProvider implements SyncableProvider {

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

    public static final String TABLE_NAME = "entries";
            public static final String PENDING_TX = "pending_tx";
        public static final String PENDING_UPDATE = "pending_update";
        public static final String PENDING_DELETE = "deleted";
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
        uriMatcher.addURI(TodoListSchema.AUTHORITY, TodoListSchema.Entries.PATH_TODOLIST_ENTRIES, ENTRIES);
        uriMatcher.addURI(TodoListSchema.AUTHORITY, TodoListSchema.Entries.PATH_TODOLIST_ENTRY_ID + "#", ENTRY_ID);
    }

    static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {

            // calls the super constructor, requesting the default cursor factory.
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }


        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                    + TodoListSchema.Entries._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + TodoListSchema.Entries.ID + " INTEGER KEY,"
                    + TodoListSchema.Entries.TITLE + " TEXT,"
                    + TodoListSchema.Entries.NOTES + " TEXT,"
                    + TodoListSchema.Entries.COMPLETE + " INTEGER,"
                    + TodoListSchema.Entries.CREATED + " LONG,"
                    + PENDING_UPDATE + " LONG,"
                    + PENDING_TX + " INTEGER DEFAULT 0,"
                    + PENDING_UPDATE + " INTEGER DEFAULT 0,"
                    + PENDING_DELETE + " INTEGER DEFAULT 0"
                    + ");");
        }


        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            // Logs that the database is being upgraded
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");

            // Kills the table and existing data
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);

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
                return TodoListSchema.Entries.CONTENT_TYPE;

            case ENTRY_ID:
                return TodoListSchema.Entries.CONTENT_ITEM_TYPE;

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
                qb.appendWhere(TodoListSchema.Entries._ID + "=" +
                        uri.getPathSegments().get(TodoListSchema.Entries.TODOLIST_ENTRY_ID_PATH_POSITION) + " AND ");
            case ENTRIES:
                qb.appendWhere(PENDING_DELETE + "=" + 0);
                qb.setTables(TABLE_NAME);
                if (TextUtils.isEmpty(sortOrder)) {
                    orderBy = TodoListSchema.Entries.DEFAULT_SORT_ORDER;
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
                values.put(TodoListSchema.Entries.ID, 0);

                // Set the Created and Modified times to NOW if not set
                Long now = System.currentTimeMillis();
                if (!values.containsKey(TodoListSchema.Entries.CREATED))
                    values.put(TodoListSchema.Entries.CREATED, now);

                if (!values.containsKey(PENDING_UPDATE))
                    values.put(PENDING_UPDATE, now);

                // If the values map doesn't contain a title, sets the value to the default title.
                if (!values.containsKey(TodoListSchema.Entries.TITLE))
                    values.put(TodoListSchema.Entries.TITLE,
                            Resources.getSystem().getString(android.R.string.untitled));

                // If the values map doesn't contain notes text, sets the value to an empty string.
                if (!values.containsKey(TodoListSchema.Entries.NOTES))
                    values.put(TodoListSchema.Entries.NOTES, "");

                // If the values map doesn't contain a completed flag
                if (!values.containsKey(TodoListSchema.Entries.COMPLETE))
                    values.put(TodoListSchema.Entries.COMPLETE, 0);

                // Mark the entry as pending a POST
                if (!values.containsKey(PENDING_TX))
                    values.put(PENDING_TX, 0);

                if (!values.containsKey(PENDING_UPDATE))
                    values.put(PENDING_UPDATE, 1);

                if (!values.containsKey(PENDING_DELETE))
                    values.put(PENDING_DELETE, 0);

                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long id = db.insert(TABLE_NAME, null, values);

        if (id > 0) {
            Uri entryUri = ContentUris.withAppendedId(TodoListSchema.Entries.CONTENT_ID_URI_BASE, id);
            Context context = getContext();
            context.getContentResolver().notifyChange(entryUri, null);
            context.startService(new Intent(TodoListSyncService.ACTION_TODOLIST_SYNC));
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
                qb.setTables(TABLE_NAME);
                final String[] what = {TodoListSchema.Entries._ID,
                        PENDING_DELETE};

                db.beginTransaction();
                try {
                    final String _where = TodoListSchema.Entries._ID + " = " + "?";
                    Cursor cur = qb.query(db, what, where, whereArgs, null, null, null);
                    for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext()) {

                        final int entryId = cur.getInt(cur.getColumnIndex(TodoListSchema.Entries._ID));

                        final String[] _whereArgs = {Integer.toString(entryId)};

                        ContentValues values = new ContentValues();
                        values.put(PENDING_DELETE, 1);
                        count += db.update(TABLE_NAME, values, _where, _whereArgs);
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

        Context context = getContext();
        context.getContentResolver().notifyChange(uri, null);
        context.startService(new Intent(TodoListSyncService.ACTION_TODOLIST_SYNC));

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
                if (!values.containsKey(PENDING_UPDATE))
                    values.put(PENDING_UPDATE, System.currentTimeMillis());

                // Mark the entry as pending a POST
                if (!values.containsKey(PENDING_UPDATE))
                    values.put(PENDING_UPDATE, 1);

                tableName = TABLE_NAME;
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count = db.update(tableName, values, where, whereArgs);

        Context context = getContext();
        context.getContentResolver().notifyChange(uri, null);
        context.startService(new Intent(TodoListSyncService.ACTION_TODOLIST_SYNC));
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


    private ContentValues toContentValues(Cursor cursor) {
        ContentValues values = new ContentValues();
        cursorIntToContentValues(cursor, TodoListSchema.Entries._ID, values);
        cursorIntToContentValues(cursor, TodoListSchema.Entries.ID, values);
        cursorStringToContentValues(cursor, TodoListSchema.Entries.TITLE, values);
        cursorStringToContentValues(cursor, TodoListSchema.Entries.NOTES, values);
        cursorIntToContentValues(cursor, TodoListSchema.Entries.COMPLETE, values);
        cursorIntToContentValues(cursor, TodoListSchema.Entries.CREATED, values);
        cursorIntToContentValues(cursor, PENDING_UPDATE, values);
        cursorIntToContentValues(cursor, PENDING_UPDATE, values);
        cursorIntToContentValues(cursor, PENDING_DELETE, values);
        cursorIntToContentValues(cursor, PENDING_TX, values);
        return values;
    }

    private static boolean checkNeedsUpdate(Cursor cur, ContentValues entryValues) {

        return ((cur.getInt(cur.getColumnIndex(PENDING_UPDATE)) == 0)
                && (cur.getInt(cur.getColumnIndex(PENDING_DELETE)) == 0)
                && (cur.getLong(cur.getColumnIndex(PENDING_UPDATE))
                != entryValues.getAsLong(PENDING_UPDATE)));
    }

    @Override
    public SyncResult onPerformSync(SyncableClient client) {
        SyncResult result = SyncResult.failed;
        Bundle syncData = client.getAll();
        if (syncData != null) {
            if (handleFullRefresh(syncData))
                result = SyncResult.success_updated;
            else
                result = SyncResult.success_no_change;

            performUpstreamSync(client);
        }
        return result;
    }

    private boolean handleFullRefresh(Bundle syncData) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String where = TodoListSchema.Entries.ID + " = ?";
        boolean notify = false;

        db.beginTransaction();
        try {
            SQLiteQueryBuilder query = new SQLiteQueryBuilder();
            query.setTables(TABLE_NAME);
            Cursor cur = query.query(db, null, null, null, null, null, null);
            for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext()) {
                String idString = Integer.toString(cur.getInt(cur.getColumnIndex(TodoListSchema.Entries.ID)));
                String[] whereArgs = {idString};

                ContentValues entryValues = syncData.getParcelable(idString);
                if (entryValues != null) {
                    if (checkNeedsUpdate(cur, entryValues)) {
                        notify = true;
                        db.update(TABLE_NAME, entryValues, where, whereArgs);
                    }
                } else {
                    notify = true;
                    db.delete(TABLE_NAME, TodoListSchema.Entries.ID + " = " + idString, null);
                }
                syncData.remove(idString);
            }

            for (String idString : syncData.keySet()) {
                ContentValues entryValues = syncData.getParcelable(idString);
                notify = true;
                db.insert(TABLE_NAME, null, entryValues);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (notify) {
            getContext().getContentResolver().notifyChange(TodoListSchema.Entries.CONTENT_URI, null);
        }

        return notify;
    }

    public List<ContentValues> stageUpstreamSync() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String where = "(" + PENDING_UPDATE + " = 1" + " OR "
                + PENDING_DELETE + " = 1 )" + " AND "
                + PENDING_TX + " = 0";

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(TABLE_NAME);

        ContentValues values = new ContentValues();
        values.put(PENDING_TX, 1);
        values.put(PENDING_UPDATE, 0);
        values.put(PENDING_DELETE, 0);

        List<ContentValues> toSync = new ArrayList<ContentValues>();
        db.beginTransaction();
        try {
            Cursor cur = qb.query(db, null, where, null, null, null, null);
            for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext())
                toSync.add(toContentValues(cur));

            db.update(TABLE_NAME, values, where, null);

            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            toSync.clear();
        } finally {
            db.endTransaction();
        }

        return toSync;
    }

    public void performUpstreamSync(SyncableClient client) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String where = TodoListSchema.Entries._ID + " = ?";
        for (ContentValues values : stageUpstreamSync()) {
            String[] whereArgs = {Integer.toString(values.getAsInteger(TodoListSchema.Entries._ID))};

            if (values.getAsInteger(PENDING_DELETE) == 1) {
                if (client.delete(values)) {
                    db.delete(TABLE_NAME, where, whereArgs);
                } else {
                    ContentValues entryValues = new ContentValues();
                    entryValues.put(PENDING_DELETE, 1);
                    entryValues.put(PENDING_TX, 0);
                    db.update(TABLE_NAME, entryValues, where, whereArgs);
                }
            } else if (values.getAsInteger(PENDING_UPDATE) == 1) {
                ContentValues entryValues;
                if (values.getAsInteger(TodoListSchema.Entries.ID) == 0) {
                    entryValues = client.insert(values);
                } else {
                    entryValues = client.update(values);
                }
                if (entryValues == null) {
                    entryValues = new ContentValues();
                    entryValues.put(PENDING_UPDATE, 1);
                }
                entryValues.put(PENDING_TX, 0);
                db.update(TABLE_NAME, entryValues, where, whereArgs);
            }
        }
    }

    private static String appendEntryIdWhereClause(Uri uri, String where) {
        String entryId = uri.getPathSegments().get(TodoListSchema.Entries.TODOLIST_ENTRY_ID_PATH_POSITION);
        String newWhere = TodoListSchema.Entries._ID + " = " + entryId;
        if (where != null)
            newWhere += " AND " + where;

        return newWhere;

    }

    private static String appendEntryDeletedWhereClause(String where) {
        String newWhere = PENDING_DELETE + " = " + 0;
        if (where != null)
            newWhere += " AND " + where;

        return newWhere;

    }
}
