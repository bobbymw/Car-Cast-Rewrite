package com.weinmann.ccr.itunes;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ITunesSearchResponse {

    @SerializedName("resultCount")
    public int resultCount;

    @SerializedName("results")
    public List<ITunesPodcastResult> results;

    public static class ITunesPodcastResult {
        @SerializedName("collectionName")
        public String title;

        @SerializedName("podcastUrl")
        public String podcastUrl;
    }
}
