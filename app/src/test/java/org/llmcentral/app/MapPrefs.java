package org.llmcentral.app;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Just enough of SharedPreferences for settings tests: booleans in a map, apply() writes at once. Shared by
 * TrackerBlockingTest and OfflineChatsTest (moved out of TrackerBlockingTest on 2026-10-18, unchanged).
 */
public final class MapPrefs implements SharedPreferences {
    private final Map<String, Object> values;

    public MapPrefs(Map<String, Object> values) {
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
