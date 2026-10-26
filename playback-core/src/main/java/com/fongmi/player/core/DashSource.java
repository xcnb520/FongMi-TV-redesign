package com.fongmi.player.core;

import android.net.Uri;

import java.util.Map;

public final class DashSource extends MediaSource {

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
}
