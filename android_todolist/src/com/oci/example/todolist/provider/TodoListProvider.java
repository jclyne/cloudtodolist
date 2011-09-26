package com.oci.example.todolist.provider;

import android.content.*;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import com.oci.example.todolist.TodoListSyncService;
import com.oci.example.todolist.client.HttpRestClient;
import com.oci.example.todolist.client.TodoListRestClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides access to a database of todolist entries. Each entry has an id, a title, notes,
 * and a completed flag
 */
public class TodoListProvider extends ContentProvider implements RestServiceProvider {

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
             * Table Name
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
                    = Uri.parse(SCHEME + AUTHORITY + "/" + PATH_TODOLIST_ENTRY_ID);


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
            public static final String PENDING_TX = "pending_tx";
            public static final String PENDING_UPDATE = "pending_update";
            public static final String PENDING_DELETE = "pending_delete";
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

    class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {

            // calls the super constructor, requesting the default cursor factory.
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }


        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + Schema.Entries.TABLE_NAME + " ("
                    + Schema.Entries._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + Schema.Entries.ID + " INTEGER UNIQUE DEFAULT NULL,"
                    + Schema.Entries.TITLE + " TEXT,"
                    + Schema.Entries.NOTES + " TEXT,"
                    + Schema.Entries.COMPLETE + " INTEGER,"
                    + Schema.Entries.CREATED + " LONG,"
                    + Schema.Entries.MODIFIED + " LONG,"
                    + Schema.Entries.PENDING_TX + " INTEGER DEFAULT 0,"
                    + Schema.Entries.PENDING_UPDATE + " INTEGER DEFAULT 0,"
                    + Schema.Entries.PENDING_DELETE + " INTEGER DEFAULT 0"
                    + ");");

            lastSyncTime = 0;
            commitLastSyncTime();
            requestSync();
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

    private static final String WHERE_DIRTY_NON_PENDING = " ( "+Schema.Entries.PENDING_TX + " = 0"
                + " AND (" + Schema.Entries.PENDING_DELETE + " = 1"
                + " OR " + Schema.Entries.PENDING_UPDATE + " = 1) ) ";

    private static String LAST_SYNC_TIME_FILENAME = "lastSyncTime";
    private double lastSyncTime = 0;

    @Override
    public boolean onCreate() {
        // Creates a new helper object. Note that the database itself isn't opened until
        // something tries to access it, and it's only created if it doesn't already exist.
        dbHelper = new DatabaseHelper(getContext());

        initLastSyncTime();
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
                //values.put(Schema.Entries.ID, null);
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
            context.startService(new Intent(TodoListSyncService.ACTION_TODOLIST_SYNC));
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

        if (count > 0) {
            Context context = getContext();
            context.getContentResolver().notifyChange(uri, null);
            context.startService(new Intent(TodoListSyncService.ACTION_TODOLIST_SYNC));
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


        if (count > 0) {
            Context context = getContext();
            context.getContentResolver().notifyChange(uri, null);
            context.startService(new Intent(TodoListSyncService.ACTION_TODOLIST_SYNC));
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


    @Override
     public SyncResult onPerformSync(HttpRestClient httpRestCleint, boolean forceRefresh) {
        TodoListRestClient client = new TodoListRestClient(httpRestCleint);
        SyncResult result = SyncResult.success_no_change;

        performUpstreamSync(client);

        if (forceRefresh)
            lastSyncTime = 0;

        if (lastSyncTime > 0){
            if (performSyncUpdate(client) )
                result = SyncResult.success_updated;

        } else {
            if (performSyncRefresh(client) )
                result = SyncResult.success_updated;
        }


        return result;
    }

    private static ContentValues entryObjectValues(JSONObject entry) throws JSONException {
        ContentValues entryValues = new ContentValues();

        entryValues.put(TodoListProvider.Schema.Entries.ID, entry.getInt(TodoListRestClient.ENTRY_ID));
        entryValues.put(TodoListProvider.Schema.Entries.COMPLETE, entry.getInt(TodoListRestClient.ENTRY_COMPLETE));
        String title = entry.optString(TodoListRestClient.ENTRY_TITLE);
        if (!title.isEmpty())
            entryValues.put(TodoListProvider.Schema.Entries.TITLE, title);
        String notes = entry.optString(TodoListRestClient.ENTRY_NOTES);
        if (!notes.isEmpty())
            entryValues.put(TodoListProvider.Schema.Entries.NOTES, notes);

        entryValues.put(TodoListProvider.Schema.Entries.CREATED,
                (long) (entry.getDouble(TodoListRestClient.ENTRY_CREATED) * 1000));
        entryValues.put(TodoListProvider.Schema.Entries.MODIFIED,
                (long) (entry.getDouble(TodoListRestClient.ENTRY_MODIFIED) * 1000));

        return entryValues;

    }
    private boolean performSyncUpdate(TodoListRestClient client) {
        boolean notify=false;

        // Perform an update sync
        TodoListRestClient.EntryListResponse response = client.getEntries(lastSyncTime);
        if (response.getResponse().getStatusCode() == TodoListRestClient.Response.SUCCESS_OK) {
            List<JSONObject> entries = response.getEntryList();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            String where = Schema.Entries.ID + " = ?";

            db.beginTransaction();
            try {
                for (JSONObject entry : entries) {
                    String[] whereArgs = {entry.getInt(TodoListRestClient.ENTRY_ID)};
                    if (entry.getBoolean(entry.getInt(TodoListRestClient.ENTRY_DELETED))) {
                        // If the entry is deleted, remove it from the local database
                        //  regardless of whether or not it is dirty. If its been deleted,
                        //  our local changes are irrelevant.
                        db.delete(Schema.Entries.TABLE_NAME, where, whereArgs);
                        notify=true;
                    } else {
                        // Append to the where clause the non-dirty flags
                        where += " AND " + Schema.Entries.PENDING_TX + " = 0"
                                + " AND " + Schema.Entries.PENDING_DELETE + " = 0"
                                + " AND " + Schema.Entries.PENDING_UPDATE + " = 0";

                        // First try and update an existing row, if that doesn't work insert a new one
                        int updatedRows = db.update(Schema.Entries.TABLE_NAME, entryObjectValues(entry),where,whereArgs);
                        if (updatedRows == 0){
                            long id = db.insert(Schema.Entries.TABLE_NAME, Schema.Entries.TITLE, entryObjectValues(entry));
                            if (id != -1)
                                notify=true;
                        }
                        else
                            notify=true;
                    }
                }
                lastSyncTime = response.getTimestamp();
                commitLastSyncTime();
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        if (notify)
            getContext().getContentResolver().notifyChange(Schema.Entries.CONTENT_URI, null);

        return notify;
    }

    private boolean performSyncRefresh(TodoListRestClient client) {

        boolean notify = false;
        TodoListRestClient.EntryListResponse response = client.getEntries(null);
        if (response.getResponse().getStatusCode() == TodoListRestClient.Response.SUCCESS_OK) {
            List<JSONObject> entries = response.getEntryList();
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            String tempTableName = Schema.Entries.TABLE_NAME + "_refresh";

            db.beginTransaction();
            try {
                // Create a Temporary Table to store all the dirty entries
                db.execSQL("CREATE TEMP TABLE " + tempTableName
                        + " AS SELECT * from " + Schema.Entries.TABLE_NAME
                        + " WHERE " + Schema.Entries.PENDING_TX + " = 1"
                        + " OR " + Schema.Entries.PENDING_DELETE + " = 1"
                        + " OR " + Schema.Entries.PENDING_UPDATE + " = 1;");

                // Replace the current table with the entries from the refresh
                db.delete(Schema.Entries.TABLE_NAME, null, null);
                for (JSONObject entry : entries)
                    db.insert(Schema.Entries.TABLE_NAME, Schema.Entries.TITLE, entryObjectValues(entry));

                // Now add back the Temporary items as an UPDATE. This insures that deleted
                //  dirty items are deleted                           KEY

                Cursor cur = db.query(tempTableName,null,null,null,null,null,null);
                String where = Schema.Entries.ID + "= ?";
                for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext()) {
                    String[] whereArgs = {cur.getString(cur.getColumnIndex(Schema.Entries.ID))};
                    ContentValues values = new ContentValues();
                    for (String column : cur.getColumnNames())
                        values.put(column, cur.getString(cur.getColumnIndex(column)));
                    db.update(Schema.Entries.TABLE_NAME, values, where, whereArgs);
                }

                lastSyncTime = response.getTimestamp();
                commitLastSyncTime();
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
                // Clean up the temporary Table
                db.execSQL("DROP TABLE IF EXISTS" + tempTableName + ";");
            }
        }

        if (notify)
            getContext().getContentResolver().notifyChange(Schema.Entries.CONTENT_URI, null);

        return notify;
    }

    private void setStateFlags(String tableName,String where,String[] whereArgs,
                               boolean pendingTx,boolean pendingDelete,
                               boolean pendingUpdate){
        ContentValues values = new ContentValues();
        values.put(Schema.Entries.PENDING_TX, pendingTx ? 1 : 0);
        values.put(Schema.Entries.PENDING_UPDATE, pendingDelete ? 1 : 0);
        values.put(Schema.Entries.PENDING_DELETE, pendingUpdate ? 1 : 0);


        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.update(Schema.Entries.TABLE_NAME, values, where, whereArgs);
    }

    public void performUpstreamSync(TodoListRestClient client) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String tempTableName = Schema.Entries.TABLE_NAME + "_upsync";

        ContentValues values = new ContentValues();
        values.put(Schema.Entries.PENDING_TX, 1);
        values.put(Schema.Entries.PENDING_UPDATE, 0);
        //values.put(Schema.Entries.PENDING_DELETE, 0);

        db.beginTransaction();
        try {
            // Create a Temporary Table to store all the syncable items
            db.execSQL("CREATE TEMP TABLE " + tempTableName
                    + " AS SELECT * from " + Schema.Entries.TABLE_NAME
                    + " WHERE " + WHERE_DIRTY_NON_PENDING + ";");

            // Update the pending flags to indicate that the entry
            //  is currently being processed
            db.update(Schema.Entries.TABLE_NAME, values, WHERE_DIRTY_NON_PENDING, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        try {
            String idWhere = Schema.Entries._ID + " = ?";
            // Walk through the temporary table of syncable items and perform
            //  the pending action
            Cursor cur = db.query(tempTableName,null,null,null,null,null,Schema.Entries.DEFAULT_SORT_ORDER);
            for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext()) {
                String[] whereArgs = {Integer.toString(cur.getInt(cur.getColumnIndex(Schema.Entries._ID)))};
                int id = cur.getInt(cur.getColumnIndex(Schema.Entries.ID));
                TodoListRestClient.EntryObjectResponse response;

                if (cur.getInt(cur.getColumnIndex(Schema.Entries.PENDING_DELETE)) == 1) {
                    // Attempt to delete the item via the client, if it succeeds, delete it locally
                    response = client.deleteEntry(id);
                    if (response.getResponse().getStatusCode() == TodoListRestClient.Response.SUCCESS_OK) {
                        db.delete(Schema.Entries.TABLE_NAME, idWhere, whereArgs);
                    } else {
                        // If it fails, reset the pending flags to indicate that it needs to be
                        //  retried
                        setStateFlags(idWhere,)
                        values.clear();
                        values.put(Schema.Entries.PENDING_DELETE, 1);
                        values.put(Schema.Entries.PENDING_TX, 0);
                        db.update(Schema.Entries.TABLE_NAME, values, idWhere, whereArgs);
                    }
                } else if (cur.getInt(cur.getColumnIndex(Schema.Entries.PENDING_UPDATE)) == 1) {
                    values.clear();
                    values.put(Schema.Entries.TITLE, cur.getString(cur.getColumnIndex(Schema.Entries.TITLE)));
                    values.put(Schema.Entries.NOTES, cur.getString(cur.getColumnIndex(Schema.Entries.NOTES)));
                    values.put(Schema.Entries.COMPLETE, cur.getInt(cur.getColumnIndex(Schema.Entries.COMPLETE)));

                    // If the entry was updated, check if it has a valid ID. If it
                    //  is zero, the entry need to be inserted, otherwise update

                    if (id == 0) {
                        response = client.postEntry(values);
                    } else {
                        response = client.putEntry(id,values);
                    }
                    // If success, update the local entry if it is not dirty
                    if (response.getResponse().getStatusCode() == TodoListRestClient.Response.SUCCESS_OK) {
                        values = entryObjectValues(response.getEntryObject());
                        values.put(Schema.Entries.PENDING_TX, 0);
                        String where = idWhere + " AND (" + Schema.Entries.PENDING_DELETE + " = 0"
                                                + " AND " + Schema.Entries.PENDING_UPDATE + " = 0)";
                        db.update(Schema.Entries.TABLE_NAME, values, where, whereArgs);
                    } else {
                        // If it fails, make sure that the flags are reset
                        //  to indicate that it needs to be retried
                        values.clear();
                        values.put(Schema.Entries.PENDING_UPDATE, 1);
                        values.put(Schema.Entries.PENDING_TX, 0);
                        db.update(Schema.Entries.TABLE_NAME, values, idWhere, whereArgs);
                    }
                }
            }
        } finally {
            db.execSQL("DROP TABLE " + tempTableName + ";");
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

    private void requestSync() {
        getContext().startService(new Intent(TodoListSyncService.ACTION_TODOLIST_SYNC));
    }

    private void initLastSyncTime() {
        FileInputStream stream = null;

        try {
            stream = getContext().openFileInput(LAST_SYNC_TIME_FILENAME);
            byte[] buffer = new byte[128];
            if (stream.read(buffer, 0, buffer.length) > 0) {
                lastSyncTime = Double.parseDouble(new String(buffer));
            }

        } catch (IOException e) {
            Log.e(TAG, "Failed to retrieve last sync time: " + LAST_SYNC_TIME_FILENAME);
            lastSyncTime = 0;
        } finally {
            try {
                if (stream != null)
                    stream.close();
            } catch (IOException e) {
            }
        }

    }

    private void commitLastSyncTime() {
        FileOutputStream stream = null;

        try {
            stream = getContext().openFileOutput(LAST_SYNC_TIME_FILENAME, Context.MODE_PRIVATE);
            stream.write(String.format("%f", lastSyncTime).getBytes());

        } catch (IOException e) {
            Log.e(TAG, "Failed to save last sync time: " + LAST_SYNC_TIME_FILENAME);
        } finally {
            try {
                if (stream != null)
                    stream.close();
            } catch (IOException e) {
            }
        }
    }

}
