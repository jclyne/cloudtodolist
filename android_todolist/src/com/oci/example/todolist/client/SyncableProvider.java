package com.oci.example.todolist.client;


public interface SyncableProvider {

    public enum SyncResult {
        failed_network_error,
        failed_invalid_request,
        failed_invalid_response,
        success_no_change,
        success_updated
    }

    public SyncResult onPerformSync(final SyncableProviderClient providerClient,boolean fullRefresh);

}
