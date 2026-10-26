package com.fongmi.player.core.source.dash;

import java.util.ArrayList;
import java.util.List;

public final class DashManifest {

    private final String baseUrl;
    private final long durationUs;
    private final boolean dynamic;
    private final List<DashRepresentation> representations = new ArrayList<>();

    public DashManifest(String baseUrl, long durationUs, boolean dynamic) {
        this.baseUrl = baseUrl;
        this.durationUs = durationUs;
        this.dynamic = dynamic;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public long getDurationUs() {
        return durationUs;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public List<DashRepresentation> getRepresentations() {
        return representations;
    }
}
