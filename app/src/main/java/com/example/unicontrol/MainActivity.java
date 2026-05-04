package com.example.unicontrol;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.unicontrol.fragments.AppsFragment;
import com.example.unicontrol.fragments.EchoFragment;
import com.example.unicontrol.fragments.FotosFragment;
import com.example.unicontrol.fragments.HomeFragment;
import com.example.unicontrol.fragments.SettingsFragment;
import com.example.unicontrol.fragments.WebUiFragment;
import com.example.unicontrol.utils.CryptoUtils;
import com.example.unicontrol.utils.NetworkUtils;
import com.example.unicontrol.utils.SettingsManager;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private int currentColor;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private Fragment echoFragment;
    private Fragment webUiFragment;
    private Fragment appsFragment;
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

    private SettingsManager settingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_main);
        requestLocationPermission();

        settingsManager = SettingsManager.getInstance(this);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        String orderString = settingsManager.getModuleOrder();
        String[] keys = orderString.split(",");

        if (savedInstanceState == null) {
            echoFragment = new EchoFragment();
            webUiFragment = new WebUiFragment();
            appsFragment = new AppsFragment();
            homeFragment = new HomeFragment();
            fotosFragment = new FotosFragment();
            settingsFragment = new SettingsFragment();

            fm.beginTransaction().add(R.id.fragment_container, settingsFragment, "5").hide(settingsFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, fotosFragment, "4").hide(fotosFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, appsFragment, "6").hide(appsFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, webUiFragment, "3").hide(webUiFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, echoFragment, "1").hide(echoFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, homeFragment, "2").hide(homeFragment).commit();

            // Intelligenter Start: Wähle das erste Modul in deiner gespeicherten Liste, das aktiviert ist!
            int startId = R.id.nav_settings;
            Fragment startFrag = settingsFragment;
            currentColor = parseColorSafe(settingsManager.getColorSettings(), "#EAEAEA");

            for (String key : keys) {
                if (settingsManager.isModuleEnabled(key)) {
                    if (SettingsManager.KEY_MOD_HOME.equals(key)) { startId = R.id.nav_home; startFrag = homeFragment; currentColor = parseColorSafe(settingsManager.getColorHome(), "#B2D3C2"); break; }
                    else if (SettingsManager.KEY_MOD_FOTOS.equals(key)) { startId = R.id.nav_fotos; startFrag = fotosFragment; currentColor = parseColorSafe(settingsManager.getColorFotos(), "#F49AC2"); break; }
                    else if (SettingsManager.KEY_MOD_ECHO.equals(key)) { startId = R.id.nav_echo; startFrag = echoFragment; currentColor = parseColorSafe(settingsManager.getColorEcho(), "#AEC6CF"); break; }
                    else if (SettingsManager.KEY_MOD_APPS.equals(key)) { startId = R.id.nav_apps; startFrag = appsFragment; currentColor = parseColorSafe(settingsManager.getColorApps(), "#D3B8E8"); break; }
                    else if (SettingsManager.KEY_MOD_WEB.equals(key)) { startId = R.id.nav_web; startFrag = webUiFragment; currentColor = parseColorSafe(settingsManager.getColorWeb(), "#FDFD96"); break; }
                }
            }

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
            appsFragment = fm.findFragmentByTag("6");

            if (echoFragment != null && !echoFragment.isHidden()) activeFragment = echoFragment;
            else if (webUiFragment != null && !webUiFragment.isHidden()) activeFragment = webUiFragment;
            else if (appsFragment != null && !appsFragment.isHidden()) activeFragment = appsFragment;
            else if (fotosFragment != null && !fotosFragment.isHidden()) activeFragment = fotosFragment;
            else if (settingsFragment != null && !settingsFragment.isHidden()) activeFragment = settingsFragment;
            else activeFragment = homeFragment;

            currentColor = parseColorSafe(settingsManager.getColorSettings(), "#EAEAEA"); // Fallback
            if (activeFragment == homeFragment) currentColor = parseColorSafe(settingsManager.getColorHome(), "#B2D3C2");
            else if (activeFragment == fotosFragment) currentColor = parseColorSafe(settingsManager.getColorFotos(), "#F49AC2");
            else if (activeFragment == echoFragment) currentColor = parseColorSafe(settingsManager.getColorEcho(), "#AEC6CF");
            else if (activeFragment == appsFragment) currentColor = parseColorSafe(settingsManager.getColorApps(), "#D3B8E8");
            else if (activeFragment == webUiFragment) currentColor = parseColorSafe(settingsManager.getColorWeb(), "#FDFD96");

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
                targetColor = parseColorSafe(settingsManager.getColorEcho(), "#AEC6CF");
            } else if (itemId == R.id.nav_apps) {
                newFragment = appsFragment;
                targetColor = parseColorSafe(settingsManager.getColorApps(), "#D3B8E8");
            } else if (itemId == R.id.nav_web) {
                newFragment = webUiFragment;
                targetColor = parseColorSafe(settingsManager.getColorWeb(), "#FDFD96");
            } else if (itemId == R.id.nav_home) {
                newFragment = homeFragment;
                targetColor = parseColorSafe(settingsManager.getColorHome(), "#B2D3C2");
            } else if (itemId == R.id.nav_fotos) {
                newFragment = fotosFragment;
                targetColor = parseColorSafe(settingsManager.getColorFotos(), "#F49AC2");
            } else if (itemId == R.id.nav_settings) {
                newFragment = settingsFragment;
                targetColor = parseColorSafe(settingsManager.getColorSettings(), "#EAEAEA");
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

    public void refreshMenu() {
        if (settingsManager == null) settingsManager = SettingsManager.getInstance(this);

        String orderString = settingsManager.getModuleOrder();
        String[] keys = orderString.split(",");

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            // Leere die Menüleiste komplett
            bottomNav.getMenu().clear();

            // Füge die Tabs in der korrekten Reihenfolge wieder ein
            for (String key : keys) {
                if (settingsManager.isModuleEnabled(key)) {
                    if (SettingsManager.KEY_MOD_HOME.equals(key)) {
                        bottomNav.getMenu().add(Menu.NONE, R.id.nav_home, Menu.NONE, "Home").setIcon(R.drawable.ic_home);
                    } else if (SettingsManager.KEY_MOD_FOTOS.equals(key)) {
                        bottomNav.getMenu().add(Menu.NONE, R.id.nav_fotos, Menu.NONE, "Fotos").setIcon(R.drawable.ic_flower);
                    } else if (SettingsManager.KEY_MOD_ECHO.equals(key)) {
                        bottomNav.getMenu().add(Menu.NONE, R.id.nav_echo, Menu.NONE, "Echo").setIcon(R.drawable.ic_robot);
                    } else if (SettingsManager.KEY_MOD_APPS.equals(key)) {
                        bottomNav.getMenu().add(Menu.NONE, R.id.nav_apps, Menu.NONE, "Apps").setIcon(android.R.drawable.ic_menu_edit);
                    } else if (SettingsManager.KEY_MOD_WEB.equals(key)) {
                        bottomNav.getMenu().add(Menu.NONE, R.id.nav_web, Menu.NONE, "Web UI").setIcon(R.drawable.ic_globe);
                    }
                }
            }
            // Einstellungen immer ans Ende hängen
            bottomNav.getMenu().add(Menu.NONE, R.id.nav_settings, Menu.NONE, "Einstellungen").setIcon(R.drawable.ic_settings);
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

    public void goToNextOnboardingTab(int currentNavId) {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav == null) return;

        Menu menu = bottomNav.getMenu();
        boolean foundCurrent = false;

        // Durchlaufe das dynamische Menü, finde den aktuellen Tab und wähle den NÄCHSTEN
        for (int i = 0; i < menu.size(); i++) {
            int id = menu.getItem(i).getItemId();
            if (foundCurrent) {
                bottomNav.setSelectedItemId(id);
                return; // Nächster Tab gefunden und gewechselt!
            }
            if (id == currentNavId) {
                foundCurrent = true;
            }
        }

        // Fallback: Wenn wir am Ende sind oder nichts finden -> Zurück zum ersten Item
        if (menu.size() > 0) {
            bottomNav.setSelectedItemId(menu.getItem(0).getItemId());
        }
    }

    private void handleNfcIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction()) ||
                NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction()) ||
                NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {

            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                // Hier greifen wir noch einmal kurz auf die SharedPreferences zu,
                // da der nfc_write_mode_id ein kurzlebiger "Einmal-Wert" ist.
                String writeId = settingsManager.getPrefs().getString("nfc_write_mode_id", null);

                if (writeId != null) {
                    writeNfcTag(tag, writeId);
                    settingsManager.getPrefs().edit().remove("nfc_write_mode_id").apply();
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
                String token = settingsManager.getHomeToken();
                if (token.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Fehler: Kein Home Assistant Token hinterlegt!", Toast.LENGTH_LONG).show());
                    return;
                }

                String savedSsid = settingsManager.getWifiSsid();
                String localUrl = settingsManager.getHomeLocal();
                String publicUrl = settingsManager.getHomePublic();
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

                CryptoUtils cryptoUtils = new CryptoUtils(this);
                String deviceId = cryptoUtils.getDeviceId();
                if (deviceId == null || deviceId.isEmpty()) deviceId = "android-app";

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

    private int parseColorSafe(String hex, String defaultHex) {
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