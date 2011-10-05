package com.oci.example.todolist.client;


import android.util.Log;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.zip.GZIPInputStream;

/**
 * Base class for an HTTP Rest client. This class is capable of issuing requests
 * with specific Content Type and Encoding specifications in the request header and
 * will decode the content encoding in the response, making it transparent to users.
 *
 * <p>Note that this client cannot parse the response content, only return the content
 * string and type. This is due to the fact that parsing requires the schema, which is
 * service specific. In this sense, class serves as a base class for service specific
 * client implementations, as this class handles the boilerplate HTTP operations.</p>
 */

@SuppressWarnings({"SameParameterValue"})
public class HttpRestClient {

    // Log tag
    private static final String TAG = "HttpRestClient";


    // Specific Header definitions
    private static final String ACCEPT_TYPE_HEADER = "Accept";
    private static final String ACCEPT_ENCODING_HEADER = "Accept-Encoding";

    // Reference to an org.apache.http.client.HttpClient
    private final HttpClient client;
    // Scheme of the service URI (http/https)
    private final String scheme;
    // Authority (hostname) of the service URI (xyxyxy.appspot.com)
    private final String authority;

    /**
     * Enumerated type that defines the supported mime types for the
     * accepted ContentType. REST apis will commonly honor the 'Accept'
     * header field, in the http request, and return the response in the
     * requested format. This is obviously service dependent.
     *
     * <p>If different content types are made available from the api,
     * the client should favor binary -> JSON -> XML, in that order.
     * A binary type is rare and less flexible, but can be very efficient
     * for certain types of data. For a general purpose format, JSON is
     * more efficient to parse and less verbose than XML</p>
     */
    public static enum ContentType {
        UNSUPPORTED, BINARY, JSON, XML;


        // Mime string definitions for each supported type
        public static final String MIME_TYPE_BINARY = "application/octet-stream";
        public static final String MIME_TYPE_JSON = "application/json";
        public static final String MIME_TYPE_XML = "application/xml";

        /**
         *
         * @return the mime string definition for the current ContentType
         */
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

        /**
         * Initializes a ContentType value based on a mime type string
         *
         * @param mimeType string value representing a mime type
         * @return ContentType as specified in the mimeType strings
         */
        public static ContentType fromMime(String mimeType) {
            if (mimeType.equals(MIME_TYPE_BINARY)) return BINARY;
            if (mimeType.equals(MIME_TYPE_JSON)) return JSON;
            if (mimeType.equals(MIME_TYPE_XML)) return XML;
            return UNSUPPORTED;
        }
    }

    /**
     * Enumerated type that defines the supported mime types for the
     * accepted ContentEncoding. REST apis will commonly honor the
     * 'Accept-Encoding' header field, in the http request, and
     * return the response encoded in the requested format.
     * This is obviously service dependent.
     *
     * <p>If gzip encoding is made available, it is a good idea
     * to use it when there is the potential for large amounts of data
     * and/or users. </p>
     */
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

    /**
     * Represents the HTTP client response, including the
     * status code, content type, and a string representing the
     * content.
     *
     * <p>Objects of this type are immutable</p>
     */
    public class Response {

        // Status code for the HTTP response
        private final int statusCode;
        // String containing the response's body
        private final String content;
        // Type of the associated content
        private final ContentType contentType;

        /**
         * Constructor
         * @param statusCode status code for the response
         * @param content response body content
         * @param contentType response content type
         */
        public Response(int statusCode,
                        String content,
                        ContentType contentType) {
            this.statusCode = statusCode;
            this.content = content;
            this.contentType = contentType;
        }

        /**
         * @return status code of this response
         */
        public int getStatusCode() {
            return statusCode;
        }

        /**
         * @return content string of this response
         */
        public String getContent() {
            return content;
        }

        /**
         * @return content type of this response
         */
        public ContentType getContentType() {
            return contentType;
        }

        /**
         * @return boolean representing whether the response
         * represents a successful request
         */
        public boolean succeeded() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    /**
     * Constructor
     * @param client org.apache.http.client.HttpClient object to wrap
     * @param authority service authority string
     * @param useHttps  flag indicating whether to use secure https over http
     */
    public HttpRestClient(HttpClient client, String authority, boolean useHttps) {
        this.scheme = (useHttps ? "https" : "http");
        this.authority = authority;
        this.client = client;
    }

