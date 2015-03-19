/*
 * Tagged - Detect HTTP Request Header Modifications and Check Digital Signature Verification
 * References:
 * http://developer.android.com/training/basics/network-ops/connecting.html
 * http://www.compiletimeerror.com/2013/01/why-and-how-to-use-asynctask.html#.VMKb5P7F95A
 * http://stackoverflow.com/questions/11532989/android-decrypt-rsa-text-using-a-public-key-stored-in-a-file
 * http://docs.oracle.com/javase/tutorial/security/apisign/vstep4.html
 *
 */

package com.example.minnie.tagged_v2;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.example.taggedlib.Tagged;

public class MainActivity extends ActionBarActivity implements View.OnClickListener {
    // Declare widgets
    ImageButton sendBtn, viewHeadersBtn, startOverBtn;
    TextView responseTv, origHeaderTv, diffTv, sigVerInfoTv, headerDifferenceInfoTv;
    ImageView sigVerIcon, headerDiffIcon;
    TableLayout sigVerTable, headersTable;
    Toolbar toolbar;

    // Tagged server variables
    final String serverIP = "155.41.105.237"; final String serverPage = "server_v1.php";

    String responseStr = "";
    String digSigHeaderName = "Auth";
    TreeMap<String, String> origHeadersMap, modHeadersMap;
    ArrayList<String> diffHeadersList;
    String TAG = "Tagged_v2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Buttons
        sendBtn = (ImageButton)findViewById(R.id.send_btn); sendBtn.setOnClickListener(this);
        viewHeadersBtn = (ImageButton)findViewById(R.id.viewheaders_btn); viewHeadersBtn.setOnClickListener(this);
        startOverBtn = (ImageButton)findViewById(R.id.startover_btn); startOverBtn.setOnClickListener(this);

        // TextViews
        responseTv = (TextView)findViewById(R.id.responseStr_textView);
        origHeaderTv = (TextView)findViewById(R.id.origHeader_textView);
        diffTv = (TextView)findViewById(R.id.diffHeader_textView);
        sigVerInfoTv = (TextView)findViewById(R.id.sigVerInfo);
        headerDifferenceInfoTv = (TextView)findViewById(R.id.headerDifferenceInfo_textView);

        // ImageViews
        sigVerIcon = (ImageView)findViewById(R.id.sigVerIcon);
        headerDiffIcon = (ImageView)findViewById(R.id.headerDiffIcon);

        // Tables
        sigVerTable = (TableLayout)findViewById(R.id.sigVer_table);
        headersTable = (TableLayout)findViewById(R.id.headers_table);

        // Toolbar
        toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        toolbar.setLogo(R.drawable.tagged_logo_v7);

        // Initial settings (appearance)
        viewHeadersBtn.setEnabled(false); viewHeadersBtn.setVisibility(View.INVISIBLE);
        startOverBtn.setEnabled(false); startOverBtn.setVisibility(View.INVISIBLE);
        sigVerTable.setEnabled(false); sigVerTable.setVisibility(View.INVISIBLE);
        headersTable.setEnabled(false); headersTable.setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu (i.e. adds items to the tool bar)
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about:
                Toast.makeText(getApplicationContext(), "Source @ https://github.com/minniek/Tagged_v2", Toast.LENGTH_LONG).show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Assign tasks for buttons
    public void onClick(View v) {
        if (v == sendBtn) {
            // Check for network connection
            String taggedURL = "http://" + serverIP + "/" + serverPage;
            ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo nwInfo = connMgr.getActiveNetworkInfo();

            // If network is present, start AsyncTask to connect to given URL
            if ((nwInfo != null) && (nwInfo.isConnected())) {
                new StartAsyncTask().execute(taggedURL);
            } else {
                responseTv.setText("ERROR No network connection detected.");
            }
        }

        if (v == viewHeadersBtn) {
            // Disable + hide view headers button, start over button, and signature verification table
            viewHeadersBtn.setEnabled(false); viewHeadersBtn.setVisibility(View.INVISIBLE);
            startOverBtn.setEnabled(true); startOverBtn.setVisibility(View.VISIBLE);
            sigVerTable.setEnabled(false); sigVerTable.setVisibility(View.INVISIBLE);

            // Enable + show headers table
            headersTable.setEnabled(true); headersTable.setVisibility(View.VISIBLE);
        }

        if (v == startOverBtn) {
            // Clear out text views
            responseTv.setText("");
            origHeaderTv.setText("");
            diffTv.setText("");

            // Clear out responseStr and header objects
            responseStr = null;
            origHeadersMap = new TreeMap<>();
            modHeadersMap = new TreeMap<>();
            diffHeadersList = new ArrayList<>();

            // Enable + show send button only
            sendBtn.setEnabled(true); sendBtn.setVisibility(View.VISIBLE);

            // Disable + hide the view headers button, start over button, and all tables
            viewHeadersBtn.setEnabled(false); viewHeadersBtn.setVisibility(View.INVISIBLE);
            startOverBtn.setEnabled(false); startOverBtn.setVisibility(View.INVISIBLE);
            sigVerTable.setEnabled(false); sigVerTable.setVisibility(View.INVISIBLE);
            headersTable.setEnabled(false); headersTable.setVisibility(View.INVISIBLE);
        }
    }

