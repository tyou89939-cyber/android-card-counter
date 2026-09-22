package com.example.cardcounter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Map;

public final class OverlayController {
    private final Context context;
    private final WindowManager windowManager;
    private TextView bar;

    public OverlayController(Context context) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean show() {
        if (bar != null || !Settings.canDrawOverlays(context)) {
            return bar != null;
        }

        bar = new TextView(context);
        bar.setTextColor(Color.WHITE);
        bar.setTextSize(14);
        bar.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        bar.setGravity(Gravity.CENTER);
        bar.setSingleLine(true);
        bar.setPadding(dp(12), 0, dp(12), 0);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(232, 15, 22, 34));
        background.setStroke(dp(1), Color.argb(180, 90, 108, 134));
        bar.setBackground(background);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                dp(42),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        try {
            windowManager.addView(bar, params);
            return true;
        } catch (WindowManager.BadTokenException | SecurityException error) {
            bar = null;
            return false;
        }
    }

    public void update(Map<Suit, Integer> counts) {
        if (bar == null) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (Suit suit : Suit.values()) {
            if (text.length() > 0) {
                text.append("    ");
            }
            text.append(suit.getSymbol()).append(' ').append(counts.get(suit));
        }
        bar.setText(text.toString());
    }

    public void hide() {
        if (bar != null) {
            try {
                windowManager.removeView(bar);
            } catch (IllegalArgumentException ignored) {
                // The window may already have been removed by the system.
            }
            bar = null;
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}