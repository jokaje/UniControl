package com.example.unicontrol.models;

public class ImmichAsset {
    public String id;
    public String type; // "IMAGE" oder "VIDEO"
    public String fileCreatedAt; // Datum und Uhrzeit
    public String originalFileName; // Dateiname

    public Boolean isFavorite;
    public String description; // Manchmal steht es hier...
    public Boolean isArchived;

    public ExifInfo exifInfo; // Die spannenden Metadaten


    // --- NEU: Interne Felder für den Hybrid-Modus (Lokal vs. Cloud) ---
    public transient boolean isLocalOnly = false;
    public transient String localUri;

    // --- NEU: Dieses Feld hat bei dir gefehlt! ---
    public String deviceAssetId;


    // Innere Klasse für die Metadaten
    public static class ExifInfo {
        public String make;
        public String model;
        public String city;
        public String state;
        public String country;

        // NEU: ... und bei den Listen-Abfragen versteckt Immich es oft hier drin!
        public String description;
        public String imageDescription; // Oft auch unter diesem Namen gespeichert
    }
}