    /**
     * Performs and HTTP get operation on the specified path relative to the
     * client's authority.
     *
     * @param path path portion of the request URI
     * @param query query string portion of the request URI
     * @param acceptType ContentType associated with 'Accept' header field in the
     * Http request
     * @return Response object for the executed request
     * @throws URISyntaxException indicates invalid syntax in the request's resulting URI
     * @throws IllegalArgumentException indicates an invalid value in the request
     * @throws IOException indicates error in underlying network state or operation
     */
    public Response Get(String path, String query, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Get(path, query, null, acceptType, ContentEncoding.NONE);
    }

    /**
     * Performs and HTTP get operation on the specified path relative to the
     * client's authority. Allows for inclusion of a fragment field in the request URI
     *
     * @param path path portion of the request URI
     * @param query query string portion of the request URI
     * @param fragment fragment string portion of the request URI
     * @param acceptType ContentType associated with 'Accept' header field in the
     * Http request
     * @return Response object for the executed request
     * @throws URISyntaxException indicates invalid syntax in the request's resulting URI
     * @throws IllegalArgumentException indicates an invalid value in the request
     * @throws IOException indicates error in underlying network state or operation
     */
    public Response Get(String path, String query, String fragment, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Get(path, query, fragment, acceptType, ContentEncoding.NONE);
    }

    /**
     * Performs and HTTP get operation on the specified path relative to the client's
     * authority. Allows for inclusion of a fragment field in the request URI and an
     * 'Accept-Encoding' field in the request header
     *
     * @param path path portion of the request URI
     * @param query query string portion of the request URI
     * @param fragment fragment string portion of the request URI
     * @param acceptType ContentType associated with 'Accept' header field in the
     * Http request
     * @param acceptEncoding ContentEncoding associated with the 'Accept-Encoding'
     * header field in the Http request
     * @return Response object for the executed request
     * @throws URISyntaxException indicates invalid syntax in the request's resulting URI
     * @throws IllegalArgumentException indicates an invalid value in the request
     * @throws IOException indicates error in underlying network state or operation
     */
    @SuppressWarnings({"WeakerAccess"})
    public Response Get(String path, String query, String fragment,
                 ContentType acceptType, ContentEncoding acceptEncoding)
            throws URISyntaxException, IOException, IllegalArgumentException {

        return executeRequest(new HttpGet(), path, query, fragment, acceptType, acceptEncoding);
    }

    /**
     * Performs and HTTP post operation on the specified path relative to the
     * client's authority.
     *
     * @param path path portion of the request URI
     * @param query query string portion of the request URI
     * @param acceptType ContentType associated with 'Accept' header field in the
     * Http request
     * @return Response object for the executed request
     * @throws URISyntaxException indicates invalid syntax in the request's resulting URI
     * @throws IllegalArgumentException indicates an invalid value in the request
     * @throws IOException indicates error in underlying network state or operation
     */
    public Response Post(String path, String query, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Post(path, query, null, acceptType, ContentEncoding.NONE);
    }

    /**
     * Performs and HTTP post operation on the specified path relative to the
     * client's authority. Allows for inclusion of a fragment field in the request URI
     *
     * @param path path portion of the request URI
     * @param query query string portion of the request URI
     * @param fragment fragment string portion of the request URI
     * @param acceptType ContentType associated with 'Accept' header field in the
     * Http request
     * @return Response object for the executed request
     * @throws URISyntaxException indicates invalid syntax in the request's resulting URI
     * @throws IllegalArgumentException indicates an invalid value in the request
     * @throws IOException indicates error in underlying network state or operation
     */
    public Response Post(String path, String query, String fragment, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Post(path, query, fragment, acceptType, ContentEncoding.NONE);
    }

    /**
     * Performs and HTTP post operation on the specified path relative to the client's
     * authority. Allows for inclusion of a fragment field in the request URI and an
     * 'Accept-Encoding' field in the request header
     *
     * @param path path portion of the request URI
     * @param query query string portion of the request URI
     * @param fragment fragment string portion of the request URI
     * @param acceptType ContentType associated with 'Accept' header field in the
     * Http request
     * @param acceptEncoding ContentEncoding associated with the 'Accept-Encoding'
     * header field in the Http request
     * @return Response object for the executed request
     * @throws URISyntaxException indicates invalid syntax in the request's resulting URI
     * @throws IllegalArgumentException indicates an invalid value in the request
     * @throws IOException indicates error in underlying network state or operation
     */
    @SuppressWarnings({"WeakerAccess"})
    public Response Post(String path, String query, String fragment,
                  ContentType acceptType, ContentEncoding acceptEncoding)
            throws IOException, URISyntaxException, IllegalArgumentException {

        return executeRequest(new HttpPost(), path, query, fragment, acceptType, acceptEncoding);
    }

