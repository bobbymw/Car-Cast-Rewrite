package com.weinmann.ccr;

import androidx.annotation.NonNull;

import com.weinmann.ccr.records.PodcastMetadata;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class OpmlImporter {
    @NonNull
    public static List<PodcastMetadata> read(InputStream inputStream) throws Exception {

        List<PodcastMetadata> podcasts = new ArrayList<>();

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(false);
        XmlPullParser parser = factory.newPullParser();

        parser.setInput(inputStream, "UTF-8");

        int eventType = parser.getEventType();

        while (eventType != XmlPullParser.END_DOCUMENT) {

            if (eventType == XmlPullParser.START_TAG &&
                    "outline".equalsIgnoreCase(parser.getName())) {

                String xmlUrl = parser.getAttributeValue(null, "xmlUrl");
                if (xmlUrl != null && !xmlUrl.isEmpty()) {

                    String title = parser.getAttributeValue(null, "title");
                    if (title == null || title.isEmpty()) {
                        title = parser.getAttributeValue(null, "text");
                    }

                    podcasts.add(new PodcastMetadata(0L, title, xmlUrl, PodcastMetadata.USE_GLOBAL_DEFAULT_MAX_DOWNLOADS, true));
                }
            }

            eventType = parser.next();
        }

        return podcasts;
    }
}