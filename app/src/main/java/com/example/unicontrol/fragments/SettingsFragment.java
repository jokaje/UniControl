package com.example.unicontrol.fragments;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
import com.example.unicontrol.R;
import com.example.unicontrol.workers.BackupWorker;
import com.example.unicontrol.workers.LocationWorker;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SettingsFragment extends Fragment {

    public static final String PREFS_NAME = "UniControlPrefs";
    public static final String KEY_WIFI_SSID = "wifi_ssid";
    public static final String KEY_ECHO_LOCAL = "echo_local";
    public static final String KEY_ECHO_PUBLIC = "echo_public";
    public static final String KEY_WEB_LOCAL = "web_local";
    public static final String KEY_WEB_PUBLIC = "web_public";
    public static final String KEY_HOME_LOCAL = "home_local";
    public static final String KEY_HOME_PUBLIC = "home_public";
    public static final String KEY_FOTOS_LOCAL = "fotos_local";
    public static final String KEY_FOTOS_PUBLIC = "fotos_public";
    public static final String KEY_FOTOS_API_KEY = "fotos_api_key";
    public static final String KEY_COLOR_ECHO = "color_echo";
    public static final String KEY_COLOR_WEB = "color_web";
    public static final String KEY_COLOR_HOME = "color_home";
    public static final String KEY_COLOR_FOTOS = "color_fotos";
    public static final String KEY_COLOR_SETTINGS = "color_settings";
    public static final String KEY_BACKUP_ALBUMS = "backup_albums_set";
    public static final String KEY_DEVICE_ID = "device_id_uuid";

    // Auto-Backup Keys
    public static final String KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled";
    public static final String KEY_AUTO_BACKUP_HOUR = "auto_backup_hour";
    public static final String KEY_AUTO_BACKUP_MINUTE = "auto_backup_minute";

    // Home Assistant Tracker Keys
    public static final String KEY_HOME_TOKEN = LocationWorker.KEY_HOME_TOKEN;
    public static final String KEY_LOCATION_TRACKING_ENABLED = "location_tracking_enabled";

    private static final int REQUEST_CODE_PERMISSIONS = 1002;
    private static final int REQUEST_CODE_LOCATION = 1004;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    private int getThemeColor() {
        if (getContext() == null) return Color.parseColor("#EAEAEA");
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        try {
            return Color.parseColor(prefs.getString(KEY_COLOR_SETTINGS, "#EAEAEA"));
        } catch (Exception e) {
            return Color.parseColor("#EAEAEA");
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(getThemeColor());

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_DEVICE_ID)) {
            prefs.edit().putString(KEY_DEVICE_ID, UUID.randomUUID().toString()).apply();
        }

        view.findViewById(R.id.btn_open_network_settings).setOnClickListener(v -> showNetworkSettingsBottomSheet());
        view.findViewById(R.id.btn_open_design_settings).setOnClickListener(v -> showDesignSettingsBottomSheet());

        Button btnOpenBackup = view.findViewById(R.id.btn_open_backup_settings);
        if (btnOpenBackup != null) btnOpenBackup.setOnClickListener(v -> checkPermissionsAndOpenBackupSettings());
    }

    private void checkPermissionsAndOpenBackupSettings() {
        if (getContext() == null || getActivity() == null) return;

        List<String> requiredPermissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) requiredPermissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) requiredPermissions.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (!requiredPermissions.isEmpty()) {
            requestPermissions(requiredPermissions.toArray(new String[0]), REQUEST_CODE_PERMISSIONS);
        } else {
            showBackupSettingsBottomSheet();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            }
            if (allGranted) showBackupSettingsBottomSheet();
            else Toast.makeText(getContext(), "Berechtigung benötigt, um lokale Alben zu finden!", Toast.LENGTH_LONG).show();
        } else if (requestCode == REQUEST_CODE_LOCATION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            }
            if (allGranted) {
                // Prüfen, ob wir nach Schritt 1 (Vordergrund) noch Schritt 2 (Hintergrund) brauchen
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getContext(), "Schritt 1 fertig! Klicke den Schalter nochmal für die Hintergrund-Erlaubnis.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getContext(), "Alle GPS-Rechte erteilt! Du kannst den Schalter nun dauerhaft aktivieren.", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(getContext(), "Bitte wähle 'Immer zulassen', sonst klappt das Tracking im Hintergrund nicht.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showBackupSettingsBottomSheet() {
        if (getContext() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_list, null);
        sheetView.setBackgroundTintList(ColorStateList.valueOf(getThemeColor()));
        dialog.setContentView(sheetView);

        View bottomSheetInternal = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheetInternal != null) bottomSheetInternal.setBackgroundResource(android.R.color.transparent);

        TextView tvTitle = sheetView.findViewById(R.id.tv_bs_title);
        LinearLayout container = sheetView.findViewById(R.id.layout_bs_container);

        tvTitle.setText("Back-Up Alben wählen");

        TextView loading = new TextView(getContext());
        loading.setText("Durchsuche dein Smartphone...");
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, 50, 0, 50);
        container.addView(loading);

        dialog.show();

        Executors.newSingleThreadExecutor().execute(() -> {
            List<LocalAlbum> albums = scanLocalMediaFolders();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    container.removeAllViews();
                    if (albums.isEmpty()) {
                        TextView empty = new TextView(getContext());
                        empty.setText("Keine lokalen Fotos gefunden.");
                        empty.setGravity(Gravity.CENTER);
                        container.addView(empty);
                    } else {
                        populateBackupList(container, albums, dialog);
                    }
                });
            }
        });
    }

    private List<LocalAlbum> scanLocalMediaFolders() {
        List<LocalAlbum> resultList = new ArrayList<>();
        if (getContext() == null) return resultList;

        HashMap<String, LocalAlbum> albumMap = new HashMap<>();
        Uri[] uris = { MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI };
        String[] projection = { MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, MediaStore.MediaColumns.DATA };

        for (Uri uri : uris) {
            try (Cursor cursor = getContext().getContentResolver().query(uri, projection, null, null, "DATE_ADDED DESC")) {
                if (cursor != null) {
                    int bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID);
                    int bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME);
                    int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);

                    while (cursor.moveToNext()) {
                        String bucketId = cursor.getString(bucketIdColumn);
                        String bucketName = cursor.getString(bucketNameColumn);
                        String imagePath = cursor.getString(dataColumn);

                        if (bucketName == null) bucketName = "Unbekannt";

                        if (albumMap.containsKey(bucketId)) {
                            albumMap.get(bucketId).count++;
                        } else {
                            LocalAlbum newAlbum = new LocalAlbum();
                            newAlbum.id = bucketId;
                            newAlbum.name = bucketName;
                            newAlbum.count = 1;
                            newAlbum.coverImagePath = imagePath;
                            albumMap.put(bucketId, newAlbum);
                        }
                    }
                }
            } catch (Exception e) {}
        }
        resultList.addAll(albumMap.values());
        Collections.sort(resultList, (a, b) -> {
            if (a.name.equalsIgnoreCase("camera")) return -1;
            if (b.name.equalsIgnoreCase("camera")) return 1;
            return a.name.compareToIgnoreCase(b.name);
        });
        return resultList;
    }

    private void scheduleAutoBackup(SharedPreferences prefs) {
        if (getContext() == null) return;

        if (!prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false)) {
            WorkManager.getInstance(requireContext()).cancelUniqueWork("ImmichAutoBackup");
            return;
        }

        int hour = prefs.getInt(KEY_AUTO_BACKUP_HOUR, 2);
        int minute = prefs.getInt(KEY_AUTO_BACKUP_MINUTE, 0);

        Calendar currentDate = Calendar.getInstance();
        Calendar dueDate = Calendar.getInstance();
        dueDate.set(Calendar.HOUR_OF_DAY, hour);
        dueDate.set(Calendar.MINUTE, minute);
        dueDate.set(Calendar.SECOND, 0);

        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24);
        }

        long timeDiff = dueDate.getTimeInMillis() - currentDate.getTimeInMillis();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build();

        // REPLACE Policy ist hier korrekt, da es eine OneTimeWorkRequest ist,
        // die sich selbst wieder in die Schlange einreiht.
        OneTimeWorkRequest backupRequest = new OneTimeWorkRequest.Builder(BackupWorker.class)
                .setInitialDelay(timeDiff, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(requireContext()).enqueueUniqueWork(
                "ImmichAutoBackup",
                ExistingWorkPolicy.REPLACE,
                backupRequest);
    }

    private void populateBackupList(LinearLayout container, List<LocalAlbum> albums, BottomSheetDialog dialog) {
        if (getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> selectedBuckets = prefs.getStringSet(KEY_BACKUP_ALBUMS, new HashSet<>());

        boolean isAutoBackup = prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false);
        int savedHour = prefs.getInt(KEY_AUTO_BACKUP_HOUR, 2);
        int savedMinute = prefs.getInt(KEY_AUTO_BACKUP_MINUTE, 0);

        LinearLayout autoBackupRow = new LinearLayout(getContext());
        autoBackupRow.setOrientation(LinearLayout.HORIZONTAL);
        autoBackupRow.setGravity(Gravity.CENTER_VERTICAL);
        autoBackupRow.setPadding(0, 0, 0, 16);

        TextView tvAutoBackup = new TextView(getContext());
        tvAutoBackup.setText("Automatisches Hintergrund-Backup");
        tvAutoBackup.setTextColor(Color.parseColor("#333333"));
        tvAutoBackup.setTextSize(16f);
        tvAutoBackup.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams paramsAutoText = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        paramsAutoText.setMargins(0, 0, 16, 0);
        tvAutoBackup.setLayoutParams(paramsAutoText);

        SwitchCompat autoToggle = new SwitchCompat(getContext());
        autoToggle.setShowText(false);
        autoToggle.setChecked(isAutoBackup);
        autoToggle.setThumbTintList(ColorStateList.valueOf(Color.parseColor("#8CA8B3")));
        autoToggle.setTrackTintList(ColorStateList.valueOf(Color.parseColor("#D0D0D0")));

        autoBackupRow.addView(tvAutoBackup);
        autoBackupRow.addView(autoToggle);
        container.addView(autoBackupRow);

        LinearLayout timeRow = new LinearLayout(getContext());
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeRow.setPadding(0, 0, 0, 48);
        timeRow.setVisibility(isAutoBackup ? View.VISIBLE : View.GONE);

        TextView tvTimeLabel = new TextView(getContext());
        tvTimeLabel.setText("Tägliche Ausführung (im WLAN):");
        tvTimeLabel.setTextColor(Color.parseColor("#666666"));
        tvTimeLabel.setTextSize(14f);
        LinearLayout.LayoutParams timeLabelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvTimeLabel.setLayoutParams(timeLabelParams);

        TextView tvTimeValue = new TextView(getContext());
        tvTimeValue.setText(String.format(Locale.getDefault(), "%02d:%02d Uhr", savedHour, savedMinute));
        tvTimeValue.setTextColor(Color.parseColor("#333333"));
        tvTimeValue.setTextSize(16f);
        tvTimeValue.setTypeface(null, Typeface.BOLD);
        tvTimeValue.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.bg_pill_selected));
        tvTimeValue.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EAEAEA")));
        tvTimeValue.setPadding(32, 16, 32, 16);

        tvTimeValue.setOnClickListener(v -> {
            int currentHour = prefs.getInt(KEY_AUTO_BACKUP_HOUR, 2);
            int currentMinute = prefs.getInt(KEY_AUTO_BACKUP_MINUTE, 0);

            new TimePickerDialog(getContext(), (view, hourOfDay, minute) -> {
                prefs.edit().putInt(KEY_AUTO_BACKUP_HOUR, hourOfDay).putInt(KEY_AUTO_BACKUP_MINUTE, minute).apply();
                tvTimeValue.setText(String.format(Locale.getDefault(), "%02d:%02d Uhr", hourOfDay, minute));

                scheduleAutoBackup(prefs);

                Toast.makeText(getContext(), "Backup-Zeit auf " + String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute) + " geändert.", Toast.LENGTH_SHORT).show();
            }, currentHour, currentMinute, true).show();
        });

        timeRow.addView(tvTimeLabel);
        timeRow.addView(tvTimeValue);
        container.addView(timeRow);

        autoToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, isChecked).apply();
            timeRow.setVisibility(isChecked ? View.VISIBLE : View.GONE);

            if (isChecked) {
                scheduleAutoBackup(prefs);
                Toast.makeText(getContext(), "Hintergrund-Backup aktiviert! 🚀", Toast.LENGTH_SHORT).show();
            } else {
                WorkManager.getInstance(requireContext()).cancelUniqueWork("ImmichAutoBackup");
                Toast.makeText(getContext(), "Hintergrund-Backup pausiert.", Toast.LENGTH_SHORT).show();
            }
        });

        View separator = new View(getContext());
        separator.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
        separator.setBackgroundColor(Color.parseColor("#DDDDDD"));
        LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2);
        sepParams.setMargins(0, 0, 0, 32);
        separator.setLayoutParams(sepParams);
        container.addView(separator);

        TextView infoText = new TextView(getContext());
        infoText.setText("Wähle die Ordner aus, die gesichert werden sollen:");
        infoText.setTextColor(Color.parseColor("#666666"));
        infoText.setPadding(0, 0, 0, 32);
        container.addView(infoText);

        for (LocalAlbum album : albums) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 0, 0, 32);

            MaterialCardView cardView = new MaterialCardView(getContext());
            cardView.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
            cardView.setRadius(24f);
            cardView.setCardElevation(0f);

            ImageView coverImage = new ImageView(getContext());
            coverImage.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            coverImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(getContext()).load(album.coverImagePath).into(coverImage);
            cardView.addView(coverImage);
            row.addView(cardView);

            LinearLayout textLayout = new LinearLayout(getContext());
            textLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textParams.setMargins(32, 0, 16, 0);
            textLayout.setLayoutParams(textParams);

            TextView tvName = new TextView(getContext());
            tvName.setText(album.name);
            tvName.setTextColor(Color.parseColor("#333333"));
            tvName.setTextSize(16f);
            tvName.setTypeface(null, Typeface.BOLD);

            TextView tvCount = new TextView(getContext());
            tvCount.setText(album.count + " Elemente");
            tvCount.setTextColor(Color.parseColor("#888888"));
            tvCount.setTextSize(12f);

            textLayout.addView(tvName);
            textLayout.addView(tvCount);
            row.addView(textLayout);

            SwitchCompat toggle = new SwitchCompat(getContext());
            toggle.setShowText(false);
            toggle.setChecked(selectedBuckets.contains(album.id));
            toggle.setThumbTintList(ColorStateList.valueOf(Color.parseColor("#8CA8B3")));
            toggle.setTrackTintList(ColorStateList.valueOf(Color.parseColor("#D0D0D0")));

            toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                Set<String> currentSelected = new HashSet<>(prefs.getStringSet(KEY_BACKUP_ALBUMS, new HashSet<>()));
                if (isChecked) currentSelected.add(album.id);
                else currentSelected.remove(album.id);
                prefs.edit().putStringSet(KEY_BACKUP_ALBUMS, currentSelected).apply();
            });

            row.addView(toggle);
            row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
            container.addView(row);
        }

        LinearLayout uploadLayout = new LinearLayout(getContext());
        uploadLayout.setOrientation(LinearLayout.VERTICAL);
        uploadLayout.setGravity(Gravity.CENTER);
        uploadLayout.setPadding(0, 32, 0, 0);

        MaterialButton activeBtnSync = new MaterialButton(getContext());
        activeBtnSync.setText("🚀 Back-Up jetzt im Hintergrund starten");
        activeBtnSync.setTextColor(Color.WHITE);
        activeBtnSync.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#8CA8B3")));
        activeBtnSync.setCornerRadius(60);
        activeBtnSync.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        uploadLayout.addView(activeBtnSync);
        container.addView(uploadLayout);

        activeBtnSync.setOnClickListener(v -> {
            Set<String> toSync = prefs.getStringSet(KEY_BACKUP_ALBUMS, new HashSet<>());
            if (toSync.isEmpty()) {
                Toast.makeText(getContext(), "Bitte wähle zuerst mindestens einen Ordner aus!", Toast.LENGTH_SHORT).show();
                return;
            }

            Data inputData = new Data.Builder()
                    .putBoolean("is_manual", true)
                    .build();

            OneTimeWorkRequest manualBackupRequest = new OneTimeWorkRequest.Builder(BackupWorker.class)
                    .setInputData(inputData)
                    .build();

            WorkManager.getInstance(requireContext()).enqueueUniqueWork(
                    "ImmichManualBackup",
                    ExistingWorkPolicy.REPLACE,
                    manualBackupRequest
            );

            Toast.makeText(getContext(), "Backup gestartet! Siehe Benachrichtigungsleiste.", Toast.LENGTH_LONG).show();
            dialog.dismiss();
        });
    }

    private static class LocalAlbum {
        String id;
        String name;
        int count;
        String coverImagePath;
    }

    private void showNetworkSettingsBottomSheet() {
        if (getContext() == null) return;
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(getContext());
        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_settings, null);
        sheetView.setBackgroundTintList(ColorStateList.valueOf(getThemeColor()));
        bottomSheetDialog.setContentView(sheetView);
        bottomSheetDialog.setOnShowListener(dialog -> {
            BottomSheetDialog d = (BottomSheetDialog) dialog;
            View bottomSheetInternal = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) bottomSheetInternal.setBackgroundResource(android.R.color.transparent);
        });

        EditText etSsid = sheetView.findViewById(R.id.et_home_ssid);
        EditText etEchoLocal = sheetView.findViewById(R.id.et_echo_local);
        EditText etEchoPublic = sheetView.findViewById(R.id.et_echo_public);
        EditText etWebLocal = sheetView.findViewById(R.id.et_web_local);
        EditText etWebPublic = sheetView.findViewById(R.id.et_web_public);
        EditText etHomeLocal = sheetView.findViewById(R.id.et_home_local);
        EditText etHomePublic = sheetView.findViewById(R.id.et_home_public);
        EditText etFotosLocal = sheetView.findViewById(R.id.et_fotos_local);
        EditText etFotosPublic = sheetView.findViewById(R.id.et_fotos_public);
        EditText etFotosApiKey = sheetView.findViewById(R.id.et_fotos_api_key);
        Button btnSave = sheetView.findViewById(R.id.btn_save_settings);

        ViewGroup parentLayout = (ViewGroup) btnSave.getParent();
        if (parentLayout instanceof LinearLayout) {
            int btnIndex = parentLayout.indexOfChild(btnSave);

            EditText etToken = new EditText(getContext());
            etToken.setHint("🔑 Home Assistant Token (Langlebig)");
            etToken.setPadding(40, 40, 40, 40);
            etToken.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.bg_pill_selected));
            etToken.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            etToken.setTextColor(Color.parseColor("#333333"));
            etToken.setHintTextColor(Color.parseColor("#888888"));
            etToken.setTextSize(16f);
            etToken.setElevation(2f);

            LinearLayout.LayoutParams originalParams = (LinearLayout.LayoutParams) etSsid.getLayoutParams();
            LinearLayout.LayoutParams tokenParams = new LinearLayout.LayoutParams(originalParams);
            tokenParams.setMargins(originalParams.leftMargin, 24, originalParams.rightMargin, 24);
            etToken.setLayoutParams(tokenParams);
            parentLayout.addView(etToken, btnIndex);

            SwitchCompat switchTracking = new SwitchCompat(getContext());
            switchTracking.setText("🌍 Standort im Hintergrund an Home Assistant senden");
            switchTracking.setTextColor(Color.parseColor("#333333"));
            switchTracking.setTextSize(14f);
            switchTracking.setTypeface(null, Typeface.BOLD);
            switchTracking.setPadding(0, 16, 0, 48);

            LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            switchParams.setMargins(originalParams.leftMargin, 16, originalParams.rightMargin, 48);
            switchTracking.setLayoutParams(switchParams);
            parentLayout.addView(switchTracking, btnIndex + 1);

            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            etToken.setText(prefs.getString(KEY_HOME_TOKEN, ""));
            switchTracking.setChecked(prefs.getBoolean(KEY_LOCATION_TRACKING_ENABLED, false));

            // --- REPARIERT FÜR ANDROID 11+: Saubere, 2-stufige Rechte-Abfrage ---
            switchTracking.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        // Schritt 1: Zuerst nur den normalen Vordergrund-Standort anfragen
                        switchTracking.setChecked(false); // Aus lassen, bis der Nutzer bestätigt
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE_LOCATION);

                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        // Schritt 2: Wenn der Vordergrund erlaubt ist, den Hintergrund gezielt anfragen
                        switchTracking.setChecked(false); // Aus für den 2. Schritt
                        Toast.makeText(getContext(), "WICHTIG: Bitte wähle gleich 'Immer zulassen' für das Hintergrund-Tracking!", Toast.LENGTH_LONG).show();
                        requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQUEST_CODE_LOCATION);
                    }
                }
            });

            btnSave.setOnClickListener(v -> {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(KEY_WIFI_SSID, etSsid.getText().toString().trim());
                editor.putString(KEY_ECHO_LOCAL, etEchoLocal.getText().toString().trim());
                editor.putString(KEY_ECHO_PUBLIC, etEchoPublic.getText().toString().trim());
                editor.putString(KEY_WEB_LOCAL, etWebLocal.getText().toString().trim());
                editor.putString(KEY_WEB_PUBLIC, etWebPublic.getText().toString().trim());
                editor.putString(KEY_HOME_LOCAL, etHomeLocal.getText().toString().trim());
                editor.putString(KEY_HOME_PUBLIC, etHomePublic.getText().toString().trim());
                editor.putString(KEY_FOTOS_LOCAL, etFotosLocal.getText().toString().trim());
                editor.putString(KEY_FOTOS_PUBLIC, etFotosPublic.getText().toString().trim());
                editor.putString(KEY_FOTOS_API_KEY, etFotosApiKey.getText().toString().trim());

                editor.putString(KEY_HOME_TOKEN, etToken.getText().toString().trim());
                boolean isTracking = switchTracking.isChecked();
                editor.putBoolean(KEY_LOCATION_TRACKING_ENABLED, isTracking);
                editor.apply();

                if (isTracking) {
                    PeriodicWorkRequest locationRequest = new PeriodicWorkRequest.Builder(LocationWorker.class, 15, TimeUnit.MINUTES).build();
                    WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                            "HomeAssistantLocation",
                            ExistingPeriodicWorkPolicy.UPDATE,
                            locationRequest);
                    Toast.makeText(getContext(), "Netzwerk gespeichert & Tracking aktiv! 🌍", Toast.LENGTH_SHORT).show();
                } else {
                    WorkManager.getInstance(requireContext()).cancelUniqueWork("HomeAssistantLocation");
                    Toast.makeText(getContext(), "Netzwerk gespeichert!", Toast.LENGTH_SHORT).show();
                }

                bottomSheetDialog.dismiss();
            });

        } else {
            btnSave.setOnClickListener(v -> {
                SharedPreferences.Editor editor = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                editor.putString(KEY_WIFI_SSID, etSsid.getText().toString().trim());
                editor.putString(KEY_ECHO_LOCAL, etEchoLocal.getText().toString().trim());
                editor.putString(KEY_ECHO_PUBLIC, etEchoPublic.getText().toString().trim());
                editor.putString(KEY_WEB_LOCAL, etWebLocal.getText().toString().trim());
                editor.putString(KEY_WEB_PUBLIC, etWebPublic.getText().toString().trim());
                editor.putString(KEY_HOME_LOCAL, etHomeLocal.getText().toString().trim());
                editor.putString(KEY_HOME_PUBLIC, etHomePublic.getText().toString().trim());
                editor.putString(KEY_FOTOS_LOCAL, etFotosLocal.getText().toString().trim());
                editor.putString(KEY_FOTOS_PUBLIC, etFotosPublic.getText().toString().trim());
                editor.putString(KEY_FOTOS_API_KEY, etFotosApiKey.getText().toString().trim());
                editor.apply();
                Toast.makeText(getContext(), "Netzwerk-Einstellungen gespeichert!", Toast.LENGTH_SHORT).show();
                bottomSheetDialog.dismiss();
            });
        }

        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        etSsid.setText(prefs.getString(KEY_WIFI_SSID, ""));
        etEchoLocal.setText(prefs.getString(KEY_ECHO_LOCAL, ""));
        etEchoPublic.setText(prefs.getString(KEY_ECHO_PUBLIC, ""));
        etWebLocal.setText(prefs.getString(KEY_WEB_LOCAL, ""));
        etWebPublic.setText(prefs.getString(KEY_WEB_PUBLIC, ""));
        etHomeLocal.setText(prefs.getString(KEY_HOME_LOCAL, ""));
        etHomePublic.setText(prefs.getString(KEY_HOME_PUBLIC, ""));
        etFotosLocal.setText(prefs.getString(KEY_FOTOS_LOCAL, ""));
        etFotosPublic.setText(prefs.getString(KEY_FOTOS_PUBLIC, ""));
        etFotosApiKey.setText(prefs.getString(KEY_FOTOS_API_KEY, ""));

        bottomSheetDialog.show();
    }

    private void showDesignSettingsBottomSheet() {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_design, null);
        sheetView.setBackgroundTintList(ColorStateList.valueOf(getThemeColor()));
        dialog.setContentView(sheetView);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bottomDialog = (BottomSheetDialog) d;
            View bottomSheetInternal = bottomDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) bottomSheetInternal.setBackgroundResource(android.R.color.transparent);
        });

        EditText etEcho = sheetView.findViewById(R.id.et_color_echo);
        EditText etWeb = sheetView.findViewById(R.id.et_color_web);
        EditText etHome = sheetView.findViewById(R.id.et_color_home);
        EditText etFotos = sheetView.findViewById(R.id.et_color_fotos);
        EditText etSettings = sheetView.findViewById(R.id.et_color_settings);
        Button btnSave = sheetView.findViewById(R.id.btn_save_colors);

        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        etEcho.setText(prefs.getString(KEY_COLOR_ECHO, "#AEC6CF"));
        etWeb.setText(prefs.getString(KEY_COLOR_WEB, "#FDFD96"));
        etHome.setText(prefs.getString(KEY_COLOR_HOME, "#B2D3C2"));
        etFotos.setText(prefs.getString(KEY_COLOR_FOTOS, "#F49AC2"));
        etSettings.setText(prefs.getString(KEY_COLOR_SETTINGS, "#EAEAEA"));

        btnSave.setOnClickListener(v -> {
            try {
                Color.parseColor(etEcho.getText().toString().trim());
                Color.parseColor(etWeb.getText().toString().trim());
                Color.parseColor(etHome.getText().toString().trim());
                Color.parseColor(etFotos.getText().toString().trim());
                Color.parseColor(etSettings.getText().toString().trim());
            } catch (IllegalArgumentException e) {
                Toast.makeText(getContext(), "Bitte überprüfe deine HEX-Codes! (Beispiel: #FF0000)", Toast.LENGTH_LONG).show();
                return;
            }
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_COLOR_ECHO, etEcho.getText().toString().trim());
            editor.putString(KEY_COLOR_WEB, etWeb.getText().toString().trim());
            editor.putString(KEY_COLOR_HOME, etHome.getText().toString().trim());
            editor.putString(KEY_COLOR_FOTOS, etFotos.getText().toString().trim());
            editor.putString(KEY_COLOR_SETTINGS, etSettings.getText().toString().trim());
            editor.apply();
            Toast.makeText(getContext(), "Farben gespeichert!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            if (getActivity() != null) getActivity().recreate();
        });
        dialog.show();
    }
}