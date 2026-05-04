package com.example.unicontrol.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/**
 * Single Source of Truth für alle SharedPreferences der App.
 */
public class SettingsManager {

    public static final String PREF_NAME = "UniControlPrefs";

    // --- KEYS ---
    public static final String KEY_WIFI_SSID = "wifi_ssid";

    // Modul-Sichtbarkeit & Reihenfolge
    public static final String KEY_MOD_HOME = "mod_home_enabled";
    public static final String KEY_MOD_FOTOS = "mod_fotos_enabled";
    public static final String KEY_MOD_ECHO = "mod_echo_enabled";
    public static final String KEY_MOD_WEB = "mod_web_enabled";
    public static final String KEY_MOD_APPS = "mod_apps_enabled";
    public static final String KEY_MOD_ORDER = "mod_order";

    // URLs & Zugangsdaten
    public static final String KEY_ECHO_LOCAL = "echo_local";
    public static final String KEY_ECHO_PUBLIC = "echo_public";
    public static final String KEY_WEB_LOCAL = "web_local";
    public static final String KEY_WEB_PUBLIC = "web_public";
    public static final String KEY_APPS_LOCAL = "apps_local";
    public static final String KEY_APPS_PUBLIC = "apps_public";
    public static final String KEY_HOME_LOCAL = "home_local";
    public static final String KEY_HOME_PUBLIC = "home_public";
    public static final String KEY_HOME_TOKEN = "home_token";
    public static final String KEY_FOTOS_LOCAL = "fotos_local";
    public static final String KEY_FOTOS_PUBLIC = "fotos_public";
    public static final String KEY_FOTOS_API_KEY = "fotos_api_key";

    // OpenClaw
    public static final String KEY_OPENCLAW_URL = "openclaw_url";
    public static final String KEY_OPENCLAW_PASSWORD = "openclaw_password";
    public static final String KEY_DEVICE_TOKEN = "device_token";

    // Farben
    public static final String KEY_COLOR_ECHO = "color_echo";
    public static final String KEY_COLOR_WEB = "color_web";
    public static final String KEY_COLOR_HOME = "color_home";
    public static final String KEY_COLOR_FOTOS = "color_fotos";
    public static final String KEY_COLOR_APPS = "color_apps";
    public static final String KEY_COLOR_SETTINGS = "color_settings";

    // Hintergrund-Dienste
    public static final String KEY_LOCATION_TRACKING_ENABLED = "location_tracking_enabled";
    public static final String KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled";
    public static final String KEY_AUTO_BACKUP_HOUR = "auto_backup_hour";
    public static final String KEY_AUTO_BACKUP_MINUTE = "auto_backup_minute";
    public static final String KEY_BACKUP_ALBUMS = "backup_albums_set";

    private static SettingsManager instance;
    private final SharedPreferences prefs;

