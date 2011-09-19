package com.oci.example.todolist.client;


import android.util.Log;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;

public class RestClient {
    private static final String TAG = "RestClient";

    private final HttpClient client = new DefaultHttpClient();
    private final String authority;

    public RestClient(String authority) {
        this.authority = authority;
    }

    public String getAuthority() {
        return authority;
    }

    public String parseResponseContent(InputStream instream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(instream));
        StringBuilder builder = new StringBuilder();

        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            return builder.toString();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        } finally {
            try {
                instream.close();
            } catch (IOException e) {
                Log.e(TAG,e.toString());
            }
        }
        return null;
    }

    public String Get(String path, String query, String fragment) {
        HttpGet request = new HttpGet();
        HttpResponse response;
        try {
            request.setURI(new URI("http", authority, path, query, fragment));
        } catch (URISyntaxException e) {
            Log.e(TAG, e.toString());
            return null;
        }

        try {
            response = client.execute(request);
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            return null;
        }

        HttpEntity entity = response.getEntity();
        if (entity == null) {
            Log.e(TAG, "Unexpected empty HTTP Entity in GET Request");
            return null;
        }

        try {
            return parseResponseContent(entity.getContent());
        } catch (IOException e) {
           Log.e(TAG, e.toString());
           return null;
        }
    }
}
