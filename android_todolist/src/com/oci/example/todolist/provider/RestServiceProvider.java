package com.oci.example.todolist.provider;


import com.oci.example.todolist.client.HttpRestClient;

public interface RestServiceProvider {

    public enum SyncResult {
        failed_network_error,
        failed_invalid_request,
        failed_invalid_response,
        success_no_change,
        success_updated
    }

    public SyncResult onPerformSync(HttpRestClient client, boolean forceRefresh);

}
