package com.example.unicontrol.workers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import android.content.pm.ServiceInfo;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ForegroundInfo;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.unicontrol.utils.CryptoUtils;
import com.example.unicontrol.utils.NetworkUtils;
import com.example.unicontrol.utils.SettingsManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class BackupWorker extends Worker {

    private static final int NOTIFICATION_ID = 4224;
    private static final String CHANNEL_ID = "unicontrol_backup_channel";

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    private void updateNotification(String progressText) {
        Context context = getApplicationContext();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Cloud Backup",
                    NotificationManager.IMPORTANCE_LOW
            );
            manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("UniControl Backup")
                .setContentText(progressText)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();

        try {
            manager.notify(NOTIFICATION_ID, notification);
        } catch (SecurityException e) {
            Log.w("UniControlBackup", "Keine Berechtigung für Benachrichtigungen, läuft stumm weiter.");
        }
    }

    private void clearNotification() {
        Context context = getApplicationContext();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

    private String computeSha1(Context context, Uri uri) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e("UniControlBackup", "Checksum fehlgeschlagen für " + uri.toString(), e);
            return null;
        }
    }

    private int tryUploadReturnCode(Context context, String uploadUrlStr, String apiKey, String deviceId, UploadItem item) {
        HttpURLConnection conn = null;
        try {
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();

            String fileName = new File(item.path).getName();
            if (fileName == null || fileName.isEmpty()) fileName = "upload.jpg";
            fileName = fileName.replace("\"", "");

            String mimeType = URLConnection.guessContentTypeFromName(fileName);
            if (mimeType == null) mimeType = "application/octet-stream";

            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            String isoDate = isoFormat.format(new Date(item.dateAddedMs));

            URL url = new URL(uploadUrlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setUseCaches(false);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setChunkedStreamingMode(0);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);

            OutputStream outputStream = conn.getOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true);

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"deviceAssetId\"\r\n\r\n");
            writer.append(item.deviceAssetId).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"deviceId\"\r\n\r\n");
            writer.append(deviceId).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"fileCreatedAt\"\r\n\r\n");
            writer.append(isoDate).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"fileModifiedAt\"\r\n\r\n");
            writer.append(isoDate).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"isFavorite\"\r\n\r\n");
            writer.append("false").append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"assetData\"; filename=\"").append(fileName).append("\"\r\n");
            writer.append("Content-Type: ").append(mimeType).append("\r\n\r\n");
            writer.flush();

            InputStream inputStream = context.getContentResolver().openInputStream(item.contentUri);
            if (inputStream != null) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
                inputStream.close();
            }

            writer.append("\r\n");
            writer.append("--").append(boundary).append("--\r\n");
            writer.close();

            int responseCode = conn.getResponseCode();

            if (responseCode >= 400) {
                InputStream es = conn.getErrorStream();
                if (es != null) {
                    byte[] errBuf = new byte[1024];
                    while (es.read(errBuf) != -1) {}
                    es.close();
                }
            }

            return responseCode;

        } catch (Exception e) {
            Log.e("UniControlBackup", "Worker Upload Fehler für: " + item.path, e);
            return -1;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private List<UploadItem> filterExistingAssets(String baseUrl, String apiKey, List<UploadItem> allItems) {
        List<UploadItem> itemsToUpload = new ArrayList<>();
        int chunkSize = 250;

        for (int i = 0; i < allItems.size(); i += chunkSize) {
            int end = Math.min(allItems.size(), i + chunkSize);
            List<UploadItem> chunk = allItems.subList(i, end);

            updateNotification("Server-Abgleich... (" + end + " von " + allItems.size() + ")");

            try {
                JsonObject root = new JsonObject();
                JsonArray assetsArray = new JsonArray();
                for (UploadItem item : chunk) {
                    JsonObject assetObj = new JsonObject();
                    assetObj.addProperty("id", item.deviceAssetId);
                    if (item.checksum != null && !item.checksum.isEmpty()) {
                        assetObj.addProperty("checksum", item.checksum);
                    }
                    assetsArray.add(assetObj);
                }
                root.add("assets", assetsArray);
                String jsonBody = root.toString();

                URL url = new URL(baseUrl + "/api/asset/bulk-upload-check");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("x-api-key", apiKey);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                OutputStream os = conn.getOutputStream();
                os.write(jsonBody.getBytes("UTF-8"));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();

                if (responseCode == 404 || responseCode == 405) {
                    url = new URL(baseUrl + "/api/assets/bulk-upload-check");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("x-api-key", apiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);

                    os = conn.getOutputStream();
                    os.write(jsonBody.getBytes("UTF-8"));
                    os.flush();
                    os.close();
                    responseCode = conn.getResponseCode();
                }

                if (responseCode == 200 || responseCode == 201) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    JsonObject responseObj = JsonParser.parseString(response.toString()).getAsJsonObject();
                    if (responseObj.has("results")) {
                        JsonArray results = responseObj.getAsJsonArray("results");
                        HashSet<String> acceptedIds = new HashSet<>();

                        for (JsonElement element : results) {
                            JsonObject resObj = element.getAsJsonObject();
                            String action = resObj.get("action").getAsString();
                            if ("accept".equalsIgnoreCase(action)) {
                                acceptedIds.add(resObj.get("id").getAsString());
                            }
                        }

                        for (UploadItem item : chunk) {
                            if (acceptedIds.contains(item.deviceAssetId)) {
                                itemsToUpload.add(item);
                            }
                        }
                    } else {
                        itemsToUpload.addAll(chunk);
                    }
                } else {
                    Log.e("UniControlBackup", "Pre-check fehlgeschlagen (" + responseCode + "). Fallback.");
                    itemsToUpload.addAll(chunk);
                }
            } catch (Exception e) {
                Log.e("UniControlBackup", "Ausnahme beim Pre-check.", e);
                itemsToUpload.addAll(chunk);
            }
        }

        return itemsToUpload;
    }

    @NonNull
    private ForegroundInfo createForegroundInfo() {
        Context context = getApplicationContext();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Cloud Backup",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Sichert im Hintergrund die neuesten Fotos.");
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("UniControl Backup")
                .setContentText("Fotos werden im Hintergrund gesichert...")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            return new ForegroundInfo(NOTIFICATION_ID, notification);
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("UniControlBackup", "Hintergrund-Backup Job gestartet!");

        // WICHTIG: Hiermit teilen wir Android mit, dass dieser Worker im Vordergrund läuft
        try {
            setForegroundAsync(createForegroundInfo()).get();
        } catch (Exception e) {
            Log.e("UniControlBackup", "Fehler beim Setzen als Foreground Service", e);
        }

        Context context = getApplicationContext();
        SettingsManager settingsManager = SettingsManager.getInstance(context);

        boolean isManual = getInputData().getBoolean("is_manual", false);

        if (!isManual && !settingsManager.isAutoBackupEnabled()) {
            return Result.success();
        }

        Set<String> bucketIds = settingsManager.getBackupAlbums();
        if (bucketIds.isEmpty()) {
            if (isManual) showToast("Fehler: Keine Alben für das Backup ausgewählt!");
            return Result.success();
        }

        try {
            updateNotification("Bereite Backup vor...");

            String savedSsid = settingsManager.getWifiSsid();
            String localUrl = settingsManager.getFotosLocal();
            String publicUrl = settingsManager.getFotosPublic();
            String apiKey = settingsManager.getFotosApiKey();

            CryptoUtils cryptoUtils = new CryptoUtils(context);
            String deviceId = cryptoUtils.getDeviceId();
            if (deviceId == null || deviceId.isEmpty()) deviceId = UUID.randomUUID().toString();

            String currentSsid = NetworkUtils.getCurrentSsid(context);

            Set<String> blacklist = settingsManager.getPrefs().getStringSet("blacklisted_local_assets", new HashSet<>());

            String targetUrl = "";
            if (!savedSsid.isEmpty() && currentSsid != null && currentSsid.equals(savedSsid) && !localUrl.isEmpty()) {
                targetUrl = formatUrl(localUrl, true);
            } else if (!publicUrl.isEmpty()) {
                targetUrl = formatUrl(publicUrl, false);
            } else if (!localUrl.isEmpty()) {
                targetUrl = formatUrl(localUrl, true);
            }

            if (targetUrl.isEmpty() || apiKey.isEmpty()) {
                Log.e("UniControlBackup", "Kein Ziel-Server erreichbar.");
                if (isManual) showToast("Fehler: Keine Server-Verbindung möglich!");
                return Result.retry();
            }

            final String cleanBaseUrl = targetUrl.endsWith("/") ? targetUrl.substring(0, targetUrl.length() - 1) : targetUrl;

            List<UploadItem> itemsToUpload = new ArrayList<>();
            Uri[] uris = { MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI };
            String[] projection = { MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.BUCKET_ID };

            StringBuilder selection = new StringBuilder(MediaStore.MediaColumns.BUCKET_ID + " IN (");
            String[] selectionArgs = new String[bucketIds.size()];
            int index = 0;
            for (String id : bucketIds) {
                selection.append("?");
                if (index < bucketIds.size() - 1) selection.append(",");
                selectionArgs[index] = id;
                index++;
            }
            selection.append(")");

            for (Uri uri : uris) {
                try (Cursor cursor = context.getContentResolver().query(uri, projection, selection.toString(), selectionArgs, "DATE_ADDED ASC")) {
                    if (cursor != null) {
                        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                        int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                        int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);

                        while (cursor.moveToNext()) {
                            String _id = cursor.getString(idColumn);

                            if (blacklist.contains(_id)) {
                                continue;
                            }

                            UploadItem item = new UploadItem();
                            item.deviceAssetId = _id;
                            item.path = cursor.getString(dataColumn);
                            item.dateAddedMs = cursor.getLong(dateColumn) * 1000L;
                            item.contentUri = ContentUris.withAppendedId(uri, cursor.getLong(idColumn));
                            itemsToUpload.add(item);
                        }
                    }
                } catch (Exception e) {
                    Log.e("UniControlBackup", "Fehler beim Scannen der Medien.", e);
                }
            }

            if (itemsToUpload.isEmpty()) {
                if (isManual) showToast("Keine lokalen Bilder in den ausgewählten Alben gefunden!");
                return Result.success();
            }

            Log.d("UniControlBackup", "Lokal gefunden: " + itemsToUpload.size() + " Bilder/Videos. Berechne Prüfsummen...");

            int totalItems = itemsToUpload.size();
            for (int i = 0; i < totalItems; i++) {
                UploadItem item = itemsToUpload.get(i);
                if (i % 25 == 0 || i == totalItems - 1) updateNotification("Analysiere Dateien... (" + (i + 1) + " von " + totalItems + ")");
                item.checksum = computeSha1(context, item.contentUri);
            }

            List<UploadItem> filteredItems = filterExistingAssets(cleanBaseUrl, apiKey, itemsToUpload);

            Log.d("UniControlBackup", "Vorab-Prüfung abgeschlossen. Neue Dateien zum Upload: " + filteredItems.size());

            int totalNewCount = filteredItems.size();
            int newUploadsCount = 0;
            int skippedCount = totalItems - totalNewCount;

            for (int i = 0; i < totalNewCount; i++) {
                UploadItem item = filteredItems.get(i);

                if (i % 5 == 0 || i == totalNewCount - 1) {
                    updateNotification("Sichere Bild " + (i + 1) + " von " + totalNewCount + "...");
                }

                int code = tryUploadReturnCode(context, cleanBaseUrl + "/api/assets", apiKey, deviceId, item);

                if (code == 200 || code == 201) {
                    newUploadsCount++;
                } else if (code == 409) {
                    skippedCount++;
                }
            }

            Log.d("UniControlBackup", "Hintergrund-Backup Job erfolgreich abgeschlossen!");

            if (isManual) {
                showToast("Backup beendet!\n" + newUploadsCount + " neu hochgeladen\n" + skippedCount + " übersprungen 🚀");
            }

            return Result.success();

        } catch (Exception e) {
            Log.e("UniControlBackup", "Worker abgebrochen", e);
            if (isManual) showToast("Backup abgebrochen. Bitte WLAN prüfen.");
            return Result.failure();
        } finally {
            clearNotification();

            if (!isManual) {
                scheduleNextBackup(context, settingsManager);
            }
        }
    }

    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
        });
    }

    private void scheduleNextBackup(Context context, SettingsManager settingsManager) {
        if (!settingsManager.isAutoBackupEnabled()) return;

        int hour = settingsManager.getAutoBackupHour();
        int minute = settingsManager.getAutoBackupMinute();

        Calendar currentDate = Calendar.getInstance();
        Calendar dueDate = Calendar.getInstance();
        dueDate.set(Calendar.HOUR_OF_DAY, hour);
        dueDate.set(Calendar.MINUTE, minute);
        dueDate.set(Calendar.SECOND, 0);

        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24);
        }

        long timeDiff = dueDate.getTimeInMillis() - currentDate.getTimeInMillis();

        androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build();

        OneTimeWorkRequest backupRequest = new OneTimeWorkRequest.Builder(BackupWorker.class)
                .setInitialDelay(timeDiff, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                "ImmichAutoBackup",
                ExistingWorkPolicy.REPLACE,
                backupRequest);
    }

    private String formatUrl(String url, boolean isLocal) {
        String formatted = url.trim();
        if (formatted.isEmpty()) return "";
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = (isLocal ? "http://" : "https://") + formatted;
        }
        return formatted;
    }

    private static class UploadItem {
        String deviceAssetId;
        String path;
        long dateAddedMs;
        Uri contentUri;
        String checksum;
    }
}