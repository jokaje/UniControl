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

public class WebUiFragment extends Fragment {

    // UI Ebenen
    private View layoutWebContent;
    private View layoutWebSetup;
    private View layoutWebIntroOverlay;

    private WebView webView;
    private TextView tvPlaceholder;
    private String currentLoadedUrl = "";

    // Unser zentraler SettingsManager
    private SettingsManager settingsManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_web_ui, container, false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // SettingsManager initialisieren
        settingsManager = SettingsManager.getInstance(requireContext());

        // --- LAYER BINDING ---
        layoutWebContent = view.findViewById(R.id.layout_web_content);
        layoutWebSetup = view.findViewById(R.id.layout_web_setup);
        layoutWebIntroOverlay = view.findViewById(R.id.layout_web_intro_overlay);

        // Dynamische Farbe anwenden
        try {
            view.setBackgroundColor(Color.parseColor(settingsManager.getColorWeb()));
        } catch (Exception ignored) {}

        webView = view.findViewById(R.id.webview_web_ui);
        tvPlaceholder = view.findViewById(R.id.tv_web_ui_placeholder);

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

        String localUrl = settingsManager.getWebLocal();
        String publicUrl = settingsManager.getWebPublic();

        // Wenn beide URLs leer sind, zeigen wir das Setup an
        if (localUrl.isEmpty() && publicUrl.isEmpty()) {
            layoutWebContent.setVisibility(View.GONE);
            layoutWebSetup.setVisibility(View.VISIBLE);
            layoutWebIntroOverlay.setVisibility(View.VISIBLE);

            EditText etSetupLocal = getView().findViewById(R.id.et_setup_web_local);
            EditText etSetupPublic = getView().findViewById(R.id.et_setup_web_public);
            MaterialButton btnIntroNext = getView().findViewById(R.id.btn_web_intro_next);
            MaterialButton btnIntroSkip = getView().findViewById(R.id.btn_web_intro_skip);
            MaterialButton btnSetupSave = getView().findViewById(R.id.btn_web_setup_save);

            // Vorbefüllen falls teilweise Daten vorhanden
            if (etSetupLocal != null) etSetupLocal.setText(localUrl);
            if (etSetupPublic != null) etSetupPublic.setText(publicUrl);

            // Klick auf "Einrichten": Blase weg, Setup zeigen
            if (btnIntroNext != null) btnIntroNext.setOnClickListener(v -> layoutWebIntroOverlay.setVisibility(View.GONE));

            // Klick auf "Ausblenden": Modul deaktivieren & weiter
            if (btnIntroSkip != null) {
                btnIntroSkip.setOnClickListener(v -> {
                    settingsManager.setModuleEnabled(SettingsManager.KEY_MOD_WEB, false);
                    Toast.makeText(getContext(), getString(R.string.web_module_hidden), Toast.LENGTH_SHORT).show();

                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).refreshMenu();
                        ((MainActivity) getActivity()).goToNextOnboardingTab(R.id.nav_web);
                    }
                });
            }

            // Klick auf "Speichern": Daten speichern, WebView starten & weiter
            if (btnSetupSave != null) {
                btnSetupSave.setOnClickListener(v -> {
                    settingsManager.setWebLocal(etSetupLocal.getText().toString().trim());
                    settingsManager.setWebPublic(etSetupPublic.getText().toString().trim());

                    Toast.makeText(getContext(), getString(R.string.web_connected), Toast.LENGTH_SHORT).show();

                    layoutWebSetup.setVisibility(View.GONE);
                    layoutWebContent.setVisibility(View.VISIBLE);

                    loadAppropriateUrl(); // Jetzt wird das Dashboard geladen!

                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).goToNextOnboardingTab(R.id.nav_web);
                    }
                });
            }
        } else {
            // Normaler Modus, Web UI ist bereits eingerichtet
            layoutWebContent.setVisibility(View.VISIBLE);
            layoutWebSetup.setVisibility(View.GONE);
            layoutWebIntroOverlay.setVisibility(View.GONE);
            loadAppropriateUrl();
        }
    }

    private void loadAppropriateUrl() {
        if (getContext() == null || webView == null) return;

        String savedSsid = settingsManager.getWifiSsid();
        String localUrl = settingsManager.getWebLocal();
        String publicUrl = settingsManager.getWebPublic();
        String currentSsid = NetworkUtils.getCurrentSsid(getContext());
        String targetUrl = "";

        if (!savedSsid.isEmpty() && currentSsid != null && currentSsid.equals(savedSsid) && !localUrl.isEmpty()) {
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