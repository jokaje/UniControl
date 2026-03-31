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
import com.example.unicontrol.models.ImmichPerson;

import java.util.List;

public class PersonAdapter extends RecyclerView.Adapter<PersonAdapter.PersonViewHolder> {

    public interface PersonListener {
        void onFaceClick(ImmichPerson person);
        void onNameEditClick(ImmichPerson person);
    }

    private final Context context;
    private final List<ImmichPerson> people;
    private final String baseUrl;
    private final String apiKey;
    private final PersonListener listener;

    public PersonAdapter(Context context, List<ImmichPerson> people, String baseUrl, String apiKey, PersonListener listener) {
        this.context = context;
        this.people = people;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PersonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_person, parent, false);
        return new PersonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PersonViewHolder holder, int position) {
        ImmichPerson person = people.get(position);

        if (person.name == null || person.name.trim().isEmpty()) {
            holder.tvName.setText("Wer ist das?");
            holder.tvName.setTextColor(0xFFAAAAAA);
        } else {
            holder.tvName.setText(person.name);
            holder.tvName.setTextColor(0xFF333333);
        }

        // Wir bauen beide möglichen Links (Neues Immich Update vs. Altes Immich)
        String newThumbnailUrl = baseUrl + "/api/people/" + person.id + "/thumbnail";
        String oldThumbnailUrl = baseUrl + "/api/person/" + person.id + "/thumbnail";

        GlideUrl newGlideUrl = new GlideUrl(newThumbnailUrl, new LazyHeaders.Builder()
                .addHeader("x-api-key", apiKey)
                .addHeader("Accept", "image/*")
                .build());

        GlideUrl oldGlideUrl = new GlideUrl(oldThumbnailUrl, new LazyHeaders.Builder()
                .addHeader("x-api-key", apiKey)
                .addHeader("Accept", "image/*")
                .build());

        // Glide probiert die neue Adresse. Falls das fehlschlägt (.error), probiert es automatisch die alte!
        Glide.with(context)
                .load(newGlideUrl)
                .error(Glide.with(context).load(oldGlideUrl))
                .centerCrop()
                .into(holder.imageFace);

        holder.imageFace.setOnClickListener(v -> listener.onFaceClick(person));
        holder.layoutName.setOnClickListener(v -> listener.onNameEditClick(person));
    }

    @Override
    public int getItemCount() {
        return people.size();
    }

    public static class PersonViewHolder extends RecyclerView.ViewHolder {
        ImageView imageFace;
        TextView tvName;
        LinearLayout layoutName;

        public PersonViewHolder(@NonNull View itemView) {
            super(itemView);
            imageFace = itemView.findViewById(R.id.image_person_face);
            tvName = itemView.findViewById(R.id.tv_person_name);
            layoutName = itemView.findViewById(R.id.layout_person_name);
        }
    }
}