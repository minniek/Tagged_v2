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
import android.widget.EditText;
import android.widget.TextView;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;


public class MainActivity extends Activity implements View.OnClickListener {

    // Declare widgets
    Button send, clear, compare;
    EditText urlTxt;
    TextView responseTxt, reqHeadersOrigTxt, diffTxt;

    // PHP Server, Python Proxy variables
    final String serverIP = "192.168.1.4"; final String serverPage = "server_v1.php";
    final String proxyIP = "192.168.1.10"; final int proxyPort = 1717;

    String responseStr = "";
    //Map<String, List<String>> requestProperties;
    TreeMap<String,String> origHeadersMap, modHeadersMap;
    //JSONObject origHeadersJSON;
    JSONObject modHeadersJSON;
    StringBuilder diffHeaders;
    String TAG = "Tagged_v2";

    // Crypto variables
    private static final String HMAC_SHA1_ALGO = "HmacSHA1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Map widgets to XML
        send = (Button)findViewById(R.id.send_btn);
        clear = (Button)findViewById(R.id.clear_btn);
        compare = (Button)findViewById(R.id.compare_btn);
        urlTxt = (EditText)findViewById(R.id.url_editText);
        responseTxt = (TextView)findViewById(R.id.responseStr_textView);
        reqHeadersOrigTxt = (TextView)findViewById(R.id.reqHeadersOrig_textView);
        diffTxt = (TextView)findViewById(R.id.diff_textView);

        // Set onClickListener event and get rid of default settings on buttons
        send.setOnClickListener(this); send.setTransformationMethod(null);
        clear.setOnClickListener(this); clear.setTransformationMethod(null);
        compare.setOnClickListener(this); compare.setTransformationMethod(null);

