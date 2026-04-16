package com.example.unicontrol.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.unicontrol.MainActivity;
import com.example.unicontrol.R;
import com.example.unicontrol.models.ChatMessage;
import com.example.unicontrol.network.OpenClawClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class OpenClawService extends Service {

    private OpenClawClient openClawClient;
    private Gson gson = new Gson();
    private String currentStatus = "Warte auf Verbindung...";

    private static final String CHANNEL_ID_SERVICE = "EchoServiceChannel";
    private static final String CHANNEL_ID_MESSAGES = "EchoMessageChannel";
    private static final int NOTIFICATION_ID_SERVICE = 100;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();

        // Android 14 (API 34+) erfordert zwingend die Angabe des Service-Typs beim Starten!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID_SERVICE, buildPersistentNotification(currentStatus), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID_SERVICE, buildPersistentNotification(currentStatus));
        }

        openClawClient = new OpenClawClient(this);
        openClawClient.setChatListener(new OpenClawClient.ChatListener() {
            @Override
            public void onAgentTyping() {
                Intent intent = new Intent("com.example.unicontrol.ECHO_TYPING");
                intent.setPackage(getPackageName()); // Wichtig für Sicherheit!
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
                currentStatus = status; // Status für spätere Anfragen merken!

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
                // Das Fragment hat sich geöffnet und fragt nach dem Status!
                Intent statusIntent = new Intent("com.example.unicontrol.ECHO_STATUS");
                statusIntent.putExtra("status", currentStatus);
                statusIntent.setPackage(getPackageName());
                sendBroadcast(statusIntent);
            }
        }
        return START_STICKY;
    }

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
        if (isEchoVisible) return; // Nicht nerven, wenn die App sowieso offen ist

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("open_fragment", "echo");
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP); // Verhindert App-Neustart!

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
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (openClawClient != null) {
            openClawClient.disconnect();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}