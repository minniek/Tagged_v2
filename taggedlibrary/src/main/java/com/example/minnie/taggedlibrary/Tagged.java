package com.example.minnie.taggedlibrary;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Set of methods related to Tagged App
 * @author Minnie Kim
 */
public class Tagged implements Map {

        static String TAG = "com.example.minnie.taggedlibrary";


        /**
         * Returns the public key string from a public key PEM file
         * @param myContext current context of application
         * @param pubKeyPemFileName the file name of the public key PEM file located in the /res/raw directory without the file name extension (ex. "public_key" instead of "public_key.pem")
         * @return the public key string without the header and footer
         */
        public String getPublicKeyString(Context myContext, String pubKeyPemFileName) {
            StringBuilder pubKeySb = null;
            int id = myContext.getResources().getIdentifier(pubKeyPemFileName, "raw", myContext.getPackageName());
            InputStream is = myContext.getResources().openRawResource(id);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            pubKeySb = new StringBuilder();
            String line;
            try {
                while ((line = br.readLine()) != null)
                    pubKeySb.append(line);
            } catch (IOException e) {
                e.printStackTrace();
            }
            String pubKeyStr = pubKeySb.toString();

            // Remove header and footer
            pubKeyStr = pubKeySb.toString();
            pubKeyStr = pubKeyStr.replace("-----BEGIN PUBLIC KEY-----", "");
            pubKeyStr = pubKeyStr.replace("-----END PUBLIC KEY-----", "");
            Log.d(TAG, "Public key string: " + pubKeyStr);

            return pubKeyStr;
        }


