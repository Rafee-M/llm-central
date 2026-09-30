package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Checks the rules of the duck.ai message bridge ({@link DuckBridge}, PROJECT.md P1): who may send
 * messages, which messages are accepted, and that bad ones are ignored. Runs on the computer.
 */
public class DuckBridgeTest {

    /** A pretend app that writes down every action the bridge asked for. */
    private static final class RecordingHost implements DuckBridge.Host {
        final List<String> calls = new ArrayList<>();

        @Override public void copyToClipboard(String text) { calls.add("copy " + text); }
        @Override public void onChatsFetched(String json) { calls.add("chats " + json); }
        @Override public void processBlob(String d, String m, String cd, String u) { calls.add("blob " + d + " " + m + " " + cd + " " + u); }
        @Override public void showSoftKeyboard() { calls.add("keyboard"); }
        @Override public void triggerNewChatShortcut() { calls.add("shortcut"); }
        @Override public void setImageZoomActive(boolean active) { calls.add("zoom " + active); }
        @Override public void onContinueLastChatSuccess() { calls.add("continued"); }
    }

    private static final String DUCK = "https://duck.ai";

    @Test
    public void originRulesCoverDuckDomainsAndTheirSubdomains() {
        // "https://*.duck.ai" doesn't match "https://duck.ai" itself, so both are needed.
        assertEquals(Arrays.asList("https://duck.ai", "https://*.duck.ai",
                "https://duckduckgo.com", "https://*.duckduckgo.com"),
                new ArrayList<>(DuckBridge.originRules()));
    }

    @Test
    public void onlyHttpsDuckOriginsAreTrusted() {
        assertTrue(DuckBridge.isTrustedOrigin("https://duck.ai"));
        assertTrue(DuckBridge.isTrustedOrigin("https://www.duck.ai"));
        assertTrue(DuckBridge.isTrustedOrigin("https://duckduckgo.com:443"));
        assertFalse(DuckBridge.isTrustedOrigin("http://duck.ai"));        // not encrypted
        assertFalse(DuckBridge.isTrustedOrigin("https://evilduck.ai"));   // look-alike
        assertFalse(DuckBridge.isTrustedOrigin("https://duck.ai.evil.com"));
        assertFalse(DuckBridge.isTrustedOrigin("https://duck.ai:8443"));  // other port
        assertFalse(DuckBridge.isTrustedOrigin("https://chatgpt.com"));   // other provider
        assertFalse(DuckBridge.isTrustedOrigin("null"));                  // file: pages
        assertFalse(DuckBridge.isTrustedOrigin(null));
    }

    @Test
    public void everyKnownMessageReachesTheApp() {
        RecordingHost host = new RecordingHost();
        assertTrue(DuckBridge.handle("copy:Hello: world", DUCK, true, host));
        assertTrue(DuckBridge.handle("chats:{\"a\":1}", DUCK, true, host));
        assertTrue(DuckBridge.handle("blob:{\"data\":\"data:text/plain;base64,SGk=\",\"mime\":\"text/plain\","
                + "\"disposition\":\"x.txt\",\"url\":\"https://duck.ai/chat\"}", DUCK, true, host));
        assertTrue(DuckBridge.handle("zoom:true", DUCK, true, host));
        assertTrue(DuckBridge.handle("zoom:false", DUCK, true, host));
        assertTrue(DuckBridge.handle("keyboard:", DUCK, true, host));
        assertTrue(DuckBridge.handle("shortcut:", DUCK, true, host));
        assertTrue(DuckBridge.handle("continued:", DUCK, true, host));
        assertEquals(Arrays.asList(
                "copy Hello: world", // only the first ":" separates the type
                "chats {\"a\":1}",
                "blob data:text/plain;base64,SGk= text/plain x.txt https://duck.ai/chat",
                "zoom true", "zoom false", "keyboard", "shortcut", "continued"), host.calls);
    }

    @Test
    public void messagesFromOtherSendersAreIgnored() {
        RecordingHost host = new RecordingHost();
        assertFalse(DuckBridge.handle("copy:x", "https://evil.example", true, host));
        assertFalse(DuckBridge.handle("copy:x", DUCK, false, host)); // an embedded frame, even on duck.ai
        assertFalse(DuckBridge.handle("copy:x", "null", true, host));
        assertTrue(host.calls.isEmpty());
    }

    @Test
    public void badOrUnknownMessagesAreIgnored() {
        RecordingHost host = new RecordingHost();
        assertFalse(DuckBridge.handle(null, DUCK, true, host));
        assertFalse(DuckBridge.handle("getChatsJson", DUCK, true, host)); // old method names do nothing
        assertFalse(DuckBridge.handle("chats:not json", DUCK, true, host));
        assertFalse(DuckBridge.handle("zoom:yes", DUCK, true, host));
        assertFalse(DuckBridge.handle("blob:{broken", DUCK, true, host));
        // A blob must carry the file itself (data: URL), never a link for the app to fetch.
        assertFalse(DuckBridge.handle("blob:{\"data\":\"https://evil.example/x\"}", DUCK, true, host));
        StringBuilder huge = new StringBuilder("copy:");
        for (int i = 0; i <= DuckBridge.MAX_COPY_CHARS; i++) {
            huge.append('a');
        }
        assertFalse(DuckBridge.handle(huge.toString(), DUCK, true, host));
        assertTrue(host.calls.isEmpty());
    }
}
