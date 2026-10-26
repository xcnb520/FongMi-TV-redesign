package com.fongmi.player.core.demux;

import com.fongmi.player.core.util.BufferPool;

import java.nio.ByteBuffer;

public final class Sample {

    private final ByteBuffer data;
    private final long ptsUs;
    private final int flags;
    private final int size;
    private final BufferPool pool;

    public Sample(ByteBuffer data, long ptsUs, int flags, int size, BufferPool pool) {
        this.data = data;
        this.ptsUs = ptsUs;
        this.flags = flags;
        this.size = size;
        this.pool = pool;
    }

    public ByteBuffer getData() {
        return data;
    }

    public long getPtsUs() {
        return ptsUs;
    }

    public int getFlags() {
        return flags;
    }

    public int getSize() {
        return size;
    }

    public void release() {
        if (pool != null) pool.release(data);
    }
}
