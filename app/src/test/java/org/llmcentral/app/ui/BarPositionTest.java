package org.llmcentral.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContextWrapper;
import android.content.SharedPreferences;

import org.junit.Test;
import org.llmcentral.app.MapPrefs;

import java.util.HashMap;
import java.util.Map;

/** The two Appearance switches of {@link BarPosition} (owner, 2026-10-31): their defaults and that a choice sticks. */
public class BarPositionTest {

    /** A Context whose settings file is a plain map (as in OfflineChatsTest). */
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
    public void barIsAtTheTopUntilTheUserMovesIt() {
        // Existing installs keep the bar where it always was.
        assertFalse(BarPosition.isBottom(new MapContext()));
    }

    @Test
    public void reverseAccountOrderIsOnByDefault() {
        // Owner: "On by default" (it only matters once the bar is at the bottom).
        assertTrue(BarPosition.reversesAccounts(new MapContext()));
    }

    @Test
    public void theUsersChoicesAreKept() {
        MapContext context = new MapContext();
        BarPosition.setBottom(context, true);
        BarPosition.setReversesAccounts(context, false);
        assertTrue(BarPosition.isBottom(context));
        assertFalse(BarPosition.reversesAccounts(context));
        assertEquals(Boolean.TRUE, context.values.get(BarPosition.PREF_BOTTOM));
        assertEquals(Boolean.FALSE, context.values.get(BarPosition.PREF_REVERSE));
        BarPosition.setBottom(context, false);
        assertFalse(BarPosition.isBottom(context));
    }
}
