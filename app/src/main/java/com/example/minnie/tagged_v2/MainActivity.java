/* Tagged - Detect HTTP Request Header Modifications
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
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
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
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
//import java.net.Proxy;

public class MainActivity extends ActionBarActivity implements View.OnClickListener {

    // Declare widgets
    Button sendBtn, viewHeadersBtn, clearBtn;
    TextView responseTv, origHeaderTv, diffTv;
    TableLayout headerTable;
    Toolbar toolbar;

    // Tagged server variables
    final String serverIP = "192.168.1.6"; final String serverPage = "server_v1.php";

    /*// Uncomment to force app to use Python Proxy
    final String proxyIP = "192.168.42.1"; final int proxyPort = 1717;
    */

    String responseStr = "";
    TreeMap<String, String> origHeadersMap, modHeadersMap;
    String TAG = "Tagged_v2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Map widgets to XML
        sendBtn = (Button)findViewById(R.id.send_btn);
        viewHeadersBtn = (Button)findViewById(R.id.viewHeaders_btn);
        clearBtn = (Button)findViewById(R.id.clear_btn);
        responseTv = (TextView)findViewById(R.id.responseStr_textView);
        origHeaderTv = (TextView)findViewById(R.id.origHeader_textView);
        diffTv = (TextView)findViewById(R.id.diffHeader_textView);
        headerTable = (TableLayout)findViewById(R.id.header_table);
        toolbar = (Toolbar)findViewById(R.id.toolbar);

        // Set onClickListener event and get rid of default settings on buttons
        sendBtn.setOnClickListener(this); sendBtn.setTransformationMethod(null);
        clearBtn.setOnClickListener(this); clearBtn.setTransformationMethod(null);
        viewHeadersBtn.setOnClickListener(this); viewHeadersBtn.setTransformationMethod(null);

        // Toolbar settings
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        toolbar.setLogo(R.drawable.tagged_logo_v6);

        // Initial widget settings
        viewHeadersBtn.setEnabled(false); viewHeadersBtn.setVisibility(View.INVISIBLE);
        clearBtn.setEnabled(false); clearBtn.setVisibility(View.INVISIBLE);
        headerTable.setEnabled(false); headerTable.setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu (i.e. adds items to the action bar)
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.send_btn:
                //Toast.makeText(getApplicationContext(), "Testing 123...", Toast.LENGTH_LONG).show();
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
                new StartAsyncTask().execute(taggedURL); // Returns "responseStr"
            } else {
                responseTv.setText("ERROR No network connection detected.");
            }

            // Begin signature verification process
            // StartAsyncTask().execute(taggedURL) returns "responseStr"
            // Create modified header map with "responseStr" to extract "Auth" header, which contains the digital signature
            modHeadersMap = new TreeMap<>();
            try {
                JSONObject modHeadersJSON= new JSONObject(responseStr);
                //Log.d(TAG, "modHeadersJSON.toString(): " + modHeadersJSON.toString());
                modHeadersMap = new Gson().fromJson(modHeadersJSON.toString(), modHeadersMap.getClass());
                //Log.d(TAG, "modHeadersMap.toString(): " + modHeadersMap.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }

            // Extract the digital signature from modHeadersMap
            String digSig = "";
            for (Map.Entry<String, String> modHeader : modHeadersMap.entrySet()) {
                if (modHeader.getKey().equals("Auth")) {
                    digSig = modHeader.getValue();
                }
            }
            //modHeadersMap.remove("Auth");
            //Log.d(TAG, "Digital signature: " + digSig);
            //Log.d(TAG, "Modified Headers minus Auth: " + modHeadersMap.toString());

            // Extract Tagged server's public key from "public_key.pem" in res/raw and create PublicKey instance
            boolean isVerified;
            try {
                InputStream is = this.getResources().openRawResource(R.raw.public_key);
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder pubKeySB = new StringBuilder();
                String line;
                try {
                    while ((line = br.readLine()) != null)
                        pubKeySB.append(line);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Remove header and footer
                String pubKeyStr = pubKeySB.toString();
                pubKeyStr = pubKeyStr.replace("-----BEGIN PUBLIC KEY-----", "");
                pubKeyStr = pubKeyStr.replace("-----END PUBLIC KEY-----", "");
                //Log.d(TAG, "Public key string: " + pubKeyStr);

                // Create Public Key instance from pubKeyStr
                X509EncodedKeySpec spec = new X509EncodedKeySpec(Base64.decode(pubKeyStr, Base64.DEFAULT));
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PublicKey pubKey = kf.generatePublic(spec);

                // Verify digital signature from Tagged server
                try {
                    Signature sig = Signature.getInstance("SHA256withRSA");
                    sig.initVerify(pubKey);

                    // "responseStr" contains the data received from Tagged server
                    // Remove the "Auth" header before processing
                    String responseStrEdited = "";  // Will contain data received from Tagged server, minus the "Auth" header
                    //Log.d(TAG, "responseStr before removing "Auth": " + responseStr);
                    responseStrEdited = responseStr.replaceAll(",\"Auth\":.*$", "}");
                    //Log.d(TAG, "responseStr after removing "Auth": " + responseStr);
                    sig.update(responseStrEdited.getBytes());

                    isVerified = sig.verify(Base64.decode(digSig, Base64.DEFAULT));
                    Log.d(TAG, "Signature verified?: " + isVerified);
                } catch (SignatureException e) {
                    e.printStackTrace();
                }
            } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
                e.printStackTrace();
            }

            // If signature is verified, alert user and give option to view headers
            if (isVerified = true) {
                Toast.makeText(getApplicationContext(), "Signature verified!", Toast.LENGTH_SHORT).show();
                sendBtn.setEnabled(false); sendBtn.setVisibility(View.INVISIBLE);
                viewHeadersBtn.setEnabled(true); viewHeadersBtn.setVisibility(View.VISIBLE);
                clearBtn.setEnabled(true); clearBtn.setVisibility(View.VISIBLE);

            } else {
                Toast.makeText(getApplicationContext(), "Signature NOT verified!", Toast.LENGTH_SHORT).show();
            }
        }

        if (v == viewHeadersBtn) {
            viewHeadersBtn.setEnabled(true); viewHeadersBtn.setVisibility(View.INVISIBLE);
            clearBtn.setEnabled(true); clearBtn.setVisibility(View.VISIBLE);
            headerTable.setEnabled(true); headerTable.setVisibility(View.VISIBLE);

            // Display original headers
            for (Map.Entry<String, String> entry : origHeadersMap.entrySet()) {
                origHeaderTv.append(entry.getKey() + ": " + entry.getValue() + "\n");
            }

            // Display modified headers
            for (Map.Entry<String, String> entry : modHeadersMap.entrySet()) {
                responseTv.append(entry.getKey() + ": " + entry.getValue() + "\n");
            }

            // Compare and display the modified headers
            StringBuilder diffHeaders = new StringBuilder();
            modHeadersMap.remove("Auth");
            //Log.d(TAG, modHeadersMap.toString());
            MapDifference<String, String> mapDiff = Maps.difference(origHeadersMap, modHeadersMap);
            //Log.d(TAG, mapDiff.entriesOnlyOnRight().toString());
            diffHeaders.append(mapDiff.entriesOnlyOnRight().toString().replace("{", "").replace("}", ""));
            diffTv.setText(diffHeaders);

            if (diffHeaders.toString().equals("")) {
                diffTv.setText("No header modification was detected.");
            }
        }

        if (v == clearBtn) {
            responseStr = "";
            responseTv.setText("");
            origHeaderTv.setText("");
            diffTv.setText("");

            viewHeadersBtn.setEnabled(false); viewHeadersBtn.setVisibility(View.INVISIBLE);
            sendBtn.setEnabled(true); sendBtn.setVisibility(View.VISIBLE);
            clearBtn.setEnabled(false); clearBtn.setVisibility(View.INVISIBLE);
            headerTable.setEnabled(false); headerTable.setVisibility(View.INVISIBLE);
        }
    }

    protected class StartAsyncTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            // Params come from the execute() call: params[0] is the url
            try {
                connectToURL(urls[0]);
                return responseStr; // return value to be used in onPostExecute
            } catch (IOException e) {
                return responseStr = "Error: could not connect to URL. Please check URL";
            }
        }

        // onPostExecute displays the results of the AsyncTask
        protected void onPostExecute(String responseStr) {
            // Make map of modified headers using responseStr
            modHeadersMap = new TreeMap<>();
            try {
                JSONObject modHeadersJSON= new JSONObject(responseStr);
                modHeadersMap = new Gson().fromJson(modHeadersJSON.toString(), modHeadersMap.getClass());
                //Log.d(TAG, "modHeadersJSON.toString(): " + modHeadersJSON.toString());
                //Log.d(TAG, "modHeadersMap.toString(): " + modHeadersMap.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private String connectToURL(String url) throws IOException {
            URL myURL = new URL(url);
            HttpURLConnection conn = (HttpURLConnection)myURL.openConnection();

            /*// Uncomment to "force" app to use Python proxy
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIP, proxyPort));
            HttpURLConnection conn = (HttpURLConnection) myURL.openConnection(proxy);
            */

            // Connect to URL and display output of Tagged server
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
                Log.d(TAG, "Tagged server echoed the following response string: " + responseStr);
                return responseStr;
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return responseStr = "ERROR MalformedURLException caught: " + e;
            } catch (IOException e) {
                e.printStackTrace();
                return responseStr = "ERROR IOException caught: " + e;
            }
        }
    }

    protected void onStop() {
        super.onStop();
    }
}