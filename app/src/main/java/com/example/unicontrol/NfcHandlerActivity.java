package com.example.unicontrol;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.widget.Toast;

import com.example.unicontrol.utils.CryptoUtils;
import com.example.unicontrol.utils.NetworkUtils;
import com.example.unicontrol.utils.SettingsManager;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class NfcHandlerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Verarbeitet den Scan sofort
        handleNfcIntent(getIntent());

        // Schließt die unsichtbare Geister-Activity in derselben Millisekunde wieder,
        // sodass sie den User nicht stört.
        finish();
    }

    private void handleNfcIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction()) ||
                NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction()) ||
                NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {

            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                String tagId = bytesToHex(tag.getId());
                String haTagId = extractHaTagIdFromNdef(intent);

                if (haTagId != null && !haTagId.isEmpty()) {
                    tagId = haTagId;
                    Toast.makeText(this, "Home Assistant Tag erkannt!\nSende Aktion im Hintergrund...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Roh-Tag erkannt!\nSende Aktion im Hintergrund...", Toast.LENGTH_SHORT).show();
                }

                sendTagToHomeAssistant(tagId);
            }
        }
    }

    private String extractHaTagIdFromNdef(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (rawMsgs != null) {
            for (Parcelable rawMsg : rawMsgs) {
                NdefMessage msg = (NdefMessage) rawMsg;
                for (NdefRecord record : msg.getRecords()) {
                    Uri uri = record.toUri();
                    if (uri != null && uri.toString().startsWith("https://www.home-assistant.io/tag/")) {
                        return uri.getLastPathSegment();
                    }
                }
            }
        }
        return null;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private void sendTagToHomeAssistant(String tagId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                SettingsManager settingsManager = SettingsManager.getInstance(this);

                String token = settingsManager.getHomeToken();
                if (token.isEmpty()) return;

                String savedSsid = settingsManager.getWifiSsid();
                String localUrl = settingsManager.getHomeLocal();
                String publicUrl = settingsManager.getHomePublic();
                String currentSsid = NetworkUtils.getCurrentSsid(this);

                String targetUrl = "";
                if (!savedSsid.isEmpty() && currentSsid != null && currentSsid.equals(savedSsid) && !localUrl.isEmpty()) {
                    targetUrl = localUrl;
                } else if (!publicUrl.isEmpty()) {
                    targetUrl = publicUrl;
                } else {
                    targetUrl = localUrl;
                }

                if (targetUrl.isEmpty()) return;

                if (!targetUrl.startsWith("http")) targetUrl = "http://" + targetUrl;
                if (targetUrl.endsWith("/")) targetUrl = targetUrl.substring(0, targetUrl.length() - 1);

                URL url = new URL(targetUrl + "/api/events/tag_scanned");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                CryptoUtils cryptoUtils = new CryptoUtils(this);
                String deviceId = cryptoUtils.getDeviceId();
                if (deviceId == null || deviceId.isEmpty()) deviceId = "android-app";

                String jsonBody = "{\"tag_id\": \"" + tagId + "\", \"device_id\": \"" + deviceId + "\"}";

                OutputStream os = conn.getOutputStream();
                os.write(jsonBody.getBytes("UTF-8"));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == 200 || responseCode == 201) {
                    runOnUiThread(() -> Toast.makeText(NfcHandlerActivity.this, "Aktion erfolgreich gesendet! ✅", Toast.LENGTH_SHORT).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(NfcHandlerActivity.this, "Fehler beim Senden (" + responseCode + ")", Toast.LENGTH_SHORT).show());
                }
                conn.disconnect();
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(NfcHandlerActivity.this, "NFC Netzwerkfehler", Toast.LENGTH_SHORT).show());
                Log.e("NFC", "HA API Error", e);
            }
        });
    }
}