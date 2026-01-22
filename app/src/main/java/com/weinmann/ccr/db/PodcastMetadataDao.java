package com.weinmann.ccr.db;

import androidx.room.*;

import com.weinmann.ccr.records.PodcastMetadata;

import java.util.List;

@Dao
public interface PodcastMetadataDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(PodcastMetadata entity);

    @Query("DELETE FROM podcast_metadata")
    void deleteAll();

    @Query("SELECT * FROM podcast_metadata WHERE isActive = 1")
    List<PodcastMetadata> getActive();

    @Query("SELECT * FROM podcast_metadata")
    List<PodcastMetadata> getAll();

    @Query("SELECT * FROM podcast_metadata WHERE id = :id LIMIT 1")
    PodcastMetadata getById(long id);

    @Update
    void update(PodcastMetadata entity);

    @Delete
    void delete(PodcastMetadata podcast);
}
