package com.fongmi.player.core.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.fongmi.player.core.PlayerCore;

public final class SurfacePlayerView extends SurfaceView implements SurfaceHolder.Callback {

    private PlayerCore player;

    public SurfacePlayerView(Context context) {
        this(context, null);
    }

    public SurfacePlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);
        setKeepScreenOn(true);
    }

    public void setPlayer(PlayerCore player) {
        this.player = player;
        if (player != null && getHolder().getSurface() != null && getHolder().getSurface().isValid()) {
            player.setSurface(getHolder().getSurface());
        }
    }

    public PlayerCore getPlayer() {
        return player;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (player != null) player.setSurface(holder.getSurface());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (player != null) player.setSurface(null);
    }
}
