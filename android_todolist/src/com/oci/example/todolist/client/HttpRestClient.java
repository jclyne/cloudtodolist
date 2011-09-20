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
import java.util.HashMap;
import java.util.Map;

public class HttpRestClient implements RestClient {
    private static final String TAG = "HttpRestClient";

    Map<ContentType,String> mimeTypes = new HashMap<ContentType,String>() {
            {
                put(ContentType.BINARY,"appliation/octet-stream");
                put(ContentType.JSON,"appliation/json");
                put(ContentType.XML,"appliation/xml");
            }
    };

    private final HttpClient client = new DefaultHttpClient();
    private static final String scheme="http:";
    private final String authority;

    public HttpRestClient(String authority) {
        this.authority = authority;
    }

    public String getAuthority() {
        return authority;
    }

    public String parseResponseContent(InputStream instream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(instream));
        StringBuilder builder = new StringBuilder();

        String line;
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

    @Override
    public String Get(String path, String query, String fragment,ContentType acceptType) {
        HttpGet request = new HttpGet();
        if (mimeTypes.containsKey(acceptType))
            request.setHeader("Accept", mimeTypes.get(acceptType));

        HttpResponse response;
        try {
            String uriString=scheme+"//"+authority+path;
            if (query != null) uriString+= "?"+query;
            if (fragment != null) uriString+= "#"+fragment;
            request.setURI(new URI(uriString));
        } catch (URISyntaxException e) {
            Log.e(TAG, e.toString());
            return null;
        }

        try {
            response = client.execute(request);
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            return null;
        } catch (IllegalArgumentException e){
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
