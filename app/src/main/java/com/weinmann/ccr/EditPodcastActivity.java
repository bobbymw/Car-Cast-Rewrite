package com.weinmann.ccr;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.weinmann.ccr.db.AppDatabase;
import com.weinmann.ccr.downloaders.PodcastDownloader;
import com.weinmann.ccr.records.PodcastMetadata;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class EditPodcastActivity extends AppCompatActivity {
    private long podcastId;
    private EditText editUrl;
    private Button btnTestEditPodcast;
    private EditText editTitle;
    private CheckBox checkboxEnabled;
    private Spinner maxDownloadsSpinner;
    private Button btnSavePodcast;
    private SpinnerItemAdapter<Integer> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        podcastId = getIntent().getLongExtra("podcastId", 0L);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_podcast);

        initViews();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshViews();
    }

    private void initViews() {
        editUrl = findViewById(R.id.editUrl);
        btnTestEditPodcast = findViewById(R.id.btnTestEditPodcast);
        editTitle = findViewById(R.id.editTitle);
        checkboxEnabled = findViewById(R.id.checkboxEnabled);
        maxDownloadsSpinner = findViewById(R.id.maxDownloadsSpinner);
        btnSavePodcast = findViewById(R.id.btnSavePodcast);

        List<SpinnerItem<Integer>> choices =
                List.of(
                        new SpinnerItem<>("Use Global Default", PodcastMetadata.USE_GLOBAL_DEFAULT_MAX_DOWNLOADS),
                        new SpinnerItem<>("2", 2),
                        new SpinnerItem<>("5", 5),
                        new SpinnerItem<>("10", 10),
                        new SpinnerItem<>("20", 20),
                        new SpinnerItem<>("Unlimited", PodcastMetadata.UNLIMITED_MAX_DOWNLOADS));
        adapter = new SpinnerItemAdapter<>(this, choices);
        maxDownloadsSpinner.setAdapter(adapter);
    }
    
    private void refreshViews() {
        if (podcastId > 0) {
            AppDatabase.getExecutor().execute(() -> {
                AppDatabase db = AppDatabase.getInstance(this);
                PodcastMetadata podcast = db.podcastMetadataDao().getById(podcastId);
                runOnUiThread(() -> setViewFromPodcast(podcast));
            });
        }
    }

    private void setupClickListeners() {
        btnSavePodcast.setOnClickListener(v ->
                AppDatabase.getExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            PodcastMetadata podcast = getPodcastFromUi();

            if (podcastId < 1) {
                db.podcastMetadataDao().insert(podcast);
            } else {
                db.podcastMetadataDao().update(podcast);
            }

            runOnUiThread(this::finish);
        }));

        btnTestEditPodcast.setOnClickListener(v -> {
            PodcastDownloader downloader = new PodcastDownloader(this, new AtomicBoolean(false));
            PodcastMetadata podcast = getPodcastFromUi();

            AppDatabase.getExecutor().execute(() -> {
                int result = downloader.testPodcast(podcast);

                runOnUiThread(() -> {
                    String message;
                    if (result > 0) {
                        message = String.format(Locale.getDefault(), "SUCCESS! Podcast would download %d episodes", result);
                    } else {
                        message = "FAILURE! No episodes would be downloaded";
                    }

                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            });

        });
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private PodcastMetadata getPodcastFromUi() {
        SpinnerItem<Integer> item = (SpinnerItem<Integer>)maxDownloadsSpinner.getSelectedItem();
        return new PodcastMetadata(
                podcastId,
                editTitle.getText().toString(),
                editUrl.getText().toString(),
                item.value(),
                checkboxEnabled.isChecked());
    }

    private void setViewFromPodcast(PodcastMetadata podcast) {
        editTitle.setText(podcast.title());
        editUrl.setText(podcast.url());
        checkboxEnabled.setChecked(podcast.isActive());
        maxDownloadsSpinner.setSelection(adapter.getIndexByValue(podcast.maxDownloads()));
        podcastId = podcast.id();
    }
}

