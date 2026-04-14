package com.example.unicontrol.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {

    private static final String PREF_NAME = "UniControlSettings";
    private static final String KEY_OPENCLAW_URL = "openclaw_url";
    private static final String KEY_OPENCLAW_PASSWORD = "openclaw_password";
    private static final String KEY_DEVICE_TOKEN = "device_token";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setOpenClawUrl(String url) {
        prefs.edit().putString(KEY_OPENCLAW_URL, url).apply();
    }

    public String getOpenClawUrl() {
        // Standardmäßig die lokale Tailscale-URL aus deinem Briefing zurückgeben
        return prefs.getString(KEY_OPENCLAW_URL, "wss://win-5kdq59pkf7i-1.tail8d49d5.ts.net");
    }

    public void setOpenClawPassword(String password) {
        prefs.edit().putString(KEY_OPENCLAW_PASSWORD, password).apply();
    }

    public String getOpenClawPassword() {
        return prefs.getString(KEY_OPENCLAW_PASSWORD, "");
    }

    public void setDeviceToken(String token) {
        prefs.edit().putString(KEY_DEVICE_TOKEN, token).apply();
    }

    public String getDeviceToken() {
        return prefs.getString(KEY_DEVICE_TOKEN, null);
    }
}