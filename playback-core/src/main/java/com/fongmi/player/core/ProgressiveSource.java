package com.fongmi.player.core;

import android.net.Uri;

import java.util.Map;

public final class ProgressiveSource extends MediaSource {

    public ProgressiveSource(Uri uri) {
        this(uri, null);
    }

    public ProgressiveSource(Uri uri, Map<String, String> headers) {
        super(uri, headers);
    }

    @Override
    public SourceType getType() {
        return SourceType.PROGRESSIVE;
    }
}
