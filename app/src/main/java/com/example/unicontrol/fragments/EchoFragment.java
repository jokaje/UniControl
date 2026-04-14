package com.example.unicontrol.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.unicontrol.R;
import com.example.unicontrol.adapters.ChatAdapter;
import com.example.unicontrol.models.ChatMessage;
import com.example.unicontrol.network.OpenClawClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class EchoFragment extends Fragment {

    private static final String TAG = "EchoFragment";
    private static final String PREF_CHAT_HISTORY = "chat_history_prefs";
    private static final String KEY_MESSAGES = "chat_messages_list";

    private RecyclerView chatRecyclerView;
    private EditText messageEditText;
    private ImageButton sendButton;

    private ChatAdapter chatAdapter;
    private OpenClawClient openClawClient;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Gson gson = new Gson();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_echo, container, false);

        chatRecyclerView = view.findViewById(R.id.chatRecyclerView);
        messageEditText = view.findViewById(R.id.messageEditText);
        sendButton = view.findViewById(R.id.sendButton);

        chatAdapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);
        chatRecyclerView.setAdapter(chatAdapter);

        // NEU: Lade alte Chat-Nachrichten, bevor wir neue System-Meldungen hinzufügen
        loadChatHistory();

        openClawClient = new OpenClawClient(requireContext());

        openClawClient.setChatListener(new OpenClawClient.ChatListener() {
            @Override
            public void onMessageReceived(String text) {
                // Echte Agent-Nachricht (isSystem = false)
                mainHandler.post(() -> addMessageToUI(new ChatMessage(text, false, false)));
            }

            @Override
            public void onConnectionStatusChanged(String status) {
                // Nur System-Meldung (isSystem = true) - wird nicht im Verlauf gespeichert
                mainHandler.post(() -> addMessageToUI(new ChatMessage(status, false, true)));
            }
        });

        sendButton.setOnClickListener(v -> {
            String text = messageEditText.getText().toString().trim();
            if (!text.isEmpty()) {
                sendMessage(text);
            }
        });

        openClawClient.connect();

        return view;
    }

    private void sendMessage(String text) {
        // Echte User-Nachricht (isSystem = false)
        addMessageToUI(new ChatMessage(text, true, false));
        messageEditText.setText("");
        openClawClient.sendChatMessage(text);
    }

    private void addMessageToUI(ChatMessage message) {
        chatAdapter.addMessage(message);
        chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);

        // Jedes Mal, wenn eine ECHTE Nachricht reinkommt, speichern wir den Verlauf
        if (!message.isSystem()) {
            saveChatHistory();
        }
    }

    // --- NEU: Chat Speichern und Laden ---
    private void saveChatHistory() {
        if (getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences(PREF_CHAT_HISTORY, Context.MODE_PRIVATE);

        // Wir filtern die System-Meldungen heraus, damit wir sie beim nächsten Start nicht wieder laden
        List<ChatMessage> toSave = new ArrayList<>();
        for (ChatMessage msg : chatAdapter.getMessages()) {
            if (!msg.isSystem()) {
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
        } else {
            // Begrüßung nur, wenn der Chat komplett leer ist
            addMessageToUI(new ChatMessage("Verbinde mit OpenClaw...", false, true));
        }
    }
    // -------------------------------------

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (openClawClient != null) {
            openClawClient.disconnect();
        }
    }
}