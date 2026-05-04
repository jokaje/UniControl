package com.example.unicontrol.fragments;

import android.content.ClipData;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
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
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

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

import com.bumptech.glide.Glide;
import com.example.unicontrol.R;
import com.example.unicontrol.adapters.AlbumAdapter;
import com.example.unicontrol.adapters.FotosAdapter;
import com.example.unicontrol.adapters.FullscreenPagerAdapter;
import com.example.unicontrol.adapters.MemoriesAdapter;
import com.example.unicontrol.adapters.PersonAdapter;
import com.example.unicontrol.models.GalleryItem;
import com.example.unicontrol.models.ImmichAlbum;
import com.example.unicontrol.models.ImmichAsset;
import com.example.unicontrol.models.ImmichPerson;
import com.example.unicontrol.models.requests.ImmichRequests;
import com.example.unicontrol.network.ImmichApi;
import com.example.unicontrol.network.RetrofitClient;
import com.example.unicontrol.utils.CryptoUtils;
import com.example.unicontrol.utils.NetworkUtils;
import com.example.unicontrol.utils.SettingsManager;
import com.example.unicontrol.viewmodels.FotosViewModel;
import com.example.unicontrol.viewmodels.SharedViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Response;

public class FotosFragment extends Fragment {

    private FotosViewModel fotosViewModel;
    private SettingsManager settingsManager; // NEU

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

    private View layoutFotosContent;
    private View layoutFotosSetup;
    private View layoutFotosIntroOverlay;

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

    private Handler descriptionHandler = new Handler(Looper.getMainLooper());
    private Runnable descriptionRunnable;
    private boolean isUpdatingDescriptionUI = false;
    private ViewPager2.OnPageChangeCallback pageChangeCallback;

    private boolean isStoryModeActive = false;
    private boolean isStoryPaused = false;
    private long lastTapTime = 0;

