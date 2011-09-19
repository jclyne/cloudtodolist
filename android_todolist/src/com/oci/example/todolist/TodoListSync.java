package com.oci.example.todolist;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class TodoListSync extends Service {
    public IBinder onBind(Intent intent) {
        return null;
    }
}
