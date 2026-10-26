package com.fongmi.player.core.source;

public final class Quality {

    private final int index;
    private final int width;
    private final int height;
    private final long bandwidth;

    public Quality(int index, int width, int height, long bandwidth) {
        this.index = index;
        this.width = width;
        this.height = height;
        this.bandwidth = bandwidth;
    }

    public int getIndex() {
        return index;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getBandwidth() {
        return bandwidth;
    }
}
