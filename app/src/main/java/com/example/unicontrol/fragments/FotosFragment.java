package com.example.unicontrol.fragments;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.example.unicontrol.R;
import com.example.unicontrol.adapters.AlbumAdapter;
import com.example.unicontrol.adapters.FotosAdapter;
import com.example.unicontrol.adapters.PersonAdapter;
import com.example.unicontrol.models.GalleryItem;
import com.example.unicontrol.models.ImmichAlbum;
import com.example.unicontrol.models.ImmichAsset;
import com.example.unicontrol.models.ImmichPerson;
import com.example.unicontrol.utils.NetworkUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
import java.util.concurrent.Executors;

public class FotosFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerViewFotos;

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

    private CoordinatorLayout fullscreenOverlay;
    private ViewPager2 viewPagerFullscreen;
    private TextView btnCloseFullscreen;
    private ImageView btnFavorite;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private TextView tvDetailDate, tvDetailLocation, tvDetailFilename, tvDetailCamera;
    private EditText etDetailDescription;

    private TextView tabFotos, tabSuche, tabAlben, tabBibliothek;

    // Steuerelemente für die Auswahl-Leisten
    private View layoutSelectionBar;
    private View layoutSelectionBottomBar;
    private TextView tvSelectionCount;
    private View btnCloseSelection;
    private ImageView btnSelectionShare, btnSelectionAddTo, btnSelectionDelete;

    private FotosAdapter currentFotosAdapter;

    private String currentApiUrl = "";
    private String currentApiKey = "";
    private List<ImmichAsset> globalAssetList = new ArrayList<>();

    private List<ImmichAsset> lastSearchResults = new ArrayList<>();
    private int lastSearchMode = -1; // 0=Fav, 1=Archiv, 2=Tresor, 3=Normal, 4=Smart

    private ImmichAsset currentViewedAsset = null;

    private HashMap<String, String> localDescriptions = new HashMap<>();
    private HashSet<String> favoriteAssets = new HashSet<>();

    private Handler descriptionHandler = new Handler(Looper.getMainLooper());
    private Runnable descriptionRunnable;
    private boolean isUpdatingDescriptionUI = false;
    private ViewPager2.OnPageChangeCallback pageChangeCallback;

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
        try {
            return Color.parseColor(colorStr);
        } catch (Exception e) {
            return Color.parseColor("#F49AC2");
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.setBackgroundColor(getThemeColor());

        recyclerViewFotos = view.findViewById(R.id.recycler_view_fotos);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#8CA8B3"), getThemeColor());
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (!currentApiUrl.isEmpty() && !currentApiKey.isEmpty()) {
                    currentPage = 1;
                    isLastPage = false;
                    syncFavoritesInBackground();
                    fetchPhotosFromImmich(currentApiUrl, currentApiKey, currentPage);
                } else {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }

        // --- NEU: Auswahl-Leisten verbinden (Oben & Unten) ---
        layoutSelectionBar = view.findViewById(R.id.layout_selection_bar);
        layoutSelectionBottomBar = view.findViewById(R.id.layout_selection_bottom_bar);
        tvSelectionCount = view.findViewById(R.id.tv_selection_count);
        btnCloseSelection = view.findViewById(R.id.btn_close_selection);

        btnSelectionShare = view.findViewById(R.id.btn_selection_share);
        btnSelectionAddTo = view.findViewById(R.id.btn_selection_add_to);
        btnSelectionDelete = view.findViewById(R.id.btn_selection_delete);

        if (btnCloseSelection != null) btnCloseSelection.setOnClickListener(v -> {
            if (currentFotosAdapter != null) currentFotosAdapter.clearSelection();
        });

        // Buttons verknüpfen
        if (btnSelectionShare != null) btnSelectionShare.setOnClickListener(v -> {
            if (currentFotosAdapter != null) shareAssets(currentFotosAdapter.getSelectedAssets());
        });
        if (btnSelectionAddTo != null) btnSelectionAddTo.setOnClickListener(v -> showMultiActionMenu());
        if (btnSelectionDelete != null) btnSelectionDelete.setOnClickListener(v -> {
            if (currentFotosAdapter != null) deleteAssets(currentFotosAdapter.getSelectedAssets());
        });

        if (layoutSelectionBar != null) layoutSelectionBar.setBackgroundColor(getThemeColor());

        layoutSearch = view.findViewById(R.id.layout_search);
        layoutSearchCategories = view.findViewById(R.id.layout_search_categories);
        layoutSearchBottomButtons = view.findViewById(R.id.layout_search_bottom_buttons);
        etSearchInput = view.findViewById(R.id.et_search_input);
        tvSearchLoading = view.findViewById(R.id.tv_search_loading);
        recyclerViewSearch = view.findViewById(R.id.recycler_view_search);

        layoutAlbums = view.findViewById(R.id.layout_albums);
        etAlbumSearch = view.findViewById(R.id.et_album_search);
        tvAlbumsLoading = view.findViewById(R.id.tv_albums_loading);
        recyclerViewAlbums = view.findViewById(R.id.recycler_view_albums);
        recyclerViewAlbums.setLayoutManager(new GridLayoutManager(getContext(), 2));

        layoutLibrary = view.findViewById(R.id.layout_library);

        tvPlaceholder = view.findViewById(R.id.tv_fotos_placeholder);

        fullscreenOverlay = view.findViewById(R.id.fullscreen_overlay);
        viewPagerFullscreen = view.findViewById(R.id.view_pager_fullscreen);
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

    // --- NEU: ZENTRALE TEILEN-FUNKTION (EINZEL & MEHRFACH) ---
    private void shareAssets(List<ImmichAsset> selected) {
        if (selected == null || selected.isEmpty() || getContext() == null) return;

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

                // Kurze Info beim Download von vielen Dateien
                if (getActivity() != null && selected.size() > 3 && progress % 3 == 0) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Lade Datei " + progress + " von " + selected.size() + "...", Toast.LENGTH_SHORT).show());
                }

                try {
                    String downloadUrl = cleanBaseUrl + "/api/assets/" + asset.id + "/original";
                    boolean isVideo = asset.type != null && asset.type.equals("VIDEO");
                    if (isVideo) hasVideo = true; else hasImage = true;

                    String fileExtension = isVideo ? ".mp4" : ".jpg";
                    File fileToShare = new File(sharedImagesDir, "share_" + asset.id + fileExtension);

                    // Nur laden, wenn die Datei nicht von einem vorherigen Teilen noch im Cache liegt
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

                    // Android braucht beim Teilen von 1 vs mehreren Dateien leicht unterschiedliche Befehle
                    if (uriList.size() == 1) {
                        shareIntent.setAction(Intent.ACTION_SEND);
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uriList.get(0));
                    } else {
                        shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
                    }

                    String mimeType = "*/*"; // Gemischt
                    if (finalHasImage && !finalHasVideo) mimeType = "image/*";
                    if (finalHasVideo && !finalHasImage) mimeType = "video/*";

                    shareIntent.setType(mimeType);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(shareIntent, "Teilen über..."));

                    // Auswahl nach dem Teilen zurücksetzen
                    if (currentFotosAdapter != null) currentFotosAdapter.clearSelection();
                });
            } else {
                if (getActivity() != null) getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Fehler beim Download", Toast.LENGTH_SHORT).show());
            }
        });
    }

    // --- NEU: ZENTRALE LÖSCHFUNKTION (EINZEL & MEHRFACH) ---
    private void deleteAssets(List<ImmichAsset> selected) {
        if (selected == null || selected.isEmpty() || getContext() == null) return;

        new AlertDialog.Builder(getContext())
                .setTitle(selected.size() + (selected.size() == 1 ? " Foto löschen" : " Fotos löschen"))
                .setMessage("In den Papierkorb deines Immich-Servers verschieben?")
                .setPositiveButton("Löschen", (dialog, which) -> {
                    Toast.makeText(getContext(), "Wird gelöscht...", Toast.LENGTH_SHORT).show();
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
                            for (int i = 0; i < selected.size(); i++) {
                                ids.append("\"").append(selected.get(i).id).append("\"");
                                if (i < selected.size() - 1) ids.append(",");
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
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        globalAssetList.removeAll(selected);
                                        if (lastSearchResults != null) lastSearchResults.removeAll(selected);
                                        if (currentFotosAdapter != null) currentFotosAdapter.clearSelection();
                                        refreshVisibleGrids();
                                        if (fullscreenOverlay != null && fullscreenOverlay.getVisibility() == View.VISIBLE) {
                                            closeFullscreen();
                                        }
                                        Toast.makeText(getContext(), selected.size() + (selected.size() == 1 ? " Element gelöscht" : " Elemente gelöscht"), Toast.LENGTH_SHORT).show();
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
                })
                .setNegativeButton("Abbrechen", null).show();
    }

    private void showMultiActionMenu() {
        if (getContext() == null || currentFotosAdapter == null) return;
        List<ImmichAsset> selected = currentFotosAdapter.getSelectedAssets();
        if (selected.isEmpty()) return;
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
                    for (ImmichAsset asset : selected) {
                        asset.isArchived = toArchive;
                        if (!toArchive && !globalAssetList.contains(asset)) {
                            globalAssetList.add(asset);
                        }
                    }

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
    }

    private void prepareForSearchResults() {
        hideAllMenusForSearch();
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

        Executors.newSingleThreadExecutor().execute(() -> {
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

                if (tabFotos != null && tabFotos.getTypeface() != null && tabFotos.getTypeface().isBold()) selectTab(tabFotos, "Fotos");
                else if (tabSuche != null && tabSuche.getTypeface() != null && tabSuche.getTypeface().isBold()) selectTab(tabSuche, "Suche");
                else if (tabAlben != null && tabAlben.getTypeface() != null && tabAlben.getTypeface().isBold()) selectTab(tabAlben, "Alben");
                else if (tabBibliothek != null && tabBibliothek.getTypeface() != null && tabBibliothek.getTypeface().isBold()) selectTab(tabBibliothek, "Bibliothek");
            });
        }
    }

    private void showSearchError(String msg) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (tvSearchLoading != null) tvSearchLoading.setVisibility(View.GONE);
                Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();

                if (tabFotos != null && tabFotos.getTypeface() != null && tabFotos.getTypeface().isBold()) selectTab(tabFotos, "Fotos");
                else if (tabSuche != null && tabSuche.getTypeface() != null && tabSuche.getTypeface().isBold()) selectTab(tabSuche, "Suche");
                else if (tabAlben != null && tabAlben.getTypeface() != null && tabAlben.getTypeface().isBold()) selectTab(tabAlben, "Alben");
                else if (tabBibliothek != null && tabBibliothek.getTypeface() != null && tabBibliothek.getTypeface().isBold()) selectTab(tabBibliothek, "Bibliothek");
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
                if (recyclerViewFotos != null) recyclerViewFotos.setVisibility(View.GONE);
                if (layoutSearch != null) layoutSearch.setVisibility(View.GONE);
                if (layoutAlbums != null) layoutAlbums.setVisibility(View.GONE);
                if (layoutLibrary != null) layoutLibrary.setVisibility(View.GONE);
            });
        }
    }

    private void selectTab(TextView selectedTab, String tabName) {
        if (getContext() == null) return;

        if (currentFotosAdapter != null) currentFotosAdapter.clearSelection();

        TextView[] allTabs = {tabFotos, tabSuche, tabAlben, tabBibliothek};
        for (TextView tab : allTabs) {
            if (tab != null) {
                tab.setBackground(null);
                tab.setTextColor(Color.parseColor("#555555"));
                tab.setTypeface(null, Typeface.NORMAL);
            }
        }
        if (selectedTab != null) {
            selectedTab.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.bg_pill_selected));
            selectedTab.setTextColor(Color.WHITE);
            selectedTab.setTypeface(null, Typeface.BOLD);
        }

        if (layoutSearch == null || recyclerViewFotos == null || layoutAlbums == null || layoutLibrary == null || tvPlaceholder == null) return;

        recyclerViewFotos.setVisibility(View.GONE);
        layoutSearch.setVisibility(View.GONE);
        layoutAlbums.setVisibility(View.GONE);
        layoutLibrary.setVisibility(View.GONE);
        tvPlaceholder.setVisibility(View.GONE);
        if (recyclerViewSearch != null) recyclerViewSearch.setVisibility(View.GONE);

        if (tabName.equals("Fotos")) {
            recyclerViewFotos.setVisibility(View.VISIBLE);
            hideKeyboard();
            tvPlaceholder.setVisibility(globalAssetList == null || globalAssetList.isEmpty() ? View.VISIBLE : View.GONE);
        } else if (tabName.equals("Suche")) {
            layoutSearch.setVisibility(View.VISIBLE);
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
        btnApply.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#8CA8B3")));
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

            v.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#8CA8B3")));
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

            if (etSearchInput != null) {
                etSearchInput.setText(finalQuery);
                etSearchInput.clearFocus();
            }
            hideKeyboard();
            performCloudMetadataSearch("{\"query\": \"" + finalQuery + "\", \"q\": \"" + finalQuery + "\", \"withExif\": true}", "Suchen...");
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showLocationFilterBottomSheet() {
        if (getContext() == null || globalAssetList == null) return;

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

        HashSet<String> countriesSet = new HashSet<>();
        HashSet<String> statesSet = new HashSet<>();
        HashSet<String> citiesSet = new HashSet<>();

        for (ImmichAsset asset : globalAssetList) {
            if (asset.exifInfo != null) {
                if (asset.exifInfo.country != null && !asset.exifInfo.country.trim().isEmpty()) countriesSet.add(asset.exifInfo.country.trim());
                if (asset.exifInfo.state != null && !asset.exifInfo.state.trim().isEmpty()) statesSet.add(asset.exifInfo.state.trim());
                if (asset.exifInfo.city != null && !asset.exifInfo.city.trim().isEmpty()) citiesSet.add(asset.exifInfo.city.trim());
            }
        }

        List<String> countriesList = new ArrayList<>(countriesSet); Collections.sort(countriesList);
        List<String> statesList = new ArrayList<>(statesSet); Collections.sort(statesList);
        List<String> citiesList = new ArrayList<>(citiesSet); Collections.sort(citiesList);

        ArrayAdapter<String> countryAdapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_dropdown_item_1line, countriesList) {
            @NonNull @Override public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ((TextView) view).setTextColor(Color.parseColor("#333333"));
                return view;
            }
            @Override public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                ((TextView) view).setTextColor(Color.parseColor("#333333"));
                return view;
            }
        };
        actvCountry.setAdapter(countryAdapter);

        ArrayAdapter<String> stateAdapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_dropdown_item_1line, statesList) {
            @NonNull @Override public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ((TextView) view).setTextColor(Color.parseColor("#333333"));
                return view;
            }
            @Override public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                ((TextView) view).setTextColor(Color.parseColor("#333333"));
                return view;
            }
        };
        actvState.setAdapter(stateAdapter);

        ArrayAdapter<String> cityAdapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_dropdown_item_1line, citiesList) {
            @NonNull @Override public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ((TextView) view).setTextColor(Color.parseColor("#333333"));
                return view;
            }
            @Override public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                ((TextView) view).setTextColor(Color.parseColor("#333333"));
                return view;
            }
        };
        actvCity.setAdapter(cityAdapter);

        actvCountry.setThreshold(0);
        actvCountry.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) actvCountry.showDropDown();
            return false;
        });

        actvState.setThreshold(0);
        actvState.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) actvState.showDropDown();
            return false;
        });

        actvCity.setThreshold(0);
        actvCity.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) actvCity.showDropDown();
            return false;
        });

        btnClear.setOnClickListener(v -> {
            actvCountry.setText("", false);
            actvState.setText("", false);
            actvCity.setText("", false);
            actvCountry.clearFocus();
            actvState.clearFocus();
            actvCity.clearFocus();
        });

        btnApply.setOnClickListener(v -> {
            String selectedCountry = actvCountry.getText().toString().trim();
            String selectedState = actvState.getText().toString().trim();
            String selectedCity = actvCity.getText().toString().trim();
            applyCloudLocationFilter(selectedCountry, selectedState, selectedCity);
            dialog.dismiss();
        });

        dialog.show();
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

        // --- NEU: ADAPTER-AUFRUF MIT CALLBACK FÜR MULTI-SELECT ---
        currentFotosAdapter = new FotosAdapter(getContext(), relevanceItems, baseUrl, apiKey,
                clickedAsset -> {
                    int index = rawListToProcess.indexOf(clickedAsset);
                    openFullscreen(rawListToProcess, index);
                },
                selectedCount -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (selectedCount > 0) {
                                if (layoutSelectionBar != null) layoutSelectionBar.setVisibility(View.VISIBLE);
                                if (layoutSelectionBottomBar != null) layoutSelectionBottomBar.setVisibility(View.VISIBLE);
                                if (tvSelectionCount != null) tvSelectionCount.setText(selectedCount + (selectedCount == 1 ? " ausgewählt" : " ausgewählt"));
                            } else {
                                if (layoutSelectionBar != null) layoutSelectionBar.setVisibility(View.GONE);
                                if (layoutSelectionBottomBar != null) layoutSelectionBottomBar.setVisibility(View.GONE);
                            }
                        });
                    }
                }
        );
        targetRecyclerView.swapAdapter(currentFotosAdapter, true);

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

        View moreOptions = view.findViewById(R.id.btn_more_options);
        if (moreOptions != null) moreOptions.setOnClickListener(v -> {
            if (getContext() == null) return;
            PopupMenu popup = new PopupMenu(getContext(), v);
            popup.getMenu().add("Details einblenden");
            popup.setOnMenuItemClickListener(item -> {
                if (item.getTitle().equals("Details") && bottomSheetBehavior != null) bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                return true;
            });
            popup.show();
        });

        View btnShare = view.findViewById(R.id.btn_share);
        if (btnShare != null) btnShare.setOnClickListener(v -> {
            if (currentViewedAsset == null || getContext() == null) return;
            List<ImmichAsset> singleList = new ArrayList<>();
            singleList.add(currentViewedAsset);
            shareAssets(singleList);
        });

        View btnEdit = view.findViewById(R.id.btn_edit);
        if (btnEdit != null) btnEdit.setOnClickListener(v -> editCurrentAsset());

        View btnAddTo = view.findViewById(R.id.btn_add_to);
        if (btnAddTo != null) btnAddTo.setOnClickListener(v -> {
            if (currentViewedAsset == null) return;
            List<ImmichAsset> singleList = new ArrayList<>();
            singleList.add(currentViewedAsset);
            showActionMenu(singleList);
        });

        if (view.findViewById(R.id.btn_delete) != null) view.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            if (currentViewedAsset == null) return;
            List<ImmichAsset> singleList = new ArrayList<>();
            singleList.add(currentViewedAsset);
            deleteAssets(singleList);
        });
    }

    private void editCurrentAsset() {
        if (currentViewedAsset == null || getContext() == null) return;

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
                            if (globalAlbumList != null && !globalAlbumList.isEmpty()) {
                                populateAlbumsInBottomSheet(dialog, tvTitle, container, assetsToModify);
                            } else {
                                tvTitle.setText("Keine Alben gefunden");
                                container.removeAllViews();
                            }
                        });
                    }
                } else {
                    if (getActivity() != null) getActivity().runOnUiThread(() -> {
                        tvTitle.setText("Fehler beim Laden");
                        container.removeAllViews();
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    tvTitle.setText("Netzwerkfehler");
                    container.removeAllViews();
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
                String json = "{\"assetIds\": [" + ids.toString() + "]}";

                conn.getOutputStream().write(json.getBytes("UTF-8"));

                int responseCode = conn.getResponseCode();

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

                if (responseCode == 200 || responseCode == 201 || responseCode == 204) {
                    if (getActivity() != null) getActivity().runOnUiThread(() -> {
                        if (currentFotosAdapter != null) currentFotosAdapter.clearSelection();
                        Toast.makeText(getContext(), "Erfolgreich zum Album hinzugefügt! 🎉", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    if (getActivity() != null) {
                        int finalResponseCode = responseCode;
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(), "Fehler beim Hinzufügen (" + finalResponseCode + ")", Toast.LENGTH_SHORT).show()
                        );
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

    private void toggleFavoriteStatus() {
        if (currentViewedAsset == null) return;

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
    }

    private void setupDescriptionSaver() {
        if (etDetailDescription != null) {
            etDetailDescription.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (isUpdatingDescriptionUI || currentViewedAsset == null) return;

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

    private void openFullscreen(List<ImmichAsset> contextList, int clickedPosition) {
        if (contextList == null || getContext() == null || viewPagerFullscreen == null || fullscreenOverlay == null) return;

        FullscreenPagerAdapter pagerAdapter = new FullscreenPagerAdapter(getContext(), contextList, currentApiUrl, currentApiKey, new FullscreenPagerAdapter.SwipeListener() {
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
        });

        viewPagerFullscreen.setAdapter(pagerAdapter);

        if (pageChangeCallback != null) {
            viewPagerFullscreen.unregisterOnPageChangeCallback(pageChangeCallback);
        }
        pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentViewedAsset = contextList.get(position);
                fillDetailsSheet(currentViewedAsset);
                updateFullscreenUI();
            }
        };
        viewPagerFullscreen.registerOnPageChangeCallback(pageChangeCallback);

        viewPagerFullscreen.setCurrentItem(clickedPosition, false);

        if (bottomSheetBehavior != null) bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        fullscreenOverlay.setAlpha(0f);
        fullscreenOverlay.setVisibility(View.VISIBLE);
        fullscreenOverlay.animate().alpha(1f).setDuration(250).start();
    }

    private void fetchSingleAssetDetails(String assetId) {
        if (currentApiUrl.isEmpty() || currentApiKey.isEmpty() || assetId == null) return;

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
            if (tvDetailCamera != null) tvDetailCamera.setText("Keine Kamera-Infos");
            if (tvDetailLocation != null) tvDetailLocation.setText("🗺️ Kein Standort");
        }

        if (etDetailDescription != null) {
            isUpdatingDescriptionUI = true;

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

            isUpdatingDescriptionUI = false;
        }

        fetchSingleAssetDetails(asset.id);
    }

    private void closeFullscreen() {
        if (bottomSheetBehavior != null) bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        if (fullscreenOverlay != null) {
            fullscreenOverlay.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                fullscreenOverlay.setVisibility(View.GONE);
                if (viewPagerFullscreen != null) viewPagerFullscreen.setAdapter(null);
                hideKeyboard();
                Glide.get(requireContext()).clearMemory();
            }).start();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) loadAppropriateUrlAndKey();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAppropriateUrlAndKey();
    }

    private void loadAppropriateUrlAndKey() {
        if (getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
        String savedSsid = prefs.getString(SettingsFragment.KEY_WIFI_SSID, "");
        String localUrl = prefs.getString(SettingsFragment.KEY_FOTOS_LOCAL, "");
        String publicUrl = prefs.getString(SettingsFragment.KEY_FOTOS_PUBLIC, "");
        String apiKey = prefs.getString(SettingsFragment.KEY_FOTOS_API_KEY, "");
        String currentSsid = NetworkUtils.getCurrentSsid(getContext());
        String targetUrl = "";

        if (!savedSsid.isEmpty() && currentSsid.equals(savedSsid) && !localUrl.isEmpty()) targetUrl = formatUrl(localUrl, true);
        else if (!publicUrl.isEmpty()) targetUrl = formatUrl(publicUrl, false);
        else if (!localUrl.isEmpty()) targetUrl = formatUrl(localUrl, true);

        if (!targetUrl.isEmpty() && !apiKey.isEmpty()) {
            if (!targetUrl.equals(currentApiUrl) || !apiKey.equals(currentApiKey)) {
                currentApiUrl = targetUrl;
                currentApiKey = apiKey;
                currentPage = 1;
                isLastPage = false;
                globalAssetList.clear();
                favoriteAssets.clear();
                fetchPhotosFromImmich(currentApiUrl, currentApiKey, currentPage);

                syncFavoritesInBackground();
            }
        } else {
            if (recyclerViewFotos != null) recyclerViewFotos.setVisibility(View.GONE);
            if (tvPlaceholder != null) {
                tvPlaceholder.setVisibility(View.VISIBLE);
                tvPlaceholder.setText("Bitte trage URL und API-Key in den Einstellungen ein.");
            }
        }
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

                        if (page == 1) globalAssetList = safeAssets;
                        else globalAssetList.addAll(safeAssets);

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                extractMetadataToCache(safeAssets);
                                isLoading = false;
                                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                                if (tvPlaceholder != null) tvPlaceholder.setVisibility(View.GONE);

                                if (tabFotos != null && tabFotos.getTypeface() != null && tabFotos.getTypeface().isBold()) {
                                    if (recyclerViewFotos != null) recyclerViewFotos.setVisibility(View.VISIBLE);
                                }

                                processAndDisplayAssets(globalAssetList, cleanBaseUrl, apiKey, recyclerViewFotos);
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

        List<ImmichAsset> sortedAssets = new ArrayList<>(rawListToProcess);
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

        // --- NEU: ADAPTER-AUFRUF MIT CALLBACK FÜR MULTI-SELECT ---
        currentFotosAdapter = new FotosAdapter(getContext(), groupedItems, baseUrl, apiKey,
                clickedAsset -> {
                    int index = sortedAssets.indexOf(clickedAsset);
                    openFullscreen(sortedAssets, index);
                },
                selectedCount -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (selectedCount > 0) {
                                if (layoutSelectionBar != null) layoutSelectionBar.setVisibility(View.VISIBLE);
                                if (layoutSelectionBottomBar != null) layoutSelectionBottomBar.setVisibility(View.VISIBLE);
                                if (tvSelectionCount != null) tvSelectionCount.setText(selectedCount + (selectedCount == 1 ? " ausgewählt" : " ausgewählt"));
                            } else {
                                if (layoutSelectionBar != null) layoutSelectionBar.setVisibility(View.GONE);
                                if (layoutSelectionBottomBar != null) layoutSelectionBottomBar.setVisibility(View.GONE);
                            }
                        });
                    }
                }
        );
        targetRecyclerView.swapAdapter(currentFotosAdapter, true);

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

        public interface SwipeListener {
            void onSwipeUp();
            void onSwipeDown();
        }

        private final SwipeListener swipeListener;

        public FullscreenPagerAdapter(Context context, List<ImmichAsset> assets, String baseUrl, String apiKey, SwipeListener listener) {
            this.context = context;
            this.assets = assets;
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.apiKey = apiKey;
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

            String imageUrl;
            if (isVideo) imageUrl = baseUrl + "/api/assets/" + asset.id + "/thumbnail";
            else imageUrl = baseUrl + "/api/assets/" + asset.id + "/original";

            GlideUrl glideUrl = new GlideUrl(imageUrl, new LazyHeaders.Builder()
                    .addHeader("x-api-key", apiKey)
                    .addHeader("Accept", "application/json")
                    .build());

            Glide.with(context).load(glideUrl).fitCenter().into(holder.imageView);

            holder.imageView.setVisibility(View.VISIBLE);
            if (holder.videoView != null) holder.videoView.setVisibility(View.GONE);

            if (isVideo && holder.btnPlay != null) {
                holder.btnPlay.setVisibility(View.VISIBLE);
                holder.btnPlay.setOnClickListener(v -> {
                    holder.imageView.setVisibility(View.GONE);
                    holder.btnPlay.setVisibility(View.GONE);
                    if (holder.videoView != null) {
                        holder.videoView.setVisibility(View.VISIBLE);
                        String videoUrl = baseUrl + "/api/assets/" + asset.id + "/original";
                        Map<String, String> headers = new HashMap<>();
                        headers.put("x-api-key", apiKey);
                        holder.videoView.setVideoURI(Uri.parse(videoUrl), headers);
                        MediaController mc = new MediaController(context);
                        mc.setAnchorView(holder.videoView);
                        holder.videoView.setMediaController(mc);
                        holder.videoView.start();
                    }
                });
            } else if (holder.btnPlay != null) {
                holder.btnPlay.setVisibility(View.GONE);
            }

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
        }

        @Override
        public void onViewRecycled(@NonNull PagerViewHolder holder) {
            super.onViewRecycled(holder);
            Glide.with(context).clear(holder.imageView);
            if (holder.videoView != null && holder.videoView.isPlaying()) {
                holder.videoView.stopPlayback();
            }
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull PagerViewHolder holder) {
            super.onViewDetachedFromWindow(holder);
            if (holder.videoView != null && holder.videoView.isPlaying()) holder.videoView.stopPlayback();
        }

        @Override public int getItemCount() { return assets.size(); }

        public static class PagerViewHolder extends RecyclerView.ViewHolder {
            com.github.chrisbanes.photoview.PhotoView imageView;
            VideoView videoView;
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