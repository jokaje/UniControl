package com.example.unicontrol.models;

public class GalleryItem {
    public static final int TYPE_MONTH_HEADER = 0;
    public static final int TYPE_DAY_HEADER = 1;
    public static final int TYPE_PHOTO = 2;

    public int type;
    public String title; // Für die Überschriften (z.B. "MAI 2026")
    public ImmichAsset asset; // Für das eigentliche Bild

    public GalleryItem(int type, String title, ImmichAsset asset) {
        this.type = type;
        this.title = title;
        this.asset = asset;
    }
}