    private SettingsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsManager(context);
        }
        return instance;
    }

    // --- WLAN ---
    public String getWifiSsid() { return prefs.getString(KEY_WIFI_SSID, ""); }
    public void setWifiSsid(String ssid) { prefs.edit().putString(KEY_WIFI_SSID, ssid).apply(); }

    // --- MODULE & REIHENFOLGE ---
    public boolean isModuleEnabled(String moduleKey) { return prefs.getBoolean(moduleKey, true); }
    public void setModuleEnabled(String moduleKey, boolean enabled) { prefs.edit().putBoolean(moduleKey, enabled).apply(); }

    public String getModuleOrder() {
        String defaultOrder = KEY_MOD_HOME + "," + KEY_MOD_FOTOS + "," + KEY_MOD_ECHO + "," + KEY_MOD_APPS + "," + KEY_MOD_WEB;
        return prefs.getString(KEY_MOD_ORDER, defaultOrder);
    }
    public void setModuleOrder(String order) { prefs.edit().putString(KEY_MOD_ORDER, order).apply(); }

    // --- URLS & TOKENS ---
    public String getEchoLocal() { return prefs.getString(KEY_ECHO_LOCAL, ""); }
    public void setEchoLocal(String url) { prefs.edit().putString(KEY_ECHO_LOCAL, url).apply(); }
    public String getEchoPublic() { return prefs.getString(KEY_ECHO_PUBLIC, ""); }
    public void setEchoPublic(String url) { prefs.edit().putString(KEY_ECHO_PUBLIC, url).apply(); }

    public String getWebLocal() { return prefs.getString(KEY_WEB_LOCAL, ""); }
    public void setWebLocal(String url) { prefs.edit().putString(KEY_WEB_LOCAL, url).apply(); }
    public String getWebPublic() { return prefs.getString(KEY_WEB_PUBLIC, ""); }
    public void setWebPublic(String url) { prefs.edit().putString(KEY_WEB_PUBLIC, url).apply(); }

    public String getAppsLocal() { return prefs.getString(KEY_APPS_LOCAL, "192.168.86.46:8767"); }
    public void setAppsLocal(String url) { prefs.edit().putString(KEY_APPS_LOCAL, url).apply(); }
    public String getAppsPublic() { return prefs.getString(KEY_APPS_PUBLIC, "coldnet.dedyn.io:8767"); }
    public void setAppsPublic(String url) { prefs.edit().putString(KEY_APPS_PUBLIC, url).apply(); }

    public String getHomeLocal() { return prefs.getString(KEY_HOME_LOCAL, ""); }
    public void setHomeLocal(String url) { prefs.edit().putString(KEY_HOME_LOCAL, url).apply(); }
    public String getHomePublic() { return prefs.getString(KEY_HOME_PUBLIC, ""); }
    public void setHomePublic(String url) { prefs.edit().putString(KEY_HOME_PUBLIC, url).apply(); }
    public String getHomeToken() { return prefs.getString(KEY_HOME_TOKEN, ""); }
    public void setHomeToken(String token) { prefs.edit().putString(KEY_HOME_TOKEN, token).apply(); }

    public String getFotosLocal() { return prefs.getString(KEY_FOTOS_LOCAL, ""); }
    public void setFotosLocal(String url) { prefs.edit().putString(KEY_FOTOS_LOCAL, url).apply(); }
    public String getFotosPublic() { return prefs.getString(KEY_FOTOS_PUBLIC, ""); }
    public void setFotosPublic(String url) { prefs.edit().putString(KEY_FOTOS_PUBLIC, url).apply(); }
    public String getFotosApiKey() { return prefs.getString(KEY_FOTOS_API_KEY, ""); }
    public void setFotosApiKey(String key) { prefs.edit().putString(KEY_FOTOS_API_KEY, key).apply(); }

    // --- OPENCLAW ---
    public String getOpenClawUrl() { return prefs.getString(KEY_OPENCLAW_URL, "wss://win-5kdq59pkf7i-1.tail8d49d5.ts.net"); }
    public void setOpenClawUrl(String url) { prefs.edit().putString(KEY_OPENCLAW_URL, url).apply(); }
    public String getOpenClawPassword() { return prefs.getString(KEY_OPENCLAW_PASSWORD, ""); }
    public void setOpenClawPassword(String pwd) { prefs.edit().putString(KEY_OPENCLAW_PASSWORD, pwd).apply(); }
    public String getDeviceToken() { return prefs.getString(KEY_DEVICE_TOKEN, null); }
    public void setDeviceToken(String token) { prefs.edit().putString(KEY_DEVICE_TOKEN, token).apply(); }

    // --- FARBEN ---
    public String getColorHome() { return prefs.getString(KEY_COLOR_HOME, "#B2D3C2"); }
    public void setColorHome(String hex) { prefs.edit().putString(KEY_COLOR_HOME, hex).apply(); }
    public String getColorFotos() { return prefs.getString(KEY_COLOR_FOTOS, "#F49AC2"); }
    public void setColorFotos(String hex) { prefs.edit().putString(KEY_COLOR_FOTOS, hex).apply(); }
    public String getColorEcho() { return prefs.getString(KEY_COLOR_ECHO, "#AEC6CF"); }
    public void setColorEcho(String hex) { prefs.edit().putString(KEY_COLOR_ECHO, hex).apply(); }
    public String getColorWeb() { return prefs.getString(KEY_COLOR_WEB, "#FDFD96"); }
    public void setColorWeb(String hex) { prefs.edit().putString(KEY_COLOR_WEB, hex).apply(); }
    public String getColorApps() { return prefs.getString(KEY_COLOR_APPS, "#D3B8E8"); }
    public void setColorApps(String hex) { prefs.edit().putString(KEY_COLOR_APPS, hex).apply(); }
    public String getColorSettings() { return prefs.getString(KEY_COLOR_SETTINGS, "#EAEAEA"); }
    public void setColorSettings(String hex) { prefs.edit().putString(KEY_COLOR_SETTINGS, hex).apply(); }

    // --- BACKUP & TRACKING ---
    public boolean isLocationTrackingEnabled() { return prefs.getBoolean(KEY_LOCATION_TRACKING_ENABLED, false); }
    public void setLocationTrackingEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_LOCATION_TRACKING_ENABLED, enabled).apply(); }

    public boolean isAutoBackupEnabled() { return prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false); }
    public void setAutoBackupEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled).apply(); }

    public int getAutoBackupHour() { return prefs.getInt(KEY_AUTO_BACKUP_HOUR, 2); }
    public int getAutoBackupMinute() { return prefs.getInt(KEY_AUTO_BACKUP_MINUTE, 0); }
    public void setAutoBackupTime(int hour, int minute) {
        prefs.edit().putInt(KEY_AUTO_BACKUP_HOUR, hour).putInt(KEY_AUTO_BACKUP_MINUTE, minute).apply();
    }

    public Set<String> getBackupAlbums() { return prefs.getStringSet(KEY_BACKUP_ALBUMS, new HashSet<>()); }
    public void setBackupAlbums(Set<String> albums) { prefs.edit().putStringSet(KEY_BACKUP_ALBUMS, albums).apply(); }

    // Für direkten SharedPreferences Zugriff, falls noch alte Fragmente ihn brauchen
    public SharedPreferences getPrefs() { return prefs; }
}