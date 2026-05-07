package com.example.unicontrol.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class HomeAssistantWsClient {

    private static final String TAG = "HA_WebSocket";
    private WebSocket webSocket;
    private final String url;
    private final String token;
    private final HaMessageListener listener;
    private int messageId = 1;

    // NEU: Ein Schalter, damit er nicht neu verbindet, wenn wir die App absichtlich schließen
    private boolean isIntentionallyClosed = false;

    public interface HaMessageListener {
        void onNotificationReceived(String title, String message, String imageEntity);
    }

    public HomeAssistantWsClient(String url, String token, HaMessageListener listener) {
        this.url = url.replace("http://", "ws://").replace("https://", "wss://") + "/api/websocket";
        this.token = token;
        this.listener = listener;
    }

    public void connect() {
        isIntentionallyClosed = false;

        // NEU: Ping-Intervall von 30 Sekunden eingebaut, hält die Leitung "wach"
        OkHttpClient client = new OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder().url(url).build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "Verbunden mit Home Assistant");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JSONObject json = new JSONObject(text);
                    String type = json.optString("type");

                    if ("auth_required".equals(type)) {
                        JSONObject authMsg = new JSONObject();
                        authMsg.put("type", "auth");
                        authMsg.put("access_token", token);
                        webSocket.send(authMsg.toString());
                    }
                    else if ("auth_ok".equals(type)) {
                        Log.d(TAG, "HA Auth erfolgreich! Abonniere Events...");
                        JSONObject subMsg = new JSONObject();
                        subMsg.put("id", messageId++);
                        subMsg.put("type", "subscribe_events");
                        subMsg.put("event_type", "unicontrol_notify");
                        webSocket.send(subMsg.toString());
                    }
                    else if ("event".equals(type)) {
                        JSONObject eventData = json.getJSONObject("event").getJSONObject("data");
                        String title = eventData.optString("title", "Home Assistant");
                        String message = eventData.optString("message", "Neue Benachrichtigung");
                        String imageEntity = eventData.optString("image_entity", null);

                        if (listener != null) {
                            listener.onNotificationReceived(title, message, imageEntity);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Fehler beim Parsen der HA Nachricht", e);
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "Verbindung geschlossen");
                triggerReconnect(); // NEU
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "Verbindungsfehler: " + t.getMessage());
                triggerReconnect(); // NEU
            }
        });
    }

    // NEU: Die Wiederverbindungs-Logik
    private void triggerReconnect() {
        if (isIntentionallyClosed) return; // Nicht verbinden, wenn App zerstört wird

        Log.d(TAG, "Versuche Reconnect in 5 Sekunden...");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "Führe Reconnect durch...");
            connect();
        }, 5000); // 5 Sekunden warten, um Spam zu vermeiden
    }

    public void disconnect() {
        isIntentionallyClosed = true;
        if (webSocket != null) {
            webSocket.close(1000, "Service destroyed");
        }
    }
}