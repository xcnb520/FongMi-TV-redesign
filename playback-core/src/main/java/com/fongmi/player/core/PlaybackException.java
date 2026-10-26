package com.fongmi.player.core;

public final class PlaybackException extends Exception {

    public static final int SOURCE_UNSUPPORTED = 1001;
    public static final int DEMUX_FAILED = 1002;
    public static final int DECODER_INIT_FAILED = 1003;
    public static final int DECODE_FAILED = 1004;
    public static final int RENDER_FAILED = 1005;
    public static final int AUDIO_INIT_FAILED = 1006;
    public static final int NETWORK_FAILED = 1007;
    public static final int UNKNOWN = 1099;

    private final int code;

    public PlaybackException(int code, String message) {
        super(message);
        this.code = code;
    }

    public PlaybackException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
