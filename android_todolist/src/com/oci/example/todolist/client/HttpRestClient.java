package com.oci.example.todolist.client;


import android.util.Log;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;

public class HttpRestClient extends  RestClient {

    private static final String TAG = "HttpRestClient";

    private static final String ACCEPT_TYPE_HEADER = "Accept";
    private static final String ACCEPT_ENCODING_HEADER = "Accept-Encoding";


    private static final int SOCKET_TIMEOUT=1000;
    private final HttpClient client;
    private static final String scheme = "http";
    private final String authority;

    public HttpRestClient(String authority) {
        this.authority = authority;

        client = new DefaultHttpClient();
        client.getParams().setParameter("http.socket.timeout", SOCKET_TIMEOUT);

    }

    @Override
    public Response Get(String path, String query, String fragment,
                        ContentType acceptType, ContentEncoding acceptEncoding)
            throws URISyntaxException, IOException, IllegalArgumentException {

        return executeRequest(new HttpGet(), path, query, fragment, acceptType, acceptEncoding);
    }

    @Override
    public Response Post(String path, String query, String fragment,
                        ContentType acceptType, ContentEncoding acceptEncoding)
            throws IOException, URISyntaxException, IllegalArgumentException {

        return executeRequest(new HttpPost(), path, query, fragment, acceptType, acceptEncoding);
    }

    @Override
    public Response Put(String path, String query, String fragment,
                        ContentType acceptType, ContentEncoding acceptEncoding)
            throws IOException, URISyntaxException, IllegalArgumentException {

        return executeRequest(new HttpPut(), path, query, fragment, acceptType, acceptEncoding);
    }

    @Override
    public Response Delete(String path, String query, String fragment,
                        ContentType acceptType, ContentEncoding acceptEncoding)
            throws IOException, URISyntaxException, IllegalArgumentException {

        return executeRequest(new HttpDelete(), path, query, fragment, acceptType, acceptEncoding);
    }


    private Response executeRequest(HttpRequestBase request,
                                    String path, String query, String fragment,
                                    ContentType acceptType, ContentEncoding acceptEncoding)
            throws IOException, URISyntaxException {

        String acceptContentMimeType = acceptType.toMime();
        if (acceptContentMimeType != null)
            request.setHeader(ACCEPT_TYPE_HEADER, acceptContentMimeType);

        String acceptEncodingMimeType = acceptEncoding.toMime();
        if ( acceptEncodingMimeType != null )
            request.setHeader(ACCEPT_ENCODING_HEADER, acceptEncodingMimeType);

        request.setURI(new URI(scheme,authority,path,query,fragment));

        HttpResponse response = client.execute(request);

        int statusCode = response.getStatusLine().getStatusCode();

        HttpEntity entity = response.getEntity();

        ContentType contentType = ContentType.UNSUPPORTED;
        if ( entity.getContentType() != null ){
            contentType = contentType.fromMime(entity.getContentType().getValue());
        }

        ContentEncoding contentEncoding = ContentEncoding.NONE;
        if ( entity.getContentEncoding() != null ) {
            contentEncoding = ContentEncoding.fromMime(entity.getContentEncoding().getValue());
        }

        if (statusCode >= 200 && statusCode < 300) {
            return new Response(statusCode,
                    contentToString(entity.getContent()),
                    contentType, contentEncoding);
        } else {
            return new Response(statusCode,
                    response.getStatusLine().getReasonPhrase(),
                    contentType, contentEncoding);
        }

    }

    private String contentToString(InputStream instream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(instream));
        StringBuilder builder = new StringBuilder();

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            return builder.toString();
        } finally {
            try {
                instream.close();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }
    }
}
