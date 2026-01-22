package com.weinmann.ccr;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.weinmann.ccr.services.DownloadService;

import java.util.Locale;

public class DownloadActivity extends AppCompatActivity implements DownloadService.IDownloadCallback {
    private Button btnDownload;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvPodcastProgress;
    private TextView tvEpisodeProgress;
    private TextView tvLog;

    private DownloadService downloadService;
    private boolean isServiceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DownloadService.LocalBinder binder = (DownloadService.LocalBinder) service;
            downloadService = binder.getService();
            isServiceBound = true;
            downloadService.setCallback(DownloadActivity.this);

            long podcastId = getIntent().getLongExtra("podcastId", -1);

            if (podcastId != -1) {
                downloadService.startDownload(podcastId);
            }
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            downloadService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);

        initViews();
        setupClickListeners();

        // Bind to service
        Intent serviceIntent = new Intent(this, DownloadService.class);
        startForegroundService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void initViews() {
        btnDownload = findViewById(R.id.btnDownload);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        tvPodcastProgress = findViewById(R.id.tvPodcastProgress);
        tvEpisodeProgress = findViewById(R.id.tvEpisodeProgress);
        tvLog = findViewById(R.id.tvLog);
        updateUI();
    }

    private void setupClickListeners() {
        btnDownload.setOnClickListener(v -> {
            if (isServiceBound && downloadService != null) {
                if (downloadService.isDownloading()) {
                    downloadService.abortDownload();
                } else {
                    if (NotificationPermissionChecker.checkNotificationPermission(this)) {
                        downloadService.startDownload(null);
                    }
                }

                updateUI();
            }
        });
    }

    private void updateUI() {
        if (isServiceBound && downloadService != null && downloadService.isDownloading()) {
            btnDownload.setText(R.string.abort_download);
            return;
        }
        btnDownload.setText(R.string.download_episodes);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isServiceBound && downloadService != null) {
            downloadService.setCallback(null);
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    // IDownloadCallback implementation
    @Override
    public void onStatusUpdate(String status) {
        tvStatus.setText(status);
    }

    @Override
    public void onProgressUpdate(int progress, int max) {
        progressBar.setMax(max);
        progressBar.setProgress(progress);
    }

    @Override
    public void onPodcastProgressUpdate(int completed, int total) {
        String text = String.format(Locale.getDefault(), "Podcasts: %d / %d", completed, total);
        tvPodcastProgress.setText(text);
    }

    @Override
    public void onEpisodeProgressUpdate(int completed, int total) {
        String text = String.format(Locale.getDefault(), "Episodes: %d / %d", completed, total);
        tvEpisodeProgress.setText(text);
    }

    @Override
    public void onLogAppend(String message) {
        tvLog.setText(message);
    }

    @Override
    public void onDownloadComplete(boolean wasAborted) {
        btnDownload.setText(R.string.download_episodes);
        tvStatus.setText(wasAborted ? "Download aborted" : "Download complete");
    }
}
