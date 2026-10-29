package org.llmcentral.app.provider;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The list of built-in providers (PROJECT.md R-PROV-1). The order here is the order in the switcher.
 *
 * <p><b>Adding a provider:</b> add a {@code ProviderSpec} constant below and put it in {@link #ALL}.
 * ProvidersTest checks the basic rules (unique ids, https home page that the provider owns, no domain
 * claimed by two providers).</p>
 *
 * <p><b>Sign-in note:</b> Apple's and Google's sign-in pages are shared by every chat provider
 * ({@link SignInHosts}, owner 2026-10-29); Google's page gets a browser user agent while it is shown
 * (web/SignInUserAgent), because Google refuses WebViews. Microsoft (ChatGPT) and GitHub (Z.ai) are
 * listed with their provider. Whether each login flow works inside the app has to be tested on a phone.</p>
 */
public final class Providers {

    /** Stored in settings; never change. */
    public static final String DUCK_ID = "duck";

    public static final ProviderSpec DUCK = new ProviderSpec(
            DUCK_ID, "duck.ai", "https://duck.ai/",
            new String[]{"duck.ai", "duckduckgo.com"},
            "D", 0xFFDE5833);

    public static final ProviderSpec CHATGPT = new ProviderSpec(
            "chatgpt", "ChatGPT", "https://chatgpt.com/",
            // openai.com covers auth.openai.com (login); Microsoft is ChatGPT's own extra sign-in option. Apple and
            // Google are shared by every chat provider (SignInHosts).
            new String[]{"chatgpt.com", "openai.com", "login.live.com", "login.microsoftonline.com"},
            "G", 0xFF10A37F);

    public static final ProviderSpec CLAUDE = new ProviderSpec(
            "claude", "Claude", "https://claude.ai/",
            new String[]{"claude.ai", "claude.com"},
            // "q" pre-fills the chat box without sending (documented for Claude's desktop links;
            // on the website [unverified], see PromptPrefill).
            "C", 0xFFD97757, "https://claude.ai/new?q=" + PromptPrefill.PLACEHOLDER);

    public static final ProviderSpec DEEPSEEK = new ProviderSpec(
            "deepseek", "DeepSeek", "https://chat.deepseek.com/",
            new String[]{"deepseek.com"},
            "DS", 0xFF4D6BFE);

    public static final ProviderSpec ZAI = new ProviderSpec(
            "zai", "Z.ai", "https://chat.z.ai/",
            // GitHub is one of Z.ai's sign-in options.
            new String[]{"z.ai", "github.com"},
            "Z", 0xFF3B3B3B);

    // Google's Gemini (2026-10-29). Only the chat site is its own: Google search, Docs, Drive and the account pages open
    // in the browser; Google's sign-in page is shared (SignInHosts) and gets a browser user agent (web/SignInUserAgent).
    // Gemini also works signed out (fewer features). Blue = the base colour of Lobe Icons' gemini-color.svg.
    public static final ProviderSpec GEMINI = new ProviderSpec(
            "gemini", "Gemini", "https://gemini.google.com/app",
            new String[]{"gemini.google.com"},
            "Ge", 0xFF3186FF);

    /** All providers, in switcher order. Unmodifiable. */
    public static final List<ProviderSpec> ALL =
            Collections.unmodifiableList(Arrays.asList(DUCK, CHATGPT, CLAUDE, DEEPSEEK, ZAI, GEMINI));

    private Providers() {
        // Only static members.
    }

    /** @return the provider with this id, or duck.ai if the id is unknown (e.g. from an older version). */
    public static ProviderSpec byId(String id) {
        ProviderSpec found = find(id);
        return found != null ? found : DUCK;
    }

    /** @return the provider with this id, or {@code null} if there is none (strict version of {@link #byId}). */
    public static ProviderSpec find(String id) {
        for (ProviderSpec p : ALL) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }
}