    private android.animation.ValueAnimator storyAnimator;
    private LinearLayout storyProgressContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_fotos, container, false);
    }

    private int getThemeColor() {
        if (getContext() == null) return Color.parseColor("#F49AC2");
        if (settingsManager == null) settingsManager = SettingsManager.getInstance(requireContext());

        String colorStr = settingsManager.getColorFotos();
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

    private FotosAdapter getActiveAdapter() {
        if ("Suche".equals(activeTab) && recyclerViewSearch != null && recyclerViewSearch.getVisibility() == View.VISIBLE) {
            if (recyclerViewSearch.getAdapter() instanceof FotosAdapter) {
                return (FotosAdapter) recyclerViewSearch.getAdapter();
            }
        } else if ("Fotos".equals(activeTab) && recyclerViewFotos != null && recyclerViewFotos.getVisibility() == View.VISIBLE) {
            if (recyclerViewFotos.getAdapter() instanceof FotosAdapter) {
                return (FotosAdapter) recyclerViewFotos.getAdapter();
            }
        }
        return currentFotosAdapter;
    }

    private void clearSelectionUI() {
        FotosAdapter adapter = getActiveAdapter();
        if (adapter != null) adapter.clearSelection();
        if (layoutSelectionBar != null) layoutSelectionBar.setVisibility(View.GONE);
        if (layoutSelectionBottomBar != null) layoutSelectionBottomBar.setVisibility(View.GONE);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        settingsManager = SettingsManager.getInstance(requireContext());
        fotosViewModel = new ViewModelProvider(this).get(FotosViewModel.class);
        setupObservers();

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (fullscreenOverlay != null && fullscreenOverlay.getVisibility() == View.VISIBLE) {
                    closeFullscreen();
                } else if (bottomSheetBehavior != null && (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED || bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HALF_EXPANDED)) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                } else if (layoutSelectionBar != null && layoutSelectionBar.getVisibility() == View.VISIBLE) {
                    clearSelectionUI();
                    refreshVisibleGrids();
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        view.setBackgroundColor(getThemeColor());

        layoutFotosContent = view.findViewById(R.id.layout_fotos_content);
        layoutFotosSetup = view.findViewById(R.id.layout_fotos_setup);
        layoutFotosIntroOverlay = view.findViewById(R.id.layout_fotos_intro_overlay);

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
                    syncFavoritesInBackground();
                    loadLocalAssetsAsync();
                    fotosViewModel.refreshPhotos(currentApiUrl, currentApiKey);
                    fotosViewModel.fetchMemories(currentApiUrl, currentApiKey);
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
                FotosAdapter adapter = getActiveAdapter();
                if (adapter != null) uploadMultipleAssets(adapter.getSelectedAssets());
            });
            ((LinearLayout) layoutSelectionBottomBar).addView(btnSelectionUploadLocal);
        }

        if (btnCloseSelection != null) btnCloseSelection.setOnClickListener(v -> {
            clearSelectionUI();
            refreshVisibleGrids();
        });

        if (btnSelectionShare != null) btnSelectionShare.setOnClickListener(v -> {
            FotosAdapter adapter = getActiveAdapter();
            if (adapter != null) shareAssets(adapter.getSelectedAssets());
        });

        if (btnSelectionAddTo != null) btnSelectionAddTo.setOnClickListener(v -> showMultiActionMenu());

        if (btnSelectionDelete != null) btnSelectionDelete.setOnClickListener(v -> {
            FotosAdapter adapter = getActiveAdapter();
            if (adapter != null) deleteAssets(adapter.getSelectedAssets());
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

    private void setupObservers() {
        fotosViewModel.getPhotos().observe(getViewLifecycleOwner(), assets -> {
            globalAssetList.clear();
            globalAssetList.addAll(assets);
            extractMetadataToCache(assets);

            if (tvPlaceholder != null) {
                if (assets.isEmpty()) {
                    tvPlaceholder.setVisibility(View.VISIBLE);
                    tvPlaceholder.setText(getString(R.string.fotos_empty));
                    if (recyclerViewFotos != null) recyclerViewFotos.setVisibility(View.GONE);
                } else {
                    tvPlaceholder.setVisibility(View.GONE);
                }
            }

            if ("Fotos".equals(activeTab)) {
                if (recyclerViewFotos != null) {
                    recyclerViewFotos.setVisibility(View.VISIBLE);
                    recyclerViewFotos.post(() -> processAndDisplayAssets(globalAssetList, currentApiUrl, currentApiKey, recyclerViewFotos));
                }
            } else {
                processAndDisplayAssets(globalAssetList, currentApiUrl, currentApiKey, recyclerViewFotos);
            }
        });

        fotosViewModel.getIsPhotosLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (swipeRefreshLayout != null && !isLoading) swipeRefreshLayout.setRefreshing(false);
        });

        fotosViewModel.getMemories().observe(getViewLifecycleOwner(), memories -> {
            if (memories != null && !memories.isEmpty() && recyclerViewMemories != null) {
                recyclerViewMemories.setVisibility(View.VISIBLE);
                String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;
                MemoriesAdapter memAdapter = new MemoriesAdapter(getContext(), memories, cleanBaseUrl, currentApiKey, memory -> {
                    openFullscreen(memory.assets, 0, true);
                });
                recyclerViewMemories.setAdapter(memAdapter);
            } else if (recyclerViewMemories != null) {
                recyclerViewMemories.setVisibility(View.GONE);
            }
        });

        fotosViewModel.getAlbums().observe(getViewLifecycleOwner(), albums -> {
            if (albums != null) {
                globalAlbumList = albums;
                if (!globalAlbumList.isEmpty()) {
                    recyclerViewAlbums.setVisibility(View.VISIBLE);
                    displayAlbums(globalAlbumList);
                } else {
                    Toast.makeText(getContext(), getString(R.string.fotos_no_albums), Toast.LENGTH_SHORT).show();
                }
            }
        });

        fotosViewModel.getIsAlbumsLoading().observe(getViewLifecycleOwner(), isAlbumsLoading -> {
            if (tvAlbumsLoading != null) tvAlbumsLoading.setVisibility(isAlbumsLoading ? View.VISIBLE : View.GONE);
        });

        fotosViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) showErrorOnUI(error);
        });

        fotosViewModel.getSearchResults().observe(getViewLifecycleOwner(), results -> {
            if (results == null) return;
            extractMetadataToCache(results);
            tvSearchLoading.setVisibility(View.GONE);

            if (results.isEmpty()) {
                showSearchError(getString(R.string.fotos_search_no_results));
                return;
            }

            Integer mode = fotosViewModel.getCurrentSearchMode().getValue();
            lastSearchMode = (mode != null) ? mode : 3;
            lastSearchResults = new ArrayList<>(results);

            recyclerViewSearch.setVisibility(View.VISIBLE);

            if (lastSearchMode == 4) {
                processAndDisplaySmartSearchResults(results, currentApiUrl, currentApiKey, recyclerViewSearch);
            } else {
                processAndDisplayAssets(results, currentApiUrl, currentApiKey, recyclerViewSearch);
            }
        });

        fotosViewModel.getPeople().observe(getViewLifecycleOwner(), people -> {
            if (people == null) return;
            tvSearchLoading.setVisibility(View.GONE);

            if (people.isEmpty()) {
                showSearchError("Keine sichtbaren Gesichter gefunden.");
                return;
            }

            recyclerViewSearch.setVisibility(View.VISIBLE);
            String cleanBaseUrl = currentApiUrl.endsWith("/") ? currentApiUrl.substring(0, currentApiUrl.length() - 1) : currentApiUrl;

            PersonAdapter pAdapter = new PersonAdapter(getContext(), people, cleanBaseUrl, currentApiKey, new PersonAdapter.PersonListener() {
                @Override public void onFaceClick(ImmichPerson person) {
                    prepareForSearchResults();
                    recyclerViewSearch.setVisibility(View.GONE);
                    tvSearchLoading.setVisibility(View.VISIBLE);
                    tvSearchLoading.setText("Lade Bilder von " + (person.name.isEmpty() ? "dieser Person" : person.name) + "...");

                    ImmichRequests.SearchMetadata req = new ImmichRequests.SearchMetadata();
                    req.personIds = Collections.singletonList(person.id);
                    req.withExif = true;
                    fotosViewModel.searchMetadata(currentApiUrl, currentApiKey, req, 3);
                }
                @Override public void onNameEditClick(ImmichPerson person) {
                    showRenameDialog(person, people);
                }
            });
            recyclerViewSearch.setAdapter(pAdapter);
        });

        fotosViewModel.getSearchErrorMessage().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) showSearchError(errorMsg);
        });

        fotosViewModel.getSingleAssetUpdate().observe(getViewLifecycleOwner(), parsedAsset -> {
            if (parsedAsset != null && currentViewedAsset != null && currentViewedAsset.id.equals(parsedAsset.id)) {
                if (parsedAsset.description != null && !parsedAsset.description.isEmpty()) {
                    localDescriptions.put(parsedAsset.id, parsedAsset.description);
                }
                currentViewedAsset.description = parsedAsset.description;
                currentViewedAsset.isFavorite = parsedAsset.isFavorite;
                currentViewedAsset.isArchived = parsedAsset.isArchived;

                updateFullscreenUI();

                if (etDetailDescription != null) {
                    String currentText = etDetailDescription.getText().toString();
                    String finalDesc = parsedAsset.description != null ? parsedAsset.description : "";
                    if (!currentText.equals(finalDesc) && (currentText.trim().isEmpty() || !etDetailDescription.hasFocus())) {
                        isUpdatingDescriptionUI = true;
                        etDetailDescription.setText(finalDesc);
                        isUpdatingDescriptionUI = false;
                    }
                }
            }
        });
    }

    private void toggleFullscreenUI() {
        isFullscreenUiVisible = !isFullscreenUiVisible;
        int visibility = isFullscreenUiVisible ? View.VISIBLE : View.GONE;
        if (btnCloseFullscreen != null) btnCloseFullscreen.setVisibility(visibility);
        if (btnFavorite != null) btnFavorite.setVisibility(visibility);
        if (btnMoreOptions != null) btnMoreOptions.setVisibility(visibility);
        if (bottomMenuFullscreen != null) bottomMenuFullscreen.setVisibility(visibility);
    }

    private void loadLocalAssetsAsync() {
        if (getContext() == null) return;

        Set<String> bucketIds = settingsManager.getBackupAlbums();
        Set<String> blacklist = settingsManager.getPrefs().getStringSet("blacklisted_local_assets", new HashSet<>());

        fotosViewModel.loadLocalAssetsAsync(getContext(), currentApiUrl, currentApiKey, bucketIds, blacklist, new FotosViewModel.LocalScanCallback() {
            @Override
            public void onFinished(List<ImmichAsset> localAssets) {
                localMediaCache.clear();
                localMediaCache.addAll(localAssets);
                if (!globalAssetList.isEmpty() && "Fotos".equals(activeTab)) {
                    refreshVisibleGrids();
                }
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

        fotosViewModel.downloadAssetsForSharing(getContext(), currentApiUrl, currentApiKey, selected, new FotosViewModel.ShareCallback() {
            @Override
            public void onProgress(int current, int total) {
                if (selected.size() > 3 && current % 3 == 0) {
                    Toast.makeText(getContext(), "Lade Datei " + current + " von " + total + "...", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFinished(ArrayList<Uri> shareUris, String mimeType) {
                Intent shareIntent = new Intent();
                if (shareUris.size() == 1) {
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, shareUris.get(0));
                } else {
                    shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                    shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris);
                }
                shareIntent.setType(mimeType);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Teilen über..."));

                clearSelectionUI();
                refreshVisibleGrids();
            }

            @Override
            public void onError(String msg) {
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteAssets(List<ImmichAsset> selected) {
        if (selected == null || selected.isEmpty() || getContext() == null) return;

        List<String> cloudAssetIdsToDelete = new ArrayList<>();
        List<ImmichAsset> localOnlyAssetsToBlacklist = new ArrayList<>();

        for(ImmichAsset a : selected) {
            if(a.isLocalOnly) {
                localOnlyAssetsToBlacklist.add(a);
            } else {
                cloudAssetIdsToDelete.add(a.id);
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.fotos_delete_title, selected.size()))
                .setMessage(getString(R.string.fotos_delete_message))
                .setPositiveButton(getString(R.string.fotos_delete_confirm), (dialog, which) -> {
                    Toast.makeText(getContext(), getString(R.string.fotos_processing), Toast.LENGTH_SHORT).show();

                    Set<String> blacklist = new HashSet<>(settingsManager.getPrefs().getStringSet("blacklisted_local_assets", new HashSet<>()));

                    if (!localOnlyAssetsToBlacklist.isEmpty()) {
                        for (ImmichAsset a : localOnlyAssetsToBlacklist) {
                            if (a.deviceAssetId != null) blacklist.add(a.deviceAssetId);
                        }
                        settingsManager.getPrefs().edit().putStringSet("blacklisted_local_assets", blacklist).apply();
                        localMediaCache.removeAll(localOnlyAssetsToBlacklist);

                        if (cloudAssetIdsToDelete.isEmpty()) {
                            clearSelectionUI();
                            refreshVisibleGrids();
                            if (fullscreenOverlay != null && fullscreenOverlay.getVisibility() == View.VISIBLE) closeFullscreen();
                            Toast.makeText(getContext(), getString(R.string.fotos_removed_view), Toast.LENGTH_SHORT).show();
                        }
                    }

                    if (!cloudAssetIdsToDelete.isEmpty()) {
                        fotosViewModel.deleteAssets(currentApiUrl, currentApiKey, cloudAssetIdsToDelete, new FotosViewModel.ActionCallback() {
                            @Override
                            public void onSuccess() {
                                List<ImmichAsset> deletedAssets = new ArrayList<>();
                                for (ImmichAsset a : selected) if (!a.isLocalOnly) deletedAssets.add(a);

                                for (ImmichAsset a : deletedAssets) {
                                    if (a.deviceAssetId != null) blacklist.add(a.deviceAssetId);
                                }
                                settingsManager.getPrefs().edit().putStringSet("blacklisted_local_assets", blacklist).apply();

                                globalAssetList.removeAll(deletedAssets);
                                if (lastSearchResults != null) lastSearchResults.removeAll(deletedAssets);

                                for (ImmichAsset deletedAsset : deletedAssets) {
                                    java.util.Iterator<ImmichAsset> it = localMediaCache.iterator();
                                    while (it.hasNext()) {
                                        ImmichAsset local = it.next();
                                        if (local.deviceAssetId != null && local.deviceAssetId.equals(deletedAsset.deviceAssetId)) {
                                            it.remove();
                                        }
                                    }
                                }

                                clearSelectionUI();
                                refreshVisibleGrids();
                                if (fullscreenOverlay != null && fullscreenOverlay.getVisibility() == View.VISIBLE) closeFullscreen();
                                Toast.makeText(getContext(), getString(R.string.fotos_deleted_success, deletedAssets.size()), Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(String errorMsg) {
                                Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                })
                .setNegativeButton(getString(R.string.action_cancel), null).show();
    }

    private void showMultiActionMenu() {
        if (getContext() == null) return;
        FotosAdapter adapter = getActiveAdapter();
        if (adapter == null) return;
        List<ImmichAsset> selected = adapter.getSelectedAssets();
        if (selected.isEmpty()) return;

        for(ImmichAsset a : selected) {
            if(a.isLocalOnly) {
                Toast.makeText(getContext(), "Cloud-Aktionen sind erst möglich, wenn die Bilder (☁️) hochgeladen wurden.", Toast.LENGTH_LONG).show();
                clearSelectionUI();
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

        fotosViewModel.archiveAssets(currentApiUrl, currentApiKey, selected, toArchive, new FotosViewModel.ActionCallback() {
            @Override
            public void onSuccess() {
                Set<String> blacklist = new HashSet<>(settingsManager.getPrefs().getStringSet("blacklisted_local_assets", new HashSet<>()));

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
                settingsManager.getPrefs().edit().putStringSet("blacklisted_local_assets", blacklist).apply();

                clearSelectionUI();
                refreshVisibleGrids();
                closeFullscreen();
                Toast.makeText(getContext(), selected.size() + " Elemente verschoben!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMsg) {
                Toast.makeText(getContext(), errorMsg, Toast.LENGTH_SHORT).show();
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

    private void syncFavoritesInBackground() {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty()) return;

        ImmichRequests.SearchMetadata req = new ImmichRequests.SearchMetadata();
        req.isFavorite = true;
        req.withArchived = true;
        req.size = 1000;

        fotosViewModel.searchMetadata(currentApiUrl, currentApiKey, req, 0);
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
        prepareForSearchResults();
        currentViewedAlbumId = albumId;
        recyclerViewSearch.setVisibility(View.GONE);
        tvSearchLoading.setVisibility(View.VISIBLE);
        tvSearchLoading.setText("Lade Album '" + albumName + "'...");

        fotosViewModel.fetchAssetsForAlbum(currentApiUrl, currentApiKey, albumId);
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

        clearSelectionUI();

        activeTab = tabName;
        currentViewedAlbumId = null;

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
            if (globalAlbumList == null || globalAlbumList.isEmpty()) {
                fotosViewModel.fetchAlbums(currentApiUrl, currentApiKey);
            }
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
        if (view.findViewById(R.id.btn_search_personen) != null) {
            view.findViewById(R.id.btn_search_personen).setOnClickListener(v -> {
                prepareForSearchResults();
                recyclerViewSearch.setVisibility(View.GONE);
                tvSearchLoading.setVisibility(View.VISIBLE);
                tvSearchLoading.setText("Lade Personen aus der Cloud...");
                fotosViewModel.fetchPeople(currentApiUrl, currentApiKey);
            });
        }
        if (view.findViewById(R.id.btn_search_ort) != null) view.findViewById(R.id.btn_search_ort).setOnClickListener(v -> showLocationFilterBottomSheet());
        if (view.findViewById(R.id.btn_search_datum) != null) view.findViewById(R.id.btn_search_datum).setOnClickListener(v -> showDateSearchBottomSheet());
        if (view.findViewById(R.id.btn_search_kamera) != null) view.findViewById(R.id.btn_search_kamera).setOnClickListener(v -> showCloudSearchDialog("Kamera suchen", "z.B. iPhone, Sony..."));

        if (view.findViewById(R.id.btn_search_medientyp) != null) {
            view.findViewById(R.id.btn_search_medientyp).setOnClickListener(v -> {
                String[] types = {"🖼️  Nur Fotos aus der Cloud", "🎥  Nur Videos aus der Cloud"};
                new AlertDialog.Builder(getContext())
                        .setTitle("Medientyp suchen")
                        .setItems(types, (dialog, which) -> {
                            ImmichRequests.SearchMetadata req = new ImmichRequests.SearchMetadata();
                            req.withExif = true;
                            req.size = 1000;
                            if (which == 0) {
                                req.type = "IMAGE";
                                prepareForSearchResults();
                                tvSearchLoading.setVisibility(View.VISIBLE);
                                tvSearchLoading.setText("Lade Fotos aus der Cloud...");
                                fotosViewModel.searchMetadata(currentApiUrl, currentApiKey, req, 3);
                            }
                            if (which == 1) {
                                req.type = "VIDEO";
                                prepareForSearchResults();
                                tvSearchLoading.setVisibility(View.VISIBLE);
                                tvSearchLoading.setText("Lade Videos aus der Cloud...");
                                fotosViewModel.searchMetadata(currentApiUrl, currentApiKey, req, 3);
                            }
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
            view.findViewById(R.id.btn_search_videos).setOnClickListener(v -> {
                ImmichRequests.SearchMetadata req = new ImmichRequests.SearchMetadata();
                req.type = "VIDEO"; req.withExif = true; req.size = 1000;
                prepareForSearchResults();
                tvSearchLoading.setVisibility(View.VISIBLE);
                tvSearchLoading.setText("Lade alle Videos aus der Cloud...");
                fotosViewModel.searchMetadata(currentApiUrl, currentApiKey, req, 3);
            });
        }

        if (view.findViewById(R.id.btn_search_favorites) != null) {
            view.findViewById(R.id.btn_search_favorites).setOnClickListener(v -> {
                ImmichRequests.SearchMetadata req = new ImmichRequests.SearchMetadata();
                req.isFavorite = true; req.withExif = true; req.withArchived = true; req.size = 1000;
                prepareForSearchResults();
                tvSearchLoading.setVisibility(View.VISIBLE);
                tvSearchLoading.setText("Lade deine Cloud-Favoriten...");
                fotosViewModel.searchMetadata(currentApiUrl, currentApiKey, req, 0);
            });
        }

        if (view.findViewById(R.id.btn_search_archive) != null) {
            view.findViewById(R.id.btn_search_archive).setOnClickListener(v -> {
                ImmichRequests.SearchMetadata req = new ImmichRequests.SearchMetadata();
                req.withArchived = true; req.withExif = true; req.size = 1000; req.isFavorite = null;
                req.visibility = "archive";
                prepareForSearchResults();
                tvSearchLoading.setVisibility(View.VISIBLE);
                tvSearchLoading.setText("Lade dein Archiv...");
                fotosViewModel.searchMetadata(currentApiUrl, currentApiKey, req, 1);
            });
        }

        if (view.findViewById(R.id.btn_search_locked) != null) {
            view.findViewById(R.id.btn_search_locked).setOnClickListener(v -> promptForLockedFolder(true, false, null));
        }
    }

    private void fetchAvailableYearsFromServer(AutoCompleteTextView actvYear) {
        fotosViewModel.fetchAvailableYears(currentApiUrl, currentApiKey, new FotosViewModel.YearsCallback() {
            @Override
            public void onFinished(List<String> yearsList) {
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
                prepareForSearchResults();
                tvSearchLoading.setVisibility(View.VISIBLE);
                tvSearchLoading.setText("KI durchsucht Cloud...");
                fotosViewModel.searchSmart(currentApiUrl, currentApiKey, finalQuery);
                dialog.dismiss();
                return;
            }

            ImmichRequests.SearchMetadata req = new ImmichRequests.SearchMetadata();
            req.withExif = true;
            req.size = 1000;

            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            Calendar cal = Calendar.getInstance();

            if (selectedMode[0].equals("last_1")) {
                cal.add(Calendar.MONTH, -1);
                req.takenAfter = iso.format(cal.getTime());
            } else if (selectedMode[0].equals("last_3")) {
                cal.add(Calendar.MONTH, -3);
                req.takenAfter = iso.format(cal.getTime());
            } else if (selectedMode[0].equals("last_9")) {
                cal.add(Calendar.MONTH, -9);
                req.takenAfter = iso.format(cal.getTime());
            } else if (selectedMode[0].equals("year")) {
                String yearStr = actvYear.getText().toString().trim();
                req.takenAfter = yearStr + "-01-01T00:00:00.000Z";
                req.takenBefore = yearStr + "-12-31T23:59:59.999Z";
            }

            if (etSearchInput != null) {
                etSearchInput.setText(finalQuery);
                etSearchInput.clearFocus();
            }
            hideKeyboard();
            prepareForSearchResults();
            tvSearchLoading.setVisibility(View.VISIBLE);
            tvSearchLoading.setText("Lade Bilder aus dem Zeitraum...");
            fotosViewModel.searchMetadata(currentApiUrl, currentApiKey, req, 3);
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

        final HashMap<String, String> originalLocMap = new HashMap<>();

        fetchLocationsForDropdowns(actvCountry, actvState, actvCity, originalLocMap);

        btnClear.setOnClickListener(v -> {
            actvCountry.setText("", false);
            actvState.setText("", false);
            actvCity.setText("", false);
            actvCountry.clearFocus();
            actvState.clearFocus();
            actvCity.clearFocus();
            fetchLocationsForDropdowns(actvCountry, actvState, actvCity, originalLocMap);
        });

        btnApply.setOnClickListener(v -> {
            String selectedCountryDe = actvCountry.getText().toString().trim();
            String selectedStateDe = actvState.getText().toString().trim();
            String selectedCityDe = actvCity.getText().toString().trim();

            String originalCountry = originalLocMap.getOrDefault(selectedCountryDe, selectedCountryDe);
            String originalState = originalLocMap.getOrDefault(selectedStateDe, selectedStateDe);
            String originalCity = originalLocMap.getOrDefault(selectedCityDe, selectedCityDe);

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

    private void fetchLocationsForDropdowns(AutoCompleteTextView actvCountry, AutoCompleteTextView actvState, AutoCompleteTextView actvCity, HashMap<String, String> originalMap) {
        if (getActivity() != null) {
            actvCountry.setHint("⏳ Lade Orte...");
            actvState.setHint("⏳ Lade Orte...");
            actvCity.setHint("⏳ Lade Orte...");
        }

        fotosViewModel.fetchLocations(currentApiUrl, currentApiKey, new FotosViewModel.LocationsCallback() {
            @Override
            public void onFinished(FotosViewModel.LocationData data) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        originalMap.clear();
                        originalMap.putAll(data.displayToOriginalMap);
                        actvCountry.setHint("🌍 Land");
                        actvState.setHint("🗺️ Bundesland");
                        actvCity.setHint("🏙️ Stadt");
                        setupLocationDropdowns(data.locationMap, data.allCountries, data.allStates, data.allCities, actvCountry, actvState, actvCity);
                    });
                }
            }

            @Override
            public void onError(String msg) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        actvCountry.setHint("❌ " + msg);
                        actvState.setHint("🗺️ Bundesland");
                        actvCity.setHint("🏙️ Stadt");
                    });
                }
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

    private void applyCloudLocationFilter(String country, String state, String city) {
        if (country.isEmpty() && state.isEmpty() && city.isEmpty()) {
            Toast.makeText(getContext(), "Bitte mindestens einen Ort eingeben.", Toast.LENGTH_SHORT).show();
            return;
        }

        ImmichRequests.SearchMetadata req = new ImmichRequests.SearchMetadata();
        if (!country.isEmpty()) req.country = country;
        if (!state.isEmpty()) req.state = state;
        if (!city.isEmpty()) req.city = city;
        req.withExif = true;
        req.size = 1000;

        prepareForSearchResults();
        tvSearchLoading.setVisibility(View.VISIBLE);
        tvSearchLoading.setText("Durchsuche Cloud nach Ort...");
        fotosViewModel.searchMetadata(currentApiUrl, currentApiKey, req, 3);
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
                        prepareForSearchResults();
                        tvSearchLoading.setVisibility(View.VISIBLE);
                        tvSearchLoading.setText("KI durchsucht Cloud...");
                        fotosViewModel.searchSmart(currentApiUrl, currentApiKey, query);
                    }
                })
                .setNegativeButton("Abbrechen", null)
                .show();
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
                    if (!query.isEmpty()) {
                        prepareForSearchResults();
                        tvSearchLoading.setVisibility(View.VISIBLE);
                        tvSearchLoading.setText("KI durchsucht Cloud...");
                        fotosViewModel.searchSmart(currentApiUrl, currentApiKey, query);
                    }
                    hideKeyboard();
                    return true;
                }
                return false;
            });
        }
    }

    private void processAndDisplaySmartSearchResults(List<ImmichAsset> rawListToProcess, String baseUrl, String apiKey, RecyclerView targetRecyclerView) {
        if (getContext() == null || rawListToProcess == null || targetRecyclerView == null) return;

        // SICHERHEITSSPERRE: Wenn der Nutzer gerade in DIESER Liste Elemente ausgewählt hat,
        // dürfen wir den Adapter nicht überschreiben, da sonst das Menü ins Leere zeigt!
        if (targetRecyclerView.getAdapter() instanceof FotosAdapter) {
            FotosAdapter oldAdapter = (FotosAdapter) targetRecyclerView.getAdapter();
            List<ImmichAsset> currentlySelected = oldAdapter.getSelectedAssets();
            if (currentlySelected != null && !currentlySelected.isEmpty()) {
                return;
            }
        }

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

        targetRecyclerView.setAdapter(currentFotosAdapter);

        if (recyclerViewState != null) {
            targetRecyclerView.getLayoutManager().onRestoreInstanceState(recyclerViewState);
        }
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
                .setPositiveButton("Speichern", (dialog, which) -> {
                    fotosViewModel.updatePersonName(currentApiUrl, currentApiKey, person, input.getText().toString().trim());
                })
                .setNegativeButton("Abbrechen", null).show();
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
                    Boolean isPhotosLoading = fotosViewModel.getIsPhotosLoading().getValue();
                    boolean loading = isPhotosLoading != null && isPhotosLoading;

                    if (dy > 0 && !loading && !fotosViewModel.isLastPage()) {
                        if (layoutManager != null) {
                            int visibleItemCount = layoutManager.getChildCount();
                            int totalItemCount = layoutManager.getItemCount();
                            int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                            if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 30) {
                                fotosViewModel.loadNextPage(currentApiUrl, currentApiKey);
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

        fotosViewModel.removeAssetsFromAlbum(currentApiUrl, currentApiKey, albumId, Collections.singletonList(asset.id), new FotosViewModel.ActionCallback() {
            @Override
            public void onSuccess() {
                if (lastSearchResults != null) lastSearchResults.remove(asset);
                refreshVisibleGrids();
                closeFullscreen();
                Toast.makeText(getContext(), "Aus Album entfernt!", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onError(String errorMsg) {
                Toast.makeText(getContext(), errorMsg, Toast.LENGTH_SHORT).show();
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
                ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

                boolean isVideo = currentViewedAsset.type != null && currentViewedAsset.type.equals("VIDEO");
                String fileExtension = isVideo ? ".mp4" : ".jpg";
                String mimeType = isVideo ? "video/*" : "image/*";

                File sharedImagesDir = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "shared_images");
                if (!sharedImagesDir.exists()) sharedImagesDir.mkdirs();
                File fileToEdit = new File(sharedImagesDir, "edit_" + currentViewedAsset.id + fileExtension);

                Response<ResponseBody> response = api.downloadAssetOriginal(currentApiKey, currentViewedAsset.id).execute();

                if (response.isSuccessful() && response.body() != null) {
                    InputStream in = response.body().byteStream();
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
                ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

                Response<JsonElement> response = api.getAlbums(currentApiKey).execute();

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
                        int finalResponseCode = response.code();
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

        List<String> ids = new ArrayList<>();
        for (ImmichAsset a : assetsToModify) ids.add(a.id);

        fotosViewModel.addAssetsToAlbum(currentApiUrl, currentApiKey, albumId, ids, new FotosViewModel.ActionCallback() {
            @Override
            public void onSuccess() {
                clearSelectionUI();
                Toast.makeText(getContext(), "Erfolgreich zum Album hinzugefügt! 🎉", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onError(String errorMsg) {
                Toast.makeText(getContext(), errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void promptForLockedFolder(boolean isViewing, boolean isUnlocking, List<ImmichAsset> multipleAssets) {
        if (getContext() == null) return;
        String savedPin = settingsManager.getPrefs().getString("locked_folder_pin", "");

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
                    settingsManager.getPrefs().edit().putString("locked_folder_pin", enteredPin).apply();
                    Toast.makeText(getContext(), "PIN gespeichert!", Toast.LENGTH_SHORT).show();
                    if (isViewing) {
                        ImmichRequests.SearchMetadata req = new ImmichRequests.SearchMetadata();
                        req.withArchived = true; req.withExif = true; req.size = 1000;
                        req.visibility = "archive";
                        prepareForSearchResults();
                        tvSearchLoading.setVisibility(View.VISIBLE);
                        tvSearchLoading.setText("Tresor wird geöffnet...");
                        fotosViewModel.searchMetadata(currentApiUrl, currentApiKey, req, 2);
                    } else {
                        toggleLockAssets(isUnlocking, multipleAssets);
                    }
                } else {
                    Toast.makeText(getContext(), "PIN muss mind. 4 Zeichen haben.", Toast.LENGTH_SHORT).show();
                }
            } else {
                if (enteredPin.equals(savedPin)) {
                    if (isViewing) {
                        ImmichRequests.SearchMetadata req = new ImmichRequests.SearchMetadata();
                        req.withArchived = true; req.withExif = true; req.size = 1000;
                        req.visibility = "archive";
                        prepareForSearchResults();
                        tvSearchLoading.setVisibility(View.VISIBLE);
                        tvSearchLoading.setText("Tresor wird geöffnet...");
                        fotosViewModel.searchMetadata(currentApiUrl, currentApiKey, req, 2);
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
        clearSelectionUI();
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

        fotosViewModel.toggleFavorite(currentApiUrl, currentApiKey, currentViewedAsset.id, newFavStatus, new FotosViewModel.ActionCallback() {
            @Override
            public void onSuccess() {
                // Background success, no UI needed unless we want a delayed sync
            }
            @Override
            public void onError(String errorMsg) {
                // Rollback
                if (newFavStatus) {
                    favoriteAssets.remove(currentViewedAsset.id);
                    currentViewedAsset.isFavorite = false;
                } else {
                    favoriteAssets.add(currentViewedAsset.id);
                    currentViewedAsset.isFavorite = true;
                }
                updateFullscreenUI();
                if (recyclerViewSearch != null && recyclerViewSearch.getVisibility() == View.VISIBLE && lastSearchMode == 0) refreshVisibleGrids();
                Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
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
        FotosAdapter adapter = getActiveAdapter();
        if (adapter == null || layoutSelectionBottomBar == null) return;
        List<ImmichAsset> selected = adapter.getSelectedAssets();
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
        List<ImmichAsset> list = new ArrayList<>();
        list.add(asset);
        uploadMultipleAssets(list);
    }

    private void uploadMultipleAssets(List<ImmichAsset> assetsToUpload) {
        if (assetsToUpload == null || assetsToUpload.isEmpty() || currentApiUrl.isEmpty() || currentApiKey.isEmpty() || getContext() == null) return;

        List<ImmichAsset> locals = new ArrayList<>();
        for (ImmichAsset a : assetsToUpload) {
            if (a.isLocalOnly && a.localUri != null) locals.add(a);
        }

        if (locals.isEmpty()) return;

        Toast.makeText(getContext(), getString(R.string.fotos_upload_starting, locals.size()), Toast.LENGTH_LONG).show();

        CryptoUtils cryptoUtils = new CryptoUtils(requireContext());
        String deviceId = cryptoUtils.getDeviceId();
        if (deviceId == null || deviceId.isEmpty()) deviceId = "android-app";

        fotosViewModel.uploadAssets(getContext(), currentApiUrl, currentApiKey, deviceId, locals, new FotosViewModel.UploadCallback() {
            @Override
            public void onProgress(int current, int total) {
                if (locals.size() > 3 && current % 3 == 0) {
                    Toast.makeText(getContext(), "Upload " + current + " von " + total + "...", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFinished(int successCount, int failCount, List<ImmichAsset> uploadedAssets) {
                for (ImmichAsset asset : uploadedAssets) {
                    asset.isLocalOnly = false;
                    localMediaCache.remove(asset);
                    if (!globalAssetList.contains(asset)) {
                        globalAssetList.add(asset);
                    }
                }

                clearSelectionUI();
                updateFullscreenUI();
                refreshVisibleGrids();

                if (failCount == 0) {
                    Toast.makeText(getContext(), getString(R.string.fotos_upload_success, successCount), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getContext(), getString(R.string.fotos_upload_partial, successCount, failCount), Toast.LENGTH_LONG).show();
                }
            }
        });
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

                    descriptionRunnable = () -> fotosViewModel.updateDescription(currentApiUrl, currentApiKey, currentViewedAsset.id, newDescription);
                    descriptionHandler.postDelayed(descriptionRunnable, 1500);
                }
            });
        }
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

        fotosViewModel.fetchSingleAssetDetails(currentApiUrl, currentApiKey, asset.id);
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

        String savedSsid = settingsManager.getWifiSsid();
        String localUrl = settingsManager.getFotosLocal();
        String publicUrl = settingsManager.getFotosPublic();
        String apiKey = settingsManager.getFotosApiKey();

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
                        settingsManager.setModuleEnabled(SettingsManager.KEY_MOD_FOTOS, false);
                        Toast.makeText(getContext(), getString(R.string.fotos_module_hidden), Toast.LENGTH_SHORT).show();
                        if (getActivity() instanceof com.example.unicontrol.MainActivity) {
                            ((com.example.unicontrol.MainActivity) getActivity()).refreshMenu();
                            ((com.example.unicontrol.MainActivity) getActivity()).goToNextOnboardingTab(R.id.nav_fotos);
                        }
                    });
                }

                if (btnSetupSave != null) {
                    btnSetupSave.setOnClickListener(v -> {
                        settingsManager.setFotosLocal(etSetupLocal.getText().toString().trim());
                        settingsManager.setFotosPublic(etSetupPublic.getText().toString().trim());
                        settingsManager.setFotosApiKey(etSetupApiKey.getText().toString().trim());
                        Toast.makeText(getContext(), getString(R.string.fotos_connected), Toast.LENGTH_SHORT).show();

                        layoutFotosSetup.setVisibility(View.GONE);
                        if (layoutFotosContent != null) layoutFotosContent.setVisibility(View.VISIBLE);

                        loadAppropriateUrlAndKey();

                        if (getActivity() instanceof com.example.unicontrol.MainActivity) {
                            ((com.example.unicontrol.MainActivity) getActivity()).goToNextOnboardingTab(R.id.nav_fotos);
                        }
                    });
                }
            }
            return;
        } else {
            if (layoutFotosContent != null) layoutFotosContent.setVisibility(View.VISIBLE);
            if (layoutFotosSetup != null) layoutFotosSetup.setVisibility(View.GONE);
            if (layoutFotosIntroOverlay != null) layoutFotosIntroOverlay.setVisibility(View.GONE);
        }

        String currentSsid = NetworkUtils.getCurrentSsid(getContext());
        String targetUrl = "";

        if (!savedSsid.isEmpty() && currentSsid != null && currentSsid.equals(savedSsid) && !localUrl.isEmpty()) {
            targetUrl = formatUrl(localUrl, true);
        } else if (!publicUrl.isEmpty()) {
            targetUrl = formatUrl(publicUrl, false);
        } else if (!localUrl.isEmpty()) {
            targetUrl = formatUrl(localUrl, true);
        }

        if (!targetUrl.isEmpty() && !apiKey.isEmpty()) {
            boolean urlChanged = !targetUrl.equals(currentApiUrl) || !apiKey.equals(currentApiKey);
            boolean needsData = globalAssetList.isEmpty();

            if (urlChanged || needsData) {
                currentApiUrl = targetUrl;
                currentApiKey = apiKey;

                if (globalAssetList.isEmpty() && tvPlaceholder != null) {
                    tvPlaceholder.setVisibility(View.VISIBLE);
                    tvPlaceholder.setText(getString(R.string.fotos_loading_network));
                    if (recyclerViewFotos != null) recyclerViewFotos.setVisibility(View.GONE);
                }

                globalAssetList.clear();
                favoriteAssets.clear();
                loadLocalAssetsAsync();

                fotosViewModel.refreshPhotos(currentApiUrl, currentApiKey);
                fotosViewModel.fetchMemories(currentApiUrl, currentApiKey);

                syncFavoritesInBackground();
            } else if (recyclerViewFotos != null && "Fotos".equals(activeTab)) {
                recyclerViewFotos.post(this::refreshVisibleGrids);
            }
        } else {
            if (recyclerViewFotos != null) recyclerViewFotos.setVisibility(View.GONE);
            if (tvPlaceholder != null) {
                tvPlaceholder.setVisibility(View.VISIBLE);
                tvPlaceholder.setText(getString(R.string.fotos_missing_config));
            }
        }
    }

    private void processAndDisplayAssets(List<ImmichAsset> rawListToProcess, String baseUrl, String apiKey, RecyclerView targetRecyclerView) {
        if (getContext() == null || rawListToProcess == null || targetRecyclerView == null) return;

        if (targetRecyclerView.getAdapter() instanceof FotosAdapter) {
            FotosAdapter oldAdapter = (FotosAdapter) targetRecyclerView.getAdapter();
            List<ImmichAsset> currentlySelected = oldAdapter.getSelectedAssets();
            if (currentlySelected != null && !currentlySelected.isEmpty()) {
                return;
            }
        }

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

        targetRecyclerView.setAdapter(currentFotosAdapter);

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
}