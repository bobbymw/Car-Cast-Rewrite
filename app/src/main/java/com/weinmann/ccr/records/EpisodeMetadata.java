package com.weinmann.ccr.records;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import org.jetbrains.annotations.Contract;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Entity(
        tableName = "episode_metadata",
        indices = {
                @Index(value = {"enclosureUrl"}, unique = true)
        }
)

public record EpisodeMetadata(
        @PrimaryKey(autoGenerate = true)
        long id,

        long podcastId,

        @NonNull
        String podcastName,

        @NonNull
        String title,

        @NonNull
        String description,

        @NonNull
        String enclosureUrl,

        /* UTC epoch millis */
        long pubDateMillis,

        @Nullable
        String audioAbsolutePath,

        @Nullable
        String mimeType,

        /* Bytes, if known */
        long contentLength,

        int currentPos,

        int duration,
        boolean isActive,
        boolean isListenedTo,
        boolean useForHistory)
{
    private static final int LISTENED_TO_IF_LESS_THAN_PERCENT_LEFT = 5;
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    @NonNull
    @Contract("_, _, _, _ -> new")
    public static EpisodeMetadata createCopyForDownload(EpisodeMetadata original,
                                                        String audioAbsolutePath,
                                                        long contentLength,
                                                        int duration) {
return new EpisodeMetadata(
                original.id(),
                original.podcastId(),
                original.podcastName(),
                original.title(),
                original.description(),
                original.enclosureUrl(),
                original.pubDateMillis(),
                audioAbsolutePath,
                original.mimeType(),
                contentLength,
                0,
                duration,
                true,
                false,
                true
        );
    }

    @Contract("_, _, _ -> new")
    @NonNull

    public static EpisodeMetadata createCopyForUpdate(EpisodeMetadata original,
                                                      int currentPos,
                                                      boolean isActive) {
        return new EpisodeMetadata(
                original.id(),
                original.podcastId(),
                original.podcastName(),
                original.title(),
                original.description(),
                original.enclosureUrl(),
                original.pubDateMillis(),
                original.audioAbsolutePath(),
                original.mimeType(),
                original.contentLength(),
                currentPos,
                original.duration(),
                isActive,
                original.shouldSetListenedTo(currentPos),
                true
        );
    }

    public String getPubDateString() {
        Instant instant = Instant.ofEpochMilli(pubDateMillis);
        return dateTimeFormatter.format(instant);
    }

    private boolean shouldSetListenedTo(int newPosition) {
        if (isListenedTo) return true;

        if (duration == 0) return false;

        int millisecondsLeft = duration - newPosition;
        return millisecondsLeft * 100 / duration < LISTENED_TO_IF_LESS_THAN_PERCENT_LEFT;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        EpisodeMetadata other = (EpisodeMetadata) obj;
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
        return podcastName + " | " + title;
    }
}
