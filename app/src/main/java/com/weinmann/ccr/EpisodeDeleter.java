package com.weinmann.ccr;

import android.app.Activity;
import android.net.Uri;
import android.widget.Toast;

import com.weinmann.ccr.db.AppDatabase;
import com.weinmann.ccr.db.EpisodeMetadataDao;
import com.weinmann.ccr.records.EpisodeMetadata;

import java.util.List;
import java.util.function.Predicate;

@SuppressWarnings("ClassCanBeRecord")
public class EpisodeDeleter {
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
                    Uri uri = Uri.parse(episode.uriString());
                    int rowsDeleted = activity.getContentResolver().delete(uri, null, null);
                    assert rowsDeleted > 0;

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
