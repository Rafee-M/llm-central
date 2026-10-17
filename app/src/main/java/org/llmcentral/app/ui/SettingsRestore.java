package org.llmcentral.app.ui;

/**
 * Brings the Settings window back when Android rebuilt the screen or the Settings page crashed (VERIFICATION.md
 * R4-4). Plain Java so the rules are unit-tested (SettingsRestoreTest); MainActivity does the Android part.
 *
 * <p><b>Why it's needed:</b> Settings is a plain {@code Dialog}. Android saves and restores an activity's own
 * views, but not dialogs it shows, so after a rebuild (process ended in the background, a hardware keyboard
 * plugged in…) the screen came back without Settings. MainActivity now writes "Settings was open, at this
 * section" into the saved state ({@code onSaveInstanceState}) and reopens it in {@code onCreate}.
 * Docs: https://developer.android.com/guide/components/activities/activity-lifecycle#save-simple,-lightweight-ui-state-using-onsaveinstancestate</p>
 *
 * <p>All WebViews share one renderer process, which Android may stop while the app is in the background; the
 * Settings page then has to be closed (its WebView is dead). It is reopened once, when the user is back.</p>
 */
public final class SettingsRestore {

    /** Saved-state key: the open section, "" for the top; missing = Settings wasn't open. */
    public static final String KEY = "settings_open_section";

    private SettingsRestore() {
    }

    /** @return what to save: null if Settings is closed, else its section ("" for the top). */
    public static String toSave(boolean open, String section) {
        if (!open) {
            return null;
        }
        return section != null ? section : "";
    }

    /**
     * @return the section if it is a plain name (1–20 lowercase letters), else null. Only such a name may be
     *         appended to {@code settings.html#…}, whatever handed it over.
     */
    public static String safeSection(String section) {
        return section != null && section.matches("[a-z]{1,20}") ? section : null;
    }

    /** @return true if Settings may reopen after its page crashed ({@code reopened} = times already done). */
    public static boolean reopenAfterCrash(int reopened) {
        return reopened < 1;
    }
}