        /**
         * Verifies a digital signature
         * @param pubKeyStr a string that is the public key (RSA) without the header and footer (return value from getPublicKeyString method)
         * @param responseStr a string that is the data that was used to generate the digital signature
         * @param digSigHeaderName a string that is the name of the header that contains the digital signature
         * @return Boolean value that indicates whether the digital signature is verified
         */
        public Boolean verifySignature(String pubKeyStr, String responseStr, String digSigHeaderName) {
            // Extract digital signature from "responseStr"
            Log.d(TAG, "responseStr: " + responseStr);
            String digSig = responseStr;
            digSig = digSig.substring(digSig.indexOf(digSigHeaderName)); // Get beginning of digSigHeaderName
            digSig = digSig.replace(digSigHeaderName + "\":\"", ""); // Remove digSigHeaderName
            digSig = digSig.substring(0, digSig.indexOf("\"")); // Extract string before the next occurring quote (signifies end of the digital signature string)
            Log.d(TAG, "digSig: " + digSig);

            // Extract Tagged server's public key from "public_key.pem" in res/raw and create PublicKey instance
            boolean isVerified = false;

            try {
                // Create Public Key instance from pubKeyStr
                X509EncodedKeySpec spec = new X509EncodedKeySpec(Base64.decode(pubKeyStr, Base64.DEFAULT));
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PublicKey pubKey = kf.generatePublic(spec);

                // Verify digital signature from Tagged server
                try {
                    Signature sig = Signature.getInstance("SHA256withRSA");
                    sig.initVerify(pubKey);

                    // "responseStr" contains the data received from Tagged server
                    // Remove the digital signature header before processing
                    String responseStrEdited = "";  // Will contain data received from Tagged server, minus the digital signature header
                    Log.d(TAG, "responseStr before removing \"" + digSigHeaderName + "\": " + responseStr);
                    // TODO change this to fit all possible locations of the digital signature header
                    responseStrEdited = responseStr.replaceAll(",\"" + digSigHeaderName + "\":.*$", "}");
                    Log.d(TAG, "responseStr after removing \"" + digSigHeaderName + "\": " + responseStrEdited);
                    sig.update(responseStrEdited.getBytes());
                    isVerified = sig.verify(Base64.decode(digSig, Base64.DEFAULT));
                    //Log.d(TAG, "Signature verified?: " + isVerified);
                } catch (SignatureException | IllegalArgumentException e) {
                    e.printStackTrace();
                }
            } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
                e.printStackTrace();
            }
            return isVerified;
        }


        /**
         * Returns an ArrayList that contains the differences of headers in map1 and map2; used list since entries in maps cannot include duplicate keys
         * @param map1 a map that contains the original request headers
         * @param map2 a map that contains the request headers received by the app that may contain modified request headers
         * @param digSigHeaderName a string that is the name of the header that contains the digital signature that will appear in map2
         * (this value will not be considered during the comparison thus will be removed beforehand)
         * @return an ArrayList that contains the difference of the headers in map1 and map2
         */
        public ArrayList<String> getRequestHeaderDifferenceList(Map<String, String> map1, Map<String, String> map2, String digSigHeaderName) {
            ArrayList<String> diffHeadersList = new ArrayList<String>();

            if (map1 == null) {
                Log.d(TAG, "ERROR: Could not process original request headers sent.");
            } else if (map2 == null ) {
                Log.d(TAG, "ERROR: Could not process request headers received.");
            } else {
                // Compare maps and place differences in diffHeadersStr
                // Make new version of map2 that does not contain the digital signature and its header
                Map<String, String> map2Edited = new HashMap<>(map2);
                map2Edited.remove(digSigHeaderName);
                Log.d(TAG, "Map1 in checkRequestHeaderDifference: " + map1.toString());
                Log.d(TAG, "map2edited (Map2 with" + digSigHeaderName +  " removed): " + map2Edited.toString());

                // Compare entries in each map and store those that are not present in one or the other in diffHeadersMap
                // An entry is a key-value pair
                // If map1 does not contain an entry that is in map2edited, add that map2 entry to diffHeadersMap
                for (Map.Entry<String, String> map2EditedEntry : map2Edited.entrySet()) {
                    if (!map1.entrySet().contains(map2EditedEntry)) {
                        diffHeadersList.add(map2EditedEntry.toString());
                    }
                }
                // If map2edited does not contain an entry that is in map1, add that map1 entry to diffHeadersMap
                for (Map.Entry<String, String> map1Entry : map1.entrySet()) {
                    if (!map2Edited.entrySet().contains(map1Entry)) {
                        diffHeadersList.add(map1Entry.toString());
                    }
                }

                Log.d(TAG, "diffHeadersList.toString(): " + diffHeadersList.toString());
            }
            return diffHeadersList;
        }


        /**
         * Returns an ArrayList of strings containing all entry differences between map1 and map2...used list since entries in maps cannot include duplicate keys
         * @param map1
         * @param map2
         * @return an ArrayList of strings containing all entry differences between map1 and map2
         */
        public <K, V> ArrayList<String> getMapDifferenceList(Map<K, V> map1, Map<K, V> map2) {
            ArrayList<String> diffHeadersList = new ArrayList<String>();

            if (map1 == null) {
                Log.d(TAG, "ERROR: Could not process original request headers.");
            } else if (map2 == null ) {
                Log.d(TAG, "ERROR: Could not process request headers received by Tagged server.");
            } else {
                // Compare maps and store entry differences in diffHeadersStr
                // An entry is a key-value pair
                // If map1 does not contain an entry that is in map2, add that map2 entry to diffHeadersSb
                for (Map.Entry<K, V> map2Entry : map2.entrySet()) {
                    if (!map1.entrySet().contains(map2Entry)) {
                        diffHeadersList.add(map2Entry.toString());
                    }
                }
                // If map2 does not contain an entry that is in map1, add that map1 entry to diffHeadersSb
                for (Map.Entry<K, V> map1Entry : map1.entrySet()) {
                    if (!map2.entrySet().contains(map1Entry)) {
                        diffHeadersList.add(map1Entry.toString());
                    }
                }

                Log.d(TAG, "diffHeadersList.toString(): " + diffHeadersList.toString());
            }
            return diffHeadersList;
        }

        @Override
        public void clear() {
            // TODO Auto-generated method stub

        }

        @Override
        public boolean containsKey(Object key) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public boolean containsValue(Object value) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public Set entrySet() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Object get(Object key) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean isEmpty() {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public Set keySet() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Object put(Object key, Object value) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void putAll(Map arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public Object remove(Object key) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public int size() {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public Collection values() {
        // TODO Auto-generated method stub
        return null;
    }
}