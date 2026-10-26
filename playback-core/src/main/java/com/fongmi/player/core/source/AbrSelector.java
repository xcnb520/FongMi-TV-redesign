package com.fongmi.player.core.source;

import java.util.List;

public final class AbrSelector {

    private AbrSelector() {
    }

    public static int select(List<Quality> qualities, long estimateBps, int currentIndex) {
        if (qualities == null || qualities.isEmpty() || estimateBps <= 0L) return currentIndex;
        long budget = (long) (estimateBps * 0.8d);
        Quality best = null;
        for (Quality quality : qualities) {
            if (quality.getHeight() > 1080) continue;
            if (quality.getBandwidth() <= budget) {
                if (best == null || quality.getBandwidth() > best.getBandwidth()) best = quality;
            }
        }
        if (best == null) best = qualities.get(0);
        Quality current = find(qualities, currentIndex);
        if (current != null) {
            if (best.getBandwidth() > current.getBandwidth() && estimateBps < current.getBandwidth() * 1.3d) return currentIndex;
            if (best.getBandwidth() < current.getBandwidth() && estimateBps > current.getBandwidth() * 0.8d) return currentIndex;
        }
        return best.getIndex();
    }

    private static Quality find(List<Quality> qualities, int index) {
        for (Quality quality : qualities) if (quality.getIndex() == index) return quality;
        return null;
    }
}
