package com.weinmann.ccr;

import com.weinmann.ccr.records.*;
import com.weinmann.ccr.services.IMediaPlayerService;
import com.weinmann.ccr.services.MediaPlayerService;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int AUDIO_PERMISSION_REQUEST_CODE = 1002;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private ImageButton btnPlayPause;
    private ImageButton btnPrevious;
    private ImageButton btnRewind;
    private ImageButton btnForward;
    private ImageButton btnNext;
    private TextView tvPodcast;
    private TextView tvPubDate;
    private TextView tvTitle;
    private TextView tvPosition;
    private TextView tvEpisodeNumber;
    private TextView tvDuration;
    private SeekBar seekBar;

    private IMediaPlayerService mediaPlayerService;
    private boolean isServiceBound = false;
    private Runnable updateTimeRunnable;
    private boolean isUserSeeking = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            mediaPlayerService = binder.getService();
            isServiceBound = true;
            updateUI();
            startTimeUpdate();
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
        setContentView(R.layout.activity_main);

        initViews();
        setupClickListeners();

        // Check and request permissions before starting service
        checkPermissionsAndStart();
    }

    private void checkPermissionsAndStart() {
        if (checkAudioPermission() && NotificationPermissionChecker.checkNotificationPermission(this)) {
            startMediaService();
        }
    }

    private boolean checkAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_AUDIO},
                        AUDIO_PERMISSION_REQUEST_CODE);
                return false;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        AUDIO_PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    private void startMediaService() {
        // Start and bind to the service
        Intent serviceIntent = new Intent(this, MediaPlayerService.class);
        startForegroundService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void initViews() {
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPrevious = findViewById(R.id.btnPrevious);
        btnRewind = findViewById(R.id.btnRewind);
        btnForward = findViewById(R.id.btnForward);
        btnNext = findViewById(R.id.btnNext);
        tvEpisodeNumber = findViewById(R.id.tvEpisodeNumber);
        tvPosition = findViewById(R.id.tvPosition);
        tvDuration = findViewById(R.id.tvDuration);
        tvPodcast = findViewById(R.id.tvPodcast);
        tvPubDate = findViewById(R.id.tvPubDate);
        tvTitle = findViewById(R.id.tvTitle);
        seekBar = findViewById(R.id.seekBar);
    }

    private void setupClickListeners() {
        btnPlayPause.setOnClickListener(v -> {
            if (isServiceBound && mediaPlayerService != null) {
                mediaPlayerService.playPause(!mediaPlayerService.isPlaying());
                updateUI();
            }
        });

        btnPrevious.setOnClickListener(v -> {
            if (isServiceBound && mediaPlayerService != null) {
                mediaPlayerService.setEpisodeIndex(
                        mediaPlayerService.getCurrentEpisodeIndex() - 1,
                        mediaPlayerService.isPlaying());
                updateUI();
            }
        });

        btnRewind.setOnClickListener(v -> {
            if (isServiceBound && mediaPlayerService != null) {
                mediaPlayerService.seekBackward();
                updateUI();
            }
        });

        btnForward.setOnClickListener(v -> {
            if (isServiceBound && mediaPlayerService != null) {
                mediaPlayerService.seekForward();
                updateUI();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (isServiceBound && mediaPlayerService != null) {
                mediaPlayerService.setEpisodeIndex(
                        mediaPlayerService.getCurrentEpisodeIndex() + 1,
                        mediaPlayerService.isPlaying());
                updateUI();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int currentPosition, boolean fromUser) {
                if (fromUser && isServiceBound && mediaPlayerService != null) {
                    // Update time display while seeking
                    updateProgress(currentPosition);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (isServiceBound && mediaPlayerService != null) {
                    mediaPlayerService.seekTo(seekBar.getProgress());
                }
                isUserSeeking = false;
            }
        });
    }

    private void updateProgress(long progress) {
        String position = Util.formatTime(progress);
        String duration = Util.formatTime(mediaPlayerService.getDuration());
        tvPosition.setText(position);
        tvDuration.setText(duration);
    }

    private void updateUI() {
        if (isServiceBound && mediaPlayerService != null) {
            EpisodeMetadata currentEpisode = mediaPlayerService.getCurrentEpisode();
            if (currentEpisode == null) {
                tvPodcast.setText("");
                tvPubDate.setText(R.string.no_episodes_text);
                tvTitle.setText("");
                tvEpisodeNumber.setText("0/0");
            } else {
                tvPodcast.setText(currentEpisode.podcastName());
                tvPubDate.setText("Published " + currentEpisode.getPubDateString());
                tvTitle.setText(currentEpisode.title());
                String episodeNumber = String.format(Locale.getDefault(), "%d/%d",
                        mediaPlayerService.getCurrentEpisodeIndex() + 1,
                        mediaPlayerService.getEpisodeCount());
                tvEpisodeNumber.setText(episodeNumber);
            }
            if (mediaPlayerService.isPlaying()) {
                btnPlayPause.setImageResource(R.drawable.player_102_pause);
            } else {
                btnPlayPause.setImageResource(R.drawable.player_102_play);
            }
        }
    }

    private void startTimeUpdate() {
        updateTimeRunnable = new Runnable() {
            @Override
            public void run() {
                if (isServiceBound && mediaPlayerService != null) {
                    long currentPosition = mediaPlayerService.getCurrentPosition();
                    long duration = mediaPlayerService.getDuration();

                    // Update seekbar max and progress (only if user is not seeking)
                    if (!isUserSeeking) {
                        seekBar.setMax((int)duration);
                        seekBar.setProgress((int)currentPosition);
                    }

                    updateProgress(currentPosition);
                    updateUI();
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(updateTimeRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateTimeRunnable != null) {
            handler.removeCallbacks(updateTimeRunnable);
        }
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isServiceBound && mediaPlayerService != null) {
            updateUI();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Audio permission granted", Toast.LENGTH_SHORT).show();
                checkPermissionsAndStart();
            } else {
                Toast.makeText(this, "Audio permission is required to play episodes.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start the service
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
                checkPermissionsAndStart();
            } else {
                // Permission denied
                Toast.makeText(this, "Notification permission is required for media controls. " +
                        "Please enable it in app settings.", Toast.LENGTH_LONG).show();

                // Optionally, show dialog to open settings
                new AlertDialog.Builder(this)
                        .setTitle(R.string.notification_permission_title)
                        .setMessage("Please enable notification permission in app settings to use media controls.")
                        .setPositiveButton("Open Settings", (dialog, which) -> {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_download) {
            startActivity(new Intent(this, DownloadActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_podcasts) {
            startActivity(new Intent(this, PodcastsActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_episodes) {
            startActivity(new Intent(this, EpisodesActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_import_opml) {
            startActivity(new Intent(this, ImportOpmlActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_exit) {
            exitApp();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void exitApp() {
        // Stop the service
        Intent serviceIntent = new Intent(this, MediaPlayerService.class);
        stopService(serviceIntent);

        // Unbind if bound
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }

        // Finish the activity
        finishAffinity();
    }
}
