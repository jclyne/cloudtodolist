package com.oci.example.todolist;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Defines a contract between the TodoList content provider and its clients. A contract defines the
 * information that a client needs to access the provider as one or more data tables. A contract
 * is a public, non-extendable (final) class that contains constants defining column names and
 * URIs. A well-written client depends only on the constants in the contract.
 */
public final class TodoList {
    public static final String AUTHORITY = "com.oci.provider.todolist";

    /**
     * Path part for the TodoList BASE URI
     */
    private static final String PATH_TODOLIST = "todolist/";


    // This class cannot be instantiated
    private TodoList() {
    }

    /**
     * Entries table contract
     */
    public static final class Entries implements BaseColumns {

        // This class cannot be instantiated
        private Entries() {
        }

        /**
         * The table name offered by this provider
         */
        public static final String TABLE_NAME = "todolist";

        /*
         * URI definitions
         */

        /**
         * The scheme part for this provider's URI
         */
        private static final String SCHEME = "content://";



        /**
         * Path part for the TodoList URI
         */
        public static final String PATH_TODOLIST_ENTRIES = PATH_TODOLIST + "entries";

        /**
         * Path part for the Note ID URI
         */
        public static final String PATH_TODOLIST_ENTRY_ID = PATH_TODOLIST + "entries/";

        /**
         * 0-relative position of a note ID segment in the path part of a note ID URI
         */
        public static final int TODOLIST_ENTRY_ID_PATH_POSITION = 2;


        /**
         * The content:// style URL for the TodoList entry list
         */
        public static final Uri CONTENT_URI
                = Uri.parse(SCHEME + AUTHORITY + "/" + PATH_TODOLIST_ENTRIES);

        /**
         * The content URI base for a single todolist entry. Callers must
         * append a numeric entry id to this Uri to retrieve an entry
         */
        public static final Uri CONTENT_ID_URI_BASE
                = Uri.parse(SCHEME + AUTHORITY + "/" +PATH_TODOLIST_ENTRY_ID);

        /**
         * The content URI match pattern for a single note, specified by its ID. Use this to match
         * incoming URIs or to construct an Intent.
         */
        public static final Uri CONTENT_ID_URI_PATTERN
                = Uri.parse(SCHEME + AUTHORITY + "/" +PATH_TODOLIST_ENTRY_ID + "#");


        /*
         * MIME type definitions
         */

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of todolist entries.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.oci.todolist.entries";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single
         * todolist entry.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.oci.todolist.entry";

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = "created ASC";

        /*
         * Column definitions
         */

        /**
         * Column name for the title of the entry
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_TITLE = "title";

        /**
         * Column name of the entry notes
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_NOTES = "notes";

        /**
         * Column name for the completed flag
         * <P>Type: BOOLEAN)</P>
         */
        public static final String COLUMN_NAME_COMPLETE = "complete";

        /**
         * Column name for the creation timestamp
         * <P>Type: INTEGER (long from System.curentTimeMillis())</P>
         */
        public static final String COLUMN_NAME_CREATE_TIME = "created";

        /**
         * Column name for the modification timestamp
         * <P>Type: INTEGER (long from System.curentTimeMillis())</P>
         */
        public static final String COLUMN_NAME_MODIFY_TIME = "modified";

        public static final String COLUMN_NAME_PENDING_TX = "pending_tx";
        public static final String COLUMN_NAME_PENDING_INSERT= "pending_insert";
        public static final String COLUMN_NAME_PENDING_UPDATE = "pending_update";
        public static final String COLUMN_NAME_PENDING_DELETE = "pending_delete";

    }
}