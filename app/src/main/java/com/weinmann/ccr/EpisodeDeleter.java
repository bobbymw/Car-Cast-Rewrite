package com.weinmann.ccr;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import com.weinmann.ccr.db.AppDatabase;
import com.weinmann.ccr.db.EpisodeMetadataDao;
import com.weinmann.ccr.records.EpisodeMetadata;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;

@SuppressWarnings("ClassCanBeRecord")
public class EpisodeDeleter {
    private final static String TAG = "EpisodeDeleter";
    private final Activity activity;

    public EpisodeDeleter(Activity activity) {
        this.activity = activity;
    }

    public void deleteEpisodes(Predicate<EpisodeMetadata> filter) {
        AppDatabase.getExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(activity);
            EpisodeMetadataDao dao = db.episodeMetadataDao();
            List<EpisodeMetadata> episodesToDelete = dao.getActive();

            if (episodesToDelete != null) {
                episodesToDelete.removeIf(e -> !filter.test(e));

                for (EpisodeMetadata episode : episodesToDelete) {

                    if (episode.audioAbsolutePath() != null) {
                        try {
                            File file = new File(episode.audioAbsolutePath());
                            if (file.exists()) {
                                file.delete();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Could not delete audio file: " + episode.audioAbsolutePath(), e);
                        }
                    }

                    EpisodeMetadata updatedEpisode = EpisodeMetadata.createCopyForUpdate(
                            episode,
                            0,
                            false);
                    dao.update(updatedEpisode);
                }

                activity.runOnUiThread(() -> Toast.makeText(
                        activity,
                        episodesToDelete.size() + " episodes deleted",
                        Toast.LENGTH_LONG
                ).show());
            }
        });
    }
}
