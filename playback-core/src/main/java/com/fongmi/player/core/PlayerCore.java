package com.fongmi.player.core;

import android.view.Surface;

public interface PlayerCore {

    void setSource(MediaSource source);

    void prepare();

    void play();

    void pause();

    void stop();

    void seekTo(long positionMs);

    void setSurface(Surface surface);

    void setVolume(float volume);

    void setSpeed(float speed);

    void selectTrack(TrackType type, int index);

    void release();

    PlayerState getState();

    long getPosition();

    long getDuration();

    boolean isPlaying();

    TrackInfo[] getTracks();

    void addListener(PlayerListener listener);

    void removeListener(PlayerListener listener);

    default void setQuality(int index) {
    }

    default int getQuality() {
        return -1;
    }
}
