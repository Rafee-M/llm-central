package org.llmcentral.app.provider;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Builds the address that opens a new chat with a prompt already typed in (PROJECT.md R-AST-5).
 * Used for shared text, "Ask AI" and converted documents.
 *
 * <p><b>Per provider (researched 2026-09-30):</b></p>
 * <ul>
 *   <li><b>duck.ai</b> [verified in this app since duckAssist]: {@code https://duck.ai/chat?q=…&handoff=…},
 *       where "handoff" is a small JSON text that puts the prompt into the chat box
 *       ({@code aiChatAutoPrompt:false} = not sent yet).</li>
 *   <li><b>Claude</b>: {@code https://claude.ai/new?q=…}. Anthropic documents {@code q} ("text to prefill
 *       in the prompt field", not sent, cut at about 14,000 characters) for its desktop app's
 *       {@code claude://} links; that the same works on the website is <b>[unverified]</b>. Longer
 *       prompts skip it. Either way the fill-in script (below) runs too and leaves a box that
 *       already has text alone.</li>
 *   <li><b>ChatGPT</b>: {@code chatgpt.com/?q=…} is widely used but undocumented and reported to
 *       <i>send</i> the prompt immediately. The app doesn't use it (the other providers only fill the box,
 *       so you can add to it first); the fill-in script is used instead.</li>
 *   <li><b>DeepSeek</b>: no address parameter found. <b>Z.ai</b>: its chat looks like Open WebUI,
 *       whose {@code ?q=} sends immediately unless {@code submit=false}; not confirmed for Z.ai, so
 *       not used. Both use the fill-in script.</li>
 * </ul>
 * <p>The fill-in script is {@code assets/js/prefill_prompt.js}, run by {@code web/PromptFiller}.</p>
 *
 * <p>Plain Java, so PromptPrefillTest checks the addresses on the computer.</p>
 */
public final class PromptPrefill {

    /** Placeholder in {@link ProviderSpec#promptUrl} that is replaced by the encoded prompt. */
    public static final String PLACEHOLDER = "{q}";

    /**
     * Longest prompt put into an address. Claude cuts {@code q} at about 14,000 characters, and a
     * long text becomes an even longer address once encoded; longer prompts are typed in by the
     * fill-in script instead.
     */
    static final int MAX_URL_PROMPT_CHARS = 6000;

    private PromptPrefill() {
        // Only static methods.
    }

    /**
     * duck.ai's own "open a chat with this prompt" link (the same address the app has always built).
     *
     * @param autoSend true = duck.ai sends the prompt right away (used by the prompt-on-launch box)
     */
    public static String duckUrl(String prompt, boolean autoSend) {
        String handoff;
        try {
            handoff = new JSONObject().put("aiChatPrompt", prompt).put("aiChatAutoPrompt", autoSend).toString();
        } catch (JSONException e) {
            return "https://duck.ai/chat?q=" + encode(prompt); // can't happen for text + boolean
        }
        return "https://duck.ai/chat?q=" + encode(prompt) + "&handoff=" + encode(handoff);
    }

    /**
     * @return the page to open for a new chat with {@code prompt}: duck.ai's handoff link, the
     *         provider's prompt address if it has one and the prompt is short enough, else its home page
     */
    public static String startUrl(ProviderSpec provider, String prompt) {
        if (provider.isDuck()) {
            return duckUrl(prompt, false);
        }
        if (provider.promptUrl != null && prompt.length() <= MAX_URL_PROMPT_CHARS) {
            return provider.promptUrl.replace(PLACEHOLDER, encode(prompt));
        }
        return provider.homeUrl;
    }

    /**
     * Percent-encodes text for an address, exactly like Android's {@code Uri.encode} (which the app
     * used before): letters, digits and {@code _-!.~'()*} stay, everything else becomes {@code %XX}
     * of its UTF-8 bytes (a space becomes {@code %20}, not {@code +}).
     */
    static String encode(String text) {
        StringBuilder sb = new StringBuilder();
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || "_-!.~'()*".indexOf(c) >= 0) {
                sb.append((char) c);
            } else {
                sb.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return sb.toString();
    }
}
