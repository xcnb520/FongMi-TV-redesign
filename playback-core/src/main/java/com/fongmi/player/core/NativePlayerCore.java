package com.fongmi.player.core;

import android.content.Context;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import com.fongmi.player.core.audio.AudioClock;
import com.fongmi.player.core.clock.MasterClock;
import com.fongmi.player.core.clock.SystemMasterClock;
import com.fongmi.player.core.decode.AudioDecoder;
import com.fongmi.player.core.decode.VideoDecoder;
import com.fongmi.player.core.demux.ExtractorDemuxer;
import com.fongmi.player.core.demux.SampleQueue;
import com.fongmi.player.core.render.VideoRenderer;
import com.fongmi.player.core.source.AbrSelector;
import com.fongmi.player.core.source.BandwidthEstimator;
import com.fongmi.player.core.source.Quality;
import com.fongmi.player.core.source.SourceHandle;
import com.fongmi.player.core.util.MediaUtil;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class NativePlayerCore implements PlayerCore {

    private static final String TAG = "PlayerCore";

    private final Context context;
    private final CopyOnWriteArrayList<PlayerListener> listeners = new CopyOnWriteArrayList<>();
    private final HandlerThread controlThread = new HandlerThread("playback-control");
    private final Handler control;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile PlayerState state = PlayerState.IDLE;
    private volatile TrackInfo[] tracks = new TrackInfo[0];
    private volatile long durationUs;
    private volatile boolean prepared;
    private volatile MediaSource source;
    private volatile Surface surface;
    private volatile float volume = 1f;
    private volatile float speed = 1f;
    private volatile int qualityIndex = -1;

    private SourceHandle currentHandle;
    private ExtractorDemuxer demuxer;
    private SampleQueue videoQueue;
    private SampleQueue audioQueue;
    private VideoDecoder videoDecoder;
    private AudioDecoder audioDecoder;
    private VideoRenderer videoRenderer;
    private AudioClock audioClock;
    private SystemMasterClock systemClock;
    private MasterClock clock;
    private MediaFormat videoFormat;
    private boolean playWhenReady;
    private long pendingSeekUs = -1L;
    private long lastQualitySwitchMs;
    private boolean videoEos;
    private boolean audioEos;

    NativePlayerCore(Context context) {
        this.context = context.getApplicationContext();
        controlThread.start();
        control = new Handler(controlThread.getLooper());
    }

    @Override
    public void setSource(MediaSource source) {
        post(() -> {
            releasePipeline();
            this.source = source;
            this.tracks = new TrackInfo[0];
            this.durationUs = 0L;
            this.pendingSeekUs = -1L;
            this.playWhenReady = false;
            this.qualityIndex = -1;
            this.state = source == null ? PlayerState.IDLE : PlayerState.READY;
        });
    }

    @Override
    public void prepare() {
        post(() -> prepareInternal(0L, playWhenReady));
    }

    private void prepareInternal(long startPositionUs, boolean autoPlay) {
        if (source == null) return;
        releasePipeline();
        state = PlayerState.PREPARING;
        Log.i(TAG, "prepare surface=" + (surface != null));
        try {
            videoQueue = new SampleQueue(48);
            audioQueue = new SampleQueue(96);
            currentHandle = source.open(context);
            qualityIndex = currentHandle.getQualityIndex();
            demuxer = new ExtractorDemuxer(context, currentHandle, videoQueue, audioQueue, demuxerListener);
            demuxer.prepare();
            tracks = demuxer.getTracks();
            durationUs = demuxer.getDurationUs();
            videoFormat = demuxer.getVideoFormat();
            MediaFormat audioFormat = demuxer.getAudioFormat();
            Log.i(TAG, "tracks=" + tracks.length + " durationUs=" + durationUs
                    + " video=" + (videoFormat == null ? "no" : videoFormat.getString(MediaFormat.KEY_MIME))
                    + " audio=" + (audioFormat == null ? "no" : audioFormat.getString(MediaFormat.KEY_MIME)));
            demuxer.setVideoEnabled(surface != null && videoFormat != null);
            demuxer.setAudioEnabled(audioFormat != null);
            if (audioFormat != null) {
                audioDecoder = new AudioDecoder(audioFormat, audioQueue, audioListener);
                try {
                    audioDecoder.start();
                    audioDecoder.setVolume(volume);
                    audioDecoder.setSpeed(speed);
                    audioClock = new AudioClock(audioDecoder);
                    clock = audioClock;
                } catch (PlaybackException e) {
                    audioDecoder.release();
                    audioDecoder = null;
                    demuxer.setAudioEnabled(false);
                }
            }
            if (clock == null) {
                systemClock = new SystemMasterClock();
                clock = systemClock;
            }
            if (videoFormat != null && surface != null) createVideoPipeline();
            long seekUs = startPositionUs > 0L ? startPositionUs : pendingSeekUs;
            pendingSeekUs = -1L;
            if (seekUs > 0L) demuxer.seekTo(seekUs);
            prepared = true;
            state = PlayerState.READY;
            notifyPrepared();
            if (autoPlay || playWhenReady) {
                playInternal();
                state = PlayerState.PLAYING;
            }
        } catch (PlaybackException e) {
            if (demuxer == null && currentHandle != null) {
                currentHandle.close();
                currentHandle = null;
            }
            fail(e);
        } catch (Exception e) {
            fail(new PlaybackException(PlaybackException.UNKNOWN, e.getMessage(), e));
        }
    }

    @Override
    public void play() {
        post(() -> {
            playWhenReady = true;
            if (!prepared) return;
            if (state == PlayerState.READY || state == PlayerState.PAUSED || state == PlayerState.BUFFERING) {
                playInternal();
                state = PlayerState.PLAYING;
            }
        });
    }

    @Override
    public void pause() {
        post(() -> {
            playWhenReady = false;
            if (state == PlayerState.PLAYING || state == PlayerState.BUFFERING) {
                pauseInternal();
                state = PlayerState.PAUSED;
            }
        });
    }

    @Override
    public void stop() {
        post(() -> {
            playWhenReady = false;
            releasePipeline();
            state = PlayerState.IDLE;
            tracks = new TrackInfo[0];
            durationUs = 0L;
            qualityIndex = -1;
        });
    }

    @Override
    public void seekTo(long positionMs) {
        post(() -> {
            long positionUs = MediaUtil.msToUs(Math.max(0L, positionMs));
            if (!prepared) {
                pendingSeekUs = positionUs;
                return;
            }
            seekInternal(positionUs);
        });
    }

    @Override
    public void setSurface(Surface surface) {
        post(() -> {
            this.surface = surface;
            Log.i(TAG, "setSurface " + (surface != null));
            if (!prepared) return;
            try {
                if (surface == null) releaseVideoPipeline();
                else if (videoFormat != null) createVideoPipeline();
            } catch (PlaybackException e) {
                fail(e);
            }
        });
    }

    @Override
    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        post(() -> {
            if (audioDecoder != null) audioDecoder.setVolume(this.volume);
        });
    }

    @Override
    public void setSpeed(float speed) {
        this.speed = Math.max(0.1f, Math.min(4f, speed));
        post(() -> {
            if (audioDecoder != null) audioDecoder.setSpeed(this.speed);
        });
    }

    @Override
    public void selectTrack(TrackType type, int index) {
        post(() -> {
            for (TrackInfo track : tracks) {
                if (track.getType() == type) track.setSelected(track.getIndex() == index);
            }
        });
    }

    @Override
    public void setQuality(int index) {
        post(() -> {
            if (!(source instanceof AdaptiveSource)) return;
            if (!prepared) {
                qualityIndex = index;
                return;
            }
            if (index == qualityIndex) return;
            switchQuality(index);
        });
    }

    @Override
    public int getQuality() {
        return qualityIndex;
    }

    @Override
    public void release() {
        if (state == PlayerState.RELEASED) return;
        control.post(() -> {
            playWhenReady = false;
            cancelAbr();
            releasePipeline();
            state = PlayerState.RELEASED;
            listeners.clear();
            controlThread.quitSafely();
        });
    }

    @Override
    public PlayerState getState() {
        return state;
    }

    @Override
    public long getPosition() {
        MasterClock current = clock;
        if (current == null) return 0L;
        long positionUs = Math.max(0L, current.positionUs());
        if (durationUs > 0L) positionUs = Math.min(positionUs, durationUs);
        return MediaUtil.usToMs(positionUs);
    }

    @Override
    public long getDuration() {
        return MediaUtil.usToMs(durationUs);
    }

    @Override
    public boolean isPlaying() {
        return state == PlayerState.PLAYING;
    }

    @Override
    public TrackInfo[] getTracks() {
        return tracks.clone();
    }

    @Override
    public void addListener(PlayerListener listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    @Override
    public void removeListener(PlayerListener listener) {
        listeners.remove(listener);
    }

    private void post(Runnable runnable) {
        if (state == PlayerState.RELEASED) return;
        control.post(runnable);
    }

    private void switchQuality(int index) {
        if (!(source instanceof AdaptiveSource)) return;
        long positionUs = MediaUtil.msToUs(getPosition());
        boolean playing = state == PlayerState.PLAYING;
        AdaptiveSource adaptive = (AdaptiveSource) source;
        adaptive.setQualityIndex(index);
        adaptive.setStartPositionUs(positionUs);
        qualityIndex = index;
        lastQualitySwitchMs = SystemClock.elapsedRealtime();
        prepareInternal(positionUs, playing);
    }

    private void createVideoPipeline() throws PlaybackException {
        if (videoFormat == null || surface == null) return;
        releaseVideoPipeline();
        videoEos = false;
        videoDecoder = new VideoDecoder(videoFormat, surface, videoQueue, videoListener);
        videoRenderer = new VideoRenderer(videoDecoder, clock, renderListener);
        videoDecoder.setRenderer(videoRenderer);
        videoDecoder.start();
        videoRenderer.start();
        Log.i(TAG, "video pipeline started");
        videoRenderer.setPlaying(state == PlayerState.PLAYING);
        if (demuxer != null) demuxer.setVideoEnabled(true);
    }

    private void releaseVideoPipeline() {
        if (videoRenderer != null) {
            videoRenderer.release();
            videoRenderer = null;
        }
        if (videoDecoder != null) {
            videoDecoder.release();
            videoDecoder = null;
        }
        if (demuxer != null) demuxer.setVideoEnabled(false);
    }

    private void releasePipeline() {
        cancelAbr();
        releaseVideoPipeline();
        if (audioDecoder != null) {
            audioDecoder.release();
            audioDecoder = null;
        }
        if (demuxer != null) {
            demuxer.release();
            demuxer = null;
        }
        if (videoQueue != null) {
            videoQueue.release();
            videoQueue = null;
        }
        if (audioQueue != null) {
            audioQueue.release();
            audioQueue = null;
        }
        currentHandle = null;
        audioClock = null;
        systemClock = null;
        clock = null;
        videoFormat = null;
        videoEos = false;
        audioEos = false;
        prepared = false;
    }

    private void playInternal() {
        Log.i(TAG, "playInternal");
        if (videoRenderer != null) videoRenderer.setPlaying(true);
        if (audioDecoder != null) audioDecoder.play();
        if (systemClock != null) systemClock.start();
        if (demuxer != null) {
            demuxer.start();
            demuxer.resume();
        }
        scheduleAbr();
    }

    private void pauseInternal() {
        cancelAbr();
        if (videoRenderer != null) videoRenderer.setPlaying(false);
        if (audioDecoder != null) audioDecoder.pause();
        if (demuxer != null) demuxer.pause();
        if (systemClock != null) systemClock.pause();
    }

    private void seekInternal(long positionUs) {
        boolean wasPlaying = state == PlayerState.PLAYING;
        if (demuxer != null) demuxer.seekTo(positionUs);
        if (videoRenderer != null) videoRenderer.flush();
        if (videoDecoder != null) videoDecoder.flush();
        if (audioDecoder != null) audioDecoder.flush();
        if (systemClock != null) systemClock.seekTo(positionUs);
        videoEos = false;
        audioEos = false;
        if (wasPlaying && playWhenReady) {
            if (videoRenderer != null) videoRenderer.setPlaying(true);
            if (audioDecoder != null) audioDecoder.play();
            if (demuxer != null) demuxer.resume();
            state = PlayerState.PLAYING;
        } else {
            if (videoRenderer != null) videoRenderer.setPlaying(false);
            if (audioDecoder != null) audioDecoder.pause();
            if (demuxer != null) demuxer.pause();
            state = PlayerState.PAUSED;
        }
        notifyBuffering(false, 0);
    }

    private void scheduleAbr() {
        cancelAbr();
        if (currentHandle == null || currentHandle.getEstimator() == null) return;
        control.postDelayed(abrTask, 3000L);
    }

    private void cancelAbr() {
        control.removeCallbacks(abrTask);
    }

    private final Runnable abrTask = new Runnable() {
        @Override
        public void run() {
            if (state != PlayerState.PLAYING || currentHandle == null) return;
            BandwidthEstimator estimator = currentHandle.getEstimator();
            List<Quality> qualities = currentHandle.getQualities();
            if (estimator == null || qualities == null || qualities.isEmpty() || estimator.estimateBps() <= 0L) {
                control.postDelayed(this, 3000L);
                return;
            }
            int target = AbrSelector.select(qualities, estimator.estimateBps(), qualityIndex);
            if (target != qualityIndex && SystemClock.elapsedRealtime() - lastQualitySwitchMs > 6000L) {
                switchQuality(target);
                return;
            }
            control.postDelayed(this, 3000L);
        }
    };

    private void fail(PlaybackException error) {
        Log.e(TAG, "playback failed " + error.getCode() + ": " + error.getMessage(), error);
        state = PlayerState.ERROR;
        notifyError(error);
    }

    private void maybeEnded() {
        boolean videoDone = videoDecoder == null || videoEos;
        boolean audioDone = audioDecoder == null || audioEos;
        if (!videoDone || !audioDone) return;
        state = PlayerState.ENDED;
        notifyEnded();
    }

    private final ExtractorDemuxer.Listener demuxerListener = new ExtractorDemuxer.Listener() {
        @Override
        public void onEnded() {
        }

        @Override
        public void onError(PlaybackException error) {
            fail(error);
        }
    };

    private final VideoDecoder.Listener videoListener = new VideoDecoder.Listener() {
        @Override
        public void onVideoSizeChanged(int width, int height, float frameRate) {
            notifyVideoSize(width, height, frameRate);
        }

        @Override
        public void onEndOfStream() {
            videoEos = true;
            maybeEnded();
        }

        @Override
        public void onError(PlaybackException error) {
            fail(error);
        }
    };

    private final AudioDecoder.Listener audioListener = new AudioDecoder.Listener() {
        @Override
        public void onAudioFormatChanged(int sampleRate, int channels, int encoding) {
        }

        @Override
        public void onFirstPcm(long ptsUs) {
        }

        @Override
        public void onEndOfStream() {
            audioEos = true;
            maybeEnded();
        }

        @Override
        public void onError(PlaybackException error) {
            fail(error);
        }
    };

    private final VideoRenderer.Listener renderListener = new VideoRenderer.Listener() {
        @Override
        public void onFirstFrameRendered() {
            notifyFirstFrame();
        }

        @Override
        public void onFrameDropped() {
        }
    };

    private void notifyPrepared() {
        TrackInfo[] snapshot = tracks.clone();
        long duration = MediaUtil.usToMs(durationUs);
        mainHandler.post(() -> {
            for (PlayerListener listener : listeners) listener.onPrepared(snapshot, duration);
        });
    }

    private void notifyBuffering(boolean buffering, int percent) {
        mainHandler.post(() -> {
            for (PlayerListener listener : listeners) listener.onBufferingChanged(buffering, percent);
        });
    }

    private void notifyFirstFrame() {
        Log.i(TAG, "FIRST_FRAME notify");
        mainHandler.post(() -> {
            for (PlayerListener listener : listeners) listener.onFirstFrameRendered();
        });
    }

    private void notifyVideoSize(int width, int height, float frameRate) {
        mainHandler.post(() -> {
            for (PlayerListener listener : listeners) listener.onVideoSizeChanged(width, height, frameRate);
        });
    }

    private void notifyEnded() {
        mainHandler.post(() -> {
            for (PlayerListener listener : listeners) listener.onEnded();
        });
    }

    private void notifyError(PlaybackException error) {
        mainHandler.post(() -> {
            for (PlayerListener listener : listeners) listener.onError(error);
        });
    }
}
