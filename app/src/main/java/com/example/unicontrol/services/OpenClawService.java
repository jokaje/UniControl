package com.example.unicontrol.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.unicontrol.MainActivity;
import com.example.unicontrol.R;
import com.example.unicontrol.models.ChatMessage;
import com.example.unicontrol.network.HomeAssistantWsClient;
import com.example.unicontrol.network.OpenClawClient;
import com.example.unicontrol.utils.SettingsManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OpenClawService extends Service {

    private OpenClawClient openClawClient;
    private HomeAssistantWsClient haWsClient;
    private Gson gson = new Gson();
    private String currentStatus = "Warte auf Verbindung...";

    private static final String CHANNEL_ID_SERVICE = "EchoServiceChannel";
    private static final String CHANNEL_ID_MESSAGES = "EchoMessageChannel";
    private static final String CHANNEL_ID_HA = "HomeAssistantChannel";
    private static final int NOTIFICATION_ID_SERVICE = 100;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID_SERVICE, buildPersistentNotification(currentStatus), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID_SERVICE, buildPersistentNotification(currentStatus));
        }

        // 1. OpenClaw Client initialisieren
        openClawClient = new OpenClawClient(this);
        openClawClient.setChatListener(new OpenClawClient.ChatListener() {
            @Override
            public void onAgentTyping() {
                Intent intent = new Intent("com.example.unicontrol.ECHO_TYPING");
                intent.setPackage(getPackageName());
                sendBroadcast(intent);
            }

            @Override
            public void onMessageReceived(String text) {
                ChatMessage msg = new ChatMessage(text, false, false, null, null);
                saveMessageToHistory(msg);

                Intent intent = new Intent("com.example.unicontrol.ECHO_NEW_MESSAGE");
                intent.putExtra("text", text);
                intent.setPackage(getPackageName());
                sendBroadcast(intent);

                showPushNotification(text);
            }

            @Override
            public void onConnectionStatusChanged(String status) {
                currentStatus = status;

                Intent intent = new Intent("com.example.unicontrol.ECHO_STATUS");
                intent.putExtra("status", status);
                intent.setPackage(getPackageName());
                sendBroadcast(intent);

                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.notify(NOTIFICATION_ID_SERVICE, buildPersistentNotification(status));
                }
            }
        });

        openClawClient.connect();

        // 2. Home Assistant WebSocket initialisieren
        startHomeAssistantWebSocket();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("SEND_MESSAGE".equals(action)) {
                String text = intent.getStringExtra("text");
                String base64 = intent.getStringExtra("base64");
                String mime = intent.getStringExtra("mimeType");
                if (openClawClient != null) {
                    openClawClient.sendChatMessage(text, base64, mime);
                }
            } else if ("REQUEST_STATUS".equals(action)) {
                if (currentStatus.contains("Fehler") || currentStatus.contains("fehlgeschlagen") || currentStatus.contains("getrennt") || currentStatus.contains("Warte")) {
                    if (openClawClient != null) openClawClient.connect();
                    if (haWsClient != null) haWsClient.connect();
                }
                Intent statusIntent = new Intent("com.example.unicontrol.ECHO_STATUS");
                statusIntent.putExtra("status", currentStatus);
                statusIntent.setPackage(getPackageName());
                sendBroadcast(statusIntent);
            }
        } else {
            if (openClawClient != null) openClawClient.connect();
            if (haWsClient != null) haWsClient.connect();
        }
        return START_STICKY;
    }

    // --- Home Assistant Methoden ---
    private void startHomeAssistantWebSocket() {
        SettingsManager settings = SettingsManager.getInstance(this);
        String token = settings.getHomeToken();
        String localUrl = settings.getHomeLocal();
        String publicUrl = settings.getHomePublic();

        String activeUrl = !publicUrl.isEmpty() ? publicUrl : localUrl;

        if (!token.isEmpty() && !activeUrl.isEmpty()) {
            if (!activeUrl.startsWith("http")) activeUrl = "http://" + activeUrl;

            haWsClient = new HomeAssistantWsClient(activeUrl, token, (title, message, imageEntity) -> {
                showHomeAssistantNotification(title, message, imageEntity);
            });
            haWsClient.connect();
        }
    }

    private void showHomeAssistantNotification(String title, String message, String imageEntity) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("open_fragment", "home");
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_HA)
                .setSmallIcon(R.drawable.ic_home)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(Notification.DEFAULT_ALL);

        // Wenn eine Kamera-Entität mitgegeben wurde, laden wir das Bild
        if (imageEntity != null && !imageEntity.isEmpty() && !imageEntity.equals("none")) {
            Executors.newSingleThreadExecutor().execute(() -> {
                Bitmap bitmap = fetchHomeAssistantImage(imageEntity);
                if (bitmap != null) {
                    builder.setStyle(new NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .setSummaryText(message)); // Zeigt den Text auch an, wenn das Bild ausgeklappt ist
                } else {
                    builder.setStyle(new NotificationCompat.BigTextStyle().bigText(message));
                }
                fireNotification(builder);
            });
        } else {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(message));
            fireNotification(builder);
        }
    }

    private void fireNotification(NotificationCompat.Builder builder) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private Bitmap fetchHomeAssistantImage(String entityId) {
        SettingsManager settings = SettingsManager.getInstance(this);
        String token = settings.getHomeToken();
        String localUrl = settings.getHomeLocal();
        String publicUrl = settings.getHomePublic();
        String activeUrl = !publicUrl.isEmpty() ? publicUrl : localUrl;

        if (activeUrl.isEmpty() || token.isEmpty()) return null;
        if (!activeUrl.startsWith("http")) activeUrl = "http://" + activeUrl;

        String imageUrl = activeUrl + "/api/camera_proxy/" + entityId;

        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(imageUrl)
                    .addHeader("Authorization", "Bearer " + token)
                    .build();

            Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                InputStream inputStream = response.body().byteStream();
                return BitmapFactory.decodeStream(inputStream);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    // ------------------------------------

    private void saveMessageToHistory(ChatMessage msg) {
        SharedPreferences prefs = getSharedPreferences("chat_history_prefs", MODE_PRIVATE);
        String json = prefs.getString("chat_messages_list", "[]");
        Type type = new TypeToken<ArrayList<ChatMessage>>() {}.getType();
        List<ChatMessage> history = gson.fromJson(json, type);
        if (history == null) history = new ArrayList<>();
        history.add(msg);
        prefs.edit().putString("chat_messages_list", gson.toJson(history)).apply();
    }

    private void showPushNotification(String text) {
        SharedPreferences prefs = getSharedPreferences("chat_state", MODE_PRIVATE);
        boolean isEchoVisible = prefs.getBoolean("is_echo_visible", false);
        if (isEchoVisible) return;

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("open_fragment", "echo");
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_MESSAGES)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("OpenClaw Agent")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(Notification.DEFAULT_ALL);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private Notification buildPersistentNotification(String status) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("open_fragment", "echo");
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
                .setContentTitle("Echo Hintergrunddienst")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel serviceChannel = new NotificationChannel(
                        CHANNEL_ID_SERVICE, "Echo Status", NotificationManager.IMPORTANCE_LOW);
                serviceChannel.setShowBadge(false);
                manager.createNotificationChannel(serviceChannel);

                NotificationChannel messageChannel = new NotificationChannel(
                        CHANNEL_ID_MESSAGES, "Echo Nachrichten", NotificationManager.IMPORTANCE_HIGH);
                messageChannel.enableLights(true);
                messageChannel.setLightColor(Color.CYAN);
                manager.createNotificationChannel(messageChannel);

                NotificationChannel haChannel = new NotificationChannel(
                        CHANNEL_ID_HA, "Home Assistant Push", NotificationManager.IMPORTANCE_HIGH);
                haChannel.enableLights(true);
                haChannel.setLightColor(Color.BLUE);
                manager.createNotificationChannel(haChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (openClawClient != null) {
            openClawClient.disconnect();
        }
        if (haWsClient != null) {
            haWsClient.disconnect();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}