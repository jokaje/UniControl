package com.example.unicontrol.adapters;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.example.unicontrol.R;
import com.example.unicontrol.models.ImmichAsset;

import java.util.Collections;
import java.util.List;

public class FullscreenPagerAdapter extends RecyclerView.Adapter<FullscreenPagerAdapter.PagerViewHolder> {
    private final Context context;
    private final List<ImmichAsset> assets;
    private final String baseUrl;
    private final String apiKey;
    private final boolean isStoryMode;

    public interface SwipeListener {
        void onSwipeUp();
        void onSwipeDown();
        void onSingleTap(float x, float width);
        void onLongPress();
        void onVideoEnded();
    }

    private final SwipeListener swipeListener;

    public FullscreenPagerAdapter(Context context, List<ImmichAsset> assets, String baseUrl, String apiKey, boolean isStoryMode, SwipeListener listener) {
        this.context = context;
        this.assets = assets;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.isStoryMode = isStoryMode;
        this.swipeListener = listener;
    }

    @NonNull
    @Override
    public PagerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_fullscreen_foto, parent, false);
        return new PagerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PagerViewHolder holder, int position) {
        ImmichAsset asset = assets.get(position);

        boolean isVideo = asset.type != null && asset.type.equals("VIDEO");

        holder.imageView.setVisibility(View.VISIBLE);
        if (holder.videoView != null) holder.videoView.setVisibility(View.GONE);
        if (holder.btnPlay != null) holder.btnPlay.setVisibility(View.GONE);

        if (asset.isLocalOnly && asset.localUri != null) {
            Glide.with(context).load(Uri.parse(asset.localUri)).fitCenter().into(holder.imageView);
        } else {
            String imageUrl = isVideo ? (baseUrl + "/api/assets/" + asset.id + "/thumbnail") : (baseUrl + "/api/assets/" + asset.id + "/original");
            GlideUrl glideUrl = new GlideUrl(imageUrl, new LazyHeaders.Builder().addHeader("x-api-key", apiKey).addHeader("Accept", "application/json").build());
            Glide.with(context).load(glideUrl).fitCenter().into(holder.imageView);
        }

        if (isVideo) {
            if (holder.exoPlayer == null) {
                holder.exoPlayer = new ExoPlayer.Builder(context).build();
                holder.videoView.setPlayer(holder.exoPlayer);
                holder.exoPlayer.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        if (playbackState == Player.STATE_ENDED) {
                            swipeListener.onVideoEnded();
                        }
                    }
                });
            }

            if (asset.isLocalOnly && asset.localUri != null) {
                holder.exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(asset.localUri)));
            } else {
                String videoUrl = baseUrl + "/api/assets/" + asset.id + "/original";
                DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                        .setDefaultRequestProperties(Collections.singletonMap("x-api-key", apiKey));
                MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(videoUrl)));
                holder.exoPlayer.setMediaSource(mediaSource);
            }
            holder.exoPlayer.prepare();

            if (isStoryMode) {
                holder.imageView.setVisibility(View.GONE);
                holder.videoView.setVisibility(View.VISIBLE);
                holder.exoPlayer.setPlayWhenReady(true);
            } else if (holder.btnPlay != null) {
                holder.btnPlay.setVisibility(View.VISIBLE);
                holder.btnPlay.setOnClickListener(v -> {
                    holder.imageView.setVisibility(View.GONE);
                    holder.btnPlay.setVisibility(View.GONE);
                    holder.videoView.setVisibility(View.VISIBLE);
                    holder.exoPlayer.setPlayWhenReady(true);
                });
            }
        }

        holder.imageView.setOnViewTapListener((view, x, y) -> swipeListener.onSingleTap(x, view.getWidth()));
        holder.imageView.setOnPhotoTapListener((view, x, y) -> swipeListener.onSingleTap(x * view.getWidth(), view.getWidth()));
        holder.imageView.setOnLongClickListener(v -> { swipeListener.onLongPress(); return true; });

        holder.imageView.setOnSingleFlingListener((MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) -> {
            if (e1 == null || e2 == null) return false;
            float diffY = e2.getY() - e1.getY();
            float diffX = e2.getX() - e1.getX();

            if (Math.abs(diffY) > Math.abs(diffX)) {
                if (diffY < -50 && Math.abs(velocityY) > 100) { swipeListener.onSwipeUp(); return true; }
                else if (diffY > 50 && Math.abs(velocityY) > 100) { swipeListener.onSwipeDown(); return true; }
            }
            return false;
        });

        if (holder.videoView != null) {
            holder.videoView.setOnTouchListener(new View.OnTouchListener() {
                private long downTime;
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        downTime = System.currentTimeMillis();
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (System.currentTimeMillis() - downTime < 200) {
                            swipeListener.onSingleTap(event.getX(), v.getWidth());
                        }
                    }
                    return true;
                }
            });
        }
    }

    @Override
    public void onViewRecycled(@NonNull PagerViewHolder holder) {
        super.onViewRecycled(holder);
        Glide.with(context).clear(holder.imageView);
        releasePlayer(holder);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull PagerViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        releasePlayer(holder);
    }

    private void releasePlayer(PagerViewHolder holder) {
        if (holder.exoPlayer != null) {
            holder.exoPlayer.release();
            holder.exoPlayer = null;
        }
    }

    @Override public int getItemCount() { return assets.size(); }

    public static class PagerViewHolder extends RecyclerView.ViewHolder {
        com.github.chrisbanes.photoview.PhotoView imageView;
        PlayerView videoView;
        ExoPlayer exoPlayer;
        ImageView btnPlay;

        public PagerViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_fullscreen_item);
            videoView = itemView.findViewById(R.id.video_fullscreen_item);
            btnPlay = itemView.findViewById(R.id.btn_play_fullscreen_video);
        }
    }
}