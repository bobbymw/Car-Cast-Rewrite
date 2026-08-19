package com.weinmann.ccr;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.weinmann.ccr.db.AppDatabase;
import com.weinmann.ccr.db.EpisodeMetadataDao;
import com.weinmann.ccr.records.EpisodeMetadata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;

@SuppressWarnings("ClassCanBeRecord")
public class EpisodeDeleter {
    private final static String TAG = "EpisodeDeleter";
    private final Context context;

    public EpisodeDeleter(Context context) {
        this.context = context;
    }

    public void deleteEpisodes(Predicate<EpisodeMetadata> filter) {
        AppDatabase.getExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            EpisodeMetadataDao dao = db.episodeMetadataDao();
            List<EpisodeMetadata> episodesToDelete = dao.getActive();
            if (episodesToDelete == null || episodesToDelete.isEmpty()) {
                return;
            }

            episodesToDelete.removeIf(e -> !filter.test(e));
            if (episodesToDelete.isEmpty()) {
                return;
            }

            db.runInTransaction(() -> {
                for (EpisodeMetadata episode : episodesToDelete) {
                    dao.deactivateById(episode.id());
                }
            });

            for (EpisodeMetadata episode : episodesToDelete) {
                if (episode.audioAbsolutePath() != null) {
                    try {
                        Path path = Paths.get(episode.audioAbsolutePath());
                        Files.delete(path);
                    } catch (Exception e) {
                        Log.e(TAG, "Could not delete audio file: " + episode.audioAbsolutePath(), e);
                    }
                }
            }

            int deletedCount = episodesToDelete.size();
            if (context instanceof Activity activity) {
                activity.runOnUiThread(() -> Toast.makeText(
                        activity,
                        deletedCount + " episodes deleted",
                        Toast.LENGTH_LONG
                ).show());
            }
        });
    }
}
