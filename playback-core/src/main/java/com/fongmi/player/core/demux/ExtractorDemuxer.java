package com.fongmi.player.core.demux;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import com.fongmi.player.core.MediaSource;
import com.fongmi.player.core.PlaybackException;
import com.fongmi.player.core.TrackInfo;
import com.fongmi.player.core.TrackType;
import com.fongmi.player.core.util.BufferPool;
import com.fongmi.player.core.util.MediaUtil;

import java.nio.ByteBuffer;
import java.util.Map;

public final class ExtractorDemuxer {

    public interface Listener {

        void onEnded();

        void onError(PlaybackException error);
    }

    private final Context context;
    private final MediaSource source;
    private final SampleQueue videoQueue;
    private final SampleQueue audioQueue;
    private final Listener listener;
    private final BufferPool bufferPool = new BufferPool(24, 256 * 1024);
    private final Object pauseLock = new Object();

    private MediaExtractor extractor;
    private MediaFormat videoFormat;
    private MediaFormat audioFormat;
    private TrackInfo[] tracks = new TrackInfo[0];
    private int videoTrack = -1;
    private int audioTrack = -1;
    private long durationUs;
    private volatile Long pendingSeekUs;
    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean released;
    private volatile boolean videoEnabled = true;
    private volatile boolean audioEnabled = true;
    private Thread thread;

    public ExtractorDemuxer(Context context, MediaSource source, SampleQueue videoQueue, SampleQueue audioQueue, Listener listener) {
        this.context = context;
        this.source = source;
        this.videoQueue = videoQueue;
        this.audioQueue = audioQueue;
        this.listener = listener;
    }

