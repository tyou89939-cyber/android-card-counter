package com.example.cardcounter;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int OVERLAY_REQUEST = 2001;
    private static final int CAPTURE_REQUEST = 2002;
    private static final int NOTIFICATION_REQUEST = 2003;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SuitCounts counts;
    private TextView status;
    private TextView countSummary;
    private Button startButton;
    private final Runnable refreshUi = new Runnable() {
        @Override
        public void run() {
            updateUi();
            handler.postDelayed(this, 800L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        counts = new SuitCounts(this);
        status = findViewById(R.id.status_text);
        countSummary = findViewById(R.id.count_summary);
        startButton = findViewById(R.id.start_button);

        findViewById(R.id.overlay_button).setOnClickListener(view -> requestOverlayPermission());
        startButton.setOnClickListener(view -> requestCapture());
        findViewById(R.id.stop_button).setOnClickListener(view -> stopCapture());
        findViewById(R.id.reset_button).setOnClickListener(view -> resetCounts());
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refreshUi);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshUi);
        super.onPause();
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_REQUEST);
        } else {
            status.setText(R.string.overlay_ready);
        }
    }

    private void requestCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            status.setText(R.string.overlay_required);
            requestOverlayPermission();
            return;
        }
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), CAPTURE_REQUEST);
    }

    private void stopCapture() {
        startService(new Intent(this, CardCounterService.class)
                .setAction(CardCounterService.ACTION_STOP));
        status.setText(R.string.status_stopped);
        updateUi();
    }

    private void resetCounts() {
        counts.reset();
        if (counts.isRunning()) {
            startService(new Intent(this, CardCounterService.class)
                    .setAction(CardCounterService.ACTION_RESET));
        }
        updateUi();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAPTURE_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                Intent serviceIntent = new Intent(this, CardCounterService.class)
                        .setAction(CardCounterService.ACTION_START)
                        .putExtra(CardCounterService.EXTRA_RESULT_CODE, resultCode)
                        .putExtra(CardCounterService.EXTRA_RESULT_DATA, data);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                status.setText(R.string.status_starting);
            } else {
                status.setText(R.string.capture_denied);
            }
        } else if (requestCode == OVERLAY_REQUEST) {
            status.setText(Settings.canDrawOverlays(this)
                    ? R.string.overlay_ready
                    : R.string.overlay_required);
        }
    }

    private void updateUi() {
        boolean running = counts.isRunning();
        startButton.setText(running ? R.string.button_running : R.string.button_start);
        status.setText(running ? R.string.status_running : R.string.status_stopped);
        StringBuilder summary = new StringBuilder();
        for (Suit suit : Suit.values()) {
            if (summary.length() > 0) {
                summary.append("   ");
            }
            summary.append(suit.getSymbol()).append(' ').append(counts.get(suit));
        }
        countSummary.setText(summary.toString());
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
        }
    }
}