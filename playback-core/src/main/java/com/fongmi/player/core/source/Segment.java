package com.fongmi.player.core.source;

public final class Segment {

    private final String url;
    private final long rangeStart;
    private final long rangeEnd;
    private final long durationUs;
    private final long startTimeUs;

    public Segment(String url, long rangeStart, long rangeEnd, long durationUs, long startTimeUs) {
        this.url = url;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.durationUs = durationUs;
        this.startTimeUs = startTimeUs;
    }

    public String getUrl() {
        return url;
    }

    public long getRangeStart() {
        return rangeStart;
    }

    public long getRangeEnd() {
        return rangeEnd;
    }

    public long getDurationUs() {
        return durationUs;
    }

    public long getStartTimeUs() {
        return startTimeUs;
    }
}
