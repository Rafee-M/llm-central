package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.llmcentral.app.provider.Providers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Checks the rules of the message bridge ({@link AppBridge}, PROJECT.md P1): who may send messages,
 * which messages are accepted, and that bad ones are ignored, for duck.ai's full bridge and the other
 * providers' downloads-only bridge. Runs on the computer.
 */
public class AppBridgeTest {

    /** A pretend app that writes down every action the bridge asked for. */
    private static final class RecordingHost implements AppBridge.Host {
        final List<String> calls = new ArrayList<>();

        @Override public void copyToClipboard(String text) { calls.add("copy " + text); }
        @Override public void onChatsFetched(String json) { calls.add("chats " + json); }
        @Override public void processBlob(String d, String m, String cd, String u, String n) { calls.add("blob " + d + " " + m + " " + cd + " " + u + " " + n); }
        @Override public void showSoftKeyboard() { calls.add("keyboard"); }
        @Override public void triggerNewChatShortcut() { calls.add("shortcut"); }
        @Override public void setImageZoomActive(boolean active) { calls.add("zoom " + active); }
        @Override public void onContinueLastChatSuccess() { calls.add("continued"); }
    }

    private static final String DUCK = "https://duck.ai";
    private static final AppBridge.Scope D = AppBridge.Scope.duck();
    private static final String BLOB = "blob:{\"data\":\"data:text/csv;base64,YSxi\",\"mime\":\"text/csv\"}";

    @Test
    public void originRulesCoverDuckDomainsAndTheirSubdomains() {
        // "https://*.duck.ai" doesn't match "https://duck.ai" itself, so both are needed.
        assertEquals(Arrays.asList("https://duck.ai", "https://*.duck.ai",
                "https://duckduckgo.com", "https://*.duckduckgo.com"),
                new ArrayList<>(AppBridge.originRules(D)));
    }

    @Test
    public void onlyHttpsDuckOriginsAreTrusted() {
        assertTrue(AppBridge.isTrustedOrigin("https://duck.ai", D));
        assertTrue(AppBridge.isTrustedOrigin("https://www.duck.ai", D));
        assertTrue(AppBridge.isTrustedOrigin("https://duckduckgo.com:443", D));
        assertFalse(AppBridge.isTrustedOrigin("http://duck.ai", D));        // not encrypted
        assertFalse(AppBridge.isTrustedOrigin("https://evilduck.ai", D));   // look-alike
        assertFalse(AppBridge.isTrustedOrigin("https://duck.ai.evil.com", D));
        assertFalse(AppBridge.isTrustedOrigin("https://duck.ai:8443", D));  // other port
        assertFalse(AppBridge.isTrustedOrigin("https://chatgpt.com", D));   // other provider
        assertFalse(AppBridge.isTrustedOrigin("null", D));                  // file: pages
        assertFalse(AppBridge.isTrustedOrigin(null, D));
    }

    @Test
    public void everyKnownMessageReachesTheApp() {
        RecordingHost host = new RecordingHost();
        assertTrue(AppBridge.handle("copy:Hello: world", DUCK, true, host, D));
        assertTrue(AppBridge.handle("chats:{\"a\":1}", DUCK, true, host, D));
        assertTrue(AppBridge.handle("blob:{\"data\":\"data:text/plain;base64,SGk=\",\"mime\":\"text/plain\","
                + "\"disposition\":\"x.txt\",\"url\":\"https://duck.ai/chat\"}", DUCK, true, host, D));
        assertTrue(AppBridge.handle("zoom:true", DUCK, true, host, D));
        assertTrue(AppBridge.handle("zoom:false", DUCK, true, host, D));
        assertTrue(AppBridge.handle("keyboard:", DUCK, true, host, D));
        assertTrue(AppBridge.handle("shortcut:", DUCK, true, host, D));
        assertTrue(AppBridge.handle("continued:", DUCK, true, host, D));
        assertEquals(Arrays.asList(
                "copy Hello: world", // only the first ":" separates the type
                "chats {\"a\":1}",
                "blob data:text/plain;base64,SGk= text/plain x.txt https://duck.ai/chat ",
                "zoom true", "zoom false", "keyboard", "shortcut", "continued"), host.calls);
    }

