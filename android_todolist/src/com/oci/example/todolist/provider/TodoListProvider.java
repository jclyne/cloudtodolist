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
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import com.oci.example.todolist.R;
import com.oci.example.todolist.TodoListActivity;
import com.oci.example.todolist.TodoListSyncHelper;
import com.oci.example.todolist.client.HttpRestClient;
import com.oci.example.todolist.client.TodoListRestClient;
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
            public static final String PENDING_UPDATE = "pending_update";
            public static final String PENDING_DELETE = "pending_delete";
            public static final String PENDING_TX = "pending_tx";
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
                    + Schema.Entries.ID + " INTEGER KEY UNIQUE DEFAULT NULL,"
                    + Schema.Entries.TITLE + " TEXT,"
                    + Schema.Entries.NOTES + " TEXT,"
                    + Schema.Entries.COMPLETE + " INTEGER,"
                    + Schema.Entries.CREATED + " LONG,"
                    + Schema.Entries.MODIFIED + " LONG,"
                    + Schema.Entries.PENDING_TX + " INTEGER DEFAULT 0,"
                    + Schema.Entries.PENDING_UPDATE + " INTEGER KEY DEFAULT 0,"
                    + Schema.Entries.PENDING_DELETE + " INTEGER KEY DEFAULT 0"
                    + ");");

            lastSyncTime = 0;
            commitLastSyncTime();
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

    private static final String WHERE_DIRTY_ENTRIES = " (" + Schema.Entries.PENDING_DELETE + " > 0"
            + " OR " + Schema.Entries.PENDING_UPDATE + " > 0) ";

    private static final String WHERE_CURRENT_ENTRIES = " (" + Schema.Entries.PENDING_DELETE + " = 0"
            + " AND " + Schema.Entries.PENDING_UPDATE + " = 0) ";

    private static final String WHERE_NON_DELETED_ENTRIES = Schema.Entries.PENDING_DELETE + " = 0";

    private NotificationManager notificationManager;
    private static String LAST_SYNC_TIME_FILENAME = "lastSyncTime";
    private double lastSyncTime = 0;

    @Override
    public boolean onCreate() {
        // Creates a new helper object. Note that the database itself isn't opened until
        // something tries to access it, and it's only created if it doesn't already exist.
        dbHelper = new DatabaseHelper(getContext());

        Context ctxt = getContext();
        notificationManager = (NotificationManager) ctxt.getSystemService(Context.NOTIFICATION_SERVICE);

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
                values.put(Schema.Entries.PENDING_UPDATE, 2);
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
            getContext().getContentResolver().notifyChange(entryUri, null);
            TodoListSyncHelper.requestLazySync(getContext());
            return entryUri;
        }


        throw new IllegalArgumentException("Failed to insert row into " + uri);
    }

    @SuppressWarnings({"UnusedDeclaration"})
    private class WhereStringBuilder {
        String where;
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


    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {

        ContentValues values = new ContentValues();
        int count;

        WhereStringBuilder whereBuilder = new WhereStringBuilder(where);
        switch (uriMatcher.match(uri)) {

            case ENTRY_ID:
                whereBuilder.appendAnd(Schema.Entries._ID + " = " + getEntryIdFromUri(uri));
            case ENTRIES:
                whereBuilder.appendAnd(WHERE_NON_DELETED_ENTRIES);
                values.put(Schema.Entries.PENDING_DELETE, 1);

                count = dbHelper.getWritableDatabase().update(Schema.Entries.TABLE_NAME,
                                                values, whereBuilder.build(), whereArgs);
                break;

            // If the incoming pattern is invalid, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (count > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
            TodoListSyncHelper.requestLazySync(getContext());
        }

        return count;
    }


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
                wherebuilder.appendAnd(Schema.Entries._ID + " = " + getEntryIdFromUri(uri));
            case ENTRIES:
                wherebuilder.appendAnd(WHERE_NON_DELETED_ENTRIES);

                // Set the Modified times to NOW if not set
                values.put(Schema.Entries.MODIFIED, System.currentTimeMillis());
                // Mark the entry as pending a POST
                values.put(Schema.Entries.PENDING_UPDATE, 2);

                SQLiteDatabase db = dbHelper.getWritableDatabase();
                count = db.update(Schema.Entries.TABLE_NAME, values, wherebuilder.build(), whereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }


        if (count > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
            TodoListSyncHelper.requestLazySync(getContext());
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
    public void onPerformSync(HttpRestClient httpRestCleint, boolean refresh) {
        TodoListRestClient client = new TodoListRestClient(httpRestCleint);
        boolean notify;
        try {
            if (refresh) {
                dbHelper.getWritableDatabase().delete(Schema.Entries.TABLE_NAME, null, null);
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
                getContext().getContentResolver().notifyChange(Schema.Entries.CONTENT_URI, null);
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
        }
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

    private boolean performSyncUpdate(TodoListRestClient client)
                throws IOException, URISyntaxException, JSONException {
        boolean notify = false;

        // Perform an update sync
        TodoListRestClient.EntryListResponse response = client.getEntries(lastSyncTime);
        int statusCode = response.getResponse().getStatusCode();
        if (statusCode == TodoListRestClient.Response.SUCCESS_OK) {
            List<JSONObject> entries = response.getEntryList();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            String idWhere = Schema.Entries.ID + " = ?";

            SQLiteStatement entryCount = db.compileStatement(
                    "SELECT COUNT(*) FROM "+Schema.Entries.TABLE_NAME + " WHERE "+idWhere);
            db.beginTransaction();
            try {
                for (JSONObject entry : entries) {
                    long id =  entry.getLong(TodoListRestClient.ENTRY_ID);
                    String[] whereArgs = {Long.toString(id)};
                    if (entry.getInt(TodoListRestClient.ENTRY_DELETED) > 0) {
                        // If the entry is deleted, remove it from the local database
                        //  regardless of whether or not it is dirty. If its been deleted,
                        //  our local changes are irrelevant.
                        if (db.delete(Schema.Entries.TABLE_NAME, idWhere, whereArgs) > 0)
                            notify = true;
                    } else {
                        ContentValues values = entryObjectValues(entry);
                        entryCount.bindLong(1,id);
                        if (entryCount.simpleQueryForLong() > 0){

                            String where = idWhere
                                + " AND " + WHERE_CURRENT_ENTRIES
                                + " AND " + Schema.Entries.MODIFIED
                                    + " != "+values.getAsLong(Schema.Entries.MODIFIED);
                            if ( db.update(Schema.Entries.TABLE_NAME, values, where, whereArgs) > 0 )
                                notify = true;
                        } else {
                            db.insert(Schema.Entries.TABLE_NAME, Schema.Entries.TITLE,values);
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

            String tempTableName = Schema.Entries.TABLE_NAME + "_refresh";

            db.beginTransaction();
            try {
                // Create a Temporary Table to store all the dirty entries
                db.execSQL("CREATE TEMP TABLE " + tempTableName
                        + " AS SELECT * from " + Schema.Entries.TABLE_NAME
                        + " WHERE " + WHERE_DIRTY_ENTRIES);

                // Replace the current table with the entries from the refresh
                db.delete(Schema.Entries.TABLE_NAME, null, null);
                for (JSONObject entry : entries)
                    db.insert(Schema.Entries.TABLE_NAME, Schema.Entries.TITLE, entryObjectValues(entry));

                // Now add back the Temporary items as an UPDATE. This insures that deleted
                //  dirty items are deleted                           KEY

                Cursor cur = db.query(tempTableName, null, null, null, null, null, null);
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
        String tempTableName = Schema.Entries.TABLE_NAME + "_upsync";

        ContentValues values = new ContentValues();
        values.put(Schema.Entries.PENDING_UPDATE, 1);

        db.beginTransaction();
        try {
            // Create a Temporary Table to store all the syncable items
            db.execSQL("CREATE TEMP TABLE " + tempTableName
                    + " AS SELECT * from " + Schema.Entries.TABLE_NAME
                    + " WHERE " + WHERE_DIRTY_ENTRIES + ";");

            // Update the pending flags to indicate that the entry
            //  is currently being processed
            db.update(Schema.Entries.TABLE_NAME, values, WHERE_DIRTY_ENTRIES, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        try {
            String idWhere = Schema.Entries._ID + " = ?";
            // Walk through the temporary table of syncable items and perform
            //  the pending action
            Cursor cur = db.query(tempTableName, null, null, null, null, null, Schema.Entries.DEFAULT_SORT_ORDER);
            for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext()) {
                String[] whereArgs = {Integer.toString(cur.getInt(cur.getColumnIndex(Schema.Entries._ID)))};
                int id = cur.getInt(cur.getColumnIndex(Schema.Entries.ID));


                try {
                    if (cur.getInt(cur.getColumnIndex(Schema.Entries.PENDING_DELETE)) > 0) {
                        // Attempt to delete the item via the client, if it succeeds, delete it locally
                        TodoListRestClient.Response response = client.deleteEntry(id);
                        if (response.getResponse().getStatusCode() == TodoListRestClient.Response.SUCCESS_OK) {
                            db.delete(Schema.Entries.TABLE_NAME, idWhere, whereArgs);
                        }
                    } else if (cur.getInt(cur.getColumnIndex(Schema.Entries.PENDING_UPDATE)) > 0) {
                        values.clear();
                        values.put(Schema.Entries.TITLE, cur.getString(cur.getColumnIndex(Schema.Entries.TITLE)));
                        values.put(Schema.Entries.NOTES, cur.getString(cur.getColumnIndex(Schema.Entries.NOTES)));
                        values.put(Schema.Entries.COMPLETE, cur.getInt(cur.getColumnIndex(Schema.Entries.COMPLETE)));

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
                            values.remove(Schema.Entries.TITLE);
                            values.remove(Schema.Entries.NOTES);
                            values.remove(Schema.Entries.COMPLETE);
                            db.beginTransaction();
                            try {
                                long pendingTx = DatabaseUtils.longForQuery(db,
                                        "SELECT " + Schema.Entries.PENDING_UPDATE
                                                + " FROM " + Schema.Entries.TABLE_NAME
                                                + " WHERE " + idWhere, whereArgs);
                                values.put(Schema.Entries.PENDING_UPDATE, pendingTx - 1);
                                db.update(Schema.Entries.TABLE_NAME, values, idWhere, whereArgs);

                                // Notify the content observers that the status of the entry
                                //  has changed. Specifically, it is now synced.
                                getContext().getContentResolver().notifyChange(
                                        ContentUris.withAppendedId(
                                                Schema.Entries.CONTENT_ID_URI_BASE,
                                                values.getAsInteger(Schema.Entries.ID)),
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
        return Integer.parseInt(uri.getPathSegments().get(Schema.Entries.TODOLIST_ENTRY_ID_PATH_POSITION));
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
        //notification.defaults |= Notification.DEFAULT_ALL;

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
        //notification.defaults |= Notification.DEFAULT_ALL;

        Intent notificationIntent = new Intent(getContext(), TodoListActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(getContext(), 0, notificationIntent, 0);

        notification.setLatestEventInfo(getContext(), contentTitle, contentText, contentIntent);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(SYNC_ERROR_ID, notification);
    }

}
