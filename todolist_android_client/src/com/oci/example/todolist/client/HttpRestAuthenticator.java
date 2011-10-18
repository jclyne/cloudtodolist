package com.oci.example.todolist.client;


import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;

public interface HttpRestAuthenticator {

    public void login (HttpClient client,String scheme, String authority);

    public void addAuthenticationInfo(HttpRequestBase request);
}
