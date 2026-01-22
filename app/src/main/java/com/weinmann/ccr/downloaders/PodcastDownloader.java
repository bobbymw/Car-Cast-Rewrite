package com.weinmann.ccr.downloaders;

import android.content.Context;

import com.weinmann.ccr.*;
import com.weinmann.ccr.db.AppDatabase;
import com.weinmann.ccr.db.EpisodeMetadataDao;
import com.weinmann.ccr.records.*;

import androidx.annotation.NonNull;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class PodcastDownloader extends BaseDownloader {

    private final Context context;

    public PodcastDownloader(@NonNull Context context, @NonNull AtomicBoolean abortRequested) {
        super(abortRequested);
        this.context = context;
    }

    public int testPodcast(PodcastMetadata podcast) {
        try {
            String rssContent = fetchTextUrl(podcast.url());
            List<EpisodeMetadata> items = parseRss(podcast.id(), rssContent, podcast.getCalculatedMaxDownloads(context));
            return items.size();
        } catch (Exception e) {
            return 0;
        }
    }

    @NonNull
    private static EpisodeMetadata createCopyToAllowRedownloadingThisTimeButNotInTheFuture(EpisodeMetadata existing) {
        EpisodeMetadata item;
        item = EpisodeMetadata.createCopyForDownload(existing, null, 0, 0);
        return item;
    }

    public String downloadPodcast(PodcastMetadata podcast) throws Exception {
        AppDatabase db = AppDatabase.getInstance(context);
        EpisodeMetadataDao dao = db.episodeMetadataDao();

        String rssContent = fetchTextUrl(podcast.url());
        if (abortRequested.get()) return "Aborted after RSS download";

        List<EpisodeMetadata> items = parseRss(podcast.id(),
                                               rssContent,
                                               podcast.getCalculatedMaxDownloads(context));
        StringBuilder resultMessageSb = new StringBuilder();
        resultMessageSb.append("Found ").append(items.size()).append(" items in RSS\n");

        int savedCount = 0;
        for (EpisodeMetadata item : items) {
            if (abortRequested.get()) {
                resultMessageSb.append(" Aborted while checking ").append(item.title());
                return resultMessageSb.toString();
            }

            EpisodeMetadata existing = dao.getByUrl(item.enclosureUrl());
            if (existing != null) {
                if (existing.useForHistory()) {
                    continue; // already downloaded
                }

                item = createCopyToAllowRedownloadingThisTimeButNotInTheFuture(existing);
            }

            saveEpisodeMetadataFile(item);
            savedCount++;
        }

        resultMessageSb.append(savedCount).append(" new episodes to download\n");
        return resultMessageSb.toString();
    }

    @NonNull
    private List<EpisodeMetadata> parseRss(long podcastId, String rssContent, int maxDownloads) throws Exception {
        List<EpisodeMetadata> items = new ArrayList<>();

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(false);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader(rssContent));

        String podcastName = "";
        boolean inItem = false;
        boolean inChannel = false;
        String currentTag = "";
        String title = "";
        String description = "";
        String pubDate = "";
        String enclosureUrl = "";
        String enclosureMimeType = "";

        int eventType = parser.getEventType();

        while (eventType != XmlPullParser.END_DOCUMENT &&
                items.size() < maxDownloads &&
                !abortRequested.get()) {
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    currentTag = parser.getName();
                    if ("channel".equalsIgnoreCase(currentTag)) {
                        inChannel = true;
                    } else if ("item".equalsIgnoreCase(currentTag)) {
                        inItem = true;
                        title = "";
                        description = "";
                        pubDate = "";
                        enclosureUrl = "";
                        enclosureMimeType = "";
                    } else if ("enclosure".equalsIgnoreCase(currentTag)) {
                        enclosureUrl = Util.normalizeUrl(parser.getAttributeValue(null, "url"));
                        enclosureMimeType = parser.getAttributeValue(null, "type");
                        if (enclosureMimeType.isEmpty()) {
                            enclosureMimeType =  guessMimeTypeFromUrl(enclosureUrl);
                        }
                    }
                    break;

                case XmlPullParser.TEXT:
                    String text = parser.getText().trim();
                    if (!text.isEmpty()) {
                        if (inItem) {
                            if ("title".equalsIgnoreCase(currentTag)) {
                                title = text;
                            } else if ("description".equalsIgnoreCase(currentTag)) {
                                description = text;
                            } else if ("pubDate".equalsIgnoreCase(currentTag)) {
                                pubDate = text;
                            }
                        } else if (inChannel && "title".equalsIgnoreCase(currentTag)) {
                            podcastName = text;
                        }
                    }
                    break;

                case XmlPullParser.END_TAG:
                    String endTag = parser.getName();
                    if ("item".equalsIgnoreCase(endTag)) {
                        inItem = false;
                        if (!title.isEmpty() && !enclosureUrl.isEmpty()) {
                            Instant pubDateInstant = ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();

                            items.add(new EpisodeMetadata(
                                    0L,
                                    podcastId,
                                    podcastName,
                                    title,
                                    description,
                                    enclosureUrl,
                                    pubDateInstant.toEpochMilli(),
                                    null,
                                    enclosureMimeType,
                                    0L,
                                    0,
                                    0,
                                    true,
                                    false,
                                    true));
                        }
                    } else if ("channel".equalsIgnoreCase(endTag)) {
                        inChannel = false;
                    }
                    currentTag = "";
                    break;
            }
            eventType = parser.next();
        }

        return items;
    }

    private void saveEpisodeMetadataFile(@NonNull EpisodeMetadata item) {
        AppDatabase db = AppDatabase.getInstance(context);

        EpisodeMetadataDao dao = db.episodeMetadataDao();
        if (item.id() > 0) {
            dao.update(item);
        } else {
            dao.insert(item);
        }
    }

    private static @NonNull String guessMimeTypeFromUrl(String url) {
        String lower = url.toLowerCase(Locale.US);
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".opus")) return "audio/opus";
        if (lower.endsWith(".m4a") || lower.endsWith(".mp4")) return "audio/mp4";
        return "audio/mp3";
    }
}
