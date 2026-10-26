package com.fongmi.player.core.render;

public final class DecodedFrame {

    private final int index;
    private final long ptsUs;
    private final int size;

    public DecodedFrame(int index, long ptsUs, int size) {
        this.index = index;
        this.ptsUs = ptsUs;
        this.size = size;
    }

    public int getIndex() {
        return index;
    }

    public long getPtsUs() {
        return ptsUs;
    }

    public int getSize() {
        return size;
    }
}
