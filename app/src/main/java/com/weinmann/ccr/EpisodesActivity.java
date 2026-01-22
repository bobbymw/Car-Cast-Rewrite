package com.weinmann.ccr;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.weinmann.ccr.db.AppDatabase;
import com.weinmann.ccr.db.EpisodeMetadataDao;
import com.weinmann.ccr.records.EpisodeMetadata;
import com.weinmann.ccr.services.IMediaPlayerService;
import com.weinmann.ccr.services.MediaPlayerService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EpisodesActivity extends AppCompatActivity {

    private static final String TAG = "EpisodesActivity";
    private final List<EpisodeMetadata> episodes = new ArrayList<>();
    private ArrayAdapter<EpisodeMetadata> adapter;
    private ListView lvEpisodes;
    private IMediaPlayerService mediaPlayerService;
    private boolean isServiceBound = false;
    private final EpisodeDeleter episodeDeleter = new EpisodeDeleter(this);

    private final ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
                mediaPlayerService = binder.getService();
                isServiceBound = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                isServiceBound = false;
                mediaPlayerService = null;
            }
        };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_episodes);

        initViews();
        setupClickListeners();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.episode_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_delete_all_episodes) {
            episodeDeleter.deleteEpisodes(e -> true);
            return true;
        } else if (item.getItemId() == R.id.action_delete_listened_to_episodes) {
            episodeDeleter.deleteEpisodes(EpisodeMetadata::isListenedTo);
            return true;
        } else if (item.getItemId() == R.id.action_clear_history) {
            AppDatabase.getExecutor().execute(() -> {
                AppDatabase db = AppDatabase.getInstance(this);
                db.episodeMetadataDao().clearAllHistory();

                runOnUiThread(() -> Toast.makeText(
                        EpisodesActivity.this,
                        "All download history cleared",
                        Toast.LENGTH_LONG
                ).show());
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
        inflater.inflate(R.menu.episode_context_menu, menu);
    }

    // CONTEXT MENU HANDLING
    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        if (info != null) {

            if (item.getItemId() == R.id.menu_play_episode) {
                mediaPlayerService.setEpisodeIndex(info.position, true);
                return true;
            } else if (item.getItemId() == R.id.menu_delete_episode) {
                EpisodeMetadata episode = episodes.get(info.position);
                episodeDeleter.deleteEpisodes(e -> e.id() == episode.id());
                return true;
            }
        }

        return super.onContextItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MediaPlayerService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    private void initViews() {
        lvEpisodes = findViewById(R.id.episodeList);
        registerForContextMenu(lvEpisodes);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, episodes) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);
                
                EpisodeMetadata episode = getItem(position);
                if (episode != null && episode.isListenedTo()) {
                    view.setBackgroundColor(Color.LTGRAY);
                    textView.setTextColor(Color.DKGRAY);
                } else {
                    view.setBackgroundColor(Color.TRANSPARENT);
                    textView.setTextColor(Color.WHITE);
                }
                return view;
            }
        };
        lvEpisodes.setAdapter(adapter);

        AppDatabase db = AppDatabase.getInstance(this);
        EpisodeMetadataDao dao = db.episodeMetadataDao();

        dao.getObservable().observe(this, newEpisodes -> {
            episodes.clear();
            if (newEpisodes != null && !newEpisodes.isEmpty()) {
                episodes.addAll(newEpisodes);
            }
            adapter.notifyDataSetChanged();
            Log.d(TAG, String.format(Locale.getDefault(), "Active episodes updated: %d",
                    newEpisodes == null ? 0 : newEpisodes.size()));
        });
    }

    private void setupClickListeners() {
        lvEpisodes.setOnItemClickListener((parent, view, position, id) ->
            mediaPlayerService.setEpisodeIndex(position, true));
    }
}
