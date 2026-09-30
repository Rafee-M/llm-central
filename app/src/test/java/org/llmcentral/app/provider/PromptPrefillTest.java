package org.llmcentral.app.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.net.URLDecoder;

/** Checks the "new chat with this prompt" addresses ({@link PromptPrefill}, PROJECT.md R-AST-5). */
public class PromptPrefillTest {

    @Test
    public void encodingMatchesAndroidsUriEncode() {
        // Same rule as android.net.Uri.encode: letters, digits and _-!.~'()* stay; the rest is %XX of
        // the UTF-8 bytes; a space is %20 (never "+").
        assertEquals("a%20b%26c%3Dd%2F%3F%23%2B%C3%A9%F0%9F%98%80-_.!~*'()%0A",
                PromptPrefill.encode("a b&c=d/?#+é😀-_.!~*'()\n"));
    }

    @Test
    public void duckLinkCarriesThePromptInQAndHandoff() throws Exception {
        String url = PromptPrefill.duckUrl("Hi there & more", false);
        assertTrue(url.startsWith("https://duck.ai/chat?q=Hi%20there%20%26%20more&handoff="));
        String handoff = URLDecoder.decode(url.substring(url.indexOf("&handoff=") + 9), "UTF-8");
        JSONObject o = new JSONObject(handoff);
        assertEquals("Hi there & more", o.getString("aiChatPrompt"));
        assertFalse(o.getBoolean("aiChatAutoPrompt")); // shown in the box, not sent
        assertTrue(new JSONObject(URLDecoder.decode(
                PromptPrefill.duckUrl("x", true).split("&handoff=")[1], "UTF-8")).getBoolean("aiChatAutoPrompt"));
    }

    @Test
    public void startAddressPerProvider() {
        assertTrue(PromptPrefill.startUrl(Providers.DUCK, "Hi").startsWith("https://duck.ai/chat?q=Hi&handoff="));
        assertEquals("https://claude.ai/new?q=Hi%20there", PromptPrefill.startUrl(Providers.CLAUDE, "Hi there"));
        // No reliable address: the home page, and the fill-in script types the prompt.
        assertEquals("https://chatgpt.com/", PromptPrefill.startUrl(Providers.CHATGPT, "Hi"));
        assertEquals("https://chat.deepseek.com/", PromptPrefill.startUrl(Providers.DEEPSEEK, "Hi"));
        assertEquals("https://chat.z.ai/", PromptPrefill.startUrl(Providers.ZAI, "Hi"));
    }

    @Test
    public void longPromptsAreNotPutIntoAnAddress() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= PromptPrefill.MAX_URL_PROMPT_CHARS; i++) {
            sb.append('a');
        }
        assertEquals("https://claude.ai/", PromptPrefill.startUrl(Providers.CLAUDE, sb.toString()));
    }
}
