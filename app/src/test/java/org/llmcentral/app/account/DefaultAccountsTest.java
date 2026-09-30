package org.llmcentral.app.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.llmcentral.app.account.DefaultAccounts.Purpose;

/**
 * Checks which account opens for the assistant, Share / "Ask AI" and the app start
 * ({@link DefaultAccounts}, PROJECT.md R-AST-1…4). Runs on the computer.
 */
public class DefaultAccountsTest {

    private static AccountStore store() {
        AccountStore store = AccountStore.fromJson(null);
        store.add("chatgpt", "Work", "chatgpt-2");
        return store;
    }

    @Test
    public void intentActionsMapToPurposes() {
        assertEquals(Purpose.ASSISTANT, DefaultAccounts.purposeOf("android.intent.action.ASSIST"));
        assertEquals(Purpose.SHARE, DefaultAccounts.purposeOf("android.intent.action.SEND"));
        assertEquals(Purpose.SHARE, DefaultAccounts.purposeOf("android.intent.action.SEND_MULTIPLE"));
        assertEquals(Purpose.SHARE, DefaultAccounts.purposeOf("android.intent.action.PROCESS_TEXT"));
        assertEquals(Purpose.LINK, DefaultAccounts.purposeOf("android.intent.action.VIEW"));
        assertEquals(Purpose.LAUNCH, DefaultAccounts.purposeOf("android.intent.action.MAIN"));
        assertEquals(Purpose.LAUNCH, DefaultAccounts.purposeOf(null));
    }

    @Test
    public void defaultIsDuckAndMissingAccountsFallBackToDuck() {
        AccountStore store = store();
        assertEquals("duck", DefaultAccounts.resolve(store, null).id);        // never chosen
        assertEquals("chatgpt-2", DefaultAccounts.resolve(store, "chatgpt-2").id);
        store.remove("chatgpt-2");                                             // chosen, then removed
        assertEquals("duck", DefaultAccounts.resolve(store, "chatgpt-2").id);
    }

    @Test
    public void eachPurposeUsesItsOwnChoiceAndLinksGoToDuck() {
        AccountStore store = store();
        assertEquals("chatgpt-2", DefaultAccounts.target(store, Purpose.ASSISTANT, "chatgpt-2", "claude").id);
        assertEquals("claude", DefaultAccounts.target(store, Purpose.SHARE, "chatgpt-2", "claude").id);
        assertEquals("duck", DefaultAccounts.target(store, Purpose.LINK, "chatgpt-2", "claude").id);
    }

    @Test
    public void launchUsesTheChoiceElseTheLastUsedAccount() {
        AccountStore store = store();
        assertEquals("claude", DefaultAccounts.launchAccount(store, "", "claude").id);          // "last used"
        assertEquals("chatgpt-2", DefaultAccounts.launchAccount(store, "chatgpt-2", "claude").id);
        assertEquals("claude", DefaultAccounts.launchAccount(store, "gone-1", "claude").id);    // removed choice
        assertEquals("duck", DefaultAccounts.launchAccount(store, "", null).id);                // first start
        assertEquals("duck", DefaultAccounts.launchAccount(store, null, "gone-2").id);
    }

    @Test
    public void onlyExistingAccountsCanBeChosen() {
        AccountStore store = store();
        assertEquals("chatgpt-2", DefaultAccounts.validChoice(store, "chatgpt-2", false));
        assertNull(DefaultAccounts.validChoice(store, "nope", false));
        assertNull(DefaultAccounts.validChoice(store, null, true));
        // "" (last used) is only a valid choice for the app start.
        assertNull(DefaultAccounts.validChoice(store, "", false));
        assertEquals("", DefaultAccounts.validChoice(store, "", true));
    }
}
