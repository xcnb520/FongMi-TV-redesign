package com.fongmi.player.core.source.dash;

import com.fongmi.player.core.source.Segment;

import java.util.ArrayList;
import java.util.List;

public final class DashRepresentation {

    private final String id;
    private final String mime;
    private final String codecs;
    private final String language;
    private final long bandwidth;
    private final int width;
    private final int height;
    private final float frameRate;
    private final long durationUs;
    private final List<Segment> segments = new ArrayList<>();

    public DashRepresentation(String id, String mime, String codecs, String language, long bandwidth, int width, int height, float frameRate, long durationUs) {
        this.id = id;
        this.mime = mime;
        this.codecs = codecs;
        this.language = language;
        this.bandwidth = bandwidth;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.durationUs = durationUs;
    }

    public String getId() {
        return id;
    }

    public String getMime() {
        return mime;
    }

    public String getCodecs() {
        return codecs;
    }

    public String getLanguage() {
        return language;
    }

    public long getBandwidth() {
        return bandwidth;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public float getFrameRate() {
        return frameRate;
    }

    public long getDurationUs() {
        return durationUs;
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public boolean isVideo() {
        return mime != null && mime.startsWith("video/");
    }

    public boolean isAudio() {
        return mime != null && mime.startsWith("audio/");
    }
}
