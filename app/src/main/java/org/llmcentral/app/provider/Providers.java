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
 * <p><b>Sign-in note:</b> Google blocks "Sign in with Google" inside apps' web views, so Google's
 * login page is deliberately not listed for anyone; users should sign in with email (or Microsoft /
 * Apple for ChatGPT, GitHub for Z.ai). Whether each login flow works inside the app still has to be
 * tested on a phone.</p>
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
            // openai.com covers auth.openai.com (login); Microsoft and Apple are ChatGPT's other sign-in options.
            new String[]{"chatgpt.com", "openai.com", "login.live.com", "login.microsoftonline.com", "appleid.apple.com"},
            "G", 0xFF10A37F);

    public static final ProviderSpec CLAUDE = new ProviderSpec(
            "claude", "Claude", "https://claude.ai/",
            new String[]{"claude.ai", "claude.com"},
            "C", 0xFFD97757);

    public static final ProviderSpec DEEPSEEK = new ProviderSpec(
            "deepseek", "DeepSeek", "https://chat.deepseek.com/",
            new String[]{"deepseek.com"},
            "DS", 0xFF4D6BFE);

    public static final ProviderSpec ZAI = new ProviderSpec(
            "zai", "Z.ai", "https://chat.z.ai/",
            // GitHub is one of Z.ai's sign-in options.
            new String[]{"z.ai", "github.com"},
            "Z", 0xFF3B3B3B);

    /** All providers, in switcher order. Unmodifiable. */
    public static final List<ProviderSpec> ALL =
            Collections.unmodifiableList(Arrays.asList(DUCK, CHATGPT, CLAUDE, DEEPSEEK, ZAI));

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
