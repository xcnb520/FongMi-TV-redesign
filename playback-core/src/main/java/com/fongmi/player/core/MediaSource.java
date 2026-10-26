package com.fongmi.player.core;

import android.net.Uri;

import java.util.Collections;
import java.util.Map;

public abstract class MediaSource {

    private final Uri uri;
    private final Map<String, String> headers;

    protected MediaSource(Uri uri, Map<String, String> headers) {
        this.uri = uri;
        this.headers = headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(headers);
    }

    public Uri getUri() {
        return uri;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public abstract SourceType getType();
}
