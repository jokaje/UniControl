package com.example.unicontrol.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.unicontrol.fragments.SettingsFragment;
import com.example.unicontrol.utils.CryptoUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.UUID;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class OpenClawClient extends WebSocketListener {

    private static final String TAG = "OpenClawClient";
    private final Context context;
    private final CryptoUtils cryptoUtils;
    private final Gson gson;
    private final SharedPreferences settingsPrefs;

    private WebSocket webSocket;
    private OkHttpClient client;
    private ChatListener chatListener;

    public interface ChatListener {
        void onMessageReceived(String text);
        void onConnectionStatusChanged(String status);
        void onAgentTyping();
    }

    public void setChatListener(ChatListener listener) {
        this.chatListener = listener;
    }

    public void sendChatMessage(String text) {
        sendChatMessage(text, null, null);
    }

    // --- HIER PASSIERT DIE MAGIE FÜR DIE ANHÄNGE ---
    public void sendChatMessage(String text, String base64Content, String mimeType) {
        if (webSocket != null) {
            JsonObject params = new JsonObject();

            // Pflichtfelder für chat.send
            params.addProperty("sessionKey", "agent:main:main");
            params.addProperty("idempotencyKey", UUID.randomUUID().toString()); // ZURÜCK: Wichtig für chat.send!
            params.addProperty("message", (text != null) ? text : "");

            // Attachments Array exakt nach Vorgabe einbauen
            if (base64Content != null && mimeType != null && !base64Content.isEmpty()) {
                JsonArray attachments = new JsonArray();

                JsonObject attachment = new JsonObject();
                attachment.addProperty("mimeType", mimeType);
                attachment.addProperty("content", base64Content);

                attachments.add(attachment);

                // Das Array zwingend unter "attachments" an params anhängen
                params.add("attachments", attachments);
                Log.d(TAG, "Chat-Nachricht mit Anhang geschnürt! MimeType: " + mimeType);
            } else {
                Log.d(TAG, "Chat-Nachricht OHNE Anhang geschnürt.");
            }

            JsonObject request = new JsonObject();
            request.addProperty("type", "req");
            request.addProperty("id", UUID.randomUUID().toString());

            // ROLLE RÜCKWÄRTS: Es ist und bleibt 'chat.send'!
            request.addProperty("method", "chat.send");
            request.add("params", params);

            // Umwandeln in Text
            String jsonPayload = gson.toJson(request);

            // Kleine Vorschau im Log
            Log.d(TAG, "Sende an WebSocket: " + jsonPayload.substring(0, Math.min(250, jsonPayload.length())) + "...");

            webSocket.send(jsonPayload);
        } else {
            if (chatListener != null) chatListener.onConnectionStatusChanged("Fehler: Nicht verbunden.");
        }
    }

    public OpenClawClient(Context context) {
        this.context = context;
        this.cryptoUtils = new CryptoUtils(context);
        this.gson = new Gson();
        this.client = new OkHttpClient();
        this.settingsPrefs = context.getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String getSmartUrl() {
        String homeSsid = settingsPrefs.getString(SettingsFragment.KEY_WIFI_SSID, "").trim();
        String localUrl = settingsPrefs.getString(SettingsFragment.KEY_ECHO_LOCAL, "").trim();
        String publicUrl = settingsPrefs.getString(SettingsFragment.KEY_ECHO_PUBLIC, "").trim();

        boolean isAtHome = false;

        try {
            android.net.wifi.WifiManager wifiManager = (android.net.wifi.WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.getConnectionInfo() != null) {
                String currentSsid = wifiManager.getConnectionInfo().getSSID();
                if (currentSsid != null) {
                    currentSsid = currentSsid.replace("\"", "").trim();
                    if (!homeSsid.isEmpty() && currentSsid.equals(homeSsid)) {
                        isAtHome = true;
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Keine Berechtigung um WLAN SSID auszulesen.", e);
        }

        String finalUrl = null;
        if (isAtHome && !localUrl.isEmpty()) {
            finalUrl = localUrl;
        } else if (!publicUrl.isEmpty()) {
            finalUrl = publicUrl;
        } else if (!localUrl.isEmpty()) {
            finalUrl = localUrl;
        }

        return finalUrl != null && !finalUrl.isEmpty() ? finalUrl.trim() : null;
    }

    public void connect() {
        String url = getSmartUrl();

        if (url == null || (!url.startsWith("ws://") && !url.startsWith("wss://") && !url.startsWith("http://") && !url.startsWith("https://"))) {
            if (chatListener != null) chatListener.onConnectionStatusChanged("Fehler: Keine gültige URL (ws:// / wss://) in Einstellungen!");
            return;
        }

        if (!cryptoUtils.hasValidIdentity()) {
            if (chatListener != null) chatListener.onConnectionStatusChanged("❌ Abbruch: Keine Identität gefunden!\nBitte trage Device-ID, Public Key und Private Key manuell in den Einstellungen ein.");
            return;
        }

        if (chatListener != null) chatListener.onConnectionStatusChanged("Verbinde mit Server...");

        try {
            Request request = new Request.Builder().url(url).build();
            webSocket = client.newWebSocket(request, this);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Fehlerhaftes URL-Format", e);
        }
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "User disconnected");
        }
    }

    @Override
    public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
        if (chatListener != null) chatListener.onConnectionStatusChanged("Verbunden! Authentifiziere...");
    }

    @Override
    public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
        try {
            JsonObject json = gson.fromJson(text, JsonObject.class);
            String type = json.has("type") ? json.get("type").getAsString() : "";

            if ("event".equals(type) && json.has("event") && "connect.challenge".equals(json.get("event").getAsString())) {
                JsonObject payload = json.getAsJsonObject("payload");
                handleConnectChallenge(payload.get("nonce").getAsString());
            }
            else if ("res".equals(type)) {
                boolean ok = json.has("ok") && json.get("ok").getAsBoolean();
                if (ok) {
                    JsonObject payload = json.has("payload") ? json.getAsJsonObject("payload") : new JsonObject();

                    if (payload.has("server") || payload.has("deviceToken")) {
                        if (chatListener != null) chatListener.onConnectionStatusChanged("Authentifizierung erfolgreich! ✅");
                        if (payload.has("deviceToken")) {
                            settingsPrefs.edit().putString(SettingsFragment.KEY_OPENCLAW_DEVICE_TOKEN, payload.get("deviceToken").getAsString()).apply();
                        }
                    }
                    else if (payload.has("runId") && payload.has("status") && "started".equals(payload.get("status").getAsString())) {
                        if (chatListener != null) chatListener.onAgentTyping();
                    }
                } else {
                    JsonObject error = json.has("error") ? json.getAsJsonObject("error") : new JsonObject();
                    String errorMsg = error.has("message") ? error.get("message").getAsString() : "";
                    JsonObject details = error.has("details") ? error.getAsJsonObject("details") : new JsonObject();
                    String reason = details.has("reason") ? details.get("reason").getAsString() : "";

                    if ("device-id-mismatch".equals(reason)) {
                        settingsPrefs.edit().remove(SettingsFragment.KEY_OPENCLAW_DEVICE_TOKEN).apply();
                        if (chatListener != null) chatListener.onConnectionStatusChanged("❌ Mismatch Fehler!\nDer Token wurde zurückgesetzt.");
                    } else if ("pairing-required".equals(reason) || "device-not-approved".equals(reason) || errorMsg.contains("pair") || errorMsg.contains("approve")) {
                        String deviceId = settingsPrefs.getString(SettingsFragment.KEY_DEVICE_ID, cryptoUtils.getDeviceId());
                        if (chatListener != null) chatListener.onConnectionStatusChanged("⏳ Gerät muss gekoppelt werden!\n\nFühre auf deinem Server aus:\nopenclaw devices approve " + deviceId);
                    } else {
                        if (chatListener != null) chatListener.onConnectionStatusChanged("❌ Server-Meldung: " + errorMsg);
                    }
                }
            }
            else if ("event".equals(type) && json.has("event") && "chat".equals(json.get("event").getAsString())) {
                JsonObject payload = json.getAsJsonObject("payload");

                if (payload.has("state") && "final".equals(payload.get("state").getAsString())) {
                    if (payload.has("message")) {
                        JsonObject message = payload.getAsJsonObject("message");
                        if (message.has("content") && message.get("content").isJsonArray()) {
                            JsonArray contentArray = message.getAsJsonArray("content");
                            if (contentArray.size() > 0) {
                                JsonObject firstPart = contentArray.get(0).getAsJsonObject();
                                if (firstPart.has("text")) {
                                    String aiText = firstPart.get("text").getAsString();
                                    if (chatListener != null) chatListener.onMessageReceived(aiText);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Fehler beim Parsen der Nachricht", e);
        }
    }

    private String toBase64Url(String base64) {
        if (base64 == null) return "";
        return base64.replace("+", "-").replace("/", "_").replace("=", "");
    }

    private void handleConnectChallenge(String nonce) {
        String deviceId = cryptoUtils.getDeviceId();
        String password = settingsPrefs.getString(SettingsFragment.KEY_OPENCLAW_PASSWORD, "");
        String deviceToken = settingsPrefs.getString(SettingsFragment.KEY_OPENCLAW_DEVICE_TOKEN, "");
        if (deviceToken == null) deviceToken = "";

        long signedAt = System.currentTimeMillis();

        String clientId = "openclaw-android";
        String clientMode = "node";
        String role = "operator";
        String scopes = "operator.read,operator.write";
        String platform = "android";
        String deviceFamily = "";

        String payloadToSign = "v3|" + deviceId + "|" + clientId + "|" + clientMode + "|"
                + role + "|" + scopes + "|" + signedAt + "|" + deviceToken + "|" + nonce + "|" + platform + "|" + deviceFamily;

        String rawSignature = cryptoUtils.signMessage(payloadToSign);
        String signature = toBase64Url(rawSignature);
        String rawPublicKey = cryptoUtils.getPublicKeyBase64();
        String publicKey = toBase64Url(rawPublicKey);

        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("id", clientId);
        clientInfo.addProperty("platform", platform);
        clientInfo.addProperty("mode", clientMode);
        clientInfo.addProperty("version", "1.0.0");

        JsonObject authObject = new JsonObject();
        authObject.addProperty("password", password);
        if (!deviceToken.isEmpty()) authObject.addProperty("deviceToken", deviceToken);

        JsonObject deviceObject = new JsonObject();
        deviceObject.addProperty("id", deviceId);
        deviceObject.addProperty("publicKey", publicKey);
        deviceObject.addProperty("nonce", nonce);
        deviceObject.addProperty("signedAt", signedAt);
        deviceObject.addProperty("signature", signature);

        JsonObject params = new JsonObject();
        params.addProperty("minProtocol", 3);
        params.addProperty("maxProtocol", 3);
        params.addProperty("role", role);

        JsonArray scopesArray = new JsonArray();
        scopesArray.add("operator.read");
        scopesArray.add("operator.write");
        params.add("scopes", scopesArray);

        params.add("client", clientInfo);
        params.add("auth", authObject);
        params.add("device", deviceObject);

        JsonObject request = new JsonObject();
        request.addProperty("type", "req");
        request.addProperty("id", UUID.randomUUID().toString());
        request.addProperty("method", "connect");
        request.add("params", params);

        webSocket.send(gson.toJson(request));
    }

    @Override
    public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
        Log.e(TAG, "WebSocket Verbindungsfehler", t);
        if (chatListener != null) chatListener.onConnectionStatusChanged("Verbindung fehlgeschlagen:\n" + t.getMessage());
    }
}