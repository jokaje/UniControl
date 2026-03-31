package com.example.unicontrol.workers;

import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.unicontrol.fragments.SettingsFragment;
import com.example.unicontrol.utils.NetworkUtils;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
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

public class BackupWorker extends Worker {

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    private boolean tryUpload(Context context, String uploadUrlStr, String apiKey, String deviceId, UploadItem item) {
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
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setUseCaches(false);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setChunkedStreamingMode(0);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

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
            if (inputStream == null) return false;

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
            inputStream.close();

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

            return (responseCode == 200 || responseCode == 201 || responseCode == 409);

        } catch (Exception e) {
            Log.e("UniControlBackup", "Worker Upload Fehler für: " + item.path, e);
            return false;
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("UniControlBackup", "Hintergrund-Backup Job gestartet!");

        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);

        // 1. Prüfen, ob Auto-Backup noch aktiv ist
        if (!prefs.getBoolean(SettingsFragment.KEY_AUTO_BACKUP_ENABLED, false)) {
            return Result.success();
        }

        Set<String> bucketIds = prefs.getStringSet(SettingsFragment.KEY_BACKUP_ALBUMS, new HashSet<>());
        if (bucketIds.isEmpty()) {
            return Result.success();
        }

        // 2. Netzwerk und URLs checken
        String savedSsid = prefs.getString(SettingsFragment.KEY_WIFI_SSID, "");
        String localUrl = prefs.getString(SettingsFragment.KEY_FOTOS_LOCAL, "");
        String publicUrl = prefs.getString(SettingsFragment.KEY_FOTOS_PUBLIC, "");
        String apiKey = prefs.getString(SettingsFragment.KEY_FOTOS_API_KEY, "");
        String deviceId = prefs.getString(SettingsFragment.KEY_DEVICE_ID, UUID.randomUUID().toString());
        String currentSsid = NetworkUtils.getCurrentSsid(context);

        String targetUrl = "";
        if (!savedSsid.isEmpty() && currentSsid.equals(savedSsid) && !localUrl.isEmpty()) {
            targetUrl = formatUrl(localUrl, true);
        } else if (!publicUrl.isEmpty()) {
            targetUrl = formatUrl(publicUrl, false);
        }

        if (targetUrl.isEmpty() || apiKey.isEmpty()) {
            Log.e("UniControlBackup", "Kein Ziel-Server erreichbar.");
            return Result.retry();
        }

        final String cleanBaseUrl = targetUrl.endsWith("/") ? targetUrl.substring(0, targetUrl.length() - 1) : targetUrl;

        // 3. Bilder zusammensuchen
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
                        UploadItem item = new UploadItem();
                        item.deviceAssetId = cursor.getString(idColumn);
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

        if (itemsToUpload.isEmpty()) return Result.success();

        // 4. Den eigentlichen Upload abarbeiten
        for (UploadItem item : itemsToUpload) {
            tryUpload(context, cleanBaseUrl + "/api/assets", apiKey, deviceId, item);
        }

        Log.d("UniControlBackup", "Hintergrund-Backup Job erfolgreich abgeschlossen!");

        // 5. WICHTIG: Den Wecker für exakt den nächsten Tag zur selben Uhrzeit stellen!
        scheduleNextBackup(context, prefs);

        return Result.success();
    }

    // --- NEU: DIESE METHODE PROGRAMMIERT DEN WECKER FÜR DEN NÄCHSTEN TAG ---
    private void scheduleNextBackup(Context context, SharedPreferences prefs) {
        if (!prefs.getBoolean(SettingsFragment.KEY_AUTO_BACKUP_ENABLED, false)) return;

        int hour = prefs.getInt(SettingsFragment.KEY_AUTO_BACKUP_HOUR, 2);
        int minute = prefs.getInt(SettingsFragment.KEY_AUTO_BACKUP_MINUTE, 0);

        Calendar currentDate = Calendar.getInstance();
        Calendar dueDate = Calendar.getInstance();
        dueDate.set(Calendar.HOUR_OF_DAY, hour);
        dueDate.set(Calendar.MINUTE, minute);
        dueDate.set(Calendar.SECOND, 0);

        // Wir springen exakt 24 Stunden in die Zukunft
        dueDate.add(Calendar.HOUR_OF_DAY, 24);

        long timeDiff = dueDate.getTimeInMillis() - currentDate.getTimeInMillis();

        androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build();

        // Der Timer für morgen
        OneTimeWorkRequest backupRequest = new OneTimeWorkRequest.Builder(BackupWorker.class)
                .setInitialDelay(timeDiff, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                "ImmichAutoBackup",
                ExistingWorkPolicy.REPLACE, // Falls noch einer läuft, überschreiben wir ihn
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
    }
}