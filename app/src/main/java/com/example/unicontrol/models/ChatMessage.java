package com.example.unicontrol.models;

public class ChatMessage {
    private String text;
    private boolean isUser; // true = Du (rechts), false = OpenClaw Agent (links)
    private boolean isSystem; // NEU: true = Nur Verbindungsstatus, wird nicht dauerhaft gespeichert
    private long timestamp;

    public ChatMessage(String text, boolean isUser) {
        this(text, isUser, false);
    }

    public ChatMessage(String text, boolean isUser, boolean isSystem) {
        this.text = text;
        this.isUser = isUser;
        this.isSystem = isSystem;
        this.timestamp = System.currentTimeMillis();
    }

    public String getText() {
        return text;
    }

    public boolean isUser() {
        return isUser;
    }

    public boolean isSystem() {
        return isSystem;
    }

    public long getTimestamp() {
        return timestamp;
    }
}