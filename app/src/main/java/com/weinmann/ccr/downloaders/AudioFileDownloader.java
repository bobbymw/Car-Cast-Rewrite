package com.weinmann.ccr.downloaders;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import com.weinmann.ccr.CcrApplication;
import com.weinmann.ccr.records.EpisodeMetadata;

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

    private int getAudioDuration(Uri uri) throws IOException {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(context, uri);
            String dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return Integer.parseInt(Objects.requireNonNull(dur)); // duration in ms
        }
    }

    public EpisodeMetadata download(@NonNull EpisodeMetadata originalEpisode) {

        String audioFileName = generateAudioFileName(originalEpisode);
        ContentValues values = getContentValues(originalEpisode, audioFileName);

        ContentResolver resolver = context.getContentResolver();

        Uri uri = resolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                values
        );

        if (uri == null) {
            throw new IllegalStateException("Failed to create MediaStore entry");
        }

        try (OutputStream out = resolver.openOutputStream(uri)) {
            fetchBinaryUrl(originalEpisode.enclosureUrl(), out);
            if (abortRequested.get()) return originalEpisode;

            if (currentBytes <= 0) {
                Log.e(TAG, "Downloaded 0 bytes — aborting");
                return originalEpisode;
            }

            // Mark the file as complete
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Audio.Media.IS_PENDING, 0);
                resolver.update(uri, done, null, null);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error opening output stream", e);
            return originalEpisode;
        }

        try {
            int duration = getAudioDuration(uri);
            return EpisodeMetadata.createCopyForDownload(
                    originalEpisode,
                    audioFileName,
                    uri.toString(),
                    currentBytes,
                    duration);
        } catch (IOException e) {
            Log.e(TAG, "Error opening output stream", e);
        }

        return originalEpisode;
    }

    @NonNull
    private static ContentValues getContentValues(@NonNull EpisodeMetadata originalEpisode, String audioFileName) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, audioFileName);
        values.put(MediaStore.Audio.Media.MIME_TYPE, originalEpisode.mimeType());
        values.put(
                MediaStore.Audio.Media.RELATIVE_PATH,
                CcrApplication.EPISODES_DIR_PATH
        );


        // Android 10+ safe writing flag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        }
        return values;
    }

    @NonNull
    private static String generateAudioFileName(@NonNull EpisodeMetadata episode) {
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String extension = mimeTypeMap.getExtensionFromMimeType(episode.mimeType());

        return String.format(Locale.getDefault(), "%d.%s",
                episode.id(),
                extension);
    }
}
