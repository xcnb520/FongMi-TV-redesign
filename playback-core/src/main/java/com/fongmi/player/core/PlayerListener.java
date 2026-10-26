package com.fongmi.player.core;

public interface PlayerListener {

    default void onPrepared(TrackInfo[] tracks, long durationMs) {
    }

    default void onBufferingChanged(boolean buffering, int percent) {
    }

    default void onFirstFrameRendered() {
    }

    default void onVideoSizeChanged(int width, int height, float frameRate) {
    }

    default void onEnded() {
    }

    default void onError(PlaybackException error) {
    }
}
