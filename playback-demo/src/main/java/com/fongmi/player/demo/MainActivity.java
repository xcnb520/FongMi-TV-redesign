package com.fongmi.player.demo;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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

import java.util.ArrayDeque;
import java.util.Locale;

public final class MainActivity extends Activity {

    private static final String TAG = "PlaybackDemo";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PlayerCore player;
    private SurfacePlayerView surface;
    private EditText input;
    private TextView status;
    private TextView time;
    private TextView logView;
    private final ArrayDeque<String> recent = new ArrayDeque<>();
    private SeekBar seek;
    private boolean userSeeking;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (player != null) {
                long duration = player.getDuration();
                long position = player.getPosition();
                if (duration > 0L) {
                    seek.setMax((int) Math.min(Integer.MAX_VALUE, duration));
                    if (!userSeeking) seek.setProgress((int) Math.min(Integer.MAX_VALUE, position));
                }
                time.setText(format(position) + " / " + format(duration));
            }
            handler.postDelayed(this, 500L);
        }
    };

    private final PlayerListener listener = new PlayerListener() {
        @Override
        public void onPrepared(TrackInfo[] tracks, long durationMs) {
            status.setText("Ready · " + tracks.length + " tracks · " + durationMs / 1000L + "s");
            StringBuilder detail = new StringBuilder("PREPARED durationMs=").append(durationMs);
            for (TrackInfo track : tracks) detail.append(" [").append(track.getType()).append(' ').append(track.getMime()).append(']');
            log(detail.toString());
        }

        @Override
        public void onBufferingChanged(boolean buffering, int percent) {
            status.setText(buffering ? "Buffering " + percent + "%" : "Playing");
            log("BUFFERING " + buffering + " " + percent);
        }

        @Override
        public void onFirstFrameRendered() {
            status.setText("First frame rendered");
            log("FIRST_FRAME");
        }

        @Override
        public void onVideoSizeChanged(int width, int height, float frameRate) {
            status.setText(width + "x" + height + " @" + String.format(Locale.US, "%.2f", frameRate) + "fps");
            log("VIDEO_SIZE " + width + "x" + height + " fps=" + frameRate);
        }

        @Override
        public void onEnded() {
            status.setText("Ended");
            log("ENDED");
        }

        @Override
        public void onError(PlaybackException error) {
            status.setText("Error " + error.getCode() + ": " + error.getMessage());
            log("ERROR " + error.getCode() + ": " + error.getMessage());
            Log.e(TAG, "playback error", error);
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

        time = new TextView(this);
        time.setTextColor(Color.WHITE);
        time.setText("00:00 / 00:00");
        root.addView(time, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setText("Enter a URL and press Load");
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        logView = new TextView(this);
        logView.setTextColor(Color.GRAY);
        logView.setTextSize(10f);
        root.addView(logView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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

        log("activity created");
        handler.post(tick);
        String autoUrl = getIntent() == null ? null : getIntent().getStringExtra("url");
        if (!TextUtils.isEmpty(autoUrl)) {
            input.setText(autoUrl);
            handler.postDelayed(this::load, 800L);
        }
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
        log("LOAD " + url);
        player.setSource(source);
        player.prepare();
        player.play();
    }

    private void log(String message) {
        Log.i(TAG, message);
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> log(message));
            return;
        }
        recent.addFirst(message);
        while (recent.size() > 8) recent.removeLast();
        StringBuilder builder = new StringBuilder();
        for (String line : recent) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(line);
        }
        logView.setText(builder.toString());
    }

    private String format(long ms) {
        long totalSeconds = Math.max(0L, ms) / 1000L;
        return String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);
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
