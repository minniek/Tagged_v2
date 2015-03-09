/* Tagged - Detect HTTP Request Header Modification
 * References:
 * http://developer.android.com/training/basics/network-ops/connecting.html
 * http://www.compiletimeerror.com/2013/01/why-and-how-to-use-asynctask.html#.VMKb5P7F95A
 *
 */

package com.example.minnie.tagged_v2;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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

public class MainActivity extends Activity implements View.OnClickListener {

    // Declare widgets
    Button send, verify, clear;
    TextView responseTxt, reqHeadersOrigTxt, diffTxt;

    // Tagged server variables
    final String serverIP = "155.41.123.98"; final String serverPage = "server_v1.php";

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
        send = (Button) findViewById(R.id.send_btn);
        verify = (Button) findViewById(R.id.verify_btn);
        clear = (Button) findViewById(R.id.clear_btn);
        responseTxt = (TextView) findViewById(R.id.responseStr_textView);
        reqHeadersOrigTxt = (TextView) findViewById(R.id.reqHeadersOrig_textView);
        diffTxt = (TextView) findViewById(R.id.diff_textView);

        // Set onClickListener event and get rid of default settings on buttons
        send.setOnClickListener(this); send.setTransformationMethod(null);
        clear.setOnClickListener(this); clear.setTransformationMethod(null);
        verify.setOnClickListener(this); verify.setTransformationMethod(null);
    }

    // Assign tasks for buttons
    public void onClick(View v) {
        if (v == send) {
            // Check for network connection
            String taggedURL = "http://" + serverIP + "/" + serverPage;
            ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo nwInfo = connMgr.getActiveNetworkInfo();

            // If network is present, start AsyncTask to connect to given URL
            if ((nwInfo != null) && (nwInfo.isConnected())) {
                new StartAsyncTask().execute(taggedURL);
            } else {
                responseTxt.setText("ERROR No network connection detected.");
            }
        }

        if (v == verify) {
            // Extract the signature from modHeadersMap and remove it from map
            String digSig = "";
            boolean isVerified;

            for (Map.Entry<String, String> modHeader : modHeadersMap.entrySet()) {
                if (modHeader.getKey().equals("Auth")) {
                    digSig = modHeader.getValue();
                }
            }
            //modHeadersMap.remove("Auth");
            Log.d(TAG, "Digital signature: " + digSig);
            Log.d(TAG, "Modified Headers minus Auth: " + modHeadersMap.toString());

            // Extract Tagged server's public key from res and create PublicKey instance
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

                // Verify signature
                try {
                    Signature sig = Signature.getInstance("SHA256withRSA");
                    sig.initVerify(pubKey);

                    /*  "responseStr" includes the data and format that was used to create the digital signature in the Tagged server
                     *  Need to remove the "Auth" header
                     */
                    //
                    Log.d(TAG, "responseStr before: " + responseStr);
                    responseStr = responseStr.replaceAll(",\"Auth\":.*$", "}");
                    Log.d(TAG, "responseStr after: " + responseStr);
                    sig.update(responseStr.getBytes());

                    isVerified = sig.verify(Base64.decode(digSig, Base64.DEFAULT));
                    Log.d(TAG, "Signature verified?: " + isVerified);
                } catch (SignatureException e) {
                    e.printStackTrace();
                }
            } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
                e.printStackTrace();
            }

            if (isVerified = true) {
                StringBuilder diffHeaders = new StringBuilder();

                // Start comparing and mark the modified/new headers
                for (Map.Entry<String, String> modHeader : modHeadersMap.entrySet()) {
                    // Case One: maps do not have same keys or same values
                    if ((!origHeadersMap.containsKey(modHeader.getKey())) || (!origHeadersMap.containsValue(modHeader.getValue()))) {
                        diffHeaders.append(modHeader.getKey() + ":" + modHeader.getValue() + "\n");
                        // Case Two: both maps share the same key but have different values
                    } else if (origHeadersMap.containsKey(modHeader.getKey())) {
                        if (!origHeadersMap.get(modHeader.getKey()).contentEquals(modHeader.getValue())) {
                            diffHeaders.append(modHeader.getKey() + ":" + modHeader.getValue() + "\n");
                        }
                    }
                    diffTxt.setText(diffHeaders);
                }
            } else {
                diffTxt.setText("Signature verification failed!.");
            }
        }

        if (v == clear) {
            responseStr = "";
            responseTxt.setText("");
            reqHeadersOrigTxt.setText("");
            diffTxt.setText("");
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
            // Display original headers
            for (Map.Entry<String, String> entry : origHeadersMap.entrySet()) {
                reqHeadersOrigTxt.append(entry.getKey() + ": " + entry.getValue() + "\n");
            }

            // Display modified headers
            for (Map.Entry<String, String> entry : modHeadersMap.entrySet()) {
                responseTxt.append(entry.getKey() + ": " + entry.getValue() + "\n");
            }
        }

        private String connectToURL(String url) throws IOException {
            URL myURL = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) myURL.openConnection();

            /*// Uncomment to "force" app to use proxy
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

                // Connect to URL and get Tagged server content
                conn.connect();
                InputStream taggedResponse = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(taggedResponse, "UTF-8"));
                StringBuilder taggedContent = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    taggedContent.append(line);
                }
                responseStr = taggedContent.toString();
                conn.disconnect();

                // Make map of modified headers
                modHeadersMap = new TreeMap<>();
                try {
                    JSONObject modHeadersJSON= new JSONObject(responseStr);
                    //Log.d(TAG, "modHeadersJSON.toString(): " + modHeadersJSON.toString());
                    modHeadersMap = new Gson().fromJson(modHeadersJSON.toString(), modHeadersMap.getClass());
                    //Log.d(TAG, "modHeadersMap.toString(): " + modHeadersMap.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }

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