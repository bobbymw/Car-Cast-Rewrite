package com.weinmann.ccr;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class NotificationPermissionChecker {
    public static boolean checkNotificationPermission(AppCompatActivity activity) {
        // POST_NOTIFICATIONS permission is required for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                // Check if we should show rationale
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                        Manifest.permission.POST_NOTIFICATIONS)) {
                    // Show explanation dialog
                    showPermissionRationale(activity);
                } else {
                    // Request permission directly
                    requestNotificationPermission(activity);
                }
                return false;
            }
        }
        return true;
    }

    private static void showPermissionRationale(AppCompatActivity activity) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.notification_permission_title)
                .setMessage(R.string.notification_permission_message)
                .setPositiveButton(R.string.ok, (dialog, which) -> requestNotificationPermission(activity))
                .setNegativeButton(R.string.cancel, (dialog, which) -> Toast.makeText(activity, "Notification permission is required for media controls and download",
                        Toast.LENGTH_LONG).show())
                .setCancelable(false)
                .show();
    }

    private static void requestNotificationPermission(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    MainActivity.NOTIFICATION_PERMISSION_REQUEST_CODE);
        }
    }
}
