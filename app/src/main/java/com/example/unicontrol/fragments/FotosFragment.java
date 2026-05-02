package com.example.unicontrol.fragments;

import android.content.ClipData;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

// --- NEUE EXOPLAYER IMPORTE ---
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.example.unicontrol.R;
import com.example.unicontrol.adapters.AlbumAdapter;
import com.example.unicontrol.adapters.FotosAdapter;
import com.example.unicontrol.adapters.MemoriesAdapter;
import com.example.unicontrol.adapters.PersonAdapter;
import com.example.unicontrol.models.GalleryItem;
import com.example.unicontrol.models.ImmichAlbum;
import com.example.unicontrol.models.ImmichAsset;
import com.example.unicontrol.models.ImmichMemory;
import com.example.unicontrol.models.ImmichPerson;
import com.example.unicontrol.network.ImmichApi;
import com.example.unicontrol.network.RetrofitClient;
import com.example.unicontrol.utils.NetworkUtils;
import com.example.unicontrol.viewmodels.SharedViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Executors;

public class FotosFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerViewFotos;

    private RecyclerView recyclerViewMemories;
    private View layoutFotosMain;

    private View layoutSearch;
    private View layoutSearchCategories;
    private View layoutSearchBottomButtons;
    private EditText etSearchInput;
    private TextView tvSearchLoading;
    private RecyclerView recyclerViewSearch;

    private View layoutAlbums;
    private EditText etAlbumSearch;
    private TextView tvAlbumsLoading;
    private RecyclerView recyclerViewAlbums;
    private List<ImmichAlbum> globalAlbumList = null;

    private View layoutLibrary;

    private TextView tvPlaceholder;

    // --- NEU: Onboarding UI Elemente ---
    private View layoutFotosContent;
    private View layoutFotosSetup;
    private View layoutFotosIntroOverlay;
    // -----------------------------------

    private CoordinatorLayout fullscreenOverlay;
    private ViewPager2 viewPagerFullscreen;
    private TextView btnCloseFullscreen;
    private ImageView btnFavorite;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private TextView tvDetailDate, tvDetailLocation, tvDetailFilename, tvDetailCamera;
    private EditText etDetailDescription;

    private TextView tabFotos, tabSuche, tabAlben, tabBibliothek;

    private View layoutSelectionBar;
    private View layoutSelectionBottomBar;
    private TextView tvSelectionCount;
    private View btnCloseSelection;
    private ImageView btnSelectionShare, btnSelectionAddTo, btnSelectionDelete;

    private ImageView btnShareFullscreen, btnEditFullscreen, btnAddToFullscreen, btnDeleteFullscreen;
    private MaterialButton btnUploadLocal;
    private MaterialButton btnSelectionUploadLocal;

    // Globale UI Elemente für den Vollbildmodus
    private View btnMoreOptions;
    private View bottomMenuFullscreen;
    private boolean isFullscreenUiVisible = false;

    private FotosAdapter currentFotosAdapter;

    private String currentApiUrl = "";
    private String currentApiKey = "";
    private List<ImmichAsset> globalAssetList = new ArrayList<>();

    private List<ImmichAsset> localMediaCache = new ArrayList<>();

    private String activeTab = "Fotos";

    private List<ImmichAsset> lastSearchResults = new ArrayList<>();
    private int lastSearchMode = -1;

    private String currentViewedAlbumId = null;
    private ImmichAsset currentViewedAsset = null;

    private HashMap<String, String> localDescriptions = new HashMap<>();
    private HashSet<String> favoriteAssets = new HashSet<>();
    private HashMap<String, String> displayToOriginalLocationMap = new HashMap<>();

    private Handler descriptionHandler = new Handler(Looper.getMainLooper());
    private Runnable descriptionRunnable;
    private boolean isUpdatingDescriptionUI = false;
    private ViewPager2.OnPageChangeCallback pageChangeCallback;

    private Handler storyAutoPlayHandler = new Handler(Looper.getMainLooper());
    private boolean isStoryModeActive = false;
    private boolean isStoryPaused = false;
    private long lastTapTime = 0;

    private android.animation.ValueAnimator storyAnimator;
    private LinearLayout storyProgressContainer;

    private int currentPage = 1;
    private boolean isLoading = false;
    private boolean isLastPage = false;
    private static final int PAGE_SIZE = 150;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_fotos, container, false);
    }

    private int getThemeColor() {
        if (getContext() == null) return Color.parseColor("#F49AC2");
        SharedPreferences prefs = getContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
        String colorStr = prefs.getString(SettingsFragment.KEY_COLOR_FOTOS, "#F49AC2");

        if (colorStr != null && !colorStr.startsWith("#")) {
            colorStr = "#" + colorStr;
        }

        try {
            return Color.parseColor(colorStr);
        } catch (Exception e) {
            return Color.parseColor("#F49AC2");
        }
    }

    private boolean isThemeDark() {
        return ColorUtils.calculateLuminance(getThemeColor()) < 0.5;
    }

    private int getUnselectedTabColor() {
        return isThemeDark() ? Color.parseColor("#B0B0B0") : Color.parseColor("#555555");
    }

    private int getPillHighlightColor() {
        int baseColor = getThemeColor();
        float[] hsv = new float[3];
        Color.colorToHSV(baseColor, hsv);

        if (isThemeDark()) {
            hsv[2] = Math.min(1.0f, hsv[2] + 0.15f);
        } else {
            hsv[2] = Math.max(0.0f, hsv[2] - 0.10f);
        }
        return Color.HSVToColor(hsv);
    }

    private Drawable getThemedPillDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        if (getContext() != null) {
            drawable.setCornerRadius(20f * getContext().getResources().getDisplayMetrics().density);
        } else {
            drawable.setCornerRadius(60f);
        }
        drawable.setColor(getPillHighlightColor());
        return drawable;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // --- NEU: Back-Button abfangen ---
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (fullscreenOverlay != null && fullscreenOverlay.getVisibility() == View.VISIBLE) {
                    closeFullscreen();
                } else if (bottomSheetBehavior != null && (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED || bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HALF_EXPANDED)) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                } else if (layoutSelectionBar != null && layoutSelectionBar.getVisibility() == View.VISIBLE) {
                    if (currentFotosAdapter != null) currentFotosAdapter.clearSelection();
                } else {
                    // Weiterleiten an das System / die Activity, wenn wir nichts abfangen müssen
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
        // ---------------------------------

        view.setBackgroundColor(getThemeColor());

        // --- NEU: Layer Binding ---
        layoutFotosContent = view.findViewById(R.id.layout_fotos_content);
        layoutFotosSetup = view.findViewById(R.id.layout_fotos_setup);
        layoutFotosIntroOverlay = view.findViewById(R.id.layout_fotos_intro_overlay);
        // --------------------------

        if (getActivity() != null && getActivity().getWindow() != null) {
            WindowInsetsControllerCompat windowInsetsController = new WindowInsetsControllerCompat(getActivity().getWindow(), view);
            windowInsetsController.setAppearanceLightStatusBars(!isThemeDark());
        }

        View topTabs = view.findViewById(R.id.layout_top_tabs);
        if (topTabs != null) {
            topTabs.setBackgroundColor(Color.TRANSPARENT);
        }

        layoutFotosMain = view.findViewById(R.id.layout_fotos_main);

        recyclerViewMemories = view.findViewById(R.id.recycler_view_memories);
        if (recyclerViewMemories != null) {
            recyclerViewMemories.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        }

        recyclerViewFotos = view.findViewById(R.id.recycler_view_fotos);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#8CA8B3"), getThemeColor());
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (!currentApiUrl.isEmpty() && !currentApiKey.isEmpty()) {
                    currentPage = 1;
                    isLastPage = false;
                    syncFavoritesInBackground();
                    loadLocalAssetsAsync();
                    fetchPhotosFromImmich(currentApiUrl, currentApiKey, currentPage);
                    fetchMemories();
                } else {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }

        layoutSelectionBar = view.findViewById(R.id.layout_selection_bar);
        layoutSelectionBottomBar = view.findViewById(R.id.layout_selection_bottom_bar);
        tvSelectionCount = view.findViewById(R.id.tv_selection_count);
        btnCloseSelection = view.findViewById(R.id.btn_close_selection);

        btnSelectionShare = view.findViewById(R.id.btn_selection_share);
        btnSelectionAddTo = view.findViewById(R.id.btn_selection_add_to);
        btnSelectionDelete = view.findViewById(R.id.btn_selection_delete);

        if (layoutSelectionBottomBar instanceof LinearLayout) {
            btnSelectionUploadLocal = new MaterialButton(getContext());
            btnSelectionUploadLocal.setText("☁️ Jetzt hochladen");
            btnSelectionUploadLocal.setTextColor(Color.WHITE);
            btnSelectionUploadLocal.setCornerRadius(60);

            LinearLayout.LayoutParams paramsUpload = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            paramsUpload.setMargins(64, 0, 64, 0);
            btnSelectionUploadLocal.setLayoutParams(paramsUpload);
            btnSelectionUploadLocal.setVisibility(View.GONE);
            btnSelectionUploadLocal.setOnClickListener(v -> {
                if (currentFotosAdapter != null) uploadMultipleAssets(currentFotosAdapter.getSelectedAssets());
            });
            ((LinearLayout) layoutSelectionBottomBar).addView(btnSelectionUploadLocal);
        }

        if (btnCloseSelection != null) btnCloseSelection.setOnClickListener(v -> {
            if (currentFotosAdapter != null) currentFotosAdapter.clearSelection();
        });

        if (btnSelectionShare != null) btnSelectionShare.setOnClickListener(v -> {
            if (currentFotosAdapter != null) shareAssets(currentFotosAdapter.getSelectedAssets());
        });
        if (btnSelectionAddTo != null) btnSelectionAddTo.setOnClickListener(v -> showMultiActionMenu());
        if (btnSelectionDelete != null) btnSelectionDelete.setOnClickListener(v -> {
            if (currentFotosAdapter != null) deleteAssets(currentFotosAdapter.getSelectedAssets());
        });

        if (layoutSelectionBar != null) layoutSelectionBar.setBackgroundColor(getThemeColor());

        if (layoutSelectionBottomBar != null) {
            layoutSelectionBottomBar.setBackgroundColor(isThemeDark() ? Color.parseColor("#1E1E1E") : Color.WHITE);
        }
        int bottomIconColor = isThemeDark() ? Color.WHITE : Color.parseColor("#555555");
        if (btnSelectionShare != null) btnSelectionShare.setColorFilter(bottomIconColor);
        if (btnSelectionAddTo != null) btnSelectionAddTo.setColorFilter(bottomIconColor);
        if (btnSelectionDelete != null) btnSelectionDelete.setColorFilter(bottomIconColor);

        layoutSearch = view.findViewById(R.id.layout_search);
        layoutSearchCategories = view.findViewById(R.id.layout_search_categories);
        layoutSearchBottomButtons = view.findViewById(R.id.layout_search_bottom_buttons);
        etSearchInput = view.findViewById(R.id.et_search_input);
        tvSearchLoading = view.findViewById(R.id.tv_search_loading);
        if (tvSearchLoading != null) tvSearchLoading.setTextColor(getUnselectedTabColor());
        recyclerViewSearch = view.findViewById(R.id.recycler_view_search);

        layoutAlbums = view.findViewById(R.id.layout_albums);
        etAlbumSearch = view.findViewById(R.id.et_album_search);
        tvAlbumsLoading = view.findViewById(R.id.tv_albums_loading);
        if (tvAlbumsLoading != null) tvAlbumsLoading.setTextColor(getUnselectedTabColor());

        recyclerViewAlbums = view.findViewById(R.id.recycler_view_albums);
        recyclerViewAlbums.setLayoutManager(new GridLayoutManager(getContext(), 2));

        layoutLibrary = view.findViewById(R.id.layout_library);

        tvPlaceholder = view.findViewById(R.id.tv_fotos_placeholder);
        if (tvPlaceholder != null) tvPlaceholder.setTextColor(getUnselectedTabColor());

        fullscreenOverlay = view.findViewById(R.id.fullscreen_overlay);
        viewPagerFullscreen = view.findViewById(R.id.view_pager_fullscreen);

        // NEU: Preloading für den ViewPager aktivieren, verhindert schwarze Übergänge in Stories
        if (viewPagerFullscreen != null) {
            viewPagerFullscreen.setOffscreenPageLimit(1);
        }

        btnCloseFullscreen = view.findViewById(R.id.btn_close_fullscreen);
        btnFavorite = view.findViewById(R.id.btn_favorite);

        View bottomSheet = view.findViewById(R.id.bottom_sheet_details);
        if (bottomSheet != null) {
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
            bottomSheetBehavior.setHideable(true);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            bottomSheet.setBackgroundTintList(ColorStateList.valueOf(getThemeColor()));
        }

        tvDetailDate = view.findViewById(R.id.tv_detail_date);
        tvDetailLocation = view.findViewById(R.id.tv_detail_location);
        tvDetailFilename = view.findViewById(R.id.tv_detail_filename);
        tvDetailCamera = view.findViewById(R.id.tv_detail_camera);
        etDetailDescription = view.findViewById(R.id.et_detail_description);

        tabFotos = view.findViewById(R.id.tab_fotos);
        tabSuche = view.findViewById(R.id.tab_suche);
        tabAlben = view.findViewById(R.id.tab_alben);
        tabBibliothek = view.findViewById(R.id.tab_bibliothek);

        setupActionButtons(view);
        setupDescriptionSaver();
        setupMenuClicks();
        setupDashboardButtons(view);
        setupSearchLogic();
        setupAlbumSearchLogic();

        if (btnCloseFullscreen != null) {
            btnCloseFullscreen.setOnClickListener(v -> closeFullscreen());
        }

        setupRecyclerView(recyclerViewFotos, true);
        setupRecyclerView(recyclerViewSearch, false);

        selectTab(tabFotos, "Fotos");
        loadAppropriateUrlAndKey();
    }

    private void toggleFullscreenUI() {
        isFullscreenUiVisible = !isFullscreenUiVisible;
        int visibility = isFullscreenUiVisible ? View.VISIBLE : View.GONE;
        if (btnCloseFullscreen != null) btnCloseFullscreen.setVisibility(visibility);
        if (btnFavorite != null) btnFavorite.setVisibility(visibility);
        if (btnMoreOptions != null) btnMoreOptions.setVisibility(visibility);
        if (bottomMenuFullscreen != null) bottomMenuFullscreen.setVisibility(visibility);
    }

    private List<ImmichAsset> filterLocalAssetsWithServer(List<ImmichAsset> localAssets) {
        List<ImmichAsset> acceptedAssets = new ArrayList<>();
        String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;

        int chunkSize = 500;

        for (int i = 0; i < localAssets.size(); i += chunkSize) {
            int end = Math.min(localAssets.size(), i + chunkSize);
            List<ImmichAsset> chunk = localAssets.subList(i, end);

            try {
                JsonObject root = new JsonObject();
                JsonArray assetsArray = new JsonArray();
                for (ImmichAsset item : chunk) {
                    JsonObject assetObj = new JsonObject();
                    assetObj.addProperty("id", item.deviceAssetId);
                    assetsArray.add(assetObj);
                }
                root.add("assets", assetsArray);
                String jsonBody = root.toString();

                URL url = new URL(cleanBaseUrl + "/api/asset/bulk-upload-check");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                OutputStream os = conn.getOutputStream();
                os.write(jsonBody.getBytes("UTF-8"));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();

                if (responseCode == 404 || responseCode == 405) {
                    url = new URL(cleanBaseUrl + "/api/assets/bulk-upload-check");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("x-api-key", currentApiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    os = conn.getOutputStream();
                    os.write(jsonBody.getBytes("UTF-8"));
                    os.flush();
                    os.close();
                    responseCode = conn.getResponseCode();
                }

                if (responseCode == 200 || responseCode == 201) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    JsonObject responseObj = JsonParser.parseString(response.toString()).getAsJsonObject();
                    if (responseObj.has("results")) {
                        JsonArray results = responseObj.getAsJsonArray("results");
                        HashSet<String> acceptedIds = new HashSet<>();
                        for (JsonElement element : results) {
                            JsonObject resObj = element.getAsJsonObject();
                            if ("accept".equalsIgnoreCase(resObj.get("action").getAsString())) {
                                acceptedIds.add(resObj.get("id").getAsString());
                            }
                        }
                        for (ImmichAsset item : chunk) {
                            if (acceptedIds.contains(item.deviceAssetId)) {
                                acceptedAssets.add(item);
                            }
                        }
                    } else {
                        acceptedAssets.addAll(chunk);
                    }
                } else {
                    acceptedAssets.addAll(chunk);
                }
            } catch (Exception e) {
                acceptedAssets.addAll(chunk);
            }
        }
        return acceptedAssets;
    }

    private void loadLocalAssetsAsync() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<ImmichAsset> tempLocalCache = new ArrayList<>();
            if (getContext() == null) return;

            SharedPreferences prefs = getContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
            Set<String> bucketIds = prefs.getStringSet(SettingsFragment.KEY_BACKUP_ALBUMS, new HashSet<>());
            Set<String> blacklist = prefs.getStringSet("blacklisted_local_assets", new HashSet<>());

            if (bucketIds.isEmpty()) return;

            Uri[] uris = { MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI };
            String[] projection = { MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE };

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
                try (Cursor cursor = getContext().getContentResolver().query(uri, projection, selection.toString(), selectionArgs, "DATE_ADDED DESC")) {
                    if (cursor != null) {
                        int idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                        int dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
                        int nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                        int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);

                        while (cursor.moveToNext()) {
                            String _id = cursor.getString(idCol);

                            if (blacklist.contains(_id)) {
                                continue;
                            }

                            ImmichAsset localAsset = new ImmichAsset();
                            localAsset.id = "local_" + _id;
                            localAsset.deviceAssetId = _id;
                            localAsset.originalFileName = cursor.getString(nameCol);

                            long dateMs = cursor.getLong(dateCol) * 1000L;
                            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                            localAsset.fileCreatedAt = isoFormat.format(new Date(dateMs));

                            String mime = cursor.getString(mimeCol);
                            localAsset.type = (mime != null && mime.startsWith("video/")) ? "VIDEO" : "IMAGE";

                            localAsset.isLocalOnly = true;
                            localAsset.localUri = ContentUris.withAppendedId(uri, Long.parseLong(_id)).toString();

                            tempLocalCache.add(localAsset);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            List<ImmichAsset> verifiedNewAssets = new ArrayList<>();
            if (!tempLocalCache.isEmpty() && !currentApiUrl.isEmpty() && !currentApiKey.isEmpty()) {
                verifiedNewAssets = filterLocalAssetsWithServer(tempLocalCache);
            } else {
                verifiedNewAssets = tempLocalCache;
            }

            final List<ImmichAsset> finalAssetsToDisplay = verifiedNewAssets;

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    localMediaCache.clear();
                    localMediaCache.addAll(finalAssetsToDisplay);

                    if (!globalAssetList.isEmpty() && "Fotos".equals(activeTab)) {
                        refreshVisibleGrids();
                    }
                });
            }
        });
    }

    private void shareAssets(List<ImmichAsset> selected) {
        if (selected == null || selected.isEmpty() || getContext() == null) return;

        for(ImmichAsset a : selected) {
            if(a.isLocalOnly) {
                Toast.makeText(getContext(), "Bitte warte, bis die Bilder hochgeladen sind, oder teile sie über die Samsung-Galerie.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        Toast.makeText(getContext(), "Lade " + selected.size() + (selected.size() == 1 ? " Datei" : " Dateien") + " zum Teilen herunter...", Toast.LENGTH_LONG).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            ArrayList<Uri> uriList = new ArrayList<>();
            boolean hasImage = false;
            boolean hasVideo = false;

            String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
            File sharedImagesDir = new File(getContext().getCacheDir(), "shared_images");
            if (!sharedImagesDir.exists()) sharedImagesDir.mkdirs();

            for (int i = 0; i < selected.size(); i++) {
                ImmichAsset asset = selected.get(i);
                final int progress = i + 1;

                if (getActivity() != null && selected.size() > 3 && progress % 3 == 0) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Lade Datei " + progress + " von " + selected.size() + "...", Toast.LENGTH_SHORT).show());
                }

                try {
                    String downloadUrl = cleanBaseUrl + "/api/assets/" + asset.id + "/original";
                    boolean isVideo = asset.type != null && asset.type.equals("VIDEO");
                    if (isVideo) hasVideo = true; else hasImage = true;

                    String fileExtension = isVideo ? ".mp4" : ".jpg";
                    File fileToShare = new File(sharedImagesDir, "share_" + asset.id + fileExtension);

                    if (!fileToShare.exists()) {
                        URL url = new URL(downloadUrl);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("x-api-key", currentApiKey);
                        conn.connect();

                        if (conn.getResponseCode() == 200 || conn.getResponseCode() == 201) {
                            InputStream in = conn.getInputStream();
                            OutputStream out = new FileOutputStream(fileToShare);
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                            in.close();
                            out.close();
                        }
                    }
                    Uri contentUri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", fileToShare);
                    uriList.add(contentUri);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (!uriList.isEmpty() && getActivity() != null) {
                boolean finalHasImage = hasImage;
                boolean finalHasVideo = hasVideo;
                getActivity().runOnUiThread(() -> {
                    Intent shareIntent = new Intent();

                    if (uriList.size() == 1) {
                        shareIntent.setAction(Intent.ACTION_SEND);
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uriList.get(0));
                    } else {
                        shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
                    }

                    String mimeType = "*/*";
                    if (finalHasImage && !finalHasVideo) mimeType = "image/*";
                    if (finalHasVideo && !finalHasImage) mimeType = "video/*";

                    shareIntent.setType(mimeType);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(shareIntent, "Teilen über..."));

                    if (currentFotosAdapter != null) currentFotosAdapter.clearSelection();
                });
            } else {
                if (getActivity() != null) getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Fehler beim Download", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void deleteAssets(List<ImmichAsset> selected) {
        if (selected == null || selected.isEmpty() || getContext() == null) return;

        List<ImmichAsset> cloudAssetsToDelete = new ArrayList<>();
        List<ImmichAsset> localOnlyAssetsToBlacklist = new ArrayList<>();

        for(ImmichAsset a : selected) {
            if(a.isLocalOnly) {
                localOnlyAssetsToBlacklist.add(a);
            } else {
                cloudAssetsToDelete.add(a);
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle(selected.size() + (selected.size() == 1 ? " Foto ausblenden/löschen" : " Fotos ausblenden/löschen"))
                .setMessage("Möchtest du diese Bilder in der Cloud löschen bzw. dauerhaft auf diesem Gerät ausblenden?")
                .setPositiveButton("Ja, weg damit", (dialog, which) -> {
                    Toast.makeText(getContext(), "Wird verarbeitet...", Toast.LENGTH_SHORT).show();

                    SharedPreferences prefs = getContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
                    Set<String> blacklist = new HashSet<>(prefs.getStringSet("blacklisted_local_assets", new HashSet<>()));

                    if (!localOnlyAssetsToBlacklist.isEmpty()) {
                        for (ImmichAsset a : localOnlyAssetsToBlacklist) {
                            if (a.deviceAssetId != null) blacklist.add(a.deviceAssetId);
                        }
                        prefs.edit().putStringSet("blacklisted_local_assets", blacklist).apply();

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                localMediaCache.removeAll(localOnlyAssetsToBlacklist);

                                if (cloudAssetsToDelete.isEmpty()) {
                                    if (currentFotosAdapter != null) currentFotosAdapter.clearSelection();
                                    refreshVisibleGrids();
                                    if (fullscreenOverlay != null && fullscreenOverlay.getVisibility() == View.VISIBLE) {
                                        closeFullscreen();
                                    }
                                    Toast.makeText(getContext(), "Aus der Ansicht entfernt!", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }

                    if (!cloudAssetsToDelete.isEmpty()) {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            HttpURLConnection conn = null;
                            try {
                                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                                URL url = new URL(cleanBaseUrl + "/api/asset");
                                conn = (HttpURLConnection) url.openConnection();
                                conn.setRequestMethod("DELETE");
                                conn.setRequestProperty("x-api-key", currentApiKey);
                                conn.setRequestProperty("Content-Type", "application/json");
                                conn.setDoOutput(true);

                                StringBuilder ids = new StringBuilder();
                                for (int i = 0; i < cloudAssetsToDelete.size(); i++) {
                                    ids.append("\"").append(cloudAssetsToDelete.get(i).id).append("\"");
                                    if (i < cloudAssetsToDelete.size() - 1) ids.append(",");
                                }
                                String json = "{\"ids\": [" + ids.toString() + "]}";

                                conn.getOutputStream().write(json.getBytes("UTF-8"));

                                int responseCode = conn.getResponseCode();

                                if (responseCode == 404 || responseCode == 405) {
                                    url = new URL(cleanBaseUrl + "/api/assets");
                                    conn = (HttpURLConnection) url.openConnection();
                                    conn.setRequestMethod("DELETE");
                                    conn.setRequestProperty("x-api-key", currentApiKey);
                                    conn.setRequestProperty("Content-Type", "application/json");
                                    conn.setDoOutput(true);
                                    conn.getOutputStream().write(json.getBytes("UTF-8"));
                                    responseCode = conn.getResponseCode();
                                }

                                if (responseCode == 200 || responseCode == 201 || responseCode == 204) {
                                    for (ImmichAsset a : cloudAssetsToDelete) {
                                        if (a.deviceAssetId != null) blacklist.add(a.deviceAssetId);
                                    }
                                    prefs.edit().putStringSet("blacklisted_local_assets", blacklist).apply();

                                    if (getActivity() != null) {
                                        getActivity().runOnUiThread(() -> {
                                            globalAssetList.removeAll(cloudAssetsToDelete);
                                            if (lastSearchResults != null) lastSearchResults.removeAll(cloudAssetsToDelete);

                                            for (ImmichAsset deletedAsset : cloudAssetsToDelete) {
                                                java.util.Iterator<ImmichAsset> it = localMediaCache.iterator();
                                                while (it.hasNext()) {
                                                    ImmichAsset local = it.next();
                                                    if (local.deviceAssetId != null && local.deviceAssetId.equals(deletedAsset.deviceAssetId)) {
                                                        it.remove();
                                                    }
                                                }
                                            }

                                            if (currentFotosAdapter != null) currentFotosAdapter.clearSelection();
                                            refreshVisibleGrids();
                                            if (fullscreenOverlay != null && fullscreenOverlay.getVisibility() == View.VISIBLE) {
                                                closeFullscreen();
                                            }
                                            Toast.makeText(getContext(), cloudAssetsToDelete.size() + (cloudAssetsToDelete.size() == 1 ? " Element gelöscht" : " Elemente gelöscht"), Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                } else {
                                    if (getActivity() != null) {
                                        int finalResponseCode = responseCode;
                                        getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Löschen fehlgeschlagen (" + finalResponseCode + ")", Toast.LENGTH_LONG).show());
                                    }
                                }
                            } catch (Exception e) {
                                if (getActivity() != null) getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Netzwerkfehler beim Löschen", Toast.LENGTH_SHORT).show());
                            } finally {
                                if (conn != null) conn.disconnect();
                            }
                        });
                    }
                })
                .setNegativeButton("Abbrechen", null).show();
    }

    private void showMultiActionMenu() {
        if (getContext() == null || currentFotosAdapter == null) return;
        List<ImmichAsset> selected = currentFotosAdapter.getSelectedAssets();
        if (selected.isEmpty()) return;

        for(ImmichAsset a : selected) {
            if(a.isLocalOnly) {
                Toast.makeText(getContext(), "Cloud-Aktionen sind erst möglich, wenn die Bilder (☁️) hochgeladen wurden.", Toast.LENGTH_LONG).show();
                if (currentFotosAdapter != null) currentFotosAdapter.clearSelection();
                return;
            }
        }

        showActionMenu(selected);
    }

    private void showActionMenu(List<ImmichAsset> assetsToModify) {
        if (getContext() == null || assetsToModify.isEmpty()) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_list, null);
        sheetView.setBackgroundTintList(ColorStateList.valueOf(getThemeColor()));
        dialog.setContentView(sheetView);

        View bottomSheetInternal = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheetInternal != null) bottomSheetInternal.setBackgroundResource(android.R.color.transparent);

        TextView tvTitle = sheetView.findViewById(R.id.tv_bs_title);
        LinearLayout container = sheetView.findViewById(R.id.layout_bs_container);

        tvTitle.setText(assetsToModify.size() + (assetsToModify.size() == 1 ? " Element ausgewählt" : " Elemente ausgewählt"));

        // NEU: "An Echo senden" im Auswahl-Menü (nur bei 1 Bild sinnvoll)
        if (assetsToModify.size() == 1) {
            TextView optEcho = createBottomSheetOption("💬  An Echo senden");
            optEcho.setOnClickListener(v -> {
                sendToEcho(assetsToModify.get(0));
                dialog.dismiss();
            });
            container.addView(optEcho);
        }

        TextView optAlbum = createBottomSheetOption("📂  Zu Album hinzufügen");
        optAlbum.setOnClickListener(v -> loadAndShowAlbumsInBottomSheet(dialog, tvTitle, container, assetsToModify));
        container.addView(optAlbum);

        boolean firstIsArchived = assetsToModify.get(0).isArchived != null && assetsToModify.get(0).isArchived;
        TextView optArchiv = createBottomSheetOption(firstIsArchived ? "🗃️  Aus Archiv wiederherstellen" : "🗃️  Ins Archiv verschieben");
        optArchiv.setOnClickListener(v -> {
            archiveMultipleAssets(!firstIsArchived, assetsToModify);
            dialog.dismiss();
        });
        container.addView(optArchiv);

        boolean firstIsLocked = assetsToModify.get(0).description != null && assetsToModify.get(0).description.toLowerCase().contains("#locked");
        TextView optLocked = createBottomSheetOption(firstIsLocked ? "🔓  Aus Tresor entfernen" : "🔒  In gesperrten Ordner");
        optLocked.setOnClickListener(v -> {
            promptForLockedFolder(false, firstIsLocked, assetsToModify);
            dialog.dismiss();
        });
        container.addView(optLocked);

        dialog.show();
    }

    private void archiveMultipleAssets(boolean toArchive, List<ImmichAsset> selected) {
        if (getContext() == null || selected.isEmpty()) return;
        Toast.makeText(getContext(), "Wird verarbeitet...", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
            String visibilityValue = toArchive ? "archive" : "timeline";

            for (ImmichAsset asset : selected) {
                HttpURLConnection conn = null;
                try {
                    String escapedDesc = asset.description != null ? asset.description.replace("\"", "\\\"").replace("\n", "\\n") : "";

                    URL url = new URL(cleanBaseUrl + "/api/assets/" + asset.id);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("PUT");
                    conn.setRequestProperty("x-api-key", currentApiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setDoOutput(true);

                    String requestBody = "{\"visibility\":\"" + visibilityValue + "\", \"description\": \"" + escapedDesc + "\"}";

                    OutputStream os = conn.getOutputStream();
                    os.write(requestBody.getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    conn.getResponseCode();
                } catch (Exception e) {
                    Log.e("Immich", "Fehler bei Asset " + asset.id);
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    SharedPreferences prefs = getContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
                    Set<String> blacklist = new HashSet<>(prefs.getStringSet("blacklisted_local_assets", new HashSet<>()));

                    for (ImmichAsset asset : selected) {
                        asset.isArchived = toArchive;
                        if (!toArchive && !globalAssetList.contains(asset)) {
                            globalAssetList.add(asset);
                        }

                        if (toArchive && asset.deviceAssetId != null) {
                            blacklist.add(asset.deviceAssetId);
                            java.util.Iterator<ImmichAsset> it = localMediaCache.iterator();
                            while (it.hasNext()) {
                                ImmichAsset local = it.next();
                                if (local.deviceAssetId != null && local.deviceAssetId.equals(asset.deviceAssetId)) {
                                    it.remove();
                                }
                            }
                        }
                    }

                    prefs.edit().putStringSet("blacklisted_local_assets", blacklist).apply();

                    if (currentFotosAdapter != null) currentFotosAdapter.clearSelection();
                    refreshVisibleGrids();
                    closeFullscreen();
                    Toast.makeText(getContext(), selected.size() + " Elemente verschoben!", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void hideAllMenusForSearch() {
        if (layoutSearchCategories != null) layoutSearchCategories.setVisibility(View.GONE);
        if (layoutSearchBottomButtons != null) layoutSearchBottomButtons.setVisibility(View.GONE);
        if (layoutLibrary != null) layoutLibrary.setVisibility(View.GONE);
        if (layoutAlbums != null) layoutAlbums.setVisibility(View.GONE);
        if (etSearchInput != null) etSearchInput.setVisibility(View.GONE);
        if (layoutSearch != null) layoutSearch.setVisibility(View.VISIBLE);

        View scrollableMenus = getView() != null ? getView().findViewById(R.id.layout_search_scrollable_menus) : null;
        if (scrollableMenus != null) scrollableMenus.setVisibility(View.GONE);
    }

    private void prepareForSearchResults() {
        hideAllMenusForSearch();
        currentViewedAlbumId = null;
    }

    private List<ImmichAsset> parseAssetsFromJson(JsonElement jsonElement) {
        List<ImmichAsset> parsedList = new ArrayList<>();
        JsonArray itemsArray = null;

        if (jsonElement.isJsonArray()) {
            itemsArray = jsonElement.getAsJsonArray();
        } else if (jsonElement.isJsonObject()) {
            JsonObject jsonObj = jsonElement.getAsJsonObject();
            if (jsonObj.has("assets")) {
                JsonElement assetsEl = jsonObj.get("assets");
                if (assetsEl.isJsonArray()) {
                    itemsArray = assetsEl.getAsJsonArray();
                } else if (assetsEl.isJsonObject() && assetsEl.getAsJsonObject().has("items")) {
                    itemsArray = assetsEl.getAsJsonObject().getAsJsonArray("items");
                }
            } else if (jsonObj.has("items")) {
                itemsArray = jsonObj.getAsJsonArray("items");
            }
        }

        if (itemsArray != null) {
            parsedList = new Gson().fromJson(itemsArray, new TypeToken<List<ImmichAsset>>(){}.getType());
            if (parsedList != null) {
                for (int i = 0; i < parsedList.size(); i++) {
                    JsonObject obj = itemsArray.get(i).getAsJsonObject();
                    if (obj.has("isArchived") && !obj.get("isArchived").isJsonNull()) {
                        parsedList.get(i).isArchived = obj.get("isArchived").getAsBoolean();
                    } else if (obj.has("visibility") && !obj.get("visibility").isJsonNull()) {
                        String vis = obj.get("visibility").getAsString();
                        parsedList.get(i).isArchived = "archive".equalsIgnoreCase(vis);
                    } else {
                        parsedList.get(i).isArchived = false;
                    }
                }
            }
        }
        return parsedList != null ? parsedList : new ArrayList<>();
    }

    private void extractMetadataToCache(List<ImmichAsset> assets) {
        if (assets == null) return;
        for (ImmichAsset a : assets) {
            if (a.isFavorite != null && a.isFavorite) {
                favoriteAssets.add(a.id);
            }

            String desc = a.description;
            if ((desc == null || desc.trim().isEmpty()) && a.exifInfo != null) {
                desc = a.exifInfo.description;
                if (desc == null || desc.trim().isEmpty()) {
                    desc = a.exifInfo.imageDescription;
                }
            }

            if (desc != null && !desc.trim().isEmpty()) {
                localDescriptions.put(a.id, desc);
                a.description = desc;
            }
        }
    }

    private void refreshVisibleGrids() {
        if (globalAssetList != null && recyclerViewFotos != null) {
            List<ImmichAsset> safeAssets = new ArrayList<>();
            for (ImmichAsset a : globalAssetList) {
                String d = a.description;
                if (d == null && a.exifInfo != null) d = a.exifInfo.description;
                if (d == null && a.exifInfo != null) d = a.exifInfo.imageDescription;
                boolean isLocked = (d != null && d.toLowerCase().contains("#locked"));
                boolean isArchived = (a.isArchived != null && a.isArchived);

                if (!isLocked && !isArchived) {
                    safeAssets.add(a);
                }
            }
            processAndDisplayAssets(safeAssets, currentApiUrl, currentApiKey, recyclerViewFotos);
        }

        if (recyclerViewSearch != null && recyclerViewSearch.getVisibility() == View.VISIBLE && lastSearchResults != null) {
            List<ImmichAsset> safeSearchAssets = new ArrayList<>();
            for (ImmichAsset a : lastSearchResults) {
                String d = a.description;
                if (d == null && a.exifInfo != null) d = a.exifInfo.description;
                if (d == null && a.exifInfo != null) d = a.exifInfo.imageDescription;
                boolean isLocked = (d != null && d.toLowerCase().contains("#locked"));
                boolean isArchived = (a.isArchived != null && a.isArchived);
                boolean isFav = (a.isFavorite != null && a.isFavorite);

                if (lastSearchMode == 1) {
                    if (isArchived && !isLocked) safeSearchAssets.add(a);
                } else if (lastSearchMode == 2) {
                    if (isLocked) safeSearchAssets.add(a);
                } else if (lastSearchMode == 0) {
                    if (isFav && !isLocked && !isArchived) safeSearchAssets.add(a);
                } else {
                    if (!isLocked && !isArchived) safeSearchAssets.add(a);
                }
            }

            if (lastSearchMode == 4) {
                processAndDisplaySmartSearchResults(safeSearchAssets, currentApiUrl, currentApiKey, recyclerViewSearch);
            } else {
                processAndDisplayAssets(safeSearchAssets, currentApiUrl, currentApiKey, recyclerViewSearch);
            }
        }
    }

    private void fetchAlbumsFromImmich() {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;
        tvAlbumsLoading.setVisibility(View.VISIBLE);
        recyclerViewAlbums.setVisibility(View.GONE);

        ImmichApi api = RetrofitClient.getImmichApi(currentApiUrl);

        // Retrofit .enqueue() führt den Aufruf asynchron im Hintergrund aus
        // und kehrt für onResponse/onFailure AUTOMATISCH auf den Main-Thread zurück!
        api.getAlbums(currentApiKey).enqueue(new retrofit2.Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull retrofit2.Call<JsonElement> call, @NonNull retrofit2.Response<JsonElement> response) {
                if (getActivity() == null) return;

                if (response.isSuccessful() && response.body() != null) {
                    JsonElement root = response.body();
                    JsonArray albumsArray = null;

                    if (root.isJsonArray()) albumsArray = root.getAsJsonArray();
                    else if (root.isJsonObject()) {
                        if (root.getAsJsonObject().has("data")) albumsArray = root.getAsJsonObject().getAsJsonArray("data");
                        else if (root.getAsJsonObject().has("albums")) albumsArray = root.getAsJsonObject().getAsJsonArray("albums");
                    }

                    if (albumsArray != null) {
                        globalAlbumList = new Gson().fromJson(albumsArray, new TypeToken<List<ImmichAlbum>>(){}.getType());
                    }

                    tvAlbumsLoading.setVisibility(View.GONE);
                    if (globalAlbumList != null && !globalAlbumList.isEmpty()) {
                        recyclerViewAlbums.setVisibility(View.VISIBLE);
                        displayAlbums(globalAlbumList);
                    } else {
                        Toast.makeText(getContext(), "Du hast noch keine Alben erstellt.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    tvAlbumsLoading.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Fehler beim Laden der Alben (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull retrofit2.Call<JsonElement> call, @NonNull Throwable t) {
                if (getActivity() == null) return;
                tvAlbumsLoading.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Verbindungsfehler zu Alben: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayAlbums(List<ImmichAlbum> albumsToShow) {
        if (getContext() == null || albumsToShow == null) return;
        AlbumAdapter adapter = new AlbumAdapter(getContext(), albumsToShow, currentApiUrl, currentApiKey, album -> fetchAssetsForAlbum(album.id, album.albumName));
        recyclerViewAlbums.setAdapter(adapter);
    }

    private void setupAlbumSearchLogic() {
        if (etAlbumSearch != null) {
            etAlbumSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (globalAlbumList == null) return;
                    String query = s.toString().trim().toLowerCase();
                    if (query.isEmpty()) {
                        displayAlbums(globalAlbumList);
                        return;
                    }
                    List<ImmichAlbum> filtered = new ArrayList<>();
                    for (ImmichAlbum album : globalAlbumList) {
                        if (album.albumName != null && album.albumName.toLowerCase().contains(query)) {
                            filtered.add(album);
                        }
                    }
                    displayAlbums(filtered);
                }
            });
        }
    }

    private void fetchAssetsForAlbum(String albumId, String albumName) {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;

        prepareForSearchResults();
        currentViewedAlbumId = albumId;

        recyclerViewSearch.setVisibility(View.GONE);

        tvSearchLoading.setVisibility(View.VISIBLE);
        tvSearchLoading.setText("Lade Album '" + albumName + "'...");

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                URL url = new URL(cleanBaseUrl + "/api/albums/" + albumId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");

                int responseCode = conn.getResponseCode();
                if (responseCode == 404 || responseCode == 405) {
                    url = new URL(cleanBaseUrl + "/api/album/" + albumId);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("x-api-key", currentApiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    responseCode = conn.getResponseCode();
                }

                if (responseCode == 200 || responseCode == 201) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    JsonObject albumObject = JsonParser.parseString(response.toString()).getAsJsonObject();
                    List<ImmichAsset> assetList = new ArrayList<>();

                    if (albumObject.has("assets")) {
                        assetList = new Gson().fromJson(albumObject.getAsJsonArray("assets"), new TypeToken<List<ImmichAsset>>(){}.getType());
                    }

                    final List<ImmichAsset> finalAssetList = assetList;

                    if (finalAssetList != null && !finalAssetList.isEmpty()) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                extractMetadataToCache(finalAssetList);

                                lastSearchMode = 3;
                                lastSearchResults = new ArrayList<>(finalAssetList);

                                tvSearchLoading.setVisibility(View.GONE);
                                recyclerViewSearch.setVisibility(View.VISIBLE);
                                processAndDisplayAssets(finalAssetList, cleanBaseUrl, currentApiKey, recyclerViewSearch);
                            });
                        }
                    } else {
                        showAlbumError("Dieses Album enthält noch keine Bilder.");
                    }
                } else {
                    showAlbumError("Fehler beim Laden des Albums (" + responseCode + ")");
                }
            } catch (Exception e) {
                showAlbumError("Verbindungsfehler beim Laden des Albums.");
            }
        });
    }

    private void showAlbumError(String msg) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                tvSearchLoading.setVisibility(View.GONE);
                Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();

                if ("Fotos".equals(activeTab)) selectTab(tabFotos, "Fotos");
                else if ("Suche".equals(activeTab)) selectTab(tabSuche, "Suche");
                else if ("Alben".equals(activeTab)) selectTab(tabAlben, "Alben");
                else if ("Bibliothek".equals(activeTab)) selectTab(tabBibliothek, "Bibliothek");
            });
        }
    }

    private void showSearchError(String msg) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (tvSearchLoading != null) tvSearchLoading.setVisibility(View.GONE);
                Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();

                if ("Fotos".equals(activeTab)) selectTab(tabFotos, "Fotos");
                else if ("Suche".equals(activeTab)) selectTab(tabSuche, "Suche");
                else if ("Alben".equals(activeTab)) selectTab(tabAlben, "Alben");
                else if ("Bibliothek".equals(activeTab)) selectTab(tabBibliothek, "Bibliothek");
            });
        }
    }

    private void showErrorOnUI(String errorMessage) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                if (tvPlaceholder != null) {
                    tvPlaceholder.setVisibility(View.VISIBLE);
                    tvPlaceholder.setText(errorMessage);
                }
                if (layoutFotosMain != null) layoutFotosMain.setVisibility(View.GONE);
                if (layoutSearch != null) layoutSearch.setVisibility(View.GONE);
                if (layoutAlbums != null) layoutAlbums.setVisibility(View.GONE);
                if (layoutLibrary != null) layoutLibrary.setVisibility(View.GONE);
            });
        }
    }

    private void selectTab(TextView selectedTab, String tabName) {
        if (getContext() == null) return;

        activeTab = tabName;
        currentViewedAlbumId = null;

        if (currentFotosAdapter != null) currentFotosAdapter.clearSelection();

        TextView[] allTabs = {tabFotos, tabSuche, tabAlben, tabBibliothek};
        for (TextView tab : allTabs) {
            if (tab != null) {
                tab.setBackground(null);
                tab.setTextColor(getUnselectedTabColor());
                tab.setTypeface(null, Typeface.NORMAL);
            }
        }
        if (selectedTab != null) {
            selectedTab.setBackground(getThemedPillDrawable());
            boolean isPillDark = ColorUtils.calculateLuminance(getPillHighlightColor()) < 0.5;
            selectedTab.setTextColor(isPillDark ? Color.WHITE : Color.parseColor("#333333"));
            selectedTab.setTypeface(null, Typeface.BOLD);
        }

        if (layoutSearch == null || layoutFotosMain == null || layoutAlbums == null || layoutLibrary == null || tvPlaceholder == null) return;

        layoutFotosMain.setVisibility(View.GONE);
        layoutSearch.setVisibility(View.GONE);
        layoutAlbums.setVisibility(View.GONE);
        layoutLibrary.setVisibility(View.GONE);
        tvPlaceholder.setVisibility(View.GONE);
        if (recyclerViewSearch != null) recyclerViewSearch.setVisibility(View.GONE);

        if (tabName.equals("Fotos")) {
            layoutFotosMain.setVisibility(View.VISIBLE);
            hideKeyboard();
            tvPlaceholder.setVisibility(globalAssetList == null || globalAssetList.isEmpty() ? View.VISIBLE : View.GONE);
        } else if (tabName.equals("Suche")) {
            layoutSearch.setVisibility(View.VISIBLE);

            View scrollableMenus = getView() != null ? getView().findViewById(R.id.layout_search_scrollable_menus) : null;
            if (scrollableMenus != null) scrollableMenus.setVisibility(View.VISIBLE);

            if(etSearchInput != null) {
                etSearchInput.setVisibility(View.VISIBLE);
                etSearchInput.setText("");
                if (layoutSearchCategories != null) layoutSearchCategories.setVisibility(View.VISIBLE);
                if (layoutSearchBottomButtons != null) layoutSearchBottomButtons.setVisibility(View.VISIBLE);
            }
        } else if (tabName.equals("Alben")) {
            layoutAlbums.setVisibility(View.VISIBLE);
            hideKeyboard();
            if (globalAlbumList == null || globalAlbumList.isEmpty()) fetchAlbumsFromImmich();
        } else if (tabName.equals("Bibliothek")) {
            layoutLibrary.setVisibility(View.VISIBLE);
            hideKeyboard();
        }
    }

    private void setupMenuClicks() {
        if (tabFotos != null) tabFotos.setOnClickListener(v -> selectTab(tabFotos, "Fotos"));
        if (tabSuche != null) tabSuche.setOnClickListener(v -> selectTab(tabSuche, "Suche"));
        if (tabAlben != null) tabAlben.setOnClickListener(v -> selectTab(tabAlben, "Alben"));
        if (tabBibliothek != null) tabBibliothek.setOnClickListener(v -> selectTab(tabBibliothek, "Bibliothek"));
    }

    private void setupDashboardButtons(View view) {
        if (view.findViewById(R.id.btn_search_personen) != null) view.findViewById(R.id.btn_search_personen).setOnClickListener(v -> fetchPeopleFromImmich());
        if (view.findViewById(R.id.btn_search_ort) != null) view.findViewById(R.id.btn_search_ort).setOnClickListener(v -> showLocationFilterBottomSheet());
        if (view.findViewById(R.id.btn_search_datum) != null) view.findViewById(R.id.btn_search_datum).setOnClickListener(v -> showDateSearchBottomSheet());
        if (view.findViewById(R.id.btn_search_kamera) != null) view.findViewById(R.id.btn_search_kamera).setOnClickListener(v -> showCloudSearchDialog("Kamera suchen", "z.B. iPhone, Sony..."));

        if (view.findViewById(R.id.btn_search_medientyp) != null) {
            view.findViewById(R.id.btn_search_medientyp).setOnClickListener(v -> {
                String[] types = {"🖼️  Nur Fotos aus der Cloud", "🎥  Nur Videos aus der Cloud"};
                new AlertDialog.Builder(getContext())
                        .setTitle("Medientyp suchen")
                        .setItems(types, (dialog, which) -> {
                            if (which == 0) performCloudMetadataSearch("{\"type\": \"IMAGE\", \"withExif\": true, \"size\": 1000}", "Lade Fotos aus der Cloud...");
                            if (which == 1) performCloudMetadataSearch("{\"type\": \"VIDEO\", \"withExif\": true, \"size\": 1000}", "Lade Videos aus der Cloud...");
                        })
                        .setNegativeButton("Abbrechen", null).show();
            });
        }

        if (view.findViewById(R.id.btn_search_recent) != null) {
            view.findViewById(R.id.btn_search_recent).setOnClickListener(v -> {
                if (etSearchInput != null) etSearchInput.setText("");
                selectTab(tabFotos, "Fotos");
            });
        }
        if (view.findViewById(R.id.btn_search_videos) != null) {
            view.findViewById(R.id.btn_search_videos).setOnClickListener(v -> performCloudMetadataSearch("{\"type\": \"VIDEO\", \"withExif\": true, \"size\": 1000}", "Lade alle Videos aus der Cloud..."));
        }

        if (view.findViewById(R.id.btn_search_favorites) != null) {
            view.findViewById(R.id.btn_search_favorites).setOnClickListener(v -> {
                fetchSpecialAssets("{\"isFavorite\": true, \"withExif\": true, \"withArchived\": true, \"size\": 1000}", "Lade deine Cloud-Favoriten...", 0);
            });
        }

        if (view.findViewById(R.id.btn_search_archive) != null) {
            view.findViewById(R.id.btn_search_archive).setOnClickListener(v -> {
                fetchSpecialAssets("{\"visibility\":\"archive\",\"withArchived\":true,\"withExif\":true,\"size\":1000}", "Lade dein Archiv...", 1);
            });
        }

        if (view.findViewById(R.id.btn_search_locked) != null) {
            view.findViewById(R.id.btn_search_locked).setOnClickListener(v -> promptForLockedFolder(true, false, null));
        }
    }

    private void fetchSpecialAssets(String jsonBody, String loadingMessage, int mode) {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;

        prepareForSearchResults();
        recyclerViewSearch.setVisibility(View.GONE);
        tvSearchLoading.setVisibility(View.VISIBLE);
        tvSearchLoading.setText(loadingMessage);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;

                URL url = new URL(cleanBaseUrl + "/api/search/metadata");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                conn.getOutputStream().write(jsonBody.getBytes("UTF-8"));

                int responseCode = conn.getResponseCode();

                InputStream inputStream = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder responseBuilder = new StringBuilder();
                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) responseBuilder.append(line);
                    reader.close();
                }
                String responseText = responseBuilder.toString();

                if (responseCode == 200 || responseCode == 201) {
                    JsonElement root = JsonParser.parseString(responseText);
                    final List<ImmichAsset> finalAssetList = parseAssetsFromJson(root);

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            extractMetadataToCache(finalAssetList);

                            List<ImmichAsset> filteredList = new ArrayList<>();
                            for (ImmichAsset a : finalAssetList) {
                                String d = a.description;
                                if (d == null && a.exifInfo != null) d = a.exifInfo.description;
                                if (d == null && a.exifInfo != null) d = a.exifInfo.imageDescription;

                                boolean isLocked = (d != null && d.toLowerCase().contains("#locked"));
                                boolean isArchived = (a.isArchived != null && a.isArchived);
                                boolean isFav = (a.isFavorite != null && a.isFavorite);

                                if (mode == 1) {
                                    if (isArchived && !isLocked) filteredList.add(a);
                                } else if (mode == 2) {
                                    if (isLocked) filteredList.add(a);
                                } else if (mode == 0) {
                                    if (isFav && !isLocked && !isArchived) filteredList.add(a);
                                }
                            }

                            if (filteredList.isEmpty()) {
                                showSearchError(mode == 2 ? "Dein Tresor ist leer." : (mode == 1 ? "Dein Archiv ist leer." : "Keine Bilder gefunden."));
                                return;
                            }

                            lastSearchMode = mode;
                            lastSearchResults = new ArrayList<>(filteredList);

                            tvSearchLoading.setVisibility(View.GONE);
                            recyclerViewSearch.setVisibility(View.VISIBLE);
                            processAndDisplayAssets(filteredList, cleanBaseUrl, currentApiKey, recyclerViewSearch);
                        });
                    }
                } else {
                    showSearchError("Fehler vom Server (" + responseCode + "):\n" + responseText);
                }
            } catch (Exception e) {
                showSearchError("Verbindungsfehler zur Cloud: " + e.getMessage());
            }
        });
    }

    private void fetchAvailableYearsFromServer(AutoCompleteTextView actvYear) {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            HashSet<String> yearsSet = new HashSet<>();
            boolean success = false;

            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                URL url = new URL(cleanBaseUrl + "/api/timeline/buckets?size=MONTH");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");

                int responseCode = conn.getResponseCode();

                if (responseCode == 404 || responseCode == 405) {
                    url = new URL(cleanBaseUrl + "/api/asset/time-buckets");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("x-api-key", currentApiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    responseCode = conn.getResponseCode();
                }

                if (responseCode == 200 || responseCode == 201) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    JsonElement jsonElement = JsonParser.parseString(response.toString());
                    if (jsonElement.isJsonArray()) {
                        JsonArray buckets = jsonElement.getAsJsonArray();
                        for (JsonElement bucket : buckets) {
                            if (bucket.isJsonObject() && bucket.getAsJsonObject().has("timeBucket")) {
                                String tb = bucket.getAsJsonObject().get("timeBucket").getAsString();
                                if (tb != null && tb.length() >= 4) {
                                    yearsSet.add(tb.substring(0, 4));
                                }
                            }
                        }
                        success = true;
                    }
                }
            } catch (Exception e) {
            }

            if (!success || yearsSet.isEmpty()) {
                Calendar calendar = Calendar.getInstance();
                int currentYear = calendar.get(Calendar.YEAR);
                for (int i = currentYear; i >= 2000; i--) {
                    yearsSet.add(String.valueOf(i));
                }
            }

            List<String> yearsList = new ArrayList<>(yearsSet);
            Collections.sort(yearsList, Collections.reverseOrder());

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    ArrayAdapter<String> yearAdapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_dropdown_item_1line, yearsList) {
                        @NonNull @Override public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                            View view = super.getView(position, convertView, parent);
                            ((TextView) view).setTextColor(Color.parseColor("#333333"));
                            return view;
                        }
                    };
                    actvYear.setAdapter(yearAdapter);
                    actvYear.setHint("📆  Im Jahr (auswählen)");
                });
            }
        });
    }

    private void showDateSearchBottomSheet() {
        if (getContext() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_list, null);
        sheetView.setBackgroundTintList(ColorStateList.valueOf(getThemeColor()));
        dialog.setContentView(sheetView);

        View bottomSheetInternal = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheetInternal != null) bottomSheetInternal.setBackgroundResource(android.R.color.transparent);

        TextView tvTitle = sheetView.findViewById(R.id.tv_bs_title);
        LinearLayout container = sheetView.findViewById(R.id.layout_bs_container);

        tvTitle.setText("Datum filtern");

        final String[] selectedMode = {""};

        TextView optLastMonth = createBottomSheetOption("📅  Letzter Monat");
        TextView optLast3Months = createBottomSheetOption("📅  Letzte 3 Monate");
        TextView optLast9Months = createBottomSheetOption("📅  Letzte 9 Monate");

        container.addView(optLastMonth);
        container.addView(optLast3Months);
        container.addView(optLast9Months);

        AutoCompleteTextView actvYear = new AutoCompleteTextView(getContext());
        actvYear.setHint("⏳  Lade Jahre aus der Cloud...");
        actvYear.setPadding(40, 40, 40, 40);
        actvYear.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.bg_pill_selected));
        actvYear.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        actvYear.setTextColor(Color.parseColor("#333333"));
        actvYear.setHintTextColor(Color.parseColor("#888888"));
        actvYear.setTextSize(16f);
        actvYear.setTypeface(null, Typeface.BOLD);
        actvYear.setElevation(4f);
        actvYear.setInputType(InputType.TYPE_NULL);

        LinearLayout.LayoutParams paramsYear = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        paramsYear.setMargins(0, 0, 0, 24);
        actvYear.setLayoutParams(paramsYear);
        container.addView(actvYear);

        fetchAvailableYearsFromServer(actvYear);

        EditText etCustom = new EditText(getContext());
        etCustom.setHint("✏️  Benutzerdefiniert (z.B. Sommer 2023)");
        etCustom.setPadding(40, 40, 40, 40);
        etCustom.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.bg_pill_selected));
        etCustom.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        etCustom.setTextColor(Color.parseColor("#333333"));
        etCustom.setHintTextColor(Color.parseColor("#888888"));
        etCustom.setTextSize(16f);
        etCustom.setTypeface(null, Typeface.BOLD);
        etCustom.setElevation(4f);

        LinearLayout.LayoutParams paramsCustom = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        paramsCustom.setMargins(0, 0, 0, 32);
        etCustom.setLayoutParams(paramsCustom);
        container.addView(etCustom);

        MaterialButton btnApply = new MaterialButton(getContext());
        btnApply.setText("Fertig");
        btnApply.setTextColor(Color.WHITE);
        btnApply.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getPillHighlightColor()));
        btnApply.setCornerRadius(60);

        LinearLayout.LayoutParams paramsBtn = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        paramsBtn.setMargins(0, 16, 0, 0);
        btnApply.setLayoutParams(paramsBtn);
        container.addView(btnApply);

        View.OnClickListener optionClickListener = v -> {
            optLastMonth.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
            optLast3Months.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
            optLast9Months.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
            optLastMonth.setTextColor(Color.parseColor("#333333"));
            optLast3Months.setTextColor(Color.parseColor("#333333"));
            optLast9Months.setTextColor(Color.parseColor("#333333"));

            v.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getPillHighlightColor()));
            ((TextView)v).setTextColor(Color.WHITE);

            if (v == optLastMonth) selectedMode[0] = "last_1";
            if (v == optLast3Months) selectedMode[0] = "last_3";
            if (v == optLast9Months) selectedMode[0] = "last_9";

            actvYear.setText("", false);
            etCustom.setText("");
            hideKeyboard();
        };

        optLastMonth.setOnClickListener(optionClickListener);
        optLast3Months.setOnClickListener(optionClickListener);
        optLast9Months.setOnClickListener(optionClickListener);

        actvYear.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) actvYear.showDropDown();
            return false;
        });

        actvYear.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!s.toString().isEmpty()) {
                    selectedMode[0] = "year";
                    optLastMonth.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
                    optLast3Months.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
                    optLast9Months.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
                    optLastMonth.setTextColor(Color.parseColor("#333333"));
                    optLast3Months.setTextColor(Color.parseColor("#333333"));
                    optLast9Months.setTextColor(Color.parseColor("#333333"));

                    etCustom.setOnFocusChangeListener(null);
                    etCustom.setText("");
                }
            }
        });

        etCustom.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!s.toString().isEmpty()) {
                    selectedMode[0] = "custom";
                    optLastMonth.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
                    optLast3Months.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
                    optLast9Months.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
                    optLastMonth.setTextColor(Color.parseColor("#333333"));
                    optLast3Months.setTextColor(Color.parseColor("#333333"));
                    optLast9Months.setTextColor(Color.parseColor("#333333"));

                    actvYear.setText("", false);
                }
            }
        });

        btnApply.setOnClickListener(v -> {
            String finalQuery = "";
            if (selectedMode[0].equals("last_1")) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.MONTH, -1);
                String lastMonth = new SimpleDateFormat("MMMM yyyy", Locale.GERMAN).format(cal.getTime());
                finalQuery = "aufgenommen im " + lastMonth;
            } else if (selectedMode[0].equals("last_3")) {
                finalQuery = "in den letzten 3 Monaten";
            } else if (selectedMode[0].equals("last_9")) {
                finalQuery = "in den letzten 9 Monaten";
            } else if (selectedMode[0].equals("year")) {
                finalQuery = "aufgenommen im Jahr " + actvYear.getText().toString();
            } else if (selectedMode[0].equals("custom")) {
                finalQuery = etCustom.getText().toString();
            }

            if (finalQuery.isEmpty()) {
                Toast.makeText(getContext(), "Bitte wähle einen Zeitraum aus.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedMode[0].equals("custom")) {
                if (etSearchInput != null) {
                    etSearchInput.setText(finalQuery);
                    etSearchInput.clearFocus();
                }
                hideKeyboard();
                performImmichSmartSearch(finalQuery);
                dialog.dismiss();
                return;
            }

            JsonObject json = new JsonObject();
            json.addProperty("withExif", true);
            json.addProperty("size", 1000);

            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            Calendar cal = Calendar.getInstance();

            if (selectedMode[0].equals("last_1")) {
                cal.add(Calendar.MONTH, -1);
                json.addProperty("takenAfter", iso.format(cal.getTime()));
            } else if (selectedMode[0].equals("last_3")) {
                cal.add(Calendar.MONTH, -3);
                json.addProperty("takenAfter", iso.format(cal.getTime()));
            } else if (selectedMode[0].equals("last_9")) {
                cal.add(Calendar.MONTH, -9);
                json.addProperty("takenAfter", iso.format(cal.getTime()));
            } else if (selectedMode[0].equals("year")) {
                String yearStr = actvYear.getText().toString().trim();
                json.addProperty("takenAfter", yearStr + "-01-01T00:00:00.000Z");
                json.addProperty("takenBefore", yearStr + "-12-31T23:59:59.999Z");
            }

            if (etSearchInput != null) {
                etSearchInput.setText(finalQuery);
                etSearchInput.clearFocus();
            }
            hideKeyboard();
            performCloudMetadataSearch(json.toString(), "Lade Bilder aus dem Zeitraum...");
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showLocationFilterBottomSheet() {
        if (getContext() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_location_filter, null);
        sheetView.setBackgroundTintList(ColorStateList.valueOf(getThemeColor()));
        dialog.setContentView(sheetView);

        View bottomSheetInternal = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheetInternal != null) bottomSheetInternal.setBackgroundResource(android.R.color.transparent);

        AutoCompleteTextView actvCountry = sheetView.findViewById(R.id.actv_country);
        AutoCompleteTextView actvState = sheetView.findViewById(R.id.actv_state);
        AutoCompleteTextView actvCity = sheetView.findViewById(R.id.actv_city);
        MaterialButton btnClear = sheetView.findViewById(R.id.btn_clear_location);
        MaterialButton btnApply = sheetView.findViewById(R.id.btn_apply_location);

        if (btnApply != null) {
            btnApply.setBackgroundTintList(ColorStateList.valueOf(getPillHighlightColor()));
        }

        fixDropdownLabel(actvCountry);
        fixDropdownLabel(actvState);
        fixDropdownLabel(actvCity);

        actvCountry.setInputType(InputType.TYPE_NULL);
        actvState.setInputType(InputType.TYPE_NULL);
        actvCity.setInputType(InputType.TYPE_NULL);

        View.OnTouchListener dropdownTouchListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                ((AutoCompleteTextView)v).showDropDown();
            }
            return false;
        };
        actvCountry.setOnTouchListener(dropdownTouchListener);
        actvState.setOnTouchListener(dropdownTouchListener);
        actvCity.setOnTouchListener(dropdownTouchListener);

        fetchLocationsForDropdowns(actvCountry, actvState, actvCity);

        btnClear.setOnClickListener(v -> {
            actvCountry.setText("", false);
            actvState.setText("", false);
            actvCity.setText("", false);
            actvCountry.clearFocus();
            actvState.clearFocus();
            actvCity.clearFocus();
            fetchLocationsForDropdowns(actvCountry, actvState, actvCity);
        });

        btnApply.setOnClickListener(v -> {
            String selectedCountryDe = actvCountry.getText().toString().trim();
            String selectedStateDe = actvState.getText().toString().trim();
            String selectedCityDe = actvCity.getText().toString().trim();

            String originalCountry = displayToOriginalLocationMap.getOrDefault(selectedCountryDe, selectedCountryDe);
            String originalState = displayToOriginalLocationMap.getOrDefault(selectedStateDe, selectedStateDe);
            String originalCity = displayToOriginalLocationMap.getOrDefault(selectedCityDe, selectedCityDe);

            hideKeyboard();
            applyCloudLocationFilter(originalCountry, originalState, originalCity);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void fixDropdownLabel(AutoCompleteTextView actv) {
        if (actv == null) return;
        ViewParent parent = actv.getParent();
        if (parent != null && parent.getParent() instanceof TextInputLayout) {
            ((TextInputLayout) parent.getParent()).setExpandedHintEnabled(false);
        } else if (parent instanceof TextInputLayout) {
            ((TextInputLayout) parent).setExpandedHintEnabled(false);
        }
    }

    private void fetchLocationsForDropdowns(AutoCompleteTextView actvCountry, AutoCompleteTextView actvState, AutoCompleteTextView actvCity) {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                actvCountry.setHint("⏳ Lade Orte...");
                actvState.setHint("⏳ Lade Orte...");
                actvCity.setHint("⏳ Lade Orte...");
            });
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                URL url = new URL(cleanBaseUrl + "/api/search/cities");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");

                int responseCode = conn.getResponseCode();

                if (responseCode == 200 || responseCode == 201) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    JsonElement root = JsonParser.parseString(response.toString());
                    if (root.isJsonArray()) {
                        JsonArray assets = root.getAsJsonArray();

                        HashMap<String, HashMap<String, HashSet<String>>> locationMap = new HashMap<>();
                        HashSet<String> allCountries = new HashSet<>();
                        HashSet<String> allStates = new HashSet<>();
                        HashSet<String> allCities = new HashSet<>();

                        for (JsonElement element : assets) {
                            if (element.isJsonObject()) {
                                JsonObject asset = element.getAsJsonObject();
                                if (asset.has("exifInfo") && !asset.get("exifInfo").isJsonNull()) {
                                    JsonObject exif = asset.getAsJsonObject("exifInfo");

                                    String origCountry = exif.has("country") && !exif.get("country").isJsonNull() ? exif.get("country").getAsString().trim() : "";
                                    String origState = exif.has("state") && !exif.get("state").isJsonNull() ? exif.get("state").getAsString().trim() : "";
                                    String origCity = exif.has("city") && !exif.get("city").isJsonNull() ? exif.get("city").getAsString().trim() : "";

                                    String country = translateLocationToGerman(origCountry);
                                    String state = translateLocationToGerman(origState);
                                    String city = translateLocationToGerman(origCity);

                                    if (!origCountry.isEmpty()) displayToOriginalLocationMap.put(country, origCountry);
                                    if (!origState.isEmpty()) displayToOriginalLocationMap.put(state, origState);
                                    if (!origCity.isEmpty()) displayToOriginalLocationMap.put(city, origCity);

                                    if (!country.isEmpty()) allCountries.add(country);
                                    if (!state.isEmpty()) allStates.add(state);
                                    if (!city.isEmpty()) allCities.add(city);

                                    if (!country.isEmpty()) {
                                        locationMap.putIfAbsent(country, new HashMap<>());
                                        if (!state.isEmpty()) {
                                            locationMap.get(country).putIfAbsent(state, new HashSet<>());
                                            if (!city.isEmpty()) {
                                                locationMap.get(country).get(state).add(city);
                                            }
                                        } else if (!city.isEmpty()) {
                                            locationMap.get(country).putIfAbsent("", new HashSet<>());
                                            locationMap.get(country).get("").add(city);
                                        }
                                    }
                                }
                            }
                        }

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                actvCountry.setHint("🌍 Land");
                                actvState.setHint("🗺️ Bundesland");
                                actvCity.setHint("🏙️ Stadt");
                                setupLocationDropdowns(locationMap, allCountries, allStates, allCities, actvCountry, actvState, actvCity);
                            });
                        }
                    }
                } else {
                    if (getActivity() != null) getActivity().runOnUiThread(() -> {
                        actvCountry.setHint("❌ Fehler (" + responseCode + ")");
                        actvState.setHint("🗺️ Bundesland");
                        actvCity.setHint("🏙️ Stadt");
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    actvCountry.setHint("❌ Verbindungsfehler");
                    actvState.setHint("🗺️ Bundesland");
                    actvCity.setHint("🏙️ Stadt");
                });
            }
        });
    }

    private void setupLocationDropdowns(
            HashMap<String, HashMap<String, HashSet<String>>> locationMap,
            HashSet<String> allCountries, HashSet<String> allStates, HashSet<String> allCities,
            AutoCompleteTextView actvCountry, AutoCompleteTextView actvState, AutoCompleteTextView actvCity) {

        if (getContext() == null) return;

        List<String> countryList = new ArrayList<>(allCountries);
        Collections.sort(countryList);
        List<String> stateList = new ArrayList<>(allStates);
        Collections.sort(stateList);
        List<String> cityList = new ArrayList<>(allCities);
        Collections.sort(cityList);

        ArrayAdapter<String> countryAdapter = getDarkTextAdapter(getContext(), countryList);
        ArrayAdapter<String> stateAdapter = getDarkTextAdapter(getContext(), stateList);
        ArrayAdapter<String> cityAdapter = getDarkTextAdapter(getContext(), cityList);

        actvCountry.setAdapter(countryAdapter);
        actvState.setAdapter(stateAdapter);
        actvCity.setAdapter(cityAdapter);

        actvCountry.setOnItemClickListener((parent, view, position, id) -> {
            String selectedCountry = countryAdapter.getItem(position);
            if (selectedCountry != null && locationMap.containsKey(selectedCountry)) {
                HashMap<String, HashSet<String>> statesMap = locationMap.get(selectedCountry);

                HashSet<String> filteredStatesSet = new HashSet<>(statesMap.keySet());
                filteredStatesSet.remove("");
                List<String> filteredStates = new ArrayList<>(filteredStatesSet);
                Collections.sort(filteredStates);
                actvState.setAdapter(getDarkTextAdapter(getContext(), filteredStates));

                HashSet<String> filteredCitiesSet = new HashSet<>();
                for (HashSet<String> cities : statesMap.values()) {
                    filteredCitiesSet.addAll(cities);
                }
                List<String> filteredCities = new ArrayList<>(filteredCitiesSet);
                Collections.sort(filteredCities);
                actvCity.setAdapter(getDarkTextAdapter(getContext(), filteredCities));

                actvState.setText("", false);
                actvCity.setText("", false);
            }
        });

        actvState.setOnItemClickListener((parent, view, position, id) -> {
            String selectedCountry = actvCountry.getText().toString();
            String selectedState = (String) parent.getItemAtPosition(position);

            if (!selectedCountry.isEmpty() && locationMap.containsKey(selectedCountry)) {
                HashSet<String> cities = locationMap.get(selectedCountry).get(selectedState);
                if (cities != null) {
                    List<String> filteredCities = new ArrayList<>(cities);
                    Collections.sort(filteredCities);
                    actvCity.setAdapter(getDarkTextAdapter(getContext(), filteredCities));
                    actvCity.setText("", false);
                }
            }
        });
    }

    private ArrayAdapter<String> getDarkTextAdapter(Context context, List<String> list) {
        return new ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, list) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ((TextView) view).setTextColor(Color.parseColor("#333333"));
                return view;
            }
        };
    }

    private String translateLocationToGerman(String englishName) {
        if (englishName == null || englishName.isEmpty()) return "";
        switch (englishName) {
            case "Germany": return "Deutschland";
            case "Switzerland": return "Schweiz";
            case "Austria": return "Österreich";
            case "Italy": return "Italien";
            case "Spain": return "Spanien";
            case "France": return "Frankreich";
            case "United States": return "USA";
            case "United Kingdom": return "Großbritannien";
            case "Netherlands": return "Niederlande";
            case "Belgium": return "Belgien";
            case "Poland": return "Polen";
            case "Czechia":
            case "Czech Republic": return "Tschechien";
            case "Croatia": return "Kroatien";
            case "Sweden": return "Schweden";
            case "Norway": return "Norwegen";
            case "Denmark": return "Dänemark";
            case "Greece": return "Griechenland";
            case "Bavaria": return "Bayern";
            case "Hesse": return "Hessen";
            case "Lower Saxony": return "Niedersachsen";
            case "North Rhine-Westphalia": return "Nordrhein-Westfalen";
            case "Rhineland-Palatinate": return "Rheinland-Pfalz";
            case "Saxony": return "Sachsen";
            case "Thuringia": return "Thüringen";
            case "Cologne": return "Köln";
            case "Munich": return "München";
            case "Nuremberg": return "Nürnberg";
            case "Vienna": return "Wien";
            case "Rome": return "Rom";
            case "Milan": return "Mailand";
            case "Venice": return "Venedig";
            case "Florence": return "Florenz";
            case "Prague": return "Prag";
            case "Warsaw": return "Warschau";
            case "Geneva": return "Genf";
            case "Zurich": return "Zürich";
            case "Lucerne": return "Luzern";
            default: return englishName;
        }
    }

    private void applyCloudLocationFilter(String country, String state, String city) {
        if (country.isEmpty() && state.isEmpty() && city.isEmpty()) {
            Toast.makeText(getContext(), "Bitte mindestens einen Ort eingeben.", Toast.LENGTH_SHORT).show();
            return;
        }

        JsonObject json = new JsonObject();
        if (!country.isEmpty()) json.addProperty("country", country);
        if (!state.isEmpty()) json.addProperty("state", state);
        if (!city.isEmpty()) json.addProperty("city", city);
        json.addProperty("withExif", true);
        json.addProperty("size", 1000);

        performCloudMetadataSearch(json.toString(), "Durchsuche Cloud nach Ort...");
    }

    private void showCloudSearchDialog(String title, String hint) {
        if (getContext() == null) return;
        final EditText input = new EditText(getContext());
        input.setHint(hint);
        input.setPadding(40, 40, 40, 40);

        new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setView(input)
                .setPositiveButton("Suchen", (dialog, which) -> {
                    String query = input.getText().toString().trim();
                    if (!query.isEmpty()) {
                        if (etSearchInput != null) {
                            etSearchInput.setText(query);
                            etSearchInput.clearFocus();
                        }
                        hideKeyboard();
                        performImmichSmartSearch(query);
                    }
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void performCloudMetadataSearch(String jsonBody, String loadingMessage) {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;

        prepareForSearchResults();
        recyclerViewSearch.setVisibility(View.GONE);
        tvSearchLoading.setVisibility(View.VISIBLE);
        tvSearchLoading.setText(loadingMessage);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                URL url = new URL(cleanBaseUrl + "/api/search/metadata");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                conn.getOutputStream().write(jsonBody.getBytes("UTF-8"));

                int responseCode = conn.getResponseCode();

                InputStream inputStream = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder responseBuilder = new StringBuilder();
                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) responseBuilder.append(line);
                    reader.close();
                }
                String responseText = responseBuilder.toString();

                if (responseCode == 200 || responseCode == 201) {
                    JsonElement jsonElement = JsonParser.parseString(responseText);
                    final List<ImmichAsset> finalAssetList = parseAssetsFromJson(jsonElement);

                    if (finalAssetList != null && !finalAssetList.isEmpty()) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                extractMetadataToCache(finalAssetList);

                                List<ImmichAsset> filteredList = new ArrayList<>();
                                for (ImmichAsset a : finalAssetList) {
                                    String d = a.description;
                                    if (d == null && a.exifInfo != null) d = a.exifInfo.description;
                                    if (d == null && a.exifInfo != null) d = a.exifInfo.imageDescription;

                                    boolean isLocked = (d != null && d.toLowerCase().contains("#locked"));
                                    boolean isArchived = (a.isArchived != null && a.isArchived);

                                    if (!isLocked && !isArchived) {
                                        filteredList.add(a);
                                    }
                                }

                                if (filteredList.isEmpty()) {
                                    showSearchError("Keine Ergebnisse gefunden.");
                                    return;
                                }

                                lastSearchMode = 3;
                                lastSearchResults = new ArrayList<>(filteredList);

                                tvSearchLoading.setVisibility(View.GONE);
                                recyclerViewSearch.setVisibility(View.VISIBLE);
                                processAndDisplayAssets(filteredList, cleanBaseUrl, currentApiKey, recyclerViewSearch);
                            });
                        }
                    } else {
                        showSearchError("Keine Ergebnisse in der Cloud gefunden.");
                    }
                } else {
                    showSearchError("Fehler vom Server (" + responseCode + "):\n" + responseText);
                }
            } catch (Exception e) {
                showSearchError("Verbindungsfehler zur Cloud: " + e.getMessage());
            }
        });
    }

    private void setupSearchLogic() {
        if (etSearchInput != null) {
            etSearchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (s.toString().trim().isEmpty()) {
                        recyclerViewSearch.setVisibility(View.GONE);
                        tvSearchLoading.setVisibility(View.GONE);
                        if (layoutSearchCategories != null) layoutSearchCategories.setVisibility(View.VISIBLE);
                        if (layoutSearchBottomButtons != null) layoutSearchBottomButtons.setVisibility(View.VISIBLE);
                    }
                }
            });

            etSearchInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    String query = etSearchInput.getText().toString().trim();
                    if (!query.isEmpty()) performImmichSmartSearch(query);
                    hideKeyboard();
                    return true;
                }
                return false;
            });
        }
    }

    private void performImmichSmartSearch(String query) {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;

        prepareForSearchResults();
        recyclerViewSearch.setVisibility(View.GONE);
        tvSearchLoading.setVisibility(View.VISIBLE);
        tvSearchLoading.setText("KI durchsucht Cloud...");

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                URL url = new URL(cleanBaseUrl + "/api/search/smart");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonBody = "{\"query\": \"" + query + "\", \"q\": \"" + query + "\", \"withExif\": true}";
                conn.getOutputStream().write(jsonBody.getBytes("UTF-8"));

                int responseCode = conn.getResponseCode();

                InputStream inputStream = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder responseBuilder = new StringBuilder();
                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) responseBuilder.append(line);
                    reader.close();
                }
                String responseText = responseBuilder.toString();

                if (responseCode == 200 || responseCode == 201) {
                    JsonElement jsonElement = JsonParser.parseString(responseText);
                    final List<ImmichAsset> finalAssetList = parseAssetsFromJson(jsonElement);

                    if (finalAssetList != null && !finalAssetList.isEmpty()) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                extractMetadataToCache(finalAssetList);
                                tvSearchLoading.setVisibility(View.GONE);
                                recyclerViewSearch.setVisibility(View.VISIBLE);

                                lastSearchMode = 4; // 4 = Smart Search
                                lastSearchResults = new ArrayList<>(finalAssetList);

                                processAndDisplaySmartSearchResults(finalAssetList, cleanBaseUrl, currentApiKey, recyclerViewSearch);
                            });
                        }
                    } else {
                        showSearchError("Die KI hat keine Treffer gefunden.");
                    }
                } else {
                    showSearchError("Fehler vom Server (" + responseCode + "):\n" + responseText);
                }
            } catch (Exception e) {
                showSearchError("Verbindungsfehler zur Cloud: " + e.getMessage());
            }
        });
    }

    private void processAndDisplaySmartSearchResults(List<ImmichAsset> rawListToProcess, String baseUrl, String apiKey, RecyclerView targetRecyclerView) {
        if (getContext() == null || rawListToProcess == null || targetRecyclerView == null) return;

        Parcelable recyclerViewState = null;
        if (targetRecyclerView.getLayoutManager() != null) {
            recyclerViewState = targetRecyclerView.getLayoutManager().onSaveInstanceState();
        }

        List<GalleryItem> relevanceItems = new ArrayList<>();

        for (ImmichAsset asset : rawListToProcess) {
            boolean isLocked = (asset.description != null && asset.description.toLowerCase().contains("#locked")) ||
                    (asset.exifInfo != null && asset.exifInfo.description != null && asset.exifInfo.description.toLowerCase().contains("#locked")) ||
                    (asset.exifInfo != null && asset.exifInfo.imageDescription != null && asset.exifInfo.imageDescription.toLowerCase().contains("#locked"));
            boolean isArchived = (asset.isArchived != null && asset.isArchived);

            if (!isLocked && !isArchived) {
                relevanceItems.add(new GalleryItem(GalleryItem.TYPE_PHOTO, "", asset));
            }
        }

        currentFotosAdapter = new FotosAdapter(getContext(), relevanceItems, baseUrl, apiKey,
                clickedAsset -> {
                    int index = rawListToProcess.indexOf(clickedAsset);
                    openFullscreen(rawListToProcess, index, false);
                },
                selectedCount -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (selectedCount > 0) {
                                if (layoutSelectionBar != null) layoutSelectionBar.setVisibility(View.VISIBLE);
                                if (layoutSelectionBottomBar != null) layoutSelectionBottomBar.setVisibility(View.VISIBLE);
                                if (tvSelectionCount != null) tvSelectionCount.setText(selectedCount + (selectedCount == 1 ? " ausgewählt" : " ausgewählt"));
                                updateSelectionBottomBarUI();
                            } else {
                                if (layoutSelectionBar != null) layoutSelectionBar.setVisibility(View.GONE);
                                if (layoutSelectionBottomBar != null) layoutSelectionBottomBar.setVisibility(View.GONE);
                            }
                        });
                    }
                }
        );

        if (targetRecyclerView.getAdapter() != null && !(targetRecyclerView.getAdapter() instanceof FotosAdapter)) {
            targetRecyclerView.getRecycledViewPool().clear();
            targetRecyclerView.setAdapter(currentFotosAdapter);
        } else {
            targetRecyclerView.setAdapter(currentFotosAdapter);
        }

        if (recyclerViewState != null) {
            targetRecyclerView.getLayoutManager().onRestoreInstanceState(recyclerViewState);
        }
    }

    private void fetchPeopleFromImmich() {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;

        prepareForSearchResults();
        recyclerViewSearch.setVisibility(View.GONE);
        tvSearchLoading.setVisibility(View.VISIBLE);
        tvSearchLoading.setText("Lade Personen aus der Cloud...");

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                URL url = new URL(cleanBaseUrl + "/api/people");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");

                int responseCode = conn.getResponseCode();
                if (responseCode == 404 || responseCode == 405) {
                    url = new URL(cleanBaseUrl + "/api/person");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("x-api-key", currentApiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    responseCode = conn.getResponseCode();
                }

                if (responseCode == 200 || responseCode == 201) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    JsonElement root = JsonParser.parseString(response.toString());
                    JsonArray peopleArray = null;
                    if (root.isJsonArray()) peopleArray = root.getAsJsonArray();
                    else if (root.isJsonObject()) {
                        if (root.getAsJsonObject().has("people")) peopleArray = root.getAsJsonObject().getAsJsonArray("people");
                        else if (root.getAsJsonObject().has("data")) peopleArray = root.getAsJsonObject().getAsJsonArray("data");
                    }

                    if (peopleArray != null && peopleArray.size() > 0) {
                        List<ImmichPerson> personList = new ArrayList<>();
                        for (JsonElement element : peopleArray) {
                            JsonObject pObj = element.getAsJsonObject();
                            if (pObj.has("isHidden") && pObj.get("isHidden").getAsBoolean()) continue;
                            ImmichPerson person = new ImmichPerson();
                            person.id = pObj.get("id").getAsString();
                            person.name = pObj.has("name") && !pObj.get("name").isJsonNull() ? pObj.get("name").getAsString() : "";
                            personList.add(person);
                        }

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (personList.isEmpty()) {
                                    showSearchError("Keine sichtbaren Gesichter gefunden.");
                                    return;
                                }
                                tvSearchLoading.setVisibility(View.GONE);
                                recyclerViewSearch.setVisibility(View.VISIBLE);
                                PersonAdapter pAdapter = new PersonAdapter(getContext(), personList, cleanBaseUrl, currentApiKey, new PersonAdapter.PersonListener() {
                                    @Override public void onFaceClick(ImmichPerson person) {
                                        fetchAssetsForPerson(person.id, (person.name == null || person.name.isEmpty()) ? "diese Person" : person.name);
                                    }
                                    @Override public void onNameEditClick(ImmichPerson person) {
                                        showRenameDialog(person, personList);
                                    }
                                });
                                recyclerViewSearch.setAdapter(pAdapter);
                            });
                        }
                    } else {
                        showSearchError("Noch keine Personen von der KI indexiert.");
                    }
                } else {
                    showSearchError("Fehler beim Laden (Code: " + responseCode + ")");
                }
            } catch (Exception e) {
                showSearchError("Verbindungsfehler zur Personen-API.");
            }
        });
    }

    private void showRenameDialog(ImmichPerson person, List<ImmichPerson> currentList) {
        if (getContext() == null) return;
        final EditText input = new EditText(getContext());
        input.setText(person.name);
        input.setHint("Neuer Name (z.B. Josua)");
        input.setPadding(32, 32, 32, 32);

        new AlertDialog.Builder(getContext())
                .setTitle("Person benennen")
                .setView(input)
                .setPositiveButton("Speichern", (dialog, which) -> updatePersonNameOnServer(person, input.getText().toString().trim(), currentList))
                .setNegativeButton("Abbrechen", null).show();
    }

    private void updatePersonNameOnServer(ImmichPerson person, String newName, List<ImmichPerson> currentList) {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                URL url = new URL(cleanBaseUrl + "/api/people/" + person.id);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.getOutputStream().write(("{\"name\": \"" + newName + "\"}").getBytes());

                int responseCode = conn.getResponseCode();
                if (responseCode == 404 || responseCode == 405) {
                    url = new URL(cleanBaseUrl + "/api/person/" + person.id);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("PUT");
                    conn.setRequestProperty("x-api-key", currentApiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.getOutputStream().write(("{\"name\": \"" + newName + "\"}").getBytes());
                    responseCode = conn.getResponseCode();
                }

                if (responseCode == 200 || responseCode == 201) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            person.name = newName;
                            if (recyclerViewSearch.getAdapter() != null) recyclerViewSearch.getAdapter().notifyDataSetChanged();
                            Toast.makeText(getContext(), "Name gespeichert!", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    if (getActivity() != null) getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Speichern fehlgeschlagen", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Verbindungsfehler beim Speichern.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void fetchAssetsForPerson(String personId, String personName) {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;

        prepareForSearchResults();
        recyclerViewSearch.setVisibility(View.GONE);
        tvSearchLoading.setVisibility(View.VISIBLE);
        tvSearchLoading.setText("Lade Bilder von " + personName + "...");

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                URL url = new URL(cleanBaseUrl + "/api/search/metadata");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                conn.getOutputStream().write(("{\"personIds\": [\"" + personId + "\"], \"withExif\": true}").getBytes());

                int responseCode = conn.getResponseCode();
                if (responseCode == 200 || responseCode == 201) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    JsonElement jsonElement = JsonParser.parseString(response.toString());
                    final List<ImmichAsset> finalAssetList = parseAssetsFromJson(jsonElement);

                    if (finalAssetList != null && !finalAssetList.isEmpty()) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                extractMetadataToCache(finalAssetList);

                                List<ImmichAsset> filteredList = new ArrayList<>();
                                for (ImmichAsset a : finalAssetList) {
                                    String d = a.description;
                                    if (d == null && a.exifInfo != null) d = a.exifInfo.description;
                                    if (d == null && a.exifInfo != null) d = a.exifInfo.imageDescription;

                                    boolean isLocked = (d != null && d.toLowerCase().contains("#locked"));
                                    if (!isLocked) filteredList.add(a);
                                }

                                if (filteredList.isEmpty()) {
                                    showSearchError("Keine Bilder für " + personName + " gefunden.");
                                    return;
                                }

                                lastSearchMode = 3;
                                lastSearchResults = new ArrayList<>(filteredList);

                                tvSearchLoading.setVisibility(View.GONE);
                                recyclerViewSearch.setVisibility(View.VISIBLE);
                                processAndDisplayAssets(filteredList, cleanBaseUrl, currentApiKey, recyclerViewSearch);
                            });
                        }
                    } else {
                        showSearchError("Keine Bilder für " + personName + " gefunden.");
                    }
                } else {
                    showSearchError("Fehler beim Laden (" + responseCode + ")");
                }
            } catch (Exception e) {
                showSearchError("Fehler beim Laden der Person.");
            }
        });
    }

    private void setupRecyclerView(RecyclerView rv, boolean addScrollListener) {
        if (rv == null) return;
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 3);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (rv.getAdapter() != null) {
                    if (rv.getAdapter() instanceof PersonAdapter) return 1;
                    int type = rv.getAdapter().getItemViewType(position);
                    if (type == GalleryItem.TYPE_MONTH_HEADER || type == GalleryItem.TYPE_DAY_HEADER) return 3;
                }
                return 1;
            }
        });
        rv.setLayoutManager(layoutManager);

        if (addScrollListener) {
            rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (dy > 0 && !isLoading && !isLastPage) {
                        if (layoutManager != null) {
                            int visibleItemCount = layoutManager.getChildCount();
                            int totalItemCount = layoutManager.getItemCount();
                            int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                            if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 30) {
                                currentPage++;
                                fetchPhotosFromImmich(currentApiUrl, currentApiKey, currentPage);
                            }
                        }
                    }
                }
            });
        }
    }

    private void setupActionButtons(View view) {
        if (btnFavorite != null) btnFavorite.setOnClickListener(v -> toggleFavoriteStatus());

        btnMoreOptions = view.findViewById(R.id.btn_more_options);
        if (btnMoreOptions != null) btnMoreOptions.setOnClickListener(v -> {
            if (getContext() == null || currentViewedAsset == null) return;
            PopupMenu popup = new PopupMenu(getContext(), v);
            popup.getMenu().add("Details einblenden");
            popup.getMenu().add("An Echo senden");

            if (currentViewedAlbumId != null) {
                popup.getMenu().add("Aus Album entfernen");
            }

            popup.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();
                if (title.equals("Details einblenden") && bottomSheetBehavior != null) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
                else if (title.equals("An Echo senden")) {
                    sendToEcho(currentViewedAsset);
                }
                else if (title.equals("Aus Album entfernen")) {
                    removeAssetFromAlbumServer(currentViewedAsset, currentViewedAlbumId);
                }
                return true;
            });
            popup.show();
        });

        btnShareFullscreen = view.findViewById(R.id.btn_share);
        btnEditFullscreen = view.findViewById(R.id.btn_edit);
        btnAddToFullscreen = view.findViewById(R.id.btn_add_to);
        btnDeleteFullscreen = view.findViewById(R.id.btn_delete);

        if (btnShareFullscreen != null) {
            bottomMenuFullscreen = (View) btnShareFullscreen.getParent();

            LinearLayout bottomMenu = (LinearLayout) btnShareFullscreen.getParent();
            btnUploadLocal = new MaterialButton(getContext());
            btnUploadLocal.setText("☁️ Jetzt hochladen");
            btnUploadLocal.setTextColor(Color.WHITE);
            btnUploadLocal.setBackgroundTintList(ColorStateList.valueOf(getPillHighlightColor()));
            btnUploadLocal.setCornerRadius(60);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            params.setMargins(64, 0, 64, 0);
            btnUploadLocal.setLayoutParams(params);
            btnUploadLocal.setVisibility(View.GONE);
            btnUploadLocal.setOnClickListener(v -> uploadSingleAsset(currentViewedAsset));
            bottomMenu.addView(btnUploadLocal);
        }

        if (btnShareFullscreen != null) btnShareFullscreen.setOnClickListener(v -> {
            if (currentViewedAsset == null || getContext() == null) return;
            List<ImmichAsset> singleList = new ArrayList<>();
            singleList.add(currentViewedAsset);
            shareAssets(singleList);
        });

        if (btnEditFullscreen != null) btnEditFullscreen.setOnClickListener(v -> editCurrentAsset());

        if (btnAddToFullscreen != null) btnAddToFullscreen.setOnClickListener(v -> {
            if (currentViewedAsset == null) return;
            List<ImmichAsset> singleList = new ArrayList<>();
            singleList.add(currentViewedAsset);
            showActionMenu(singleList);
        });

        if (btnDeleteFullscreen != null) btnDeleteFullscreen.setOnClickListener(v -> {
            if (currentViewedAsset == null) return;
            List<ImmichAsset> singleList = new ArrayList<>();
            singleList.add(currentViewedAsset);
            deleteAssets(singleList);
        });
    }

    private void removeAssetFromAlbumServer(ImmichAsset asset, String albumId) {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;
        Toast.makeText(getContext(), "Wird aus Album entfernt...", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection conn = null;
            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                URL url = new URL(cleanBaseUrl + "/api/albums/" + albumId + "/assets");

                String jsonAssetIds = "{\"assetIds\": [\"" + asset.id + "\"]}";
                String jsonIds = "{\"ids\": [\"" + asset.id + "\"]}";

                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.getOutputStream().write(jsonAssetIds.getBytes("UTF-8"));

                int responseCode = conn.getResponseCode();

                if (responseCode == 400) {
                    url = new URL(cleanBaseUrl + "/api/albums/" + albumId + "/assets");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("DELETE");
                    conn.setRequestProperty("x-api-key", currentApiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.getOutputStream().write(jsonIds.getBytes("UTF-8"));
                    responseCode = conn.getResponseCode();
                }

                if (responseCode == 404 || responseCode == 405) {
                    url = new URL(cleanBaseUrl + "/api/album/" + albumId + "/assets");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("DELETE");
                    conn.setRequestProperty("x-api-key", currentApiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.getOutputStream().write(jsonAssetIds.getBytes("UTF-8"));
                    responseCode = conn.getResponseCode();
                }

                String errorResponse = "";
                if (responseCode >= 400) {
                    try {
                        InputStream es = conn.getErrorStream();
                        if (es != null) {
                            BufferedReader er = new BufferedReader(new InputStreamReader(es));
                            StringBuilder errBuilder = new StringBuilder();
                            String l;
                            while ((l = er.readLine()) != null) errBuilder.append(l);
                            errorResponse = errBuilder.toString();
                            er.close();
                        }
                    } catch (Exception e) {}
                }

                int finalResponseCode = responseCode;
                String finalErrorResponse = errorResponse;

                if (responseCode == 200 || responseCode == 201 || responseCode == 204) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (lastSearchResults != null) lastSearchResults.remove(asset);
                            refreshVisibleGrids();
                            closeFullscreen();
                            Toast.makeText(getContext(), "Aus Album entfernt!", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (finalErrorResponse.isEmpty()) {
                                Toast.makeText(getContext(), "Fehler beim Entfernen (" + finalResponseCode + ")", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "Fehler (" + finalResponseCode + "): " + finalErrorResponse, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            } catch (Exception e) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Netzwerkfehler", Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void editCurrentAsset() {
        if (currentViewedAsset == null || getContext() == null) return;

        if (currentViewedAsset.isLocalOnly) {
            Toast.makeText(getContext(), "Dieses Bild wurde noch nicht hochgeladen (☁️). Bitte bearbeite es in der Samsung Galerie.", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(getContext(), "Lade in Editor...", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                String downloadUrl = cleanBaseUrl + "/api/assets/" + currentViewedAsset.id + "/original";

                boolean isVideo = currentViewedAsset.type != null && currentViewedAsset.type.equals("VIDEO");
                String fileExtension = isVideo ? ".mp4" : ".jpg";
                String mimeType = isVideo ? "video/*" : "image/*";

                File sharedImagesDir = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "shared_images");
                if (!sharedImagesDir.exists()) sharedImagesDir.mkdirs();
                File fileToEdit = new File(sharedImagesDir, "edit_" + currentViewedAsset.id + fileExtension);

                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.connect();

                if (conn.getResponseCode() == 200 || conn.getResponseCode() == 201) {
                    InputStream in = conn.getInputStream();
                    OutputStream out = new FileOutputStream(fileToEdit);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    in.close();
                    out.close();

                    Uri contentUri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", fileToEdit);

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Intent editIntent = new Intent(Intent.ACTION_EDIT);
                            editIntent.setDataAndType(contentUri, mimeType);
                            editIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            editIntent.setClipData(ClipData.newRawUri("", contentUri));

                            startActivity(Intent.createChooser(editIntent, "Bearbeiten mit..."));
                        });
                    }
                } else {
                    if (getActivity() != null) getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Download fehlgeschlagen", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Fehler beim Vorbereiten.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private TextView createBottomSheetOption(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(16f);
        tv.setTextColor(Color.parseColor("#333333"));
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(40, 40, 40, 40);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 24);
        tv.setLayoutParams(params);

        tv.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.bg_pill_selected));
        tv.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        tv.setElevation(4f);

        return tv;
    }

    private void loadAndShowAlbumsInBottomSheet(BottomSheetDialog dialog, TextView tvTitle, LinearLayout container, List<ImmichAsset> assetsToModify) {
        tvTitle.setText("Lade Alben...");
        container.removeAllViews();

        TextView loading = new TextView(getContext());
        loading.setText("Cloud wird durchsucht...");
        loading.setGravity(android.view.Gravity.CENTER);
        loading.setPadding(0, 50, 0, 50);
        container.addView(loading);

        Executors.newSingleThreadExecutor().execute(() -> {
            if (globalAlbumList != null && !globalAlbumList.isEmpty()) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> populateAlbumsInBottomSheet(dialog, tvTitle, container, assetsToModify));
                return;
            }

            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                URL url = new URL(cleanBaseUrl + "/api/albums");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");

                int responseCode = conn.getResponseCode();
                if (responseCode == 404 || responseCode == 405) {
                    url = new URL(cleanBaseUrl + "/api/album");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("x-api-key", currentApiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    responseCode = conn.getResponseCode();
                }

                if (responseCode == 200 || responseCode == 201) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    JsonElement root = JsonParser.parseString(response.toString());
                    JsonArray albumsArray = null;

                    if (root.isJsonArray()) albumsArray = root.getAsJsonArray();
                    else if (root.isJsonObject()) {
                        if (root.getAsJsonObject().has("data")) albumsArray = root.getAsJsonObject().getAsJsonArray("data");
                        else if (root.getAsJsonObject().has("albums")) albumsArray = root.getAsJsonObject().getAsJsonArray("albums");
                    }

                    if (albumsArray != null) {
                        globalAlbumList = new Gson().fromJson(albumsArray, new TypeToken<List<ImmichAlbum>>(){}.getType());
                    }

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            tvAlbumsLoading.setVisibility(View.GONE);
                            if (globalAlbumList != null && !globalAlbumList.isEmpty()) {
                                recyclerViewAlbums.setVisibility(View.VISIBLE);
                                displayAlbums(globalAlbumList);
                            } else {
                                Toast.makeText(getContext(), "Du hast noch keine Alben erstellt.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } else {
                    if (getActivity() != null) {
                        int finalResponseCode = responseCode;
                        getActivity().runOnUiThread(() -> {
                            tvAlbumsLoading.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Fehler beim Laden der Alben (" + finalResponseCode + ")", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            } catch (Exception e) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    tvAlbumsLoading.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Verbindungsfehler zu Alben.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void populateAlbumsInBottomSheet(BottomSheetDialog dialog, TextView tvTitle, LinearLayout container, List<ImmichAsset> assetsToModify) {
        tvTitle.setText("Zu welchem Album?");
        container.removeAllViews();

        for (ImmichAlbum album : globalAlbumList) {
            TextView opt = createBottomSheetOption("📂  " + (album.albumName != null ? album.albumName : "Unbenannt"));
            opt.setOnClickListener(v -> {
                addAssetsToAlbumOnServer(album.id, assetsToModify);
                dialog.dismiss();
            });
            container.addView(opt);
        }
    }

    private void addAssetsToAlbumOnServer(String albumId, List<ImmichAsset> assetsToModify) {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;
        Toast.makeText(getContext(), "Wird hinzugefügt...", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                URL url = new URL(cleanBaseUrl + "/api/albums/" + albumId + "/assets");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                StringBuilder ids = new StringBuilder();
                for (int i = 0; i < assetsToModify.size(); i++) {
                    ids.append("\"").append(assetsToModify.get(i).id).append("\"");
                    if (i < assetsToModify.size() - 1) ids.append(",");
                }

                String json = "{\"assetIds\": [" + ids.toString() + "], \"ids\": [" + ids.toString() + "]}";

                conn.getOutputStream().write(json.getBytes("UTF-8"));

                int responseCode = conn.getResponseCode();

                if (responseCode == 400) {
                    url = new URL(cleanBaseUrl + "/api/albums/" + albumId + "/assets");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("PUT");
                    conn.setRequestProperty("x-api-key", currentApiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    String jsonAlternative = "{\"assetIds\": [" + ids.toString() + "]}";
                    conn.getOutputStream().write(jsonAlternative.getBytes("UTF-8"));
                    responseCode = conn.getResponseCode();
                }

                if (responseCode == 404 || responseCode == 405) {
                    url = new URL(cleanBaseUrl + "/api/album/" + albumId + "/assets");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("PUT");
                    conn.setRequestProperty("x-api-key", currentApiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.getOutputStream().write(json.getBytes("UTF-8"));
                    responseCode = conn.getResponseCode();
                }

                String errorResponse = "";
                if (responseCode >= 400) {
                    try {
                        InputStream es = conn.getErrorStream();
                        if (es != null) {
                            BufferedReader er = new BufferedReader(new InputStreamReader(es));
                            StringBuilder errBuilder = new StringBuilder();
                            String l;
                            while ((l = er.readLine()) != null) errBuilder.append(l);
                            errorResponse = errBuilder.toString();
                            er.close();
                        }
                    } catch (Exception e) {}
                }

                int finalResponseCode = responseCode;
                String finalErrorResponse = errorResponse;

                if (responseCode == 200 || responseCode == 201 || responseCode == 204) {
                    if (getActivity() != null) getActivity().runOnUiThread(() -> {
                        if (currentFotosAdapter != null) currentFotosAdapter.clearSelection();
                        Toast.makeText(getContext(), "Erfolgreich zum Album hinzugefügt! 🎉", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (finalErrorResponse.isEmpty()) {
                                Toast.makeText(getContext(), "Fehler beim Hinzufügen (" + finalResponseCode + ")", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "Fehler (" + finalResponseCode + "): " + finalErrorResponse, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            } catch (Exception e) {
                if (getActivity() != null) getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Netzwerkfehler beim Hinzufügen.", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void promptForLockedFolder(boolean isViewing, boolean isUnlocking, List<ImmichAsset> multipleAssets) {
        if (getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
        String savedPin = prefs.getString("locked_folder_pin", "");

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(savedPin.isEmpty() ? "Tresor einrichten" : "Tresor entsperren");
        builder.setMessage(savedPin.isEmpty() ? "Lege eine 4-stellige PIN fest, um deine Bilder zu schützen:" : "Bitte gib deine PIN ein:");

        final EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setPadding(50, 50, 50, 50);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String enteredPin = input.getText().toString();
            if (savedPin.isEmpty()) {
                if (enteredPin.length() >= 4) {
                    prefs.edit().putString("locked_folder_pin", enteredPin).apply();
                    Toast.makeText(getContext(), "PIN gespeichert!", Toast.LENGTH_SHORT).show();
                    if (isViewing) {
                        fetchSpecialAssets("{\"visibility\":\"archive\",\"withArchived\":true,\"withExif\":true,\"size\":1000}", "Tresor wird geöffnet...", 2);
                    } else {
                        toggleLockAssets(isUnlocking, multipleAssets);
                    }
                } else {
                    Toast.makeText(getContext(), "PIN muss mind. 4 Zeichen haben.", Toast.LENGTH_SHORT).show();
                }
            } else {
                if (enteredPin.equals(savedPin)) {
                    if (isViewing) {
                        fetchSpecialAssets("{\"visibility\":\"archive\",\"withArchived\":true,\"withExif\":true,\"size\":1000}", "Tresor wird geöffnet...", 2);
                    } else {
                        toggleLockAssets(isUnlocking, multipleAssets);
                    }
                } else {
                    Toast.makeText(getContext(), "Falsche PIN! 🚫", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Abbrechen", null);
        builder.show();
    }

    private void toggleLockAssets(boolean isUnlocking, List<ImmichAsset> assets) {
        if (assets == null || assets.isEmpty()) return;

        for (ImmichAsset asset : assets) {
            if (isUnlocking) {
                String currentDesc = asset.description != null ? asset.description : "";
                asset.description = currentDesc.replace("#locked", "").trim();
            } else {
                String newDesc = asset.description != null ? asset.description + " #locked" : "#locked";
                asset.description = newDesc;
            }
        }

        archiveMultipleAssets(!isUnlocking, assets);
    }

    private void sendToEcho(ImmichAsset asset) {
        if (asset == null || getActivity() == null) return;

        String imageUrl;
        if (asset.isLocalOnly && asset.localUri != null) {
            imageUrl = asset.localUri;
        } else {
            String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
            imageUrl = cleanBaseUrl + "/api/assets/" + asset.id + "/original";
        }

        SharedViewModel sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        sharedViewModel.setPendingImageUri(imageUrl);

        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_echo);
        } else {
            Toast.makeText(getContext(), "Bild an Echo gesendet!", Toast.LENGTH_SHORT).show();
        }

        if (fullscreenOverlay != null && fullscreenOverlay.getVisibility() == View.VISIBLE) {
            closeFullscreen();
        }
        if (currentFotosAdapter != null) {
            currentFotosAdapter.clearSelection();
        }
    }

    private void toggleFavoriteStatus() {
        if (currentViewedAsset == null) return;

        if (currentViewedAsset.isLocalOnly) {
            Toast.makeText(getContext(), "Dieses Bild ist noch nicht in der Cloud, Favorisieren derzeit nicht möglich.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isFav = favoriteAssets.contains(currentViewedAsset.id) || (currentViewedAsset.isFavorite != null && currentViewedAsset.isFavorite);
        boolean newFavStatus = !isFav;

        if (newFavStatus) {
            favoriteAssets.add(currentViewedAsset.id);
            currentViewedAsset.isFavorite = true;
            Toast.makeText(getContext(), "Zu Favoriten hinzugefügt ❤️", Toast.LENGTH_SHORT).show();
        } else {
            favoriteAssets.remove(currentViewedAsset.id);
            currentViewedAsset.isFavorite = false;
            Toast.makeText(getContext(), "Aus Favoriten entfernt", Toast.LENGTH_SHORT).show();
        }
        updateFullscreenUI();

        if (recyclerViewSearch != null && recyclerViewSearch.getVisibility() == View.VISIBLE && lastSearchMode == 0) {
            refreshVisibleGrids();
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection conn = null;
            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;

                URL url = new URL(cleanBaseUrl + "/api/assets/" + currentViewedAsset.id);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                String requestBody = "{\"isFavorite\": " + newFavStatus + "}";
                OutputStream os = conn.getOutputStream();
                os.write(requestBody.getBytes("UTF-8"));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                InputStream inputStream = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder responseBuilder = new StringBuilder();
                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) responseBuilder.append(line);
                    reader.close();
                }
                String responseText = responseBuilder.toString();

                if (responseCode != 200 && responseCode != 201 && responseCode != 204) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (newFavStatus) {
                                favoriteAssets.remove(currentViewedAsset.id);
                                currentViewedAsset.isFavorite = false;
                            } else {
                                favoriteAssets.add(currentViewedAsset.id);
                                currentViewedAsset.isFavorite = true;
                            }
                            updateFullscreenUI();
                            if (recyclerViewSearch != null && recyclerViewSearch.getVisibility() == View.VISIBLE && lastSearchMode == 0) refreshVisibleGrids();
                            Toast.makeText(getContext(), "Fehler beim Cloud-Sync (" + responseCode + "): " + responseText, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (newFavStatus) {
                            favoriteAssets.remove(currentViewedAsset.id);
                            currentViewedAsset.isFavorite = false;
                        } else {
                            favoriteAssets.add(currentViewedAsset.id);
                            currentViewedAsset.isFavorite = true;
                        }
                        updateFullscreenUI();
                        if (recyclerViewSearch != null && recyclerViewSearch.getVisibility() == View.VISIBLE && lastSearchMode == 0) refreshVisibleGrids();
                        Toast.makeText(getContext(), "Netzwerkfehler beim Favorisieren: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void updateFullscreenUI() {
        if (currentViewedAsset == null || btnFavorite == null) return;
        if (favoriteAssets.contains(currentViewedAsset.id) || (currentViewedAsset.isFavorite != null && currentViewedAsset.isFavorite)) {
            btnFavorite.setColorFilter(getThemeColor());
            favoriteAssets.add(currentViewedAsset.id);
            currentViewedAsset.isFavorite = true;
        } else {
            btnFavorite.setColorFilter(Color.WHITE);
        }

        if (currentViewedAsset.isLocalOnly) {
            if (btnShareFullscreen != null) btnShareFullscreen.setVisibility(View.GONE);
            if (btnEditFullscreen != null) btnEditFullscreen.setVisibility(View.GONE);
            if (btnAddToFullscreen != null) btnAddToFullscreen.setVisibility(View.GONE);
            if (btnDeleteFullscreen != null) btnDeleteFullscreen.setVisibility(View.GONE);
            if (btnUploadLocal != null) {
                btnUploadLocal.setVisibility(View.VISIBLE);
                if (btnUploadLocal.getParent() instanceof View) {
                    ((View) btnUploadLocal.getParent()).requestLayout();
                }
            }
        } else {
            if (btnShareFullscreen != null) btnShareFullscreen.setVisibility(View.VISIBLE);
            if (btnEditFullscreen != null) btnEditFullscreen.setVisibility(View.VISIBLE);
            if (btnAddToFullscreen != null) btnAddToFullscreen.setVisibility(View.VISIBLE);
            if (btnDeleteFullscreen != null) btnDeleteFullscreen.setVisibility(View.VISIBLE);
            if (btnUploadLocal != null) btnUploadLocal.setVisibility(View.GONE);
        }
    }

    private void updateSelectionBottomBarUI() {
        if (currentFotosAdapter == null || layoutSelectionBottomBar == null) return;
        List<ImmichAsset> selected = currentFotosAdapter.getSelectedAssets();
        boolean hasLocal = false;
        boolean hasCloud = false;
        for (ImmichAsset a : selected) {
            if (a.isLocalOnly) hasLocal = true;
            else hasCloud = true;
        }

        if (hasLocal && !hasCloud) {
            if (btnSelectionShare != null) btnSelectionShare.setVisibility(View.GONE);
            if (btnSelectionAddTo != null) btnSelectionAddTo.setVisibility(View.GONE);
            if (btnSelectionDelete != null) btnSelectionDelete.setVisibility(View.GONE);

            if (btnSelectionUploadLocal != null) {
                btnSelectionUploadLocal.setVisibility(View.VISIBLE);
                btnSelectionUploadLocal.setBackgroundTintList(ColorStateList.valueOf(getPillHighlightColor()));
                if (btnSelectionUploadLocal.getParent() instanceof View) {
                    ((View) btnSelectionUploadLocal.getParent()).requestLayout();
                }
            }
        } else {
            if (btnSelectionShare != null) btnSelectionShare.setVisibility(View.VISIBLE);
            if (btnSelectionAddTo != null) btnSelectionAddTo.setVisibility(View.VISIBLE);
            if (btnSelectionDelete != null) btnSelectionDelete.setVisibility(View.VISIBLE);

            if (btnSelectionUploadLocal != null) btnSelectionUploadLocal.setVisibility(View.GONE);
        }
    }

    private void uploadSingleAsset(ImmichAsset asset) {
        if (asset == null || !asset.isLocalOnly || asset.localUri == null) return;
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;

        Toast.makeText(getContext(), "Upload startet...", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
            SharedPreferences prefs = getContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
            String deviceId = prefs.getString(SettingsFragment.KEY_DEVICE_ID, "android-app");

            boolean success = performSingleSyncUpload(asset, cleanBaseUrl, currentApiKey, deviceId);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (success) {
                        asset.isLocalOnly = false;
                        localMediaCache.remove(asset);
                        globalAssetList.add(asset);
                        updateFullscreenUI();
                        refreshVisibleGrids();
                        Toast.makeText(getContext(), "Upload erfolgreich! ☁️➡️✅", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Fehler beim Upload.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void uploadMultipleAssets(List<ImmichAsset> assetsToUpload) {
        if (assetsToUpload == null || assetsToUpload.isEmpty() || currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;

        List<ImmichAsset> locals = new ArrayList<>();
        for (ImmichAsset a : assetsToUpload) {
            if (a.isLocalOnly && a.localUri != null) locals.add(a);
        }

        if (locals.isEmpty()) return;

        Toast.makeText(getContext(), locals.size() + " Bilder werden hochgeladen...", Toast.LENGTH_LONG).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
            SharedPreferences prefs = getContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
            String deviceId = prefs.getString(SettingsFragment.KEY_DEVICE_ID, "android-app");

            int successCount = 0;
            int failCount = 0;

            for (int i = 0; i < locals.size(); i++) {
                ImmichAsset asset = locals.get(i);
                final int progress = i + 1;
                if (getActivity() != null && locals.size() > 3 && progress % 3 == 0) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Upload " + progress + " von " + locals.size() + "...", Toast.LENGTH_SHORT).show());
                }

                boolean success = performSingleSyncUpload(asset, cleanBaseUrl, currentApiKey, deviceId);
                if (success) {
                    successCount++;
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            asset.isLocalOnly = false;
                            localMediaCache.remove(asset);
                            if (!globalAssetList.contains(asset)) {
                                globalAssetList.add(asset);
                            }
                        });
                    }
                } else {
                    failCount++;
                }
            }

            final int finalSuccess = successCount;
            final int finalFail = failCount;

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (currentFotosAdapter != null) currentFotosAdapter.clearSelection();
                    refreshVisibleGrids();
                    if (finalFail == 0) {
                        Toast.makeText(getContext(), "Alle " + finalSuccess + " Uploads erfolgreich! ☁️➡️✅", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getContext(), finalSuccess + " erfolgreich, " + finalFail + " fehlgeschlagen.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private boolean performSingleSyncUpload(ImmichAsset asset, String cleanBaseUrl, String apiKey, String deviceId) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(cleanBaseUrl + "/api/assets");
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            conn = (HttpURLConnection) url.openConnection();
            conn.setUseCaches(false);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            OutputStream outputStream = conn.getOutputStream();
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(outputStream, "UTF-8"), true);

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"deviceAssetId\"\r\n\r\n");
            writer.append(asset.deviceAssetId).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"deviceId\"\r\n\r\n");
            writer.append(deviceId).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"fileCreatedAt\"\r\n\r\n");
            writer.append(asset.fileCreatedAt).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"fileModifiedAt\"\r\n\r\n");
            writer.append(asset.fileCreatedAt).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"isFavorite\"\r\n\r\n");
            writer.append("false").append("\r\n");

            String fileName = asset.originalFileName;
            if (fileName == null || fileName.isEmpty()) fileName = "upload.jpg";
            String mimeType = asset.type.equals("VIDEO") ? "video/mp4" : "image/jpeg";

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"assetData\"; filename=\"").append(fileName).append("\"\r\n");
            writer.append("Content-Type: ").append(mimeType).append("\r\n\r\n");
            writer.flush();

            Uri uri = Uri.parse(asset.localUri);
            InputStream is = getContext().getContentResolver().openInputStream(uri);
            if (is != null) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
                is.close();
            }

            writer.append("\r\n");
            writer.append("--").append(boundary).append("--\r\n");
            writer.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == 200 || responseCode == 201) {
                BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JsonObject responseObj = JsonParser.parseString(response.toString()).getAsJsonObject();
                if (responseObj.has("id")) {
                    asset.id = responseObj.get("id").getAsString();
                }
                return true;
            } else if (responseCode == 409) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void setupDescriptionSaver() {
        if (etDetailDescription != null) {
            etDetailDescription.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (isUpdatingDescriptionUI || currentViewedAsset == null || currentViewedAsset.isLocalOnly) return;

                    String newDescription = s.toString();
                    localDescriptions.put(currentViewedAsset.id, newDescription);
                    currentViewedAsset.description = newDescription;

                    if (descriptionRunnable != null) {
                        descriptionHandler.removeCallbacks(descriptionRunnable);
                    }

                    descriptionRunnable = () -> saveDescriptionToServer(currentViewedAsset.id, newDescription);
                    descriptionHandler.postDelayed(descriptionRunnable, 1500);
                }
            });
        }
    }

    private void saveDescriptionToServer(String assetId, String description) {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection conn = null;
            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                URL url = new URL(cleanBaseUrl + "/api/assets/" + assetId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                String escapedDesc = description.replace("\"", "\\\"").replace("\n", "\\n");
                String requestBody = "{\"description\": \"" + escapedDesc + "\"}";

                OutputStream os = conn.getOutputStream();
                os.write(requestBody.getBytes("UTF-8"));
                os.flush();
                os.close();

                conn.getResponseCode();
            } catch (Exception e) {
                Log.e("Immich_Desc", "Fehler beim Speichern der Beschreibung", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void hideKeyboard() {
        if (getActivity() == null) return;
        View view = getActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void startStoryAutoPlay(int position, int total) {
        if (storyAnimator != null) {
            storyAnimator.cancel();
        }

        // Stelle sicher, dass die aktuelle Progressbar bei 0 startet
        if (storyProgressContainer != null && position < storyProgressContainer.getChildCount()) {
            android.widget.ProgressBar pb = (android.widget.ProgressBar) storyProgressContainer.getChildAt(position);
            if (pb != null) pb.setProgress(0);
        }

        storyAnimator = android.animation.ValueAnimator.ofInt(0, 10000);
        storyAnimator.setDuration(3500);
        storyAnimator.setInterpolator(new android.view.animation.LinearInterpolator());

        storyAnimator.addUpdateListener(animation -> {
            if (storyProgressContainer != null && position < storyProgressContainer.getChildCount()) {
                android.widget.ProgressBar pb = (android.widget.ProgressBar) storyProgressContainer.getChildAt(position);
                if (pb != null) {
                    pb.setProgress((int) animation.getAnimatedValue());
                }
            }
        });

        storyAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (isStoryModeActive && viewPagerFullscreen != null && viewPagerFullscreen.getCurrentItem() == position && !isStoryPaused) {
                    if ((int) storyAnimator.getAnimatedValue() >= 9900) {
                        if (position < total - 1) {
                            viewPagerFullscreen.setCurrentItem(position + 1, false);
                        } else {
                            closeFullscreen();
                        }
                    }
                }
            }
        });

        if (!isStoryPaused) {
            storyAnimator.start();
        }
    }

    private void openFullscreen(List<ImmichAsset> contextList, int clickedPosition, boolean isStoryMode) {
        if (contextList == null || getContext() == null || viewPagerFullscreen == null || fullscreenOverlay == null) return;

        isStoryModeActive = isStoryMode;
        isStoryPaused = false;

        isFullscreenUiVisible = false;
        if (btnCloseFullscreen != null) btnCloseFullscreen.setVisibility(View.GONE);
        if (btnFavorite != null) btnFavorite.setVisibility(View.GONE);
        if (btnMoreOptions != null) btnMoreOptions.setVisibility(View.GONE);
        if (bottomMenuFullscreen != null) bottomMenuFullscreen.setVisibility(View.GONE);

        if (isStoryModeActive) {
            if (storyProgressContainer == null) {
                storyProgressContainer = new LinearLayout(getContext());
                storyProgressContainer.setOrientation(LinearLayout.HORIZONTAL);
                CoordinatorLayout.LayoutParams lp = new CoordinatorLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (int)(4 * getResources().getDisplayMetrics().density));
                lp.setMargins(32, 64, 32, 0);
                lp.gravity = android.view.Gravity.TOP;
                storyProgressContainer.setLayoutParams(lp);
                fullscreenOverlay.addView(storyProgressContainer);
            }
            storyProgressContainer.removeAllViews();
            storyProgressContainer.setWeightSum(contextList.size());

            for (int i = 0; i < contextList.size(); i++) {
                android.widget.ProgressBar pb = new android.widget.ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                p.setMargins(8, 0, 8, 0);
                pb.setLayoutParams(p);
                pb.setMax(10000);
                pb.setProgress(0);

                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                bg.setCornerRadius(20f);
                bg.setColor(Color.parseColor("#44FFFFFF"));

                android.graphics.drawable.GradientDrawable progress = new android.graphics.drawable.GradientDrawable();
                progress.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                progress.setCornerRadius(20f);
                progress.setColor(Color.WHITE);

                android.graphics.drawable.ClipDrawable clipProgress = new android.graphics.drawable.ClipDrawable(
                        progress, android.view.Gravity.LEFT, android.graphics.drawable.ClipDrawable.HORIZONTAL);

                android.graphics.drawable.LayerDrawable layerDrawable = new android.graphics.drawable.LayerDrawable(new Drawable[]{bg, clipProgress});
                layerDrawable.setId(0, android.R.id.background);
                layerDrawable.setId(1, android.R.id.progress);

                pb.setProgressDrawable(layerDrawable);

                storyProgressContainer.addView(pb);
            }
            storyProgressContainer.setVisibility(View.VISIBLE);
        } else {
            if (storyProgressContainer != null) {
                storyProgressContainer.setVisibility(View.GONE);
            }
        }

        FullscreenPagerAdapter pagerAdapter = new FullscreenPagerAdapter(getContext(), contextList, currentApiUrl, currentApiKey, isStoryModeActive, new FullscreenPagerAdapter.SwipeListener() {
            @Override
            public void onSwipeUp() {
                if (bottomSheetBehavior != null) bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
            @Override
            public void onSwipeDown() {
                if (bottomSheetBehavior != null && (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED ||
                        bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HALF_EXPANDED)) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                } else {
                    closeFullscreen();
                }
            }
            @Override
            public void onSingleTap(float x, float width) {
                long now = System.currentTimeMillis();
                if (now - lastTapTime < 300) return;
                lastTapTime = now;

                if (isStoryModeActive) {
                    int current = viewPagerFullscreen.getCurrentItem();
                    int total = viewPagerFullscreen.getAdapter().getItemCount();

                    if (x < width * 0.3f) {
                        if (current > 0) {
                            viewPagerFullscreen.setCurrentItem(current - 1, false);
                        } else if (storyAnimator != null) {
                            storyAnimator.cancel();
                            startStoryAutoPlay(0, total);
                        }
                    } else if (x > width * 0.7f) {
                        if (current < total - 1) {
                            viewPagerFullscreen.setCurrentItem(current + 1, false);
                        } else {
                            closeFullscreen();
                        }
                    } else {
                        toggleFullscreenUI();
                    }
                } else {
                    toggleFullscreenUI();
                }
            }
            @Override
            public void onLongPress() {
                if (isStoryModeActive && storyAnimator != null) {
                    if (isStoryPaused) {
                        isStoryPaused = false;
                        storyAnimator.resume();
                        Toast.makeText(getContext(), "▶️ Fortgesetzt", Toast.LENGTH_SHORT).show();
                    } else {
                        isStoryPaused = true;
                        storyAnimator.pause();
                        Toast.makeText(getContext(), "⏸️ Angehalten", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            @Override
            public void onVideoEnded() {
                if (isStoryModeActive) {
                    int current = viewPagerFullscreen.getCurrentItem();
                    int total = viewPagerFullscreen.getAdapter().getItemCount();
                    if (current < total - 1) {
                        viewPagerFullscreen.setCurrentItem(current + 1, false);
                    } else {
                        closeFullscreen();
                    }
                }
            }
        });

        if (pageChangeCallback != null) {
            viewPagerFullscreen.unregisterOnPageChangeCallback(pageChangeCallback);
        }

        pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentViewedAsset = contextList.get(position);
                fillDetailsSheet(currentViewedAsset);
                updateFullscreenUI();

                isStoryPaused = false;

                if (isStoryModeActive) {
                    if (storyProgressContainer != null) {
                        for (int i = 0; i < storyProgressContainer.getChildCount(); i++) {
                            android.widget.ProgressBar pb = (android.widget.ProgressBar) storyProgressContainer.getChildAt(i);
                            if (i < position) pb.setProgress(10000);
                            else if (i > position) pb.setProgress(0);
                        }
                    }

                    boolean isVideo = currentViewedAsset.type != null && currentViewedAsset.type.equals("VIDEO");
                    if (!isVideo) {
                        startStoryAutoPlay(position, contextList.size());
                    } else {
                        if (storyAnimator != null) {
                            storyAnimator.cancel();
                        }
                    }
                }
            }
        };

        viewPagerFullscreen.registerOnPageChangeCallback(pageChangeCallback);
        viewPagerFullscreen.setAdapter(pagerAdapter);
        viewPagerFullscreen.setCurrentItem(clickedPosition, false);

        if (bottomSheetBehavior != null) bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        fullscreenOverlay.setAlpha(0f);
        fullscreenOverlay.setVisibility(View.VISIBLE);
        fullscreenOverlay.animate().alpha(1f).setDuration(250).start();
    }

    private void fetchSingleAssetDetails(String assetId) {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty() || assetId == null || assetId.startsWith("local_")) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                URL url = new URL(cleanBaseUrl + "/api/assets/" + assetId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");

                int responseCode = conn.getResponseCode();

                if (responseCode == 404 || responseCode == 405) {
                    url = new URL(cleanBaseUrl + "/api/asset/assetById/" + assetId);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("x-api-key", currentApiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    responseCode = conn.getResponseCode();
                }

                if (responseCode == 404 || responseCode == 405) {
                    url = new URL(cleanBaseUrl + "/api/asset/" + assetId);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("x-api-key", currentApiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    responseCode = conn.getResponseCode();
                }

                if (responseCode == 200 || responseCode == 201) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    JsonObject assetObj = JsonParser.parseString(response.toString()).getAsJsonObject();

                    String fetchedDesc = "";
                    if (assetObj.has("description") && !assetObj.get("description").isJsonNull()) {
                        fetchedDesc = assetObj.get("description").getAsString();
                    }
                    if (fetchedDesc.trim().isEmpty() && assetObj.has("exifInfo") && !assetObj.get("exifInfo").isJsonNull()) {
                        JsonObject exifObj = assetObj.getAsJsonObject("exifInfo");
                        if (exifObj.has("description") && !exifObj.get("description").isJsonNull()) {
                            fetchedDesc = exifObj.get("description").getAsString();
                        }
                        if (fetchedDesc.trim().isEmpty() && exifObj.has("imageDescription") && !exifObj.get("imageDescription").isJsonNull()) {
                            fetchedDesc = exifObj.get("imageDescription").getAsString();
                        }
                    }

                    boolean fetchedFav = false;
                    if (assetObj.has("isFavorite") && !assetObj.get("isFavorite").isJsonNull()) {
                        fetchedFav = assetObj.get("isFavorite").getAsBoolean();
                    }

                    boolean fetchedArchived = false;
                    if (assetObj.has("isArchived") && !assetObj.get("isArchived").isJsonNull()) {
                        fetchedArchived = assetObj.get("isArchived").getAsBoolean();
                    } else if (assetObj.has("visibility") && !assetObj.get("visibility").isJsonNull()) {
                        String vis = assetObj.get("visibility").getAsString();
                        fetchedArchived = "archive".equalsIgnoreCase(vis);
                    }

                    final String finalDesc = fetchedDesc;
                    final boolean finalFav = fetchedFav;
                    final boolean finalArchived = fetchedArchived;

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (!finalDesc.isEmpty()) localDescriptions.put(assetId, finalDesc);

                            if (finalFav) favoriteAssets.add(assetId);
                            else favoriteAssets.remove(assetId);

                            if (currentViewedAsset != null && currentViewedAsset.id.equals(assetId)) {
                                currentViewedAsset.description = finalDesc;
                                currentViewedAsset.isFavorite = finalFav;
                                currentViewedAsset.isArchived = finalArchived;

                                updateFullscreenUI();

                                if (etDetailDescription != null) {
                                    String currentText = etDetailDescription.getText().toString();
                                    if (!currentText.equals(finalDesc) && (currentText.trim().isEmpty() || !etDetailDescription.hasFocus())) {
                                        isUpdatingDescriptionUI = true;
                                        etDetailDescription.setText(finalDesc);
                                        isUpdatingDescriptionUI = false;
                                    }
                                }
                            }
                        });
                    }
                }
            } catch (Exception e) {}
        });
    }

    private void fillDetailsSheet(ImmichAsset asset) {
        if (asset == null) return;
        if (tvDetailDate != null) {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("d. MMMM yyyy • HH:mm", Locale.GERMAN);
                Date date = inputFormat.parse(asset.fileCreatedAt);
                if (date != null) tvDetailDate.setText(outputFormat.format(date));
            } catch (Exception e) { tvDetailDate.setText("Unbekanntes Datum"); }
        }
        if (tvDetailFilename != null) tvDetailFilename.setText(asset.originalFileName != null ? asset.originalFileName : "Unbekannt");

        if (asset.exifInfo != null) {
            String make = asset.exifInfo.make != null ? asset.exifInfo.make : "";
            String model = asset.exifInfo.model != null ? asset.exifInfo.model : "Unbekannte Kamera";
            if (tvDetailCamera != null) tvDetailCamera.setText(make + " " + model);

            String city = asset.exifInfo.city != null ? asset.exifInfo.city : "";
            String country = asset.exifInfo.country != null ? asset.exifInfo.country : "";
            if (tvDetailLocation != null) {
                if (!city.isEmpty() || !country.isEmpty()) tvDetailLocation.setText("🗺️ " + city + ", " + country);
                else tvDetailLocation.setText("🗺️ Kein Standort");
            }
        } else {
            if (tvDetailCamera != null) tvDetailCamera.setText(asset.isLocalOnly ? "Lokal auf Gerät" : "Keine Kamera-Infos");
            if (tvDetailLocation != null) tvDetailLocation.setText("🗺️ Kein Standort");
        }

        if (etDetailDescription != null) {
            isUpdatingDescriptionUI = true;

            if (asset.isLocalOnly) {
                etDetailDescription.setText("Wartet auf Upload in die Cloud ☁️");
                etDetailDescription.setEnabled(false);
            } else {
                etDetailDescription.setEnabled(true);
                String savedDescription = localDescriptions.get(asset.id);
                if (savedDescription != null && !savedDescription.isEmpty()) {
                    etDetailDescription.setText(savedDescription);
                } else {
                    String desc = asset.description;
                    if ((desc == null || desc.trim().isEmpty()) && asset.exifInfo != null) {
                        desc = asset.exifInfo.description;
                        if (desc == null || desc.trim().isEmpty()) {
                            desc = asset.exifInfo.imageDescription;
                        }
                    }

                    if (desc != null && !desc.trim().isEmpty()) {
                        etDetailDescription.setText(desc);
                        localDescriptions.put(asset.id, desc);
                    } else {
                        etDetailDescription.setText("");
                    }
                }
            }

            isUpdatingDescriptionUI = false;
        }

        fetchSingleAssetDetails(asset.id);
    }

    private void closeFullscreen() {
        isStoryModeActive = false;
        if (storyAnimator != null) {
            storyAnimator.cancel();
            storyAnimator = null;
        }
        if (storyProgressContainer != null) {
            storyProgressContainer.setVisibility(View.GONE);
        }

        if (bottomSheetBehavior != null) bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        if (fullscreenOverlay != null) {
            fullscreenOverlay.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                fullscreenOverlay.setVisibility(View.GONE);
                if (viewPagerFullscreen != null) {
                    viewPagerFullscreen.setOffscreenPageLimit(ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT);
                    viewPagerFullscreen.setAdapter(null);
                }
                hideKeyboard();
                Glide.get(requireContext()).clearMemory();
            }).start();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadAppropriateUrlAndKey();
            if (!globalAssetList.isEmpty() && recyclerViewFotos != null && "Fotos".equals(activeTab)) {
                recyclerViewFotos.post(this::refreshVisibleGrids);
            }
        } else {
            if (isStoryModeActive && storyAnimator != null) {
                isStoryPaused = true;
                storyAnimator.pause();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAppropriateUrlAndKey();
        if (!globalAssetList.isEmpty() && recyclerViewFotos != null && "Fotos".equals(activeTab)) {
            recyclerViewFotos.post(this::refreshVisibleGrids);
        }
        if (isStoryModeActive && storyAnimator != null && isStoryPaused) {
            isStoryPaused = false;
            storyAnimator.resume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isStoryModeActive && storyAnimator != null) {
            isStoryPaused = true;
            storyAnimator.pause();
        }
    }

    private void loadAppropriateUrlAndKey() {
        if (getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
        String savedSsid = prefs.getString(SettingsFragment.KEY_WIFI_SSID, "");
        String localUrl = prefs.getString(SettingsFragment.KEY_FOTOS_LOCAL, "");
        String publicUrl = prefs.getString(SettingsFragment.KEY_FOTOS_PUBLIC, "");
        String apiKey = prefs.getString(SettingsFragment.KEY_FOTOS_API_KEY, "");

        // --- NEU: Onboarding Logik für Fotos ---
        if (localUrl.isEmpty() || apiKey.isEmpty()) {
            if (layoutFotosContent != null) layoutFotosContent.setVisibility(View.GONE);
            if (layoutFotosSetup != null) layoutFotosSetup.setVisibility(View.VISIBLE);
            if (layoutFotosIntroOverlay != null) layoutFotosIntroOverlay.setVisibility(View.VISIBLE);

            if (getView() != null) {
                EditText etSetupLocal = getView().findViewById(R.id.et_setup_fotos_local);
                EditText etSetupPublic = getView().findViewById(R.id.et_setup_fotos_public);
                EditText etSetupApiKey = getView().findViewById(R.id.et_setup_fotos_api_key);
                MaterialButton btnIntroNext = getView().findViewById(R.id.btn_fotos_intro_next);
                MaterialButton btnIntroSkip = getView().findViewById(R.id.btn_fotos_intro_skip);
                MaterialButton btnSetupSave = getView().findViewById(R.id.btn_fotos_setup_save);

                if (etSetupLocal != null) etSetupLocal.setText(localUrl);
                if (etSetupPublic != null) etSetupPublic.setText(publicUrl);
                if (etSetupApiKey != null) etSetupApiKey.setText(apiKey);

                if (btnIntroNext != null) btnIntroNext.setOnClickListener(v -> layoutFotosIntroOverlay.setVisibility(View.GONE));

                if (btnIntroSkip != null) {
                    btnIntroSkip.setOnClickListener(v -> {
                        prefs.edit().putBoolean(SettingsFragment.KEY_MOD_FOTOS, false).apply();
                        Toast.makeText(getContext(), "Fotos-Modul ausgeblendet.", Toast.LENGTH_SHORT).show();
                        if (getActivity() instanceof com.example.unicontrol.MainActivity) {
                            ((com.example.unicontrol.MainActivity) getActivity()).refreshMenu();
                            ((com.example.unicontrol.MainActivity) getActivity()).goToNextOnboardingTab(R.id.nav_fotos);
                        }
                    });
                }

                if (btnSetupSave != null) {
                    btnSetupSave.setOnClickListener(v -> {
                        prefs.edit()
                                .putString(SettingsFragment.KEY_FOTOS_LOCAL, etSetupLocal.getText().toString().trim())
                                .putString(SettingsFragment.KEY_FOTOS_PUBLIC, etSetupPublic.getText().toString().trim())
                                .putString(SettingsFragment.KEY_FOTOS_API_KEY, etSetupApiKey.getText().toString().trim())
                                .apply();
                        Toast.makeText(getContext(), "Fotos verbunden! ✅", Toast.LENGTH_SHORT).show();

                        layoutFotosSetup.setVisibility(View.GONE);
                        if (layoutFotosContent != null) layoutFotosContent.setVisibility(View.VISIBLE);

                        loadAppropriateUrlAndKey(); // Lädt jetzt die echten Daten!

                        if (getActivity() instanceof com.example.unicontrol.MainActivity) {
                            ((com.example.unicontrol.MainActivity) getActivity()).goToNextOnboardingTab(R.id.nav_fotos);
                        }
                    });
                }
            }
            return; // Ladeversuch abbrechen, wir sind im Setup!
        } else {
            if (layoutFotosContent != null) layoutFotosContent.setVisibility(View.VISIBLE);
            if (layoutFotosSetup != null) layoutFotosSetup.setVisibility(View.GONE);
            if (layoutFotosIntroOverlay != null) layoutFotosIntroOverlay.setVisibility(View.GONE);
        }
        // ---------------------------------------

        String currentSsid = NetworkUtils.getCurrentSsid(getContext());
        String targetUrl = "";

        if (!savedSsid.isEmpty() && currentSsid.equals(savedSsid) && !localUrl.isEmpty()) targetUrl = formatUrl(localUrl, true);
        else if (!publicUrl.isEmpty()) targetUrl = formatUrl(publicUrl, false);
        else if (!localUrl.isEmpty()) targetUrl = formatUrl(localUrl, true);

        if (!targetUrl.isEmpty() && !apiKey.isEmpty()) {
            boolean urlChanged = !targetUrl.equals(currentApiUrl) || !apiKey.equals(currentApiKey);
            boolean needsData = globalAssetList.isEmpty();

            if (urlChanged || needsData) {
                currentApiUrl = targetUrl;
                currentApiKey = apiKey;
                currentPage = 1;
                isLastPage = false;
                globalAssetList.clear();
                favoriteAssets.clear();

                loadLocalAssetsAsync();

                fetchMemories();
                fetchPhotosFromImmich(currentApiUrl, currentApiKey, currentPage);
                syncFavoritesInBackground();
            } else if (recyclerViewFotos != null && "Fotos".equals(activeTab)) {
                recyclerViewFotos.post(this::refreshVisibleGrids);
            }
        } else {
            if (recyclerViewFotos != null) recyclerViewFotos.setVisibility(View.GONE);
            if (tvPlaceholder != null) {
                tvPlaceholder.setVisibility(View.VISIBLE);
                tvPlaceholder.setText("Bitte trage URL und API-Key in den Einstellungen ein.");
            }
        }
    }

    private void fetchMemories() {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;

                SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'00:00:00.000'Z'", Locale.US);
                iso.setTimeZone(TimeZone.getTimeZone("UTC"));
                String today = iso.format(new Date());

                URL url = new URL(cleanBaseUrl + "/api/memories?for=" + today);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");

                int responseCode = conn.getResponseCode();

                if (responseCode == 404 || responseCode == 400 || responseCode == 405) {
                    url = new URL(cleanBaseUrl + "/api/memories");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("x-api-key", currentApiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    responseCode = conn.getResponseCode();
                }

                if (responseCode == 200 || responseCode == 201) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    JsonElement element = JsonParser.parseString(response.toString());
                    if (element.isJsonArray()) {
                        JsonArray jsonArray = element.getAsJsonArray();
                        List<ImmichMemory> memoriesList = new ArrayList<>();

                        for (JsonElement item : jsonArray) {
                            if (!item.isJsonObject()) continue;
                            JsonObject obj = item.getAsJsonObject();
                            ImmichMemory memory = new ImmichMemory();

                            memory.id = obj.has("id") && !obj.get("id").isJsonNull() ? obj.get("id").getAsString() : "";

                            String title = "";

                            if (obj.has("title") && !obj.get("title").isJsonNull()) {
                                title = obj.get("title").getAsString().trim();
                            }

                            if (title.isEmpty() && obj.has("data") && obj.get("data").isJsonObject()) {
                                JsonObject dataObj = obj.getAsJsonObject("data");
                                if (dataObj.has("title") && !dataObj.get("title").isJsonNull()) {
                                    title = dataObj.get("title").getAsString().trim();
                                }
                            }

                            if (title.isEmpty() && obj.has("name") && !obj.get("name").isJsonNull()) {
                                title = obj.get("name").getAsString().trim();
                            }

                            if (title.isEmpty()) {
                                title = "Erinnerung";
                            }

                            memory.title = title;

                            if (obj.has("assets") && obj.get("assets").isJsonArray()) {
                                memory.assets = new Gson().fromJson(obj.getAsJsonArray("assets"), new TypeToken<List<ImmichAsset>>(){}.getType());
                            }

                            if (memory.assets != null && !memory.assets.isEmpty()) {
                                memoriesList.add(memory);
                            }
                        }

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (!memoriesList.isEmpty() && recyclerViewMemories != null) {
                                    recyclerViewMemories.setVisibility(View.VISIBLE);

                                    MemoriesAdapter memAdapter = new MemoriesAdapter(getContext(), memoriesList, cleanBaseUrl, currentApiKey, memory -> {
                                        openFullscreen(memory.assets, 0, true);
                                    });
                                    recyclerViewMemories.setAdapter(memAdapter);
                                } else if (recyclerViewMemories != null) {
                                    recyclerViewMemories.setVisibility(View.GONE);
                                }
                            });
                        }
                    }
                }
            } catch (Exception e) {
                if (getActivity() != null && recyclerViewMemories != null) {
                    getActivity().runOnUiThread(() -> recyclerViewMemories.setVisibility(View.GONE));
                }
            }
        });
    }

    private void syncFavoritesInBackground() {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection conn = null;
            try {
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                URL url = new URL(cleanBaseUrl + "/api/search/metadata");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("x-api-key", currentApiKey);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                String jsonBody = "{\"isFavorite\": true, \"withArchived\": true, \"size\": 1000}";
                conn.getOutputStream().write(jsonBody.getBytes("UTF-8"));

                int responseCode = conn.getResponseCode();
                InputStream inputStream = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder responseBuilder = new StringBuilder();
                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) responseBuilder.append(line);
                    reader.close();
                }
                String responseText = responseBuilder.toString();

                if (responseCode == 200 || responseCode == 201) {
                    JsonElement jsonElement = JsonParser.parseString(responseText);
                    final List<ImmichAsset> finalFavs = parseAssetsFromJson(jsonElement);

                    if (finalFavs != null && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            extractMetadataToCache(finalFavs);
                            if (currentViewedAsset != null) {
                                updateFullscreenUI();
                            }
                        });
                    }
                }
            } catch (Exception e) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void fetchPhotosFromImmich(String baseUrl, String apiKey, int page) {
        isLoading = true;

        if (page == 1 && tvPlaceholder != null) {
            tvPlaceholder.setVisibility(View.VISIBLE);
            tvPlaceholder.setText("Lade Bilder aus dem Heimnetz...");
            if (recyclerViewFotos != null) recyclerViewFotos.setVisibility(View.GONE);
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection conn = null;
            try {
                String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
                URL url = new URL(cleanBaseUrl + "/api/search/metadata");

                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("x-api-key", apiKey);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                String jsonBody = "{\"size\": " + PAGE_SIZE + ", \"page\": " + page + ", \"withExif\": true}";
                conn.getOutputStream().write(jsonBody.getBytes("UTF-8"));

                int responseCode = conn.getResponseCode();

                InputStream inputStream = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder responseBuilder = new StringBuilder();
                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) responseBuilder.append(line);
                    reader.close();
                }
                String responseText = responseBuilder.toString();

                if (responseCode == 200 || responseCode == 201) {
                    JsonElement jsonElement = JsonParser.parseString(responseText);
                    final List<ImmichAsset> finalAssetList = parseAssetsFromJson(jsonElement);

                    if (finalAssetList != null) {
                        if (finalAssetList.size() < PAGE_SIZE) isLastPage = true;

                        List<ImmichAsset> safeAssets = new ArrayList<>();
                        for (ImmichAsset a : finalAssetList) {
                            String d = a.description;
                            if (d == null && a.exifInfo != null) d = a.exifInfo.description;
                            if (d == null && a.exifInfo != null) d = a.exifInfo.imageDescription;

                            boolean isLocked = (d != null && d.toLowerCase().contains("#locked"));
                            boolean isArchived = (a.isArchived != null && a.isArchived);

                            if (!isLocked && !isArchived) {
                                safeAssets.add(a);
                            }
                        }

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (page == 1) {
                                    globalAssetList.clear();
                                    globalAssetList.addAll(safeAssets);
                                } else {
                                    globalAssetList.addAll(safeAssets);
                                }

                                extractMetadataToCache(safeAssets);
                                isLoading = false;
                                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                                if (tvPlaceholder != null) tvPlaceholder.setVisibility(View.GONE);

                                if ("Fotos".equals(activeTab)) {
                                    if (recyclerViewFotos != null) {
                                        recyclerViewFotos.setVisibility(View.VISIBLE);
                                        recyclerViewFotos.post(() ->
                                                processAndDisplayAssets(globalAssetList, cleanBaseUrl, apiKey, recyclerViewFotos)
                                        );
                                    }
                                } else {
                                    processAndDisplayAssets(globalAssetList, cleanBaseUrl, apiKey, recyclerViewFotos);
                                }
                            });
                        }
                    } else {
                        isLoading = false;
                        if (getActivity() != null) getActivity().runOnUiThread(() -> { if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false); });
                        if (page == 1) showErrorOnUI("Keine Bilder gefunden.");
                    }
                } else {
                    isLoading = false;
                    if (getActivity() != null) getActivity().runOnUiThread(() -> { if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false); });
                    if (page == 1) showErrorOnUI("Fehler vom Server (" + responseCode + "):\n" + responseText);
                }
            } catch (Exception e) {
                isLoading = false;
                if (getActivity() != null) getActivity().runOnUiThread(() -> { if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false); });
                if (page == 1) showErrorOnUI("Verbindungsfehler: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void processAndDisplayAssets(List<ImmichAsset> rawListToProcess, String baseUrl, String apiKey, RecyclerView targetRecyclerView) {
        if (getContext() == null || rawListToProcess == null || targetRecyclerView == null) return;

        Parcelable recyclerViewState = null;
        if (targetRecyclerView.getLayoutManager() != null) {
            recyclerViewState = targetRecyclerView.getLayoutManager().onSaveInstanceState();
        }

        List<ImmichAsset> combinedList = new ArrayList<>(rawListToProcess);

        HashSet<String> knownDeviceAssetIds = new HashSet<>();
        HashSet<String> knownFileNames = new HashSet<>();

        for (ImmichAsset remote : rawListToProcess) {
            if (remote.deviceAssetId != null) knownDeviceAssetIds.add(remote.deviceAssetId);
            if (remote.originalFileName != null) {
                String name = remote.originalFileName;
                if(name.lastIndexOf(".") > 0) name = name.substring(0, name.lastIndexOf("."));
                knownFileNames.add(name);
            }
        }

        if (globalAssetList != null) {
            for (ImmichAsset remote : globalAssetList) {
                if (remote.deviceAssetId != null) knownDeviceAssetIds.add(remote.deviceAssetId);
                if (remote.originalFileName != null) {
                    String name = remote.originalFileName;
                    if(name.lastIndexOf(".") > 0) name = name.substring(0, name.lastIndexOf("."));
                    knownFileNames.add(name);
                }
            }
        }

        if (targetRecyclerView == recyclerViewFotos) {
            for (ImmichAsset local : localMediaCache) {
                String localName = local.originalFileName;
                if(localName != null && localName.lastIndexOf(".") > 0) localName = localName.substring(0, localName.lastIndexOf("."));

                boolean alreadyUploaded = knownDeviceAssetIds.contains(local.deviceAssetId) ||
                        (localName != null && knownFileNames.contains(localName));

                if (!alreadyUploaded) {
                    combinedList.add(local);
                }
            }
        }

        List<ImmichAsset> sortedAssets = new ArrayList<>(combinedList);
        Collections.sort(sortedAssets, (a, b) -> {
            if (a.fileCreatedAt == null || b.fileCreatedAt == null) return 0;
            return b.fileCreatedAt.compareTo(a.fileCreatedAt);
        });

        List<GalleryItem> groupedItems = new ArrayList<>();
        String currentMonthYear = "";
        String currentDay = "";

        SimpleDateFormat parseFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.GERMAN);
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEE, dd. MMM yyyy", Locale.GERMAN);

        for (ImmichAsset asset : sortedAssets) {
            try {
                Date d = parseFormat.parse(asset.fileCreatedAt);
                if (d != null) {
                    String monthYear = monthYearFormat.format(d).toUpperCase();
                    String day = dayFormat.format(d);

                    if (!monthYear.equals(currentMonthYear)) {
                        groupedItems.add(new GalleryItem(GalleryItem.TYPE_MONTH_HEADER, monthYear, null));
                        currentMonthYear = monthYear;
                        currentDay = "";
                    }
                    if (!day.equals(currentDay)) {
                        groupedItems.add(new GalleryItem(GalleryItem.TYPE_DAY_HEADER, day, null));
                        currentDay = day;
                    }
                }
            } catch (Exception e) {}
            groupedItems.add(new GalleryItem(GalleryItem.TYPE_PHOTO, "", asset));
        }

        currentFotosAdapter = new FotosAdapter(getContext(), groupedItems, baseUrl, apiKey,
                clickedAsset -> {
                    int index = sortedAssets.indexOf(clickedAsset);
                    openFullscreen(sortedAssets, index, false);
                },
                selectedCount -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (selectedCount > 0) {
                                if (layoutSelectionBar != null) layoutSelectionBar.setVisibility(View.VISIBLE);
                                if (layoutSelectionBottomBar != null) layoutSelectionBottomBar.setVisibility(View.VISIBLE);
                                if (tvSelectionCount != null) tvSelectionCount.setText(selectedCount + (selectedCount == 1 ? " ausgewählt" : " ausgewählt"));
                                updateSelectionBottomBarUI();
                            } else {
                                if (layoutSelectionBar != null) layoutSelectionBar.setVisibility(View.GONE);
                                if (layoutSelectionBottomBar != null) layoutSelectionBottomBar.setVisibility(View.GONE);
                            }
                        });
                    }
                }
        );

        if (targetRecyclerView.getAdapter() != null && !(targetRecyclerView.getAdapter() instanceof FotosAdapter)) {
            targetRecyclerView.getRecycledViewPool().clear();
            targetRecyclerView.setAdapter(currentFotosAdapter);
        } else {
            targetRecyclerView.setAdapter(currentFotosAdapter);
        }

        if (recyclerViewState != null) {
            targetRecyclerView.getLayoutManager().onRestoreInstanceState(recyclerViewState);
        }
    }

    private String formatUrl(String url, boolean isLocal) {
        String formatted = url.trim();
        if (formatted.isEmpty()) return "";
        if (formatted.endsWith("/api")) formatted = formatted.substring(0, formatted.length() - 4);
        else if (formatted.endsWith("/api/")) formatted = formatted.substring(0, formatted.length() - 5);
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = (isLocal ? "http://" : "https://") + formatted;
        }
        return formatted;
    }

    public static class FullscreenPagerAdapter extends RecyclerView.Adapter<FullscreenPagerAdapter.PagerViewHolder> {
        private final Context context;
        private final List<ImmichAsset> assets;
        private final String baseUrl;
        private final String apiKey;
        private final boolean isStoryMode;

        public interface SwipeListener {
            void onSwipeUp();
            void onSwipeDown();
            void onSingleTap(float x, float width);
            void onLongPress();
            void onVideoEnded();
        }

        private final SwipeListener swipeListener;

        public FullscreenPagerAdapter(Context context, List<ImmichAsset> assets, String baseUrl, String apiKey, boolean isStoryMode, SwipeListener listener) {
            this.context = context;
            this.assets = assets;
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.apiKey = apiKey;
            this.isStoryMode = isStoryMode;
            this.swipeListener = listener;
        }

        @NonNull
        @Override
        public PagerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_fullscreen_foto, parent, false);
            return new PagerViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PagerViewHolder holder, int position) {
            ImmichAsset asset = assets.get(position);

            boolean isVideo = asset.type != null && asset.type.equals("VIDEO");

            holder.imageView.setVisibility(View.VISIBLE);
            if (holder.videoView != null) holder.videoView.setVisibility(View.GONE);
            if (holder.btnPlay != null) holder.btnPlay.setVisibility(View.GONE);

            if (asset.isLocalOnly && asset.localUri != null) {
                Glide.with(context).load(Uri.parse(asset.localUri)).fitCenter().into(holder.imageView);
            } else {
                String imageUrl = isVideo ? (baseUrl + "/api/assets/" + asset.id + "/thumbnail") : (baseUrl + "/api/assets/" + asset.id + "/original");
                GlideUrl glideUrl = new GlideUrl(imageUrl, new LazyHeaders.Builder().addHeader("x-api-key", apiKey).addHeader("Accept", "application/json").build());
                Glide.with(context).load(glideUrl).fitCenter().into(holder.imageView);
            }

            if (isVideo) {
                if (holder.exoPlayer == null) {
                    holder.exoPlayer = new ExoPlayer.Builder(context).build();
                    holder.videoView.setPlayer(holder.exoPlayer);
                    holder.exoPlayer.addListener(new Player.Listener() {
                        @Override
                        public void onPlaybackStateChanged(int playbackState) {
                            if (playbackState == Player.STATE_ENDED) {
                                swipeListener.onVideoEnded();
                            }
                        }
                    });
                }

                if (asset.isLocalOnly && asset.localUri != null) {
                    holder.exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(asset.localUri)));
                } else {
                    String videoUrl = baseUrl + "/api/assets/" + asset.id + "/original";
                    DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                            .setDefaultRequestProperties(Collections.singletonMap("x-api-key", apiKey));
                    MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(Uri.parse(videoUrl)));
                    holder.exoPlayer.setMediaSource(mediaSource);
                }
                holder.exoPlayer.prepare();

                if (isStoryMode) {
                    holder.imageView.setVisibility(View.GONE);
                    holder.videoView.setVisibility(View.VISIBLE);
                    holder.exoPlayer.setPlayWhenReady(true);
                } else if (holder.btnPlay != null) {
                    holder.btnPlay.setVisibility(View.VISIBLE);
                    holder.btnPlay.setOnClickListener(v -> {
                        holder.imageView.setVisibility(View.GONE);
                        holder.btnPlay.setVisibility(View.GONE);
                        holder.videoView.setVisibility(View.VISIBLE);
                        holder.exoPlayer.setPlayWhenReady(true);
                    });
                }
            }

            holder.imageView.setOnViewTapListener((view, x, y) -> swipeListener.onSingleTap(x, view.getWidth()));
            holder.imageView.setOnPhotoTapListener((view, x, y) -> swipeListener.onSingleTap(x * view.getWidth(), view.getWidth()));
            holder.imageView.setOnLongClickListener(v -> { swipeListener.onLongPress(); return true; });

            holder.imageView.setOnSingleFlingListener((MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) -> {
                if (e1 == null || e2 == null) return false;
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();

                if (Math.abs(diffY) > Math.abs(diffX)) {
                    if (diffY < -50 && Math.abs(velocityY) > 100) { swipeListener.onSwipeUp(); return true; }
                    else if (diffY > 50 && Math.abs(velocityY) > 100) { swipeListener.onSwipeDown(); return true; }
                }
                return false;
            });

            if (holder.videoView != null) {
                holder.videoView.setOnTouchListener(new View.OnTouchListener() {
                    private long downTime;
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            downTime = System.currentTimeMillis();
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {
                            if (System.currentTimeMillis() - downTime < 200) {
                                swipeListener.onSingleTap(event.getX(), v.getWidth());
                            }
                        }
                        return true;
                    }
                });
            }
        }

        @Override
        public void onViewRecycled(@NonNull PagerViewHolder holder) {
            super.onViewRecycled(holder);
            Glide.with(context).clear(holder.imageView);
            releasePlayer(holder);
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull PagerViewHolder holder) {
            super.onViewDetachedFromWindow(holder);
            releasePlayer(holder);
        }

        private void releasePlayer(PagerViewHolder holder) {
            if (holder.exoPlayer != null) {
                holder.exoPlayer.release();
                holder.exoPlayer = null;
            }
        }

        @Override public int getItemCount() { return assets.size(); }

        public static class PagerViewHolder extends RecyclerView.ViewHolder {
            com.github.chrisbanes.photoview.PhotoView imageView;
            PlayerView videoView;
            ExoPlayer exoPlayer;
            ImageView btnPlay;

            public PagerViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.image_fullscreen_item);
                videoView = itemView.findViewById(R.id.video_fullscreen_item);
                btnPlay = itemView.findViewById(R.id.btn_play_fullscreen_video);
            }
        }
    }
}