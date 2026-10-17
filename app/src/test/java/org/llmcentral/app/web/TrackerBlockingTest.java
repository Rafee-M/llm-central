package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContextWrapper;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    /** Just enough of SharedPreferences for the setting: booleans in a map, apply() writes at once. */
    private static final class MapPrefs implements SharedPreferences {
        private final Map<String, Object> values;

        MapPrefs(Map<String, Object> values) {
            this.values = values;
        }

        @Override public boolean getBoolean(String key, boolean defValue) {
            Object v = values.get(key);
            return v instanceof Boolean ? (Boolean) v : defValue;
        }
        @Override public boolean contains(String key) { return values.containsKey(key); }
        @Override public Map<String, ?> getAll() { return values; }
        @Override public String getString(String key, String defValue) { return defValue; }
        @Override public Set<String> getStringSet(String key, Set<String> defValues) { return defValues; }
        @Override public int getInt(String key, int defValue) { return defValue; }
        @Override public long getLong(String key, long defValue) { return defValue; }
        @Override public float getFloat(String key, float defValue) { return defValue; }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }

        @Override public Editor edit() {
            final Map<String, Object> pending = new HashMap<>();
            return new Editor() {
                @Override public Editor putBoolean(String key, boolean value) { pending.put(key, value); return this; }
                @Override public Editor putString(String key, String value) { pending.put(key, value); return this; }
                @Override public Editor putStringSet(String key, Set<String> v) { pending.put(key, v); return this; }
                @Override public Editor putInt(String key, int value) { pending.put(key, value); return this; }
                @Override public Editor putLong(String key, long value) { pending.put(key, value); return this; }
                @Override public Editor putFloat(String key, float value) { pending.put(key, value); return this; }
                @Override public Editor remove(String key) { pending.put(key, null); return this; }
                @Override public Editor clear() { values.clear(); return this; }
                @Override public boolean commit() { apply(); return true; }
                @Override public void apply() { values.putAll(pending); }
            };
        }
    }
}
