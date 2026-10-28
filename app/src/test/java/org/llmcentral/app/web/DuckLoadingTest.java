package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.llmcentral.app.account.AccountStore;

/** When duck.ai's page is loaded, backed up and freed (plan 2026-10-15-duck-lazy-loading.md). */
public class DuckLoadingTest {

    // --- Offline backup (onStop and the ✕): never from a page that isn't there.

    @Test
    public void dumpNeedsLoadedDuck() {
        assertFalse(DuckLoading.mayDumpChats(true, true, false, "https://duck.ai/"));
    }

    @Test
    public void dumpNeedsDuckUrl() {
        assertFalse(DuckLoading.mayDumpChats(true, true, true, "about:blank"));
        assertFalse(DuckLoading.mayDumpChats(true, true, true, null));
    }

    @Test
    public void dumpWhenUsedAndOn() {
        assertTrue(DuckLoading.mayDumpChats(true, true, true, "https://duck.ai/chat"));
        assertFalse(DuckLoading.mayDumpChats(false, true, true, "https://duck.ai/chat"));
        assertFalse(DuckLoading.mayDumpChats(true, false, true, "https://duck.ai/chat"));
    }

    // --- A rebuilt screen (light/dark switch) reopens the account it showed, not "Open at start".

    @Test
    public void rebuiltScreenReopensItsAccount() {
        AccountStore store = AccountStore.fromJson(null);
        store.add("chatgpt", "ChatGPT", "chatgpt");
        assertEquals("chatgpt", DuckLoading.accountAfterRebuild(store, "chatgpt").id);
        assertSame(store.duck(), DuckLoading.accountAfterRebuild(store, null));
        assertSame(store.duck(), DuckLoading.accountAfterRebuild(store, "gone"));
    }

    // --- duck.ai's renderer died.

    @Test
    public void crashOfUnloadedDuckLoadsNothing() {
        assertNull(DuckLoading.reloadAfterCrash(false, "https://duck.ai/"));
        assertNull(DuckLoading.reloadAfterCrash(false, null));
    }

    @Test
    public void crashReloadsLastWebPage() {
        assertEquals("https://duck.ai/chat?x=1", DuckLoading.reloadAfterCrash(true, "https://duck.ai/chat?x=1"));
        assertEquals("https://duck.ai/", DuckLoading.reloadAfterCrash(true, "about:blank"));
        assertEquals("https://duck.ai/", DuckLoading.reloadAfterCrash(true, null));
    }

    // --- Assistant gesture with voice on.

    @Test
    public void assistantDuckNotLoadedNeedsNoSecondLoad() {
        // Not loaded before: showing duck.ai loads it once and onPageFinished starts the voice chat.
        assertFalse(DuckLoading.voiceNeedsOwnLoad(false, "https://duck.ai/"));
        assertFalse(DuckLoading.voiceNeedsOwnLoad(true, "https://duck.ai/"));
        assertTrue(DuckLoading.voiceNeedsOwnLoad(true, "about:blank"));
        assertTrue(DuckLoading.voiceNeedsOwnLoad(true, null));
    }

    // --- ✕ on duck.ai: the page is freed after the backup's answer or the time limit, whichever comes first.

    @Test
    public void backupAnswerFinishesUnloadOnce() {
        DuckLoading.PendingUnload unload = new DuckLoading.PendingUnload();
        int ticket = unload.begin();
        assertTrue(unload.isPending());
        assertTrue(unload.finishAny());
        assertFalse(unload.finishAny());
        assertFalse(unload.finish(ticket)); // the time limit comes later: nothing left to do
        assertFalse(unload.isPending());
    }

    @Test
    public void timeoutFinishesUnloadOnce() {
        DuckLoading.PendingUnload unload = new DuckLoading.PendingUnload();
        int ticket = unload.begin();
        assertTrue(unload.finish(ticket));
        assertFalse(unload.finishAny()); // a late answer: the page is already freed
    }

    @Test
    public void cancelledUnloadIgnoresLateAnswerAndTimeout() {
        // duck.ai was picked again while its backup ran: the page stays.
        DuckLoading.PendingUnload unload = new DuckLoading.PendingUnload();
        int ticket = unload.begin();
        unload.cancel();
        assertFalse(unload.isPending());
        assertFalse(unload.finishAny());
        assertFalse(unload.finish(ticket));
    }

    @Test
    public void newUnloadIgnoresOldTimeout() {
        DuckLoading.PendingUnload unload = new DuckLoading.PendingUnload();
        int first = unload.begin();
        unload.cancel();
        int second = unload.begin();
        assertFalse(unload.finish(first)); // the first ✕'s timer must not cut the second backup short
        assertTrue(unload.finish(second));
    }

    @Test
    public void nothingPendingFinishesNothing() {
        DuckLoading.PendingUnload unload = new DuckLoading.PendingUnload();
        assertFalse(unload.finishAny()); // an ordinary backup answer (onStop, viewer) frees nothing
        assertFalse(unload.isPending());
    }
}
