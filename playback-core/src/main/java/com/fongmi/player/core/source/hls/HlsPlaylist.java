package com.fongmi.player.core.source.hls;

import com.fongmi.player.core.source.Segment;

import java.util.ArrayList;
import java.util.List;

public final class HlsPlaylist {

    private final boolean master;
    private final List<HlsVariant> variants = new ArrayList<>();
    private final List<Segment> segments = new ArrayList<>();
    private long durationUs;
    private boolean endList;

    public HlsPlaylist(boolean master) {
        this.master = master;
    }

    public boolean isMaster() {
        return master;
    }

    public List<HlsVariant> getVariants() {
        return variants;
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public long getDurationUs() {
        return durationUs;
    }

    public void addDuration(long durationUs) {
        this.durationUs += durationUs;
    }

    public boolean isEndList() {
        return endList;
    }

    public void setEndList(boolean endList) {
        this.endList = endList;
    }
}
