package com.fongmi.player.core.source.hls;

public final class HlsVariant {

    private final String url;
    private final long bandwidth;
    private final int width;
    private final int height;
    private final String codecs;

    public HlsVariant(String url, long bandwidth, int width, int height, String codecs) {
        this.url = url;
        this.bandwidth = bandwidth;
        this.width = width;
        this.height = height;
        this.codecs = codecs;
    }

    public String getUrl() {
        return url;
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

    public String getCodecs() {
        return codecs;
    }
}