    /**
     * Performs and HTTP put operation on the specified path relative to the
     * client's authority.
     *
     * @param path path portion of the request URI
     * @param query query string portion of the request URI
     * @param acceptType ContentType associated with 'Accept' header field in the
     * Http request
     * @return Response object for the executed request
     * @throws URISyntaxException indicates invalid syntax in the request's resulting URI
     * @throws IllegalArgumentException indicates an invalid value in the request
     * @throws IOException indicates error in underlying network state or operation
     */
    public Response Put(String path, String query, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Put(path, query, null, acceptType, ContentEncoding.NONE);
    }

    /**
     * Performs and HTTP put operation on the specified path relative to the
     * client's authority. Allows for inclusion of a fragment field in the request URI
     *
     * @param path path portion of the request URI
     * @param query query string portion of the request URI
     * @param fragment fragment string portion of the request URI
     * @param acceptType ContentType associated with 'Accept' header field in the
     * Http request
     * @return Response object for the executed request
     * @throws URISyntaxException indicates invalid syntax in the request's resulting URI
     * @throws IllegalArgumentException indicates an invalid value in the request
     * @throws IOException indicates error in underlying network state or operation
     */
    public Response Put(String path, String query, String fragment, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Put(path, query, fragment, acceptType, ContentEncoding.NONE);
    }

    /**
     * Performs and HTTP put operation on the specified path relative to the client's
     * authority. Allows for inclusion of a fragment field in the request URI and an
     * 'Accept-Encoding' field in the request header
     *
     * @param path path portion of the request URI
     * @param query query string portion of the request URI
     * @param fragment fragment string portion of the request URI
     * @param acceptType ContentType associated with 'Accept' header field in the
     * Http request
     * @param acceptEncoding ContentEncoding associated with the 'Accept-Encoding'
     * header field in the Http request
     * @return Response object for the executed request
     * @throws URISyntaxException indicates invalid syntax in the request's resulting URI
     * @throws IllegalArgumentException indicates an invalid value in the request
     * @throws IOException indicates error in underlying network state or operation
     */
    @SuppressWarnings({"WeakerAccess"})
    public Response Put(String path, String query, String fragment,
                 ContentType acceptType, ContentEncoding acceptEncoding)
            throws IOException, URISyntaxException, IllegalArgumentException {

        return executeRequest(new HttpPut(), path, query, fragment, acceptType, acceptEncoding);
    }

    /**
     * Performs and HTTP delete operation on the specified path relative to the
     * client's authority.
     *
     * @param path path portion of the request URI
     * @param query query string portion of the request URI
     * @param acceptType ContentType associated with 'Accept' header field in the
     * Http request
     * @return Response object for the executed request
     * @throws URISyntaxException indicates invalid syntax in the request's resulting URI
     * @throws IllegalArgumentException indicates an invalid value in the request
     * @throws IOException indicates error in underlying network state or operation
     */
    public Response Delete(String path, String query, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Delete(path, query, null, acceptType, ContentEncoding.NONE);
    }

    /**
     * Performs and HTTP delete operation on the specified path relative to the
     * client's authority. Allows for inclusion of a fragment field in the request URI
     *
     * @param path path portion of the request URI
     * @param query query string portion of the request URI
     * @param fragment fragment string portion of the request URI
     * @param acceptType ContentType associated with 'Accept' header field in the
     * Http request
     * @return Response object for the executed request
     * @throws URISyntaxException indicates invalid syntax in the request's resulting URI
     * @throws IllegalArgumentException indicates an invalid value in the request
     * @throws IOException indicates error in underlying network state or operation
     */
    public Response Delete(String path, String query, String fragment, ContentType acceptType)
            throws URISyntaxException, IllegalArgumentException, IOException {
        return Delete(path, query, fragment, acceptType, ContentEncoding.NONE);
    }

    /**
     * Performs and HTTP delete operation on the specified path relative to the client's
     * authority. Allows for inclusion of a fragment field in the request URI and an
     * 'Accept-Encoding' field in the request header
     *
     * @param path path portion of the request URI
     * @param query query string portion of the request URI
     * @param fragment fragment string portion of the request URI
     * @param acceptType ContentType associated with 'Accept' header field in the
     * Http request
     * @param acceptEncoding ContentEncoding associated with the 'Accept-Encoding'
     * header field in the Http request
     * @return Response object for the executed request
     * @throws URISyntaxException indicates invalid syntax in the request's resulting URI
     * @throws IllegalArgumentException indicates an invalid value in the request
     * @throws IOException indicates error in underlying network state or operation
     */
    @SuppressWarnings({"WeakerAccess"})
    public Response Delete(String path, String query, String fragment,
                    ContentType acceptType, ContentEncoding acceptEncoding)
            throws IOException, URISyntaxException, IllegalArgumentException {

        return executeRequest(new HttpDelete(), path, query, fragment, acceptType, acceptEncoding);
    }

