/* Tagged - Detect HTTP Request Header Modification
 * References:
 * http://developer.android.com/training/basics/network-ops/connecting.html
 * http://www.compiletimeerror.com/2013/01/why-and-how-to-use-asynctask.html#.VMKb5P7F95A
 *
 */

package com.example.minnie.tagged_v2;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends ActionBarActivity implements View.OnClickListener {

    // Declare widgets
    Button send, clear, compare;
    EditText urlTxt;
    TextView responseTxt, reqHeadersOrigTxt, diffTxt;

    // PHP Server, Python Proxy variables
    final String serverIP = "155.41.46.89"; final String serverPage = "server_v1.php";
    final String proxyIP = "155.41.124.7"; final int proxyPort = 1717;

    String responseStr = "";
    Map<String,String> origHeadersMap;
    JSONObject origHeadersJSON, modHeadersJSON;
    StringBuilder diffHeaders;
    String TAG = "Tagged_v2";

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
            // First, check for network connection
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
            responseTxt.setText("");
            reqHeadersOrigTxt.setText("");
            diffTxt.setText("");
        }

        if (v == compare) {
            diffHeaders = new StringBuilder();
            try {
                modHeadersJSON = new JSONObject(responseStr);
                Log.d(TAG, "modHeadersJSON.toString(): " + modHeadersJSON.toString()); // DEBUGGING

                // TODO Use Gson or Jackson to convert JSONObject to Map...
                Map<String,String> modHeadersMap = new HashMap<>();
                Iterator modIter = modHeadersJSON.keys();
                while(modIter.hasNext()) {
                    String key = (String)modIter.next();
                    try {
                        String value = modHeadersJSON.getString(key);
                        modHeadersMap.put(key, value);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                Log.d(TAG, "modHeadersMap.toString(): " + modHeadersMap.toString()); // DEBUGGING

                // Compare original with modified...display the diff
                // Recall: a map will not contain duplicate keys
                for (Map.Entry<String, String> modHeader : modHeadersMap.entrySet()) {
                    if ((!origHeadersMap.containsKey(modHeader.getKey())) || (!origHeadersMap.containsValue(modHeader.getValue()))) { // Case One: maps do not have same keys or same values
                        diffHeaders.append(modHeader.getKey() + ":" + modHeader.getValue() + "\n");
                    } else if (origHeadersMap.containsKey(modHeader.getKey())) { // Case Two: if both maps share the same key but have different values
                        if (!origHeadersMap.get(modHeader.getKey()).contentEquals(modHeader.getValue())){
                            diffHeaders.append(modHeader.getKey() + ":" + modHeader.getValue() + "\n");
                        }
                    }
                }

                // If no difference is found...
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
            responseTxt.setText(responseStr);
            reqHeadersOrigTxt.setText(origHeadersJSON.toString());
        }

        private String connectToURL(String url) throws IOException {
            // Force HTTP requests to go through Proxy
            URL myURL = new URL(url);
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIP, proxyPort));
            HttpURLConnection conn = (HttpURLConnection)myURL.openConnection(proxy);

            // Connect to URL and display HTML of Tagged PHP server
            try {
                conn.setReadTimeout(10*1000); // milliseconds
                conn.setConnectTimeout(10*1000); // milliseconds

                // Create JSON object to store original request headers
                // NOTE: JSON objects do not allow duplicate keys and will override existing keys
                origHeadersJSON = new JSONObject();
                try {
                    origHeadersJSON.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
                    origHeadersJSON.put("Accept-Encoding", "gzip, deflate, sdch");
                    origHeadersJSON.put("Accept-Language", "en-US,en;q=0.8");
                    origHeadersJSON.put("Cache-Control", "max-age=123456789");
                    origHeadersJSON.put("Connection", "close");
                    origHeadersJSON.put("Connection", "open"); // TESTER - this will override the previous key-value assignment
                    origHeadersJSON.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.111 Safari/537.36");
                }  catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "origHeadersJSON.toString(): " + origHeadersJSON.toString()); // DEBUGGING

                // Convert JSON Object to HashMap
                origHeadersMap = new HashMap<>();
                Iterator origIter = origHeadersJSON.keys();
                while(origIter.hasNext()) {
                    String key = (String)origIter.next();
                    try {
                        String value = origHeadersJSON.getString(key);
                        Log.d(TAG, "origHeadersJSON.get(key): " + origHeadersJSON.get(key).toString());
                        origHeadersMap.put(key, value);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                Log.d(TAG, "origHeadersMap.toString(): " + origHeadersMap.toString()); // DEBUGGING

                // Set request headers
                // NOTE: addRequestProperty method will NOT override existing properties (i.e. duplicate header-value pairs are allowed)
                for (Map.Entry<String, String> header : origHeadersMap.entrySet()) {
                   conn.addRequestProperty(header.getKey(), header.getValue());
                }
                //conn.addRequestProperty("Accept-Language", "DUPE"); // TESTER - duplicate header will be added
                //Log.d(TAG, "getRequestProperties.toString()" + conn.getRequestProperties().toString()); // DEBUGGING - returns Map<String, List<String>>

                // Connect to URL to read and display HTML of Tagged Server (should show modified request headers made by Tagged Proxy)
                conn.connect();
                InputStream htmlContent = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(htmlContent, "UTF-8"));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }

                // Close connection and return PHP server's HTML
                conn.disconnect();
                return responseStr = content.toString();
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return responseStr = "ERROR MalformedURLException caught: " + e;
            } catch (IOException e) {
                e.printStackTrace();
                return responseStr = "ERROR IOException caught: " + e;
            }
        }
    }
}