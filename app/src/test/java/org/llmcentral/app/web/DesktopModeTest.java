package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Checks the "Desktop site" user agent and the saved per-account list ({@link DesktopMode}). */
public class DesktopModeTest {

    @Test
    public void phoneUserAgentBecomesDesktopButStaysAWebView() {
        assertEquals("Mozilla/5.0 (X11; Linux x86_64; wv) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Version/4.0 Chrome/140.0.7339.207 Safari/537.36",
                DesktopMode.desktopUserAgent("Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.240205.004; wv) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/140.0.7339.207 Mobile Safari/537.36"));
        // Android 16's shortened ("reduced") user agent.
        assertEquals("Mozilla/5.0 (X11; Linux x86_64; wv) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Version/4.0 Chrome/140.0.0.0 Safari/537.36",
                DesktopMode.desktopUserAgent("Mozilla/5.0 (Linux; Android 10; K; wv) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Version/4.0 Chrome/140.0.0.0 Mobile Safari/537.36"));
    }

    @Test
    public void unusualUserAgentsOnlyLoseTheMobileWord() {
        assertEquals("Something/1.0 Safari/537.36", DesktopMode.desktopUserAgent("Something/1.0 Mobile Safari/537.36"));
        assertEquals("", DesktopMode.desktopUserAgent(null));
    }

    @Test
    public void savedListAddsAndRemovesAccounts() {
        String json = DesktopMode.with(null, "chatgpt", true);
        json = DesktopMode.with(json, "claude-1", true);
        assertEquals("[\"chatgpt\",\"claude-1\"]", json);
        assertTrue(DesktopMode.ids(json).contains("claude-1"));
        json = DesktopMode.with(json, "chatgpt", false);
        assertEquals("[\"claude-1\"]", json);
        assertTrue(DesktopMode.ids("not json").isEmpty());
    }
}
