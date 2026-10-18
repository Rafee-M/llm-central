package org.llmcentral.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Bringing Settings back after the screen was rebuilt or its page crashed ({@link SettingsRestore}, R4-4). */
public class SettingsRestoreTest {

    @Test
    public void closedSettingsSavesNothing() {
        assertNull(SettingsRestore.toSave(false, "appearance", null));
    }

    @Test
    public void openSettingsSavesItsSection() {
        assertEquals("appearance", SettingsRestore.toSave(true, "appearance", null));
    }

    @Test
    public void openSettingsWithoutASectionSavesTheTop() {
        // "" = open Settings at the top (null would mean "wasn't open").
        assertEquals("", SettingsRestore.toSave(true, null, null));
    }

    @Test
    public void aReopenStillWaitingIsSavedToo() {
        // Settings' page was stopped in the background and waits to reopen; if Android rebuilds the screen before the
        // user is back, the waiting reopen must survive the rebuild (re-check of R4-4).
        assertEquals("appearance", SettingsRestore.toSave(false, null, "appearance"));
        assertEquals("", SettingsRestore.toSave(false, null, ""));
    }

    @Test
    public void onlyPlainSectionNamesReachThePageAddress() {
        // The section is appended to settings.html#…; never anything else (it may come from a saved state).
        assertEquals("accounts", SettingsRestore.safeSection("accounts"));
        assertNull(SettingsRestore.safeSection(""));
        assertNull(SettingsRestore.safeSection(null));
        assertNull(SettingsRestore.safeSection("a#b"));
        assertNull(SettingsRestore.safeSection("../x"));
        assertNull(SettingsRestore.safeSection("Appearance"));
        assertNull(SettingsRestore.safeSection("abcdefghijklmnopqrstu")); // 21 letters
    }

    @Test
    public void afterACrashSettingsReopensOnlyOnce() {
        // A page that keeps crashing must not reopen forever.
        assertTrue(SettingsRestore.reopenAfterCrash(0));
        assertFalse(SettingsRestore.reopenAfterCrash(1));
    }
}
