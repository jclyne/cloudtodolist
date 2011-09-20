package com.oci.example.todolist.client;

public interface RestClient {

    public static  enum ContentType { BINARY, JSON, XML };

    public String Get(String path,String query, String fragment,ContentType acceptType);
}
