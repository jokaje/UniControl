package com.example.unicontrol.fragments;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.unicontrol.MainActivity;
import com.example.unicontrol.R;
import com.example.unicontrol.adapters.ChatAdapter;
import com.example.unicontrol.models.ChatMessage;
import com.example.unicontrol.services.OpenClawService;
import com.example.unicontrol.utils.CryptoUtils;
import com.example.unicontrol.utils.SettingsManager;
import com.example.unicontrol.viewmodels.SharedViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class EchoFragment extends Fragment {

    private static final String PREF_CHAT_HISTORY = "chat_history_prefs";
    private static final String KEY_MESSAGES = "chat_messages_list";

    // UI Ebenen
    private View layoutChat;
    private View layoutSetup;
    private View layoutIntroOverlay;

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
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Gson gson = new Gson();

    private int currentThemeColor;
    private CryptoUtils cryptoUtils;
    private SettingsManager settingsManager;

    private String pendingBase64 = null;
    private String pendingMimeType = null;
    private String pendingUriString = null;

    private ChatMessage typingMessage = null;
    private SharedViewModel sharedViewModel;

    private MediaRecorder mediaRecorder;
    private String audioFilePath = null;
    private boolean isRecording = false;

    private final BroadcastReceiver echoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.example.unicontrol.ECHO_NEW_MESSAGE".equals(action)) {
                String text = intent.getStringExtra("text");
                if (typingMessage != null) {
                    chatAdapter.removeMessage(typingMessage);
                    typingMessage = null;
                }
                chatAdapter.addMessage(new ChatMessage(text, false, false, null, null));
                chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
            }
            else if ("com.example.unicontrol.ECHO_TYPING".equals(action)) {
                if (typingMessage == null) {
                    typingMessage = new ChatMessage("KI schreibt...", false);
                    typingMessage.setTypingIndicator(true);
                    chatAdapter.addMessage(typingMessage);
                    chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
                }
            }
            else if ("com.example.unicontrol.ECHO_STATUS".equals(action)) {
                String status = intent.getStringExtra("status");
                if (status != null) handleStatusUpdate(status);
            }
        }
    };

    private final ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) processSelectedFile(uri);
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_echo, container, false);

        settingsManager = SettingsManager.getInstance(requireContext());
        cryptoUtils = new CryptoUtils(requireContext());

        // --- LAYER BINDING ---
        layoutChat = view.findViewById(R.id.layout_echo_chat);
        layoutSetup = view.findViewById(R.id.layout_echo_setup);
        layoutIntroOverlay = view.findViewById(R.id.layout_echo_intro_overlay);

        // Setup Fields
        EditText etSetupLocal = view.findViewById(R.id.et_setup_echo_local);
        EditText etSetupPublic = view.findViewById(R.id.et_setup_echo_public);
        EditText etSetupPass = view.findViewById(R.id.et_setup_echo_pass);
        EditText etSetupDevice = view.findViewById(R.id.et_setup_device_id);
        EditText etSetupPub = view.findViewById(R.id.et_setup_pub_key);
        EditText etSetupPriv = view.findViewById(R.id.et_setup_priv_key);
        MaterialButton btnSetupSave = view.findViewById(R.id.btn_setup_save);
        MaterialButton btnIntroNext = view.findViewById(R.id.btn_intro_next);
        MaterialButton btnIntroSkip = view.findViewById(R.id.btn_intro_skip);

        // Chat Fields
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

        // Theme laden über SettingsManager
        String echoColorHex = settingsManager.getColorEcho();
        currentThemeColor = Color.parseColor("#AEC6CF");
        try { currentThemeColor = Color.parseColor(echoColorHex); } catch (Exception ignored) {}

        chatAdapter.setThemeColor(currentThemeColor);
        messageEditText.setTextColor(Color.parseColor("#333333"));
        messageEditText.setHintTextColor(Color.parseColor("#888888"));
        sendButton.setColorFilter(currentThemeColor);

        chatAdapter.setMessageClickListener(this::showMessageMenu);
        updateStatusUI(Color.parseColor("#FFB347"), "Warte auf Dienst...");
        loadChatHistory();

        // --- ONBOARDING LOGIK ---
        boolean hasIdentity = cryptoUtils.hasValidIdentity();
        String localUrl = settingsManager.getEchoLocal();

        if (!hasIdentity || localUrl.isEmpty()) {
            // Zeige Sprechblase & Setup!
            layoutChat.setVisibility(View.GONE);
            layoutSetup.setVisibility(View.VISIBLE);
            layoutIntroOverlay.setVisibility(View.VISIBLE);

            // Vorbefüllen falls teilweise Daten vorhanden (über SettingsManager)
            etSetupLocal.setText(localUrl);
            etSetupPublic.setText(settingsManager.getEchoPublic());
            etSetupPass.setText(settingsManager.getOpenClawPassword());
            etSetupDevice.setText(cryptoUtils.getDeviceId());
            etSetupPub.setText(cryptoUtils.getPublicKeyBase64());
            etSetupPriv.setText(cryptoUtils.getPrivateKeyBase64());

            Intent serviceIntent = new Intent(requireContext(), OpenClawService.class);
            requireContext().stopService(serviceIntent);
        } else {
            // Normaler Chat-Modus!
            layoutChat.setVisibility(View.VISIBLE);
            layoutSetup.setVisibility(View.GONE);
            layoutIntroOverlay.setVisibility(View.GONE);

            Intent serviceIntent = new Intent(requireContext(), OpenClawService.class);
            ContextCompat.startForegroundService(requireContext(), serviceIntent);
        }

        // Klick auf "Einrichten": Blendet nur die Blase aus
        btnIntroNext.setOnClickListener(v -> layoutIntroOverlay.setVisibility(View.GONE));

        // Klick auf "Nicht benötigt": Deaktiviert das Modul und geht weiter!
        btnIntroSkip.setOnClickListener(v -> {
            settingsManager.setModuleEnabled(SettingsManager.KEY_MOD_ECHO, false);
            Toast.makeText(getContext(), "Echo-Modul ausgeblendet.", Toast.LENGTH_SHORT).show();

            if (getActivity() instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) getActivity();
                mainActivity.refreshMenu(); // Lässt den Tab unten magisch verschwinden!
                mainActivity.goToNextOnboardingTab(R.id.nav_echo); // Geht zum nächsten Modul
            }
        });

        // Klick auf "Speichern": Schließt Setup ab und geht weiter
        btnSetupSave.setOnClickListener(v -> {
            settingsManager.setEchoLocal(etSetupLocal.getText().toString().trim());
            settingsManager.setEchoPublic(etSetupPublic.getText().toString().trim());
            settingsManager.setOpenClawPassword(etSetupPass.getText().toString().trim());

            cryptoUtils.setIdentity(
                    etSetupDevice.getText().toString().trim(),
                    etSetupPriv.getText().toString().trim(),
                    etSetupPub.getText().toString().trim()
            );

            Toast.makeText(getContext(), "Echo verbunden! ✅", Toast.LENGTH_SHORT).show();

            layoutSetup.setVisibility(View.GONE);
            layoutChat.setVisibility(View.VISIBLE);

            Intent serviceIntent = new Intent(requireContext(), OpenClawService.class);
            ContextCompat.startForegroundService(requireContext(), serviceIntent);

            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).goToNextOnboardingTab(R.id.nav_echo);
            }
        });
        // -----------------------

        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        sharedViewModel.getPendingImageUri().observe(getViewLifecycleOwner(), uriString -> {
            if (uriString != null && !uriString.isEmpty()) {
                processSelectedFile(Uri.parse(uriString));
                sharedViewModel.clearPendingImageUri();
            }
        });

        if (attachButton != null) attachButton.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
        if (removeAttachmentButton != null) removeAttachmentButton.setOnClickListener(v -> clearPendingAttachment());

        messageEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateSendButtonIcon(); }
        });
        updateSendButtonIcon();

        sendButton.setOnTouchListener((v, event) -> {
            boolean hasContent = !messageEditText.getText().toString().trim().isEmpty() || pendingBase64 != null;

            if (hasContent) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    sendMessage(messageEditText.getText().toString().trim());
                    v.performClick();
                }
                return true;
            } else {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (checkAudioPermission()) {
                        startRecording();
                        sendButton.setColorFilter(Color.parseColor("#FF6961"));
                        messageEditText.setHint("🎤 Aufnahme läuft... (Loslassen zum Senden)");
                    }
                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (isRecording) {
                        stopRecordingAndSend();
                        sendButton.setColorFilter(currentThemeColor);
                        messageEditText.setHint("Frag OpenClaw etwas...");
                    }
                    return true;
                }
            }
            return false;
        });

        return view;
    }

    private void updateSendButtonIcon() {
        if (getContext() == null || sendButton == null) return;
        boolean hasContent = !messageEditText.getText().toString().trim().isEmpty() || pendingBase64 != null;
        if (hasContent) {
            sendButton.setImageResource(android.R.drawable.ic_menu_send);
        } else {
            sendButton.setImageResource(android.R.drawable.ic_btn_speak_now);
        }
    }

    private boolean checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1005);
            return false;
        }
        return true;
    }

    private void startRecording() {
        if (getContext() == null) return;
        audioFilePath = getContext().getCacheDir().getAbsolutePath() + "/audiomsg_" + System.currentTimeMillis() + ".m4a";
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setOutputFile(audioFilePath);
        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Fehler beim Start der Audio-Aufnahme", Toast.LENGTH_SHORT).show();
            isRecording = false;
        }
    }

    private void stopRecordingAndSend() {
        if (mediaRecorder != null && isRecording) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaRecorder = null;
            isRecording = false;

            File audioFile = new File(audioFilePath);
            if (audioFile.exists()) {
                try (InputStream is = new FileInputStream(audioFile)) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    int nRead;
                    byte[] data = new byte[16384];
                    while ((nRead = is.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    buffer.flush();
                    byte[] fileBytes = buffer.toByteArray();
                    String base64 = Base64.encodeToString(fileBytes, Base64.NO_WRAP);

                    ChatMessage newMsg = new ChatMessage("🎤 Sprachnachricht gesendet", true, false, Uri.fromFile(audioFile).toString(), "audio/mp4");
                    chatAdapter.addMessage(newMsg);
                    chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
                    saveChatHistory();

                    Intent serviceIntent = new Intent(requireContext(), OpenClawService.class);
                    serviceIntent.setAction("SEND_MESSAGE");
                    serviceIntent.putExtra("text", "Ich habe dir eine Sprachnachricht gesendet.");
                    serviceIntent.putExtra("base64", base64);
                    serviceIntent.putExtra("mimeType", "audio/mp4");
                    requireContext().startService(serviceIntent);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleStatusUpdate(String status) {
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
    }

    @Override
    public void onResume() {
        super.onResume();
        requireContext().getSharedPreferences("chat_state", Context.MODE_PRIVATE)
                .edit().putBoolean("is_echo_visible", true).apply();

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.unicontrol.ECHO_NEW_MESSAGE");
        filter.addAction("com.example.unicontrol.ECHO_TYPING");
        filter.addAction("com.example.unicontrol.ECHO_STATUS");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(echoReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            ContextCompat.registerReceiver(requireContext(), echoReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        }

        loadChatHistory();

        if (cryptoUtils.hasValidIdentity()) {
            Intent serviceIntent = new Intent(requireContext(), OpenClawService.class);
            serviceIntent.setAction("REQUEST_STATUS");
            requireContext().startService(serviceIntent);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().getSharedPreferences("chat_state", Context.MODE_PRIVATE)
                .edit().putBoolean("is_echo_visible", false).apply();

        requireContext().unregisterReceiver(echoReceiver);
    }

    private void sendMessage(String text) {
        ChatMessage newMsg = new ChatMessage(text, true, false, pendingUriString, pendingMimeType);
        chatAdapter.addMessage(newMsg);
        chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
        saveChatHistory();

        Intent serviceIntent = new Intent(requireContext(), OpenClawService.class);
        serviceIntent.setAction("SEND_MESSAGE");
        serviceIntent.putExtra("text", text);
        serviceIntent.putExtra("base64", pendingBase64);
        serviceIntent.putExtra("mimeType", pendingMimeType);
        requireContext().startService(serviceIntent);

        messageEditText.setText("");
        clearPendingAttachment();
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
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    OkHttpClient httpClient = new OkHttpClient();
                    Request.Builder reqBuilder = new Request.Builder().url(uri.toString());

                    // API Key via SettingsManager holen
                    String apiKey = settingsManager.getFotosApiKey();
                    if (!apiKey.isEmpty()) {
                        reqBuilder.addHeader("x-api-key", apiKey);
                        reqBuilder.addHeader("Accept", "application/json");
                    }

                    Response resp = httpClient.newCall(reqBuilder.build()).execute();
                    if (!resp.isSuccessful() || resp.body() == null) throw new Exception("HTTP Download Fehler");

                    String contentType = resp.header("Content-Type");
                    if (contentType != null) pendingMimeType = contentType;
                    is = resp.body().byteStream();
                } else {
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
                    if (original == null) throw new Exception("Bild fehlerhaft");

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
                } else {
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
                    attachButton.setColorFilter(Color.parseColor("#77DD77"));
                    updateSendButtonIcon();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(getContext(), "Fehler beim Laden der Datei.", Toast.LENGTH_SHORT).show();
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
        updateSendButtonIcon();
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
                    startActivity(Intent.createChooser(sendIntent, "Weiterleiten an..."));
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

    private void saveChatHistory() {
        if (getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences(PREF_CHAT_HISTORY, Context.MODE_PRIVATE);
        List<ChatMessage> toSave = new ArrayList<>();
        for (ChatMessage msg : chatAdapter.getMessages()) {
            if (!msg.isSystem() && !msg.isTypingIndicator()) {
                toSave.add(msg);
            }
        }
        prefs.edit().putString(KEY_MESSAGES, gson.toJson(toSave)).apply();
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
}