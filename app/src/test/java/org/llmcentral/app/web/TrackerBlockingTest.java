package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContextWrapper;
import android.content.SharedPreferences;

import org.junit.Before;
import org.llmcentral.app.MapPrefs;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * The "Block trackers" setting is asked for on every request a page makes (WebView background threads), so it is
 * read from the settings file once and then kept in memory; Settings' switch updates both. Android's
 * {@code getSharedPreferences} takes a lock shared by the whole app (AOSP {@code ContextImpl}), which a per-request
 * read would take thousands of times. These tests use a stand-in Context that counts how often the file is opened.
 */
public class TrackerBlockingTest {

    /** A Context whose settings file is a plain map; counts getSharedPreferences calls. */
    private static final class CountingContext extends ContextWrapper {
        final Map<String, Object> values = new HashMap<>();
        int opened;

        CountingContext() {
            super(null);
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            opened++;
            return new MapPrefs(values);
        }
    }

    @Before
    public void forgetTheKeptSetting() throws Exception {
        // The kept value is static (one per app process); each test starts as a freshly started app.
        Field field = TrackerBlocking.class.getDeclaredField("enabled");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    public void missingSettingMeansOn() {
        assertTrue(TrackerBlocking.isEnabled(new CountingContext()));
    }

    @Test
    public void settingIsReadFromTheFileOnlyOnce() {
        CountingContext context = new CountingContext();
        context.values.put(TrackerBlocking.PREF, false);
        assertFalse(TrackerBlocking.isEnabled(context));
        assertFalse(TrackerBlocking.isEnabled(context));
        assertFalse(TrackerBlocking.isEnabled(context));
        assertEquals(1, context.opened);
    }

    @Test
    public void switchIsSeenAtOnceAndSaved() {
        CountingContext context = new CountingContext();
        assertTrue(TrackerBlocking.isEnabled(context));          // opened 1: first read
        TrackerBlocking.setEnabled(context, false);               // opened 2: the write
        assertFalse(TrackerBlocking.isEnabled(context));         // from memory
        assertEquals(2, context.opened);
        assertEquals(Boolean.FALSE, context.values.get(TrackerBlocking.PREF)); // survives a restart
        TrackerBlocking.setEnabled(context, true);
        assertTrue(TrackerBlocking.isEnabled(context));
    }
}
