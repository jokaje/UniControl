package com.example.unicontrol.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.example.unicontrol.R;
import com.example.unicontrol.models.ImmichAsset;
import com.example.unicontrol.models.ImmichMemory;

import java.util.List;

public class MemoriesAdapter extends RecyclerView.Adapter<MemoriesAdapter.MemoryViewHolder> {

    public interface OnMemoryClickListener {
        void onMemoryClick(ImmichMemory memory);
    }

    private final Context context;
    private final List<ImmichMemory> memoryList;
    private final String baseUrl;
    private final String apiKey;
    private final OnMemoryClickListener listener;

    public MemoriesAdapter(Context context, List<ImmichMemory> memoryList, String baseUrl, String apiKey, OnMemoryClickListener listener) {
        this.context = context;
        this.memoryList = memoryList;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MemoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_memory, parent, false);
        return new MemoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemoryViewHolder holder, int position) {
        ImmichMemory memory = memoryList.get(position);

        holder.tvTitle.setText(memory.title != null ? memory.title : "Erinnerung");

        if (memory.assets != null && !memory.assets.isEmpty()) {
            ImmichAsset coverAsset = memory.assets.get(0);
            String thumbnailUrl = baseUrl + "/api/assets/" + coverAsset.id + "/thumbnail";

            GlideUrl glideUrl = new GlideUrl(thumbnailUrl, new LazyHeaders.Builder()
                    .addHeader("x-api-key", apiKey)
                    .addHeader("Accept", "application/json")
                    .build());

            Glide.with(context)
                    .load(glideUrl)
                    .centerCrop()
                    .into(holder.ivCover);
        } else {
            holder.ivCover.setImageDrawable(null);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onMemoryClick(memory);
        });
    }

    @Override
    public int getItemCount() {
        return memoryList.size();
    }

    public static class MemoryViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover;
        TextView tvTitle;

        public MemoryViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_memory_cover);
            tvTitle = itemView.findViewById(R.id.tv_memory_title);
        }
    }
}