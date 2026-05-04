package com.example.unicontrol.viewmodels;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsViewModel extends ViewModel {

    private final MutableLiveData<List<LocalAlbum>> localAlbumsLiveData = new MutableLiveData<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public LiveData<List<LocalAlbum>> getLocalAlbums() {
        return localAlbumsLiveData;
    }

    public void loadLocalMediaFolders(Context context) {
        final Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            HashMap<String, LocalAlbum> albumMap = new HashMap<>();
            Uri[] uris = { MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI };
            String[] projection = { MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, MediaStore.MediaColumns.DATA };

            for (Uri uri : uris) {
                try (Cursor cursor = appContext.getContentResolver().query(uri, projection, null, null, "DATE_ADDED DESC")) {
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
                } catch (Exception ignored) {}
            }

            List<LocalAlbum> resultList = new ArrayList<>(albumMap.values());
            Collections.sort(resultList, (a, b) -> {
                if (a.name.equalsIgnoreCase("camera")) return -1;
                if (b.name.equalsIgnoreCase("camera")) return 1;
                return a.name.compareToIgnoreCase(b.name);
            });

            localAlbumsLiveData.postValue(resultList);
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }

    // Datenmodell für das Backup-BottomSheet
    public static class LocalAlbum {
        public String id;
        public String name;
        public int count;
        public String coverImagePath;
    }
}