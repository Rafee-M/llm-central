package org.llmcentral.app.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.llmcentral.app.provider.Providers;

import java.util.List;

/** Checks the account rules of {@link AccountStore} (PROJECT.md R-ACC-1…6). Runs on the computer. */
public class AccountStoreTest {

    private static String ids(List<Account> accounts) {
        StringBuilder sb = new StringBuilder();
        for (Account a : accounts) {
            sb.append(sb.length() == 0 ? "" : ",").append(a.id);
        }
        return sb.toString();
    }

    @Test
    public void firstStartHasOneVisibleAccountPerProviderWithProviderIds() {
        AccountStore store = AccountStore.fromJson(null);
        assertEquals("duck,chatgpt,claude,deepseek,zai", ids(store.all()));
        assertEquals(5, store.visible().size());
        assertEquals("ChatGPT", store.find("chatgpt").name);
        // duck.ai stays in WebView's default profile (keeps existing chats); others get their own.
        assertNull(store.duck().profileName());
        assertEquals("acct_chatgpt", store.find("chatgpt").profileName());
    }

    @Test
    public void jsonRoundTripKeepsEverything() {
        AccountStore store = AccountStore.fromJson(null);
        store.add("claude", "Work", "claude-1");
        store.rename("chatgpt", "Personal");
        store.setVisible("zai", false);
        AccountStore copy = AccountStore.fromJson(store.toJson());
        assertEquals(store.toJson(), copy.toJson());
        assertEquals("Personal", copy.find("chatgpt").name);
        assertFalse(copy.find("zai").visible);
    }

    @Test
    public void addedAccountsStayGroupedWithTheirProvider() {
        AccountStore store = AccountStore.fromJson(null);
        store.add("chatgpt", "Work", "chatgpt-2");
        assertEquals("duck,chatgpt,chatgpt-2,claude,deepseek,zai", ids(store.all()));
        // Provider without accounts left: a new one goes back to the provider's usual position.
        store.remove("claude");
        store.add("claude", "", "claude-2");
        assertEquals("duck,chatgpt,chatgpt-2,claude-2,deepseek,zai", ids(store.all()));
    }

    @Test
    public void emptyNameGetsFirstFreeDefaultName() {
        AccountStore store = AccountStore.fromJson(null);
        assertEquals("ChatGPT 2", store.add("chatgpt", "   ", "c2").name);
        assertEquals("ChatGPT 3", store.add("chatgpt", null, "c3").name);
    }

    @Test
    public void duckAiCanNeitherBeAddedTwiceNorRemoved() {
        AccountStore store = AccountStore.fromJson(null);
        assertNull(store.add(Providers.DUCK_ID, "Second", "duck-2"));
        assertNull(store.remove(Providers.DUCK_ID));
        assertNull(store.add("unknown-provider", "X", "x-1"));
        assertNull("ids are never reused", store.add("chatgpt", "X", "claude"));
    }

    @Test
    public void lastVisibleAccountCannotBeHidden() {
        AccountStore store = AccountStore.fromJson(null);
        for (Account a : store.all()) {
            if (!a.isDuck()) {
                assertTrue(store.setVisible(a.id, false));
            }
        }
        assertFalse("R-ACC-6", store.setVisible(Providers.DUCK_ID, false));
        assertEquals(1, store.visible().size());
    }

    @Test
    public void removingTheOnlyVisibleAccountShowsDuckAgain() {
        AccountStore store = AccountStore.fromJson(null);
        for (Account a : store.all()) {
            if (!a.id.equals("claude")) {
                store.setVisible(a.id, false);
            }
        }
        assertEquals("claude", ids(store.visible()));
        assertNotNull(store.remove("claude"));
        assertEquals("duck", ids(store.visible()));
    }

    @Test
    public void namesAreCleanedAndLimited() {
        assertEquals("Work account", AccountStore.cleanName("  Work\n\t  account  "));
        assertEquals("", AccountStore.cleanName(null));
        assertEquals("", AccountStore.cleanName(" \u0000 "));
        // 45 emoji (each 2 Java chars) → cut to 40 emoji, never in the middle of one.
        StringBuilder emoji = new StringBuilder();
        for (int i = 0; i < 45; i++) {
            emoji.appendCodePoint(0x1F600);
        }
        String cut = AccountStore.cleanName(emoji.toString());
        assertEquals(AccountStore.MAX_NAME_LENGTH, cut.codePointCount(0, cut.length()));
        AccountStore store = AccountStore.fromJson(null);
        assertFalse("empty names are refused", store.rename("chatgpt", "   "));
    }

    @Test
    public void brokenOrHostileJsonIsRepaired() {
        // Unknown provider, duplicate id, second duck.ai, a non-duck account using the id "duck",
        // and no duck.ai at all → the store keeps the valid entries and adds duck.ai back.
        String json = "{\"v\":1,\"accounts\":["
                + "{\"id\":\"x\",\"provider\":\"nope\",\"name\":\"X\",\"visible\":true},"
                + "{\"id\":\"duck\",\"provider\":\"claude\",\"name\":\"Fake\",\"visible\":true},"
                + "{\"id\":\"c1\",\"provider\":\"claude\",\"name\":\"\",\"visible\":false},"
                + "{\"id\":\"c1\",\"provider\":\"claude\",\"name\":\"Dup\",\"visible\":true}"
                + "]}";
        AccountStore store = AccountStore.fromJson(json);
        assertEquals("duck,c1", ids(store.all()));
        assertEquals("Claude", store.find("c1").name);
        assertEquals("duck", ids(store.visible()));
        assertEquals("duck,chatgpt,claude,deepseek,zai", ids(AccountStore.fromJson("not json").all()));
    }

    @Test
    public void newIdsAreUniqueAndPrefixed() {
        String a = AccountStore.newId("claude");
        String b = AccountStore.newId("claude");
        assertTrue(a.startsWith("claude-"));
        assertFalse(a.equals(b));
    }
}
