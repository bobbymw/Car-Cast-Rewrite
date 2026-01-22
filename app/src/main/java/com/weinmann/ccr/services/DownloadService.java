package com.weinmann.ccr.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.weinmann.ccr.DownloadActivity;
import com.weinmann.ccr.R;
import com.weinmann.ccr.db.AppDatabase;
import com.weinmann.ccr.db.EpisodeMetadataDao;
import com.weinmann.ccr.db.PodcastMetadataDao;
import com.weinmann.ccr.downloaders.AudioFileDownloader;
import com.weinmann.ccr.downloaders.PodcastDownloader;
import com.weinmann.ccr.records.EpisodeMetadata;
import com.weinmann.ccr.records.PodcastMetadata;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class DownloadService extends Service {
    private static final String TAG = "DownloadService";
    private static final String DOWNLOAD_CHANNEL_ID = "download_channel";
    private static final int DOWNLOAD_NOTIFICATION_ID = 2;
    private static final String ACTION_ABORT = "com.weinmann.ccr.ACTION_ABORT";

    private final IBinder binder = new LocalBinder();
    private final AtomicBoolean isDownloading = new AtomicBoolean(false);
    private final AtomicBoolean abortRequested = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private PodcastDownloader podcastDownloader;
    private AudioFileDownloader audioFileDownloader;
    private PodcastMetadataDao podcastMetadataDao;
    private EpisodeMetadataDao episodeMetadataDao;
    private ExecutorService downloadExecutor;
    private Runnable downloadRunnable;
    
    private int totalPodcasts = 0;
    private int totalEpisodes = 0;
    private int currentProgress = 0;
    private int completedPodcasts = 0;
    private int completedEpisodes = 0;
    private String currentStatus = "";
    private String currentTitle = "";
    private StringBuilder logBuffer = new StringBuilder();
    
    private IDownloadCallback callback;
    private NotificationManager notificationManager;

    public interface IDownloadCallback {
        void onStatusUpdate(String status);
        void onProgressUpdate(int progress, int max);
        void onPodcastProgressUpdate(int completed, int total);
        void onEpisodeProgressUpdate(int completed, int total);
        void onLogAppend(String message);
        void onDownloadComplete(boolean wasAborted);
    }

    public class LocalBinder extends Binder {
        public DownloadService getService() {
            return DownloadService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createDownloadNotificationChannel();
        
        podcastMetadataDao = AppDatabase.getInstance(this).podcastMetadataDao();
        episodeMetadataDao = AppDatabase.getInstance(this).episodeMetadataDao();
        podcastDownloader = new PodcastDownloader(this, abortRequested);
        audioFileDownloader = new AudioFileDownloader(this, abortRequested);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_ABORT.equals(intent.getAction())) {
            abortDownload();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        abortRequested.set(true);
        if (downloadExecutor != null) {
            downloadExecutor.shutdownNow();
        }
        dismissDownloadNotification();
    }

    public boolean isDownloading() { return isDownloading.get(); }

    public void setCallback(IDownloadCallback callback) {
        this.callback = callback;
        // Send current state to callback
        if (callback != null && isDownloading.get()) {
            callback.onStatusUpdate(currentStatus);
            callback.onProgressUpdate(currentProgress, totalPodcasts + totalEpisodes);
            callback.onPodcastProgressUpdate(completedPodcasts, totalPodcasts);
            callback.onEpisodeProgressUpdate(completedEpisodes, totalEpisodes);
            if (logBuffer.length() > 0) {
                callback.onLogAppend(logBuffer.toString());
            }
        }
    }

    public void startDownload(Long podcastId) {
        if (isDownloading.get()) {
            return;
        }
        
        isDownloading.set(true);
        abortRequested.set(false);
        currentProgress = 0;
        completedPodcasts = 0;
        completedEpisodes = 0;
        totalPodcasts = 0;
        totalEpisodes = 0;
        currentStatus = "Starting download...";
        currentTitle = "";
        logBuffer = new StringBuilder();
        
        updateStatus(currentStatus);
        showDownloadNotification("");
        
        downloadExecutor = Executors.newSingleThreadExecutor();
        if (podcastId == null) {
            downloadExecutor.execute(this::downloadAll);
        } else {
            downloadExecutor.execute(() -> downloadSinglePodcast(podcastId));
        }
    }

    public void abortDownload() {
        abortRequested.set(true);
        updateStatus("Aborting...");
        appendLog("Aborted");
    }

    public void downloadSinglePodcast(long podcastId) {
        PodcastMetadata podcast = podcastMetadataDao.getById(podcastId);
        downloadSpecifiedPodcasts(List.of(podcast));
    }

    private void downloadAll() {
        List<PodcastMetadata> podcasts = podcastMetadataDao.getActive();
        downloadSpecifiedPodcasts(podcasts);
    }

    private void downloadSpecifiedPodcasts(List<PodcastMetadata> podcasts) {
        totalPodcasts = podcasts.size();

        guesstimateInitialTotalDownloads(podcasts);
        updateProgress(0);
        updatePodcastProgress(0, totalPodcasts);

        downloadPodcasts(podcasts);

        List<EpisodeMetadata> episodes = episodeMetadataDao.getToDownload();
        totalEpisodes = episodes.size();

        updateProgress(completedPodcasts);
        updateEpisodeProgress(0, totalEpisodes);

        downloadAudioFiles(episodes);

        final boolean wasAborted = abortRequested.get();
        isDownloading.set(false);

        mainHandler.post(() -> {
            updateStatus(wasAborted ? "Download aborted" : "Download complete");
            dismissDownloadNotification();
            if (callback != null) {
                callback.onDownloadComplete(wasAborted);
            }
        });
    }

    private void downloadPodcasts(@NonNull List<PodcastMetadata> podcasts) {
        for (PodcastMetadata podcast : podcasts) {
            if (abortRequested.get()) {
                break;
            }

            try {
                currentTitle = podcast.title();
                updateStatus("Downloading podcast: " + currentTitle);
                showDownloadNotification(currentTitle);
                appendLog("Fetching: " + currentTitle + ": " + podcast.url());

                String downloadPodcastResult = podcastDownloader.downloadPodcast(podcast);
                appendLog(downloadPodcastResult);

                completedPodcasts++;
                updatePodcastProgress(completedPodcasts, totalPodcasts);
                updateProgress(completedPodcasts);
            } catch (Exception e) {
                Log.e(TAG, "Error downloading " + podcast.url(), e);
                appendLog("Error for " + podcast.url() + ": " + e.getMessage());
            }
        }
    }

    private void downloadAudioFiles(List<EpisodeMetadata> episodes) {
        startDownloadUpdates();
        for (EpisodeMetadata episode : episodes) {
            if (abortRequested.get()) {
                break;
            }

            currentTitle = episode.toString();
            showDownloadNotification(currentTitle);
            appendLog("Downloading: " + currentTitle);

            try {
                EpisodeMetadata updatedEpisode = audioFileDownloader.download(episode);

                if (updatedEpisode.contentLength() > 0 && !abortRequested.get()) {
                    episodeMetadataDao.update(updatedEpisode);
                    completedEpisodes++;
                    updateEpisodeProgress(completedEpisodes, totalEpisodes);
                    updateProgress(totalPodcasts + completedEpisodes);
                } else {
                    appendLog("Failed to download: " + currentTitle);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error downloading file " + episode.audioFileName(), e);
                appendLog("Error for " + currentTitle + ": " + e.getMessage());
            }
        }
        stopDownloadUpdates();
    }

    private void updateStatus(String status) {
        currentStatus = status;
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onStatusUpdate(status);
            }
        });
    }

    private void updateProgress(int progress) {
        currentProgress = progress;
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onProgressUpdate(progress, totalPodcasts + totalEpisodes);
            }
            // Update notification with current progress
            if (isDownloading.get() && !currentTitle.isEmpty()) {
                showDownloadNotification(currentTitle);
            }
        });
    }

    private void updatePodcastProgress(int completed, int total) {
        completedPodcasts = completed;
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onPodcastProgressUpdate(completed, total);
            }
        });
    }

    private void updateEpisodeProgress(int completed, int total) {
        completedEpisodes = completed;
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onEpisodeProgressUpdate(completed, total);
            }
        });
    }

    private void appendLog(String message) {
        logBuffer.append(message).append("\n");
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onLogAppend(logBuffer.toString());
            }
        });
    }

    private void showDownloadNotification(String title) {
        Intent intent = new Intent(this, DownloadActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);

        String notificationTitle = title.isEmpty() ? "Downloading..." : "Downloading " + title;
        int maxProgress = totalPodcasts + totalEpisodes;
        int progressPercent = maxProgress > 0 ? (currentProgress * 100 / maxProgress) : 0;

        Notification notification = new NotificationCompat.Builder(this, DOWNLOAD_CHANNEL_ID)
                .setContentTitle(notificationTitle)
                .setContentText("Download in progress...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setProgress(100, progressPercent, false)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();

        if (isDownloading.get()) {
            startForeground(DOWNLOAD_NOTIFICATION_ID, notification);
        } else {
            notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification);
        }
    }

    private void dismissDownloadNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    private void createDownloadNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "Download Progress",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Shows download progress for podcasts and episodes");
        notificationManager.createNotificationChannel(channel);
    }

    private void guesstimateInitialTotalDownloads(List<PodcastMetadata> podcasts) {
        for (PodcastMetadata podcast : podcasts) {
            int maxDownloads = podcast.getCalculatedMaxDownloads(this);
            totalEpisodes += maxDownloads == Integer.MAX_VALUE ? 10 : maxDownloads;
        }
    }

    private void startDownloadUpdates() {
        downloadRunnable = new Runnable() {
            @Override
            public void run() {
                String status = String.format(Locale.getDefault(), "Downloading %s\n%d kb",
                        currentTitle,
                        audioFileDownloader.getCurrentBytes() / 1024);
                updateStatus(status);
                mainHandler.postDelayed(this, 1000);
            }
        };
        mainHandler.post(downloadRunnable);
    }

    private void stopDownloadUpdates() {
        if (downloadRunnable != null) {
            mainHandler.removeCallbacks(downloadRunnable);
        }
    }
}
