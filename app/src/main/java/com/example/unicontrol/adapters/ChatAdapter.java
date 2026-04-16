package com.example.unicontrol.adapters;

import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
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
    private int themeColor = Color.parseColor("#AEC6CF");

    public interface MessageClickListener {
        void onMessageLongClick(ChatMessage message, View anchorView);
    }
    private MessageClickListener messageClickListener;

    public void setMessageClickListener(MessageClickListener listener) {
        this.messageClickListener = listener;
    }

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

    // NEU: Damit können wir das "KI schreibt..." wieder verschwinden lassen
    public void removeMessage(ChatMessage message) {
        int index = messages.indexOf(message);
        if (index >= 0) {
            messages.remove(index);
            notifyItemRemoved(index);
        }
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
            if (vh.cardView != null) vh.cardView.setCardBackgroundColor(themeColor);
            boolean isDark = ColorUtils.calculateLuminance(themeColor) < 0.5;
            vh.tvMessage.setTextColor(isDark ? Color.WHITE : Color.parseColor("#333333"));
            vh.tvMessage.setTypeface(null, Typeface.NORMAL);

            if (msg.hasAttachment() && vh.ivAttachment != null) {
                if (msg.getMimeType() != null && msg.getMimeType().startsWith("image/")) {
                    try {
                        Uri uri = Uri.parse(msg.getAttachmentUri());
                        if ("content".equals(uri.getScheme())) {
                            vh.ivAttachment.setVisibility(View.GONE);
                        } else {
                            vh.ivAttachment.setVisibility(View.VISIBLE);
                            vh.ivAttachment.setImageURI(uri);
                        }
                    } catch (Exception e) {
                        vh.ivAttachment.setVisibility(View.GONE);
                        e.printStackTrace();
                    }
                } else {
                    vh.ivAttachment.setVisibility(View.GONE);
                }
            } else if (vh.ivAttachment != null) {
                vh.ivAttachment.setVisibility(View.GONE);
            }

            if (msg.getText() == null || msg.getText().isEmpty()) {
                vh.tvMessage.setVisibility(View.GONE);
            } else {
                vh.tvMessage.setVisibility(View.VISIBLE);
            }

        } else if (viewType == VIEW_TYPE_SYSTEM) {
            if (vh.cardView != null) {
                vh.cardView.setCardBackgroundColor(Color.TRANSPARENT);
                vh.cardView.setCardElevation(0f);
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) vh.cardView.getLayoutParams();
                params.gravity = Gravity.CENTER;
                vh.cardView.setLayoutParams(params);
            }
            vh.tvMessage.setTextColor(Color.parseColor("#888888"));
            vh.tvMessage.setTextSize(12f);
            vh.tvMessage.setTypeface(null, Typeface.NORMAL);
            if (vh.ivAttachment != null) vh.ivAttachment.setVisibility(View.GONE);
            vh.tvMessage.setVisibility(View.VISIBLE);

        } else {
            // AGENT (Links)
            if (vh.cardView != null) {
                vh.cardView.setCardBackgroundColor(Color.WHITE);
                vh.cardView.setCardElevation(1f);
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) vh.cardView.getLayoutParams();
                params.gravity = Gravity.START;
                vh.cardView.setLayoutParams(params);
            }

            // NEU: Wenn es die "KI schreibt..." Blase ist, machen wir sie grau und kursiv!
            if (msg.isTypingIndicator()) {
                vh.tvMessage.setTextColor(Color.parseColor("#999999"));
                vh.tvMessage.setTypeface(null, Typeface.ITALIC);
            } else {
                vh.tvMessage.setTextColor(Color.parseColor("#333333"));
                vh.tvMessage.setTypeface(null, Typeface.NORMAL);
            }

            vh.tvMessage.setTextSize(16f);
            if (vh.ivAttachment != null) vh.ivAttachment.setVisibility(View.GONE);
            vh.tvMessage.setVisibility(View.VISIBLE);
        }

        if (vh.cardView != null) {
            vh.cardView.setOnLongClickListener(v -> {
                // Das Popup-Menü darf bei der "Tippt gerade..." Blase nicht aufgehen
                if (messageClickListener != null && !msg.isSystem() && !msg.isTypingIndicator()) {
                    messageClickListener.onMessageLongClick(msg, vh.cardView);
                    return true;
                }
                return false;
            });
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;
        MaterialCardView cardView;
        ImageView ivAttachment;

        MessageViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            cardView = itemView.findViewById(R.id.messageCard);
            ivAttachment = itemView.findViewById(R.id.ivAttachment);
        }
    }
}