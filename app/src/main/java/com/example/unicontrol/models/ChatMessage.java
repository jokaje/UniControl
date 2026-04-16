package com.example.unicontrol.models;

public class ChatMessage {
    private String text;
    private boolean isUser;
    private boolean isSystem;
    private long timestamp;
    private String attachmentUri;
    private String mimeType;

    // NEU: Markierung für die "KI schreibt..." Blase
    private boolean isTypingIndicator = false;

    public ChatMessage(String text, boolean isUser) {
        this(text, isUser, false, null, null);
    }

    public ChatMessage(String text, boolean isUser, boolean isSystem) {
        this(text, isUser, isSystem, null, null);
    }

    public ChatMessage(String text, boolean isUser, boolean isSystem, String attachmentUri, String mimeType) {
        this.text = text;
        this.isUser = isUser;
        this.isSystem = isSystem;
        this.attachmentUri = attachmentUri;
        this.mimeType = mimeType;
        this.timestamp = System.currentTimeMillis();
    }

    public String getText() { return text; }
    public boolean isUser() { return isUser; }
    public boolean isSystem() { return isSystem; }
    public long getTimestamp() { return timestamp; }
    public String getAttachmentUri() { return attachmentUri; }
    public String getMimeType() { return mimeType; }
    public boolean hasAttachment() { return attachmentUri != null && !attachmentUri.isEmpty(); }

    // NEU: Getter & Setter für den Tipp-Indikator
    public boolean isTypingIndicator() { return isTypingIndicator; }
    public void setTypingIndicator(boolean typingIndicator) { this.isTypingIndicator = typingIndicator; }
}