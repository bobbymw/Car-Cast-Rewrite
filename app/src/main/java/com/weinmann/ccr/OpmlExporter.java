package com.weinmann.ccr;

import android.content.Context;
import android.net.Uri;

import com.weinmann.ccr.records.PodcastMetadata;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class OpmlExporter {

    public static void exportToUri(Context context, List<PodcastMetadata> podcasts, Uri outputUri) throws Exception {

        // Load podcasts (blocking is OK if this is already running in a worker thread)

        // Build OPML
        String opml = buildOpml(podcasts);

        // Write to the chosen URI
        try (OutputStream os = context.getContentResolver().openOutputStream(outputUri)) {
            if (os == null) throw new Exception("Unable to open output stream");
            os.write(opml.getBytes(StandardCharsets.UTF_8));
        }
    }


    // Build OPML 2.0 XML from list of PodcastMetadata
    private static String buildOpml(List<PodcastMetadata> podcasts) {
        StringBuilder sb = new StringBuilder();

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<opml version=\"2.0\">\n");
        sb.append("  <head>\n");
        sb.append("    <title>Episode Subscriptions</title>\n");
        sb.append("  </head>\n");
        sb.append("  <body>\n");

        for (PodcastMetadata podcast : podcasts) {
            String title = escapeXml(podcast.title());
            String url = escapeXml(podcast.url());

            sb.append("    <outline type=\"rss\" text=\"")
                    .append(title)
                    .append("\" title=\"")
                    .append(title)
                    .append("\" xmlUrl=\"")
                    .append(url)
                    .append("\" />\n");
        }

        sb.append("  </body>\n");
        sb.append("</opml>\n");

        return sb.toString();
    }


    // Basic XML escaping
    private static String escapeXml(String in) {
        if (in == null) return "";
        return in
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
