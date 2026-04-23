package com.example.unicontrol.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.unicontrol.R;

public class AppsFragment extends Fragment {

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;

    // Die Keys aus den Settings
    public static final String KEY_APPS_LOCAL = "apps_local";
    public static final String KEY_APPS_PUBLIC = "apps_public";
    public static final String KEY_COLOR_APPS = "color_apps";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_apps, container, false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        webView = view.findViewById(R.id.webview_apps);
        progressBar = view.findViewById(R.id.progressBar);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh); // NEU: Swipe-Layout binden

        // Hintergrundfarbe setzen
        SharedPreferences prefs = requireContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
        String hexColor = prefs.getString(KEY_COLOR_APPS, "#D3B8E8"); // Flieder als Standard
        try {
            view.setBackgroundColor(Color.parseColor(hexColor));
            webView.setBackgroundColor(Color.parseColor(hexColor));
        } catch (Exception ignored) {}

        // NEU: Logik für das Herunterziehen (Pull-to-Refresh)
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (webView != null) {
                webView.reload(); // Lade die aktuelle Seite der WebView neu
            }
        });

        // WebView Settings (Sehr wichtig für moderne Web-Apps wie dein Backend)
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);

        // Client für Seiten-Navigation
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        // Client für Ladebalken
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                    // NEU: Wenn der Ladevorgang fertig ist, verstecken wir das runde Lade-Symbol des SwipeRefreshLayouts
                    if (swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                }
            }
        });

        // URL ermitteln und laden
        String targetUrl = determineUrl(prefs);
        if (targetUrl != null && !targetUrl.isEmpty()) {
            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                targetUrl = "http://" + targetUrl;
            }
            webView.loadUrl(targetUrl);
        } else {
            Toast.makeText(getContext(), "Bitte konfiguriere zuerst die App-Entwicklung URLs in den Einstellungen.", Toast.LENGTH_LONG).show();
            // Falls fehlerhaft beendet, Animation abbrechen
            if (swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }

    private String determineUrl(SharedPreferences prefs) {
        String localUrl = prefs.getString(KEY_APPS_LOCAL, "192.168.86.46:8767");
        String publicUrl = prefs.getString(KEY_APPS_PUBLIC, "coldnet.dedyn.io:8767");
        String homeSsid = prefs.getString(SettingsFragment.KEY_WIFI_SSID, "");

        if (isAtHome(homeSsid)) {
            return localUrl;
        } else {
            return publicUrl;
        }
    }

    private boolean isAtHome(String targetSsid) {
        if (targetSsid == null || targetSsid.isEmpty()) return false;
        if (getContext() == null) return false;

        ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                WifiManager wifiManager = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    if (wifiInfo != null && wifiInfo.getSSID() != null) {
                        String currentSsid = wifiInfo.getSSID().replace("\"", "");
                        return currentSsid.equals(targetSsid.replace("\"", ""));
                    }
                }
            }
        }
        return false;
    }

    // Damit man im WebView zurückgehen kann, ohne das Fragment zu schließen
    public boolean canGoBack() {
        return webView != null && webView.canGoBack();
    }

    public void goBack() {
        if (webView != null) webView.goBack();
    }
}