    protected class StartAsyncTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            // Params come from the execute() call: params[0] is the url
            try {
                responseStr = connectToURL(urls[0]);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Error: could not connect to " + urls[0]);
            }
            return responseStr; // Return value is passed to onPostExecute()
        }

        protected void onPostExecute(String responseStr) {
            // Set origHeaderTv to display original headers when the viewHeadersBtn is pressed
            for (Map.Entry<String, String> entry : origHeadersMap.entrySet()) {
                origHeaderTv.append(entry.getKey() + ": " + entry.getValue() + "\n");
            }

            // Make modHeadersMap from responseStr
            modHeadersMap = new TreeMap<>();
            try {
                JSONObject modHeadersJSON= new JSONObject(responseStr);
                modHeadersMap = new Gson().fromJson(modHeadersJSON.toString(), modHeadersMap.getClass());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // Set responseTv to display headers when the viewHeadersBtn is pressed
            for (Map.Entry<String, String> entry : modHeadersMap.entrySet()) {
                responseTv.append(entry.getKey() + ": " + entry.getValue() + "\n");
            }

            // Get public key string from PEM file in res/raw
            String pubKeyPemFile = "public_key";
            Context myContext = getApplicationContext();
            String pubKeyStr = new Tagged().getPublicKeyString(myContext, pubKeyPemFile);
            Log.d(TAG, "Public key string (pubKeyStr): " + pubKeyStr);

            // Get verification of signature
            Boolean isVerified = new Tagged().verifySignature(pubKeyStr, responseStr, digSigHeaderName);
            Log.d(TAG, "Signature verified (isVerified)?: " + isVerified);

            // Set corresponding images + text depending on isVerified value
            if (isVerified == true) {
                sigVerIcon.setImageResource(R.drawable.verified_icon);
                sigVerInfoTv.setText("Tagged server signature verified.");
            } else if (isVerified == false) {
                sigVerIcon.setImageResource(R.drawable.unverified_icon);
                sigVerInfoTv.setText("Tagged server signature NOT verified.");
            }
            sigVerTable.setEnabled(true); sigVerTable.setVisibility(View.VISIBLE);

            /*// TESTERS (when Python proxy is not available...)
            modHeadersMap.put("X-tagged", "mini");
            modHeadersMap.put("Happy", "hacking!");
            modHeadersMap.remove("Host");
            modHeadersMap.remove("Connection");
            */

            // Get list of request header differences between origHeadersMap and modHeadersMap
            diffHeadersList = new Tagged().getRequestHeaderDifferenceList(origHeadersMap, modHeadersMap, digSigHeaderName);
            Log.d(TAG, "Difference of headers list: " + diffHeadersList);

            // Set icons and text views
            if (!diffHeadersList.isEmpty()) {
                headerDiffIcon.setImageResource(R.drawable.unverified_icon);
                headerDifferenceInfoTv.setText("Header modification detected.");
            } else {
                headerDiffIcon.setImageResource(R.drawable.verified_icon);
                headerDifferenceInfoTv.setText("No header modification detected.");
            }
            // Enable view headers and start over buttons
            viewHeadersBtn.setEnabled(true); viewHeadersBtn.setVisibility(View.VISIBLE);
            startOverBtn.setEnabled(true); startOverBtn.setVisibility(View.VISIBLE);

            // Disable + hide send button
            sendBtn.setEnabled(false); sendBtn.setVisibility(View.INVISIBLE);

            // Set diffTv to display diffHeadersStr when the viewHeadersBtn is pressed
            if (diffHeadersList.isEmpty()) {
                diffTv.append("No header modifications were detected!");
            } else {
                Collections.sort(diffHeadersList);
                for (String entry : diffHeadersList) {
                    diffTv.append(entry + "\n");
                }
            }
        }

        private String connectToURL(String url) throws IOException {
            // Connect to Tagged server and return server's response
            URL myURL = new URL(url);
            HttpURLConnection conn = (HttpURLConnection)myURL.openConnection();
            try {
                conn.setReadTimeout(10 * 1000); // milliseconds
                conn.setConnectTimeout(10 * 1000); // milliseconds

                // Set request headers, and then create TreeMap using getRequestProperties()
                // NOTE: addRequestProperty() method does NOT override existing values
                conn.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
                conn.addRequestProperty("Accept-Encoding", "gzip, deflate, sdch");
                conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
                conn.addRequestProperty("Cache-Control", "max-age=0, no-cache");
                conn.addRequestProperty("Connection", "close");
                conn.addRequestProperty("Host", serverIP);
                conn.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.111 Safari/537.36");
                //Log.d(TAG, "getRequestProperties.toString(): " + conn.getRequestProperties().toString());

                // Convert getRequestProperties() list parameter to string by creating TreeMap (red-black tree) to get sorted keys
                origHeadersMap = new TreeMap<>();
                for (Map.Entry<String, List<String>> entry : conn.getRequestProperties().entrySet()) {
                    origHeadersMap.put(entry.getKey(), entry.getValue().toString().replace("[", "").replace("]", "")); // Remove brackets from list
                }
                //Log.d(TAG, "origHeadersMap.toString(): " + origHeadersMap.toString());

                // Connect to URL, get Tagged server content, then disconnect
                conn.connect();
                InputStream taggedResponse = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(taggedResponse));
                StringBuilder taggedContent = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    taggedContent.append(line);
                }
                responseStr = taggedContent.toString();
                conn.disconnect();
            } catch (MalformedURLException e) {
                e.printStackTrace();
                Log.d(TAG, "ERROR MalformedURLException caught: " + e);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "ERROR IOException caught: " + e);
            }

            Log.d(TAG, "Tagged server echoed the following response string: " + responseStr);
            return responseStr;
        }
    }

    protected void onStop() {
        super.onStop();
    }
}