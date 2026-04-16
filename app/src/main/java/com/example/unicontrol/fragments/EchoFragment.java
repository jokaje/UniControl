package com.example.unicontrol.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.unicontrol.R;
import com.example.unicontrol.adapters.ChatAdapter;
import com.example.unicontrol.models.ChatMessage;
import com.example.unicontrol.network.OpenClawClient;
import com.example.unicontrol.viewmodels.SharedViewModel;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class EchoFragment extends Fragment {

    private static final String TAG = "EchoFragment";
    private static final String PREF_CHAT_HISTORY = "chat_history_prefs";
    private static final String KEY_MESSAGES = "chat_messages_list";

    private RecyclerView chatRecyclerView;
    private EditText messageEditText;
    private ImageButton sendButton;
    private ImageButton attachButton;

    private View attachmentPreviewLayout;
    private ImageView attachmentPreviewImage;
    private ImageButton removeAttachmentButton;

    private View statusIndicator;
    private TextView statusText;

    private ChatAdapter chatAdapter;
    private OpenClawClient openClawClient;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Gson gson = new Gson();

    private int currentThemeColor;

    private String pendingBase64 = null;
    private String pendingMimeType = null;
    private String pendingUriString = null;

    private ChatMessage typingMessage = null;

    private SharedViewModel sharedViewModel;

    private final ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    processSelectedFile(uri);
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_echo, container, false);

        chatRecyclerView = view.findViewById(R.id.chatRecyclerView);
        messageEditText = view.findViewById(R.id.messageEditText);
        sendButton = view.findViewById(R.id.sendButton);
        attachButton = view.findViewById(R.id.attachButton);
        statusIndicator = view.findViewById(R.id.statusIndicator);
        statusText = view.findViewById(R.id.statusText);

        attachmentPreviewLayout = view.findViewById(R.id.attachmentPreviewLayout);
        attachmentPreviewImage = view.findViewById(R.id.attachmentPreviewImage);
        removeAttachmentButton = view.findViewById(R.id.removeAttachmentButton);

        chatAdapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);
        chatRecyclerView.setAdapter(chatAdapter);

        SharedPreferences prefs = requireContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
        String echoColorHex = prefs.getString(SettingsFragment.KEY_COLOR_ECHO, "#AEC6CF");
        try {
            currentThemeColor = Color.parseColor(echoColorHex);
        } catch (Exception e) {
            currentThemeColor = Color.parseColor("#AEC6CF");
        }

        chatAdapter.setThemeColor(currentThemeColor);
        messageEditText.setTextColor(Color.parseColor("#333333"));
        messageEditText.setHintTextColor(Color.parseColor("#888888"));
        sendButton.setColorFilter(currentThemeColor);

        chatAdapter.setMessageClickListener((message, anchorView) -> {
            showMessageMenu(message, anchorView);
        });

        updateStatusUI(Color.parseColor("#FF6961"), "Getrennt");
        loadChatHistory();

        // --- Briefkasten für Bilder aus anderen Fragmenten abrufen ---
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        sharedViewModel.getPendingImageUri().observe(getViewLifecycleOwner(), uriString -> {
            if (uriString != null && !uriString.isEmpty()) {
                processSelectedFile(Uri.parse(uriString));
                sharedViewModel.clearPendingImageUri();
            }
        });

        openClawClient = new OpenClawClient(requireContext());
        openClawClient.setChatListener(new OpenClawClient.ChatListener() {
            @Override
            public void onAgentTyping() {
                mainHandler.post(() -> {
                    if (typingMessage == null) {
                        typingMessage = new ChatMessage("KI schreibt...", false);
                        typingMessage.setTypingIndicator(true);
                        addMessageToUI(typingMessage);
                    }
                });
            }

            @Override
            public void onMessageReceived(String text) {
                mainHandler.post(() -> {
                    if (typingMessage != null) {
                        chatAdapter.removeMessage(typingMessage);
                        typingMessage = null;
                    }
                    addMessageToUI(new ChatMessage(text, false, false, null, null));
                });
            }

            @Override
            public void onConnectionStatusChanged(String status) {
                mainHandler.post(() -> {
                    int color = Color.parseColor("#FFB347");
                    String shortStatus = "Verbinde...";
                    if (status.contains("erfolgreich") || status.contains("✅")) {
                        color = Color.parseColor("#77DD77"); shortStatus = "Verbunden";
                    } else if (status.contains("Fehler") || status.contains("❌") || status.contains("Abbruch") || status.contains("fehlgeschlagen")) {
                        color = Color.parseColor("#FF6961"); shortStatus = "Fehler";
                        if (typingMessage != null) {
                            chatAdapter.removeMessage(typingMessage);
                            typingMessage = null;
                        }
                    } else if (status.contains("gekoppelt werden") || status.contains("⏳")) {
                        color = Color.parseColor("#FFB347"); shortStatus = "Pairing nötig";
                    }
                    updateStatusUI(color, shortStatus);
                });
            }
        });

        if (attachButton != null) attachButton.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
        if (removeAttachmentButton != null) removeAttachmentButton.setOnClickListener(v -> clearPendingAttachment());

        sendButton.setOnClickListener(v -> {
            String text = messageEditText.getText().toString().trim();
            if (!text.isEmpty() || pendingBase64 != null) {
                sendMessage(text);
            }
        });

        openClawClient.connect();
        return view;
    }

    private void processSelectedFile(Uri uri) {
        if (getContext() == null) return;

        String scheme = uri.getScheme();
        ContentResolver resolver = getContext().getContentResolver();

        if ("http".equals(scheme) || "https".equals(scheme)) {
            pendingMimeType = "image/jpeg";
        } else {
            pendingMimeType = resolver.getType(uri);
            if (pendingMimeType == null) pendingMimeType = "application/octet-stream";
        }

        new Thread(() -> {
            try {
                InputStream is;

                // Netzwerk-Download falls das Bild aus der Cloud kommt (z.B. Immich URL)
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    OkHttpClient httpClient = new OkHttpClient();
                    Request.Builder reqBuilder = new Request.Builder().url(uri.toString());

                    // Immich API-Key anhängen, sonst wird der Download blockiert (401/403)
                    SharedPreferences prefs = getContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
                    String apiKey = prefs.getString(SettingsFragment.KEY_FOTOS_API_KEY, "");
                    if (!apiKey.isEmpty()) {
                        reqBuilder.addHeader("x-api-key", apiKey);
                        reqBuilder.addHeader("Accept", "application/json");
                    }

                    Request req = reqBuilder.build();
                    Response resp = httpClient.newCall(req).execute();

                    if (!resp.isSuccessful() || resp.body() == null) {
                        throw new Exception("HTTP Download Fehler: Code " + resp.code());
                    }

                    String contentType = resp.header("Content-Type");
                    if (contentType != null) pendingMimeType = contentType;

                    is = resp.body().byteStream();
                } else {
                    // Lokale Datei vom Gerät
                    is = resolver.openInputStream(uri);
                }

                if (is == null) throw new Exception("Stream Fehler");

                File cacheDir = getContext().getCacheDir();
                File localFile = new File(cacheDir, "upload_" + System.currentTimeMillis() + ".jpg");
                FileOutputStream fos = new FileOutputStream(localFile);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                if (pendingMimeType.startsWith("image/")) {
                    Bitmap original = BitmapFactory.decodeStream(is);
                    is.close();

                    if (original == null) throw new Exception("Bild konnte nicht dekodiert werden.");

                    int maxDim = 1200;
                    int w = original.getWidth();
                    int h = original.getHeight();

                    Bitmap finalBitmap = original;
                    if (w > maxDim || h > maxDim) {
                        float ratio = Math.min((float) maxDim / w, (float) maxDim / h);
                        finalBitmap = Bitmap.createScaledBitmap(original, Math.round(w * ratio), Math.round(h * ratio), true);
                        if (finalBitmap != original) original.recycle();
                    }

                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, buffer);

                    pendingMimeType = "image/jpeg";
                }
                else {
                    int nRead;
                    byte[] data = new byte[16384];
                    while ((nRead = is.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                        fos.write(data, 0, nRead);
                    }
                    is.close();
                }

                fos.flush();
                fos.close();

                byte[] fileBytes = buffer.toByteArray();
                pendingBase64 = Base64.encodeToString(fileBytes, Base64.NO_WRAP);
                pendingUriString = Uri.fromFile(localFile).toString();

                mainHandler.post(() -> {
                    attachmentPreviewLayout.setVisibility(View.VISIBLE);
                    if (pendingMimeType.startsWith("image/")) {
                        attachmentPreviewImage.setImageURI(Uri.parse(pendingUriString));
                    } else {
                        attachmentPreviewImage.setImageDrawable(null);
                        attachmentPreviewImage.setBackgroundColor(Color.GRAY);
                    }
                    attachButton.setColorFilter(Color.parseColor("#77DD77")); // Grün
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    Toast.makeText(getContext(), "Fehler beim Laden (Netzwerk?)", Toast.LENGTH_SHORT).show();
                    clearPendingAttachment();
                });
            }
        }).start();
    }

    private void clearPendingAttachment() {
        pendingBase64 = null;
        pendingMimeType = null;
        pendingUriString = null;
        if (attachButton != null) attachButton.clearColorFilter();
        if (attachmentPreviewLayout != null) attachmentPreviewLayout.setVisibility(View.GONE);
        if (attachmentPreviewImage != null) attachmentPreviewImage.setImageDrawable(null);
    }

    private void showMessageMenu(ChatMessage message, View anchorView) {
        if (getContext() == null) return;
        PopupMenu popup = new PopupMenu(getContext(), anchorView);
        popup.getMenu().add(0, 1, 0, "📋 Kopieren");
        popup.getMenu().add(0, 2, 1, "↩️ Antworten");
        popup.getMenu().add(0, 3, 2, "↗️ Weiterleiten");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Nachricht", message.getText());
                    if (clipboard != null) clipboard.setPrimaryClip(clip);
                    Toast.makeText(getContext(), "Nachricht kopiert", Toast.LENGTH_SHORT).show();
                    return true;
                case 2:
                    String prefix = message.isUser() ? "Du: " : "OpenClaw: ";
                    String quote = "> " + prefix + message.getText().replace("\n", "\n> ") + "\n\n";
                    messageEditText.setText(quote);
                    messageEditText.setSelection(quote.length());
                    messageEditText.requestFocus();
                    return true;
                case 3:
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, message.getText());
                    sendIntent.setType("text/plain");
                    Intent shareIntent = Intent.createChooser(sendIntent, "Weiterleiten an...");
                    startActivity(shareIntent);
                    return true;
            }
            return false;
        });
        popup.show();
    }

    private void updateStatusUI(int color, String text) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        statusIndicator.setBackground(shape);
        statusText.setText(text);
    }

    private void sendMessage(String text) {
        ChatMessage newMsg = new ChatMessage(text, true, false, pendingUriString, pendingMimeType);
        addMessageToUI(newMsg);

        openClawClient.sendChatMessage(text, pendingBase64, pendingMimeType);

        messageEditText.setText("");
        clearPendingAttachment();
    }

    private void addMessageToUI(ChatMessage message) {
        chatAdapter.addMessage(message);
        chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        if (!message.isSystem() && !message.isTypingIndicator()) {
            saveChatHistory();
        }
    }

    private void saveChatHistory() {
        if (getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences(PREF_CHAT_HISTORY, Context.MODE_PRIVATE);
        List<ChatMessage> toSave = new ArrayList<>();
        for (ChatMessage msg : chatAdapter.getMessages()) {
            if (!msg.isSystem() && !msg.isTypingIndicator()) {
                toSave.add(msg);
            }
        }
        String json = gson.toJson(toSave);
        prefs.edit().putString(KEY_MESSAGES, json).apply();
    }

    private void loadChatHistory() {
        if (getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences(PREF_CHAT_HISTORY, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_MESSAGES, null);
        if (json != null) {
            Type type = new TypeToken<ArrayList<ChatMessage>>() {}.getType();
            List<ChatMessage> savedMessages = gson.fromJson(json, type);
            if (savedMessages != null && !savedMessages.isEmpty()) {
                chatAdapter.setMessages(savedMessages);
                chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (openClawClient != null) {
            openClawClient.disconnect();
        }
    }
}