package com.weinmann.ccr.itunes;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ITunesApi {

    @GET("search")
    Call<ITunesSearchResponse> searchPodcasts(
            @Query("term") String term,
            @Query("media") String media,
            @Query("limit") int limit
    );
}
