package com.example.unicontrol.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
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
import com.example.unicontrol.utils.SettingsManager;

public class AppsFragment extends Fragment {

    // --- FALLBACKS: Wichtig für MainActivity und SettingsFragment ---
    public static final String KEY_APPS_LOCAL = "apps_local";
    public static final String KEY_APPS_PUBLIC = "apps_public";
    public static final String KEY_COLOR_APPS = "color_apps";
    // ----------------------------------------------------------------

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;

    // Unser zentraler SettingsManager
    private SettingsManager settingsManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_apps, container, false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        settingsManager = SettingsManager.getInstance(requireContext());

        webView = view.findViewById(R.id.webview_apps);
        progressBar = view.findViewById(R.id.progressBar);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);

        // Hintergrundfarbe setzen
        String hexColor = settingsManager.getColorApps();
        try {
            view.setBackgroundColor(Color.parseColor(hexColor));
            webView.setBackgroundColor(Color.parseColor(hexColor));
        } catch (Exception ignored) {}

        // Logik für das Herunterziehen (Pull-to-Refresh)
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (webView != null) {
                webView.reload(); // Lade die aktuelle Seite der WebView neu
            }
        });

        // WebView Settings
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
                    if (swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                }
            }
        });

        // URL ermitteln und laden
        String targetUrl = determineUrl();
        if (targetUrl != null && !targetUrl.isEmpty()) {
            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                targetUrl = "http://" + targetUrl;
            }
            webView.loadUrl(targetUrl);
        } else {
            Toast.makeText(getContext(), "Bitte konfiguriere zuerst die App-Entwicklung URLs in den Einstellungen.", Toast.LENGTH_LONG).show();
            if (swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }

    private String determineUrl() {
        String localUrl = settingsManager.getAppsLocal();
        String publicUrl = settingsManager.getAppsPublic();
        String homeSsid = settingsManager.getWifiSsid();

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