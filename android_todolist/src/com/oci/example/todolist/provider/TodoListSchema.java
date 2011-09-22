package com.oci.example.todolist.provider;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Defines a contract between the TodoListSchema content provider and its clients. A contract defines the
 * information that a client needs to access the provider as one or more data tables. A contract
 * is a public, non-extendable (final) class that contains constants defining column names and
 * URIs. A well-written client depends only on the constants in the contract.
 */
public final class TodoListSchema {
    public static final String AUTHORITY = "com.oci.provider.todolist";
    private static final String SCHEME = "content://";
    private static final String PATH_TODOLIST = "todolist/";


    private TodoListSchema() {
    }

    public static final class Entries implements BaseColumns {

        // This class cannot be instantiated
        private Entries() {
        }

        /**
         * The table name offered by this provider
         */


        /*
         * URI definitions
         */

        /**
         * Path part for the TodoListSchema URI
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
         * The content:// style URL for the TodoListSchema entry list
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
         * Column name for the TodoListSchema ID of the entry
         * <P>Type: TEXT</P>
         */
        public static final String ID = "ID";

        /**
         * Column name for the title of the entry
         * <P>Type: TEXT</P>
         */
        public static final String TITLE = "title";

        /**
         * Column name of the entry notes
         * <P>Type: TEXT</P>
         */
        public static final String NOTES = "notes";

        /**
         * Column name for the completed flag
         * <P>Type: BOOLEAN)</P>
         */
        public static final String COMPLETE = "complete";

        /**
         * Column name for the creation timestamp
         * <P>Type: INTEGER (long from System.curentTimeMillis())</P>
         */
        public static final String CREATED = "created";

        /**
         * Column name for the modification timestamp
         * <P>Type: INTEGER (long from System.curentTimeMillis())</P>
         */
        public static final String MODIFIED = "modified";
    }
}