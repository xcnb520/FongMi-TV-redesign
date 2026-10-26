package com.fongmi.player.core;

import android.content.Context;

public final class PlayerCoreFactory {

    private PlayerCoreFactory() {
    }

    public static PlayerCore create(Context context) {
        return new NativePlayerCore(context.getApplicationContext());
    }
}
