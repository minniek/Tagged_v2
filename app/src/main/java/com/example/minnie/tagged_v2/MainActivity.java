/* Tagged - Detect HTTP Request Header Modifications
 * References:
 * http://developer.android.com/training/basics/network-ops/connecting.html
 * http://www.compiletimeerror.com/2013/01/why-and-how-to-use-asynctask.html#.VMKb5P7F95A
 * http://stackoverflow.com/questions/11532989/android-decrypt-rsa-text-using-a-public-key-stored-in-a-file
 * http://docs.oracle.com/javase/tutorial/security/apisign/vstep4.html
 * http://docs.guava-libraries.googlecode.com/git/javadoc/com/google/common/collect/MapDifference.html
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
import android.widget.ImageButton;
import android.widget.ImageView;
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
    ImageButton sendBtn, viewHeadersBtn, startoverBtn;
    TextView responseTv, origHeaderTv, diffTv, sigVerInfoTv, headerDifferenceInfoTv;
    ImageView sigVerIcon, headerDiffIcon;
    TableLayout sigVerTable, headersTable;
    Toolbar toolbar;

    // Tagged server variables
    final String serverIP = "155.41.39.13"; final String serverPage = "server_v1.php";

    /*// Uncomment to force app to use Python Proxy
    final String proxyIP = "192.168.42.1"; final int proxyPort = 1717;
    */

    String responseStr = "";
    String digSigHeaderName = "Auth";
    TreeMap<String, String> origHeadersMap, modHeadersMap, diffHeadersMap;
    String TAG = "Tagged_v2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Buttons
        sendBtn = (ImageButton)findViewById(R.id.send_btn);
        viewHeadersBtn = (ImageButton)findViewById(R.id.viewheaders_btn);
        startoverBtn = (ImageButton)findViewById(R.id.startover_btn);
        sendBtn.setOnClickListener(this);
        startoverBtn.setOnClickListener(this);
        viewHeadersBtn.setOnClickListener(this);

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
        startoverBtn.setEnabled(false); startoverBtn.setVisibility(View.INVISIBLE);
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
            viewHeadersBtn.setEnabled(false); viewHeadersBtn.setVisibility(View.INVISIBLE);
            startoverBtn.setEnabled(true); startoverBtn.setVisibility(View.VISIBLE);
            sigVerTable.setEnabled(false); sigVerTable.setVisibility(View.INVISIBLE);
            headersTable.setEnabled(true); headersTable.setVisibility(View.VISIBLE);

            // Display original headers
            for (Map.Entry<String, String> entry : origHeadersMap.entrySet()) {
                origHeaderTv.append(entry.getKey() + ": " + entry.getValue() + "\n");
            }

            // Display headers sent to the Tagged app (client)
            if (modHeadersMap == null) {
                responseTv.append("ERROR: Could not fetch modified headers!");
            } else {
                for (Map.Entry<String, String> entry : modHeadersMap.entrySet()) {
                    responseTv.append(entry.getKey() + ": " + entry.getValue() + "\n");
                }
            }

            /*
            // Display headers received by Tagged server and the diff
            if (modHeadersMap == null) {
                responseTv.append("ERROR: Could not fetch modified headers!");
            } else {
                for (Map.Entry<String, String> entry : modHeadersMap.entrySet()) {
                    responseTv.append(entry.getKey() + ": " + entry.getValue() + "\n");
                }

                // Compare and display the modified headers
                // entriesOnlyOnRight(): returns map that contains keys that are only present in the right map (i.e. modHeadersMap)
                StringBuilder diffHeaders = new StringBuilder();
                modHeadersMap.remove("Auth");
                //Log.d(TAG, modHeadersMap.toString());
                MapDifference<String, String> mapDiff = Maps.difference(origHeadersMap, modHeadersMap);
                //Log.d(TAG, mapDiff.entriesOnlyOnRight().toString());
                diffHeaders.append(mapDiff.entriesOnlyOnRight().toString().replace("{", "").replace("}", ""));
                diffTv.setText(diffHeaders);

                if (diffHeaders.toString().equals("")) {
                    diffTv.setText("No header modifications were detected!");
                }
            }
            */
        }

        if (v == startoverBtn) {
            // Clear out text views
            responseTv.setText("");
            origHeaderTv.setText("");
            diffTv.setText("");

            // Enable + show send button only
            sendBtn.setEnabled(true); sendBtn.setVisibility(View.VISIBLE);

            // Disable + hide the view headers button, start over button, and all tables
            viewHeadersBtn.setEnabled(false); viewHeadersBtn.setVisibility(View.INVISIBLE);
            startoverBtn.setEnabled(false); startoverBtn.setVisibility(View.INVISIBLE);
            sigVerTable.setEnabled(false); sigVerTable.setVisibility(View.INVISIBLE);
            headersTable.setEnabled(false); headersTable.setVisibility(View.INVISIBLE);
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

        protected void onPostExecute(String responseStr) {
            // Call verifySignature method
            verifySignature(responseStr, digSigHeaderName);
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
                //Log.d(TAG, "Tagged server echoed the following response string: " + responseStr);
                return responseStr;
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return responseStr = "ERROR MalformedURLException caught: " + e;
            } catch (IOException e) {
                e.printStackTrace();
                //testFlag = true;
                return responseStr = "ERROR IOException caught: " + e;
            }
        }
    }

    public void verifySignature(String responseStr, String digSigHeaderName) {
        // Create map of modified headers using responseStr
        modHeadersMap = new TreeMap<>();
        try {
            JSONObject modHeadersJSON= new JSONObject(responseStr);
            modHeadersMap = new Gson().fromJson(modHeadersJSON.toString(), modHeadersMap.getClass());
            //Log.d(TAG, "modHeadersJSON.toString(): " + modHeadersJSON.toString());
            //Log.d(TAG, "modHeadersMap.toString(): " + modHeadersMap.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Extract the digital signature from modHeadersMap
        String digSig = "";
        for (Map.Entry<String, String> modHeader : modHeadersMap.entrySet()) {
            if (modHeader.getKey().equals(digSigHeaderName)) {
                digSig = modHeader.getValue();
            }
        }

        // Extract Tagged server's public key from "public_key.pem" in res/raw and create PublicKey instance
        boolean isVerified;
        try {
            InputStream is = this.getResources().openRawResource(R.raw.public_key);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder pubKeySb = new StringBuilder();
            String line;
            try {
                while ((line = br.readLine()) != null)
                    pubKeySb.append(line);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Remove header and footer
            String pubKeyStr = pubKeySb.toString();
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
                //Log.d(TAG, "responseStr before removing \"Auth\": " + responseStr);
                //responseStrEdited = responseStr.replaceAll(",\"Auth\":.*$", "}");
                //Log.d(TAG, "responseStr after removing \"Auth\": " + responseStrEdited);
                Log.d(TAG, "responseStr before removing \" + digSigHeaderName + \": " + responseStr);
                responseStrEdited = responseStr.replaceAll(",\"" + digSigHeaderName + "\":.*$", "}");
                Log.d(TAG, "responseStr after removing \" + digSigHeaderName + \": " + responseStrEdited);
                sig.update(responseStrEdited.getBytes());
                isVerified = sig.verify(Base64.decode(digSig, Base64.DEFAULT));
                Log.d(TAG, "Signature verified?: " + isVerified);

                // Alert user if signature is un/verified and give option to view headers
                if (isVerified == true) {
                    // Enable + show sig verified info
                    sigVerTable.setEnabled(true); sigVerTable.setVisibility(View.VISIBLE);
                    sigVerIcon.setImageResource(R.drawable.verified_icon);
                    sigVerInfoTv.setText("Tagged server signature verified.");

                    // Determine if there are any header modifications
                    checkRequestHeaderDifference(origHeadersMap, modHeadersMap, "Auth");

                    // Enable view headers and start over buttons
                    viewHeadersBtn.setEnabled(true); viewHeadersBtn.setVisibility(View.VISIBLE);
                    startoverBtn.setEnabled(true); startoverBtn.setVisibility(View.VISIBLE);

                    // Disable + hide send button
                    sendBtn.setEnabled(false); sendBtn.setVisibility(View.INVISIBLE);
                }

                if (isVerified == false) {
                    // Enable + show sig unverified info and view headers button
                    sigVerTable.setEnabled(true); sigVerTable.setVisibility(View.VISIBLE);
                    sigVerIcon.setImageResource(R.drawable.unverified_icon);
                    sigVerInfoTv.setText("Tagged server signature NOT verified.");

                    // Determine if there are any header modifications
                    checkRequestHeaderDifference(origHeadersMap, modHeadersMap, "Auth");

                    // Enable view headers and start over buttons
                    viewHeadersBtn.setEnabled(true); viewHeadersBtn.setVisibility(View.VISIBLE);
                    startoverBtn.setEnabled(true); startoverBtn.setVisibility(View.VISIBLE);

                    // Disable + hide send button
                    sendBtn.setEnabled(false); sendBtn.setVisibility(View.INVISIBLE);
                }
            } catch (SignatureException e) {
                e.printStackTrace();
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    public void  checkRequestHeaderDifference(Map<String, String> map1, Map<String, String> map2, String
                                              digSigHeaderName) {
        if (map1 == null) {
            headerDifferenceInfoTv.setText("ERROR: Could not process original request headers.");
        } else if (map2 == null ) {
            headerDifferenceInfoTv.setText("ERROR: Could not process request headers received by Tagged server.");
        } else {
            // Compare maps and check difference
            String diffHeaders = "";
            map1 = new TreeMap<>(map1);
            TreeMap<String, String> map2edited = new TreeMap<>(map2);
            map2edited.remove(digSigHeaderName);
            Log.d(TAG, "map1 in checkRequestHeaderDifference: " + map1.toString());
            Log.d(TAG, "map2edited (TreeMap with" + digSigHeaderName +  " removed): " + map2edited.toString());

            /*
            // Create MapDifference object and call entriesOnlyOnRight(leftmap, rightmap)
            // entriesOnlyOnRight(): returns map containing the entries from the right map whose keys are not present in the left map
            MapDifference<String, String> mapDiff = Maps.difference(map1, map2edited);
            //Log.d(TAG, mapDiff.entriesOnlyOnRight().toString());
            diffHeaders.append(mapDiff.entriesOnlyOnRight());
            Log.d(TAG, "Diff string: " + diffHeaders.toString());
            */

            // Testers for the following for-loop (when Python proxy not available)
            //map2edited.put("X-tagged", "mini");
            map2edited.remove("Host");
            map2edited.remove("Connection");
            Log.d(TAG, "map2edited when manually edited: " + map2edited.toString());


            TreeMap<String, String> diffHeadersMap1 = new TreeMap<>();
            TreeMap<String, String> diffHeadersMap2 = new TreeMap<>();
            TreeMap<String, String> diffHeadersMap3 = new TreeMap<>();
            int whichDiffHeadersMap = 0;

            // Three cases
            // Entry is the key-value pair
            for (Map.Entry<String, String> map2Entry : map2edited.entrySet()) {
                if (map1.size() != map2.size()) {
                    if (map1.size() > map2.size()) {
                        whichDiffHeadersMap = 1;
                        Log.d(TAG, " whichDiffHeadersMap: " +  whichDiffHeadersMap);
                        if (!map2.entrySet().contains(map1.entrySet())) {
                            diffHeadersMap1 = new TreeMap<>(map1);
                            diffHeadersMap1.remove(map2Entry);
                        }
                    }

                    // TODO Fix this logic
                    if (map1.size() < map2.size()) {
                        whichDiffHeadersMap = 2;
                        Log.d(TAG, " whichDiffHeadersMap: " +  whichDiffHeadersMap);
                        if (!map1.entrySet().contains(map2Entry)) {
                            diffHeadersMap2.put(map2Entry.getKey(), map2Entry.getValue());
                        }
                    }
                } else {
                    whichDiffHeadersMap = 3;
                    Log.d(TAG, " whichDiffHeadersMap: " +  whichDiffHeadersMap);
                    if (!map1.entrySet().contains(map2Entry)) {
                        diffHeadersMap3.put(map2Entry.getKey(), map2Entry.getValue());
                    }
                }
            }

            switch (whichDiffHeadersMap) {
                case 1: Log.d(TAG, "Diff headers: " + diffHeadersMap1.toString());
                        diffHeaders = diffHeadersMap1.toString();
                        // Set headers for display
                        for (Map.Entry<String, String> entry : diffHeadersMap1.entrySet()) {
                            diffTv.append(entry.getKey() + ": " + entry.getValue() + "\n");
                        }
                        break;
                case 2: Log.d(TAG, "Diff headers: " + diffHeadersMap2.toString());
                        diffHeaders = diffHeadersMap2.toString();
                        for (Map.Entry<String, String> entry : diffHeadersMap2.entrySet()) {
                            diffTv.append(entry.getKey() + ": " + entry.getValue() + "\n");
                        }
                        break;
                case 3: Log.d(TAG, "Diff headers: " + diffHeadersMap3.toString());
                        diffHeaders = diffHeadersMap2.toString();
                        for (Map.Entry<String, String> entry : diffHeadersMap3.entrySet()) {
                            diffTv.append(entry.getKey() + ": " + entry.getValue() + "\n");
                        }
                        break;
            }

            if (diffHeaders.equals("{}")) {
                headerDiffIcon.setImageResource(R.drawable.verified_icon);
                headerDifferenceInfoTv.setText("No header modifications were detected.");
            } else if (!diffHeaders.toString().contentEquals("{}")) { // Empty map
                headerDiffIcon.setImageResource(R.drawable.unverified_icon);
                headerDifferenceInfoTv.setText("Header modifications were detected.");
            }
        }
    }

    protected void onStop() {
        super.onStop();
    }
}