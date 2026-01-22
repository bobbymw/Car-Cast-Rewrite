package com.weinmann.ccr;

import android.app.Application;
import android.os.Environment;

import com.weinmann.ccr.records.PodcastMetadata;

import java.util.List;

public class CcrApplication extends Application {

    public static final String PREFS_NAME = "app_settings";
    public static final String KEY_LAST_EPISODE_ID = "last_episode_id";
    public static final String KEY_MAX_DOWNLOADS = "max_downloads";
    public static final String KEY_PLAYBACK_SPEED = "playback_speed";
    public static final String KEY_REWIND_SECONDS = "rewind_seconds";
    public static final String KEY_FORWARD_SECONDS = "forward_seconds";
    public static final int DEFAULT_MAX_DOWNLOADS_PER_PODCAST = 10;
    public static final int DEFAULT_REWIND_SECONDS = 30;
    public static final int DEFAULT_FORWARD_SECONDS = 30;
    public static final float DEFAULT_PLAYBACK_SPEED = 1.0f;
    public static final String EPISODES_DIR_PATH = Environment.DIRECTORY_PODCASTS + "/ccr";

    public static final List<PodcastMetadata> DefaultPodcasts = List.of(
        new PodcastMetadata(0L, "The Clark Howard Podcast", "https://feeds.megaphone.fm/clarkhoward", 2, true)
    );
}
