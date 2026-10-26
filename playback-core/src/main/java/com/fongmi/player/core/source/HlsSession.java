package com.fongmi.player.core.source;

import android.net.Uri;

import com.fongmi.player.core.PlaybackException;
import com.fongmi.player.core.source.hls.HlsParser;
import com.fongmi.player.core.source.hls.HlsPlaylist;
import com.fongmi.player.core.source.hls.HlsVariant;

import java.util.Map;

public final class HlsSession {

    private final Uri uri;
    private final Map<String, String> headers;

    public HlsSession(Uri uri, Map<String, String> headers) {
        this.uri = uri;
        this.headers = headers;
    }

    public SourceHandle open() throws PlaybackException {
        SegmentLoader loader = new SegmentLoader();
        HlsPlaylist playlist = HlsParser.parse(loader.loadText(uri.toString(), headers), uri.toString());
        if (playlist.isMaster()) {
            HlsVariant variant = selectVariant(playlist);
            if (variant == null) throw new PlaybackException(PlaybackException.SOURCE_UNSUPPORTED, "No HLS variant");
            playlist = HlsParser.parse(loader.loadText(variant.getUrl(), headers), variant.getUrl());
        }
        if (playlist.getSegments().isEmpty()) throw new PlaybackException(PlaybackException.SOURCE_UNSUPPORTED, "No HLS segment");
        SegmentMediaDataSource dataSource = new SegmentMediaDataSource(playlist.getSegments(), headers);
        return SourceHandle.single(dataSource, playlist.getDurationUs());
    }

    private HlsVariant selectVariant(HlsPlaylist playlist) {
        HlsVariant selected = null;
        for (HlsVariant variant : playlist.getVariants()) {
            if (selected == null) {
                selected = variant;
                continue;
            }
            boolean fits = variant.getHeight() > 0 && variant.getHeight() <= 1080;
            boolean higher = variant.getHeight() >= selected.getHeight();
            boolean downgrade = selected.getHeight() > 1080;
            if (fits && (selected.getHeight() <= 0 || downgrade || higher)) selected = variant;
        }
        return selected;
    }
}