    @Test
    public void messagesFromOtherSendersAreIgnored() {
        RecordingHost host = new RecordingHost();
        assertFalse(AppBridge.handle("copy:x", "https://evil.example", true, host, D));
        assertFalse(AppBridge.handle("copy:x", DUCK, false, host, D)); // an embedded frame, even on duck.ai
        assertFalse(AppBridge.handle("copy:x", "null", true, host, D));
        assertTrue(host.calls.isEmpty());
    }

    @Test
    public void badOrUnknownMessagesAreIgnored() {
        RecordingHost host = new RecordingHost();
        assertFalse(AppBridge.handle(null, DUCK, true, host, D));
        assertFalse(AppBridge.handle("getChatsJson", DUCK, true, host, D)); // old method names do nothing
        assertFalse(AppBridge.handle("chats:not json", DUCK, true, host, D));
        assertFalse(AppBridge.handle("zoom:yes", DUCK, true, host, D));
        assertFalse(AppBridge.handle("blob:{broken", DUCK, true, host, D));
        // A blob must carry the file itself (data: URL), never a link for the app to fetch.
        assertFalse(AppBridge.handle("blob:{\"data\":\"https://evil.example/x\"}", DUCK, true, host, D));
        StringBuilder huge = new StringBuilder("copy:");
        for (int i = 0; i <= AppBridge.MAX_COPY_CHARS; i++) {
            huge.append('a');
        }
        assertFalse(AppBridge.handle(huge.toString(), DUCK, true, host, D));
        assertTrue(host.calls.isEmpty());
    }

    @Test
    public void downloadsOnlyBridgeCoversJustTheProvidersChatSite() {
        assertEquals(Arrays.asList("https://chatgpt.com", "https://*.chatgpt.com"),
                new ArrayList<>(AppBridge.originRules(AppBridge.Scope.downloads(Providers.CHATGPT))));
        assertEquals(Arrays.asList("https://chat.deepseek.com", "https://*.chat.deepseek.com"),
                new ArrayList<>(AppBridge.originRules(AppBridge.Scope.downloads(Providers.DEEPSEEK))));
        AppBridge.Scope z = AppBridge.Scope.downloads(Providers.ZAI);
        assertTrue(AppBridge.isTrustedOrigin("https://chat.z.ai", z));
        assertFalse(AppBridge.isTrustedOrigin("https://z.ai", z));          // not the chat site
        assertFalse(AppBridge.isTrustedOrigin("https://github.com", z));    // Z.ai's sign-in site
        AppBridge.Scope g = AppBridge.Scope.downloads(Providers.CHATGPT);
        assertFalse(AppBridge.isTrustedOrigin("https://auth.openai.com", g)); // ChatGPT's sign-in site
        assertFalse(AppBridge.isTrustedOrigin("https://duck.ai", g));
    }

    @Test
    public void downloadsOnlyBridgeAcceptsNothingButFiles() {
        RecordingHost host = new RecordingHost();
        AppBridge.Scope g = AppBridge.Scope.downloads(Providers.CHATGPT);
        assertTrue(AppBridge.handle(BLOB, "https://chatgpt.com", true, host, g));
        assertFalse(AppBridge.handle(BLOB, "https://chatgpt.com", false, host, g));   // frame
        assertFalse(AppBridge.handle("copy:x", "https://chatgpt.com", true, host, g)); // duck.ai-only types
        assertFalse(AppBridge.handle("chats:{}", "https://chatgpt.com", true, host, g));
        assertFalse(AppBridge.handle("zoom:true", "https://chatgpt.com", true, host, g));
        assertFalse(AppBridge.handle("keyboard:", "https://chatgpt.com", true, host, g));
        // The page's own name for the file travels along (WebView itself doesn't pass it on).
        assertTrue(AppBridge.handle("blob:{\"data\":\"data:;base64,YSxi\",\"name\":\"scores.csv\"}",
                "https://chatgpt.com", true, host, g));
        assertEquals(Arrays.asList("blob data:text/csv;base64,YSxi text/csv   ",
                "blob data:;base64,YSxi    scores.csv"), host.calls);
    }
}
