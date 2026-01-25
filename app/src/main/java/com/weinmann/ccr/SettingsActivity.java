package com.weinmann.ccr;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    private EditText maxDownloadsEdit;
    private Spinner playbackSpeedSpinner;
    private EditText rewindSecondsEdit;
    private EditText forwardSecondsEdit;
    private CheckBox deleteAfterListeningCheckbox;
    private SpinnerItemAdapter<Float> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        maxDownloadsEdit = findViewById(R.id.edit_max_downloads);
        playbackSpeedSpinner = findViewById(R.id.spinner_playback_speed);
        rewindSecondsEdit = findViewById(R.id.edit_rewind_seconds);
        forwardSecondsEdit = findViewById(R.id.edit_forward_seconds);
        deleteAfterListeningCheckbox = findViewById(R.id.checkbox_delete_after_listening);
        Button saveButton = findViewById(R.id.button_save_settings);


        List<SpinnerItem<Float>> speeds =
                List.of(
                        new SpinnerItem<>("0.5", 0.5F),
                        new SpinnerItem<>("0.75", 0.75F),
                        new SpinnerItem<>("1.0", 1F),
                        new SpinnerItem<>("1.25", 1.25F),
                        new SpinnerItem<>("1.5", 1.5F),
                        new SpinnerItem<>("1.75", 1.75F),
                        new SpinnerItem<>("2.0", 2F));
        adapter = new SpinnerItemAdapter<>(this, speeds);
        playbackSpeedSpinner.setAdapter(adapter);

        loadSettings();

        saveButton.setOnClickListener(v -> saveSettings());
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isValid(EditText e) {
        return e.getText() != null && !e.getText().toString().trim().isEmpty();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(CcrApplication.PREFS_NAME, MODE_PRIVATE);

        maxDownloadsEdit.setText(String.valueOf(prefs.getInt(CcrApplication.KEY_MAX_DOWNLOADS, CcrApplication.DEFAULT_MAX_DOWNLOADS_PER_PODCAST)));
        rewindSecondsEdit.setText(String.valueOf(prefs.getInt(CcrApplication.KEY_REWIND_SECONDS, CcrApplication.DEFAULT_REWIND_SECONDS)));
        forwardSecondsEdit.setText(String.valueOf(prefs.getInt(CcrApplication.KEY_FORWARD_SECONDS, CcrApplication.DEFAULT_FORWARD_SECONDS)));
        deleteAfterListeningCheckbox.setChecked(prefs.getBoolean(CcrApplication.KEY_DELETE_AFTER_LISTENING, false));

        float playbackSpeed = prefs.getFloat(CcrApplication.KEY_PLAYBACK_SPEED, CcrApplication.DEFAULT_PLAYBACK_SPEED);
        playbackSpeedSpinner.setSelection(adapter.getIndexByValue(playbackSpeed));
    }

    @SuppressWarnings("unchecked")
    private void saveSettings() {
        if (!isValid(maxDownloadsEdit) ||
                !isValid(rewindSecondsEdit) ||
                !isValid(forwardSecondsEdit)) {

            Toast.makeText(this, R.string.invalid_numbers, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int maxDownloads = Integer.parseInt(maxDownloadsEdit.getText().toString());
            int rewindSecs = Integer.parseInt(rewindSecondsEdit.getText().toString());
            int forwardSecs = Integer.parseInt(forwardSecondsEdit.getText().toString());
            SpinnerItem<Float> playbackSpeedItem = (SpinnerItem<Float>)playbackSpeedSpinner.getSelectedItem();
            float playbackSpeed = playbackSpeedItem.value();

            SharedPreferences prefs =
                    getSharedPreferences(CcrApplication.PREFS_NAME, MODE_PRIVATE);

            prefs.edit()
                    .putInt(CcrApplication.KEY_MAX_DOWNLOADS, maxDownloads)
                    .putInt(CcrApplication.KEY_REWIND_SECONDS, rewindSecs)
                    .putInt(CcrApplication.KEY_FORWARD_SECONDS, forwardSecs)
                    .putFloat(CcrApplication.KEY_PLAYBACK_SPEED, playbackSpeed)
                    .putBoolean(CcrApplication.KEY_DELETE_AFTER_LISTENING, deleteAfterListeningCheckbox.isChecked())
                    .apply();

            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
            finish();

        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.invalid_numbers, Toast.LENGTH_SHORT).show();
        }
    }
}
