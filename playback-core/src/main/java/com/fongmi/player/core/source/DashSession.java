package com.fongmi.player.core.source;

import android.net.Uri;

import com.fongmi.player.core.PlaybackException;
import com.fongmi.player.core.source.dash.DashManifest;
import com.fongmi.player.core.source.dash.DashParser;
import com.fongmi.player.core.source.dash.DashRepresentation;

import java.util.List;
import java.util.Map;

public final class DashSession {

    private final Uri uri;
    private final Map<String, String> headers;

    public DashSession(Uri uri, Map<String, String> headers) {
        this.uri = uri;
        this.headers = headers;
    }

    public SourceHandle open() throws PlaybackException {
        SegmentLoader loader = new SegmentLoader();
        String xml = loader.loadText(uri.toString(), headers);
        DashManifest manifest = DashParser.parse(xml, uri.toString());
        DashRepresentation video = selectVideo(manifest.getRepresentations());
        DashRepresentation audio = selectAudio(manifest.getRepresentations());
        if (video == null && audio == null) throw new PlaybackException(PlaybackException.SOURCE_UNSUPPORTED, "No DASH representation");
        SegmentMediaDataSource videoSource = video == null ? null : new SegmentMediaDataSource(video.getSegments(), headers);
        SegmentMediaDataSource audioSource = audio == null ? null : new SegmentMediaDataSource(audio.getSegments(), headers);
        long durationUs = manifest.getDurationUs();
        if (durationUs <= 0L && video != null) durationUs = video.getDurationUs();
        if (durationUs <= 0L && audio != null) durationUs = audio.getDurationUs();
        if (videoSource != null && audioSource != null) return SourceHandle.dual(videoSource, audioSource, durationUs);
        if (videoSource != null) return SourceHandle.single(videoSource, durationUs);
        return SourceHandle.single(audioSource, durationUs);
    }

    private DashRepresentation selectVideo(List<DashRepresentation> representations) {
        DashRepresentation selected = null;
        for (DashRepresentation representation : representations) {
            if (!representation.isVideo()) continue;
            if (representation.getSegments().isEmpty()) continue;
            if (selected == null) {
                selected = representation;
                continue;
            }
            boolean fits = representation.getHeight() > 0 && representation.getHeight() <= 1080;
            boolean higher = representation.getHeight() >= selected.getHeight();
            boolean downgrade = selected.getHeight() > 1080;
            if (fits && (selected.getHeight() <= 0 || downgrade || higher)) selected = representation;
        }
        return selected;
    }

    private DashRepresentation selectAudio(List<DashRepresentation> representations) {
        DashRepresentation selected = null;
        for (DashRepresentation representation : representations) {
            if (!representation.isAudio()) continue;
            if (representation.getSegments().isEmpty()) continue;
            if (selected == null || representation.getBandwidth() > selected.getBandwidth()) selected = representation;
        }
        return selected;
    }
}
