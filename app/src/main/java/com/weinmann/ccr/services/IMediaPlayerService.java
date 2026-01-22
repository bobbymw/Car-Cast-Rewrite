package com.weinmann.ccr.services;

import com.weinmann.ccr.records.EpisodeMetadata;

public interface IMediaPlayerService {

    void playPause(boolean shouldPlay);

    void setEpisodeIndex(int index, boolean shouldPlay);

    void seekBackward();

    void seekForward();

    void seekTo(int position);

    boolean isPlaying();

    int getCurrentPosition();

    int getDuration();

    EpisodeMetadata getCurrentEpisode();

    int getCurrentEpisodeIndex();

    int getEpisodeCount();
}
