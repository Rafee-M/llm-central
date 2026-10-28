package org.llmcentral.app.web;

import org.llmcentral.app.account.Account;
import org.llmcentral.app.account.AccountStore;

/**
 * The rules for loading duck.ai's page only when it is needed ("duck.ai always active", plan
 * docs/superpowers/plans/2026-10-15-duck-lazy-loading.md).
 *
 * <p><b>Background:</b> the duck.ai tab is the WebView from the layout ({@code MainActivity.chatWebView}). Until
 * 2026-10-27 its page loaded at every app start, even when the user started on (and only used) another account:
 * start-up time, data, memory and battery for nothing. Now the WebView object still exists from the start (many
 * parts of the app refer to it), but its <i>page</i> loads only when duck.ai is shown or a duck.ai feature needs it
 * ({@link TabManager#isLoaded} tells which). The account menu's ✕ can also free the page again.</p>
 *
 * <p>The decisions that depend on "is duck.ai's page there?" are kept here, in plain Java, so DuckLoadingTest checks
 * them on the computer (MainActivity itself can't be unit-tested).</p>
 */
public final class DuckLoading {

    /**
     * How long the ✕ on duck.ai waits for the offline backup's answer before freeing the page anyway. The backup
     * reads every saved chat and picture inside duck.ai's page, which can take a few seconds on a slow phone.
     */
    public static final long UNLOAD_BACKUP_WAIT_MS = 10_000;

    private static final String DUCK_HOME = "https://duck.ai/";

    private DuckLoading() {
    }

    /**
     * May the offline backup (assets/js/dump_chats.js) run now? It reads duck.ai's saved chats from inside its page
     * (IndexedDB and localStorage) and answers through the message bridge, which exists only on duck.ai's own
     * address. On a page that isn't there it would find nothing.
     *
     * @param usedSinceDump  duck.ai was on screen since the last backup (round 6: no work when only others were used)
     * @param offlineChatsOn Settings → Data &amp; tools → "Offline chats" (storage/OfflineChats)
     * @param duckLoaded     duck.ai's page is loaded ({@link TabManager#isLoaded})
     * @param duckUrl        the duck.ai WebView's address
     */
    public static boolean mayDumpChats(boolean usedSinceDump, boolean offlineChatsOn, boolean duckLoaded,
                                       String duckUrl) {
        return usedSinceDump && offlineChatsOn && duckLoaded && duckUrl != null && duckUrl.startsWith("https://duck");
    }

    /**
     * The account to show when Android rebuilt the screen (e.g. after a light/dark switch). The screen saved the
     * account it showed; Chromium saves nothing for a WebView that never loaded a page (aw_contents.cc
     * {@code GetOpaqueState}), so "duck.ai's state came back" can't tell a rebuild from a fresh start any more.
     *
     * @param savedAccountId the id the screen saved, or null
     * @return that account, or duck.ai if nothing was saved or the account was removed meanwhile
     */
    public static Account accountAfterRebuild(AccountStore store, String savedAccountId) {
        Account saved = savedAccountId != null ? store.find(savedAccountId) : null;
        return saved != null ? saved : store.duck();
    }

    /**
     * duck.ai's page process died and its WebView was replaced: which page to load in the new one.
     *
     * @param duckWasLoaded duck.ai's page was loaded before (an unloaded duck.ai stays unloaded)
     * @param lastUrl       the dead WebView's address
     * @return null = load nothing; else the last web page, or duck.ai's start page for anything else
     */
    public static String reloadAfterCrash(boolean duckWasLoaded, String lastUrl) {
        if (!duckWasLoaded) {
            return null;
        }
        boolean isWebPage = lastUrl != null && (lastUrl.startsWith("https://") || lastUrl.startsWith("http://"));
        return isWebPage ? lastUrl : DUCK_HOME;
    }

    /**
     * Assistant gesture with "voice" on: must the app load duck.ai itself to start the voice chat?
     *
     * @param duckWasLoaded duck.ai's page was loaded before this request. If not, showing duck.ai has just loaded
     *                      it, and the voice chat starts when that page has finished loading, so a second load
     *                      would only cut the first one short.
     * @param duckUrl       the duck.ai WebView's address
     */
    public static boolean voiceNeedsOwnLoad(boolean duckWasLoaded, String duckUrl) {
        return duckWasLoaded && (duckUrl == null || !duckUrl.startsWith("https://duck.ai"));
    }

    /**
     * The ✕ on duck.ai's row: the page is freed once its offline backup has answered, or after
     * {@link #UNLOAD_BACKUP_WAIT_MS}, whichever comes first, and only once. If duck.ai is picked again meanwhile,
     * the unload is cancelled and the page stays.
     *
     * <p>Each ✕ gets a "ticket" number, so the timer of an earlier, cancelled ✕ can't finish a later one early.
     * Used on the main thread only.</p>
     */
    public static final class PendingUnload {
        private int ticket;
        private boolean pending;

        /** @return the ticket of the new pending unload (hand it to its timer) */
        public int begin() {
            pending = true;
            return ++ticket;
        }

        /** duck.ai was shown again: keep the page. */
        public void cancel() {
            pending = false;
        }

        /** @return true if the unload with this ticket is still pending: free the page now (the time is up). */
        public boolean finish(int ticket) {
            if (!pending || ticket != this.ticket) {
                return false;
            }
            pending = false;
            return true;
        }

        /** @return true if an unload was pending: free the page now (the backup has answered). */
        public boolean finishAny() {
            if (!pending) {
                return false;
            }
            pending = false;
            return true;
        }

        public boolean isPending() {
            return pending;
        }
    }
}
