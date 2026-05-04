package com.example.unicontrol.fragments;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.unicontrol.MainActivity;
import com.example.unicontrol.R;
import com.example.unicontrol.utils.NetworkUtils;
import com.example.unicontrol.utils.SettingsManager;
import com.google.android.material.button.MaterialButton;

public class HomeFragment extends Fragment {

    // UI Ebenen
    private View layoutHomeContent;
    private View layoutHomeSetup;
    private View layoutHomeIntroOverlay;

    private WebView webView;
    private TextView tvPlaceholder;
    private String currentLoadedUrl = "";

    // Unser neuer zentraler SettingsManager
    private SettingsManager settingsManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // SettingsManager initialisieren
        settingsManager = SettingsManager.getInstance(requireContext());

        // --- LAYER BINDING ---
        layoutHomeContent = view.findViewById(R.id.layout_home_content);
        layoutHomeSetup = view.findViewById(R.id.layout_home_setup);
        layoutHomeIntroOverlay = view.findViewById(R.id.layout_home_intro_overlay);

        // Dynamische Farbe anwenden (sauber über den Manager geladen)
        try {
            view.setBackgroundColor(Color.parseColor(settingsManager.getColorHome()));
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
            checkOnboarding();
        } else {
            checkOnboarding();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (webView != null) {
            if (hidden) webView.onPause();
            else {
                webView.onResume();
                checkOnboarding();
            }
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
            checkOnboarding();
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

    // --- Onboarding und Weiterleitungs-Logik ---
    private void checkOnboarding() {
        if (getContext() == null || getView() == null) return;

        // Anstatt SharedPreferences direkt anzusprechen, nutzen wir den Manager
        String localUrl = settingsManager.getHomeLocal();
        String publicUrl = settingsManager.getHomePublic();

        // Wenn beide URLs leer sind, gehen wir davon aus, dass Home Assistant noch nicht eingerichtet ist
        if (localUrl.isEmpty() && publicUrl.isEmpty()) {
            layoutHomeContent.setVisibility(View.GONE);
            layoutHomeSetup.setVisibility(View.VISIBLE);
            layoutHomeIntroOverlay.setVisibility(View.VISIBLE);

            EditText etSetupSsid = getView().findViewById(R.id.et_setup_home_ssid);
            EditText etSetupLocal = getView().findViewById(R.id.et_setup_home_local);
            EditText etSetupPublic = getView().findViewById(R.id.et_setup_home_public);
            EditText etSetupToken = getView().findViewById(R.id.et_setup_home_token);
            MaterialButton btnIntroNext = getView().findViewById(R.id.btn_home_intro_next);
            MaterialButton btnIntroSkip = getView().findViewById(R.id.btn_home_intro_skip);
            MaterialButton btnSetupSave = getView().findViewById(R.id.btn_home_setup_save);

            // Vorbefüllen falls teilweise Daten vorhanden
            if (etSetupSsid != null) etSetupSsid.setText(settingsManager.getWifiSsid());
            if (etSetupLocal != null) etSetupLocal.setText(localUrl);
            if (etSetupPublic != null) etSetupPublic.setText(publicUrl);
            if (etSetupToken != null) etSetupToken.setText(settingsManager.getHomeToken());

            // Klick auf "Einrichten": Blase weg, Setup zeigen
            if (btnIntroNext != null) btnIntroNext.setOnClickListener(v -> layoutHomeIntroOverlay.setVisibility(View.GONE));

            // Klick auf "Ausblenden": Modul deaktivieren & weiter
            if (btnIntroSkip != null) {
                btnIntroSkip.setOnClickListener(v -> {
                    settingsManager.setModuleEnabled(SettingsManager.KEY_MOD_HOME, false);
                    Toast.makeText(getContext(), "Home-Modul ausgeblendet.", Toast.LENGTH_SHORT).show();

                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).refreshMenu();
                        ((MainActivity) getActivity()).goToNextOnboardingTab(R.id.nav_home);
                    }
                });
            }

            // Klick auf "Speichern": Daten speichern, WebView starten & weiter
            if (btnSetupSave != null) {
                btnSetupSave.setOnClickListener(v -> {
                    settingsManager.setWifiSsid(etSetupSsid.getText().toString().trim());
                    settingsManager.setHomeLocal(etSetupLocal.getText().toString().trim());
                    settingsManager.setHomePublic(etSetupPublic.getText().toString().trim());
                    settingsManager.setHomeToken(etSetupToken.getText().toString().trim());

                    Toast.makeText(getContext(), "Home Assistant verbunden! ✅", Toast.LENGTH_SHORT).show();

                    layoutHomeSetup.setVisibility(View.GONE);
                    layoutHomeContent.setVisibility(View.VISIBLE);

                    loadAppropriateUrl(); // Jetzt wird das Dashboard geladen!

                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).goToNextOnboardingTab(R.id.nav_home);
                    }
                });
            }
        } else {
            // Normaler Modus, Home Assistant ist bereits eingerichtet
            layoutHomeContent.setVisibility(View.VISIBLE);
            layoutHomeSetup.setVisibility(View.GONE);
            layoutHomeIntroOverlay.setVisibility(View.GONE);
            loadAppropriateUrl();
        }
    }

    private void loadAppropriateUrl() {
        if (getContext() == null || webView == null) return;

        String savedSsid = settingsManager.getWifiSsid();
        String localUrl = settingsManager.getHomeLocal();
        String publicUrl = settingsManager.getHomePublic();
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