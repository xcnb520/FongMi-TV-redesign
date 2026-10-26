package com.fongmi.player.core.source.hls;

import com.fongmi.player.core.PlaybackException;
import com.fongmi.player.core.source.Segment;

import java.io.BufferedReader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HlsParser {

    public static HlsPlaylist parse(String text, String playlistUrl) throws PlaybackException {
        try {
            boolean master = text.contains("#EXT-X-STREAM-INF");
            HlsPlaylist playlist = new HlsPlaylist(master);
            if (master) {
                parseMaster(text, playlistUrl, playlist);
            } else {
                parseMedia(text, playlistUrl, playlist);
            }
            return playlist;
        } catch (Exception e) {
            throw new PlaybackException(PlaybackException.SOURCE_UNSUPPORTED, e.getMessage(), e);
        }
    }

    private static void parseMaster(String text, String playlistUrl, HlsPlaylist playlist) throws Exception {
        BufferedReader reader = new BufferedReader(new StringReader(text));
        String line;
        Map<String, String> pending = null;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                pending = parseAttributes(line.substring("#EXT-X-STREAM-INF:".length()));
            } else if (!line.isEmpty() && !line.startsWith("#") && pending != null) {
                long bandwidth = parseLong(pending.get("BANDWIDTH"));
                int width = 0;
                int height = 0;
                String resolution = pending.get("RESOLUTION");
                if (resolution != null) {
                    int x = resolution.indexOf('x');
                    if (x > 0) {
                        width = parseInt(resolution.substring(0, x));
                        height = parseInt(resolution.substring(x + 1));
                    }
                }
                String codecs = pending.get("CODECS");
                playlist.getVariants().add(new HlsVariant(resolve(playlistUrl, line), bandwidth, width, height, codecs));
                pending = null;
            }
        }
    }

    private static void parseMedia(String text, String playlistUrl, HlsPlaylist playlist) throws Exception {
        BufferedReader reader = new BufferedReader(new StringReader(text));
        String line;
        long durationUs = 0L;
        long rangeStart = 0L;
        long rangeEnd = -1L;
        String initUrl = null;
        long initRangeStart = 0L;
        long initRangeEnd = -1L;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF:")) {
                String value = line.substring("#EXTINF:".length());
                int comma = value.indexOf(',');
                if (comma >= 0) value = value.substring(0, comma);
                durationUs = (long) (parseDouble(value) * 1_000_000d);
            } else if (line.startsWith("#EXT-X-BYTERANGE:")) {
                String value = line.substring("#EXT-X-BYTERANGE:".length());
                int at = value.indexOf('@');
                if (at >= 0) {
                    long length = parseLong(value.substring(0, at));
                    rangeStart = parseLong(value.substring(at + 1));
                    rangeEnd = rangeStart + length - 1L;
                } else {
                    long length = parseLong(value);
                    rangeEnd = rangeStart + length - 1L;
                }
            } else if (line.startsWith("#EXT-X-MAP:")) {
                Map<String, String> attributes = parseAttributes(line.substring("#EXT-X-MAP:".length()));
                initUrl = resolve(playlistUrl, attributes.get("URI"));
                String byterange = attributes.get("BYTERANGE");
                if (byterange != null) {
                    int at = byterange.indexOf('@');
                    if (at >= 0) {
                        long length = parseLong(byterange.substring(0, at));
                        initRangeStart = parseLong(byterange.substring(at + 1));
                        initRangeEnd = initRangeStart + length - 1L;
                    } else {
                        initRangeStart = 0L;
                        initRangeEnd = parseLong(byterange) - 1L;
                    }
                }
            } else if (line.startsWith("#EXT-X-ENDLIST")) {
                playlist.setEndList(true);
            } else if (!line.startsWith("#")) {
                String url = resolve(playlistUrl, line);
                playlist.getSegments().add(new Segment(url, rangeStart, rangeEnd, durationUs, 0L));
                playlist.addDuration(durationUs);
                durationUs = 0L;
                if (rangeEnd >= 0L) rangeStart = rangeEnd + 1L;
                else rangeStart = 0L;
                rangeEnd = -1L;
            }
        }
        if (initUrl != null) playlist.getSegments().add(0, new Segment(initUrl, initRangeStart, initRangeEnd, 0L, 0L));
    }

    private static Map<String, String> parseAttributes(String input) {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') quoted = !quoted;
            if (c == ',' && !quoted) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) parts.add(current.toString());
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) value = value.substring(1, value.length() - 1);
            result.put(key, value);
        }
        return result;
    }

    private static String resolve(String base, String reference) {
        if (reference == null || reference.isEmpty()) return base;
        try {
            if (reference.startsWith("http://") || reference.startsWith("https://") || reference.startsWith("file:")) return reference;
            return new URL(new URL(base), reference).toString();
        } catch (Exception e) {
            return reference;
        }
    }

    private static long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private static int parseInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static double parseDouble(String value) {
        try {
            return value == null ? 0d : Double.parseDouble(value.trim());
        } catch (Exception e) {
            return 0d;
        }
    }
}