        // Preload URL to connect to Tagged server
        urlTxt.setText("http://" + serverIP + "/" + serverPage, TextView.BufferType.EDITABLE);
    }

    // Assign tasks for buttons
    public void onClick(View v) {
        if (v == send) {

            diffTxt.setText(""); // Clear out JSONException message from previous send if needed...

            // Check for network connection
            String stringURL = urlTxt.getText().toString();
            ConnectivityManager connMgr = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo nwInfo = connMgr.getActiveNetworkInfo();

            // If network is present, start AsyncTask to connect to given URL
            if ((nwInfo != null) && (nwInfo.isConnected())) {
                new StartAsyncTask().execute(stringURL);
            } else {
                responseTxt.setText("ERROR No network connection detected.");
            }
        }

        if (v == clear) {
            responseStr = "";
            //origHeadersJSON = new JSONObject();
            modHeadersJSON = new JSONObject();

            responseTxt.setText("");
            reqHeadersOrigTxt.setText("");
            diffTxt.setText("");
        }

        if (v == compare) {
            diffHeaders = new StringBuilder();
            try {
                // Convert JSONObject to Map
                modHeadersJSON = new JSONObject(responseStr); // responseStr stores PHP server's output (i.e. the modified headers)
                Log.d(TAG, "modHeadersJSON.toString(): " + modHeadersJSON.toString()); // DEBUGGING
                modHeadersMap = new TreeMap<>();
                modHeadersMap = new Gson().fromJson(modHeadersJSON.toString(), modHeadersMap.getClass());
                Log.d(TAG, "modHeadersMap.toString(): " + modHeadersMap.toString());
                responseTxt.setText(modHeadersMap.toString()); // Display modified headers

                // TODO Test this logic more thoroughly...it doesn't handle the case if a key:value pair is only in the origHeadersMap -_-
                // Compare original with modified and display the diff
                for (Map.Entry<String, String> modHeader : modHeadersMap.entrySet()) {
                    // Case One: maps do not have same keys or same values
                    if ((!origHeadersMap.containsKey(modHeader.getKey())) || (!origHeadersMap.containsValue(modHeader.getValue()))) {
                        diffHeaders.append(modHeader.getKey() + ":" + modHeader.getValue() + "\n");
                        // Case Two: both maps share the same key but have different values
                    } else if (origHeadersMap.containsKey(modHeader.getKey())) {
                        if (!origHeadersMap.get(modHeader.getKey()).contentEquals(modHeader.getValue())){
                            diffHeaders.append(modHeader.getKey() + ":" + modHeader.getValue() + "\n");
                        }
                    }
                }
                // Case Three: no differences are found
                if (origHeadersMap.equals(modHeadersMap)) { // Equivalent to "map1.entrySet().equals(map2.entrySet())
                    diffHeaders.append("No differences found.");
                }
            } catch (JSONException e) {
                e.printStackTrace();
                diffHeaders.append("ERROR Could not make JSONObject with \"responseStr\"");
            }
            diffTxt.setText(diffHeaders);
        }
    }

    private class StartAsyncTask extends AsyncTask<String, Void, String> {
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
            reqHeadersOrigTxt.setText(origHeadersMap.toString()); // Display original request headers
            responseTxt.setText(responseStr); // Display modified headers
        }

        private String connectToURL(String url) throws IOException {
            // Force HTTP requests to go through Proxy
            URL myURL = new URL(url);
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIP, proxyPort));
            HttpURLConnection conn = (HttpURLConnection) myURL.openConnection(proxy);

            // Connect to URL and display HTML of Tagged PHP server
            try {
                conn.setReadTimeout(10 * 1000); // milliseconds
                conn.setConnectTimeout(10 * 1000); // milliseconds

                // Create JSON object to store original request headers
                // NOTE: JSON objects do not allow duplicate keys and will override existing keys BUT keys are case-sensitive
                //origHeadersJSON = new JSONObject();
                //try {
                //origHeadersJSON.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
                    /*origHeadersJSON.put("Accept-Encoding", "gzip, deflate, sdch");
                    origHeadersJSON.put("Accept-Language", "en-US,en;q=0.8");
                    origHeadersJSON.put("Cache-Control", "max-age=0");
                    origHeadersJSON.put("Cache-Control", "max-age=123456789"); // TESTER - this will override the previous key-value assignment
                    origHeadersJSON.put("Connection", "close");
                    origHeadersJSON.put("key1", "value1");
                    origHeadersJSON.put("KEY2", "value2");
                    //origHeadersJSON.put("kEy2", "vAlue2"); // addRequestProperty() will treat this as key2=[value2, vAlue2]
                    origHeadersJSON.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.111 Safari/537.36");
                }  catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "origHeadersJSON.toString(): " + origHeadersJSON.toString());
                */

                /*
                // Convert JSON Object to HashMap
                origHeadersMap = new HashMap<>();
                Iterator origIter = origHeadersJSON.keys();
                while(origIter.hasNext()) {
                    String key = (String)origIter.next();
                    try {
                        String value = origHeadersJSON.getString(key);
                        origHeadersMap.put(key, value);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                Log.d(TAG, "origHeadersMap.toString(): " + origHeadersMap.toString());
                */

                 /*
                // Set request headers
                // NOTE: addRequestProperty method will NOT override existing properties; if same header is found
                for (Map.Entry<String, String> header : origHeadersMap.entrySet()) {
                    conn.addRequestProperty(header.getKey(), header.getValue());
                }
                */
                //Log.d(TAG, "getRequestProperties.toString()" + conn.getRequestProperties().toString().replace("[", "").replace("]", "")); // Returns Map<String, List<String>>

                // Set request headers using addRequestProperty()
                // NOTE: addRequestProperty() method does NOT override existing values but it also does NOT duplicate keys; keys are case-insensitive
                conn.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
                conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
                conn.addRequestProperty("Cache-Control", "max-age=0");
                conn.addRequestProperty("Cache-Control", "max-age=123456789"); // TESTER - should store as Cache-Control=[max-age=0,max-age=123456789]
                conn.addRequestProperty("Connection", "close");
                conn.addRequestProperty("Key1", "value1");
                conn.addRequestProperty("Key2", "value2");
                //conn.addRequestProperty("kEy2", "vAlue2"); // TESTER - should store as Key2=[value2, vAlue2]
                conn.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.111 Safari/537.36");
                Log.d(TAG, "getRequestProperties.toString() BEFORE HMAC Signature: " + conn.getRequestProperties().toString());

                // Convert getRequestProperties() List parameter to String by creating new tree map to get sorted keys
                origHeadersMap = new TreeMap<>();
                for (Map.Entry<String, List<String>> entry : conn.getRequestProperties().entrySet()) {
                    origHeadersMap.put(entry.getKey(), entry.getValue().toString().replace("[", "").replace("]", "")); // Remove brackets from list
                    //origHeadersMap.put(entry.getKey(), entry.getValue().toString());
                }
                Log.d(TAG, "origHeadersMap.toString() BEFORE HMAC Signature: " + origHeadersMap.toString());

/**********************************************************************************************************************************************************************/
                // Create HMAC signature and send it as a header value ---> Test-Sig: <testHMACSig>
                // Reference: http://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/AuthJavaSampleHMACSignature.html
                String testHMACSig ="";
                try {
                    // Get a HMAC-SHA1 key from the raw key bytes
                    String testKey = "this is not a real key";
                    byte[] testKeyByte = testKey.getBytes();
                    SecretKeySpec testSigningKey = new SecretKeySpec(testKeyByte, HMAC_SHA1_ALGO);

                    // Get an HMAC-SHA1 Mac instance and initialize with the signing key
                    Mac testMac = Mac.getInstance(HMAC_SHA1_ALGO);
                    testMac.init(testSigningKey);

                    // Compute the HMAC on input data bytes
                    Log.d(TAG, "Data for HMAC: " + origHeadersMap.toString());
                    byte[] testRawHmac = testMac.doFinal(origHeadersMap.toString().getBytes());

                    // Base64-encode the HMAC
                    testHMACSig = Base64.encodeToString(testRawHmac, 0); // 0 - Default
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                    Log.e(TAG, "ERROR InvalidKeyException: " + e);
                } catch (NoSuchAlgorithmException e){
                    e.printStackTrace();
                    Log.e(TAG, "ERROR NoSuchAlgorithmException: " + e);
                }
                Log.d(TAG, "testHMACSig: " + testHMACSig);
                // Add HMAC signature as a request header value
                conn.addRequestProperty("Test-Sig", testHMACSig);
/**********************************************************************************************************************************************************************/
                // Create new origHeadersMap to include the Test-Sig header
                origHeadersMap = new TreeMap<>();
                for (Map.Entry<String, List<String>> entry : conn.getRequestProperties().entrySet()) {
                    origHeadersMap.put(entry.getKey(), entry.getValue().toString().replace("[", "").replace("]", "")); // Remove brackets from list
                    //origHeadersMap.put(entry.getKey(), entry.getValue().toString());
                }
                Log.d(TAG, "origHeadersMap.toString() AFTER HMAC Signature: " + origHeadersMap.toString());

                // Connect to URL and get PHP server content
                conn.connect();
                InputStream PHPResponse = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(PHPResponse, "UTF-8"));
                StringBuilder PHPContent = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    PHPContent.append(line);
                }

                // Close connection and return PHP server content
                conn.disconnect();
                return responseStr = PHPContent.toString();
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