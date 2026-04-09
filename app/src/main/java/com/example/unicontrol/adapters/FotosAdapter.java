package com.example.unicontrol.adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.example.unicontrol.R;
import com.example.unicontrol.fragments.SettingsFragment;
import com.example.unicontrol.models.GalleryItem;
import com.example.unicontrol.models.ImmichAsset;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FotosAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onFotoClick(ImmichAsset asset);
    }

    public interface OnSelectionListener {
        void onSelectionChanged(int selectedCount);
    }

    private final Context context;
    private final List<GalleryItem> items;
    private final String baseUrl;
    private final String apiKey;
    private final OnItemClickListener listener;
    private final OnSelectionListener selectionListener;

    private boolean isSelectionMode = false;
    private final Set<ImmichAsset> selectedAssets = new HashSet<>();

    public FotosAdapter(Context context, List<GalleryItem> items, String baseUrl, String apiKey, OnItemClickListener listener, OnSelectionListener selectionListener) {
        this.context = context;
        this.items = items;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.listener = listener;
        this.selectionListener = selectionListener;
    }

    public void clearSelection() {
        selectedAssets.clear();
        isSelectionMode = false;
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(0);
        }
    }

    public List<ImmichAsset> getSelectedAssets() {
        return new ArrayList<>(selectedAssets);
    }

    private void toggleSelection(int position, ImmichAsset asset) {
        if (selectedAssets.contains(asset)) {
            selectedAssets.remove(asset);
        } else {
            selectedAssets.add(asset);
        }

        if (selectedAssets.isEmpty()) {
            isSelectionMode = false;
            notifyDataSetChanged();
        } else {
            notifyItemChanged(position);
        }

        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selectedAssets.size());
        }
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

        // --- NEU: Intelligente Farbermittlung (Luminanz) für die Datums-Header ---
        SharedPreferences prefs = context.getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
        String colorStr = prefs.getString(SettingsFragment.KEY_COLOR_FOTOS, "#F49AC2");
        int themeColor = Color.parseColor("#F49AC2");
        try {
            themeColor = Color.parseColor(colorStr);
        } catch (Exception e) {}

        boolean isDarkTheme = ColorUtils.calculateLuminance(themeColor) < 0.5;
        // Haupt-Monats-Header: Knackiges Weiß oder starkes Grau
        int monthColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");
        // Sub-Tages-Header: Etwas weicher, damit der Monat optisch heraussticht
        int dayColor = isDarkTheme ? Color.parseColor("#DADADA") : Color.parseColor("#555555");


        if (holder instanceof MonthHeaderViewHolder) {
            MonthHeaderViewHolder monthHolder = (MonthHeaderViewHolder) holder;
            monthHolder.tvMonth.setText(item.title);
            monthHolder.tvMonth.setTextColor(monthColor);

            // WICHTIG: Klicks hart deaktivieren, um Blocking zu verhindern
            monthHolder.itemView.setOnClickListener(null);
            monthHolder.itemView.setOnLongClickListener(null);
            monthHolder.itemView.setClickable(false);

        } else if (holder instanceof DayHeaderViewHolder) {
            DayHeaderViewHolder dayHolder = (DayHeaderViewHolder) holder;
            dayHolder.tvDay.setText(item.title);
            dayHolder.tvDay.setTextColor(dayColor);

            // WICHTIG: Klicks hart deaktivieren, um Blocking zu verhindern
            dayHolder.itemView.setOnClickListener(null);
            dayHolder.itemView.setOnLongClickListener(null);
            dayHolder.itemView.setClickable(false);

        } else if (holder instanceof FotoViewHolder) {
            ImmichAsset asset = item.asset;
            FotoViewHolder fotoHolder = (FotoViewHolder) holder;

            // Bild laden lokal vs Cloud
            if (asset.isLocalOnly && asset.localUri != null) {
                Glide.with(context)
                        .load(Uri.parse(asset.localUri))
                        .centerCrop()
                        .into(fotoHolder.imageView);
                fotoHolder.iconPending.setVisibility(View.VISIBLE);
            } else {
                String thumbnailUrl = baseUrl + "/api/assets/" + asset.id + "/thumbnail";
                GlideUrl glideUrl = new GlideUrl(thumbnailUrl, new LazyHeaders.Builder()
                        .addHeader("x-api-key", apiKey)
                        .addHeader("Accept", "application/json")
                        .build());

                Glide.with(context)
                        .load(glideUrl)
                        .centerCrop()
                        .into(fotoHolder.imageView);
                fotoHolder.iconPending.setVisibility(View.GONE);
            }

            if (asset.type != null && asset.type.equals("VIDEO")) {
                fotoHolder.iconPlay.setVisibility(View.VISIBLE);
            } else {
                fotoHolder.iconPlay.setVisibility(View.GONE);
            }

            boolean isSelected = selectedAssets.contains(asset);
            if (isSelected) {
                fotoHolder.imageView.setScaleX(0.85f);
                fotoHolder.imageView.setScaleY(0.85f);
                fotoHolder.itemView.setBackgroundColor(Color.parseColor("#E0F0FF"));

                if (fotoHolder.iconSelected != null) fotoHolder.iconSelected.setVisibility(View.VISIBLE);
            } else {
                fotoHolder.imageView.setScaleX(1.0f);
                fotoHolder.imageView.setScaleY(1.0f);
                fotoHolder.itemView.setBackgroundColor(Color.TRANSPARENT);

                if (fotoHolder.iconSelected != null) fotoHolder.iconSelected.setVisibility(View.GONE);
            }

            // --- WICHTIG: Klicks explizit erlauben und Listener sauber zuweisen ---
            fotoHolder.itemView.setClickable(true);
            fotoHolder.itemView.setFocusable(true);

            fotoHolder.itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(position, asset);
                } else {
                    if (listener != null) {
                        listener.onFotoClick(asset);
                    }
                }
            });

            fotoHolder.itemView.setOnLongClickListener(v -> {
                if (!isSelectionMode) {
                    isSelectionMode = true;
                    toggleSelection(position, asset);
                    return true;
                }
                return false;
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
        ImageView iconPlay;
        TextView iconSelected;
        ImageView iconPending;

        public FotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_view_foto);
            iconPlay = itemView.findViewById(R.id.icon_play_video);
            iconSelected = itemView.findViewById(R.id.icon_selected);
            iconPending = itemView.findViewById(R.id.icon_pending_upload);
        }
    }
}