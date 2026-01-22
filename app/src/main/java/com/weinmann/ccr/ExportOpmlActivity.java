package com.weinmann.ccr;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.weinmann.ccr.db.AppDatabase;
import com.weinmann.ccr.records.PodcastMetadata;

import java.util.List;

public class ExportOpmlActivity extends AppCompatActivity {
    private static final String TAG = "ExportOpmlActivity";
    private ActivityResultLauncher<Intent> createDocumentLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export_opml);

        createDocumentLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                                Uri uri = result.getData().getData();
                                if (uri != null) {
                                    export(uri);
                                }
                            }
                        });

        Button btnExport = findViewById(R.id.btnExport);
        btnExport.setOnClickListener(v -> launchExport());
    }

    private void export(Uri uri) {
        AppDatabase.getExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                List<PodcastMetadata> podcasts = db.podcastMetadataDao().getAll();
                OpmlExporter.exportToUri(this, podcasts, uri);

                runOnUiThread(() -> {
                    Toast.makeText(this, "OPML saved successfully", Toast.LENGTH_LONG).show();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Error saving OPML", Toast.LENGTH_LONG).show()
                );
                Log.e(TAG, "Error saving OPML", e);
            }
        });
    }

    public void launchExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/xml");
        intent.putExtra(Intent.EXTRA_TITLE, "episodes.opml");

        createDocumentLauncher.launch(intent);
    }

}
