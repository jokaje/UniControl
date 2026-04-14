package com.example.unicontrol.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

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

    // NEU: UI Elemente für den Status
    private View statusIndicator;
    private TextView statusText;

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

        statusIndicator = view.findViewById(R.id.statusIndicator);
        statusText = view.findViewById(R.id.statusText);

        chatAdapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);
        chatRecyclerView.setAdapter(chatAdapter);

        // --- FARBE AUS DEN EINSTELLUNGEN LADEN ---
        SharedPreferences prefs = requireContext().getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
        String echoColorHex = prefs.getString(SettingsFragment.KEY_COLOR_ECHO, "#AEC6CF");
        int themeColor;
        try {
            themeColor = Color.parseColor(echoColorHex);
        } catch (Exception e) {
            themeColor = Color.parseColor("#AEC6CF");
        }

        // --- THEME-FARBE ANWENDEN ---
        chatAdapter.setThemeColor(themeColor);

        // Eingabefeld: Textfarbe = Theme-Farbe, Hint = Grau (damit es nie weiß-auf-weiß ist!)
        messageEditText.setTextColor(themeColor);
        messageEditText.setHintTextColor(Color.parseColor("#888888"));

        // Den Senden-Button ebenfalls in der Theme-Farbe einfärben!
        sendButton.setColorFilter(themeColor);
        // ------------------------------------------

        // Initiale Farbe setzen (Getrennt / Rot)
        updateStatusUI(Color.parseColor("#FF6961"), "Getrennt");

        // Lade alte Chat-Nachrichten
        loadChatHistory();

        openClawClient = new OpenClawClient(requireContext());

        openClawClient.setChatListener(new OpenClawClient.ChatListener() {
            @Override
            public void onMessageReceived(String text) {
                // Echte Agent-Nachricht (wird im Chat angezeigt)
                mainHandler.post(() -> addMessageToUI(new ChatMessage(text, false, false)));
            }

            @Override
            public void onConnectionStatusChanged(String status) {
                mainHandler.post(() -> {
                    // Logik für den Status-Punkt
                    int color = Color.parseColor("#FFB347"); // Standard: Orange (Verbindet...)
                    String shortStatus = "Verbinde...";

                    if (status.contains("erfolgreich") || status.contains("✅")) {
                        color = Color.parseColor("#77DD77"); // Grün
                        shortStatus = "Verbunden";
                    } else if (status.contains("Fehler") || status.contains("❌") || status.contains("Abbruch") || status.contains("fehlgeschlagen")) {
                        color = Color.parseColor("#FF6961"); // Rot
                        shortStatus = "Fehler";
                        if (getContext() != null) {
                            Toast.makeText(getContext(), status, Toast.LENGTH_LONG).show();
                        }
                    } else if (status.contains("gekoppelt werden") || status.contains("⏳")) {
                        color = Color.parseColor("#FFB347"); // Orange
                        shortStatus = "Pairing nötig";
                        if (getContext() != null) {
                            Toast.makeText(getContext(), status, Toast.LENGTH_LONG).show();
                        }
                    }

                    updateStatusUI(color, shortStatus);
                });
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

    private void updateStatusUI(int color, String text) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        statusIndicator.setBackground(shape);
        statusText.setText(text);
    }

    private void sendMessage(String text) {
        // Echte User-Nachricht
        addMessageToUI(new ChatMessage(text, true, false));
        messageEditText.setText("");
        openClawClient.sendChatMessage(text);
    }

    private void addMessageToUI(ChatMessage message) {
        chatAdapter.addMessage(message);
        chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);

        // Speichere den Verlauf
        if (!message.isSystem()) {
            saveChatHistory();
        }
    }

    private void saveChatHistory() {
        if (getContext() == null) return;
        SharedPreferences prefs = getContext().getSharedPreferences(PREF_CHAT_HISTORY, Context.MODE_PRIVATE);

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