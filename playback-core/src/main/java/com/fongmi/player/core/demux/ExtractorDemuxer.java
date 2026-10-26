package com.fongmi.player.core.demux;

import android.content.Context;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.fongmi.player.core.PlaybackException;
import com.fongmi.player.core.TrackInfo;
import com.fongmi.player.core.TrackType;
import com.fongmi.player.core.source.SourceHandle;
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
    private final SourceHandle handle;
    private final SampleQueue videoQueue;
    private final SampleQueue audioQueue;
    private final Listener listener;
    private final BufferPool bufferPool = new BufferPool(24, 256 * 1024);
    private final Object pauseLock = new Object();

    private MediaExtractor singleExtractor;
    private MediaExtractor videoExtractor;
    private MediaExtractor audioExtractor;
    private MediaFormat videoFormat;
    private MediaFormat audioFormat;
    private TrackInfo[] tracks = new TrackInfo[0];
    private int videoTrack = -1;
    private int audioTrack = -1;
    private long durationUs;
    private boolean dual;
    private boolean videoEnded;
    private boolean audioEnded;
    private volatile Long pendingSeekUs;
    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean released;
    private volatile boolean videoEnabled = true;
    private volatile boolean audioEnabled = true;
    private Thread thread;

    public ExtractorDemuxer(Context context, SourceHandle handle, SampleQueue videoQueue, SampleQueue audioQueue, Listener listener) {
        this.context = context;
        this.handle = handle;
        this.videoQueue = videoQueue;
        this.audioQueue = audioQueue;
        this.listener = listener;
    }

    public void prepare() throws PlaybackException {
        try {
            dual = handle.isDual();
            if (dual) {
                videoExtractor = new MediaExtractor();
                videoExtractor.setDataSource(handle.getVideoDataSource());
                audioExtractor = new MediaExtractor();
                audioExtractor.setDataSource(handle.getAudioDataSource());
                videoTrack = findTrack(videoExtractor, true);
                audioTrack = findTrack(audioExtractor, false);
                if (videoTrack < 0 || audioTrack < 0) throw new PlaybackException(PlaybackException.SOURCE_UNSUPPORTED, "Missing DASH track");
                videoExtractor.selectTrack(videoTrack);
                audioExtractor.selectTrack(audioTrack);
            } else {
                singleExtractor = new MediaExtractor();
                MediaDataSource dataSource = handle.getVideoDataSource() != null ? handle.getVideoDataSource() : handle.getAudioDataSource();
                if (dataSource != null) singleExtractor.setDataSource(dataSource);
                else if (handle.hasUri()) singleExtractor.setDataSource(context, handle.getUri(), handle.getHeaders());
                else throw new PlaybackException(PlaybackException.SOURCE_UNSUPPORTED, "No source");
                for (int i = 0; i < singleExtractor.getTrackCount(); i++) {
                    MediaFormat format = singleExtractor.getTrackFormat(i);
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
                if (videoTrack >= 0) singleExtractor.selectTrack(videoTrack);
                if (audioTrack >= 0) singleExtractor.selectTrack(audioTrack);
            }
            durationUs = Math.max(readDuration(videoFormat, audioFormat), handle.getDurationUs());
            tracks = buildTracks();
        } catch (PlaybackException e) {
            release();
            throw e;
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
        releaseExtractor(singleExtractor);
        releaseExtractor(videoExtractor);
        releaseExtractor(audioExtractor);
        singleExtractor = null;
        videoExtractor = null;
        audioExtractor = null;
        handle.close();
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
                    doSeek(seek);
                }
                if (paused) {
                    synchronized (pauseLock) {
                        while (paused && !released) pauseLock.wait();
                    }
                    continue;
                }
                boolean more = dual ? readDualStep() : readSingleStep();
                if (!more) {
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

    private boolean readSingleStep() throws InterruptedException {
        int track = singleExtractor.getSampleTrackIndex();
        if (track < 0 || singleExtractor.getSampleTime() < 0L) return false;
        boolean video = track == videoTrack;
        boolean audio = track == audioTrack;
        if (!readSample(singleExtractor, video ? videoQueue : audioQueue, video ? videoEnabled : audioEnabled, video, audio)) return false;
        return singleExtractor.advance();
    }

    private boolean readDualStep() throws InterruptedException {
        long videoTime = videoEnabled ? videoExtractor.getSampleTime() : -1L;
        long audioTime = audioEnabled ? audioExtractor.getSampleTime() : -1L;
        if (videoTime < 0L && audioTime < 0L) return false;
        boolean video = videoTime >= 0L && (audioTime < 0L || videoTime <= audioTime);
        MediaExtractor extractor = video ? videoExtractor : audioExtractor;
        if (!readSample(extractor, video ? videoQueue : audioQueue, true, video, !video)) return false;
        if (!extractor.advance()) {
            if (video) videoEnded = true;
            else audioEnded = true;
        }
        if (videoEnded && audioEnded) return false;
        if (videoEnded) videoEnabled = false;
        if (audioEnded) audioEnabled = false;
        return true;
    }

    private boolean readSample(MediaExtractor extractor, SampleQueue queue, boolean enabled, boolean video, boolean audio) throws InterruptedException {
        long sampleSize = extractor.getSampleSize();
        if (sampleSize <= 0L || sampleSize > Integer.MAX_VALUE) return false;
        int size = (int) sampleSize;
        ByteBuffer buffer = bufferPool.acquire(size);
        int read = extractor.readSampleData(buffer, 0);
        if (read < 0) {
            bufferPool.release(buffer);
            return false;
        }
        buffer.position(0);
        buffer.limit(read);
        Sample sample = new Sample(buffer, extractor.getSampleTime(), extractor.getSampleFlags(), read, bufferPool);
        if (enabled) queue.put(sample);
        else sample.release();
        return true;
    }

    private void doSeek(long positionUs) {
        videoEnded = false;
        audioEnded = false;
        if (dual) {
            videoExtractor.seekTo(positionUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            audioExtractor.seekTo(positionUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        } else if (singleExtractor != null) {
            singleExtractor.seekTo(positionUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        }
        videoQueue.clear();
        audioQueue.clear();
        videoQueue.reset();
        audioQueue.reset();
    }

    private void finish() {
        videoQueue.end();
        audioQueue.end();
        if (!released) listener.onEnded();
    }

    private int findTrack(MediaExtractor extractor, boolean video) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (video && MediaUtil.isVideo(mime)) {
                videoFormat = format;
                return i;
            }
            if (!video && MediaUtil.isAudio(mime)) {
                audioFormat = format;
                return i;
            }
        }
        return -1;
    }

    private void releaseExtractor(MediaExtractor extractor) {
        if (extractor != null) extractor.release();
    }

    private long readDuration(MediaFormat video, MediaFormat audio) {
        long value = readLong(video, MediaFormat.KEY_DURATION);
        long audioValue = readLong(audio, MediaFormat.KEY_DURATION);
        return Math.max(Math.max(0L, value), Math.max(0L, audioValue));
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
