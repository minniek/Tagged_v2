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

    // Server, Proxy variables
    final String serverIP = "192.168.1.4"; final String serverPage = "server_v1.php";
    final String proxyIP = "192.168.1.11"; final int proxyPort = 1717;

    // Display values
    String responseStr = "";
    JSONObject origHeadersJSON;
    JSONObject modHeadersJSON;
    StringBuilder diffHeaders;

    String TAG = "Tagged_v2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        send = (Button)findViewById(R.id.send_btn);
        clear = (Button)findViewById(R.id.clear_btn);
        compare = (Button)findViewById(R.id.compare_btn);
        urlTxt = (EditText)findViewById(R.id.url_editText);
        responseTxt = (TextView)findViewById(R.id.responseStr_textView);
        reqHeadersOrigTxt = (TextView)findViewById(R.id.reqHeadersOrig_textView);
        diffTxt = (TextView)findViewById(R.id.diff_textView);

        // Set onClickListener event to buttons
        send.setOnClickListener(this);
        clear.setOnClickListener(this);
        compare.setOnClickListener(this);

        // Preload URL to connect to Tagged! server that is running on localhost
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
            // TODO After debugging, move entire code to onPostExecute (tested on 2/12/15 @ 1:25AM, didn't crash)
            diffHeaders = new StringBuilder();

            try {
                modHeadersJSON = new JSONObject(responseStr);
                Log.d(TAG, "origHeadersJSON.toString(): " + origHeadersJSON.toString()); // DEBUGGING
                Log.d(TAG, "modHeadersJSON.toString(): " + modHeadersJSON.toString()); // DEBUGGING

                // TODO Use Gson or Jackson to convert JSONObject to Map...
                // Convert JSONObject to Map to facilitate parsing
                Map<String,String> origHeadersMap = new HashMap<>();
                Iterator origIter = origHeadersJSON.keys();
                while(origIter.hasNext()) {
                    String key = (String) origIter.next();
                    try {
                        String value = origHeadersJSON.getString(key);
                        origHeadersMap.put(key, value);
                        Log.d(TAG, "origHeadersMap: " + origHeadersMap.toString()); // DEBUGGING
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                Map<String,String> modHeadersMap = new HashMap<>();
                Iterator modIter = modHeadersJSON.keys();
                while(modIter.hasNext()) {
                    String key = (String) modIter.next();
                    try {
                        String value = modHeadersJSON.getString(key);
                        modHeadersMap.put(key, value);
                        Log.d(TAG, "modHeadersMap: " + modHeadersMap.toString()); // DEBUGGING
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                // Compare original with modified...display the diff
                for (Map.Entry<String, String> modHeader : modHeadersMap.entrySet()) {
                    if ((!origHeadersMap.containsKey(modHeader.getKey())) ||
                            (!origHeadersMap.containsValue(modHeader.getValue()))) {
                        diffHeaders.append(modHeader.getKey() + ":" + modHeader.getValue() + "\n");
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            diffTxt.setText(diffHeaders);
        }
    }

    private class StartAsyncTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            // params comes from the execute() call: params[0] is the url
            try {
                connectToURL(urls[0]);
                return responseStr; // return value to be used in onPostExecute
            } catch (IOException e) {
                responseStr = "Error: could not connect to URL. Please check URL";
                return responseStr;
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
            //HttpURLConnection conn = (HttpURLConnection)myURL.openConnection();

            // Connect to URL and display HTML of Tagged Server
            try {
                conn.setReadTimeout(20*1000); // milliseconds
                conn.setConnectTimeout(20*1000); // milliseconds

                // Set request headers
                conn.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
                conn.addRequestProperty("Accept-Encoding", "gzip, deflate, sdch");
                conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
                conn.addRequestProperty("Cache-Control", "max-age=123456789");
                //conn.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.111 Safari/537.36");
                //reqHeadersOrig = conn.getRequestProperties(); // returns Map<String, List<String>> data structure

                // Create JSON object that stores original request headers
                origHeadersJSON = new JSONObject();
                try {
                    origHeadersJSON.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
                    origHeadersJSON.put("Accept-Encoding", "gzip, deflate, sdch");
                    origHeadersJSON.put("Accept-Language", "en-US,en;q=0.8");
                    origHeadersJSON.put("Cache-Control", "max-age=123456789");
                }  catch (JSONException e) {
                    e.printStackTrace();
                }

                // Connect to URL
                conn.connect();

                // Read and display HTML of Tagged Server (should also output modified request headers made by Tagged Proxy...)
                InputStream htmlContent = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(htmlContent, "UTF-8"));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
                responseStr = content.toString();

                // Close connection and return server's output
                conn.disconnect();
                return responseStr;
            } catch (MalformedURLException e) {
                e.printStackTrace();
                responseStr = "ERROR MalformedURLException caught: " + e;
                return responseStr;
            } catch (IOException e) {
                e.printStackTrace();
                responseStr = "ERROR IOException caught: " + e;
                return responseStr;
            }
        }
    }
}