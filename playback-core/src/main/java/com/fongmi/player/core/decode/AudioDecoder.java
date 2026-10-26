package com.fongmi.player.core.decode;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.fongmi.player.core.PlaybackException;
import com.fongmi.player.core.audio.AudioSink;
import com.fongmi.player.core.demux.Sample;
import com.fongmi.player.core.demux.SampleQueue;

import java.nio.ByteBuffer;

public final class AudioDecoder {

    private static final String TAG = "PlayerCore";

    public interface Listener {

        void onAudioFormatChanged(int sampleRate, int channels, int encoding);

        void onFirstPcm(long ptsUs);

        void onEndOfStream();

        void onError(PlaybackException error);
    }

    private final MediaFormat format;
    private final SampleQueue queue;
    private final Listener listener;
    private final HandlerThread thread = new HandlerThread("playback-audio");
    private MediaCodec codec;
    private Handler handler;
    private volatile AudioSink sink;
    private volatile long basePtsUs = -1L;
    private volatile boolean eosQueued;
    private volatile boolean released;
    private volatile boolean wantPlay;
    private volatile float volume = 1f;
    private volatile float speed = 1f;

    public AudioDecoder(MediaFormat format, SampleQueue queue, Listener listener) {
        this.format = format;
        this.queue = queue;
        this.listener = listener;
    }

    public void start() throws PlaybackException {
        try {
            String mime = format.getString(MediaFormat.KEY_MIME);
            thread.start();
            handler = new Handler(thread.getLooper());
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.setCallback(callback, handler);
            codec.start();
        } catch (Exception e) {
            throw new PlaybackException(PlaybackException.DECODER_INIT_FAILED, e.getMessage(), e);
        }
    }

    public void play() {
        wantPlay = true;
        AudioSink target = sink;
        if (target != null) target.play();
    }

    public void pause() {
        wantPlay = false;
        AudioSink target = sink;
        if (target != null) target.pause();
    }

    public void setVolume(float volume) {
        this.volume = volume;
        AudioSink target = sink;
        if (target != null) target.setVolume(volume);
    }

    public void setSpeed(float speed) {
        this.speed = speed;
        AudioSink target = sink;
        if (target != null) target.setSpeed(speed);
    }

    public long positionUs() {
        AudioSink target = sink;
        if (target == null || basePtsUs < 0) return 0L;
        return basePtsUs + target.positionUs();
    }

    public void flush() {
        basePtsUs = -1L;
        eosQueued = false;
        if (codec != null) {
            try {
                codec.flush();
            } catch (Exception ignored) {
            }
        }
        AudioSink target = sink;
        if (target != null) target.flush();
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
        AudioSink target = sink;
        if (target != null) {
            target.release();
            sink = null;
        }
        thread.quitSafely();
    }

    private void ensureSink(int sampleRate, int channels, int encoding) {
        AudioSink current = sink;
        if (current != null && current.getSampleRate() == sampleRate && current.getChannels() == channels && current.getEncoding() == encoding) return;
        if (current != null) current.release();
        try {
            AudioSink created = new AudioSink(sampleRate, channels, encoding);
            created.setVolume(volume);
            if (speed != 1f) created.setSpeed(speed);
            sink = created;
            if (wantPlay) created.play();
            listener.onAudioFormatChanged(sampleRate, channels, encoding);
            Log.i(TAG, "audio sink " + sampleRate + "Hz/" + channels + "ch wantPlay=" + wantPlay);
        } catch (PlaybackException e) {
            Log.e(TAG, "audio sink failed", e);
            listener.onError(e);
        }
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
                mediaCodec.queueInputBuffer(index, 0, sample.getSize(), sample.getPtsUs(), flags);
                sample.release();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                listener.onError(new PlaybackException(PlaybackException.DECODE_FAILED, e.getMessage(), e));
            }
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec mediaCodec, int index, MediaCodec.BufferInfo info) {
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) listener.onEndOfStream();
            if (info.size <= 0) {
                mediaCodec.releaseOutputBuffer(index, false);
                return;
            }
            ByteBuffer output = mediaCodec.getOutputBuffer(index);
            if (output == null) {
                mediaCodec.releaseOutputBuffer(index, false);
                return;
            }
            output.position(info.offset);
            output.limit(info.offset + info.size);
            if (basePtsUs < 0) {
                basePtsUs = Math.max(0L, info.presentationTimeUs);
                listener.onFirstPcm(basePtsUs);
                Log.i(TAG, "first pcm ptsUs=" + basePtsUs);
            }
            AudioSink target = sink;
            if (target != null) target.write(output, info.size);
            mediaCodec.releaseOutputBuffer(index, false);
        }

        @Override
        public void onError(MediaCodec mediaCodec, MediaCodec.CodecException e) {
            Log.e(TAG, "audio codec error", e);
            listener.onError(new PlaybackException(PlaybackException.DECODE_FAILED, e.getMessage(), e));
        }

        @Override
        public void onOutputFormatChanged(MediaCodec mediaCodec, MediaFormat outputFormat) {
            int sampleRate = outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int channels = outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            int encoding = AudioFormat.ENCODING_PCM_16BIT;
            if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
            Log.i(TAG, "audio output format " + sampleRate + "Hz/" + channels + "ch enc=" + encoding);
            ensureSink(sampleRate, channels, encoding);
        }
    };
}
