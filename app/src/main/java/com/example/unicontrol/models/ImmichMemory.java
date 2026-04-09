package com.example.unicontrol.models;

import java.util.List;

public class ImmichMemory {
    public String id;
    public String title;       // Standard in neueren Versionen
    public String name;        // Kommt in manchen Versionen vor
    public String memoryKey;   // Kommt oft als Roh-Format (z.B. "1_year_ago")
    public String type;
    public String createdAt;
    public List<ImmichAsset> assets;
}