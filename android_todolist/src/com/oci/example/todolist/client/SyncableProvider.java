package com.oci.example.todolist.client;


import java.security.PublicKey;

public interface SyncableProvider {

    public enum SyncResult {
        failed,
        success_no_change,
        success_updated
    }

    public SyncResult onPerformSync(final SyncableClient client);

}
