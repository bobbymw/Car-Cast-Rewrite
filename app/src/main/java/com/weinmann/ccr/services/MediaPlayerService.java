package com.weinmann.ccr.services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.lifecycle.Observer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.weinmann.ccr.CcrApplication;
import com.weinmann.ccr.CurrentItemList;
import com.weinmann.ccr.EpisodeDeleter;
import com.weinmann.ccr.db.AppDatabase;
import com.weinmann.ccr.db.EpisodeMetadataDao;
import com.weinmann.ccr.records.EpisodeMetadata;

import java.io.File;
import java.util.List;

public class MediaPlayerService extends Service implements IMediaPlayerService {
    private static final String TAG = "MediaPlayerService";

    private final IBinder binder = new LocalBinder();
    private final MediaNotifier mediaNotifier = new MediaNotifier(this);

    private ExoPlayer player;

    private MediaSessionCompat mediaSession;

    private EpisodeMetadataDao episodeMetadataDao;

    private Observer<List<EpisodeMetadata>> activeEpisodesObserver;
    private final CurrentItemList<EpisodeMetadata> episodes = new CurrentItemList<>();

    public class LocalBinder extends Binder {
        public MediaPlayerService getService() {
            return MediaPlayerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        initializeMediaSession();
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        episodeMetadataDao = db.episodeMetadataDao();

        // Create observer only once
        activeEpisodesObserver = list -> {
            episodes.clear();
            episodes.addAll(list);
            onLoadEpisodes();
            Log.d("MediaPlayerService",
                    "Active episodes updated: " + list.size());
        };

        // Listen for ANY DB change affecting isActive
        episodeMetadataDao.getObservable().observeForever(activeEpisodesObserver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("ACTION_PLAY_PAUSE".equals(action)) {
                playPause(!isPlaying());
            } else if ("ACTION_PREVIOUS".equals(action)) {
                setEpisodeIndex(episodes.getCurrentIndex() - 1, isPlaying());
            } else if ("ACTION_NEXT".equals(action)) {
                setEpisodeIndex(episodes.getCurrentIndex() + 1, isPlaying());
            } else if ("ACTION_STOP".equals(action)) {
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        startForeground(MediaNotifier.NOTIFICATION_ID, mediaNotifier.createNotification(isPlaying(), mediaSession.getSessionToken()));
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void playPause(boolean shouldPlay) {
        if (shouldPlay) {
            play();
        } else {
            pause();
        }
    }

    @Override
    public void setEpisodeIndex(int index, boolean shouldPlay) {
        if (index == getCurrentEpisodeIndex()) {
            playPause(shouldPlay);
            return;
        }

        killMediaPlayer();
        assert (player == null);

        episodes.setCurrentIndex(index);

        if (getCurrentEpisode() != null) {
            SharedPreferences prefs = getSharedPreferences(CcrApplication.PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putLong(CcrApplication.KEY_LAST_EPISODE_ID, getCurrentEpisode().id()).apply();
            updateMediaSessionMetadata();

            if (shouldPlay) {
                if (getCurrentEpisode().currentPos() >= getCurrentEpisode().duration()) {
                    updateAndSaveCurrentEpisodePosition(0); // because we specifically want to play this episode again
                }
            }

            playPause(shouldPlay);
        }
    }

    @Override
    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    @Override
    public int getCurrentPosition() {
        if (player != null) {
            long pos = player.getCurrentPosition();
            return (pos > 0) ? (int) pos : 0;
        }

        if (getCurrentEpisode() != null) {
            return getCurrentEpisode().currentPos();
        }

        return 0;
    }

    @Override
    public int getDuration() {
        if (player != null) {
            long duration = player.getDuration();
            return (duration > 0) ? (int) duration : 0;
        }

        if (getCurrentEpisode() != null) return getCurrentEpisode().duration();
        return 0;
    }

    @Override
    public void seekBackward() {
        SharedPreferences prefs = getSharedPreferences(CcrApplication.PREFS_NAME, MODE_PRIVATE);
        int seconds = prefs.getInt(CcrApplication.KEY_REWIND_SECONDS, 30);

        int newPosition = getCurrentPosition()  - (seconds * 1000);
        seekTo(newPosition);
    }

    @Override
    public void seekForward() {
        SharedPreferences prefs = getSharedPreferences(CcrApplication.PREFS_NAME, MODE_PRIVATE);
        int seconds = prefs.getInt(CcrApplication.KEY_FORWARD_SECONDS, 30);

        int newPosition = getCurrentPosition() + (seconds * 1000);
        seekTo(newPosition);
    }

    @Override
    public void seekTo(int position) {
        boolean wasPlaying = isPlaying();
        if (position < 0) position = 0;
        if (position >= getDuration()) position = getDuration() - 1;

        if (player != null) {
            player.seekTo(position);
            updatePlaybackState();
            mediaNotifier.updateNotification(isPlaying(), mediaSession.getSessionToken());
        }

        updateAndSaveCurrentEpisodePosition(position);
        playPause(wasPlaying);
    }

    @Override
    public EpisodeMetadata getCurrentEpisode() {
        return episodes.getCurrentItem();
    }

    @Override
    public int getCurrentEpisodeIndex() {
        return episodes.getCurrentIndex();
    }

    @Override
    public int getEpisodeCount() {
        return episodes.size();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (activeEpisodesObserver != null) {
            episodeMetadataDao.getObservable().removeObserver(activeEpisodesObserver);
        }

        killMediaPlayer();

        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
    }

    private void play() {
        if (isPlaying() || getCurrentEpisode() == null) return;
        if (player == null) {
            initializePlayer();
        }
        float playbackSpeed = getPlaybackSpeed();
        player.setPlaybackSpeed(playbackSpeed);
        player.play();
    }

    private void pause() {
        if (isPlaying() && getCurrentEpisode() != null) {
            player.pause();
        }
    }

    private void setEpisodeById(long id, boolean shouldPlay) {
        int index = episodes.indexOf(e -> e.id() == id);
        if (index < 0) {
            killMediaPlayer();
            return;
        }

        setEpisodeIndex(index, shouldPlay);
    }

    private void onLoadEpisodes() {

        boolean wasPlaying = isPlaying();
        SharedPreferences prefs = getSharedPreferences(CcrApplication.PREFS_NAME, MODE_PRIVATE);
        long lastEpisodeId = prefs.getLong(CcrApplication.KEY_LAST_EPISODE_ID, 0L);

        episodes.removeIf(episode ->
                (episode.audioAbsolutePath() == null) ||
                !new File(episode.audioAbsolutePath()).exists());

        setEpisodeById(lastEpisodeId, wasPlaying);
    }

    private void updateMediaSessionMetadata() {
        if (mediaSession == null || getCurrentEpisode() == null) return;

        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, episodes.getCurrentIndex() + 1)
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, episodes.size())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, getCurrentEpisode().title())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, getCurrentEpisode().podcastName())
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, getCurrentEpisode().description())
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());

