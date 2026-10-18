package org.llmcentral.app.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContextWrapper;
import android.content.SharedPreferences;

import org.junit.Test;
import org.llmcentral.app.MapPrefs;

import java.util.HashMap;
import java.util.Map;

/** The "Offline chats (duck.ai)" setting ({@link OfflineChats}): off unless the user turns it on (owner, 2026-10-18). */
public class OfflineChatsTest {

    /** A Context whose settings file is a plain map. */
    private static final class MapContext extends ContextWrapper {
        final Map<String, Object> values = new HashMap<>();

        MapContext() {
            super(null);
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return new MapPrefs(values);
        }
    }

    @Test
    public void missingSettingMeansOff() {
        // A new install (or one that never touched the switch) saves no chats.
        assertFalse(OfflineChats.isEnabled(new MapContext()));
    }

    @Test
    public void theUsersChoiceIsKept() {
        MapContext context = new MapContext();
        OfflineChats.setEnabled(context, true);
        assertTrue(OfflineChats.isEnabled(context));
        assertEquals(Boolean.TRUE, context.values.get(OfflineChats.PREF));
        OfflineChats.setEnabled(context, false);
        assertFalse(OfflineChats.isEnabled(context));
    }
}
