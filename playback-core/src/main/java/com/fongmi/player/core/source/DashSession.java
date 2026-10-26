package com.fongmi.player.core.source;

import android.net.Uri;

import com.fongmi.player.core.PlaybackException;
import com.fongmi.player.core.source.dash.DashManifest;
import com.fongmi.player.core.source.dash.DashParser;
import com.fongmi.player.core.source.dash.DashRepresentation;

import java.util.ArrayList;
import java.util.Comparator;
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
        return open(-1, 0L);
    }

    public SourceHandle open(int requestedIndex, long startPositionUs) throws PlaybackException {
        SegmentLoader loader = new SegmentLoader();
        String xml = loader.loadText(uri.toString(), headers);
        DashManifest manifest = DashParser.parse(xml, uri.toString());
        List<DashRepresentation> videos = new ArrayList<>();
        for (DashRepresentation representation : manifest.getRepresentations()) {
            if (representation.isVideo() && !representation.getSegments().isEmpty()) videos.add(representation);
        }
        videos.sort(Comparator.comparingLong(DashRepresentation::getBandwidth));
        List<Quality> qualities = new ArrayList<>();
        for (int i = 0; i < videos.size(); i++) {
            DashRepresentation representation = videos.get(i);
            qualities.add(new Quality(i, representation.getWidth(), representation.getHeight(), representation.getBandwidth()));
        }
        int index = requestedIndex >= 0 && requestedIndex < videos.size() ? requestedIndex : (qualities.isEmpty() ? -1 : qualities.get(0).getIndex());
        DashRepresentation video = index >= 0 ? videos.get(index) : null;
        DashRepresentation audio = selectAudio(manifest.getRepresentations());
        if (video == null && audio == null) throw new PlaybackException(PlaybackException.SOURCE_UNSUPPORTED, "No DASH representation");
        BandwidthEstimator estimator = video == null ? null : new BandwidthEstimator();
        SegmentMediaDataSource videoSource = video == null ? null : new SegmentMediaDataSource(filter(video.getSegments(), startPositionUs), headers, estimator);
        SegmentMediaDataSource audioSource = audio == null ? null : new SegmentMediaDataSource(filter(audio.getSegments(), startPositionUs), headers, null);
        long durationUs = manifest.getDurationUs();
        if (durationUs <= 0L && video != null) durationUs = video.getDurationUs();
        if (durationUs <= 0L && audio != null) durationUs = audio.getDurationUs();
        return SourceHandle.adaptive(videoSource, audioSource, durationUs, estimator, qualities, index);
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

    private List<Segment> filter(List<Segment> segments, long startPositionUs) {
        if (startPositionUs <= 0L) return segments;
        List<Segment> result = new ArrayList<>();
        boolean started = false;
        boolean hasMedia = false;
        for (Segment segment : segments) {
            if (segment.getDurationUs() <= 0L) {
                result.add(segment);
                continue;
            }
            if (!started && segment.getStartTimeUs() + segment.getDurationUs() > startPositionUs) started = true;
            if (started) {
                result.add(segment);
                hasMedia = true;
            }
        }
        return hasMedia ? result : segments;
    }
}
