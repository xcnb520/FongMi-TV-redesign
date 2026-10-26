package com.fongmi.player.demo;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.fongmi.player.core.DashSource;
import com.fongmi.player.core.HlsSource;
import com.fongmi.player.core.MediaSource;
import com.fongmi.player.core.PlaybackException;
import com.fongmi.player.core.PlayerCore;
import com.fongmi.player.core.PlayerCoreFactory;
import com.fongmi.player.core.PlayerListener;
import com.fongmi.player.core.ProgressiveSource;
import com.fongmi.player.core.TrackInfo;
import com.fongmi.player.core.view.SurfacePlayerView;

import java.util.Locale;

public final class MainActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PlayerCore player;
    private SurfacePlayerView surface;
    private EditText input;
    private TextView status;
    private SeekBar seek;
    private boolean userSeeking;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (player != null) {
                long duration = player.getDuration();
                if (duration > 0L) {
                    seek.setMax((int) duration);
                    if (!userSeeking) seek.setProgress((int) player.getPosition());
                }
            }
            handler.postDelayed(this, 500L);
        }
    };

    private final PlayerListener listener = new PlayerListener() {
        @Override
        public void onPrepared(TrackInfo[] tracks, long durationMs) {
            status.setText("Ready · " + tracks.length + " tracks · " + durationMs / 1000L + "s");
        }

        @Override
        public void onBufferingChanged(boolean buffering, int percent) {
            status.setText(buffering ? "Buffering " + percent + "%" : "Playing");
        }

        @Override
        public void onVideoSizeChanged(int width, int height, float frameRate) {
            status.setText(width + "x" + height + " @" + String.format(Locale.US, "%.2f", frameRate) + "fps");
        }

        @Override
        public void onEnded() {
            status.setText("Ended");
        }

        @Override
        public void onError(PlaybackException error) {
            status.setText("Error " + error.getCode() + ": " + error.getMessage());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8, 8, 12));
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        surface = new SurfacePlayerView(this);
        root.addView(surface, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setHint("MP4 / MPD / M3U8 URL");
        root.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        Button load = new Button(this);
        load.setText("Load");
        Button play = new Button(this);
        play.setText("Play");
        Button pause = new Button(this);
        pause.setText("Pause");
        Button stop = new Button(this);
        stop.setText("Stop");
        buttons.addView(load);
        buttons.addView(play);
        buttons.addView(pause);
        buttons.addView(stop);
        root.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        seek = new SeekBar(this);
        root.addView(seek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setText("Enter a URL and press Load");
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        player = PlayerCoreFactory.create(this);
        player.addListener(listener);
        surface.setPlayer(player);

        load.setOnClickListener(v -> load());
        play.setOnClickListener(v -> player.play());
        pause.setOnClickListener(v -> player.pause());
        stop.setOnClickListener(v -> player.stop());
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) player.seekTo(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
            }
        });

        handler.post(tick);
    }

    private void load() {
        String url = input.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            status.setText("Enter a URL first");
            return;
        }
        Uri uri = Uri.parse(url);
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.US);
        MediaSource source;
        if (path.endsWith(".mpd")) source = new DashSource(uri);
        else if (path.endsWith(".m3u8")) source = new HlsSource(uri);
        else source = new ProgressiveSource(uri);
        status.setText("Preparing...");
        player.setSource(source);
        player.prepare();
        player.play();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tick);
        if (player != null) {
            player.removeListener(listener);
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
