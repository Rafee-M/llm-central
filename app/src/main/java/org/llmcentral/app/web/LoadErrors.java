package org.llmcentral.app.web;

/**
 * What the app does when a page can't be loaded (no connection, site down, …), VERIFICATION.md O-M4.
 *
 * <p>Until 2026-10-16 ChatGPT, Claude, DeepSeek and Z.ai (and duck.ai with "Offline chats" off) showed
 * Chromium's raw "Webpage not available" page. Now the tab shows a black screen with the app's icon and
 * "Tap to retry" (the same look as an unloaded tab, ui/UnloadedPlaceholder); duck.ai with "Offline chats"
 * on keeps showing the chats saved on the phone instead.</p>
 *
 * <p>Only the <i>main page</i> counts: when only a picture or script of a page fails, the page itself is
 * there. Plain Java, so LoadErrorsTest checks it on the computer.</p>
 */
public final class LoadErrors {

    public enum Reaction {
        /** Not the page itself (a part of it): nothing to do. */
        IGNORE,
        /** duck.ai with "Offline chats" on: show the saved chats (MainActivity.showOfflineChats). */
        SAVED_CHATS,
        /** Cover the tab with the retry screen until the next page starts loading. */
        RETRY_SCREEN
    }

    private LoadErrors() {
    }

    /**
     * @param mainFrame      the error is about the page itself (not a picture, script or frame inside it)
     * @param isDuck         the tab is duck.ai's
     * @param offlineChatsOn Settings → Data &amp; tools → "Offline chats (duck.ai)"
     */
    public static Reaction reaction(boolean mainFrame, boolean isDuck, boolean offlineChatsOn) {
        if (!mainFrame) {
            return Reaction.IGNORE;
        }
        return isDuck && offlineChatsOn ? Reaction.SAVED_CHATS : Reaction.RETRY_SCREEN;
    }
}
