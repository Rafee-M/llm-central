package org.llmcentral.app.provider;

import org.llmcentral.app.web.NavigationPolicy;

/**
 * "Continue with Apple" / "Continue with Google": the sign-in pages that every chat provider (not duck.ai) may open
 * <b>inside the app</b> (owner, 2026-10-29). Plain Java, checked by ProvidersTest.
 *
 * <p><b>Why inside the app:</b> a sign-in only counts for an account if it happens in that account's own WebView
 * profile ("container", web/Profiles): the tab, or a pop-up made in the same profile (web/PopupWindows). The cookies
 * the sign-in sets then stay in that account; another account of the same provider has its own Apple/Google
 * sign-in. In the phone's browser it would sign in the browser, never the app.</p>
 *
 * <p><b>Shared, not owned:</b> these hosts are deliberately <i>not</i> in any provider's {@code inAppDomains}, because
 * those lists also decide where the microphone is allowed, where the app's page scripts run and where prompts are
 * filled in ({@link ProviderSpec#ownsHost}). They are only allowed as places to navigate to
 * ({@link ProviderSpec#loadsInApp}). Exact hosts and their subdomains only (never all of google.com or apple.com):
 * Google search or Apple support links still open in the browser.</p>
 *
 * <p>Hosts, from Apple's and Google's documented web sign-in addresses: {@code appleid.apple.com/auth/authorize}
 * (Sign in with Apple), {@code idmsa.apple.com} (its sign-in frame), {@code accounts.google.com} (Google's OAuth and
 * sign-in pages; Z.ai's flow, traced 2026-10-29: {@code chat.z.ai/oauth/google/login} → 307 →
 * {@code accounts.google.com/o/oauth2/v2/auth}). If a phone test shows a step of a sign-in opening in the browser, add
 * that step's host here (summary.md §5.12).</p>
 */
public final class SignInHosts {

    /** The shared sign-in hosts (each also covers its subdomains). */
    public static final String[] HOSTS = {"appleid.apple.com", "idmsa.apple.com", "accounts.google.com"};

    /** Google refuses its sign-in in a WebView's own user agent (web/SignInUserAgent). */
    private static final String[] GOOGLE = {"accounts.google.com"};

    private SignInHosts() {
    }

    /** @return true if {@code host} is one of the shared sign-in pages. */
    public static boolean isSignInHost(String host) {
        return NavigationPolicy.hostMatches(host, HOSTS);
    }

    /** @return true if the page on {@code host} must see a browser's user agent (Google's sign-in). */
    public static boolean needsBrowserUserAgent(String host) {
        return NavigationPolicy.hostMatches(host, GOOGLE);
    }
}
