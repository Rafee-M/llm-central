package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.llmcentral.app.provider.Providers;
import org.llmcentral.app.web.PopupWindows.Decision;

/** Checks which pages a sign-in pop-up may show ({@link PopupWindows#decide}, PROJECT.md §5.5). */
public class PopupWindowsTest {

    @Test
    public void providerAndLoginPagesLoadInThePopup() {
        assertEquals(Decision.LOAD, PopupWindows.decide("https://auth.openai.com/log-in", Providers.CHATGPT, false, true));
        assertEquals(Decision.LOAD, PopupWindows.decide("https://login.live.com/oauth", Providers.CHATGPT, false, false));
        assertEquals(Decision.LOAD, PopupWindows.decide("https://github.com/login/oauth", Providers.ZAI, false, true));
        assertEquals(Decision.LOAD, PopupWindows.decide("about:blank", Providers.CLAUDE, false, false));
    }

    @Test
    public void otherSitesGoToTheBrowserOnlyAfterATap() {
        assertEquals(Decision.BROWSER, PopupWindows.decide("https://example.com/", Providers.CLAUDE, false, true));
        assertEquals(Decision.BLOCK, PopupWindows.decide("https://ads.example/", Providers.CLAUDE, false, false));
        // Once the user is looking at the pop-up, its links to other sites open in the browser.
        assertEquals(Decision.BROWSER, PopupWindows.decide("https://example.com/", Providers.CLAUDE, true, false));
    }

    @Test
    public void lookAlikesAndOtherSchemesAreNotTheProvider() {
        assertEquals(Decision.BROWSER, PopupWindows.decide("https://evilclaude.ai/", Providers.CLAUDE, false, true));
        // Google sign-in is deliberately not an in-app host (Google blocks it in WebViews).
        assertEquals(Decision.BROWSER, PopupWindows.decide("https://accounts.google.com/o/oauth2", Providers.CHATGPT, false, true));
        assertEquals(Decision.BLOCK, PopupWindows.decide("intent://x#Intent;end", Providers.CHATGPT, false, true));
        assertEquals(Decision.BLOCK, PopupWindows.decide("javascript:alert(1)", Providers.CHATGPT, false, true));
        assertEquals(Decision.BLOCK, PopupWindows.decide("not a url", Providers.CHATGPT, false, true));
    }

    /**
     * Only a web page counts as "the pop-up showed something": a pop-up that only ever committed about:blank (or a
     * page-made blob:/data: document) and then turned into a download must still close its empty sheet.
     */
    @Test
    public void onlyHttpPagesCountAsShown() {
        assertTrue(PopupWindows.isWebPage("https://claude.ai/login"));
        assertTrue(PopupWindows.isWebPage("HTTP://example.com/"));
        assertFalse(PopupWindows.isWebPage("about:blank"));
        assertFalse(PopupWindows.isWebPage("blob:https://claude.ai/1234"));
        assertFalse(PopupWindows.isWebPage("data:text/plain,x"));
        assertFalse(PopupWindows.isWebPage(null));
        assertFalse(PopupWindows.isWebPage("::"));
    }
}
