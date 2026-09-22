package com.example.cardcounter;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.EnumMap;
import java.util.Map;

public final class SuitCounts {
    private static final String PREFS = "card_counter";
    private static final String KEY_RUNNING = "capture_running";
    private final SharedPreferences preferences;

    public SuitCounts(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized int get(Suit suit) {
        return preferences.getInt(keyFor(suit), 13);
    }

    public synchronized void decrement(Suit suit) {
        int next = Math.max(0, get(suit) - 1);
        preferences.edit().putInt(keyFor(suit), next).apply();
    }

    public synchronized void reset() {
        SharedPreferences.Editor editor = preferences.edit();
        for (Suit suit : Suit.values()) {
            editor.putInt(keyFor(suit), 13);
        }
        editor.apply();
    }

    public synchronized Map<Suit, Integer> snapshot() {
        Map<Suit, Integer> result = new EnumMap<>(Suit.class);
        for (Suit suit : Suit.values()) {
            result.put(suit, get(suit));
        }
        return result;
    }

    public synchronized void setRunning(boolean running) {
        preferences.edit().putBoolean(KEY_RUNNING, running).apply();
    }

    public synchronized boolean isRunning() {
        return preferences.getBoolean(KEY_RUNNING, false);
    }

    private static String keyFor(Suit suit) {
        return "count_" + suit.name().toLowerCase();
    }
}