package com.fongmi.player.core.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.PlaybackParams;
import android.os.Build;

import com.fongmi.player.core.PlaybackException;

import java.nio.ByteBuffer;

public final class AudioSink {

    private final int sampleRate;
    private final int channels;
    private final int encoding;
    private final AudioTrack track;

    public AudioSink(int sampleRate, int channels, int encoding) throws PlaybackException {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.encoding = encoding;
        int channelMask = channelMask(channels);
        int minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding);
        int bufferSize = minBuffer > 0 ? minBuffer * 4 : 256 * 1024;
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build();
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build();
        AudioTrack.Builder builder = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSize);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
        track = builder.build();
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            throw new PlaybackException(PlaybackException.AUDIO_INIT_FAILED, "AudioTrack init failed");
        }
    }

    public void play() {
        if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) track.play();
    }

    public void pause() {
        if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) track.pause();
    }

    public void write(ByteBuffer data, int size) {
        if (size <= 0) return;
        data.position(0);
        data.limit(size);
        track.write(data, size, AudioTrack.WRITE_BLOCKING);
    }

    public void flush() {
        try {
            track.pause();
            track.flush();
        } catch (Exception ignored) {
        }
    }

    public void setVolume(float volume) {
        track.setVolume(volume);
    }

    public void setSpeed(float speed) {
        PlaybackParams params = new PlaybackParams();
        params.setSpeed(speed);
        track.setPlaybackParams(params);
    }

    public long positionUs() {
        if (track == null) return 0L;
        try {
            AudioTimestamp timestamp = new AudioTimestamp();
            if (track.getTimestamp(timestamp)) return timestamp.framePosition * 1_000_000L / sampleRate;
        } catch (Exception ignored) {
        }
        long frames = track.getPlaybackHeadPosition() & 0xFFFFFFFFL;
        return frames * 1_000_000L / sampleRate;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public int getEncoding() {
        return encoding;
    }

    public void release() {
        try {
            track.stop();
        } catch (Exception ignored) {
        }
        track.release();
    }

    private static int channelMask(int channels) {
        if (channels == 1) return AudioFormat.CHANNEL_OUT_MONO;
        if (channels == 6) return AudioFormat.CHANNEL_OUT_5POINT1;
        return AudioFormat.CHANNEL_OUT_STEREO;
    }
}
