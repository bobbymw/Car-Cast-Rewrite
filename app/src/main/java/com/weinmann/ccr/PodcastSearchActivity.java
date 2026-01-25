package com.weinmann.ccr;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.weinmann.ccr.db.AppDatabase;
import com.weinmann.ccr.itunes.ITunesApi;
import com.weinmann.ccr.records.PodcastMetadata;
import com.weinmann.ccr.itunes.ITunesSearchResponse;

import java.util.ArrayList;

import retrofit2.*;
import retrofit2.converter.gson.GsonConverterFactory;

public class PodcastSearchActivity extends AppCompatActivity {
    private EditText searchText;

    private PodcastSearchAdapter adapter;
    private ITunesApi api;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_podcast_search);

        searchText = findViewById(R.id.searchText);
        Button searchButton = findViewById(R.id.searchButton);
        ListView listView = findViewById(R.id.listViewResults);

        adapter = new PodcastSearchAdapter(this, new ArrayList<>());
        listView.setAdapter(adapter);

        db = AppDatabase.getInstance(this);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://itunes.apple.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        api = retrofit.create(ITunesApi.class);

        searchButton.setOnClickListener(v -> doSearch());

        searchText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch();
                return true;
            }
            return false;
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            ITunesSearchResponse.ITunesPodcastResult result =
                    (ITunesSearchResponse.ITunesPodcastResult) adapter.getItem(position);

            if (result.podcastUrl == null) {
                Toast.makeText(this, "No podcast URL for this podcast.", Toast.LENGTH_SHORT).show();
                return;
            }

            savePodcast(result.title, result.podcastUrl);
        });
    }

    private void doSearch() {
        String q = searchText.getText().toString().trim();
        if (q.isEmpty()) {
            Toast.makeText(this, "Enter search term", Toast.LENGTH_SHORT).show();
            return;
        }

        Call<ITunesSearchResponse> call = api.searchPodcasts(q, "podcast", 50);

        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ITunesSearchResponse> call, @NonNull Response<ITunesSearchResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(PodcastSearchActivity.this, "Search failed", Toast.LENGTH_SHORT).show();
                    return;
                }

                adapter.update(response.body().results);
            }

            @Override
            public void onFailure(@NonNull Call<ITunesSearchResponse> call, @NonNull Throwable t) {
                Toast.makeText(PodcastSearchActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void savePodcast(String title, String podcastUrl) {
        AppDatabase.getExecutor().execute(() -> {
            PodcastMetadata podcast = new PodcastMetadata(
                    0L,
                    title,
                    podcastUrl,
                    PodcastMetadata.USE_GLOBAL_DEFAULT_MAX_DOWNLOADS,
                    true
            );

            db.podcastMetadataDao().insert(podcast);

            runOnUiThread(() ->
                    Toast.makeText(PodcastSearchActivity.this, "Added: " + title, Toast.LENGTH_SHORT).show()
            );
        });
    }
}
