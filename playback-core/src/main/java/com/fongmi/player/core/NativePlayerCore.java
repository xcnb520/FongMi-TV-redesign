package com.fongmi.player.core;

import android.content.Context;
import android.view.Surface;

import java.util.concurrent.CopyOnWriteArrayList;

public final class NativePlayerCore implements PlayerCore {

    private final Context context;
    private final CopyOnWriteArrayList<PlayerListener> listeners = new CopyOnWriteArrayList<>();
    private PlayerState state = PlayerState.IDLE;
    private MediaSource source;
    private Surface surface;
    private TrackInfo[] tracks = new TrackInfo[0];
    private long positionMs;
    private long durationMs;
    private float volume = 1f;
    private float speed = 1f;

    NativePlayerCore(Context context) {
        this.context = context;
    }

    @Override
    public synchronized void setSource(MediaSource source) {
        if (state == PlayerState.RELEASED) return;
        this.source = source;
        this.tracks = new TrackInfo[0];
        this.positionMs = 0L;
        this.durationMs = 0L;
        this.state = source == null ? PlayerState.IDLE : PlayerState.READY;
    }

    @Override
    public synchronized void prepare() {
        if (source == null || state == PlayerState.RELEASED) return;
        state = PlayerState.PREPARING;
        state = PlayerState.READY;
        notifyPrepared();
    }

    @Override
    public synchronized void play() {
        if (state == PlayerState.READY || state == PlayerState.BUFFERING || state == PlayerState.PAUSED) {
            state = PlayerState.PLAYING;
        }
    }

    @Override
    public synchronized void pause() {
        if (state == PlayerState.PLAYING || state == PlayerState.BUFFERING) {
            state = PlayerState.PAUSED;
        }
    }

    @Override
    public synchronized void stop() {
        if (state == PlayerState.RELEASED) return;
        state = PlayerState.IDLE;
        positionMs = 0L;
        durationMs = 0L;
        tracks = new TrackInfo[0];
    }

    @Override
    public synchronized void seekTo(long positionMs) {
        this.positionMs = Math.max(0L, positionMs);
    }

    @Override
    public synchronized void setSurface(Surface surface) {
        this.surface = surface;
    }

    @Override
    public synchronized void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
    }

    @Override
    public synchronized void setSpeed(float speed) {
        this.speed = Math.max(0.1f, Math.min(4f, speed));
    }

    @Override
    public synchronized void selectTrack(TrackType type, int index) {
        for (TrackInfo track : tracks) {
            if (track.getType() == type) track.setSelected(track.getIndex() == index);
        }
    }

    @Override
    public synchronized void release() {
        state = PlayerState.RELEASED;
        source = null;
        surface = null;
        tracks = new TrackInfo[0];
        listeners.clear();
    }

    @Override
    public synchronized PlayerState getState() {
        return state;
    }

    @Override
    public synchronized long getPosition() {
        return positionMs;
    }

    @Override
    public synchronized long getDuration() {
        return durationMs;
    }

    @Override
    public synchronized boolean isPlaying() {
        return state == PlayerState.PLAYING;
    }

    @Override
    public synchronized TrackInfo[] getTracks() {
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

    Context getContext() {
        return context;
    }

    Surface getSurface() {
        return surface;
    }

    float getVolume() {
        return volume;
    }

    float getSpeed() {
        return speed;
    }

    private void notifyPrepared() {
        for (PlayerListener listener : listeners) listener.onPrepared(tracks.clone(), durationMs);
    }
}
