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

    @Test
    public void returnsNewList() {
        assertNotSame(ALL, SwitcherModel.rows(ALL, loaded(), false, "duck"));
    }

    @Test
    public void cannotUnloadDuck() {
        assertFalse(SwitcherModel.canUnload(DUCK, true));
    }

    @Test
    public void cannotUnloadWhenNotLoaded() {
        assertFalse(SwitcherModel.canUnload(CHATGPT, false));
    }

    @Test
    public void canUnloadLoadedAccount() {
        assertTrue(SwitcherModel.canUnload(WORK, true));
    }

    @Test
    public void resetTextSizeOnlyWhenNotDefault() {
        assertFalse(SwitcherModel.canResetTextSize(100));
        assertTrue(SwitcherModel.canResetTextSize(120));
        assertTrue(SwitcherModel.canResetTextSize(50));
        assertEquals(100, SwitcherModel.DEFAULT_TEXT_ZOOM);
    }
}
