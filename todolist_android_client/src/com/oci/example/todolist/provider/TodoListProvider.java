package com.oci.example.todolist.provider;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.*;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.util.Log;
import com.oci.example.todolist.R;
import com.oci.example.todolist.TodoListActivity;
import com.oci.example.todolist.TodoListSyncHelper;
import com.oci.example.todolist.client.HttpRestClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides access to a database of todolist entries. Each entry has an id, a title, notes,
 * and a completed flag
 */
public class TodoListProvider extends ContentProvider implements RestDataProvider {

    // Used for debugging and logging
    private static final String TAG = "TodoListProvider";


    // Name of the underlying sqlite database
    private static final String DATABASE_NAME = "todolist.db";

    // Current version of the underlying sqlite database
    private static final int DATABASE_VERSION = 1;

    /**
     * A UriMatcher Definitions
     * The Uri matcher allows the handlers to identify what data is to affected
     */
    // Constants used by the Uri matcher to identify URI matches
    private static final int ENTRIES = 1;
    private static final int ENTRY_ID = 2;

    // Reference to a URI matcher
    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    // Add both the CONTENT_URI and CONTENT_ID_URI uri's to the matcher
    static {
        uriMatcher.addURI(TodoListSchema.AUTHORITY, TodoListSchema.Entries.PATH_TODOLIST_ENTRIES, ENTRIES);
        uriMatcher.addURI(TodoListSchema.AUTHORITY, TodoListSchema.Entries.PATH_TODOLIST_ENTRY_ID + "#", ENTRY_ID);
    }

    /**
     * A helper class to manage database creation and version management.
     * <p/>
     * NOTE: this will defer opening and upgrading the database until first use,
     * to avoid blocking application startup with long-running database upgrades. Best
     * practice is to access the provider in a background task, or to use a Loader
     */
    class DatabaseHelper extends SQLiteOpenHelper {

        /**
         * Constructor - tells the base class the name of
         * database and current version
         *
         * @param context context of the provider
         */
        DatabaseHelper(Context context) {

            // calls the super constructor, requesting the default cursor factory.
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        /**
         * Called when the database is created for the first time. This will create
         * the TodoList table as defined in the schema. This will also initialize
         * the lastSyncTime to 0 ( the beginning of time)
         *
         * @param db instance of a writable database
         */
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TodoListSchema.Entries.TABLE_NAME + " ("
                    + TodoListSchema.Entries._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + TodoListSchema.Entries.ID + " INTEGER KEY UNIQUE DEFAULT NULL,"
                    + TodoListSchema.Entries.TITLE + " TEXT,"
                    + TodoListSchema.Entries.NOTES + " TEXT,"
                    + TodoListSchema.Entries.COMPLETE + " INTEGER,"
                    + TodoListSchema.Entries.CREATED + " LONG,"
                    + TodoListSchema.Entries.MODIFIED + " LONG,"
                    + TodoListSchema.Entries.PENDING_TX + " INTEGER DEFAULT 0,"
                    + TodoListSchema.Entries.PENDING_UPDATE + " INTEGER KEY DEFAULT 0,"
                    + TodoListSchema.Entries.PENDING_DELETE + " INTEGER KEY DEFAULT 0"
                    + ");");

            lastSyncTime = 0;
            commitLastSyncTime();
        }


        /**
         * Called when the database needs to be upgraded. This will drop the table, wiping all
         * the data. This is acceptable as a sync will result in a full refresh.
         *
         * @param db         instance of a writable database
         * @param oldVersion old version
         * @param newVersion new version
         */
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            // Logs that the database is being upgraded
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");

            // Kills the table and existing data
            db.execSQL("DROP TABLE IF EXISTS " + TodoListSchema.Entries.TABLE_NAME);

