package com.fongmi.player.core.util;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

public final class BufferPool {

    private final ArrayDeque<ByteBuffer> pool = new ArrayDeque<>();
    private final int maxRetained;
    private final int defaultSize;

    public BufferPool(int maxRetained, int defaultSize) {
        this.maxRetained = maxRetained;
        this.defaultSize = defaultSize;
    }

    public synchronized ByteBuffer acquire(int size) {
        ByteBuffer buffer;
        while ((buffer = pool.pollFirst()) != null) {
            if (buffer.capacity() >= size) {
                buffer.clear();
                return buffer;
            }
        }
        return ByteBuffer.allocateDirect(Math.max(size, defaultSize));
    }

    public synchronized void release(ByteBuffer buffer) {
        if (buffer == null) return;
        if (pool.size() >= maxRetained) return;
        buffer.clear();
        pool.addLast(buffer);
    }

    public synchronized void clear() {
        pool.clear();
    }
}
