/* Tagged - Detect HTTP Request Header Modification
 * References:
 * http://developer.android.com/training/basics/network-ops/connecting.html
 * http://www.compiletimeerror.com/2013/01/why-and-how-to-use-asynctask.html#.VMKb5P7F95A
 *
 */

package com.example.minnie.tagged_v2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.Proxy;

import org.json.JSONException;
import org.json.JSONObject;

import android.support.v7.app.ActionBarActivity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends ActionBarActivity implements View.OnClickListener {

    Button send, clear;
    EditText urlTxt;
    TextView responseTxt, reqHeadersOrigTxt;

    final String serverIP = "192.168.1.5";
    final String serverPage = "server_v1.php";
    final String proxyIP = "192.168.16.130";
    final int proxyPort = 1717;
    final String TAG = "tagged_v1"; // Logcat

    String responseStr = ""; // Will display result in TextView "responseStrTxt"
    String reqHeadersOrig = ""; // Will display result in TextView "reqHeadersOrigTxt"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        send = (Button)findViewById(R.id.send_btn);
        clear = (Button)findViewById(R.id.clear_btn);
        urlTxt = (EditText)findViewById(R.id.url_editText);
        responseTxt = (TextView)findViewById(R.id.responseStr_textView);
        reqHeadersOrigTxt = (TextView)findViewById(R.id.reqHeadersOrig_textView);

        // Set onClickListener to buttons
        send.setOnClickListener(this);
        clear.setOnClickListener(this);

        // Preload URL to connect to Tagged! server that is running on localhost
        urlTxt.setText("http://" + serverIP + "/" + serverPage, TextView.BufferType.EDITABLE);
    }

    // Assign tasks for buttons
    public void onClick(View v) {
        if (v == send) {
            // Check for network connection before proceeding...
            String stringURL = urlTxt.getText().toString();
            ConnectivityManager connMgr = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo nwInfo = connMgr.getActiveNetworkInfo();
            // If network is present, start AsyncTask to connect to given URL
            if ((nwInfo != null) && (nwInfo.isConnected())) {
                new GoToWebpageTask().execute(stringURL);
            } else {
                responseTxt.setText("ERROR: No network connection detected.");
            }

            // Convert reqHeadersOrig (Map) to JSON
            try {
                JSONObject json = new JSONObject(reqHeadersOrig);
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        if (v == clear) {
            responseTxt.setText("");
            reqHeadersOrigTxt.setText("");
        }
    }

    private class GoToWebpageTask extends AsyncTask<String, Void, String> {
        //String responseStr = ""; // Will display result in Textview "responseStrTxt"
        //String reqHeadersOrig = ""; // Will display result in TextView "reqHeadersOrigTxt"

        @Override
        protected String doInBackground(String... urls) {
            // params comes from the execute() call: params[0] is the url
            try {
                connectToUrl(urls[0]);
                return responseStr; // return value to be used in onPostExecute
            } catch (IOException e) {
                responseStr = "Error: could not connect to URL. Please check URL";
                return responseStr;
            }
        }

        // onPostExecute displays the results of the AsyncTask
        protected void onPostExecute(String responseStr) {
            responseTxt.setText(responseStr);
            reqHeadersOrigTxt.setText(reqHeadersOrig);
        }

        private String connectToUrl(String url) throws IOException {
            // Force HTTP requests to go through Proxy
            URL myURL = new URL(url);
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIP, proxyPort));
            HttpURLConnection conn = (HttpURLConnection)myURL.openConnection(proxy);

            // Connect to URL and display HTML of Tagged Server
            try {
                conn.setReadTimeout(20*1000); // milliseconds
                conn.setConnectTimeout(20*1000); // milliseconds

                // Set header requests
                conn.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
                conn.addRequestProperty("Accept-Encoding", "gzip, deflate, sdch");
                conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
                conn.addRequestProperty("Cache-Control", "max-age=123456789");
                //conn.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.111 Safari/537.36");
                reqHeadersOrig = conn.getRequestProperties().toString();

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
                responseStr = "ERROR: MalformedURLException caught: " + e;
                return responseStr;
            } catch (IOException e) {
                e.printStackTrace();
                responseStr = "ERROR: IOException caught: " + e;
                return responseStr;
            }
        }
    }
}
