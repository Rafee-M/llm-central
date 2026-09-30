package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;

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
}
