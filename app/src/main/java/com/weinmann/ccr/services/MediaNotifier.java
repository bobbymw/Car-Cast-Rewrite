package com.weinmann.ccr.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.weinmann.ccr.MainActivity;
import com.weinmann.ccr.R;

import org.jetbrains.annotations.Contract;


public class MediaNotifier {
    public static final String CHANNEL_ID = "media_player_channel";
    public static final int NOTIFICATION_ID = 1;
    private final Context context;
    private boolean isChannelCreated = false;

    @Contract(pure = true)
    public MediaNotifier(Context context) {
        this.context = context;
    }

    @NonNull
    public Notification createNotification(boolean isPlaying, MediaSessionCompat.Token mediaSessionToken) {
        if (!isChannelCreated) {
            createNotificationChannel();
        }
        PendingIntent notificationPendingIntent = createActivityPendingIntent();
        PendingIntent playPausePendingIntent = createPendingIntent("ACTION_PLAY_PAUSE", 0);
        PendingIntent stopPendingIntent = createPendingIntent("ACTION_STOP", 1);
        PendingIntent previousPendingIntent = createPendingIntent("ACTION_PREVIOUS", 3);
        PendingIntent nextPendingIntent = createPendingIntent("ACTION_NEXT", 2);

        int playPauseIcon = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        String playPauseText = isPlaying ? context.getString(R.string.pause) : context.getString(R.string.play);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(notificationPendingIntent)
                .addAction(android.R.drawable.ic_media_previous, context.getString(R.string.previous), previousPendingIntent)
                .addAction(playPauseIcon, playPauseText, playPausePendingIntent)
                .addAction(android.R.drawable.ic_media_next, context.getString(R.string.next), nextPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.exit), stopPendingIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSessionToken)
                        .setShowActionsInCompactView(0, 1, 2))
                .setOnlyAlertOnce(true);

        return builder.build();
    }

    public void updateNotification(boolean isPlaying, MediaSessionCompat.Token token) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, createNotification(isPlaying, token));
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.media_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(context.getString(R.string.media_channel_description));
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
        isChannelCreated = true;
    }

    private PendingIntent createActivityPendingIntent() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, 0, intent, flags);
    }

    private PendingIntent createPendingIntent(String action, int requestCode) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        Intent intent = new Intent(context, MediaPlayerService.class);
        if (action != null) {
            intent.setAction(action);
        }
        return PendingIntent.getService(context, requestCode, intent, flags);
    }
}
