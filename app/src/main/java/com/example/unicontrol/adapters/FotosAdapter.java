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
import com.example.unicontrol.models.GalleryItem;
import com.example.unicontrol.models.ImmichAsset;

import java.util.List;

public class FotosAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onFotoClick(ImmichAsset asset);
    }

    private final Context context;
    private final List<GalleryItem> items;
    private final String baseUrl;
    private final String apiKey;
    private final OnItemClickListener listener;

    public FotosAdapter(Context context, List<GalleryItem> items, String baseUrl, String apiKey, OnItemClickListener listener) {
        this.context = context;
        this.items = items;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == GalleryItem.TYPE_MONTH_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_header_month, parent, false);
            return new MonthHeaderViewHolder(view);
        } else if (viewType == GalleryItem.TYPE_DAY_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_header_day, parent, false);
            return new DayHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_foto, parent, false);
            return new FotoViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        GalleryItem item = items.get(position);

        if (holder instanceof MonthHeaderViewHolder) {
            ((MonthHeaderViewHolder) holder).tvMonth.setText(item.title);
        } else if (holder instanceof DayHeaderViewHolder) {
            ((DayHeaderViewHolder) holder).tvDay.setText(item.title);
        } else if (holder instanceof FotoViewHolder) {
            ImmichAsset asset = item.asset;
            String thumbnailUrl = baseUrl + "/api/assets/" + asset.id + "/thumbnail";

            GlideUrl glideUrl = new GlideUrl(thumbnailUrl, new LazyHeaders.Builder()
                    .addHeader("x-api-key", apiKey)
                    .addHeader("Accept", "application/json")
                    .build());

            FotoViewHolder fotoHolder = (FotoViewHolder) holder;
            Glide.with(context)
                    .load(glideUrl)
                    .centerCrop()
                    .into(fotoHolder.imageView);

            // NEU: Erkennen, ob es ein Video ist, und Play-Symbol einblenden!
            if (asset.type != null && asset.type.equals("VIDEO")) {
                fotoHolder.iconPlay.setVisibility(View.VISIBLE);
            } else {
                fotoHolder.iconPlay.setVisibility(View.GONE);
            }

            fotoHolder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFotoClick(asset);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class MonthHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvMonth;
        public MonthHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMonth = itemView.findViewById(R.id.tv_month_header);
        }
    }

    public static class DayHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay;
        public DayHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDay = itemView.findViewById(R.id.tv_day_header);
        }
    }

    public static class FotoViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ImageView iconPlay; // NEU
        public FotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_view_foto);
            iconPlay = itemView.findViewById(R.id.icon_play_video); // NEU
        }
    }
}