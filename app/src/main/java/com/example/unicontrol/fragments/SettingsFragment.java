package com.example.unicontrol.fragments;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
import com.example.unicontrol.utils.CryptoUtils;
import com.example.unicontrol.utils.SettingsManager;
import com.example.unicontrol.viewmodels.SettingsViewModel;
import com.example.unicontrol.workers.BackupWorker;
import com.example.unicontrol.workers.LocationWorker;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SettingsFragment extends Fragment {

    public static final String PREFS_NAME = SettingsManager.PREF_NAME;

    private static final int REQUEST_CODE_PERMISSIONS = 1002;
    private static final int REQUEST_CODE_LOCATION = 1004;

    private SettingsManager settingsManager;
    private SettingsViewModel viewModel;
    private CryptoUtils cryptoUtils;
    private SwitchCompat switchTracking;

    private List<ModuleItem> moduleItems;
    private ModuleAdapter moduleAdapter;

    // UI Referenzen
    private EditText etSsid, etEchoLocal, etEchoPublic, etOpenClawPassword;
    private EditText etWebLocal, etWebPublic, etAppsLocal, etAppsPublic;
    private EditText etHomeLocal, etHomePublic, etHomeToken;
    private EditText etFotosLocal, etFotosPublic, etFotosApiKey;
    private EditText etDeviceId, etPublicKey, etPrivateKey;
    private EditText etColorHome, etColorFotos, etColorEcho, etColorWeb, etColorApps, etColorSettings;

    private BottomSheetDialog currentBackupDialog;
    private LinearLayout backupContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        settingsManager = SettingsManager.getInstance(requireContext());
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        cryptoUtils = new CryptoUtils(requireContext());

        view.setBackgroundColor(Color.parseColor(settingsManager.getColorSettings()));

        initUI(view);
        loadValuesToUI();
        setupObservers();

        MaterialButton btnSaveAll = view.findViewById(R.id.btn_save_all);
        btnSaveAll.setOnClickListener(v -> saveAllSettings());
    }

    private void initUI(View view) {
        // Module RecyclerView
        RecyclerView rvModules = view.findViewById(R.id.rv_modules);
        rvModules.setLayoutManager(new LinearLayoutManager(getContext()));

        moduleItems = loadModuleItems();
        moduleAdapter = new ModuleAdapter(moduleItems);
        rvModules.setAdapter(moduleAdapter);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                Collections.swap(moduleItems, fromPosition, toPosition);
                moduleAdapter.notifyItemMoved(fromPosition, toPosition);
                return true;
            }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }
        });
        itemTouchHelper.attachToRecyclerView(rvModules);

        // Bindings
        etSsid = view.findViewById(R.id.et_home_ssid);
        etEchoLocal = view.findViewById(R.id.et_echo_local);
        etEchoPublic = view.findViewById(R.id.et_echo_public);
        etOpenClawPassword = view.findViewById(R.id.et_openclaw_password);
        etWebLocal = view.findViewById(R.id.et_web_local);
        etWebPublic = view.findViewById(R.id.et_web_public);
        etAppsLocal = view.findViewById(R.id.et_apps_local);
        etAppsPublic = view.findViewById(R.id.et_apps_public);
        etHomeLocal = view.findViewById(R.id.et_home_local);
        etHomePublic = view.findViewById(R.id.et_home_public);
        etHomeToken = view.findViewById(R.id.et_home_token);
        etFotosLocal = view.findViewById(R.id.et_fotos_local);
        etFotosPublic = view.findViewById(R.id.et_fotos_public);
        etFotosApiKey = view.findViewById(R.id.et_fotos_api_key);
        etDeviceId = view.findViewById(R.id.et_device_id);
        etPublicKey = view.findViewById(R.id.et_public_key);
        etPrivateKey = view.findViewById(R.id.et_private_key);
        etColorHome = view.findViewById(R.id.et_color_home);
        etColorFotos = view.findViewById(R.id.et_color_fotos);
        etColorEcho = view.findViewById(R.id.et_color_echo);
        etColorWeb = view.findViewById(R.id.et_color_web);
        if (etColorApps != null) etColorApps = view.findViewById(R.id.et_color_apps);
        etColorSettings = view.findViewById(R.id.et_color_settings);

        switchTracking = view.findViewById(R.id.switch_tracking);

        setupColorPicker(etColorHome);
        setupColorPicker(etColorFotos);
        setupColorPicker(etColorEcho);
        setupColorPicker(etColorWeb);
        if (etColorApps != null) setupColorPicker(etColorApps);
        setupColorPicker(etColorSettings);

        // Tracker Logic
        switchTracking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    switchTracking.setChecked(false);
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE_LOCATION);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    switchTracking.setChecked(false);
                    // Den Toast belassen wir erstmal hartgecodet oder man kann ihn auch auslagern
                    Toast.makeText(getContext(), "WICHTIG: Bitte wähle gleich 'Immer zulassen' für das Hintergrund-Tracking!", Toast.LENGTH_LONG).show();
                    requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQUEST_CODE_LOCATION);
                } else {
                    settingsManager.setLocationTrackingEnabled(true);
                    startLocationTracking();
                    Toast.makeText(getContext(), getString(R.string.toast_tracking_activated), Toast.LENGTH_SHORT).show();
                }
            } else {
                settingsManager.setLocationTrackingEnabled(false);
                WorkManager.getInstance(requireContext()).cancelUniqueWork("HomeAssistantLocation");
                Toast.makeText(getContext(), getString(R.string.toast_tracking_paused), Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.btn_write_nfc).setOnClickListener(v -> {
            String newTagId = UUID.randomUUID().toString();
            settingsManager.getPrefs().edit().putString("nfc_write_mode_id", newTagId).apply();
            Toast.makeText(getContext(), getString(R.string.toast_nfc_ready), Toast.LENGTH_LONG).show();
        });

        view.findViewById(R.id.btn_open_backup_settings).setOnClickListener(v -> checkPermissionsAndOpenBackupSettings());
    }

    private void loadValuesToUI() {
        etSsid.setText(settingsManager.getWifiSsid());
        etEchoLocal.setText(settingsManager.getEchoLocal());
        etEchoPublic.setText(settingsManager.getEchoPublic());
        if (etOpenClawPassword != null) etOpenClawPassword.setText(settingsManager.getOpenClawPassword());
        etWebLocal.setText(settingsManager.getWebLocal());
        etWebPublic.setText(settingsManager.getWebPublic());
        if (etAppsLocal != null) etAppsLocal.setText(settingsManager.getAppsLocal());
        if (etAppsPublic != null) etAppsPublic.setText(settingsManager.getAppsPublic());
        etHomeLocal.setText(settingsManager.getHomeLocal());
        etHomePublic.setText(settingsManager.getHomePublic());
        etHomeToken.setText(settingsManager.getHomeToken());
        etFotosLocal.setText(settingsManager.getFotosLocal());
        etFotosPublic.setText(settingsManager.getFotosPublic());
        etFotosApiKey.setText(settingsManager.getFotosApiKey());

        if (etDeviceId != null) etDeviceId.setText(cryptoUtils.getDeviceId());
        if (etPublicKey != null) etPublicKey.setText(cryptoUtils.getPublicKeyBase64());
        if (etPrivateKey != null) etPrivateKey.setText(cryptoUtils.getPrivateKeyBase64());

        etColorHome.setText(settingsManager.getColorHome());
        etColorFotos.setText(settingsManager.getColorFotos());
        etColorEcho.setText(settingsManager.getColorEcho());
        etColorWeb.setText(settingsManager.getColorWeb());
        if (etColorApps != null) etColorApps.setText(settingsManager.getColorApps());
        etColorSettings.setText(settingsManager.getColorSettings());

        switchTracking.setChecked(settingsManager.isLocationTrackingEnabled());
    }

    private void saveAllSettings() {
        StringBuilder orderBuilder = new StringBuilder();
        for (int i = 0; i < moduleItems.size(); i++) {
            ModuleItem item = moduleItems.get(i);
            settingsManager.setModuleEnabled(item.key, item.enabled);
            orderBuilder.append(item.key);
            if (i < moduleItems.size() - 1) orderBuilder.append(",");
        }
        settingsManager.setModuleOrder(orderBuilder.toString());

        settingsManager.setWifiSsid(etSsid.getText().toString().trim());
        settingsManager.setEchoLocal(etEchoLocal.getText().toString().trim());
        settingsManager.setEchoPublic(etEchoPublic.getText().toString().trim());
        if (etOpenClawPassword != null) settingsManager.setOpenClawPassword(etOpenClawPassword.getText().toString().trim());

        settingsManager.setWebLocal(etWebLocal.getText().toString().trim());
        settingsManager.setWebPublic(etWebPublic.getText().toString().trim());
        if (etAppsLocal != null) settingsManager.setAppsLocal(etAppsLocal.getText().toString().trim());
        if (etAppsPublic != null) settingsManager.setAppsPublic(etAppsPublic.getText().toString().trim());

        settingsManager.setHomeLocal(etHomeLocal.getText().toString().trim());
        settingsManager.setHomePublic(etHomePublic.getText().toString().trim());
        settingsManager.setHomeToken(etHomeToken.getText().toString().trim());

        settingsManager.setFotosLocal(etFotosLocal.getText().toString().trim());
        settingsManager.setFotosPublic(etFotosPublic.getText().toString().trim());
        settingsManager.setFotosApiKey(etFotosApiKey.getText().toString().trim());

        if (etDeviceId != null && etPrivateKey != null && etPublicKey != null) {
            String newDevId = etDeviceId.getText().toString().trim();
            String newPriv = etPrivateKey.getText().toString().trim();
            String newPub = etPublicKey.getText().toString().trim();
            if (!newDevId.isEmpty() && !newPriv.isEmpty() && !newPub.isEmpty()) {
                cryptoUtils.setIdentity(newDevId, newPriv, newPub);
            }
        }

        settingsManager.setColorHome(etColorHome.getText().toString().trim());
        settingsManager.setColorFotos(etColorFotos.getText().toString().trim());
        settingsManager.setColorEcho(etColorEcho.getText().toString().trim());
        settingsManager.setColorWeb(etColorWeb.getText().toString().trim());
        if (etColorApps != null) settingsManager.setColorApps(etColorApps.getText().toString().trim());
        settingsManager.setColorSettings(etColorSettings.getText().toString().trim());

        // NEU: Wir nutzen den Text aus der strings.xml
        Toast.makeText(getContext(), getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show();

        if (getView() != null) getView().setBackgroundColor(Color.parseColor(settingsManager.getColorSettings()));
        if (getActivity() instanceof com.example.unicontrol.MainActivity) {
            ((com.example.unicontrol.MainActivity) getActivity()).refreshMenu();
        }
    }

    private void setupObservers() {
        viewModel.getLocalAlbums().observe(getViewLifecycleOwner(), albums -> {
            if (backupContainer != null && currentBackupDialog != null) {
                backupContainer.removeAllViews();
                if (albums == null || albums.isEmpty()) {
                    TextView empty = new TextView(getContext());
                    // NEU: Text aus strings.xml
                    empty.setText(getString(R.string.backup_sheet_empty));
                    empty.setGravity(Gravity.CENTER);
                    backupContainer.addView(empty);
                } else {
                    populateBackupList(backupContainer, albums, currentBackupDialog);
                }
            }
        });
    }

    private void startLocationTracking() {
        PeriodicWorkRequest locationRequest = new PeriodicWorkRequest.Builder(LocationWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork("HomeAssistantLocation", ExistingPeriodicWorkPolicy.UPDATE, locationRequest);
    }

    private List<ModuleItem> loadModuleItems() {
        List<ModuleItem> allModules = new ArrayList<>();
        // NEU: Die Titel kommen jetzt aus der strings.xml
        allModules.add(new ModuleItem(SettingsManager.KEY_MOD_HOME, getString(R.string.module_home), "#B2D3C2"));
        allModules.add(new ModuleItem(SettingsManager.KEY_MOD_FOTOS, getString(R.string.module_fotos), "#F49AC2"));
        allModules.add(new ModuleItem(SettingsManager.KEY_MOD_ECHO, getString(R.string.module_echo), "#AEC6CF"));
        allModules.add(new ModuleItem(SettingsManager.KEY_MOD_APPS, getString(R.string.module_apps), "#D3B8E8"));
        allModules.add(new ModuleItem(SettingsManager.KEY_MOD_WEB, getString(R.string.module_web), "#FDFD96"));

        String orderString = settingsManager.getModuleOrder();
        String[] keys = orderString.split(",");

        List<ModuleItem> sorted = new ArrayList<>();
        for (String key : keys) {
            for (ModuleItem item : allModules) {
                if (item.key.equals(key)) {
                    item.enabled = settingsManager.isModuleEnabled(key);
                    sorted.add(item);
                    break;
                }
            }
        }
        for (ModuleItem item : allModules) {
            if (!sorted.contains(item)) {
                item.enabled = settingsManager.isModuleEnabled(item.key);
                sorted.add(item);
            }
        }
        return sorted;
    }

    private static class ModuleItem {
        String key, title, colorHex;
        boolean enabled;
        ModuleItem(String key, String title, String colorHex) {
            this.key = key; this.title = title; this.colorHex = colorHex; this.enabled = true;
        }
    }

    private class ModuleAdapter extends RecyclerView.Adapter<ModuleAdapter.ViewHolder> {
        private final List<ModuleItem> items;

        ModuleAdapter(List<ModuleItem> items) { this.items = items; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_module, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ModuleItem item = items.get(position);
            holder.switchModule.setText(item.title);
            holder.switchModule.setOnCheckedChangeListener(null);
            holder.switchModule.setChecked(item.enabled);
            try { holder.switchModule.setThumbTintList(ColorStateList.valueOf(Color.parseColor(item.colorHex))); } catch (Exception ignored) {}
            holder.switchModule.setOnCheckedChangeListener((buttonView, isChecked) -> item.enabled = isChecked);
        }

        @Override public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            SwitchCompat switchModule;
            ImageView dragHandle;
            ViewHolder(View itemView) {
                super(itemView);
                switchModule = itemView.findViewById(R.id.switch_module);
                dragHandle = itemView.findViewById(R.id.iv_drag_handle);
            }
        }
    }

    // --- UI Helpers ---
    private void setupColorPicker(EditText editText) {
        if (editText == null) return;
        editText.setFocusable(false);
        editText.setClickable(true);
        editText.setCursorVisible(false);
        applyColorToEditText(editText, editText.getText().toString());
        editText.setOnClickListener(v -> showColorPickerDialog(editText));
    }

    private void showColorPickerDialog(EditText targetEditText) {
        if (getContext() == null || targetEditText == null) return;
        final String[] colorHexes = {"#B2D3C2", "#F49AC2", "#AEC6CF", "#FDFD96", "#D3B8E8", "#EAEAEA", "#FFB347", "#CFCFC4", "#B39EB5", "#77DD77", "#84B6F4", "#FDCAE1", "#FFD1DC", "#C1E1C1", "#333333", "#FFFFFF"};
        final String[] colorNames = {"Uni Grün (Home)", "Uni Pink (Fotos)", "Uni Blau (Echo)", "Uni Gelb (Web)", "Uni Flieder (Apps)", "Uni Grau (Settings)", "Pastell Orange", "Mittelgrau", "Zartes Lila", "Hellgrün", "Himmelblau", "Rosa", "Kirschblüte", "Minzgrün", "Dunkel (Fast Schwarz)", "Klar Weiß"};

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Wähle eine Menü-Farbe");

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(), android.R.layout.select_dialog_item, colorNames) {
            @NonNull @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextSize(16f);
                view.setPadding(32, 32, 32, 32);
                int color;
                try { color = Color.parseColor(colorHexes[position]); } catch (Exception e) { color = Color.TRANSPARENT; }
                GradientDrawable colorCircle = new GradientDrawable();
                colorCircle.setShape(GradientDrawable.OVAL);
                colorCircle.setColor(color);
                if (colorHexes[position].equals("#FFFFFF")) colorCircle.setStroke(2, Color.parseColor("#CCCCCC"));
                colorCircle.setSize(60, 60);
                view.setCompoundDrawablesWithIntrinsicBounds(colorCircle, null, null, null);
                view.setCompoundDrawablePadding(32);
                return view;
            }
        };

        builder.setAdapter(adapter, (dialog, which) -> {
            String selectedHex = colorHexes[which];
            targetEditText.setText(selectedHex);
            applyColorToEditText(targetEditText, selectedHex);
        });

        builder.setNegativeButton("Abbrechen", null).show();
    }

    private void applyColorToEditText(EditText editText, String hexColor) {
        try {
            int color = Color.parseColor(hexColor);
            editText.setBackgroundTintList(ColorStateList.valueOf(color));
            boolean isDark = ColorUtils.calculateLuminance(color) < 0.5;
            editText.setTextColor(isDark ? Color.WHITE : Color.parseColor("#333333"));
        } catch (Exception ignored) {}
    }

    // --- Backup Logik ---
    private void checkPermissionsAndOpenBackupSettings() {
        if (getContext() == null || getActivity() == null) return;
        List<String> requiredPermissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) requiredPermissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) requiredPermissions.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!requiredPermissions.isEmpty()) requestPermissions(requiredPermissions.toArray(new String[0]), REQUEST_CODE_PERMISSIONS);
        else showBackupSettingsBottomSheet();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) if (result != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            if (allGranted) showBackupSettingsBottomSheet();
            else Toast.makeText(getContext(), getString(R.string.toast_storage_permission_needed), Toast.LENGTH_LONG).show();
        } else if (requestCode == REQUEST_CODE_LOCATION) {
            boolean allGranted = true;
            for (int result : grantResults) if (result != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            if (allGranted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getContext(), "Schritt 1 fertig! Aktiviere den Schalter erneut für die Hintergrund-Erlaubnis.", Toast.LENGTH_LONG).show();
                } else {
                    if (switchTracking != null) switchTracking.setChecked(true);
                }
            } else {
                Toast.makeText(getContext(), getString(R.string.toast_location_permission_needed), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showBackupSettingsBottomSheet() {
        if (getContext() == null) return;
        currentBackupDialog = new BottomSheetDialog(getContext());
        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_list, null);
        sheetView.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(settingsManager.getColorSettings())));
        currentBackupDialog.setContentView(sheetView);

        View bottomSheetInternal = currentBackupDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheetInternal != null) bottomSheetInternal.setBackgroundResource(android.R.color.transparent);

        TextView tvTitle = sheetView.findViewById(R.id.tv_bs_title);
        backupContainer = sheetView.findViewById(R.id.layout_bs_container);

        // NEU: Text aus strings.xml
        tvTitle.setText(getString(R.string.backup_sheet_title));

        TextView loading = new TextView(getContext());
        loading.setText(getString(R.string.backup_sheet_loading));
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, 50, 0, 50);
        backupContainer.addView(loading);

        currentBackupDialog.show();
        viewModel.loadLocalMediaFolders(requireContext());
    }

    private void scheduleAutoBackup() {
        if (!settingsManager.isAutoBackupEnabled()) {
            WorkManager.getInstance(requireContext()).cancelUniqueWork("ImmichAutoBackup");
            return;
        }
        int hour = settingsManager.getAutoBackupHour();
        int minute = settingsManager.getAutoBackupMinute();

        Calendar currentDate = Calendar.getInstance();
        Calendar dueDate = Calendar.getInstance();
        dueDate.set(Calendar.HOUR_OF_DAY, hour);
        dueDate.set(Calendar.MINUTE, minute);
        dueDate.set(Calendar.SECOND, 0);
        if (dueDate.before(currentDate)) dueDate.add(Calendar.HOUR_OF_DAY, 24);

        long timeDiff = dueDate.getTimeInMillis() - currentDate.getTimeInMillis();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build();
        OneTimeWorkRequest backupRequest = new OneTimeWorkRequest.Builder(BackupWorker.class)
                .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(requireContext()).enqueueUniqueWork("ImmichAutoBackup", ExistingWorkPolicy.REPLACE, backupRequest);
    }

    private void populateBackupList(LinearLayout container, List<SettingsViewModel.LocalAlbum> albums, BottomSheetDialog dialog) {
        if (getContext() == null) return;
        Set<String> selectedBuckets = settingsManager.getBackupAlbums();
        boolean isAutoBackup = settingsManager.isAutoBackupEnabled();
        int savedHour = settingsManager.getAutoBackupHour();
        int savedMinute = settingsManager.getAutoBackupMinute();

        LinearLayout autoBackupRow = new LinearLayout(getContext());
        autoBackupRow.setOrientation(LinearLayout.HORIZONTAL);
        autoBackupRow.setGravity(Gravity.CENTER_VERTICAL);
        autoBackupRow.setPadding(0, 0, 0, 16);

        TextView tvAutoBackup = new TextView(getContext());
        tvAutoBackup.setText(getString(R.string.backup_sheet_auto_title));
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
        tvTimeLabel.setText(getString(R.string.backup_sheet_auto_time_label));
        tvTimeLabel.setTextColor(Color.parseColor("#666666"));
        tvTimeLabel.setTextSize(14f);
        timeRow.addView(tvTimeLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTimeValue = new TextView(getContext());
        tvTimeValue.setText(String.format(Locale.getDefault(), "%02d:%02d Uhr", savedHour, savedMinute));
        tvTimeValue.setTextColor(Color.parseColor("#333333"));
        tvTimeValue.setTextSize(16f);
        tvTimeValue.setTypeface(null, Typeface.BOLD);
        tvTimeValue.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.bg_pill_selected));
        tvTimeValue.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EAEAEA")));
        tvTimeValue.setPadding(32, 16, 32, 16);

        tvTimeValue.setOnClickListener(v -> {
            new TimePickerDialog(getContext(), (view, hourOfDay, minute) -> {
                settingsManager.setAutoBackupTime(hourOfDay, minute);
                tvTimeValue.setText(String.format(Locale.getDefault(), "%02d:%02d Uhr", hourOfDay, minute));
                scheduleAutoBackup();
                Toast.makeText(getContext(), "Backup-Zeit auf " + String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute) + " geändert.", Toast.LENGTH_SHORT).show();
            }, settingsManager.getAutoBackupHour(), settingsManager.getAutoBackupMinute(), true).show();
        });

        timeRow.addView(tvTimeValue);
        container.addView(timeRow);

        autoToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsManager.setAutoBackupEnabled(isChecked);
            timeRow.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                scheduleAutoBackup();
                Toast.makeText(getContext(), getString(R.string.toast_backup_auto_activated), Toast.LENGTH_SHORT).show();
            } else {
                WorkManager.getInstance(requireContext()).cancelUniqueWork("ImmichAutoBackup");
                Toast.makeText(getContext(), getString(R.string.toast_backup_paused), Toast.LENGTH_SHORT).show();
            }
        });

        View separator = new View(getContext());
        separator.setBackgroundColor(Color.parseColor("#DDDDDD"));
        LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2);
        sepParams.setMargins(0, 0, 0, 32);
        separator.setLayoutParams(sepParams);
        container.addView(separator);

        TextView infoText = new TextView(getContext());
        infoText.setText(getString(R.string.backup_sheet_select_folders));
        infoText.setTextColor(Color.parseColor("#666666"));
        infoText.setPadding(0, 0, 0, 32);
        container.addView(infoText);

        for (SettingsViewModel.LocalAlbum album : albums) {
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
                Set<String> currentSelected = new HashSet<>(settingsManager.getBackupAlbums());
                if (isChecked) currentSelected.add(album.id);
                else currentSelected.remove(album.id);
                settingsManager.setBackupAlbums(currentSelected);
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
        activeBtnSync.setText(getString(R.string.backup_sheet_btn_start));
        activeBtnSync.setTextColor(Color.WHITE);
        activeBtnSync.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#8CA8B3")));
        activeBtnSync.setCornerRadius(60);
        activeBtnSync.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        uploadLayout.addView(activeBtnSync);
        container.addView(uploadLayout);

        activeBtnSync.setOnClickListener(v -> {
            Set<String> toSync = settingsManager.getBackupAlbums();
            if (toSync.isEmpty()) {
                Toast.makeText(getContext(), getString(R.string.toast_select_folder_first), Toast.LENGTH_SHORT).show();
                return;
            }
            Data inputData = new Data.Builder().putBoolean("is_manual", true).build();
            OneTimeWorkRequest manualBackupRequest = new OneTimeWorkRequest.Builder(BackupWorker.class).setInputData(inputData).build();
            WorkManager.getInstance(requireContext()).enqueueUniqueWork("ImmichManualBackup", ExistingWorkPolicy.REPLACE, manualBackupRequest);
            Toast.makeText(getContext(), getString(R.string.toast_backup_started), Toast.LENGTH_LONG).show();
            dialog.dismiss();
        });
    }
}