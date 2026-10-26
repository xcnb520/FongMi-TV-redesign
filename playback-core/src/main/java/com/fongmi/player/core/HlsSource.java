package com.fongmi.player.core;

import android.content.Context;
import android.net.Uri;

import com.fongmi.player.core.source.HlsSession;
import com.fongmi.player.core.source.SourceHandle;

import java.util.Map;

public final class HlsSource extends MediaSource {

    public HlsSource(Uri uri) {
        this(uri, null);
    }

    public HlsSource(Uri uri, Map<String, String> headers) {
        super(uri, headers);
    }

    @Override
    public SourceType getType() {
        return SourceType.HLS;
    }

    @Override
    public SourceHandle open(Context context) throws PlaybackException {
        return new HlsSession(getUri(), getHeaders()).open();
    }
}
