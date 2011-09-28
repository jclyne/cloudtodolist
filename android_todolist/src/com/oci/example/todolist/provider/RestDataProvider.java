package com.oci.example.todolist.provider;


import com.oci.example.todolist.client.HttpRestClient;

public interface RestDataProvider {

    public void onPerformSync(HttpRestClient client,boolean refresh);

}
