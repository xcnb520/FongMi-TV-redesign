package com.fongmi.player.core.decode;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import com.fongmi.player.core.PlaybackException;
import com.fongmi.player.core.demux.Sample;
import com.fongmi.player.core.demux.SampleQueue;
import com.fongmi.player.core.render.DecodedFrame;
import com.fongmi.player.core.render.VideoRenderer;

import java.nio.ByteBuffer;

public final class VideoDecoder {

    private static final String TAG = "PlayerCore";

    public interface Listener {

        void onVideoSizeChanged(int width, int height, float frameRate);

        void onEndOfStream();

        void onError(PlaybackException error);
    }

    private final MediaFormat format;
    private final Surface surface;
    private final SampleQueue queue;
    private final Listener listener;
    private final HandlerThread thread = new HandlerThread("playback-video");
    private MediaCodec codec;
    private Handler handler;
    private VideoRenderer renderer;
    private volatile boolean eosQueued;
    private volatile boolean released;
    private boolean firstInput;
    private boolean firstOutput;

    public VideoDecoder(MediaFormat format, Surface surface, SampleQueue queue, Listener listener) {
        this.format = format;
        this.surface = surface;
        this.queue = queue;
        this.listener = listener;
    }

    public void setRenderer(VideoRenderer renderer) {
        this.renderer = renderer;
    }

    public void start() throws PlaybackException {
        try {
            String mime = format.getString(MediaFormat.KEY_MIME);
            thread.start();
            handler = new Handler(thread.getLooper());
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, surface, null, 0);
            codec.setCallback(callback, handler);
            codec.start();
            Log.i(TAG, "video decoder " + mime);
        } catch (Exception e) {
            throw new PlaybackException(PlaybackException.DECODER_INIT_FAILED, e.getMessage(), e);
        }
    }

    public void releaseFrame(int index, boolean render) {
        if (codec == null) return;
        try {
            codec.releaseOutputBuffer(index, render);
        } catch (Exception ignored) {
        }
    }

    public void flush() {
        if (codec == null || released) return;
        eosQueued = false;
        try {
            codec.flush();
        } catch (Exception ignored) {
        }
    }

    public void release() {
        released = true;
        if (codec != null) {
            try {
                codec.stop();
            } catch (Exception ignored) {
            }
            try {
                codec.release();
            } catch (Exception ignored) {
            }
            codec = null;
        }
        thread.quitSafely();
    }

    private final MediaCodec.Callback callback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(MediaCodec mediaCodec, int index) {
            if (eosQueued || released) return;
            try {
                Sample sample = queue.take();
                if (sample == null) {
                    if (queue.isEnded()) {
                        eosQueued = true;
                        mediaCodec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                    return;
                }
                ByteBuffer input = mediaCodec.getInputBuffer(index);
                if (input == null) {
                    sample.release();
                    return;
                }
                input.clear();
                input.put(sample.getData());
                int flags = (sample.getFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                if (!firstInput) {
                    firstInput = true;
                    Log.i(TAG, "first video sample ptsUs=" + sample.getPtsUs() + " size=" + sample.getSize());
                }
                mediaCodec.queueInputBuffer(index, 0, sample.getSize(), sample.getPtsUs(), flags);
                sample.release();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (!released) listener.onError(new PlaybackException(PlaybackException.DECODE_FAILED, e.getMessage(), e));
            }
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec mediaCodec, int index, MediaCodec.BufferInfo info) {
            if (released) return;
            if (!firstOutput && info.size > 0) {
                firstOutput = true;
                Log.i(TAG, "first video output ptsUs=" + info.presentationTimeUs + " size=" + info.size);
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) listener.onEndOfStream();
            if (info.size <= 0) {
                try {
                    mediaCodec.releaseOutputBuffer(index, false);
                } catch (Exception ignored) {
                }
                return;
            }
            VideoRenderer target = renderer;
            if (target == null) {
                try {
                    mediaCodec.releaseOutputBuffer(index, false);
                } catch (Exception ignored) {
                }
                return;
            }
            target.enqueue(new DecodedFrame(index, info.presentationTimeUs, info.size));
        }

        @Override
        public void onError(MediaCodec mediaCodec, MediaCodec.CodecException e) {
            if (released) return;
            Log.e(TAG, "video codec error", e);
            listener.onError(new PlaybackException(PlaybackException.DECODE_FAILED, e.getMessage(), e));
        }

        @Override
        public void onOutputFormatChanged(MediaCodec mediaCodec, MediaFormat outputFormat) {
            if (released) return;
            int width = outputFormat.containsKey(MediaFormat.KEY_WIDTH) ? outputFormat.getInteger(MediaFormat.KEY_WIDTH) : 0;
            int height = outputFormat.containsKey(MediaFormat.KEY_HEIGHT) ? outputFormat.getInteger(MediaFormat.KEY_HEIGHT) : 0;
            float frameRate = 0f;
            if (outputFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                try {
                    frameRate = outputFormat.getFloat(MediaFormat.KEY_FRAME_RATE);
                } catch (Exception e) {
                    frameRate = outputFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
                }
            }
            Log.i(TAG, "video output format " + width + "x" + height + " fps=" + frameRate);
            listener.onVideoSizeChanged(width, height, frameRate);
        }
    };
}
