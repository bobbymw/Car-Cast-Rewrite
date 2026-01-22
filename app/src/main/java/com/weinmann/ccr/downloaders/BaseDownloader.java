package com.weinmann.ccr.downloaders;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BaseDownloader {
    protected final AtomicBoolean abortRequested;
    protected long currentBytes = 0;

    public BaseDownloader(AtomicBoolean abortRequested) {
        this.abortRequested = abortRequested;
    }

    public long getCurrentBytes() {
        return currentBytes;
    }

    protected String fetchTextUrl(String urlString) throws Exception {
        currentBytes = 0L;
        HttpURLConnection connection = openConnection(urlString);

        try (InputStream inputStream = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (abortRequested.get()) break;
                content.append(line).append("\n");
                currentBytes += line.getBytes().length;
            }
            return content.toString();
        } finally {
            connection.disconnect();
        }
    }

    protected void fetchBinaryUrl(String urlString, OutputStream outputStream) throws IOException {
        currentBytes = 0L;
        HttpURLConnection connection = openConnection(urlString);

        try (InputStream in = connection.getInputStream()) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                if (abortRequested.get()) break;
                outputStream.write(buffer, 0, len);
                currentBytes += len;
            }
        } finally {
            connection.disconnect();
        }
    }

    @NonNull
    private HttpURLConnection openConnection(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        return connection;
    }
}
