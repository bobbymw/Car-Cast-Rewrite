package com.weinmann.ccr;

import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.weinmann.ccr.db.AppDatabase;
import com.weinmann.ccr.records.PodcastMetadata;

import java.util.ArrayList;
import java.util.List;

public class PodcastsActivity extends AppCompatActivity {

    private final List<PodcastMetadata> podcasts = new ArrayList<>();
    private final EpisodeDeleter episodeDeleter = new EpisodeDeleter(this);

    private ArrayAdapter<PodcastMetadata> adapter;

    private final ActivityResultLauncher<Intent> editPodcastLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            refreshViews();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcasts);

        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshViews();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.podcast_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_reset_to_demos) {
            AppDatabase.getExecutor().execute(() -> {
                AppDatabase db = AppDatabase.getInstance(this);
                db.podcastMetadataDao().deleteAll();

                for (PodcastMetadata podcast : CcrApplication.DefaultPodcasts) {
                    db.podcastMetadataDao().insert(podcast);
                }

                runOnUiThread(() -> {
                    Toast.makeText(
                            PodcastsActivity.this,
                            "Podcasts reset to demos",
                            Toast.LENGTH_LONG
                    ).show();
                    refreshViews();
                });
            });
            return true;
        } else if (item.getItemId() == R.id.action_export_opml) {
            startActivity(new Intent(this, ExportOpmlActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_import_opml) {
            editPodcastLauncher.launch(new Intent(this, ImportOpmlActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_podcast_search) {
            startActivity(new Intent(this, PodcastSearchActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_add_podcast) {
            editPodcastLauncher.launch(new Intent(this, EditPodcastActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_delete_all_podcasts) {
            AppDatabase.getExecutor().execute(() -> {
                AppDatabase db = AppDatabase.getInstance(this);
                db.podcastMetadataDao().deleteAll();

                runOnUiThread(() -> {
                    Toast.makeText(
                            PodcastsActivity.this,
                            "All podcasts deleted",
                            Toast.LENGTH_LONG
                    ).show();
                    refreshViews();
                });
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // CONTEXT MENU CREATION
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.podcast_context_menu, menu);

        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) menuInfo;
        PodcastMetadata podcast = podcasts.get(info.position);

        // Update menu text depending on podcast state
        MenuItem disableItem = menu.findItem(R.id.menu_toggle_enable_podcast);
        disableItem.setTitle(podcast.isActive() ? "Disable Podcast" : "Enable Podcast");
    }

    // CONTEXT MENU HANDLING
    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        if (info != null) {
            PodcastMetadata podcast = podcasts.get(info.position);

            if (item.getItemId() == R.id.menu_toggle_enable_podcast) {
                toggleEnablePodcast(podcast);
                return true;
            } else if (item.getItemId() == R.id.menu_edit_podcast) {
                editPodcast(podcast);
                return true;
            } else if (item.getItemId() == R.id.menu_download_podcast) {
                downloadPodcast(podcast);
                return true;
            } else if (item.getItemId() == R.id.menu_delete_episodes) {
                deleteEpisodes(podcast);
                return true;
            } else if (item.getItemId() == R.id.menu_delete_podcast) {
                deletePodcast(podcast);
                return true;
            }
        }

        return super.onContextItemSelected(item);
    }

    private void initViews() {
        ListView lvPodcasts = findViewById(R.id.podcastList);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, podcasts);
        lvPodcasts.setAdapter(adapter);

        // enable long-press context menu ⬅️
        registerForContextMenu(lvPodcasts);
    }
    
    private void refreshViews() {
        AppDatabase.getExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            List<PodcastMetadata> newPodcasts = db.podcastMetadataDao().getAll();
            runOnUiThread(() -> {
                podcasts.clear();
                podcasts.addAll(newPodcasts);
                adapter.notifyDataSetChanged();
            });
        });
    }

    // DB ACTION: DISABLE
    private void toggleEnablePodcast(PodcastMetadata podcast) {
        AppDatabase.getExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            PodcastMetadata updated = new PodcastMetadata(
                    podcast.id(),
                    podcast.title(),
                    podcast.url(),
                    podcast.maxDownloads(),
                    !podcast.isActive()
            );

            db.podcastMetadataDao().update(updated);

            runOnUiThread(this::refreshViews);
        });
    }

    private void editPodcast(PodcastMetadata podcast) {
        Intent intent = new Intent(this, EditPodcastActivity.class);
        intent.putExtra("podcastId", podcast.id());
        startActivity(intent);
    }

    private void downloadPodcast(PodcastMetadata podcast) {
        Intent intent = new Intent(this, DownloadActivity.class);
        intent.putExtra("podcastId", podcast.id());
        startActivity(intent);
    }

    private void deleteEpisodes(PodcastMetadata podcast) {
        episodeDeleter.deleteEpisodes(e -> e.podcastId() == podcast.id());
    }

    private void deletePodcast(PodcastMetadata podcast) {
        AppDatabase.getExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            db.podcastMetadataDao().delete(podcast);

            runOnUiThread(this::refreshViews);
        });
    }
}

