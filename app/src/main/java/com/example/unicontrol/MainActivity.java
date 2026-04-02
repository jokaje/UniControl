package com.example.unicontrol;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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

public class MainActivity extends AppCompatActivity {

    private int currentColor;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    final Fragment echoFragment = new EchoFragment();
    final Fragment webUiFragment = new WebUiFragment();
    final Fragment homeFragment = new HomeFragment();
    final Fragment fotosFragment = new FotosFragment();
    final Fragment settingsFragment = new SettingsFragment();
    final FragmentManager fm = getSupportFragmentManager();
    Fragment activeFragment = homeFragment;

    private ObjectAnimator uploadAnimator;

    // NEU: Getrennte Status-Tracker für die Samsung-Optimierung
    private boolean isManualBackupRunning = false;
    private boolean isAutoBackupRunning = false;

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

        currentColor = getDynamicColor(SettingsFragment.KEY_COLOR_HOME, "#B2D3C2");
        bottomNav.setBackgroundColor(currentColor);
        bottomNav.setBackgroundTintList(null);

        int darkColor = Color.parseColor("#333333");
        ColorStateList iconColorStates = new ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_checked},
                        new int[]{android.R.attr.state_checked}
                },
                new int[]{
                        Color.argb(120, 51, 51, 51),
                        darkColor
                }
        );
        bottomNav.setItemIconTintList(iconColorStates);
        bottomNav.setItemTextColor(iconColorStates);

        if (savedInstanceState == null) {
            fm.beginTransaction().add(R.id.fragment_container, settingsFragment, "5").hide(settingsFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, fotosFragment, "4").hide(fotosFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, webUiFragment, "3").hide(webUiFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, echoFragment, "1").hide(echoFragment).commit();
            fm.beginTransaction().add(R.id.fragment_container, homeFragment, "2").commit();

            bottomNav.setSelectedItemId(R.id.nav_home);
        }

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

            if (newFragment != activeFragment) {
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

        // --- REPARIERT FÜR SAMSUNG: Getrennte, saubere Beobachter ---
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("ImmichManualBackup")
                .observe(this, workInfos -> {
                    isManualBackupRunning = false;
                    for (WorkInfo workInfo : workInfos) {
                        // Wir ignorieren ENQUEUED komplett, da Samsung Jobs oft stundenlang parkt
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
    }

    // Führt die Status beider Worker zusammen, ohne dass sie sich überschreiben
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
        colorAnimation.addUpdateListener(animator ->
                bottomNav.setBackgroundColor((int) animator.getAnimatedValue())
        );
        colorAnimation.start();
    }
}