            // Recreates the database with a new version
            onCreate(db);
        }
    }

    // DatabaseHelper reference
    private DatabaseHelper dbHelper;

    /**
     * This is for unit tests, returns direct access to a the database to
     * prepopulate data for tests
     *
     * @return A writable SQLite database object for the data being managed by the provider
     */
    SQLiteDatabase getWritableDatabase() {
        return dbHelper.getWritableDatabase();
    }

    /**
     * Static WHERE clauses
     */
    private static final String WHERE_DIRTY_ENTRIES = " (" + TodoListSchema.Entries.PENDING_DELETE + " > 0"
            + " OR " + TodoListSchema.Entries.PENDING_UPDATE + " > 0) ";

    private static final String WHERE_CURRENT_ENTRIES = " (" + TodoListSchema.Entries.PENDING_DELETE + " = 0"
            + " AND " + TodoListSchema.Entries.PENDING_UPDATE + " = 0) ";

    private static final String WHERE_NON_DELETED_ENTRIES = TodoListSchema.Entries.PENDING_DELETE + " = 0";

    // Reference to the system wide Notification Manager to notify the user of Sync results
    private NotificationManager notificationManager;
    // Filename that persists the lastSyncTime for updates
    private static String LAST_SYNC_TIME_FILENAME = "lastSyncTime";
    // Current lastSyncTimeValue
    private double lastSyncTime = 0;

    /**
     * This method is called for all registered content providers on the application main thread at
     * application launch time. It must not perform lengthy operations, or application startup
     * will be delayed.
     *
     * @return if the provider was successfully loaded, false otherwise
     */
    @Override
    public boolean onCreate() {
        // Creates a new helper object. Note that the database itself isn't opened until
        // something tries to access it, and it's only created if it doesn't already exist.
        dbHelper = new DatabaseHelper(getContext());

        // Get a reference to the notification manager
        notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);

        // Initialize the last sync time for its persisted file
        initLastSyncTime();

        // Assumes that any failures will be reported by a thrown exception.
        return true;
    }

    /**
     * This handles requests for the MIME type of the data at the given URI
     *
     * @param uri Uri to query
     * @return a MIME type string, or null if there is no type
     */
    @Override
    public String getType(Uri uri) {

        // Return the MIME type based on the incoming URI pattern
        switch (uriMatcher.match(uri)) {

            case ENTRIES:
                return TodoListSchema.Entries.CONTENT_TYPE;

            case ENTRY_ID:
                return TodoListSchema.Entries.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * Handles query requests from clients
     *
     * @param uri       Uri to query
     * @param what      list of columns to put into the cursor. If null all columns are included.
     * @param where     selection criteria to apply when filtering rows. If null then all rows are included
     * @param whereArgs any included '?'s in where will be replaced by the values from whereArgs,
     *                  in order that they appear in the selection. The values will be bound as Strings.
     * @param sortOrder sort order for the rows in the cursor. If null then the the
     *                  TodoListSchema.DEFAULT_SORT_ORDER will be used
     * @return a cursor or null
     */
    @Override
    public Cursor query(Uri uri, String[] what, String where, String[] whereArgs, String sortOrder) {

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        String orderBy;
        switch (uriMatcher.match(uri)) {

            case ENTRY_ID:
                /**
                 *  When an ENTRY_ID resource is specified, a filter to include that entry id  is
                 *  added to the where clause
                 */
                qb.appendWhere(TodoListSchema.Entries._ID + "=" +
                        uri.getPathSegments().get(TodoListSchema.Entries.TODOLIST_ENTRY_ID_PATH_POSITION) + " AND ");
                // fall through
            case ENTRIES:
                /**
                 * For all requests, entries that have the PENDING_DELETE flag are not returned in the
                 * query and treated as if they have been deleted.
                 */
                qb.appendWhere(TodoListSchema.Entries.PENDING_DELETE + "=" + 0);
                qb.setTables(TodoListSchema.Entries.TABLE_NAME);
                if (sortOrder != null) {
                    orderBy = sortOrder;
                } else {
                    orderBy = TodoListSchema.Entries.DEFAULT_SORT_ORDER;
                }
                break;

            default:
                // If the URI doesn't match any of the known patterns, throw an exception.
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // Perform the query
        Cursor cur = qb.query(dbHelper.getReadableDatabase(), what, where, whereArgs, null, null, orderBy);

        // This is required register the cursor, with the resolver, for notifications on the specified URI.
        cur.setNotificationUri(getContext().getContentResolver(), uri);

        return cur;
    }

    /**
     * Handles requests to insert a new row
     *
     * @param uri  Uri of the insertion request.
     * @param contentValues set of column_name/value pairs to add to the database
     * @return Uri for the newly inserted item
     */
    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {

        // Initialize a new ContentValues object to whatever was passed in
        ContentValues values = new ContentValues();
        if (contentValues != null)
            values.putAll(contentValues);

        long now = System.currentTimeMillis();
        long newId;

        switch (uriMatcher.match(uri)) {
            case ENTRIES:
                /**
                 * Initialize the PENDING_UPDATE to 2. This indicates that
                 * the entry needs to synced upstream. This is a tri-state flag
                 * used to indicate that the sync operation has been staged and
                 * completed.
                 */
                values.put(TodoListSchema.Entries.PENDING_UPDATE, 2);
                values.put(TodoListSchema.Entries.PENDING_DELETE, 0);

                // Initialize values, that are omitted, to defaults
                if (!values.containsKey(TodoListSchema.Entries.CREATED))
                    values.put(TodoListSchema.Entries.CREATED, now);

                if (!values.containsKey(TodoListSchema.Entries.MODIFIED))
                    values.put(TodoListSchema.Entries.MODIFIED, now);

                if (!values.containsKey(TodoListSchema.Entries.TITLE))
                    values.put(TodoListSchema.Entries.TITLE,
                            Resources.getSystem()
                                    .getString(android.R.string.untitled));

                if (!values.containsKey(TodoListSchema.Entries.NOTES))
                    values.put(TodoListSchema.Entries.NOTES, "");

                if (!values.containsKey(TodoListSchema.Entries.COMPLETE))
                    values.put(TodoListSchema.Entries.COMPLETE, 0);

                // Perform the insert
                newId = dbHelper.getWritableDatabase()
                            .insert(TodoListSchema.Entries.TABLE_NAME, null, values);

                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // If the insert succeeded, the newly inserted row will be assigned an ID.
        //  If it is zero, it failed.
        if (newId == 0)
            throw new IllegalArgumentException("Failed to insert row into " + uri);

        // Notify changes on the newly created entry URI
        Uri entryUri = ContentUris.withAppendedId(TodoListSchema.Entries.CONTENT_ID_URI_BASE, newId);
        getContext().getContentResolver().notifyChange(entryUri, null);

        // Request a lazy sync to sync this change upstream
        try{
            TodoListSyncHelper.requestLazySync(getContext());
        } catch (UnsupportedOperationException e) {
            // This will happen when running unit tests, just ignore
        }
        return entryUri;
    }

    /**
     * Helper class for building 'where' clause strings
     */
    @SuppressWarnings({"UnusedDeclaration"})
    private class WhereStringBuilder {
        // Base where clause
        String where;
        // string to append to the base
        String append;

        WhereStringBuilder() {
            this.where = null;
            this.append = null;
        }

        WhereStringBuilder(String where) {
            this.where = where;
            this.append = null;
        }

        void appendAnd(String append) {
            if (this.append != null) {
                this.append += " AND " + append;
            } else {
                this.append = append;
            }
        }

        void appendAndParenthesis(String append) {
            if (this.append != null) {
                this.append += " AND (" + append + ")";
            } else {
                this.append = append;
            }
        }

        void appendOr(String append) {
            if (this.append != null) {
                this.append += " OR " + append;
            } else {
                this.append = append;
            }
        }

        void appendOrParenthesis(String append) {
            if (this.append != null) {
                this.append += " OR (" + append + ")";
            } else {
                this.append = append;
            }
        }

        public String build() {
            if (where != null) {
                return where + " AND (" + append + ")";
            } else {
                return append;
            }
        }
    }

    /**
     * Handles requests to delete one or more rows
     *
     * @param uri  Uri to query
     * @param where     selection criteria to apply when deleting rows. If null then all rows are deleted
     * @param whereArgs any included '?'s in where will be replaced by the values from whereArgs,
     *                  in order that they appear in the selection. The values will be bound as Strings.
     * @return number of rows deleted
     */
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {

        ContentValues values = new ContentValues();
        int count;

        WhereStringBuilder whereBuilder = new WhereStringBuilder(where);
        switch (uriMatcher.match(uri)) {

            case ENTRY_ID:
                /**
                 *  When an ENTRY_ID resource is specified, a filter to include that entry id  is
                 *  added to the where clause
                 */
                whereBuilder.appendAnd(TodoListSchema.Entries._ID + " = " + getEntryIdFromUri(uri));
                //fall through
            case ENTRIES:
                // Append where clause to include all PENDING_DELETE = 0 rows
                whereBuilder.appendAnd(WHERE_NON_DELETED_ENTRIES);

                /**
                 * The delete doesn't immediately delete, it set the  PENDING_DELETE flag
                 * to 1. This will trigger an upstream sync of the deleted item. Once it is
                 * delete upstream, it will be deleted completely from the database.
                 */
                values.put(TodoListSchema.Entries.PENDING_DELETE, 1);

                count = dbHelper.getWritableDatabase().update(TodoListSchema.Entries.TABLE_NAME,
                        values, whereBuilder.build(), whereArgs);
                break;

            // If the incoming pattern is invalid, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (count > 0) {
            // If any rows where affected, notify listeners and request a lazy sync
            getContext().getContentResolver().notifyChange(uri, null);
            try{
                TodoListSyncHelper.requestLazySync(getContext());
            } catch (UnsupportedOperationException e) {
                // This will happen when running unit tests, just ignore
            }
        }
        return count;
    }

    /**
     * Handles requests to update one or more rows
     * @param uri Uri to query
     * @param contentValues bundle mapping column names to new column values
     * @param where     selection criteria to apply when updating rows. If null then all rows are updated
     * @param whereArgs any included '?'s in where will be replaced by the values from whereArgs,
     *                  in order that they appear in the selection. The values will be bound as Strings.
     * @return number of rows updated
     */
    @Override
    public int update(Uri uri, ContentValues contentValues, String where, String[] whereArgs) {

        // Initialize a new ContentValues object to whatever was passed in
        ContentValues values = new ContentValues();
        if (contentValues != null)
            values.putAll(contentValues);

        WhereStringBuilder wherebuilder = new WhereStringBuilder(where);
        int count;
        switch (uriMatcher.match(uri)) {

            case ENTRY_ID:
                /**
                 *  When an ENTRY_ID resource is specified, a filter to include that entry id  is
                 *  added to the where clause
                 */
                wherebuilder.appendAnd(TodoListSchema.Entries._ID + " = " + getEntryIdFromUri(uri));
                // fall through
            case ENTRIES:
                // Append where clause to include all PENDING_DELETE = 0 rows
                wherebuilder.appendAnd(WHERE_NON_DELETED_ENTRIES);

                // Set the Modified times to NOW if not set
                values.put(TodoListSchema.Entries.MODIFIED, System.currentTimeMillis());
                // Mark the entry as pending an update
                values.put(TodoListSchema.Entries.PENDING_UPDATE, 2);

                // Perform the update
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                count = db.update(TodoListSchema.Entries.TABLE_NAME, values, wherebuilder.build(), whereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }


        if (count > 0) {
            // If any rows where affected, notify listeners and request a lazy sync
            getContext().getContentResolver().notifyChange(uri, null);
            try{
                TodoListSyncHelper.requestLazySync(getContext());
            } catch (UnsupportedOperationException e) {
                // This will happen when running unit tests, just ignore
            }
        }

        return count;
    }

    /**
     * Handles requests to perform a batch of operation
     * This implementation will perform the operations in a transaction. In the event
     * of any operation failing, the transation will be rolled back
     *
     * @param operations operations to apply
     * @return list of results of the operations
     */
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

    // Todo: implement a sync status return code
    @Override
    synchronized public void onPerformSync(HttpRestClient httpRestClient, boolean refresh) {

        TodoListRestClient client = new TodoListRestClient(httpRestClient);
        boolean notify;
        try {
            if (refresh) {
                dbHelper.getWritableDatabase().delete(TodoListSchema.Entries.TABLE_NAME, null, null);
                lastSyncTime = 0;
            } else {
                performUpstreamSync(client);
            }

            if (lastSyncTime > 0) {
                notify = performSyncUpdate(client);
            } else {
                notify = performSyncRefresh(client);
            }

            if (notify) {
                getContext().getContentResolver().notifyChange(TodoListSchema.Entries.CONTENT_URI, null);
                showUpdateNotification("TodoList Updated", "TodoList Updated", "New Entries");
            }
        } catch (IOException e) {
            Log.e(TAG, "Network Error: " + e.getMessage());
            showSyncErrorNotification("TodoList Sync Error", "Network Error", e.getMessage());
        } catch (URISyntaxException e) {
            Log.e(TAG, "Invalid request: " + e.getMessage());
            showSyncErrorNotification("TodoList Sync Error", "Invalid Request", e.getMessage());
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid request: " + e.getMessage());
            showSyncErrorNotification("TodoList Sync Error", "Invalid Request", e.getMessage());
        } catch (JSONException e) {
            Log.e(TAG, "Invalid response: " + e.getMessage());
            showSyncErrorNotification("TodoList Sync Error", "Invalid Response", e.getMessage());
        } finally {
            ContentValues pendingTxValue = new ContentValues();
            pendingTxValue.put(TodoListSchema.Entries.PENDING_TX, 0);
            if (getWritableDatabase().update(TodoListSchema.Entries.TABLE_NAME,
                    pendingTxValue, TodoListSchema.Entries.PENDING_TX + "=1", null) > 0)
                getContext().getContentResolver().notifyChange(TodoListSchema.Entries.CONTENT_URI, null);
        }
    }

    private static ContentValues entryObjectValues(JSONObject entry) throws JSONException {
        ContentValues entryValues = new ContentValues();

        entryValues.put(TodoListSchema.Entries.ID, entry.getInt(TodoListRestClient.ENTRY_ID));
        entryValues.put(TodoListSchema.Entries.COMPLETE,
                entry.getBoolean(TodoListRestClient.ENTRY_COMPLETE) ? 1 : 0);
        String title = entry.optString(TodoListRestClient.ENTRY_TITLE);
        if (!title.isEmpty())
            entryValues.put(TodoListSchema.Entries.TITLE, title);
        String notes = entry.optString(TodoListRestClient.ENTRY_NOTES);
        if (!notes.isEmpty())
            entryValues.put(TodoListSchema.Entries.NOTES, notes);

        entryValues.put(TodoListSchema.Entries.CREATED,
                (long) (entry.getDouble(TodoListRestClient.ENTRY_CREATED) * 1000));
        entryValues.put(TodoListSchema.Entries.MODIFIED,
                (long) (entry.getDouble(TodoListRestClient.ENTRY_MODIFIED) * 1000));

        return entryValues;

    }

    private boolean performSyncUpdate(TodoListRestClient client)
            throws IOException, URISyntaxException, JSONException {
        boolean notify = false;

        // Perform an update sync
        TodoListRestClient.EntryListResponse response = client.getEntries(lastSyncTime);
        int statusCode = response.getResponse().getStatusCode();
        if (statusCode == TodoListRestClient.Response.SUCCESS_OK) {
            List<JSONObject> entries = response.getEntryList();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            String idWhere = TodoListSchema.Entries.ID + " = ?";

            SQLiteStatement entryCount = db.compileStatement(
                    "SELECT COUNT(*) FROM " + TodoListSchema.Entries.TABLE_NAME + " WHERE " + idWhere);
            db.beginTransaction();
            try {
                for (JSONObject entry : entries) {
                    long id = entry.getLong(TodoListRestClient.ENTRY_ID);
                    String[] whereArgs = {Long.toString(id)};
                    if (entry.getBoolean(TodoListRestClient.ENTRY_DELETED)) {
                        // If the entry is deleted, remove it from the local database
                        //  regardless of whether or not it is dirty. If its been deleted,
                        //  our local changes are irrelevant.
                        if (db.delete(TodoListSchema.Entries.TABLE_NAME, idWhere, whereArgs) > 0)
                            notify = true;
                    } else {
                        ContentValues values = entryObjectValues(entry);
                        entryCount.bindLong(1, id);
                        if (entryCount.simpleQueryForLong() > 0) {

                            String where = idWhere
                                    + " AND " + WHERE_CURRENT_ENTRIES
                                    + " AND " + TodoListSchema.Entries.MODIFIED
                                    + " != " + values.getAsLong(TodoListSchema.Entries.MODIFIED);
                            if (db.update(TodoListSchema.Entries.TABLE_NAME, values, where, whereArgs) > 0)
                                notify = true;
                        } else {
                            db.insert(TodoListSchema.Entries.TABLE_NAME, TodoListSchema.Entries.TITLE, values);
                            notify = true;
                        }
                    }
                }
                lastSyncTime = response.getTimestamp();
                commitLastSyncTime();
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } else if (statusCode == TodoListRestClient.Response.FAILED_BAD_REQUEST) {
            // A bad request is returned if the last sync time is out of the acceptable
            //  window. In this case, we need to do a refresh.
            notify = performSyncRefresh(client);
        }

        return notify;
    }

    private boolean performSyncRefresh(TodoListRestClient client)
            throws IOException, URISyntaxException, JSONException {

        boolean notify = false;
        TodoListRestClient.EntryListResponse response = client.getEntries();
        if (response.getResponse().getStatusCode() == TodoListRestClient.Response.SUCCESS_OK) {
            List<JSONObject> entries = response.getEntryList();
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            String tempTableName = TodoListSchema.Entries.TABLE_NAME + "_refresh";

            db.beginTransaction();
            try {
                // Create a Temporary Table to store all the dirty entries
                db.execSQL("CREATE TEMP TABLE " + tempTableName
                        + " AS SELECT * from " + TodoListSchema.Entries.TABLE_NAME
                        + " WHERE " + WHERE_DIRTY_ENTRIES);

                // Replace the current table with the entries from the refresh
                db.delete(TodoListSchema.Entries.TABLE_NAME, null, null);
                for (JSONObject entry : entries)
                    db.insert(TodoListSchema.Entries.TABLE_NAME, TodoListSchema.Entries.TITLE, entryObjectValues(entry));

                // Now add back the Temporary items as an UPDATE. This insures that deleted
                //  dirty items are deleted                           KEY

                Cursor cur = db.query(tempTableName, null, null, null, null, null, null);
                String where = TodoListSchema.Entries.ID + "= ?";
                for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext()) {
                    String[] whereArgs = {cur.getString(cur.getColumnIndex(TodoListSchema.Entries.ID))};
                    ContentValues values = new ContentValues();
                    for (String column : cur.getColumnNames())
                        values.put(column, cur.getString(cur.getColumnIndex(column)));
                    db.update(TodoListSchema.Entries.TABLE_NAME, values, where, whereArgs);
                }

                lastSyncTime = response.getTimestamp();
                commitLastSyncTime();
                notify = true;
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
                // Clean up the temporary Table
                db.execSQL("DROP TABLE IF EXISTS " + tempTableName + ";");
            }
        }

        return notify;
    }

    public void performUpstreamSync(TodoListRestClient client) throws IOException {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String tempTableName = TodoListSchema.Entries.TABLE_NAME + "_upsync";

        ContentValues values = new ContentValues();
        values.put(TodoListSchema.Entries.PENDING_UPDATE, 1);
        values.put(TodoListSchema.Entries.PENDING_TX, 1);

        db.beginTransaction();
        try {
            // Create a Temporary Table to store all the syncable items
            db.execSQL("CREATE TEMP TABLE " + tempTableName
                    + " AS SELECT * from " + TodoListSchema.Entries.TABLE_NAME
                    + " WHERE " + WHERE_DIRTY_ENTRIES + ";");

            // Update the pending flags to indicate that the entry
            //  is currently being processed
            db.update(TodoListSchema.Entries.TABLE_NAME, values, WHERE_DIRTY_ENTRIES, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        try {
            String idWhere = TodoListSchema.Entries._ID + " = ?";
            // Walk through the temporary table of syncable items and perform
            //  the pending action
            Cursor cur = db.query(tempTableName, null, null, null, null, null, TodoListSchema.Entries.DEFAULT_SORT_ORDER);
            for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext()) {
                String[] whereArgs = {Integer.toString(cur.getInt(cur.getColumnIndex(TodoListSchema.Entries._ID)))};
                int id = cur.getInt(cur.getColumnIndex(TodoListSchema.Entries.ID));


                try {
                    if (cur.getInt(cur.getColumnIndex(TodoListSchema.Entries.PENDING_DELETE)) > 0) {
                        // Attempt to delete the item via the client, if it succeeds, delete it locally
                        TodoListRestClient.Response response = client.deleteEntry(id);
                        if (response.getResponse().getStatusCode() == TodoListRestClient.Response.SUCCESS_OK) {
                            db.delete(TodoListSchema.Entries.TABLE_NAME, idWhere, whereArgs);
                        }
                    } else if (cur.getInt(cur.getColumnIndex(TodoListSchema.Entries.PENDING_UPDATE)) > 0) {
                        values.clear();
                        values.put(TodoListSchema.Entries.TITLE, cur.getString(cur.getColumnIndex(TodoListSchema.Entries.TITLE)));
                        values.put(TodoListSchema.Entries.NOTES, cur.getString(cur.getColumnIndex(TodoListSchema.Entries.NOTES)));
                        values.put(TodoListSchema.Entries.COMPLETE, cur.getInt(cur.getColumnIndex(TodoListSchema.Entries.COMPLETE)));

                        // If the entry was updated, check if it has a valid ID. If it
                        //  is zero, the entry need to be inserted, otherwise update
                        TodoListRestClient.EntryObjectResponse response;
                        if (id == 0) {
                            response = client.postEntry(values);
                        } else {
                            response = client.putEntry(id, values);
                        }
                        // If success, update the local entry if it is not dirty
                        int statusCode = response.getResponse().getStatusCode();
                        if (statusCode == TodoListRestClient.Response.SUCCESS_OK
                                || statusCode == TodoListRestClient.Response.SUCCESS_ADDED) {
                            values = entryObjectValues(response.getEntryObject());
                            values.remove(TodoListSchema.Entries.TITLE);
                            values.remove(TodoListSchema.Entries.NOTES);
                            values.remove(TodoListSchema.Entries.COMPLETE);
                            db.beginTransaction();
                            try {
                                long pendingTx = DatabaseUtils.longForQuery(db,
                                        "SELECT " + TodoListSchema.Entries.PENDING_UPDATE
                                                + " FROM " + TodoListSchema.Entries.TABLE_NAME
                                                + " WHERE " + idWhere, whereArgs);
                                values.put(TodoListSchema.Entries.PENDING_UPDATE, pendingTx - 1);
                                db.update(TodoListSchema.Entries.TABLE_NAME, values, idWhere, whereArgs);

                                // Notify the content observers that the status of the entry
                                //  has changed. Specifically, it is now synced.
                                getContext().getContentResolver().notifyChange(
                                        ContentUris.withAppendedId(
                                                TodoListSchema.Entries.CONTENT_ID_URI_BASE,
                                                values.getAsInteger(TodoListSchema.Entries.ID)),
                                        null);

                                db.setTransactionSuccessful();
                            } finally {
                                db.endTransaction();
                            }
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Invalid response: " + e.getMessage());
                } catch (URISyntaxException e) {
                    Log.e(TAG, "Invalid request: " + e.getMessage());
                }
            }
        } finally {
            db.execSQL("DROP TABLE " + tempTableName + ";");
        }
    }

    private static int getEntryIdFromUri(Uri uri) {
        return Integer.parseInt(uri.getPathSegments().get(TodoListSchema.Entries.TODOLIST_ENTRY_ID_PATH_POSITION));
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
            } catch (IOException ignored) {
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
            } catch (IOException ignored) {
            }
        }
    }


    private static final int SYNC_UPDATE_ID = 1;

    private void showUpdateNotification(CharSequence tickerText, CharSequence contentTitle, CharSequence contentText) {

        Notification notification = new Notification(R.drawable.icon, tickerText, System.currentTimeMillis());
        notification.defaults |= Notification.DEFAULT_ALL;

        Intent notificationIntent = new Intent(getContext(), TodoListActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(getContext(), 0, notificationIntent, 0);

        notification.setLatestEventInfo(getContext(), contentTitle, contentText, contentIntent);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        notificationManager.cancel(SYNC_ERROR_ID);
        notificationManager.notify(SYNC_UPDATE_ID, notification);
    }

    private static final int SYNC_ERROR_ID = 2;

    private void showSyncErrorNotification(CharSequence tickerText, CharSequence contentTitle, CharSequence contentText) {

        Notification notification = new Notification(R.drawable.icon, tickerText, System.currentTimeMillis());
        notification.defaults |= Notification.DEFAULT_ALL;

        Intent notificationIntent = new Intent(getContext(), TodoListActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(getContext(), 0, notificationIntent, 0);

        notification.setLatestEventInfo(getContext(), contentTitle, contentText, contentIntent);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(SYNC_ERROR_ID, notification);
    }

}
