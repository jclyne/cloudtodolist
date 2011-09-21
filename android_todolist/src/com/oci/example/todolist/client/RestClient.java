package com.oci.example.todolist.client;

import java.io.IOException;
import java.net.URISyntaxException;

abstract public class RestClient {

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

    public Response Get(String path, String query, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Get(path, query, null, acceptType, ContentEncoding.NONE);
    }

    public Response Get(String path, String query, String fragment, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Get(path, query, fragment, acceptType, ContentEncoding.NONE);
    }

    abstract public Response Get(String path, String query, String fragment,
                                 ContentType acceptType, ContentEncoding acceptEncoding)
            throws URISyntaxException, IllegalArgumentException, IOException;

    public Response Post(String path, String query, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Post(path, query, null, acceptType, ContentEncoding.NONE);
    }

    public Response Post(String path, String query, String fragment, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Post(path, query, fragment, acceptType, ContentEncoding.NONE);
    }

    abstract public Response Post(String path, String query, String fragment,
                                  ContentType acceptType, ContentEncoding acceptEncoding)
            throws IOException, URISyntaxException, IllegalArgumentException;

    public Response Put(String path, String query, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Put(path, query, null, acceptType, ContentEncoding.NONE);
    }

    public Response Put(String path, String query, String fragment, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Put(path, query, fragment, acceptType, ContentEncoding.NONE);
    }

    abstract public Response Put(String path, String query, String fragment,
                                 ContentType acceptType, ContentEncoding acceptEncoding)
            throws IOException, URISyntaxException, IllegalArgumentException;

    public Response Delete(String path, String query, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Delete(path, query, null, acceptType, ContentEncoding.NONE);
    }

    public Response Delete(String path, String query, String fragment, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Delete(path, query, fragment, acceptType, ContentEncoding.NONE);
    }

    abstract public Response Delete(String path, String query, String fragment,
                                    ContentType acceptType, ContentEncoding acceptEncoding)
            throws IOException, URISyntaxException, IllegalArgumentException;
}
