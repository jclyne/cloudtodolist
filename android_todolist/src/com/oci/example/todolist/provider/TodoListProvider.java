package com.oci.example.todolist.provider;

import android.content.*;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
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
     * Defines a contract between the Schema content provider and its clients. A contract defines the
     * information that a client needs to access the provider as one or more data tables. A contract
     * is a public, non-extendable (final) class that contains constants defining column names and
     * URIs. A well-written client depends only on the constants in the contract.
     */
    public static final class Schema {
        public static final String AUTHORITY = "com.oci.provider.todolist";
        private static final String SCHEME = "content://";
        private static final String PATH_TODOLIST = "todolist/";


        private Schema() {
        }

        public static final class Entries implements BaseColumns {

            /**
             *  Table Name
             */
            private static final String TABLE_NAME = "entries";


            // This class cannot be instantiated
            private Entries() {
            }

            /*
             * URI definitions
             */

            // Path Definitions
            public static final String PATH_TODOLIST_ENTRIES = PATH_TODOLIST + "entries";
            public static final String PATH_TODOLIST_ENTRY_ID = PATH_TODOLIST + "entries/";
            private static final int TODOLIST_ENTRY_ID_PATH_POSITION = 2;

            public static final Uri CONTENT_URI
                    = Uri.parse(SCHEME + AUTHORITY + "/" + PATH_TODOLIST_ENTRIES);

            public static final Uri CONTENT_ID_URI_BASE
                    = Uri.parse(SCHEME + AUTHORITY + "/" +PATH_TODOLIST_ENTRY_ID);


            /*
             * MIME type definitions
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.oci.todolist.entries";
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.oci.todolist.entry";

            /**
             * Default Sort Order
             */
            public static final String DEFAULT_SORT_ORDER = "created ASC";

            /*
             * Public definitions
             */
            public static final String ID = "ID";
            public static final String TITLE = "title";
            public static final String NOTES = "notes";
            public static final String COMPLETE = "complete";
            public static final String CREATED = "created";
            public static final String MODIFIED = "modified";

            /**
             * Private
             */
            private static final String PENDING_TX = "pending_tx";
            private static final String PENDING_UPDATE = "pending_update";
            private static final String PENDING_DELETE = "deleted";
        }
    }


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
        uriMatcher.addURI(Schema.AUTHORITY, Schema.Entries.PATH_TODOLIST_ENTRIES, ENTRIES);
        uriMatcher.addURI(Schema.AUTHORITY, Schema.Entries.PATH_TODOLIST_ENTRY_ID + "#", ENTRY_ID);
    }

    static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {

            // calls the super constructor, requesting the default cursor factory.
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }


        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + Schema.Entries.TABLE_NAME + " ("
                    + Schema.Entries._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + Schema.Entries.ID + " INTEGER KEY,"
                    + Schema.Entries.TITLE + " TEXT,"
                    + Schema.Entries.NOTES + " TEXT,"
                    + Schema.Entries.COMPLETE + " INTEGER,"
                    + Schema.Entries.CREATED + " LONG,"
                    + Schema.Entries.MODIFIED + " LONG,"
                    + Schema.Entries.PENDING_TX + " INTEGER DEFAULT 0,"
                    + Schema.Entries.PENDING_UPDATE + " INTEGER DEFAULT 0,"
                    + Schema.Entries.PENDING_DELETE + " INTEGER DEFAULT 0"
                    + ");");
        }


        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            // Logs that the database is being upgraded
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");

            // Kills the table and existing data
            db.execSQL("DROP TABLE IF EXISTS " + Schema.Entries.TABLE_NAME);

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

    String getEntriesTableName() {
        return Schema.Entries.TABLE_NAME;
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
                return Schema.Entries.CONTENT_TYPE;

            case ENTRY_ID:
                return Schema.Entries.CONTENT_ITEM_TYPE;

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
                qb.appendWhere(Schema.Entries._ID + "=" +
                        uri.getPathSegments().get(Schema.Entries.TODOLIST_ENTRY_ID_PATH_POSITION) + " AND ");
            case ENTRIES:
                qb.appendWhere(Schema.Entries.PENDING_DELETE + "=" + 0);
                qb.setTables(Schema.Entries.TABLE_NAME);
                if (TextUtils.isEmpty(sortOrder)) {
                    orderBy = Schema.Entries.DEFAULT_SORT_ORDER;
                } else {
                    orderBy = sortOrder;
                }
                break;

            default:
                // If the URI doesn't match any of the known patterns, throw an exception.
                throw new IllegalArgumentException("Unknown URI " + uri);
        }


        Cursor cur = qb.query(dbHelper.getReadableDatabase(), what, where, whereArgs, null, null, orderBy);
        cur.setNotificationUri(getContext().getContentResolver(), uri);

        return cur;
    }


    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {

        // Initialize a new ContentValues object to whatever was passed in
        ContentValues values = new ContentValues();
        if (contentValues != null)
            values.putAll(contentValues);

        long now = System.currentTimeMillis();
        long id;

        switch (uriMatcher.match(uri)) {
            case ENTRIES:
                values.put(Schema.Entries.ID, 0);
                values.put(Schema.Entries.PENDING_TX, 0);
                values.put(Schema.Entries.PENDING_UPDATE, 1);
                values.put(Schema.Entries.PENDING_DELETE, 0);

                if (!values.containsKey(Schema.Entries.CREATED))
                    values.put(Schema.Entries.CREATED, now);

                if (!values.containsKey(Schema.Entries.MODIFIED))
                    values.put(Schema.Entries.MODIFIED, now);

                if (!values.containsKey(Schema.Entries.TITLE))
                    values.put(Schema.Entries.TITLE,
                            Resources.getSystem()
                                    .getString(android.R.string.untitled));

                if (!values.containsKey(Schema.Entries.NOTES))
                    values.put(Schema.Entries.NOTES, "");

                if (!values.containsKey(Schema.Entries.COMPLETE))
                    values.put(Schema.Entries.COMPLETE, 0);

                id = dbHelper.getWritableDatabase().insert(Schema.Entries.TABLE_NAME, null, values);

                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }


        if (id > 0) {
            Uri entryUri = ContentUris.withAppendedId(Schema.Entries.CONTENT_ID_URI_BASE, id);
            Context context = getContext();
            context.getContentResolver().notifyChange(entryUri, null);
            //context.startService(new Intent(TodoListSyncService.ACTION_TODOLIST_SYNC));
            return entryUri;
        }


        throw new IllegalArgumentException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {

        ContentValues values = new ContentValues();
        int count;
        switch (uriMatcher.match(uri)) {

            case ENTRY_ID:
                where = appendEntryIdWhereClause(uri, where);
            case ENTRIES:
                where = appendEntryDeletedWhereClause(where);
                values.put(Schema.Entries.PENDING_DELETE, 1);

                count = dbHelper.getWritableDatabase().update(Schema.Entries.TABLE_NAME, values, where, whereArgs);
                break;

            // If the incoming pattern is invalid, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (count > 0){
            Context context = getContext();
            context.getContentResolver().notifyChange(uri, null);
            //context.startService(new Intent(TodoListSyncService.ACTION_TODOLIST_SYNC));
        }

        return count;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String where, String[] whereArgs) {

        // Initialize a new ContentValues object to whatever was passed in

        ContentValues values = new ContentValues();
        if (contentValues != null)
            values.putAll(contentValues);
        int count;
        switch (uriMatcher.match(uri)) {

            case ENTRY_ID:
                where = appendEntryIdWhereClause(uri, where);
            case ENTRIES:
                where = appendEntryDeletedWhereClause(where);

                // Set the Modified times to NOW if not set
                values.put(Schema.Entries.MODIFIED, System.currentTimeMillis());
                // Mark the entry as pending a POST
                values.put(Schema.Entries.PENDING_UPDATE, 1);

                count = dbHelper.getWritableDatabase().update(Schema.Entries.TABLE_NAME, values, where, whereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }


        if (count > 0){
            Context context = getContext();
            context.getContentResolver().notifyChange(uri, null);
            //context.startService(new Intent(TodoListSyncService.ACTION_TODOLIST_SYNC));
        }

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
        cursorIntToContentValues(cursor, Schema.Entries._ID, values);
        cursorIntToContentValues(cursor, Schema.Entries.ID, values);
        cursorStringToContentValues(cursor, Schema.Entries.TITLE, values);
        cursorStringToContentValues(cursor, Schema.Entries.NOTES, values);
        cursorIntToContentValues(cursor, Schema.Entries.COMPLETE, values);
        cursorIntToContentValues(cursor, Schema.Entries.CREATED, values);
        cursorIntToContentValues(cursor, Schema.Entries.MODIFIED, values);
        cursorIntToContentValues(cursor, Schema.Entries.PENDING_UPDATE, values);
        cursorIntToContentValues(cursor, Schema.Entries.PENDING_DELETE, values);
        cursorIntToContentValues(cursor, Schema.Entries.PENDING_TX, values);
        return values;
    }

    private static boolean checkNeedsUpdate(Cursor cur, ContentValues entryValues) {

        return ((cur.getInt(cur.getColumnIndex(Schema.Entries.PENDING_UPDATE)) == 0)
                && (cur.getInt(cur.getColumnIndex(Schema.Entries.PENDING_DELETE)) == 0)
                && (cur.getLong(cur.getColumnIndex(Schema.Entries.MODIFIED))
                != entryValues.getAsLong(Schema.Entries.MODIFIED)));
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
        String where = Schema.Entries.ID + " = ?";
        boolean notify = false;

        db.beginTransaction();
        try {
            SQLiteQueryBuilder query = new SQLiteQueryBuilder();
            query.setTables(Schema.Entries.TABLE_NAME);
            Cursor cur = query.query(db, null, null, null, null, null, null);
            for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext()) {
                String idString = Integer.toString(cur.getInt(cur.getColumnIndex(Schema.Entries.ID)));
                String[] whereArgs = {idString};

                ContentValues entryValues = syncData.getParcelable(idString);
                if (entryValues != null) {
                    if (checkNeedsUpdate(cur, entryValues)) {
                        notify = true;
                        db.update(Schema.Entries.TABLE_NAME, entryValues, where, whereArgs);
                    }
                } else {
                    notify = true;
                    db.delete(Schema.Entries.TABLE_NAME, Schema.Entries.ID + " = " + idString, null);
                }
                syncData.remove(idString);
            }

            for (String idString : syncData.keySet()) {
                ContentValues entryValues = syncData.getParcelable(idString);
                notify = true;
                db.insert(Schema.Entries.TABLE_NAME, null, entryValues);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (notify) {
            getContext().getContentResolver().notifyChange(Schema.Entries.CONTENT_URI, null);
        }

        return notify;
    }

    public List<ContentValues> stageUpstreamSync() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String where = "(" + Schema.Entries.PENDING_UPDATE + " = 1" + " OR "
                + Schema.Entries.PENDING_DELETE + " = 1 )" + " AND "
                + Schema.Entries.PENDING_TX + " = 0";

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(Schema.Entries.TABLE_NAME);

        ContentValues values = new ContentValues();
        values.put(Schema.Entries.PENDING_TX, 1);
        values.put(Schema.Entries.PENDING_UPDATE, 0);
        values.put(Schema.Entries.PENDING_DELETE, 0);

        List<ContentValues> toSync = new ArrayList<ContentValues>();
        db.beginTransaction();
        try {
            Cursor cur = qb.query(db, null, where, null, null, null, null);
            for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext())
                toSync.add(toContentValues(cur));

            db.update(Schema.Entries.TABLE_NAME, values, where, null);

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
        String where = Schema.Entries._ID + " = ?";
        for (ContentValues values : stageUpstreamSync()) {
            String[] whereArgs = {Integer.toString(values.getAsInteger(Schema.Entries._ID))};

            if (values.getAsInteger(Schema.Entries.PENDING_DELETE) == 1) {
                if (client.delete(values)) {
                    db.delete(Schema.Entries.TABLE_NAME, where, whereArgs);
                } else {
                    ContentValues entryValues = new ContentValues();
                    entryValues.put(Schema.Entries.PENDING_DELETE, 1);
                    entryValues.put(Schema.Entries.PENDING_TX, 0);
                    db.update(Schema.Entries.TABLE_NAME, entryValues, where, whereArgs);
                }
            } else if (values.getAsInteger(Schema.Entries.PENDING_UPDATE) == 1) {
                ContentValues entryValues;
                if (values.getAsInteger(Schema.Entries.ID) == 0) {
                    entryValues = client.insert(values);
                } else {
                    entryValues = client.update(values);
                }
                if (entryValues == null) {
                    entryValues = new ContentValues();
                    entryValues.put(Schema.Entries.PENDING_UPDATE, 1);
                }
                entryValues.put(Schema.Entries.PENDING_TX, 0);
                db.update(Schema.Entries.TABLE_NAME, entryValues, where, whereArgs);
            }
        }
    }

    private static String appendEntryIdWhereClause(Uri uri, String where) {
        String entryId = uri.getPathSegments().get(Schema.Entries.TODOLIST_ENTRY_ID_PATH_POSITION);
        String newWhere = Schema.Entries._ID + " = " + entryId;
        if (where != null)
            newWhere += " AND " + where;

        return newWhere;

    }

    private static String appendEntryDeletedWhereClause(String where) {
        String newWhere = Schema.Entries.PENDING_DELETE + " = " + 0;
        if (where != null)
            newWhere += " AND " + where;

        return newWhere;

    }

}
