package org.llmcentral.app.provider;

import org.llmcentral.app.web.NavigationPolicy;

/**
 * Describes one AI chat website ("provider") the app can show: duck.ai, ChatGPT, Claude, ...
 *
 * <p><b>Why a class like this:</b> instead of writing {@code if (provider is ChatGPT) ... else if ...}
 * all over the app, every provider-specific fact lives here, in one small object. Adding a provider
 * later means adding one entry in {@link Providers}, nothing else (PROJECT.md R-PROV-2/3).</p>
 *
 * <p>Objects of this class never change after creation ("immutable"): all fields are {@code final}.
 * That makes them safe to share everywhere.</p>
 *
 * <p>Plain Java only (no Android classes), so it's unit-tested on the computer
 * (ProvidersTest).</p>
 */
public final class ProviderSpec {

    /** Short internal name, stored in settings (e.g. "chatgpt"). Never change an existing id. */
    public final String id;
    /** Name shown to the user in the switcher (e.g. "ChatGPT"). */
    public final String displayName;
    /** Page opened when the provider is selected for the first time. */
    public final String homeUrl;
    /**
     * Websites allowed to open <i>inside</i> this provider's tab: the provider's own domains plus the
     * login pages it redirects to. Subdomains are included automatically ("openai.com" also allows
     * "auth.openai.com"). Links to any other site open in the user's browser.
     */
    private final String[] inAppDomains;
    /** 1–2 letters drawn in the round badge that represents the provider in the switcher. */
    public final String badgeText;
    /** Badge background colour as 0xAARRGGBB (the provider's brand-like colour). */
    public final int badgeColor;
    /**
     * Address that opens a new chat with a prompt typed in, with {@code {q}} where the (encoded)
     * prompt goes, or {@code null} if the provider has none we can rely on (see
     * {@link PromptPrefill}). Example: {@code https://claude.ai/new?q={q}}.
     */
    public final String promptUrl;

    public ProviderSpec(String id, String displayName, String homeUrl, String[] inAppDomains,
                        String badgeText, int badgeColor) {
        this(id, displayName, homeUrl, inAppDomains, badgeText, badgeColor, null);
    }

    public ProviderSpec(String id, String displayName, String homeUrl, String[] inAppDomains,
                        String badgeText, int badgeColor, String promptUrl) {
        this.id = id;
        this.displayName = displayName;
        this.homeUrl = homeUrl;
        this.inAppDomains = inAppDomains.clone(); // private copy, so nobody can change it later
        this.badgeText = badgeText;
        this.badgeColor = badgeColor;
        this.promptUrl = promptUrl;
    }

    /**
     * @return true if {@code host} is one of this provider's own sites or login pages. Uses the exact
     *         domain rules from {@link NavigationPolicy#hostMatches} ("evilduck.ai" is not "duck.ai"). Also decides
     *         the microphone, page scripts and prompt filling, so the shared sign-in hosts are not part of it.
     */
    public boolean ownsHost(String host) {
        return NavigationPolicy.hostMatches(host, inAppDomains);
    }

    /**
     * @return true if a page on {@code host} may load inside this provider's tab or pop-up: its own hosts, and for
     *         every chat provider except duck.ai (which has no sign-in) the shared Apple/Google sign-in pages
     *         ({@link SignInHosts}).
     */
    public boolean loadsInApp(String host) {
        return ownsHost(host) || (!isDuck() && SignInHosts.isSignInHost(host));
    }

    /** A copy of the in-app domain list (for tests and diagnostics). */
    public String[] inAppDomains() {
        return inAppDomains.clone();
    }

    /**
     * duck.ai is special: it's the default provider, it needs no login, and the app's extra
     * features (voice chat, offline history, "Ask duck.ai", injected helper scripts) only work there.
     */
    public boolean isDuck() {
        return Providers.DUCK_ID.equals(id);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
