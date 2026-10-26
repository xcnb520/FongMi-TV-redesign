package com.fongmi.player.core.clock;

public final class SystemMasterClock implements MasterClock {

    private long baseUs;
    private long startNanos;
    private boolean running;

    public synchronized void start() {
        if (running) return;
        startNanos = System.nanoTime();
        running = true;
    }

    public synchronized void pause() {
        if (!running) return;
        baseUs = positionUs();
        running = false;
    }

    public synchronized void seekTo(long positionUs) {
        baseUs = Math.max(0L, positionUs);
        startNanos = System.nanoTime();
    }

    @Override
    public synchronized long positionUs() {
        return baseUs + (running ? (System.nanoTime() - startNanos) / 1000L : 0L);
    }
}