        mediaSession.setMetadata(metadataBuilder.build());
    }

    private void updatePlaybackState() {
        if (mediaSession == null) return;
        if (episodes.getCurrentItem() == null) return;

        int state = isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;

        float playbackSpeed = getPlaybackSpeed();

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_REWIND |
                                PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                PlaybackStateCompat.ACTION_FAST_FORWARD |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_STOP |
                                PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(state, getCurrentPosition(), playbackSpeed);

        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private float getPlaybackSpeed() {
        SharedPreferences prefs = getSharedPreferences(CcrApplication.PREFS_NAME, MODE_PRIVATE);
        return prefs.getFloat(CcrApplication.KEY_PLAYBACK_SPEED, CcrApplication.DEFAULT_PLAYBACK_SPEED);
    }

    private synchronized void initializePlayer() {
        if (player != null) {
            return;
        }

        player = new ExoPlayer.Builder(this).build();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    updateMediaSessionMetadata();
                } else if (state == Player.STATE_ENDED) {
                    setEpisodeIndex(episodes.getCurrentIndex() + 1, true);
                    handleDeleteAfterListening();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlaybackState();
                mediaNotifier.updateNotification(isPlaying, mediaSession.getSessionToken());
            }
        });

        Log.d(TAG, "Loading audio from: " + getCurrentEpisode().audioAbsolutePath());

        MediaItem mediaItem =
                new MediaItem.Builder()
                        .setUri(getCurrentEpisode().audioAbsolutePath())
                        .build();

        player.setMediaItem(mediaItem);
        player.prepare();
        player.seekTo(getCurrentEpisode().currentPos());
    }

    private void handleDeleteAfterListening() {
        SharedPreferences prefs = getSharedPreferences(CcrApplication.PREFS_NAME, MODE_PRIVATE);
        boolean shouldDeleteAfterListening = prefs.getBoolean(CcrApplication.KEY_DELETE_AFTER_LISTENING, false);
        
        if (shouldDeleteAfterListening) {
            EpisodeDeleter episodeDeleter = new EpisodeDeleter(this);
            episodeDeleter.deleteEpisodes(EpisodeMetadata::isListenedTo);
        }
    }

    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(this, TAG);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { play(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToPrevious() { setEpisodeIndex(episodes.getCurrentIndex() - 1, isPlaying()); }
            @Override public void onSkipToNext() { setEpisodeIndex(episodes.getCurrentIndex() + 1, isPlaying()); }
            @Override public void onSeekTo(long pos) { seekTo((int) pos); }
        });

        mediaSession.setActive(true);
    }

    private void updateAndSaveCurrentEpisodePosition(int position) {
        if (getCurrentEpisode() == null) {
            return;
        }
        EpisodeMetadata updatedEpisode = EpisodeMetadata.createCopyForUpdate(
                getCurrentEpisode(),
                position,
                true);

        episodes.replaceCurrentItem(updatedEpisode);

        AppDatabase.getExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            db.episodeMetadataDao().update(updatedEpisode);
        });
    }

    private void killMediaPlayer() {
        updateAndSaveCurrentEpisodePosition(getCurrentPosition());

        if (player != null) {
            if (player.isPlaying()) {
                player.pause();
            }
            player.stop();

            player.release();
            player = null;
        }
    }
}
