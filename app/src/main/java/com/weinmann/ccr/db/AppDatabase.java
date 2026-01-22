package com.weinmann.ccr.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.weinmann.ccr.records.PodcastMetadata;
import com.weinmann.ccr.records.EpisodeMetadata;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {PodcastMetadata.class, EpisodeMetadata.class },
        version = 1
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    private static final ExecutorService databaseWriteExecutor =
            Executors.newSingleThreadExecutor();

    public abstract PodcastMetadataDao podcastMetadataDao();
    public abstract EpisodeMetadataDao episodeMetadataDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "Ccr_database"
                            )
                            // Explicit migration list (even if empty)
                            .addMigrations()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public static ExecutorService getExecutor() {
        return databaseWriteExecutor;
    }
}
