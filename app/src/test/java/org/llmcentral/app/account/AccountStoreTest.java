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
        assertEquals("duck,chatgpt,claude,deepseek,zai,gemini", ids(store.all()));
        assertEquals(org.llmcentral.app.provider.Providers.ALL.size(), store.visible().size()); // one per provider
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
    public void addedAccountsGoToTheBottom() {
        // The list order is the switcher menu's order; a new account is added at its end (owner, 2026-10-15).
        AccountStore store = AccountStore.fromJson(null);
        store.add("chatgpt", "Work", "chatgpt-2");
        assertEquals("duck,chatgpt,claude,deepseek,zai,gemini,chatgpt-2", ids(store.all()));
        store.remove("claude");
        store.add("claude", "", "claude-2");
        assertEquals("duck,chatgpt,deepseek,zai,gemini,chatgpt-2,claude-2", ids(store.all()));
    }

    @Test
    public void showingAgainMovesToTheBottom() {
        AccountStore store = AccountStore.fromJson(null);
        assertTrue(store.setVisible("chatgpt", false));
        assertTrue(store.setVisible("chatgpt", true));
        assertEquals("duck,claude,deepseek,zai,gemini,chatgpt", ids(store.visible()));
        // Switching on an account that is already shown changes nothing.
        assertTrue(store.setVisible("claude", true));
        assertEquals("duck,claude,deepseek,zai,gemini,chatgpt", ids(store.visible()));
    }

    @Test
    public void hidingKeepsTheOthersInPlace() {
        AccountStore store = AccountStore.fromJson(null);
        assertTrue(store.setVisible("claude", false));
        assertEquals("duck,chatgpt,claude,deepseek,zai,gemini", ids(store.all()));
        assertEquals("duck,chatgpt,deepseek,zai,gemini", ids(store.visible()));
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
        assertEquals("duck,chatgpt,claude,deepseek,zai,gemini", ids(AccountStore.fromJson("not json").all()));
    }

    @Test
    public void newIdsAreUniqueAndPrefixed() {
        String a = AccountStore.newId("claude");
        String b = AccountStore.newId("claude");
        assertTrue(a.startsWith("claude-"));
        assertFalse(a.equals(b));
    }

    @Test
    public void reorderMixesProvidersAndKeepsHiddenSlots() {
        AccountStore store = AccountStore.fromJson(null);
        store.add("chatgpt", "Work", "chatgpt-2");
        store.setVisible("deepseek", false);
        // Visible: duck,chatgpt,claude,zai,gemini,chatgpt-2 → any order, also across providers.
        assertTrue(store.reorder(java.util.Arrays.asList("chatgpt-2", "claude", "duck", "zai", "gemini", "chatgpt")));
        assertEquals("chatgpt-2,claude,duck,zai,gemini,chatgpt", ids(store.visible()));
        // The hidden account keeps its place in the full list (slot 4 of 7).
        assertEquals("chatgpt-2,claude,duck,deepseek,zai,gemini,chatgpt", ids(store.all()));
        // Saved: the new order survives a save and load.
        assertEquals(ids(store.all()), ids(AccountStore.fromJson(store.toJson()).all()));
    }

    @Test
    public void reorderRefusesAnythingButTheVisibleSet() {
        AccountStore store = AccountStore.fromJson(null);
        store.setVisible("zai", false);
        String before = ids(store.all());
        assertFalse("missing one", store.reorder(java.util.Arrays.asList("duck", "chatgpt", "claude")));
        assertFalse("hidden one", store.reorder(java.util.Arrays.asList("duck", "chatgpt", "claude", "deepseek", "zai")));
        // Same length, but the hidden "zai" instead of the visible "deepseek": would duplicate one account and lose another.
        assertFalse("hidden instead of visible", store.reorder(java.util.Arrays.asList("duck", "chatgpt", "claude", "zai")));
        assertFalse("duplicate", store.reorder(java.util.Arrays.asList("duck", "chatgpt", "claude", "claude")));
        assertFalse("unknown", store.reorder(java.util.Arrays.asList("duck", "chatgpt", "claude", "nope")));
        assertFalse("null list", store.reorder(null));
        assertFalse("null id", store.reorder(java.util.Arrays.asList("duck", "chatgpt", "claude", null)));
        assertEquals("nothing changed", before, ids(store.all()));
    }
}
