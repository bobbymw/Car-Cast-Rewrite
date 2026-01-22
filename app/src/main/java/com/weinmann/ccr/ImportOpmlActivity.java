package com.weinmann.ccr;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.weinmann.ccr.db.AppDatabase;
import com.weinmann.ccr.records.PodcastMetadata;

import java.io.InputStream;
import java.util.List;

public class ImportOpmlActivity extends AppCompatActivity {
    private static final String TAG = "ImportOpmlActivity";

    private ActivityResultLauncher<String[]> openOpmlLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_opml);

        Button btnImport = findViewById(R.id.btnImport);
        btnImport.setOnClickListener(v -> launchFilePicker());

        openOpmlLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.OpenDocument(),
                        this::onOpmlSelected
                );
    }

    private void launchFilePicker() {
        openOpmlLauncher.launch(new String[]{
                "*/*"
        });
    }

    private void onOpmlSelected(Uri uri) {
        if (uri == null) {
            return;
        }

        getContentResolver().takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );


        AppDatabase.getExecutor().execute(() -> {

            try (InputStream is = getContentResolver().openInputStream(uri)) {

                List<PodcastMetadata> podcasts = OpmlImporter.read(is);
                AppDatabase db = AppDatabase.getInstance(this);

                CheckBox checkboxDeleteExisting = findViewById(R.id.checkboxDeleteExisting);
                boolean shouldDeleteFirst = checkboxDeleteExisting.isChecked();
                if (shouldDeleteFirst) {
                    db.podcastMetadataDao().deleteAll();
                }

                List<PodcastMetadata> existing = db.podcastMetadataDao().getAll();

                for (PodcastMetadata podcast : podcasts) {
                    boolean exists = existing.stream().anyMatch(f -> f.url().equals(podcast.url()));
                    if (!exists) {
                        db.podcastMetadataDao().insert(podcast);
                    }
                }

                runOnUiThread(() -> Toast.makeText(
                        ImportOpmlActivity.this,
                        "Imported " + podcasts.size() + " podcasts",
                        Toast.LENGTH_LONG
                ).show());

            } catch (Exception e) {
                Log.e(TAG, "Failed to import OPML", e);

                runOnUiThread(() ->
                        Toast.makeText(
                                ImportOpmlActivity.this,
                                "Failed to import OPML",
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        });
    }
}