    public void prepare() throws PlaybackException {
        try {
            extractor = new MediaExtractor();
            Uri uri = source.getUri();
            Map<String, String> headers = source.getHeaders();
            if (headers == null || headers.isEmpty()) extractor.setDataSource(context, uri, null);
            else extractor.setDataSource(context, uri, headers);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (MediaUtil.isVideo(mime) && videoTrack < 0) {
                    videoTrack = i;
                    videoFormat = format;
                } else if (MediaUtil.isAudio(mime) && audioTrack < 0) {
                    audioTrack = i;
                    audioFormat = format;
                }
            }
            if (videoTrack < 0 && audioTrack < 0) throw new PlaybackException(PlaybackException.SOURCE_UNSUPPORTED, "No playable track");
            if (videoTrack >= 0) extractor.selectTrack(videoTrack);
            if (audioTrack >= 0) extractor.selectTrack(audioTrack);
            durationUs = readDuration(videoFormat, audioFormat);
            tracks = buildTracks();
        } catch (Exception e) {
            release();
            throw new PlaybackException(PlaybackException.DEMUX_FAILED, e.getMessage(), e);
        }
    }

    public void start() {
        if (thread != null) return;
        running = true;
        thread = new Thread(this::loop, "playback-demux");
        thread.start();
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    public void setVideoEnabled(boolean enabled) {
        videoEnabled = enabled;
    }

    public void setAudioEnabled(boolean enabled) {
        audioEnabled = enabled;
    }

    public void seekTo(long positionUs) {
        pendingSeekUs = Math.max(0L, positionUs);
        videoQueue.clear();
        audioQueue.clear();
    }

    public void release() {
        released = true;
        running = false;
        paused = false;
        videoQueue.release();
        audioQueue.release();
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
        if (thread != null) {
            try {
                thread.join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        if (extractor != null) {
            extractor.release();
            extractor = null;
        }
    }

    public MediaFormat getVideoFormat() {
        return videoFormat;
    }

    public MediaFormat getAudioFormat() {
        return audioFormat;
    }

    public TrackInfo[] getTracks() {
        return tracks.clone();
    }

    public long getDurationUs() {
        return durationUs;
    }

    private void loop() {
        try {
            while (running && !released) {
                Long seek = pendingSeekUs;
                if (seek != null) {
                    pendingSeekUs = null;
                    extractor.seekTo(seek, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    videoQueue.clear();
                    audioQueue.clear();
                    videoQueue.reset();
                    audioQueue.reset();
                }
                if (paused) {
                    synchronized (pauseLock) {
                        while (paused && !released) pauseLock.wait();
                    }
                    continue;
                }
                int track = extractor.getSampleTrackIndex();
                if (track < 0) {
                    finish();
                    return;
                }
                int size = extractor.getSampleSize();
                if (size <= 0) {
                    finish();
                    return;
                }
                ByteBuffer buffer = bufferPool.acquire(size);
                int read = extractor.readSampleData(buffer, 0);
                if (read < 0) {
                    bufferPool.release(buffer);
                    finish();
                    return;
                }
                buffer.position(0);
                buffer.limit(read);
                long ptsUs = extractor.getSampleTime();
                int flags = extractor.getSampleFlags();
                Sample sample = new Sample(buffer, ptsUs, flags, read, bufferPool);
                if (track == videoTrack) {
                    if (videoEnabled) videoQueue.put(sample);
                    else sample.release();
                } else if (track == audioTrack) {
                    if (audioEnabled) audioQueue.put(sample);
                    else sample.release();
                } else {
                    sample.release();
                }
                if (!extractor.advance()) {
                    finish();
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!released) listener.onError(new PlaybackException(PlaybackException.DEMUX_FAILED, e.getMessage(), e));
        }
    }

    private void finish() {
        videoQueue.end();
        audioQueue.end();
        if (!released) listener.onEnded();
    }

    private long readDuration(MediaFormat video, MediaFormat audio) {
        long value = readLong(video, MediaFormat.KEY_DURATION);
        if (value <= 0) value = readLong(audio, MediaFormat.KEY_DURATION);
        return Math.max(0L, value);
    }

    private long readLong(MediaFormat format, String key) {
        if (format == null || !format.containsKey(key)) return 0L;
        try {
            return format.getLong(key);
        } catch (Exception e) {
            try {
                return format.getInteger(key);
            } catch (Exception ignored) {
                return 0L;
            }
        }
    }

    private TrackInfo[] buildTracks() {
        TrackInfo[] result = new TrackInfo[(videoTrack >= 0 ? 1 : 0) + (audioTrack >= 0 ? 1 : 0)];
        int cursor = 0;
        if (videoTrack >= 0) result[cursor++] = buildTrack(videoFormat, videoTrack, TrackType.VIDEO);
        if (audioTrack >= 0) result[cursor] = buildTrack(audioFormat, audioTrack, TrackType.AUDIO);
        return result;
    }

    private TrackInfo buildTrack(MediaFormat format, int index, TrackType type) {
        String mime = format.getString(MediaFormat.KEY_MIME);
        String codec = format.containsKey("codecs-string") ? format.getString("codecs-string") : mime;
        String language = format.containsKey(MediaFormat.KEY_LANGUAGE) ? format.getString(MediaFormat.KEY_LANGUAGE) : null;
        int width = readInt(format, MediaFormat.KEY_WIDTH);
        int height = readInt(format, MediaFormat.KEY_HEIGHT);
        float frameRate = readFloat(format, MediaFormat.KEY_FRAME_RATE);
        int bitrate = readInt(format, MediaFormat.KEY_BIT_RATE);
        int channels = readInt(format, MediaFormat.KEY_CHANNEL_COUNT);
        int sampleRate = readInt(format, MediaFormat.KEY_SAMPLE_RATE);
        return new TrackInfo(type, index, mime, codec, language, width, height, frameRate, bitrate, channels, sampleRate, true);
    }

    private int readInt(MediaFormat format, String key) {
        if (format == null || !format.containsKey(key)) return 0;
        try {
            return format.getInteger(key);
        } catch (Exception e) {
            try {
                return (int) format.getFloat(key);
            } catch (Exception ignored) {
                return 0;
            }
        }
    }

    private float readFloat(MediaFormat format, String key) {
        if (format == null || !format.containsKey(key)) return 0f;
        try {
            return format.getFloat(key);
        } catch (Exception e) {
            try {
                return format.getInteger(key);
            } catch (Exception ignored) {
                return 0f;
            }
        }
    }
}