    /**
     * Executes the underlying HTTP request and builds the Response object. If the
     * request is successful(status code 2xx, the response will contain the contents
     * of the body in its content field. If the status code indicates failure, the content
     * field will contain the Reason Phrase for the specified status code.
     *
     * @param request the request object being of type HttpGet,HttpPost,HttpPut,
     * or HttpDelete, whichever public method is invoking it.
     * @param path path portion of the request URI
     * @param query query string portion of the request URI
     * @param fragment fragment string portion of the request URI
     * @param acceptType ContentType associated with 'Accept' header field in the
     * Http request
     * @param acceptEncoding ContentEncoding associated with the 'Accept-Encoding'
     * header field in the Http request
     * @return Response object for the executed request
     * @throws IOException IOException indicates error in underlying network state or operation
     * @throws URISyntaxException indicates invalid syntax in the request's resulting URI
     */
    private Response executeRequest(HttpRequestBase request,String path,
                                    String query, String fragment,
                                    ContentType acceptType,
                                    ContentEncoding acceptEncoding)
            throws IOException, URISyntaxException {

        // Set the request's 'Accept' header to the desired ContentType
        String acceptContentMimeType = acceptType.toMime();
        if (acceptContentMimeType != null)
            request.setHeader(ACCEPT_TYPE_HEADER, acceptContentMimeType);

        // Set the request's 'Accept-Encoding' header to the desired ContentEncoding
        String acceptEncodingMimeType = acceptEncoding.toMime();
        if (acceptEncodingMimeType != null)
            request.setHeader(ACCEPT_ENCODING_HEADER, acceptEncodingMimeType);

        // Build the URI from the specified components
        request.setURI(new URI(scheme, authority, path, query, fragment));

        // Execute the request and block for response
        HttpResponse response = client.execute(request);


        // Get the status code and entity of the response
        int statusCode = response.getStatusLine().getStatusCode();
        HttpEntity entity = response.getEntity();

        // Parse the Content Type returned in the entity, throwing an
        //  exception if the response contains an unsupported type
        ContentType contentType = ContentType.UNSUPPORTED;
        if (entity.getContentType() != null) {
            contentType = ContentType.fromMime(entity.getContentType().getValue());
        }

        if (contentType == ContentType.UNSUPPORTED)
            throw new ClientProtocolException("Invalid Content Type '"
                                                +entity.getContentType()
                                                    +"'received in response");

        // Parse the Content Encoding returned in the entity, throwing an
        //  exception if the response contains an unsupported type
        ContentEncoding contentEncoding = ContentEncoding.NONE;
        if (entity.getContentEncoding() != null) {
            contentEncoding = ContentEncoding.fromMime(entity.getContentEncoding().getValue());
        }

        if (contentEncoding == ContentEncoding.UNSUPPORTED)
            throw new ClientProtocolException("Invalid Content Encoding '"
                                                +entity.getContentType()
                                                    +"'received in response");

        // If the status code indicates success, convert the entity body into
        //  a string and build the response object
        if (statusCode >= 200 && statusCode < 300) {
            return new Response(statusCode,
                    contentToString(entity.getContent(),
                                    contentEncoding),
                    contentType);
        } else {
        // If the status code indicates error, build the response object
        //  with the status reason phrase in the content field
            return new Response(statusCode,
                    response.getStatusLine().getReasonPhrase(),
                    contentType);
        }

    }

    /**
     * Converts an input stream from an HTTP response to a string, while handling
     * and content encoding.
     *
     * <p>This is appropriate for REST responses as the size of the body should be
     * manageable. If large amounts of data are needed, the client should use whatever
     * the API implements for paging.</p>
     *
     * @param instream stream from the response entity
     * @param encoding encoding type for the input stream
     * @return string containing the http response
     * @throws IOException indicates that there is an issue reading from the underlying
     * InputStream in the response entity
     */
    private String contentToString(InputStream instream, ContentEncoding encoding) throws IOException {

        /**
         * Create a buffered reader that wraps the appropriate input stream for
         * the encoding type
         */
        BufferedReader reader;
        switch (encoding) {

            case GZIP:
                reader = new BufferedReader(
                            new InputStreamReader(
                                    new GZIPInputStream(instream)));
                break;

            default:
            case NONE:
                reader = new BufferedReader(
                            new InputStreamReader(instream));
                break;
        }

        try {
            StringBuilder builder = new StringBuilder();
            String line;

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