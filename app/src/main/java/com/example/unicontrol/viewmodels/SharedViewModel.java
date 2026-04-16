package com.example.unicontrol.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SharedViewModel extends ViewModel {
    // Hier speichern wir die URL oder den lokalen Pfad des Bildes
    private final MutableLiveData<String> pendingImageUri = new MutableLiveData<>();

    public void setPendingImageUri(String uri) {
        pendingImageUri.setValue(uri);
    }

    public LiveData<String> getPendingImageUri() {
        return pendingImageUri;
    }

    public void clearPendingImageUri() {
        pendingImageUri.setValue(null);
    }
}