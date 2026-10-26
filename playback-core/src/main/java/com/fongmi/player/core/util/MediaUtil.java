package com.fongmi.player.core.util;

public final class MediaUtil {

    private MediaUtil() {
    }

    public static boolean isVideo(String mime) {
        return mime != null && mime.startsWith("video/");
    }

    public static boolean isAudio(String mime) {
        return mime != null && mime.startsWith("audio/");
    }

    public static long usToMs(long us) {
        return us / 1000L;
    }

    public static long msToUs(long ms) {
        return ms * 1000L;
    }
}
