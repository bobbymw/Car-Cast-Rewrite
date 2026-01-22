package com.weinmann.ccr.db;

import androidx.lifecycle.LiveData;
import androidx.room.*;

import com.weinmann.ccr.records.EpisodeMetadata;

import java.util.List;

@Dao
public interface EpisodeMetadataDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(EpisodeMetadata entity);

    @Query("SELECT * FROM episode_metadata WHERE enclosureUrl = :enclosureUrl")
    EpisodeMetadata getByUrl(String enclosureUrl);

    @Query("UPDATE episode_metadata SET useForHistory = 0")
    void clearAllHistory();

    @Query("SELECT * FROM episode_metadata WHERE isActive = 1 ORDER BY audioAbsolutePath ASC")
    LiveData<List<EpisodeMetadata>> getObservable();

    @Query("SELECT * FROM episode_metadata WHERE isActive = 1 ORDER BY audioAbsolutePath ASC")
    List<EpisodeMetadata> getActive();

    @Query("SELECT * FROM episode_metadata WHERE isActive = 1 AND contentLength = 0")
    List<EpisodeMetadata> getToDownload();

    @Update
    void update(EpisodeMetadata entity);
}
