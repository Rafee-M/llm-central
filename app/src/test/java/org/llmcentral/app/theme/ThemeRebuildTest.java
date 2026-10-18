package org.llmcentral.app.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * When a light ↔ dark flip rebuilds the screen ({@link ThemeRebuild}); VERIFICATION.md R4-2 / R4-3.
 * "applied" = the mode the screen was built in, "now" = what the theme setting gives now.
 */
public class ThemeRebuildTest {

    @Test
    public void resumeRebuildsWhenTheModeFlippedElsewhere() {
        // E.g. the drawer switched to Dark while this screen was in the background.
        assertTrue(ThemeRebuild.onResume(false, true, false));
    }

    @Test
    public void resumeDoesNothingWhenTheModeIsUnchanged() {
        assertFalse(ThemeRebuild.onResume(true, true, false));
    }

    @Test
    public void resumeWaitsWhileSettingsIsOpen() {
        // R4-2: the flip came from Settings (or happened while it was open). Rebuilding now would close Settings;
        // closing Settings rebuilds instead.
        assertFalse(ThemeRebuild.onResume(false, true, true));
    }

    @Test
    public void closingSettingsRebuildsAfterAFlip() {
        assertTrue(ThemeRebuild.onSettingsClosed(false, true, false));
    }

    @Test
    public void closingSettingsWithoutAFlipDoesNothing() {
        assertFalse(ThemeRebuild.onSettingsClosed(true, true, false));
    }

    @Test
    public void closingSettingsOfAScreenThatIsGoingAwayNeverRebuilds() {
        // R4-3: onDestroy closes Settings; Android runs the "closed" code later, when the rebuilt screen already
        // exists. Asking for a rebuild then would rebuild the new screen a second time.
        assertFalse(ThemeRebuild.onSettingsClosed(false, true, true));
    }
}
