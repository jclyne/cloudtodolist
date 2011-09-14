package com.oci.example.todolist;

import android.test.ActivityInstrumentationTestCase2;

/**
 * This is a simple framework for a test of an Application.  See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more information on
 * how to write and extend Application tests.
 * <p/>
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class com.oci.example.todolist.TodoListActivityTest \
 * com.oci.example.todolist.tests/android.test.InstrumentationTestRunner
 */
public class TodoListActivityTest extends ActivityInstrumentationTestCase2<TodoListActivity> {

    public TodoListActivityTest() {
        super("com.oci.example.todolist", TodoListActivity.class);
    }

}
