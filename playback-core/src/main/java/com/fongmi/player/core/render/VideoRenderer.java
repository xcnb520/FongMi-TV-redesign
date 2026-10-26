package com.fongmi.player.core.render;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Choreographer;

import com.fongmi.player.core.clock.MasterClock;
import com.fongmi.player.core.clock.SystemMasterClock;
import com.fongmi.player.core.decode.VideoDecoder;

import java.util.ArrayDeque;

public final class VideoRenderer {

    private static final String TAG = "PlayerCore";

    public interface Listener {

        void onFirstFrameRendered();

        void onFrameDropped();
    }

    private static final long EARLY_THRESHOLD_US = 12000L;
    private static final long DROP_THRESHOLD_US = 80000L;
    private static final int MAX_QUEUED = 6;

    private final VideoDecoder decoder;
    private final MasterClock clock;
    private final Listener listener;
    private final ArrayDeque<DecodedFrame> frames = new ArrayDeque<>();
    private final HandlerThread thread = new HandlerThread("playback-render");
    private Handler handler;
    private Choreographer choreographer;
    private volatile boolean released;
    private boolean firstFrameRendered;
    private volatile boolean playing;

    public VideoRenderer(VideoDecoder decoder, MasterClock clock, Listener listener) {
        this.decoder = decoder;
        this.clock = clock;
        this.listener = listener;
    }

    public void start() {
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(() -> {
            choreographer = Choreographer.getInstance();
            choreographer.postFrameCallback(frameCallback);
        });
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public void enqueue(DecodedFrame frame) {
        DecodedFrame dropped = null;
        synchronized (frames) {
            if (frames.size() >= MAX_QUEUED) dropped = frames.pollFirst();
            frames.addLast(frame);
        }
        if (dropped != null) {
            decoder.releaseFrame(dropped.getIndex(), false);
            listener.onFrameDropped();
        }
    }

    public void flush() {
        synchronized (frames) {
            for (DecodedFrame frame : frames) decoder.releaseFrame(frame.getIndex(), false);
            frames.clear();
        }
        firstFrameRendered = false;
    }

    public void release() {
        released = true;
        flush();
        if (handler != null && choreographer != null) {
            handler.post(() -> choreographer.removeFrameCallback(frameCallback));
        }
        thread.quitSafely();
    }

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (released) return;
            if (!playing) {
                choreographer.postFrameCallback(this);
                return;
            }
            DecodedFrame frame;
            synchronized (frames) {
                frame = frames.peekFirst();
            }
            if (frame == null) {
                choreographer.postFrameCallback(this);
                return;
            }
            long delayUs = frame.getPtsUs() - clock.positionUs();
            if (!firstFrameRendered && clock instanceof SystemMasterClock && Math.abs(delayUs) > EARLY_THRESHOLD_US) {
                ((SystemMasterClock) clock).syncTo(frame.getPtsUs());
                delayUs = frame.getPtsUs() - clock.positionUs();
                Log.i(TAG, "video clock synced to " + frame.getPtsUs());
            }
            if (delayUs > EARLY_THRESHOLD_US) {
                choreographer.postFrameCallback(this);
                return;
            }
            synchronized (frames) {
                frames.pollFirst();
            }
            boolean render = delayUs > -DROP_THRESHOLD_US;
            decoder.releaseFrame(frame.getIndex(), render);
            if (render) {
                if (!firstFrameRendered) {
                    firstFrameRendered = true;
                    Log.i(TAG, "FIRST_FRAME ptsUs=" + frame.getPtsUs());
                    listener.onFirstFrameRendered();
                }
            } else {
                listener.onFrameDropped();
            }
            choreographer.postFrameCallback(this);
        }
    };
}
