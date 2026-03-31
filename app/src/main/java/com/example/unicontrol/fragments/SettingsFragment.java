package com.example.unicontrol.fragments;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.ContentUris;
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
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
// NEU: Wir nutzen jetzt OneTimeWorkRequest statt Periodic für exaktes Timing!
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
import com.example.unicontrol.MainActivity;
import com.example.unicontrol.R;
import com.example.unicontrol.utils.NetworkUtils;
import com.example.unicontrol.workers.BackupWorker;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Executors;

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

    private static final int REQUEST_CODE_PERMISSIONS = 1002;

    private static volatile boolean isUploadingGlobal = false;
    private static int globalUploadTotal = 0;
    private static int globalUploadCurrent = 0;
    private static int globalUploadSuccess = 0;
    private static int lastErrorCode = 0;
    private static String lastErrorMessage = "";

    private TextView activeTvProgress;
    private ProgressBar activeProgressBar;
    private MaterialButton activeBtnSync;

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
        }
    }

    private void showBackupSettingsBottomSheet() {
        if (getContext() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_list, null);
        sheetView.setBackgroundTintList(ColorStateList.valueOf(getThemeColor()));
        dialog.setContentView(sheetView);

        dialog.setOnDismissListener(d -> {
            activeTvProgress = null;
            activeProgressBar = null;
            activeBtnSync = null;
        });

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
                        populateBackupList(container, albums);
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

    // --- NEU: UHRZEIT-BERECHNUNG FÜR DEN EINMAL-BUTLER ---
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

        // Wenn die Uhrzeit heute schon vorbei ist, planen wir es für morgen
        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24);
        }

        long timeDiff = dueDate.getTimeInMillis() - currentDate.getTimeInMillis();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED) // Nur im WLAN
                .setRequiresBatteryNotLow(true) // Nicht wenn Akku fast leer
                .build();

        // FIX: Wir nutzen eine OneTimeWorkRequest. Diese läuft punktgenau ab!
        OneTimeWorkRequest backupRequest = new OneTimeWorkRequest.Builder(BackupWorker.class)
                .setInitialDelay(timeDiff, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(requireContext()).enqueueUniqueWork(
                "ImmichAutoBackup",
                ExistingWorkPolicy.REPLACE,
                backupRequest);
    }

    private void populateBackupList(LinearLayout container, List<LocalAlbum> albums) {
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

        activeTvProgress = new TextView(getContext());
        activeTvProgress.setTextColor(Color.parseColor("#333333"));
        activeTvProgress.setTypeface(null, Typeface.BOLD);

        activeProgressBar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
        activeProgressBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        activeProgressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#8CA8B3")));

        activeBtnSync = new MaterialButton(getContext());
        activeBtnSync.setTextColor(Color.WHITE);
        activeBtnSync.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#8CA8B3")));
        activeBtnSync.setCornerRadius(60);
        activeBtnSync.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        uploadLayout.addView(activeTvProgress);
        uploadLayout.addView(activeProgressBar);
        uploadLayout.addView(activeBtnSync);
        container.addView(uploadLayout);

        updateUploadUIState();

        activeBtnSync.setOnClickListener(v -> {
            if (isUploadingGlobal) {
                Toast.makeText(getContext(), "Upload läuft bereits...", Toast.LENGTH_SHORT).show();
                return;
            }
            Set<String> toSync = prefs.getStringSet(KEY_BACKUP_ALBUMS, new HashSet<>());
            if (toSync.isEmpty()) {
                Toast.makeText(getContext(), "Bitte wähle zuerst mindestens einen Ordner aus!", Toast.LENGTH_SHORT).show();
                return;
            }

            isUploadingGlobal = true;
            globalUploadCurrent = 0;
            globalUploadTotal = 0;
            globalUploadSuccess = 0;
            lastErrorCode = 0;
            lastErrorMessage = "";
            updateUploadUIState();

            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).setUploadAnimation(true);
            }

            startImmichUploadSync(toSync);
        });
    }

    private void updateUploadUIState() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (activeBtnSync == null || activeTvProgress == null || activeProgressBar == null) return;

            if (isUploadingGlobal) {
                activeBtnSync.setEnabled(false);
                activeBtnSync.setText("Wird hochgeladen...");
                activeTvProgress.setVisibility(View.VISIBLE);
                activeProgressBar.setVisibility(View.VISIBLE);
                activeProgressBar.setMax(globalUploadTotal > 0 ? globalUploadTotal : 100);
                activeProgressBar.setProgress(globalUploadCurrent);
                activeTvProgress.setText("Sichere Datei " + globalUploadCurrent + " von " + globalUploadTotal + "...");
            } else {
                if (globalUploadTotal > 0 && globalUploadCurrent >= globalUploadTotal) {
                    activeTvProgress.setVisibility(View.VISIBLE);
                    activeTvProgress.setText("Fertig! " + globalUploadSuccess + " von " + globalUploadTotal + " gesichert.");
                    activeProgressBar.setVisibility(View.GONE);
                    activeBtnSync.setEnabled(true);
                    activeBtnSync.setText("Upload abgeschlossen");
                } else {
                    activeTvProgress.setVisibility(View.GONE);
                    activeProgressBar.setVisibility(View.GONE);
                    activeBtnSync.setEnabled(true);
                    activeBtnSync.setText("🚀 Back-Up jetzt starten");
                }
            }
        });
    }

    private boolean tryUpload(Context context, String uploadUrlStr, String apiKey, String deviceId, UploadItem item) {
        try {
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();

            String fileName = new File(item.path).getName();
            if (fileName == null || fileName.isEmpty()) fileName = "upload.jpg";
            fileName = fileName.replace("\"", "");

            String mimeType = URLConnection.guessContentTypeFromName(fileName);
            if (mimeType == null) mimeType = "application/octet-stream";

            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            String isoDate = isoFormat.format(new Date(item.dateAddedMs));

            URL url = new URL(uploadUrlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setUseCaches(false);
            conn.setDoOutput(true);
            conn.setDoInput(true);

            conn.setChunkedStreamingMode(0);

            conn.setRequestMethod("POST");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            OutputStream outputStream = conn.getOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true);

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"deviceAssetId\"\r\n\r\n");
            writer.append(item.deviceAssetId).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"deviceId\"\r\n\r\n");
            writer.append(deviceId).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"fileCreatedAt\"\r\n\r\n");
            writer.append(isoDate).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"fileModifiedAt\"\r\n\r\n");
            writer.append(isoDate).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"isFavorite\"\r\n\r\n");
            writer.append("false").append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"assetData\"; filename=\"").append(fileName).append("\"\r\n");
            writer.append("Content-Type: ").append(mimeType).append("\r\n\r\n");
            writer.flush();

            InputStream inputStream = context.getContentResolver().openInputStream(item.contentUri);
            if (inputStream == null) return false;

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
            inputStream.close();

            writer.append("\r\n");
            writer.append("--").append(boundary).append("--\r\n");
            writer.close();

            int responseCode = conn.getResponseCode();
            lastErrorCode = responseCode;

            if (responseCode >= 400) {
                InputStream es = conn.getErrorStream();
                if (es != null) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(es, "UTF-8"));
                    StringBuilder errBuilder = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) errBuilder.append(line);
                    es.close();

                    lastErrorMessage = errBuilder.toString();
                    if (lastErrorMessage.length() > 150) {
                        lastErrorMessage = lastErrorMessage.substring(0, 150) + "...";
                    }
                } else {
                    lastErrorMessage = "Server hat keine Details geliefert.";
                }
            }

            return (responseCode == 200 || responseCode == 201 || responseCode == 409);

        } catch (Exception e) {
            e.printStackTrace();
            lastErrorMessage = e.getMessage();
            return false;
        }
    }

    private void startImmichUploadSync(Set<String> bucketIds) {
        if (getContext() == null) return;

        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedSsid = prefs.getString(KEY_WIFI_SSID, "");
        String localUrl = prefs.getString(KEY_FOTOS_LOCAL, "");
        String publicUrl = prefs.getString(KEY_FOTOS_PUBLIC, "");
        String apiKey = prefs.getString(KEY_FOTOS_API_KEY, "");
        String deviceId = prefs.getString(KEY_DEVICE_ID, UUID.randomUUID().toString());
        String currentSsid = NetworkUtils.getCurrentSsid(getContext());
        String targetUrl = "";

        if (!savedSsid.isEmpty() && currentSsid.equals(savedSsid) && !localUrl.isEmpty()) targetUrl = formatUrl(localUrl, true);
        else if (!publicUrl.isEmpty()) targetUrl = formatUrl(publicUrl, false);
        else if (!localUrl.isEmpty()) targetUrl = formatUrl(localUrl, true);

        if (targetUrl.isEmpty() || apiKey.isEmpty()) {
            if (getActivity() != null) getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Bitte trage erst API-Key und URL in den Einstellungen ein!", Toast.LENGTH_LONG).show());
            isUploadingGlobal = false;
            updateUploadUIState();
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).setUploadAnimation(false);
            return;
        }

        final String cleanBaseUrl = targetUrl.endsWith("/") ? targetUrl.substring(0, targetUrl.length() - 1) : targetUrl;

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<UploadItem> itemsToUpload = new ArrayList<>();
                Uri[] uris = { MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI };

                String[] projection = { MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.BUCKET_ID };

                StringBuilder selection = new StringBuilder(MediaStore.MediaColumns.BUCKET_ID + " IN (");
                String[] selectionArgs = new String[bucketIds.size()];
                int index = 0;
                for (String id : bucketIds) {
                    selection.append("?");
                    if (index < bucketIds.size() - 1) selection.append(",");
                    selectionArgs[index] = id;
                    index++;
                }
                selection.append(")");

                for (Uri uri : uris) {
                    try (Cursor cursor = getContext().getContentResolver().query(uri, projection, selection.toString(), selectionArgs, "DATE_ADDED ASC")) {
                        if (cursor != null) {
                            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                            int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                            int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);

                            while (cursor.moveToNext()) {
                                UploadItem item = new UploadItem();
                                item.deviceAssetId = cursor.getString(idColumn);
                                item.path = cursor.getString(dataColumn);
                                item.dateAddedMs = cursor.getLong(dateColumn) * 1000L;
                                item.contentUri = ContentUris.withAppendedId(uri, cursor.getLong(idColumn));
                                itemsToUpload.add(item);
                            }
                        }
                    }
                }

                if (itemsToUpload.isEmpty()) {
                    isUploadingGlobal = false;
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Ordner sind leer, nichts hochzuladen!", Toast.LENGTH_SHORT).show());
                        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).setUploadAnimation(false);
                    }
                    updateUploadUIState();
                    return;
                }

                globalUploadTotal = itemsToUpload.size();
                globalUploadCurrent = 0;
                globalUploadSuccess = 0;
                updateUploadUIState();

                for (UploadItem item : itemsToUpload) {
                    globalUploadCurrent++;
                    updateUploadUIState();

                    boolean success = tryUpload(getContext(), cleanBaseUrl + "/api/assets", apiKey, deviceId, item);

                    if (success) {
                        globalUploadSuccess++;
                    }
                }

                isUploadingGlobal = false;
                updateUploadUIState();

                if (getActivity() != null) {
                    if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).setUploadAnimation(false);
                    getActivity().runOnUiThread(() -> {

                        if (globalUploadSuccess == 0 && globalUploadTotal > 0) {
                            Toast.makeText(getContext(), "Fehler " + lastErrorCode + ":\n" + lastErrorMessage, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getContext(), "Backup durchgelaufen! 🎉", Toast.LENGTH_LONG).show();
                        }

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            globalUploadCurrent = 0;
                            globalUploadTotal = 0;
                            updateUploadUIState();
                        }, 4000);
                    });
                }

            } catch (Exception e) {
                isUploadingGlobal = false;
                updateUploadUIState();
                if (getActivity() != null) {
                    if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).setUploadAnimation(false);
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Fehler beim Vorbereiten des Backups.", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private static class LocalAlbum {
        String id;
        String name;
        int count;
        String coverImagePath;
    }

    private static class UploadItem {
        String deviceAssetId;
        String path;
        long dateAddedMs;
        Uri contentUri;
    }

    private String formatUrl(String url, boolean isLocal) {
        String formatted = url.trim();
        if (formatted.isEmpty()) return "";
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = (isLocal ? "http://" : "https://") + formatted;
        }
        return formatted;
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
            editor.apply();
            Toast.makeText(getContext(), "Netzwerk-Einstellungen gespeichert!", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });
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