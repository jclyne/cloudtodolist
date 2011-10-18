package com.oci.example.todolist.client;

import android.accounts.*;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class GaeAuthenticator implements HttpRestAuthenticator {

    private Context context;
    private Account account;

    private static final String authTokenType = "ah";

    public GaeAuthenticator(Context context, Account account) {
        this.context = context;
        this.account = account;
    }

    @Override
    public void login(final HttpClient client, final String scheme, final String authority) {

        AccountManager accountManager = AccountManager.get(context);
        AccountManagerFuture result = accountManager.getAuthToken(
                                    account, authTokenType, true, null, new Handler());

        try {
            Bundle bundle = (Bundle) result.getResult();
            setCookieFromToken(client,
                        bundle.getString(AccountManager.KEY_AUTHTOKEN),
                        scheme, authority);
        } catch (OperationCanceledException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (AuthenticatorException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void addAuthenticationInfo(HttpRequestBase request) {

    }

    private boolean setCookieFromToken(HttpClient client, String authToken,String scheme, String authority) {

        try {

            DefaultHttpClient httpClient = (DefaultHttpClient)client;

            // Don't follow redirects
            httpClient.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);

            HttpGet request = new HttpGet();
            request.setURI(new URI(scheme, authority,"/_ah/login", "continue=http://localhost/&auth="+authToken, null));
            HttpResponse response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() != 302)
                // Response should be a redirect
                return false;

            for (Cookie cookie : httpClient.getCookieStore().getCookies()) {
                if (cookie.getName().equals("ACSID"))
                    return true;
            }
        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } finally {
            client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, true);
        }
        return true;
    }
}
