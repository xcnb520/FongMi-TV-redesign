package com.fongmi.player.core.source;

import android.media.MediaDataSource;
import android.os.SystemClock;

import com.fongmi.player.core.PlaybackException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SegmentMediaDataSource extends MediaDataSource {

    private static final int MAX_CACHED_SEGMENTS = 6;

    private final List<Segment> segments;
    private final Map<String, String> headers;
    private final SegmentLoader loader = new SegmentLoader();
    private final BandwidthEstimator estimator;
    private final long[] lengths;
    private final LinkedHashMap<Integer, byte[]> cache = new LinkedHashMap<>(MAX_CACHED_SEGMENTS, 0.75f, true);
    private boolean closed;

    public SegmentMediaDataSource(List<Segment> segments, Map<String, String> headers) {
        this(segments, headers, null);
    }

    public SegmentMediaDataSource(List<Segment> segments, Map<String, String> headers, BandwidthEstimator estimator) {
        this.segments = new ArrayList<>(segments);
        this.headers = headers;
        this.estimator = estimator;
        this.lengths = new long[this.segments.size()];
        for (int i = 0; i < lengths.length; i++) lengths[i] = -1L;
    }

    public List<Segment> getSegments() {
        return segments;
    }

    @Override
    public synchronized int readAt(long position, byte[] buffer, int offset, int size) {
        if (closed || position < 0 || size <= 0) return -1;
        long start = 0L;
        for (int i = 0; i < segments.size(); i++) {
            long length = lengths[i];
            if (length < 0L) {
                try {
                    length = download(i);
                } catch (PlaybackException e) {
                    return -1;
                }
            }
            if (position < start + length) {
                byte[] data = data(i);
                if (data == null) return -1;
                int inOffset = (int) (position - start);
                int count = Math.min(size, data.length - inOffset);
                System.arraycopy(data, inOffset, buffer, offset, count);
                return count;
            }
            start += length;
        }
        return -1;
    }

    @Override
    public synchronized long getSize() {
        return -1L;
    }

    @Override
    public synchronized void close() {
        closed = true;
        cache.clear();
    }

    private byte[] data(int index) {
        byte[] data = cache.get(index);
        if (data != null) return data;
        try {
            download(index);
        } catch (PlaybackException e) {
            return null;
        }
        return cache.get(index);
    }

    private long download(int index) throws PlaybackException {
        if (index < 0 || index >= segments.size()) return -1L;
        long started = SystemClock.elapsedRealtime();
        byte[] data = loader.load(segments.get(index), headers);
        if (estimator != null) estimator.sample(data.length, Math.max(1L, SystemClock.elapsedRealtime() - started));
        lengths[index] = data.length;
        cache.put(index, data);
        while (cache.size() > MAX_CACHED_SEGMENTS) {
            Integer eldest = cache.keySet().iterator().next();
            cache.remove(eldest);
        }
        return data.length;
    }
}
