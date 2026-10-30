package org.llmcentral.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.llmcentral.app.account.Account;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** JVM unit tests for {@link SwitcherModel}: which accounts the account menu lists, which actions it allows. */
public class SwitcherModelTest {

    private static final Account DUCK = new Account("duck", "duck", "duck.ai", true);
    private static final Account CHATGPT = new Account("chatgpt", "chatgpt", "ChatGPT", true);
    private static final Account WORK = new Account("acct_2", "claude", "Work", true);
    private static final Account DEEPSEEK = new Account("acct_3", "deepseek", "DeepSeek", true);
    private static final List<Account> ALL = Arrays.asList(DUCK, CHATGPT, WORK, DEEPSEEK);

    private static Predicate<String> loaded(String... ids) {
        Set<String> set = new HashSet<>(Arrays.asList(ids));
        return set::contains;
    }

    private static List<String> ids(List<Account> accounts) {
        List<String> out = new ArrayList<>();
        for (Account a : accounts) {
            out.add(a.id);
        }
        return out;
    }

    @Test
    public void offReturnsAllInOrder() {
        assertEquals(Arrays.asList("duck", "chatgpt", "acct_2", "acct_3"),
                ids(SwitcherModel.rows(ALL, loaded("duck"), false, "duck")));
    }

    @Test
    public void activeOnlyReturnsOnlyLoaded() {
        assertEquals(Arrays.asList("duck", "acct_2"),
                ids(SwitcherModel.rows(ALL, loaded("duck", "acct_2"), true, "acct_2")));
    }

    @Test
    public void activeOnlyKeepsOrder() {
        // The loaded set's order must not matter: the menu keeps the user's account order.
        assertEquals(Arrays.asList("duck", "acct_3"),
                ids(SwitcherModel.rows(ALL, loaded("acct_3", "duck"), true, "duck")));
    }

    @Test
    public void activeOnlyKeepsCurrentEvenIfUnloaded() {
        // The account on screen was unloaded (or is being re-created): it must still be listed.
        assertEquals(Arrays.asList("duck", "chatgpt"),
                ids(SwitcherModel.rows(ALL, loaded("duck"), true, "chatgpt")));
    }

    @Test
    public void activeOnlyNeverEmptyWhenCurrentVisible() {
        List<Account> visible = Arrays.asList(CHATGPT, WORK);
        assertEquals(Arrays.asList("acct_2"), ids(SwitcherModel.rows(visible, loaded("acct_2"), true, "acct_2")));
    }

    @Test
    public void currentNotInVisibleIsNotAdded() {
        assertEquals(Arrays.asList("duck"),
                ids(SwitcherModel.rows(Arrays.asList(DUCK, CHATGPT), loaded("duck", "acct_9"), true, "acct_9")));
    }

    // Hidden accounts (owner, 2026-10-30): Settings → Manage accounts can still open them, so the menu lists a hidden
    // account at the bottom while it is loaded (its ✕ can unload it) or on screen ("Tap to load" stays reachable).
    private static final Account SECRET = new Account("acct_4", "chatgpt", "Secret", false);
    private static final Account OLD = new Account("acct_5", "zai", "Old", false);
    private static final List<Account> WITH_HIDDEN = Arrays.asList(DUCK, SECRET, CHATGPT, OLD, WORK);

    @Test
    public void hiddenAccountsAreLeftOutWhileNotLoaded() {
        assertEquals(Arrays.asList("duck", "chatgpt", "acct_2"),
                ids(SwitcherModel.rows(WITH_HIDDEN, loaded("duck"), false, "duck")));
    }

    @Test
    public void loadedHiddenAccountsComeLastInTheirOrder() {
        // Hidden accounts are listed after every shown one, even if their slot in the saved order is earlier.
        assertEquals(Arrays.asList("duck", "chatgpt", "acct_2", "acct_4", "acct_5"),
                ids(SwitcherModel.rows(WITH_HIDDEN, loaded("duck", "acct_5", "acct_4"), false, "duck")));
    }

    @Test
    public void hiddenAccountOnScreenStaysAfterItsUnload() {
        // Unloaded from the menu while on screen: still listed (and its black "Tap to load" screen stays)...
        assertEquals(Arrays.asList("duck", "chatgpt", "acct_2", "acct_4"),
                ids(SwitcherModel.rows(WITH_HIDDEN, loaded("duck"), false, "acct_4")));
        // ...until another account is on screen: then it is gone.
        assertEquals(Arrays.asList("duck", "chatgpt", "acct_2"),
                ids(SwitcherModel.rows(WITH_HIDDEN, loaded("duck"), false, "chatgpt")));
    }

    @Test
    public void activeOnlyListsLoadedHiddenAccountsToo() {
        assertEquals(Arrays.asList("duck", "acct_4"),
                ids(SwitcherModel.rows(WITH_HIDDEN, loaded("duck", "acct_4"), true, "duck")));
        assertEquals(Arrays.asList("duck", "acct_5"),
                ids(SwitcherModel.rows(WITH_HIDDEN, loaded("duck"), true, "acct_5")));
    }

    @Test
    public void returnsNewList() {
        assertNotSame(ALL, SwitcherModel.rows(ALL, loaded(), false, "duck"));
    }

    @Test
    public void unloadButtonOnlyOnLoadedRows() {
        // The ✕ sits where the green dot used to be: only accounts whose tab is loaded get one.
        assertEquals(SwitcherModel.UnloadButton.NONE, SwitcherModel.unloadButton(CHATGPT, false));
        assertEquals(SwitcherModel.UnloadButton.NONE, SwitcherModel.unloadButton(WORK, false));
    }

    @Test
    public void duckUnloadButtonWorksToo() {
        // Since 2026-10-27 (owner) the ✕ frees duck.ai's page as well (after its offline backup, MainActivity).
        assertEquals(SwitcherModel.UnloadButton.ENABLED, SwitcherModel.unloadButton(DUCK, true));
        assertEquals(SwitcherModel.UnloadButton.NONE, SwitcherModel.unloadButton(DUCK, false));
    }

    @Test
    public void loadedAccountCanBeUnloaded() {
        assertEquals(SwitcherModel.UnloadButton.ENABLED, SwitcherModel.unloadButton(WORK, true));
        assertEquals(SwitcherModel.UnloadButton.ENABLED, SwitcherModel.unloadButton(CHATGPT, true));
    }

    @Test
    public void resetTextSizeHiddenWhilePinchZoomsPage() {
        // "Pinch zooms the page" on: pinching no longer changes the text size, so the reset button is hidden.
        assertFalse(SwitcherModel.showsResetTextSize(true));
        assertTrue(SwitcherModel.showsResetTextSize(false));
    }

    @Test
    public void resetTextSizeOnlyWhenNotDefault() {
        assertFalse(SwitcherModel.canResetTextSize(100));
        assertTrue(SwitcherModel.canResetTextSize(120));
        assertTrue(SwitcherModel.canResetTextSize(50));
        assertEquals(100, SwitcherModel.DEFAULT_TEXT_ZOOM);
    }
}
