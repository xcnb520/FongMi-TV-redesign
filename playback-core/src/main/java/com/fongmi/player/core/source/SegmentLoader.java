package com.fongmi.player.core.source;

import com.fongmi.player.core.PlaybackException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;

public final class SegmentLoader {

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;

    public byte[] load(Segment segment, Map<String, String> headers) throws PlaybackException {
        String url = segment.getUrl();
        if (url.startsWith("file:")) return readFile(url, segment.getRangeStart(), segment.getRangeEnd());
        try {
            HttpURLConnection connection = open(url, headers);
            if (segment.getRangeEnd() >= segment.getRangeStart() && segment.getRangeStart() >= 0) {
                connection.setRequestProperty("Range", "bytes=" + segment.getRangeStart() + "-" + segment.getRangeEnd());
            }
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw new PlaybackException(PlaybackException.NETWORK_FAILED, "HTTP " + code + " for " + url);
            }
            try (InputStream input = connection.getInputStream()) {
                return readAll(input);
            } finally {
                connection.disconnect();
            }
        } catch (PlaybackException e) {
            throw e;
        } catch (Exception e) {
            throw new PlaybackException(PlaybackException.NETWORK_FAILED, e.getMessage(), e);
        }
    }

    public String loadText(String url, Map<String, String> headers) throws PlaybackException {
        if (url.startsWith("file:")) {
            byte[] data = readFile(url, 0, -1);
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        }
        try {
            HttpURLConnection connection = open(url, headers);
            try (InputStream input = connection.getInputStream()) {
                return new String(readAll(input), java.nio.charset.StandardCharsets.UTF_8);
            } finally {
                connection.disconnect();
            }
        } catch (PlaybackException e) {
            throw e;
        } catch (Exception e) {
            throw new PlaybackException(PlaybackException.NETWORK_FAILED, e.getMessage(), e);
        }
    }

    private HttpURLConnection open(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("Connection", "keep-alive");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        return connection;
    }

    private byte[] readFile(String url, long start, long end) throws PlaybackException {
        try {
            File file = new File(new URI(url));
            long length = file.length();
            long from = Math.max(0L, start);
            long to = end >= from ? Math.min(end, length - 1) : length - 1;
            int size = (int) Math.max(0L, to - from + 1);
            byte[] result = new byte[size];
            try (FileInputStream input = new FileInputStream(file)) {
                long skipped = input.skip(from);
                if (skipped < from) throw new IOException("Cannot seek file");
                int read = 0;
                while (read < size) {
                    int count = input.read(result, read, size - read);
                    if (count < 0) break;
                    read += count;
                }
            }
            return result;
        } catch (Exception e) {
            throw new PlaybackException(PlaybackException.NETWORK_FAILED, e.getMessage(), e);
        }
    }

    private byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        return output.toByteArray();
    }
}
