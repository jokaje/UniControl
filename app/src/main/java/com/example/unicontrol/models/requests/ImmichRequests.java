package com.example.unicontrol.models.requests;

import java.util.List;

/**
 * Bündelt alle Request-Modelle für Retrofit, um manuelles JSON-Bauen zu vermeiden.
 */
public class ImmichRequests {

    public static class SearchMetadata {
        public Integer size;
        public Integer page;
        public Boolean withExif;
        public Boolean isFavorite;
        public Boolean withArchived;
        public String visibility;
        public String type;
        public String takenAfter;
        public String takenBefore;
        public String country;
        public String state;
        public String city;
        public List<String> personIds;
    }

    public static class SmartSearch {
        public String query;
        public String q;
        public Boolean withExif;
    }

    public static class BulkUploadCheck {
        public List<AssetCheck> assets;

        public static class AssetCheck {
            public String id;
            public AssetCheck(String id) {
                this.id = id;
            }
        }
    }

    public static class AssetIds {
        public List<String> assetIds;
        public List<String> ids; // Fallback für manche API-Versionen
    }

    public static class Ids {
        public List<String> ids;
    }

    public static class UpdateAsset {
        public String visibility;
        public String description;
        public Boolean isFavorite;
    }

    public static class UpdatePerson {
        public String name;
    }
}