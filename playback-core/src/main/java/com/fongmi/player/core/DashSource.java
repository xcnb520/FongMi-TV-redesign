package com.fongmi.player.core;

import android.content.Context;
import android.net.Uri;

import com.fongmi.player.core.source.DashSession;
import com.fongmi.player.core.source.SourceHandle;

import java.util.Map;

public final class DashSource extends MediaSource implements AdaptiveSource {

    private volatile int qualityIndex = -1;
    private volatile long startPositionUs;

    public DashSource(Uri uri) {
        this(uri, null);
    }

    public DashSource(Uri uri, Map<String, String> headers) {
        super(uri, headers);
    }

    @Override
    public SourceType getType() {
        return SourceType.DASH;
    }

    @Override
    public SourceHandle open(Context context) throws PlaybackException {
        return new DashSession(getUri(), getHeaders()).open(qualityIndex, startPositionUs);
    }

    @Override
    public void setQualityIndex(int index) {
        this.qualityIndex = index;
    }

    @Override
    public void setStartPositionUs(long positionUs) {
        this.startPositionUs = positionUs;
    }
}
