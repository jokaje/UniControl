package com.example.unicontrol;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.unicontrol.fragments.EchoFragment;
import com.example.unicontrol.fragments.FotosFragment;
import com.example.unicontrol.fragments.HomeFragment;
import com.example.unicontrol.fragments.SettingsFragment;
import com.example.unicontrol.fragments.WebUiFragment;
import com.example.unicontrol.utils.NetworkUtils;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private int currentColor;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private Fragment echoFragment;
    private Fragment webUiFragment;
    private Fragment homeFragment;
    private Fragment fotosFragment;
    private Fragment settingsFragment;
    private final FragmentManager fm = getSupportFragmentManager();
    private Fragment activeFragment;

    private ObjectAnimator uploadAnimator;

    private boolean isManualBackupRunning = false;
    private boolean isAutoBackupRunning = false;

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_main);
        requestLocationPermission();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, MODE_PRIVATE);
        boolean modHome = prefs.getBoolean(SettingsFragment.KEY_MOD_HOME, true);
        boolean modFotos = prefs.getBoolean(SettingsFragment.KEY_MOD_FOTOS, true);
        boolean modEcho = prefs.getBoolean(SettingsFragment.KEY_MOD_ECHO, true);
        boolean modWeb = prefs.getBoolean(SettingsFragment.KEY_MOD_WEB, true);

        if (savedInstanceState == null) {
            echoFragment = new EchoFragment();
            webUiFragment = new WebUiFragment();
            homeFragment = new HomeFragment();
            fotosFragment = new FotosFragment();
            settingsFragment = new SettingsFragment();

            fm.beginTransaction().add(R.id.fragment_container, settingsFragment, "5").hide(settingsFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, fotosFragment, "4").hide(fotosFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, webUiFragment, "3").hide(webUiFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, echoFragment, "1").hide(echoFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, homeFragment, "2").hide(homeFragment).commit();

            // Intelligenter Start: Wähle das erste Modul, das aktiviert ist!
            int startId = R.id.nav_settings;
            Fragment startFrag = settingsFragment;
            currentColor = getDynamicColor(SettingsFragment.KEY_COLOR_SETTINGS, "#EAEAEA");

            if (modHome) { startId = R.id.nav_home; startFrag = homeFragment; currentColor = getDynamicColor(SettingsFragment.KEY_COLOR_HOME, "#B2D3C2"); }
            else if (modFotos) { startId = R.id.nav_fotos; startFrag = fotosFragment; currentColor = getDynamicColor(SettingsFragment.KEY_COLOR_FOTOS, "#F49AC2"); }
            else if (modEcho) { startId = R.id.nav_echo; startFrag = echoFragment; currentColor = getDynamicColor(SettingsFragment.KEY_COLOR_ECHO, "#AEC6CF"); }
            else if (modWeb) { startId = R.id.nav_web; startFrag = webUiFragment; currentColor = getDynamicColor(SettingsFragment.KEY_COLOR_WEB, "#FDFD96"); }

            fm.beginTransaction().show(startFrag).commit();
            activeFragment = startFrag;

            // Menü updaten bevor wir was auswählen
            refreshMenu();
            bottomNav.setSelectedItemId(startId);

        } else {
            echoFragment = fm.findFragmentByTag("1");
            homeFragment = fm.findFragmentByTag("2");
            webUiFragment = fm.findFragmentByTag("3");
            fotosFragment = fm.findFragmentByTag("4");
            settingsFragment = fm.findFragmentByTag("5");

            if (echoFragment != null && !echoFragment.isHidden()) activeFragment = echoFragment;
            else if (webUiFragment != null && !webUiFragment.isHidden()) activeFragment = webUiFragment;
            else if (fotosFragment != null && !fotosFragment.isHidden()) activeFragment = fotosFragment;
            else if (settingsFragment != null && !settingsFragment.isHidden()) activeFragment = settingsFragment;
            else activeFragment = homeFragment;

            currentColor = getDynamicColor(SettingsFragment.KEY_COLOR_SETTINGS, "#EAEAEA"); // Fallback
            if (activeFragment == homeFragment) currentColor = getDynamicColor(SettingsFragment.KEY_COLOR_HOME, "#B2D3C2");
            else if (activeFragment == fotosFragment) currentColor = getDynamicColor(SettingsFragment.KEY_COLOR_FOTOS, "#F49AC2");
            else if (activeFragment == echoFragment) currentColor = getDynamicColor(SettingsFragment.KEY_COLOR_ECHO, "#AEC6CF");
            else if (activeFragment == webUiFragment) currentColor = getDynamicColor(SettingsFragment.KEY_COLOR_WEB, "#FDFD96");

            refreshMenu();
        }

        bottomNav.setBackgroundColor(currentColor);
        bottomNav.setBackgroundTintList(null);
        updateBottomNavColors(bottomNav, currentColor);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            int targetColor = currentColor;
            Fragment newFragment = activeFragment;

            if (itemId == R.id.nav_echo) {
                newFragment = echoFragment;
                targetColor = getDynamicColor(SettingsFragment.KEY_COLOR_ECHO, "#AEC6CF");
            } else if (itemId == R.id.nav_web) {
                newFragment = webUiFragment;
                targetColor = getDynamicColor(SettingsFragment.KEY_COLOR_WEB, "#FDFD96");
            } else if (itemId == R.id.nav_home) {
                newFragment = homeFragment;
                targetColor = getDynamicColor(SettingsFragment.KEY_COLOR_HOME, "#B2D3C2");
            } else if (itemId == R.id.nav_fotos) {
                newFragment = fotosFragment;
                targetColor = getDynamicColor(SettingsFragment.KEY_COLOR_FOTOS, "#F49AC2");
            } else if (itemId == R.id.nav_settings) {
                newFragment = settingsFragment;
                targetColor = getDynamicColor(SettingsFragment.KEY_COLOR_SETTINGS, "#EAEAEA");
            }

            if (newFragment != null && newFragment != activeFragment) {
                fm.beginTransaction().hide(activeFragment).show(newFragment).commit();
                animateMenuColor(bottomNav, currentColor, targetColor);
                currentColor = targetColor;
                activeFragment = newFragment;
            }
            return true;
        });

        View splashOverlay = findViewById(R.id.splash_overlay);
        if (savedInstanceState == null) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                splashOverlay.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .withEndAction(() -> splashOverlay.setVisibility(View.GONE))
                        .start();
            }, 2000);
        } else {
            splashOverlay.setVisibility(View.GONE);
        }

        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("ImmichManualBackup")
                .observe(this, workInfos -> {
                    isManualBackupRunning = false;
                    for (WorkInfo workInfo : workInfos) {
                        if (workInfo.getState() == WorkInfo.State.RUNNING) {
                            isManualBackupRunning = true;
                            break;
                        }
                    }
                    updateAnimationState();
                });

        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("ImmichAutoBackup")
                .observe(this, workInfos -> {
                    isAutoBackupRunning = false;
                    for (WorkInfo workInfo : workInfos) {
                        if (workInfo.getState() == WorkInfo.State.RUNNING) {
                            isAutoBackupRunning = true;
                            break;
                        }
                    }
                    updateAnimationState();
                });

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null) {
            Intent nfcIntent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            pendingIntent = PendingIntent.getActivity(this, 0, nfcIntent, flags);
        }

        handleNfcIntent(getIntent());
        checkNavigationIntent(getIntent());
    }

    // --- NEU: Dynamische Anpassung der Menüleiste ---
    public void refreshMenu() {
        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, MODE_PRIVATE);
        boolean modHome = prefs.getBoolean(SettingsFragment.KEY_MOD_HOME, true);
        boolean modFotos = prefs.getBoolean(SettingsFragment.KEY_MOD_FOTOS, true);
        boolean modEcho = prefs.getBoolean(SettingsFragment.KEY_MOD_ECHO, true);
        boolean modWeb = prefs.getBoolean(SettingsFragment.KEY_MOD_WEB, true);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            // Erstmal alles leeren und standardmäßig füllen (falls wir Module wieder einschalten)
            bottomNav.getMenu().clear();
            bottomNav.inflateMenu(R.menu.bottom_nav_menu);

            // Nicht gewollte Module aus der Leiste werfen
            if (!modHome) bottomNav.getMenu().removeItem(R.id.nav_home);
            if (!modFotos) bottomNav.getMenu().removeItem(R.id.nav_fotos);
            if (!modEcho) bottomNav.getMenu().removeItem(R.id.nav_echo);
            if (!modWeb) bottomNav.getMenu().removeItem(R.id.nav_web);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null && pendingIntent != null) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNfcIntent(intent);
        checkNavigationIntent(intent);
    }

    private void checkNavigationIntent(Intent intent) {
        if (intent != null) {
            String targetFragment = intent.getStringExtra("open_fragment");
            if ("echo".equals(targetFragment)) {
                BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
                if (bottomNav != null && bottomNav.getMenu().findItem(R.id.nav_echo) != null) {
                    bottomNav.setSelectedItemId(R.id.nav_echo);
                }
                intent.removeExtra("open_fragment");
            }
        }
    }

    // --- NEU: Der Lotse für die Onboarding-Tour ---
    public void goToNextOnboardingTab(int currentNavId) {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav == null) return;

        // Die ideale Tour-Reihenfolge durch die App
        int[] order = {R.id.nav_home, R.id.nav_fotos, R.id.nav_echo, R.id.nav_web, R.id.nav_settings};

        int currentIndex = -1;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == currentNavId) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex != -1) {
            // Suche den nächsten AKTIVEN Tab
            for (int i = currentIndex + 1; i < order.length; i++) {
                int nextId = order[i];
                if (bottomNav.getMenu().findItem(nextId) != null) {
                    bottomNav.setSelectedItemId(nextId);
                    return; // Nächster Tab gefunden und gewechselt!
                }
            }
        }
        // Fallback: Wenn wir am Ende sind (Settings) oder nichts finden -> Home
        if (bottomNav.getMenu().findItem(R.id.nav_home) != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }
    // ------------------------------------------

    private void handleNfcIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction()) ||
                NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction()) ||
                NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {

            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
                String writeId = prefs.getString("nfc_write_mode_id", null);

                if (writeId != null) {
                    writeNfcTag(tag, writeId);
                    prefs.edit().remove("nfc_write_mode_id").apply();
                    return;
                }

                String tagId = bytesToHex(tag.getId());
                String haTagId = extractHaTagIdFromNdef(intent);

                if (haTagId != null && !haTagId.isEmpty()) {
                    tagId = haTagId;
                    Toast.makeText(this, "HA-Tag erkannt!\nID: " + tagId + "\nSende an Home Assistant...", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Roh-Tag erkannt!\nHardware-ID: " + tagId + "\nSende an Home Assistant...", Toast.LENGTH_LONG).show();
                }

                sendTagToHomeAssistant(tagId);
            }
        }
    }

    private void writeNfcTag(Tag tag, String tagId) {
        try {
            NdefMessage ndefMessage = createNdefMessage(tagId);
            Ndef ndef = Ndef.get(tag);

            if (ndef == null) {
                NdefFormatable formatable = NdefFormatable.get(tag);
                if (formatable != null) {
                    formatable.connect();
                    formatable.format(ndefMessage);
                    formatable.close();
                    runOnUiThread(() -> Toast.makeText(this, "NFC-Tag formatiert & beschrieben! ✅\nNeue ID: " + tagId, Toast.LENGTH_LONG).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Tag unterstützt kein NDEF Format.", Toast.LENGTH_LONG).show());
                }
                return;
            }

            ndef.connect();
            if (!ndef.isWritable()) {
                runOnUiThread(() -> Toast.makeText(this, "Fehler: NFC-Tag ist schreibgeschützt!", Toast.LENGTH_LONG).show());
                ndef.close();
                return;
            }

            ndef.writeNdefMessage(ndefMessage);
            ndef.close();
            runOnUiThread(() -> Toast.makeText(this, "NFC-Tag erfolgreich beschrieben! ✅\nNeue ID: " + tagId, Toast.LENGTH_LONG).show());

        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Fehler beim Beschreiben des Tags.", Toast.LENGTH_LONG).show());
            Log.e("NFC_WRITE", "Error writing tag", e);
        }
    }

    private NdefMessage createNdefMessage(String tagId) {
        String url = "https://www.home-assistant.io/tag/" + tagId;
        NdefRecord uriRecord = NdefRecord.createUri(url);
        NdefRecord aarRecord = NdefRecord.createApplicationRecord(getPackageName());
        return new NdefMessage(new NdefRecord[]{uriRecord, aarRecord});
    }

    private String extractHaTagIdFromNdef(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (rawMsgs != null) {
            for (Parcelable rawMsg : rawMsgs) {
                NdefMessage msg = (NdefMessage) rawMsg;
                for (NdefRecord record : msg.getRecords()) {
                    Uri uri = record.toUri();
                    if (uri != null && uri.toString().startsWith("https://www.home-assistant.io/tag/")) {
                        return uri.getLastPathSegment();
                    }
                }
            }
        }
        return null;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private void sendTagToHomeAssistant(String tagId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
                String token = prefs.getString(SettingsFragment.KEY_HOME_TOKEN, "");
                if (token.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Fehler: Kein Home Assistant Token hinterlegt!", Toast.LENGTH_LONG).show());
                    return;
                }

                String savedSsid = prefs.getString(SettingsFragment.KEY_WIFI_SSID, "");
                String localUrl = prefs.getString(SettingsFragment.KEY_HOME_LOCAL, "");
                String publicUrl = prefs.getString(SettingsFragment.KEY_HOME_PUBLIC, "");
                String currentSsid = NetworkUtils.getCurrentSsid(this);

                String targetUrl = "";
                if (!savedSsid.isEmpty() && currentSsid != null && currentSsid.equals(savedSsid) && !localUrl.isEmpty()) {
                    targetUrl = localUrl;
                } else if (!publicUrl.isEmpty()) {
                    targetUrl = publicUrl;
                } else {
                    targetUrl = localUrl;
                }

                if (targetUrl.isEmpty()) return;

                if (!targetUrl.startsWith("http")) targetUrl = "http://" + targetUrl;
                if (targetUrl.endsWith("/")) targetUrl = targetUrl.substring(0, targetUrl.length() - 1);

                URL url = new URL(targetUrl + "/api/events/tag_scanned");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String deviceId = prefs.getString(SettingsFragment.KEY_DEVICE_ID, "android-app");
                String jsonBody = "{\"tag_id\": \"" + tagId + "\", \"device_id\": \"" + deviceId + "\"}";

                OutputStream os = conn.getOutputStream();
                os.write(jsonBody.getBytes("UTF-8"));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == 200 || responseCode == 201) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "NFC Event an Home Assistant gesendet! ✅", Toast.LENGTH_SHORT).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Fehler beim NFC-Senden (" + responseCode + ")", Toast.LENGTH_SHORT).show());
                }
                conn.disconnect();
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "NFC Netzwerkfehler", Toast.LENGTH_SHORT).show());
                Log.e("NFC", "HA API Error", e);
            }
        });
    }

    private void updateBottomNavColors(BottomNavigationView bottomNav, int bgColor) {
        boolean isDark = ColorUtils.calculateLuminance(bgColor) < 0.5;

        int checkedColor = isDark ? Color.WHITE : Color.parseColor("#333333");
        int uncheckedColor = isDark ? Color.argb(150, 255, 255, 255) : Color.argb(120, 51, 51, 51);

        ColorStateList iconColorStates = new ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_checked},
                        new int[]{android.R.attr.state_checked}
                },
                new int[]{
                        uncheckedColor,
                        checkedColor
                }
        );
        bottomNav.setItemIconTintList(iconColorStates);
        bottomNav.setItemTextColor(iconColorStates);
    }

    private void updateAnimationState() {
        setUploadAnimation(isManualBackupRunning || isAutoBackupRunning);
    }

    private ImageView findIconView(View view) {
        if (view instanceof ImageView) {
            return (ImageView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                ImageView result = findIconView(vg.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    public void setUploadAnimation(boolean isUploading) {
        runOnUiThread(() -> {
            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            if (bottomNav == null) return;

            View fotosTab = bottomNav.findViewById(R.id.nav_fotos);
            if (fotosTab != null) {
                ImageView icon = findIconView(fotosTab);

                if (icon != null) {
                    icon.setPivotX(icon.getWidth() / 2f);
                    icon.setPivotY(icon.getHeight() / 2f);

                    if (isUploading) {
                        if (uploadAnimator == null || uploadAnimator.getTarget() != icon) {
                            if (uploadAnimator != null) uploadAnimator.cancel();
                            uploadAnimator = ObjectAnimator.ofFloat(icon, "rotation", 0f, 360f);
                            uploadAnimator.setDuration(1500);
                            uploadAnimator.setRepeatCount(ValueAnimator.INFINITE);
                            uploadAnimator.setInterpolator(new LinearInterpolator());
                        }
                        if (!uploadAnimator.isRunning()) uploadAnimator.start();
                    } else {
                        if (uploadAnimator != null) {
                            uploadAnimator.cancel();
                            icon.animate().rotation(0f).setDuration(300).start();
                        }
                    }
                }
            }
        });
    }

    private int getDynamicColor(String key, String defaultHex) {
        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, MODE_PRIVATE);
        String hex = prefs.getString(key, defaultHex);

        if (hex != null && !hex.startsWith("#")) {
            hex = "#" + hex;
        }

        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return Color.parseColor(defaultHex);
        }
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void animateMenuColor(BottomNavigationView bottomNav, int startColor, int endColor) {
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, endColor);
        colorAnimation.setDuration(300);
        colorAnimation.addUpdateListener(animator -> {
            int animatedColor = (int) animator.getAnimatedValue();
            bottomNav.setBackgroundColor(animatedColor);
            updateBottomNavColors(bottomNav, animatedColor);
        });
        colorAnimation.start();
    }
}