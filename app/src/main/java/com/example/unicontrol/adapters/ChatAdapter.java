package com.example.unicontrol.adapters;

import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.unicontrol.R;
import com.example.unicontrol.models.ChatMessage;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_AGENT = 2;
    private static final int VIEW_TYPE_SYSTEM = 3;

    private List<ChatMessage> messages = new ArrayList<>();
    private int themeColor = Color.parseColor("#AEC6CF"); // Fallback-Farbe

    // NEU: Setzt die Farbe aus den Einstellungen!
    public void setThemeColor(int color) {
        this.themeColor = color;
        notifyDataSetChanged();
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> loadedMessages) {
        this.messages = new ArrayList<>(loadedMessages);
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = messages.get(position);
        if (msg.isSystem()) {
            return VIEW_TYPE_SYSTEM;
        }
        return msg.isUser() ? VIEW_TYPE_USER : VIEW_TYPE_AGENT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_USER) {
            return new MessageViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false));
        } else {
            // Agent & System teilen sich das gleiche Layout, System wird aber in onBind anders gestylt
            return new MessageViewHolder(inflater.inflate(R.layout.item_chat_agent, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        MessageViewHolder vh = (MessageViewHolder) holder;

        vh.tvMessage.setText(msg.getText());

        int viewType = getItemViewType(position);

        if (viewType == VIEW_TYPE_USER) {
            // User-Nachricht: Hintergrund auf Theme-Farbe setzen!
            if (vh.cardView != null) {
                vh.cardView.setCardBackgroundColor(themeColor);
            }
            vh.tvMessage.setTextColor(Color.parseColor("#333333")); // Dunkle Schrift auf farbigem Grund
        } else if (viewType == VIEW_TYPE_SYSTEM) {
            // System-Nachricht: Zentriert, transparent (ohne Hintergrund)
            if (vh.cardView != null) {
                vh.cardView.setCardBackgroundColor(Color.TRANSPARENT);
                vh.cardView.setCardElevation(0f);
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) vh.cardView.getLayoutParams();
                params.gravity = Gravity.CENTER;
                vh.cardView.setLayoutParams(params);
            }
            vh.tvMessage.setTextColor(Color.parseColor("#888888")); // Grauer kleiner Text
            vh.tvMessage.setTextSize(12f);
        } else {
            // Agent-Nachricht: Weißer Hintergrund
            if (vh.cardView != null) {
                vh.cardView.setCardBackgroundColor(Color.WHITE);
                vh.cardView.setCardElevation(1f);
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) vh.cardView.getLayoutParams();
                params.gravity = Gravity.START;
                vh.cardView.setLayoutParams(params);
            }
            vh.tvMessage.setTextColor(Color.parseColor("#333333"));
            vh.tvMessage.setTextSize(16f);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;
        MaterialCardView cardView;

        MessageViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            cardView = itemView.findViewById(R.id.messageCard);
        }
    }
}