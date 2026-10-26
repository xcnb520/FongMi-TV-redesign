package com.fongmi.player.core.source;

public final class BandwidthEstimator {

    private double estimateBps;

    public synchronized void sample(long bytes, long elapsedMs) {
        if (bytes <= 0L || elapsedMs <= 0L) return;
        double sample = bytes * 8000d / elapsedMs;
        estimateBps = estimateBps <= 0d ? sample : estimateBps * 0.8d + sample * 0.2d;
    }

    public synchronized long estimateBps() {
        return (long) estimateBps;
    }

    public synchronized void reset() {
        estimateBps = 0d;
    }
}
