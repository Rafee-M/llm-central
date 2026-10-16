package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** What the app does when a page can't be loaded (VERIFICATION.md O-M4). */
public class LoadErrorsTest {

    @Test
    public void partsOfAPageAreIgnored() {
        // An image or script of the page failed: the page itself is there; nothing to cover.
        assertEquals(LoadErrors.Reaction.IGNORE, LoadErrors.reaction(false, false, true));
        assertEquals(LoadErrors.Reaction.IGNORE, LoadErrors.reaction(false, true, true));
    }

    @Test
    public void duckWithOfflineChatsShowsTheSavedChats() {
        assertEquals(LoadErrors.Reaction.SAVED_CHATS, LoadErrors.reaction(true, true, true));
    }

    @Test
    public void everythingElseShowsTheRetryScreen() {
        assertEquals(LoadErrors.Reaction.RETRY_SCREEN, LoadErrors.reaction(true, false, true));
        assertEquals(LoadErrors.Reaction.RETRY_SCREEN, LoadErrors.reaction(true, false, false));
        // duck.ai with "Offline chats" off used to show WebView's raw "Webpage not available" page.
        assertEquals(LoadErrors.Reaction.RETRY_SCREEN, LoadErrors.reaction(true, true, false));
    }
}
