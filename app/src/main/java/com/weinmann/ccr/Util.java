package com.weinmann.ccr;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Locale;

public class Util {

    @NonNull
    public static String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.US,"%02d:%02d", minutes, seconds);
    }

    @NonNull
    public static String normalizeUrl(String url) {
        Uri uri = Uri.parse(url).normalizeScheme();
        return uri.buildUpon()
                .clearQuery()
                .fragment(null)
                .build()
                .toString();
    }
}
