package org.llmcentral.app.theme;

/**
 * When a light ↔ dark flip rebuilds the screen ({@code Activity.recreate()}). Plain Java so the rules are
 * unit-tested (ThemeRebuildTest). VERIFICATION.md R4-2 / R4-3.
 *
 * <p><b>Why a rebuild at all:</b> websites and Android's own dialogs only switch light/dark when the screen is
 * built again with the new mode (see {@link ThemeManager#nightOverride}). Colours alone are repainted at once.</p>
 *
 * <p><b>Why these rules:</b> a rebuild closes every window on the screen, Settings included. So while Settings is
 * open the rebuild waits until it closes. And the "Settings closed" code may run <i>after</i> the screen was
 * destroyed: Android delivers a dialog's dismiss listener later, as a posted message
 * ({@code Dialog.sendDismissMessage}), and a rebuild re-uses the screen's record, so a {@code recreate()}
 * asked for at that moment rebuilds the brand-new screen a second time (every tab reloaded twice).</p>
 */
public final class ThemeRebuild {

    private ThemeRebuild() {
    }

    /**
     * On returning to the screen ({@code onResume}).
     *
     * @param appliedNight   the mode the screen was built in
     * @param nightNow       the mode the theme setting gives now
     * @param settingsOpen   true while the Settings window is showing
     */
    public static boolean onResume(boolean appliedNight, boolean nightNow, boolean settingsOpen) {
        return appliedNight != nightNow && !settingsOpen;
    }

    /**
     * When the Settings window has closed.
     *
     * @param screenGoingAway true if the screen is finishing or already destroyed
     */
    public static boolean onSettingsClosed(boolean appliedNight, boolean nightNow, boolean screenGoingAway) {
        return appliedNight != nightNow && !screenGoingAway;
    }
}
