package com.fongmi.player.core.source;

import android.media.MediaDataSource;
import android.net.Uri;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SourceHandle implements AutoCloseable {

    private final Uri uri;
    private final Map<String, String> headers;
    private final MediaDataSource videoDataSource;
    private final MediaDataSource audioDataSource;
    private final long durationUs;
    private final Runnable closer;
    private final BandwidthEstimator estimator;
    private final List<Quality> qualities;
    private final int qualityIndex;

    private SourceHandle(Uri uri, Map<String, String> headers, MediaDataSource videoDataSource, MediaDataSource audioDataSource, long durationUs, Runnable closer, BandwidthEstimator estimator, List<Quality> qualities, int qualityIndex) {
        this.uri = uri;
        this.headers = headers;
        this.videoDataSource = videoDataSource;
        this.audioDataSource = audioDataSource;
        this.durationUs = durationUs;
        this.closer = closer;
        this.estimator = estimator;
        this.qualities = qualities == null ? Collections.emptyList() : qualities;
        this.qualityIndex = qualityIndex;
    }

    public static SourceHandle uri(Uri uri, Map<String, String> headers, long durationUs) {
        return new SourceHandle(uri, headers, null, null, durationUs, null, null, null, -1);
    }

    public static SourceHandle single(MediaDataSource dataSource, long durationUs) {
        return new SourceHandle(null, null, dataSource, null, durationUs, null, null, null, -1);
    }

    public static SourceHandle dual(MediaDataSource videoDataSource, MediaDataSource audioDataSource, long durationUs) {
        return new SourceHandle(null, null, videoDataSource, audioDataSource, durationUs, null, null, null, -1);
    }

    public static SourceHandle adaptive(MediaDataSource videoDataSource, MediaDataSource audioDataSource, long durationUs, BandwidthEstimator estimator, List<Quality> qualities, int qualityIndex) {
        return new SourceHandle(null, null, videoDataSource, audioDataSource, durationUs, null, estimator, qualities, qualityIndex);
    }

    public Uri getUri() {
        return uri;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public MediaDataSource getVideoDataSource() {
        return videoDataSource;
    }

    public MediaDataSource getAudioDataSource() {
        return audioDataSource;
    }

    public long getDurationUs() {
        return durationUs;
    }

    public BandwidthEstimator getEstimator() {
        return estimator;
    }

    public List<Quality> getQualities() {
        return qualities;
    }

    public int getQualityIndex() {
        return qualityIndex;
    }

    public boolean isDual() {
        return videoDataSource != null && audioDataSource != null;
    }

    public boolean hasUri() {
        return uri != null;
    }

    @Override
    public void close() {
        if (closer != null) closer.run();
        if (videoDataSource != null) {
            try {
                videoDataSource.close();
            } catch (Exception ignored) {
            }
        }
        if (audioDataSource != null) {
            try {
                audioDataSource.close();
            } catch (Exception ignored) {
            }
        }
    }
}
