package com.example.unicontrol.viewmodels;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.unicontrol.models.ImmichAlbum;
import com.example.unicontrol.models.ImmichAsset;
import com.example.unicontrol.models.ImmichMemory;
import com.example.unicontrol.models.ImmichPerson;
import com.example.unicontrol.models.requests.ImmichRequests;
import com.example.unicontrol.network.ImmichApi;
import com.example.unicontrol.network.RetrofitClient;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FotosViewModel extends ViewModel {

    // Zentraler Executor für sauberes Thread-Management
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Main Timeline
    private final MutableLiveData<List<ImmichAsset>> photosLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPhotosLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // Memories & Albums
    private final MutableLiveData<List<ImmichMemory>> memoriesLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<ImmichAlbum>> albumsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isAlbumsLoading = new MutableLiveData<>(false);

    // Search & People
    private final MutableLiveData<List<ImmichAsset>> searchResultsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentSearchMode = new MutableLiveData<>(-1);
    private final MutableLiveData<List<ImmichPerson>> peopleLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> searchErrorMessage = new MutableLiveData<>();

    // Single Asset Update
    private final MutableLiveData<ImmichAsset> singleAssetUpdateLiveData = new MutableLiveData<>();

    private final List<ImmichAsset> currentAssets = new ArrayList<>();
    private int currentPage = 1;
    private boolean isLastPage = false;
    private static final int PAGE_SIZE = 150;

    // --- Interfaces ---
    public interface ActionCallback {
        void onSuccess();
        void onError(String errorMsg);
    }

    public interface LocalScanCallback {
        void onFinished(List<ImmichAsset> localAssets);
    }

    public interface UploadCallback {
        void onProgress(int current, int total);
        void onFinished(int successCount, int failCount, List<ImmichAsset> uploadedAssets);
    }

    public interface ShareCallback {
        void onProgress(int current, int total);
        void onFinished(ArrayList<Uri> shareUris, String mimeType);
        void onError(String msg);
    }

    public static class LocationData {
        public HashMap<String, HashMap<String, HashSet<String>>> locationMap = new HashMap<>();
        public HashSet<String> allCountries = new HashSet<>();
        public HashSet<String> allStates = new HashSet<>();
        public HashSet<String> allCities = new HashSet<>();
        public HashMap<String, String> displayToOriginalMap = new HashMap<>();
    }

    public interface LocationsCallback {
        void onFinished(LocationData data);
        void onError(String msg);
    }

    public interface YearsCallback {
        void onFinished(List<String> years);
    }

    // --- LiveData Getters ---
    public LiveData<List<ImmichAsset>> getPhotos() { return photosLiveData; }
    public LiveData<Boolean> getIsPhotosLoading() { return isPhotosLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    public LiveData<List<ImmichMemory>> getMemories() { return memoriesLiveData; }
    public LiveData<List<ImmichAlbum>> getAlbums() { return albumsLiveData; }
    public LiveData<Boolean> getIsAlbumsLoading() { return isAlbumsLoading; }

    public LiveData<List<ImmichAsset>> getSearchResults() { return searchResultsLiveData; }
    public LiveData<Integer> getCurrentSearchMode() { return currentSearchMode; }
    public LiveData<List<ImmichPerson>> getPeople() { return peopleLiveData; }
    public LiveData<String> getSearchErrorMessage() { return searchErrorMessage; }

    public LiveData<ImmichAsset> getSingleAssetUpdate() { return singleAssetUpdateLiveData; }

    public boolean isLastPage() { return isLastPage; }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Stellt sicher, dass keine Threads weiterlaufen, wenn das ViewModel zerstört wird
        executor.shutdown();
    }

    // --- Methoden ---

    public void refreshPhotos(String baseUrl, String apiKey) {
        currentPage = 1;
        isLastPage = false;
        currentAssets.clear();
        loadNextPage(baseUrl, apiKey);
    }

    public void loadNextPage(String baseUrl, String apiKey) {
        if (isPhotosLoading.getValue() == Boolean.TRUE || isLastPage || baseUrl.isEmpty() || apiKey.isEmpty()) return;
        isPhotosLoading.setValue(true);

        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

        ImmichRequests.SearchMetadata req = new ImmichRequests.SearchMetadata();
        req.size = PAGE_SIZE;
        req.page = currentPage;
        req.withExif = true;

        api.searchMetadata(apiKey, req).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                isPhotosLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    List<ImmichAsset> newAssets = parseAssetsFromJson(response.body());
                    if (newAssets.size() < PAGE_SIZE) {
                        isLastPage = true;
                    }

                    List<ImmichAsset> safeAssets = new ArrayList<>();
                    for (ImmichAsset a : newAssets) {
                        String d = a.description;
                        if (d == null && a.exifInfo != null) d = a.exifInfo.description;
                        if (d == null && a.exifInfo != null) d = a.exifInfo.imageDescription;
                        boolean isLocked = (d != null && d.toLowerCase().contains("#locked"));
                        boolean isArchived = (a.isArchived != null && a.isArchived);

                        if (!isLocked && !isArchived) safeAssets.add(a);
                    }

                    if (currentPage == 1) currentAssets.clear();
                    currentAssets.addAll(safeAssets);
                    photosLiveData.setValue(new ArrayList<>(currentAssets));
                    currentPage++;
                } else {
                    errorMessage.setValue("Fehler vom Server (" + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                isPhotosLoading.setValue(false);
                errorMessage.setValue("Verbindungsfehler: " + t.getMessage());
            }
        });
    }

    public void fetchMemories(String baseUrl, String apiKey) {
        if (baseUrl.isEmpty() || apiKey.isEmpty()) return;
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'00:00:00.000'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        String today = iso.format(new Date());

        api.getMemories(apiKey, today).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (response.code() == 404 || response.code() == 400 || response.code() == 405) {
                    api.getMemories(apiKey, null).enqueue(new Callback<JsonElement>() {
                        @Override
                        public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                            handleMemoriesResponse(response);
                        }
                        @Override
                        public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                            memoriesLiveData.setValue(null);
                        }
                    });
                } else {
                    handleMemoriesResponse(response);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                memoriesLiveData.setValue(null);
            }
        });
    }

    private void handleMemoriesResponse(Response<JsonElement> response) {
        if (response.isSuccessful() && response.body() != null) {
            JsonElement element = response.body();
            if (element.isJsonArray()) {
                JsonArray jsonArray = element.getAsJsonArray();
                List<ImmichMemory> memoriesList = new ArrayList<>();
                for (JsonElement item : jsonArray) {
                    if (!item.isJsonObject()) continue;
                    JsonObject obj = item.getAsJsonObject();
                    ImmichMemory memory = new ImmichMemory();
                    memory.id = obj.has("id") && !obj.get("id").isJsonNull() ? obj.get("id").getAsString() : "";

                    String title = "";
                    if (obj.has("title") && !obj.get("title").isJsonNull()) title = obj.get("title").getAsString().trim();
                    if (title.isEmpty() && obj.has("data") && obj.get("data").isJsonObject()) {
                        JsonObject dataObj = obj.getAsJsonObject("data");
                        if (dataObj.has("title") && !dataObj.get("title").isJsonNull()) title = dataObj.get("title").getAsString().trim();
                    }
                    if (title.isEmpty() && obj.has("name") && !obj.get("name").isJsonNull()) title = obj.get("name").getAsString().trim();
                    if (title.isEmpty()) title = "Erinnerung";
                    memory.title = title;

                    if (obj.has("assets") && obj.get("assets").isJsonArray()) {
                        memory.assets = new Gson().fromJson(obj.getAsJsonArray("assets"), new TypeToken<List<ImmichAsset>>(){}.getType());
                    }

                    if (memory.assets != null && !memory.assets.isEmpty()) memoriesList.add(memory);
                }
                memoriesLiveData.setValue(memoriesList);
                return;
            }
        }
        memoriesLiveData.setValue(null);
    }

    public void fetchAlbums(String baseUrl, String apiKey) {
        if (baseUrl.isEmpty() || apiKey.isEmpty()) return;
        isAlbumsLoading.setValue(true);
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

        api.getAlbums(apiKey).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                isAlbumsLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    JsonElement root = response.body();
                    JsonArray albumsArray = null;
                    if (root.isJsonArray()) albumsArray = root.getAsJsonArray();
                    else if (root.isJsonObject()) {
                        if (root.getAsJsonObject().has("data")) albumsArray = root.getAsJsonObject().getAsJsonArray("data");
                        else if (root.getAsJsonObject().has("albums")) albumsArray = root.getAsJsonObject().getAsJsonArray("albums");
                    }
                    if (albumsArray != null) {
                        List<ImmichAlbum> list = new Gson().fromJson(albumsArray, new TypeToken<List<ImmichAlbum>>(){}.getType());
                        albumsLiveData.setValue(list);
                    } else {
                        albumsLiveData.setValue(new ArrayList<>());
                    }
                } else {
                    albumsLiveData.setValue(null);
                    errorMessage.setValue("Fehler beim Laden der Alben (" + response.code() + ")");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                isAlbumsLoading.setValue(false);
                albumsLiveData.setValue(null);
                errorMessage.setValue("Verbindungsfehler zu Alben.");
            }
        });
    }

    public void searchMetadata(String baseUrl, String apiKey, ImmichRequests.SearchMetadata req, int mode) {
        if (baseUrl.isEmpty() || apiKey.isEmpty()) return;
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

        api.searchMetadata(apiKey, req).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ImmichAsset> assets = parseAssetsFromJson(response.body());
                    List<ImmichAsset> filteredList = new ArrayList<>();

                    for (ImmichAsset a : assets) {
                        String d = a.description;
                        if (d == null && a.exifInfo != null) d = a.exifInfo.description;
                        if (d == null && a.exifInfo != null) d = a.exifInfo.imageDescription;

                        boolean isLocked = (d != null && d.toLowerCase().contains("#locked"));
                        boolean isArchived = (a.isArchived != null && a.isArchived);
                        boolean isFav = (a.isFavorite != null && a.isFavorite);

                        if (mode == 1) { // Archiv
                            if (isArchived && !isLocked) filteredList.add(a);
                        } else if (mode == 2) { // Tresor
                            if (isLocked) filteredList.add(a);
                        } else if (mode == 0) { // Favoriten
                            if (isFav && !isLocked && !isArchived) filteredList.add(a);
                        } else { // Normal (3)
                            if (!isLocked && !isArchived) filteredList.add(a);
                        }
                    }
                    currentSearchMode.setValue(mode);
                    searchResultsLiveData.setValue(filteredList);
                } else {
                    searchErrorMessage.setValue("Fehler vom Server (" + response.code() + ")");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                searchErrorMessage.setValue("Verbindungsfehler zur Cloud: " + t.getMessage());
            }
        });
    }

    public void searchSmart(String baseUrl, String apiKey, String query) {
        if (baseUrl.isEmpty() || apiKey.isEmpty()) return;
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

        ImmichRequests.SmartSearch req = new ImmichRequests.SmartSearch();
        req.query = query;
        req.q = query;
        req.withExif = true;

        api.searchSmart(apiKey, req).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ImmichAsset> assets = parseAssetsFromJson(response.body());
                    List<ImmichAsset> filteredList = new ArrayList<>();

                    for (ImmichAsset a : assets) {
                        String d = a.description;
                        if (d == null && a.exifInfo != null) d = a.exifInfo.description;
                        if (d == null && a.exifInfo != null) d = a.exifInfo.imageDescription;
                        boolean isLocked = (d != null && d.toLowerCase().contains("#locked"));
                        boolean isArchived = (a.isArchived != null && a.isArchived);

                        if (!isLocked && !isArchived) filteredList.add(a);
                    }
                    currentSearchMode.setValue(4); // 4 = Smart Search
                    searchResultsLiveData.setValue(filteredList);
                } else {
                    searchErrorMessage.setValue("Fehler vom Server (" + response.code() + ")");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                searchErrorMessage.setValue("Verbindungsfehler zur Cloud: " + t.getMessage());
            }
        });
    }

    public void fetchPeople(String baseUrl, String apiKey) {
        if (baseUrl.isEmpty() || apiKey.isEmpty()) return;
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

        api.getPeople(apiKey).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (response.code() == 404 || response.code() == 405) {
                    api.getPeopleLegacy(apiKey).enqueue(new Callback<JsonElement>() {
                        @Override
                        public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                            handlePeopleResponse(response);
                        }
                        @Override
                        public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                            searchErrorMessage.setValue("Verbindungsfehler zur Personen-API.");
                        }
                    });
                } else {
                    handlePeopleResponse(response);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                searchErrorMessage.setValue("Verbindungsfehler zur Personen-API.");
            }
        });
    }

    private void handlePeopleResponse(Response<JsonElement> response) {
        if (response.isSuccessful() && response.body() != null) {
            JsonElement root = response.body();
            JsonArray peopleArray = null;
            if (root.isJsonArray()) peopleArray = root.getAsJsonArray();
            else if (root.isJsonObject()) {
                if (root.getAsJsonObject().has("people")) peopleArray = root.getAsJsonObject().getAsJsonArray("people");
                else if (root.getAsJsonObject().has("data")) peopleArray = root.getAsJsonObject().getAsJsonArray("data");
            }

            if (peopleArray != null) {
                List<ImmichPerson> personList = new ArrayList<>();
                for (JsonElement element : peopleArray) {
                    JsonObject pObj = element.getAsJsonObject();
                    if (pObj.has("isHidden") && pObj.get("isHidden").getAsBoolean()) continue;
                    ImmichPerson person = new ImmichPerson();
                    person.id = pObj.get("id").getAsString();
                    person.name = pObj.has("name") && !pObj.get("name").isJsonNull() ? pObj.get("name").getAsString() : "";
                    personList.add(person);
                }
                peopleLiveData.setValue(personList);
            } else {
                searchErrorMessage.setValue("Noch keine Personen indexiert.");
            }
        } else {
            searchErrorMessage.setValue("Fehler beim Laden (Code: " + response.code() + ")");
        }
    }

    public void updatePersonName(String baseUrl, String apiKey, ImmichPerson person, String newName) {
        if (baseUrl.isEmpty() || apiKey.isEmpty()) return;
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

        ImmichRequests.UpdatePerson req = new ImmichRequests.UpdatePerson();
        req.name = newName;

        api.updatePerson(apiKey, person.id, req).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (response.isSuccessful()) {
                    person.name = newName;
                    List<ImmichPerson> currentList = peopleLiveData.getValue();
                    if (currentList != null) peopleLiveData.setValue(new ArrayList<>(currentList));
                } else {
                    searchErrorMessage.setValue("Speichern fehlgeschlagen (" + response.code() + ")");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                searchErrorMessage.setValue("Verbindungsfehler beim Speichern.");
            }
        });
    }

    public void fetchAssetsForAlbum(String baseUrl, String apiKey, String albumId) {
        if (baseUrl.isEmpty() || apiKey.isEmpty()) return;
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

        api.getAlbumDetails(apiKey, albumId).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (response.code() == 404 || response.code() == 405) {
                    api.getAlbumDetailsLegacy(apiKey, albumId).enqueue(new Callback<JsonElement>() {
                        @Override
                        public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                            handleAlbumAssetsResponse(response);
                        }
                        @Override
                        public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                            searchErrorMessage.setValue("Verbindungsfehler beim Laden des Albums.");
                        }
                    });
                } else {
                    handleAlbumAssetsResponse(response);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                searchErrorMessage.setValue("Verbindungsfehler beim Laden des Albums.");
            }
        });
    }

    private void handleAlbumAssetsResponse(Response<JsonElement> response) {
        if (response.isSuccessful() && response.body() != null) {
            JsonObject albumObject = response.body().getAsJsonObject();
            List<ImmichAsset> assetList = new ArrayList<>();
            if (albumObject.has("assets")) {
                assetList = new Gson().fromJson(albumObject.getAsJsonArray("assets"), new TypeToken<List<ImmichAsset>>(){}.getType());
            }
            currentSearchMode.setValue(3);
            searchResultsLiveData.setValue(assetList);
        } else {
            searchErrorMessage.setValue("Fehler beim Laden des Albums (" + response.code() + ")");
        }
    }

    public void deleteAssets(String baseUrl, String apiKey, List<String> idsToDelete, ActionCallback callback) {
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

        ImmichRequests.Ids req = new ImmichRequests.Ids();
        req.ids = idsToDelete;

        api.deleteAssets(apiKey, req).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful() || response.code() == 204) {
                    callback.onSuccess();
                } else {
                    api.deleteAssetsLegacy(apiKey, req).enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                            if (response.isSuccessful() || response.code() == 204) {
                                callback.onSuccess();
                            } else {
                                callback.onError("Fehler beim Löschen (" + response.code() + ")");
                            }
                        }
                        @Override
                        public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                            callback.onError("Netzwerkfehler beim Löschen.");
                        }
                    });
                }
            }
            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                callback.onError("Netzwerkfehler beim Löschen.");
            }
        });
    }

    public void archiveAssets(String baseUrl, String apiKey, List<ImmichAsset> assets, boolean toArchive, ActionCallback callback) {
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);
        String visibilityValue = toArchive ? "archive" : "timeline";

        executor.execute(() -> {
            boolean hasError = false;
            for (ImmichAsset asset : assets) {
                try {
                    ImmichRequests.UpdateAsset req = new ImmichRequests.UpdateAsset();
                    req.visibility = visibilityValue;
                    req.description = asset.description != null ? asset.description : "";
                    Response<JsonElement> res = api.updateAsset(apiKey, asset.id, req).execute();
                    if (!res.isSuccessful()) hasError = true;
                } catch (Exception e) {
                    hasError = true;
                }
            }
            if (hasError) {
                mainHandler.post(() -> callback.onError("Einige Elemente konnten nicht verschoben werden."));
            } else {
                mainHandler.post(callback::onSuccess);
            }
        });
    }

    public void toggleFavorite(String baseUrl, String apiKey, String assetId, boolean isFavorite, ActionCallback callback) {
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

        ImmichRequests.UpdateAsset req = new ImmichRequests.UpdateAsset();
        req.isFavorite = isFavorite;

        api.updateAsset(apiKey, assetId, req).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (response.isSuccessful()) callback.onSuccess();
                else callback.onError("Fehler beim Cloud-Sync (" + response.code() + ")");
            }
            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                callback.onError("Netzwerkfehler beim Favorisieren.");
            }
        });
    }

    public void updateDescription(String baseUrl, String apiKey, String assetId, String description) {
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

        ImmichRequests.UpdateAsset req = new ImmichRequests.UpdateAsset();
        req.description = description;

        api.updateAsset(apiKey, assetId, req).enqueue(new Callback<JsonElement>() {
            @Override public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {}
            @Override public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {}
        });
    }

    public void addAssetsToAlbum(String baseUrl, String apiKey, String albumId, List<String> assetIds, ActionCallback callback) {
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

        ImmichRequests.AssetIds req = new ImmichRequests.AssetIds();
        req.assetIds = assetIds;
        req.ids = assetIds;

        api.addAssetsToAlbum(apiKey, albumId, req).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (response.isSuccessful()) callback.onSuccess();
                else callback.onError("Fehler beim Hinzufügen (" + response.code() + ")");
            }
            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                callback.onError("Netzwerkfehler beim Hinzufügen.");
            }
        });
    }

    public void removeAssetsFromAlbum(String baseUrl, String apiKey, String albumId, List<String> assetIds, ActionCallback callback) {
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

        ImmichRequests.AssetIds req = new ImmichRequests.AssetIds();
        req.assetIds = assetIds;
        req.ids = assetIds;

        api.removeAssetsFromAlbum(apiKey, albumId, req).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (response.isSuccessful() || response.code() == 204) callback.onSuccess();
                else callback.onError("Fehler beim Entfernen (" + response.code() + ")");
            }
            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                callback.onError("Netzwerkfehler beim Entfernen.");
            }
        });
    }

    public void fetchSingleAssetDetails(String baseUrl, String apiKey, String assetId) {
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

        api.getAssetDetails(apiKey, assetId).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject assetObj = response.body().getAsJsonObject();
                    ImmichAsset parsed = new ImmichAsset();
                    parsed.id = assetId;

                    if (assetObj.has("description") && !assetObj.get("description").isJsonNull()) {
                        parsed.description = assetObj.get("description").getAsString();
                    } else if (assetObj.has("exifInfo") && !assetObj.get("exifInfo").isJsonNull()) {
                        JsonObject exifObj = assetObj.getAsJsonObject("exifInfo");
                        if (exifObj.has("description") && !exifObj.get("description").isJsonNull()) {
                            parsed.description = exifObj.get("description").getAsString();
                        } else if (exifObj.has("imageDescription") && !exifObj.get("imageDescription").isJsonNull()) {
                            parsed.description = exifObj.get("imageDescription").getAsString();
                        }
                    }

                    if (assetObj.has("isFavorite") && !assetObj.get("isFavorite").isJsonNull()) {
                        parsed.isFavorite = assetObj.get("isFavorite").getAsBoolean();
                    }

                    if (assetObj.has("isArchived") && !assetObj.get("isArchived").isJsonNull()) {
                        parsed.isArchived = assetObj.get("isArchived").getAsBoolean();
                    } else if (assetObj.has("visibility") && !assetObj.get("visibility").isJsonNull()) {
                        parsed.isArchived = "archive".equalsIgnoreCase(assetObj.get("visibility").getAsString());
                    }

                    singleAssetUpdateLiveData.setValue(parsed);
                }
            }
            @Override public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {}
        });
    }

    public void loadLocalAssetsAsync(Context context, String baseUrl, String apiKey, Set<String> bucketIds, Set<String> blacklist, LocalScanCallback callback) {
        // Hol dir den ApplicationContext, um Memory Leaks zu vermeiden
        final Context appContext = context.getApplicationContext();

        executor.execute(() -> {
            List<ImmichAsset> tempLocalCache = new ArrayList<>();
            if (appContext == null || bucketIds.isEmpty()) {
                mainHandler.post(() -> callback.onFinished(tempLocalCache));
                return;
            }

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
                try (Cursor cursor = appContext.getContentResolver().query(uri, projection, selection.toString(), selectionArgs, "DATE_ADDED DESC")) {
                    if (cursor != null) {
                        int idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                        int dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
                        int nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                        int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);

                        while (cursor.moveToNext()) {
                            String _id = cursor.getString(idCol);
                            if (blacklist.contains(_id)) continue;

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

            List<ImmichAsset> acceptedAssets = new ArrayList<>();
            if (!tempLocalCache.isEmpty() && !baseUrl.isEmpty() && !apiKey.isEmpty()) {
                String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
                ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);
                int chunkSize = 500;

                for (int i = 0; i < tempLocalCache.size(); i += chunkSize) {
                    int end = Math.min(tempLocalCache.size(), i + chunkSize);
                    List<ImmichAsset> chunk = tempLocalCache.subList(i, end);

                    try {
                        ImmichRequests.BulkUploadCheck req = new ImmichRequests.BulkUploadCheck();
                        req.assets = new ArrayList<>();
                        for (ImmichAsset item : chunk) req.assets.add(new ImmichRequests.BulkUploadCheck.AssetCheck(item.deviceAssetId));

                        Response<JsonElement> response = api.bulkUploadCheck(apiKey, req).execute();
                        if (response.code() == 404 || response.code() == 405) {
                            response = api.bulkUploadCheckLegacy(apiKey, req).execute();
                        }

                        if (response.isSuccessful() && response.body() != null) {
                            JsonObject responseObj = response.body().getAsJsonObject();
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
                                    if (acceptedIds.contains(item.deviceAssetId)) acceptedAssets.add(item);
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
            } else {
                acceptedAssets = tempLocalCache;
            }

            final List<ImmichAsset> finalAssets = acceptedAssets;
            mainHandler.post(() -> callback.onFinished(finalAssets));
        });
    }

    public void uploadAssets(Context context, String baseUrl, String apiKey, String deviceId, List<ImmichAsset> assetsToUpload, UploadCallback callback) {
        final Context appContext = context.getApplicationContext();

        executor.execute(() -> {
            String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

            int successCount = 0;
            int failCount = 0;
            List<ImmichAsset> successfulAssets = new ArrayList<>();

            for (int i = 0; i < assetsToUpload.size(); i++) {
                ImmichAsset asset = assetsToUpload.get(i);
                final int currentProgress = i + 1;

                mainHandler.post(() -> callback.onProgress(currentProgress, assetsToUpload.size()));

                try {
                    RequestBody rDeviceAssetId = RequestBody.create(MediaType.parse("text/plain"), asset.deviceAssetId);
                    RequestBody rDeviceId = RequestBody.create(MediaType.parse("text/plain"), deviceId);
                    RequestBody rFileCreatedAt = RequestBody.create(MediaType.parse("text/plain"), asset.fileCreatedAt);
                    RequestBody rFileModifiedAt = RequestBody.create(MediaType.parse("text/plain"), asset.fileCreatedAt);
                    RequestBody rIsFavorite = RequestBody.create(MediaType.parse("text/plain"), "false");

                    String fileName = asset.originalFileName;
                    if (fileName == null || fileName.isEmpty()) fileName = "upload.jpg";
                    String mimeType = asset.type.equals("VIDEO") ? "video/mp4" : "image/jpeg";

                    final Uri uri = Uri.parse(asset.localUri);
                    RequestBody reqFile = new RequestBody() {
                        @Override
                        public MediaType contentType() { return MediaType.parse(mimeType); }
                        @Override
                        public void writeTo(@NonNull BufferedSink sink) throws java.io.IOException {
                            try (InputStream is = appContext.getContentResolver().openInputStream(uri)) {
                                if (is != null) sink.writeAll(Okio.source(is));
                            }
                        }
                    };

                    MultipartBody.Part body = MultipartBody.Part.createFormData("assetData", fileName, reqFile);
                    Response<JsonElement> response = api.uploadAsset(apiKey, rDeviceAssetId, rDeviceId, rFileCreatedAt, rFileModifiedAt, rIsFavorite, body).execute();

                    if (response.isSuccessful() && response.body() != null) {
                        JsonObject responseObj = response.body().getAsJsonObject();
                        if (responseObj.has("id")) asset.id = responseObj.get("id").getAsString();
                        successCount++;
                        successfulAssets.add(asset);
                    } else if (response.code() == 409) {
                        successCount++;
                        successfulAssets.add(asset);
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    failCount++;
                }
            }

            final int finalSuccess = successCount;
            final int finalFail = failCount;
            mainHandler.post(() -> callback.onFinished(finalSuccess, finalFail, successfulAssets));
        });
    }

    public void downloadAssetsForSharing(Context context, String baseUrl, String apiKey, List<ImmichAsset> selected, ShareCallback callback) {
        final Context appContext = context.getApplicationContext();

        executor.execute(() -> {
            ArrayList<Uri> uriList = new ArrayList<>();
            boolean hasImage = false;
            boolean hasVideo = false;

            String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);
            File sharedImagesDir = new File(appContext.getCacheDir(), "shared_images");
            if (!sharedImagesDir.exists()) sharedImagesDir.mkdirs();

            for (int i = 0; i < selected.size(); i++) {
                ImmichAsset asset = selected.get(i);
                final int progress = i + 1;

                mainHandler.post(() -> callback.onProgress(progress, selected.size()));

                try {
                    boolean isVideo = asset.type != null && asset.type.equals("VIDEO");
                    if (isVideo) hasVideo = true; else hasImage = true;

                    String fileExtension = isVideo ? ".mp4" : ".jpg";
                    File fileToShare = new File(sharedImagesDir, "share_" + asset.id + fileExtension);

                    if (!fileToShare.exists()) {
                        Response<ResponseBody> response = api.downloadAssetOriginal(apiKey, asset.id).execute();
                        if (response.isSuccessful() && response.body() != null) {
                            InputStream in = response.body().byteStream();
                            OutputStream out = new FileOutputStream(fileToShare);
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                            in.close();
                            out.close();
                        }
                    }
                    Uri contentUri = FileProvider.getUriForFile(appContext, appContext.getPackageName() + ".fileprovider", fileToShare);
                    uriList.add(contentUri);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (!uriList.isEmpty()) {
                String mimeType = "*/*";
                if (hasImage && !hasVideo) mimeType = "image/*";
                if (hasVideo && !hasImage) mimeType = "video/*";

                String finalMimeType = mimeType;
                mainHandler.post(() -> callback.onFinished(uriList, finalMimeType));
            } else {
                mainHandler.post(() -> callback.onError("Fehler beim Download der Dateien."));
            }
        });
    }

    public void fetchAvailableYears(String baseUrl, String apiKey, YearsCallback callback) {
        if (baseUrl.isEmpty() || apiKey.isEmpty()) return;
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

        api.getTimeBuckets(apiKey, "MONTH").enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (response.code() == 404 || response.code() == 405) {
                    api.getTimeBucketsLegacy(apiKey).enqueue(new Callback<JsonElement>() {
                        @Override
                        public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                            handleYearsResponse(response, callback);
                        }
                        @Override
                        public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                            generateFallbackYears(callback);
                        }
                    });
                } else {
                    handleYearsResponse(response, callback);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                generateFallbackYears(callback);
            }
        });
    }

    private void handleYearsResponse(Response<JsonElement> response, YearsCallback callback) {
        HashSet<String> yearsSet = new HashSet<>();
        if (response.isSuccessful() && response.body() != null) {
            JsonElement jsonElement = response.body();
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
            }
        }

        if (yearsSet.isEmpty()) {
            generateFallbackYears(callback);
        } else {
            List<String> yearsList = new ArrayList<>(yearsSet);
            Collections.sort(yearsList, Collections.reverseOrder());
            callback.onFinished(yearsList);
        }
    }

    private void generateFallbackYears(YearsCallback callback) {
        HashSet<String> yearsSet = new HashSet<>();
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);
        for (int i = currentYear; i >= 2000; i--) {
            yearsSet.add(String.valueOf(i));
        }
        List<String> yearsList = new ArrayList<>(yearsSet);
        Collections.sort(yearsList, Collections.reverseOrder());
        callback.onFinished(yearsList);
    }

    public void fetchLocations(String baseUrl, String apiKey, LocationsCallback callback) {
        if (baseUrl.isEmpty() || apiKey.isEmpty()) return;
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        ImmichApi api = RetrofitClient.getImmichApi(cleanBaseUrl);

        api.getCities(apiKey).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> call, @NonNull Response<JsonElement> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonElement root = response.body();
                    if (root.isJsonArray()) {
                        JsonArray assets = root.getAsJsonArray();
                        LocationData data = new LocationData();

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

                                    if (!origCountry.isEmpty()) data.displayToOriginalMap.put(country, origCountry);
                                    if (!origState.isEmpty()) data.displayToOriginalMap.put(state, origState);
                                    if (!origCity.isEmpty()) data.displayToOriginalMap.put(city, origCity);

                                    if (!country.isEmpty()) data.allCountries.add(country);
                                    if (!state.isEmpty()) data.allStates.add(state);
                                    if (!city.isEmpty()) data.allCities.add(city);

                                    if (!country.isEmpty()) {
                                        data.locationMap.putIfAbsent(country, new HashMap<>());
                                        if (!state.isEmpty()) {
                                            data.locationMap.get(country).putIfAbsent(state, new HashSet<>());
                                            if (!city.isEmpty()) {
                                                data.locationMap.get(country).get(state).add(city);
                                            }
                                        } else if (!city.isEmpty()) {
                                            data.locationMap.get(country).putIfAbsent("", new HashSet<>());
                                            data.locationMap.get(country).get("").add(city);
                                        }
                                    }
                                }
                            }
                        }
                        callback.onFinished(data);
                    } else {
                        callback.onError("Fehler beim Parsen der Orte.");
                    }
                } else {
                    callback.onError("Fehler (" + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonElement> call, @NonNull Throwable t) {
                callback.onError("Verbindungsfehler");
            }
        });
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

    public static List<ImmichAsset> parseAssetsFromJson(JsonElement jsonElement) {
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
}