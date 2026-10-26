package com.fongmi.player.core.audio;

import com.fongmi.player.core.clock.MasterClock;
import com.fongmi.player.core.decode.AudioDecoder;

public final class AudioClock implements MasterClock {

    private final AudioDecoder decoder;

    public AudioClock(AudioDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public long positionUs() {
        return decoder.positionUs();
    }
}
