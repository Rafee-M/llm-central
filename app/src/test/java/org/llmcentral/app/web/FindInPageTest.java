package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

/**
 * Find in page ({@link FindInPage}): which providers get their own site search (Ctrl+F sent to the page), which key
 * presses mean "find", and the "3/12" count text of the app's find bar.
 */
public class FindInPageTest {

    @Test
    public void onlyClaudeUsesItsSiteSearch() {
        // Owner, 2026-10-29: Ctrl+F on claude.ai opens Claude's own search; the others get the app's bar.
        assertTrue(FindInPage.usesSiteSearch("claude"));
        assertFalse(FindInPage.usesSiteSearch("duck"));
        assertFalse(FindInPage.usesSiteSearch("chatgpt"));
        assertFalse(FindInPage.usesSiteSearch("deepseek"));
        assertFalse(FindInPage.usesSiteSearch("zai"));
    }

    @Test
    public void unknownProvidersGetTheAppsBar() {
        // A provider added later (e.g. Gemini, built in a parallel session) must work at once: the app's bar is the
        // safe default until someone has checked the site's own Ctrl+F on a phone.
        assertFalse(FindInPage.usesSiteSearch("gemini"));
        assertFalse(FindInPage.usesSiteSearch(null));
    }

    @Test
    public void ctrlFIsTheFindShortcut() {
        assertTrue(FindInPage.isFindShortcut(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON));
        assertTrue(FindInPage.isFindShortcut(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F,
                KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON));
    }

    @Test
    public void otherKeysAreNotTheFindShortcut() {
        assertFalse(FindInPage.isFindShortcut(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F, 0));            // plain f
        assertFalse(FindInPage.isFindShortcut(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON));
        assertFalse(FindInPage.isFindShortcut(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_G, KeyEvent.META_CTRL_ON));
        assertFalse(FindInPage.isFindShortcut(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F,
                KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON));                                         // Ctrl+Shift+F
        assertFalse(FindInPage.isFindShortcut(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F,
                KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON));                                           // Ctrl+Alt+F
    }

    @Test
    public void highlightCssColoursAllMatchesAndTheCurrentOne() {
        // Chromium's find highlights can be styled by the page since WebView 144 (CSS ::search-text): every match in
        // the softer accent, the current one in the full accent, each with its matching text colour.
        assertEquals("::search-text{background-color:#AABBCC;color:#112233}"
                        + "::search-text:current{background-color:#445566;color:#FFFFFF}",
                FindInPage.highlightCss("#AABBCC", "#112233", "#445566", "#FFFFFF"));
    }

    @Test
    public void highlightCssOnlyAcceptsColours() {
        // The text ends up inside the page's style sheet: anything but #RRGGBB is refused, so nothing else can get in.
        assertEquals("", FindInPage.highlightCss("#AABBCC", "red;}body{display:none", "#445566", "#FFFFFF"));
        assertEquals("", FindInPage.highlightCss(null, "#112233", "#445566", "#FFFFFF"));
    }

    @Test
    public void countTextShowsCurrentOfTotal() {
        // WebView's FindListener counts the active match from 0; people count from 1.
        assertEquals("1/12", FindInPage.countText(0, 12, true));
        assertEquals("3/12", FindInPage.countText(2, 12, false)); // still counting: the partial total is shown
        assertEquals("0/0", FindInPage.countText(0, 0, true));    // nothing found
        assertEquals("", FindInPage.countText(0, 0, false));      // nothing found yet, still counting
    }
}
