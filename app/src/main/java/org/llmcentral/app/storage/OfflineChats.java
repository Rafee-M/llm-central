package org.llmcentral.app.storage;

import android.content.Context;

/**
 * The setting "Offline chats (duck.ai)" (Settings → Data &amp; tools), <b>off by default</b> since 2026-10-18 (owner:
 * nothing is copied to the phone unless the user asks for it; it also saves the backup's work each time the app goes to
 * the background). An install that never touched the switch is off too: a copy saved earlier stays on the phone,
 * unused, until the switch is turned on (shown again) or on and off (deleted).
 *
 * <p><b>On</b>: when the app goes to the background after duck.ai was used, it copies
 * duck.ai's chats to the phone ({@code dump_chats.js} → {@code ChatDatabaseHelper}). They can be read
 * in "Saved chats", and they stand in for duck.ai when it can't load (no internet, crashes).</p>
 *
 * <p><b>Off</b>: nothing is copied, the copy already on the phone is deleted, "Saved chats" is
 * greyed out and a failed duck.ai load shows WebView's normal error page. duck.ai's page also stops
 * holding on to its files for the backup: {@code blob_download_patch.js} then forgets a file 60 s
 * after the page lets go of it, like on the other providers. Chats inside duck.ai itself (its own
 * website storage in the WebView) are not touched.</p>
 *
 * <p>The page learns the setting through one variable, {@code window.__llmcOfflineChats}, which the
 * app sets before {@code blob_download_patch.js} runs and again when the setting changes.</p>
 */
public final class OfflineChats {

    /** Preference key in {@code duck_assist_prefs}; missing = off (until 2026-10-18 missing meant on). */
    public static final String PREF = "offline_chats";

    private OfflineChats() {
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences("duck_assist_prefs", Context.MODE_PRIVATE)
                .getBoolean(PREF, false);
    }

    public static void setEnabled(Context context, boolean on) {
        // apply() changes the in-memory value at once (later isEnabled calls see it) and writes to
        // disk in the background.
        context.getSharedPreferences("duck_assist_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean(PREF, on).apply();
    }

    /** Run in the duck.ai page right before {@code blob_download_patch.js}. */
    public static String pageFlag(boolean on) {
        return "window.__llmcOfflineChats=" + on + ";";
    }

    /** Run in an already loaded duck.ai page when the setting changes (off also forgets its kept files). */
    public static String pageSwitch(boolean on) {
        return "window.__duckSetOfflineChats&&window.__duckSetOfflineChats(" + on + ");";
    }
}
