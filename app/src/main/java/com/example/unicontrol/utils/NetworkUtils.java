package com.example.unicontrol.utils;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

public class NetworkUtils {

    // Gibt den Namen (SSID) des aktuell verbundenen WLANs zurück
    public static String getCurrentSsid(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo info = wifiManager.getConnectionInfo();
            if (info != null && info.getSSID() != null) {
                String ssid = info.getSSID();

                // Android gibt die SSID oft in Anführungszeichen zurück (z.B. "MeinWlan").
                // Diese entfernen wir hier für einen sauberen Vergleich.
                if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid = ssid.substring(1, ssid.length() - 1);
                }

                // Falls die Berechtigung fehlt oder GPS aus ist, gibt Android dies zurück
                if (ssid.equals("<unknown ssid>")) {
                    return "";
                }

                return ssid;
            }
        }
        return "";
    }
}