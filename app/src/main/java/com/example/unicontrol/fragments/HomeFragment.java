package com.example.unicontrol.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.unicontrol.R;
import com.example.unicontrol.utils.NetworkUtils;

public class HomeFragment extends Fragment {

    private WebView webView;
    private TextView tvPlaceholder;
    private String currentLoadedUrl = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Dynamische Farbe anwenden!
        SharedPreferences prefs = requireContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
        try {
            view.setBackgroundColor(Color.parseColor(prefs.getString(SettingsFragment.KEY_COLOR_HOME, "#B2D3C2")));
        } catch (Exception ignored) {}

        webView = view.findViewById(R.id.webview_home);
        tvPlaceholder = view.findViewById(R.id.tv_home_placeholder);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient());

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
            currentLoadedUrl = savedInstanceState.getString("saved_url", "");
        } else {
            loadAppropriateUrl();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (webView != null) {
            if (hidden) webView.onPause();
            else { webView.onResume(); loadAppropriateUrl(); }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (webView != null && !isHidden()) {
            webView.onResume();
            loadAppropriateUrl();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
            outState.putString("saved_url", currentLoadedUrl);
        }
    }

    private void loadAppropriateUrl() {
        if (getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
        String savedSsid = prefs.getString(SettingsFragment.KEY_WIFI_SSID, "");
        String localUrl = prefs.getString(SettingsFragment.KEY_HOME_LOCAL, "");
        String publicUrl = prefs.getString(SettingsFragment.KEY_HOME_PUBLIC, "");
        String currentSsid = NetworkUtils.getCurrentSsid(getContext());
        String targetUrl = "";

        if (!savedSsid.isEmpty() && currentSsid.equals(savedSsid) && !localUrl.isEmpty()) {
            targetUrl = formatUrl(localUrl, true);
        } else if (!publicUrl.isEmpty()) {
            targetUrl = formatUrl(publicUrl, false);
        } else if (!localUrl.isEmpty()) {
            targetUrl = formatUrl(localUrl, true);
        }

        if (!targetUrl.isEmpty()) {
            webView.setVisibility(View.VISIBLE);
            tvPlaceholder.setVisibility(View.GONE);
            if (!targetUrl.equals(currentLoadedUrl)) {
                webView.loadUrl(targetUrl);
                currentLoadedUrl = targetUrl;
            }
        } else {
            webView.setVisibility(View.GONE);
            tvPlaceholder.setVisibility(View.VISIBLE);
            currentLoadedUrl = "";
        }
    }

    private String formatUrl(String url, boolean isLocal) {
        String formatted = url.trim();
        if (formatted.isEmpty()) return "";
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = (isLocal ? "http://" : "https://") + formatted;
        }
        return formatted;
    }
}