package com.oci.example.todolist.client;


import android.util.Log;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;

@SuppressWarnings({"NullableProblems", "SameParameterValue", "UnusedParameters", "UnusedDeclaration"})
public class HttpRestClient {

    private static final String TAG = "HttpRestClient";


    private static final String ACCEPT_TYPE_HEADER = "Accept";
    private static final String ACCEPT_ENCODING_HEADER = "Accept-Encoding";

    private final HttpClient client;
    private final String scheme;
    private final String authority;

    public static enum ContentType {
        UNSUPPORTED, BINARY, JSON, XML;


        public static final String MIME_TYPE_BINARY = "application/octet-stream";
        public static final String MIME_TYPE_JSON = "application/json";
        public static final String MIME_TYPE_XML = "application/xml";

        public String toMime() {
            switch (this) {
                case BINARY:
                    return MIME_TYPE_BINARY;
                case JSON:
                    return MIME_TYPE_JSON;
                case XML:
                    return MIME_TYPE_XML;
                default:
                    return null;
            }
        }

        public static ContentType fromMime(String mimeType) {
            if (mimeType.equals(MIME_TYPE_BINARY)) return BINARY;
            if (mimeType.equals(MIME_TYPE_JSON)) return JSON;
            if (mimeType.equals(MIME_TYPE_XML)) return XML;
            return UNSUPPORTED;
        }
    }

    public static enum ContentEncoding {
        UNSUPPORTED, NONE, GZIP;

        public static final String MIME_TYPE_GZIP = "application/x-gzip";

        public String toMime() {
            switch (this) {
                case GZIP:
                    return MIME_TYPE_GZIP;
                default:
                    return null;
            }
        }

        public static ContentEncoding fromMime(String mimeType) {
            if (mimeType == null) return NONE;
            if (mimeType.equals(MIME_TYPE_GZIP)) return GZIP;
            return UNSUPPORTED;
        }
    }

     @SuppressWarnings({"UnusedDeclaration"})
     public class Response {
        private final int statusCode;
        private final String content;
        private final ContentType contentType;
        private final ContentEncoding contentEncoding;

        public Response(int statusCode,
                        String content,
                        ContentType contentType,
                        ContentEncoding contentEncoding) {
            this.statusCode = statusCode;
            this.content = content;
            this.contentType = contentType;
            this.contentEncoding = contentEncoding;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getContent() {
            return content;
        }

        public ContentType getContentType() {
            return contentType;
        }

        public ContentEncoding getContentEncoding() {
            return contentEncoding;
        }

        public boolean succeeded() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    public HttpRestClient(HttpClient client, String authority, boolean useHttps) {
        this.scheme = (useHttps ? "https" : "http");
        this.authority = authority;

        this.client = client;


    }

    public Response Get(String path, String query, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Get(path, query, null, acceptType, ContentEncoding.NONE);
    }

    public Response Get(String path, String query, String fragment, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Get(path, query, fragment, acceptType, ContentEncoding.NONE);
    }

    Response Get(String path, String query, String fragment,
                 ContentType acceptType, ContentEncoding acceptEncoding)
            throws URISyntaxException, IOException, IllegalArgumentException {

        return executeRequest(new HttpGet(), path, query, fragment, acceptType, ContentEncoding.NONE);
    }

    public Response Post(String path, String query, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Post(path, query, null, acceptType, ContentEncoding.NONE);
    }

    public Response Post(String path, String query, String fragment, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Post(path, query, fragment, acceptType, ContentEncoding.NONE);
    }

    Response Post(String path, String query, String fragment,
                  ContentType acceptType, ContentEncoding acceptEncoding)
            throws IOException, URISyntaxException, IllegalArgumentException {

        return executeRequest(new HttpPost(), path, query, fragment, acceptType, acceptEncoding);
    }

    public Response Put(String path, String query, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Put(path, query, null, acceptType, ContentEncoding.NONE);
    }

    public Response Put(String path, String query, String fragment, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Put(path, query, fragment, acceptType, ContentEncoding.NONE);
    }

    Response Put(String path, String query, String fragment,
                 ContentType acceptType, ContentEncoding acceptEncoding)
            throws IOException, URISyntaxException, IllegalArgumentException {

        return executeRequest(new HttpPut(), path, query, fragment, acceptType, acceptEncoding);
    }

    public Response Delete(String path, String query, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Delete(path, query, null, acceptType, ContentEncoding.NONE);
    }

    public Response Delete(String path, String query, String fragment, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Delete(path, query, fragment, acceptType, ContentEncoding.NONE);
    }

    Response Delete(String path, String query, String fragment,
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
            contentType = ContentType.fromMime(entity.getContentType().getValue());
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
