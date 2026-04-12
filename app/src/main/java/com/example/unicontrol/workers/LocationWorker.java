package com.example.unicontrol.workers;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.unicontrol.fragments.SettingsFragment;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LocationWorker extends Worker {

    // Wir speichern den Token später in den Settings unter diesem Key
    public static final String KEY_HOME_TOKEN = "home_assistant_token";

    public LocationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);

        String publicUrl = prefs.getString(SettingsFragment.KEY_HOME_PUBLIC, "");
        String localUrl = prefs.getString(SettingsFragment.KEY_HOME_LOCAL, "");
        String token = prefs.getString(KEY_HOME_TOKEN, "");
        String deviceId = prefs.getString(SettingsFragment.KEY_DEVICE_ID, "unknown_device");

        // Wenn wir keinen Token haben, können wir uns nicht bei Home Assistant anmelden
        if (token.isEmpty()) {
            return Result.failure();
        }

        // Wir versuchen immer die Public URL zu nehmen, da du im Hintergrund meistens unterwegs bist
        String targetUrl = publicUrl.isEmpty() ? localUrl : publicUrl;
        if (targetUrl.isEmpty()) return Result.failure();

        final String cleanBaseUrl = targetUrl.endsWith("/") ? targetUrl.substring(0, targetUrl.length() - 1) : targetUrl;

        // Prüfen, ob wir überhaupt GPS-Rechte haben
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("UniControlLocation", "Keine GPS-Rechte vorhanden.");
            return Result.failure();
        }

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return Result.failure();

        try {
            // Wir holen uns die letzte bekannte GPS-Position des Handys
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (location != null) {
                double lat = location.getLatitude();
                double lon = location.getLongitude();
                float accuracy = location.getAccuracy();

                // Generiere einen einzigartigen, bereinigten Gerätenamen, z.B. "unicontrol_pixel_7_a1b2"
                String safeModelName = Build.MODEL.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
                String shortId = deviceId.length() >= 4 ? deviceId.substring(0, 4) : "1234";
                String devId = "unicontrol_" + safeModelName + "_" + shortId;

                sendLocationToHomeAssistant(cleanBaseUrl, token, devId, lat, lon, accuracy);
            }
            return Result.success();

        } catch (SecurityException e) {
            Log.e("UniControlLocation", "Sicherheitsausnahme beim GPS-Abruf", e);
            return Result.failure();
        }
    }

    private void sendLocationToHomeAssistant(String baseUrl, String token, String devId, double lat, double lon, float accuracy) {
        HttpURLConnection conn = null;
        try {
            // FIX: Wir nutzen jetzt den offiziellen Device Tracker Service von Home Assistant!
            URL url = new URL(baseUrl + "/api/services/device_tracker/see");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // FIX: Dieses JSON lässt Home Assistant die Zone (Home/Work/Unterwegs) selbst anhand der GPS-Daten berechnen
            String jsonBody = "{"
                    + "\"dev_id\": \"" + devId + "\", "
                    + "\"gps\": [" + lat + ", " + lon + "], "
                    + "\"gps_accuracy\": " + accuracy
                    + "}";

            OutputStream os = conn.getOutputStream();
            os.write(jsonBody.getBytes("UTF-8"));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == 200 || responseCode == 201) {
                Log.d("UniControlLocation", "Standort erfolgreich an HA gesendet: " + devId);
            } else {
                Log.e("UniControlLocation", "Fehler beim Senden des Standorts: " + responseCode);
            }

        } catch (Exception e) {
            Log.e("UniControlLocation", "Verbindungsfehler zu Home Assistant", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}