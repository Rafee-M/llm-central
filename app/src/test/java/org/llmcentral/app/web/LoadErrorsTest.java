package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    // --- When the retry screen goes away (LoadErrors.TabState). WebView's order for a failed page:
    // onPageStarted → onReceivedError → onPageFinished, then onPageCommitVisible once its error page is drawn.

    @Test
    public void aFailedPageShowsTheRetryScreenEvenAfterItsErrorPageIsDrawn() {
        LoadErrors.TabState tab = new LoadErrors.TabState();
        tab.pageStarted();
        tab.failed("https://chatgpt.com/");
        assertFalse(tab.pageVisible()); // WebView's own error page under the black screen
        assertTrue(tab.showsRetry());
        assertEquals("https://chatgpt.com/", tab.failedUrl());
    }

    @Test
    public void theRetryScreenStaysWhileTheRetryIsUnderway() {
        // The review found this: hiding it at the tap (or at onPageStarted) showed Chromium's raw error page
        // for as long as the slow retry took.
        LoadErrors.TabState tab = new LoadErrors.TabState();
        tab.pageStarted();
        tab.failed("https://claude.ai/");
        tab.pageVisible();
        tab.pageStarted(); // the retry starts
        assertTrue(tab.showsRetry());
        tab.failed("https://claude.ai/"); // and fails again
        assertFalse(tab.pageVisible());
        assertTrue(tab.showsRetry());
    }

    @Test
    public void aPageShownWithoutAnErrorRemovesTheRetryScreen() {
        LoadErrors.TabState tab = new LoadErrors.TabState();
        tab.pageStarted();
        tab.failed("https://claude.ai/");
        tab.pageVisible();
        tab.pageStarted();
        assertTrue(tab.pageVisible()); // true = the screen must be updated
        assertFalse(tab.showsRetry());
        assertFalse(tab.pageVisible()); // nothing more to do
    }

    @Test
    public void aCancelledRetryKeepsTheScreen() {
        // A cancelled load (e.g. another tap) never becomes visible: the error page is still underneath.
        LoadErrors.TabState tab = new LoadErrors.TabState();
        tab.pageStarted();
        tab.failed("");
        tab.pageVisible();
        tab.pageStarted();
        assertTrue(tab.showsRetry());
        assertEquals("", tab.failedUrl());
    }
}
