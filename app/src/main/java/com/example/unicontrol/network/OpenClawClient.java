package com.example.unicontrol.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.unicontrol.utils.CryptoUtils;
import com.example.unicontrol.utils.SettingsManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    private final SettingsManager settingsManager;

    private WebSocket webSocket;
    private OkHttpClient client;
    private ChatListener chatListener;

    // Flags und Handler für Auto-Reconnect
    private boolean isConnecting = false;
    private boolean manualDisconnect = false;
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());

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

    public void sendChatMessage(String text, String base64Content, String mimeType) {
        if (webSocket != null) {

            // Size Guard - Schutz vor zu großen Base64 Strings (Videos/Audio)
            if (base64Content != null && base64Content.length() > 10_000_000) {
                Log.e(TAG, "Abbruch: Base64 String ist zu lang (" + base64Content.length() + " Zeichen)");
                if (chatListener != null) {
                    chatListener.onConnectionStatusChanged("❌ Anhang zu groß (max ~7.5MB). Bitte eine kleinere Datei wählen oder komprimieren.");
                }
                return;
            }

            JsonObject params = new JsonObject();

            // Pflichtfelder für chat.send
            params.addProperty("sessionKey", "agent:main:main");
            params.addProperty("idempotencyKey", UUID.randomUUID().toString());
            params.addProperty("message", (text != null) ? text : "");

            // Attachments Array exakt nach Vorgabe einbauen
            if (base64Content != null && mimeType != null && !base64Content.isEmpty()) {
                JsonArray attachments = new JsonArray();

                JsonObject attachment = new JsonObject();
                attachment.addProperty("mimeType", mimeType);
                attachment.addProperty("content", base64Content);

                // Optionales fileName-Feld hinzufügen!
                String ext = mimeType.contains("/") ? mimeType.split("/")[1] : "bin";
                attachment.addProperty("fileName", "upload_" + System.currentTimeMillis() + "." + ext);

                attachments.add(attachment);

                params.add("attachments", attachments);
                Log.d(TAG, "Chat-Nachricht mit Anhang geschnürt! MimeType: " + mimeType);
            } else {
                Log.d(TAG, "Chat-Nachricht OHNE Anhang geschnürt.");
            }

            JsonObject request = new JsonObject();
            request.addProperty("type", "req");
            request.addProperty("id", UUID.randomUUID().toString());
            request.addProperty("method", "chat.send");
            request.add("params", params);

            String jsonPayload = gson.toJson(request);
            Log.d(TAG, "Sende an WebSocket: " + jsonPayload.substring(0, Math.min(250, jsonPayload.length())) + "...");

            boolean success = webSocket.send(jsonPayload);
            if (!success) {
                Log.e(TAG, "WebSocket.send() gab 'false' zurück. Nachricht wurde nicht versendet!");
                if (chatListener != null) {
                    chatListener.onConnectionStatusChanged("❌ Senden fehlgeschlagen. Datei zu groß für WebSocket oder Verbindung getrennt.");
                }
            }
        } else {
            if (chatListener != null) chatListener.onConnectionStatusChanged("Fehler: Nicht verbunden.");
        }
    }

    public OpenClawClient(Context context) {
        this.context = context;
        this.cryptoUtils = new CryptoUtils(context);
        this.gson = new Gson();
        this.settingsManager = SettingsManager.getInstance(context);

        // Ping-Intervall hinzugefügt (Keep-Alive für den Hintergrund, verhindert Timeout)
        this.client = new OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
    }

    private String getSmartUrl() {
        String homeSsid = settingsManager.getWifiSsid().trim();
        String localUrl = settingsManager.getEchoLocal().trim();
        String publicUrl = settingsManager.getEchoPublic().trim();

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
        if (isConnecting) return;
        manualDisconnect = false;
        isConnecting = true;

        String url = getSmartUrl();

        if (url == null || (!url.startsWith("ws://") && !url.startsWith("wss://") && !url.startsWith("http://") && !url.startsWith("https://"))) {
            if (chatListener != null) chatListener.onConnectionStatusChanged("Fehler: Keine gültige URL (ws:// / wss://) in Einstellungen!");
            isConnecting = false;
            return;
        }

        if (!cryptoUtils.hasValidIdentity()) {
            if (chatListener != null) chatListener.onConnectionStatusChanged("❌ Abbruch: Keine Identität gefunden!\nBitte trage Device-ID, Public Key und Private Key manuell in den Einstellungen ein.");
            isConnecting = false;
            return;
        }

        if (chatListener != null) chatListener.onConnectionStatusChanged("Verbinde mit Server...");

        try {
            Request request = new Request.Builder().url(url).build();
            webSocket = client.newWebSocket(request, this);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Fehlerhaftes URL-Format", e);
            isConnecting = false;
            scheduleReconnect();
        }
    }

    public void disconnect() {
        manualDisconnect = true;
        reconnectHandler.removeCallbacksAndMessages(null);
        if (webSocket != null) {
            webSocket.close(1000, "User disconnected");
            webSocket = null;
        }
    }

    @Override
    public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
        isConnecting = false;
        reconnectHandler.removeCallbacksAndMessages(null);
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
                            settingsManager.setDeviceToken(payload.get("deviceToken").getAsString());
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
                        settingsManager.setDeviceToken(""); // Reset Device Token
                        if (chatListener != null) chatListener.onConnectionStatusChanged("❌ Mismatch Fehler!\nDer Token wurde zurückgesetzt.");
                    } else if ("pairing-required".equals(reason) || "device-not-approved".equals(reason) || errorMsg.contains("pair") || errorMsg.contains("approve")) {
                        String deviceId = cryptoUtils.getDeviceId();
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
        String password = settingsManager.getOpenClawPassword();
        String deviceToken = settingsManager.getDeviceToken();
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
        isConnecting = false;
        if (chatListener != null) chatListener.onConnectionStatusChanged("Verbindung fehlgeschlagen:\n" + t.getMessage());
        scheduleReconnect();
    }

    @Override
    public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
        isConnecting = false;
        if (chatListener != null) chatListener.onConnectionStatusChanged("Verbindung getrennt.");
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (manualDisconnect) return;
        reconnectHandler.removeCallbacksAndMessages(null);
        reconnectHandler.postDelayed(() -> {
            if (!manualDisconnect) {
                if (chatListener != null) chatListener.onConnectionStatusChanged("Versuche Reconnect...");
                connect();
            }
        }, 5000); // 5 Sekunden warten, dann neuer Versuch (schont den Akku)
    }
}