package com.weinmann.ccr.records;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.room.*;

import com.weinmann.ccr.CcrApplication;

import org.jetbrains.annotations.Contract;

@Entity(
        tableName = "podcast_metadata",
        indices = {
                @Index(value = {"url"}, unique = true)
        }
)

public record PodcastMetadata (
        @PrimaryKey(autoGenerate = true)
        long id,

        @NonNull
        String title,

        @NonNull
        String url,
        int maxDownloads,
        boolean isActive) {
    public static final int USE_GLOBAL_DEFAULT_MAX_DOWNLOADS = -1;
    public static final int UNLIMITED_MAX_DOWNLOADS = -2;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PodcastMetadata other = (PodcastMetadata) obj;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @NonNull
    @Contract(pure = true)
    @Override
    public String toString() {
        return isActive ? title : title + " (disabled)";
    }

    public int getCalculatedMaxDownloads(Context context) {
        return switch (maxDownloads) {
            case USE_GLOBAL_DEFAULT_MAX_DOWNLOADS -> {
                SharedPreferences prefs = context.getSharedPreferences(CcrApplication.PREFS_NAME, MODE_PRIVATE);
                yield prefs.getInt(CcrApplication.KEY_MAX_DOWNLOADS, CcrApplication.DEFAULT_MAX_DOWNLOADS_PER_PODCAST);
            }
            case UNLIMITED_MAX_DOWNLOADS -> Integer.MAX_VALUE;
            default -> maxDownloads;
        };


    }
}
