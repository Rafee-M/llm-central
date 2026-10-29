package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContextWrapper;
import android.content.SharedPreferences;

import org.junit.Test;
import org.llmcentral.app.MapPrefs;
import org.llmcentral.app.provider.ProviderSpec;
import org.llmcentral.app.provider.Providers;

import java.util.HashMap;
import java.util.Map;

/**
 * Settings → Data &amp; tools → "Allow third-party cookies" ({@link ThirdPartyCookies}): blocked unless the user turns
 * it on (owner, 2026-10-29), and never for duck.ai.
 */
public class ThirdPartyCookiesTest {

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
    public void missingSettingMeansBlocked() {
        // A new install (or one that never touched the switch) blocks third-party cookies.
        assertFalse(ThirdPartyCookies.isOn(new MapContext()));
    }

    @Test
    public void theUsersChoiceIsKept() {
        MapContext context = new MapContext();
        ThirdPartyCookies.setOn(context, true);
        assertTrue(ThirdPartyCookies.isOn(context));
        assertEquals(Boolean.TRUE, context.values.get(ThirdPartyCookies.PREF));
        ThirdPartyCookies.setOn(context, false);
        assertFalse(ThirdPartyCookies.isOn(context));
    }

    @Test
    public void duckAiIsAlwaysBlocked() {
        assertFalse(ThirdPartyCookies.allowed(Providers.DUCK, true));
        assertFalse(ThirdPartyCookies.allowed(Providers.DUCK, false));
    }

    @Test
    public void otherProvidersFollowTheSetting() {
        for (ProviderSpec provider : new ProviderSpec[] {
                Providers.CHATGPT, Providers.CLAUDE, Providers.DEEPSEEK, Providers.ZAI}) {
            assertTrue(provider.id, ThirdPartyCookies.allowed(provider, true));
            assertFalse(provider.id, ThirdPartyCookies.allowed(provider, false));
        }
    }
}
