package org.llmcentral.app.ui;

import android.content.Context;

/**
 * Where the app's bar (layout/top_bar.xml: account chip, search, settings) sits, and the account menu's order
 * there: Settings → Appearance → "Top bar at the bottom" and "Reverse account order" (owner, 2026-10-31).
 *
 * <p><b>At the bottom</b> the bar is exactly the same view, moved under the page (both screens: the main screen
 * and the assistant drawer); only the chip's ▾ is turned to point up, because the account menu then opens
 * <i>above</i> the chip ({@link SwitcherPopup}).</p>
 *
 * <p><b>Reverse account order</b> (on by default) only matters at the bottom: the menu then lists its accounts
 * exactly backwards ({@link SwitcherModel#inMenuOrder}), so the first account is the lowest row, next to the bar
 * and your thumb, and newly added accounts are at the top.</p>
 *
 * <p>The settings are read again each time {@code MainActivity.applyTheme()} runs (start, every Appearance
 * change, and when a screen comes back to the front), so a change made in one screen reaches the other.</p>
 */
public final class BarPosition {

    /** Preference keys in {@code duck_assist_prefs}. Missing = bar at the top / reversed order on. */
    public static final String PREF_BOTTOM = "top_bar_bottom";
    public static final String PREF_REVERSE = "switcher_reverse_bottom";

    private BarPosition() {
        // Only static methods.
    }

    public static boolean isBottom(Context context) {
        return prefs(context).getBoolean(PREF_BOTTOM, false);
    }

    public static void setBottom(Context context, boolean bottom) {
        prefs(context).edit().putBoolean(PREF_BOTTOM, bottom).apply();
    }

    public static boolean reversesAccounts(Context context) {
        return prefs(context).getBoolean(PREF_REVERSE, true);
    }

    public static void setReversesAccounts(Context context, boolean reverse) {
        prefs(context).edit().putBoolean(PREF_REVERSE, reverse).apply();
    }

    private static android.content.SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("duck_assist_prefs", Context.MODE_PRIVATE);
    }
}
