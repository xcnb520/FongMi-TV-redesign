package com.fongmi.player.core.demux;

import java.util.ArrayDeque;

public final class SampleQueue {

    private final ArrayDeque<Sample> queue = new ArrayDeque<>();
    private final int capacity;
    private boolean ended;
    private boolean released;

    public SampleQueue(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void put(Sample sample) throws InterruptedException {
        while (queue.size() >= capacity && !released) wait();
        if (released) {
            sample.release();
            return;
        }
        queue.addLast(sample);
        notifyAll();
    }

    public synchronized Sample take() throws InterruptedException {
        while (queue.isEmpty() && !ended && !released) wait();
        if (released) return null;
        Sample sample = queue.pollFirst();
        notifyAll();
        return sample;
    }

    public synchronized void end() {
        ended = true;
        notifyAll();
    }

    public synchronized void reset() {
        ended = false;
        notifyAll();
    }

    public synchronized void clear() {
        for (Sample sample : queue) sample.release();
        queue.clear();
        notifyAll();
    }

    public synchronized void release() {
        released = true;
        clear();
    }

    public synchronized boolean isEnded() {
        return ended;
    }
}
