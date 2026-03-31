package com.example.unicontrol.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.example.unicontrol.R;
import com.example.unicontrol.models.ImmichAlbum;

import java.util.List;

public class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder> {

    public interface OnAlbumClickListener {
        void onAlbumClick(ImmichAlbum album);
    }

    private final Context context;
    private final List<ImmichAlbum> albums;
    private final String baseUrl;
    private final String apiKey;
    private final OnAlbumClickListener listener;

    public AlbumAdapter(Context context, List<ImmichAlbum> albums, String baseUrl, String apiKey, OnAlbumClickListener listener) {
        this.context = context;
        this.albums = albums;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AlbumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_album, parent, false);
        // Wir machen die Kachel quadratisch
        int width = parent.getMeasuredWidth() / 2;
        view.setLayoutParams(new RecyclerView.LayoutParams(width, width + 150)); // Platz für Text darunter
        return new AlbumViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
        ImmichAlbum album = albums.get(position);

        holder.tvTitle.setText(album.albumName != null ? album.albumName : "Unbenanntes Album");
        holder.tvCount.setText(album.assetCount + " Elemente");

        // Zeige Badge, wenn geteilt
        if (album.shared) {
            holder.layoutSharedBadge.setVisibility(View.VISIBLE);
        } else {
            holder.layoutSharedBadge.setVisibility(View.GONE);
        }

        // Lade das Cover-Bild (falls vorhanden)
        if (album.albumThumbnailAssetId != null && !album.albumThumbnailAssetId.isEmpty()) {
            String thumbnailUrl = baseUrl + "/api/assets/" + album.albumThumbnailAssetId + "/thumbnail";
            GlideUrl glideUrl = new GlideUrl(thumbnailUrl, new LazyHeaders.Builder()
                    .addHeader("x-api-key", apiKey)
                    .addHeader("Accept", "image/*")
                    .build());

            Glide.with(context)
                    .load(glideUrl)
                    .centerCrop()
                    .into(holder.imageCover);
        } else {
            holder.imageCover.setImageDrawable(null); // Leer lassen, falls kein Cover da ist
        }

        holder.itemView.setOnClickListener(v -> listener.onAlbumClick(album));
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }

    public static class AlbumViewHolder extends RecyclerView.ViewHolder {
        ImageView imageCover;
        TextView tvTitle;
        TextView tvCount;
        LinearLayout layoutSharedBadge;

        public AlbumViewHolder(@NonNull View itemView) {
            super(itemView);
            imageCover = itemView.findViewById(R.id.image_album_cover);
            tvTitle = itemView.findViewById(R.id.tv_album_title);
            tvCount = itemView.findViewById(R.id.tv_album_count);
            layoutSharedBadge = itemView.findViewById(R.id.layout_shared_badge);
        }
    }
}