package com.weinmann.ccr.downloaders;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import com.weinmann.ccr.records.EpisodeMetadata;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioFileDownloader extends BaseDownloader {
    public static final String TAG = "AudioFileDownloader";
    private final Context context;

    public AudioFileDownloader(Context context, AtomicBoolean abortRequested) {
        super(abortRequested);
        this.context = context;
    }

    public EpisodeMetadata download(@NonNull EpisodeMetadata originalEpisode) {
        File audioFile;

        try {
            audioFile = generateAudioFile(originalEpisode);
        } catch (IOException e) {
            Log.e(TAG, "Error generating audio file", e);
            return originalEpisode;
        }

        try (OutputStream out  = new FileOutputStream(audioFile)) {
            fetchBinaryUrl(originalEpisode.enclosureUrl(), out);
            if (abortRequested.get()) return originalEpisode;

            if (currentBytes <= 0) {
                Log.e(TAG, "Downloaded 0 bytes — aborting");
                return originalEpisode;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error opening output stream", e);
            return originalEpisode;
        }

        try {
            int duration = getAudioDuration(audioFile.getAbsolutePath());
            return EpisodeMetadata.createCopyForDownload(
                    originalEpisode,
                    audioFile.getAbsolutePath(),
                    currentBytes,
                    duration);
        } catch (IOException e) {
            Log.e(TAG, "Error opening output stream", e);
        }

        return originalEpisode;
    }

    private static int getAudioDuration(String audioFileAbsolutePath) throws IOException {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(audioFileAbsolutePath);
            String dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return Integer.parseInt(Objects.requireNonNull(dur)); // duration in ms
        }
    }

    @NonNull
    private File generateAudioFile(@NonNull EpisodeMetadata episode) throws IOException {
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String extension = mimeTypeMap.getExtensionFromMimeType(episode.mimeType());

        String fileName = String.format(Locale.getDefault(), "%d.%s",
                episode.id(),
                extension);

        File podcastsDir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS);
        if (podcastsDir == null) {
            throw new IllegalStateException("External storage not available");
        }

        if (!podcastsDir.exists() && !podcastsDir.mkdirs()) {
            throw new IOException("Failed to create podcasts directory");
        }

        return new File(podcastsDir, fileName);
    }
}
