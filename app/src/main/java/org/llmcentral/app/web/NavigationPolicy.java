package org.llmcentral.app.web;

import java.net.URI;
import java.util.Locale;

/**
 * Decides which web addresses may load <b>inside</b> the app's chat WebView.
 *
 * <p><b>Why this matters:</b> pages inside the chat WebView get the app's JavaScript bridge
 * ({@code window.Android}: save files, clipboard, open dialogs...). So only the chat sites we trust may
 * load there; every other link opens in the user's normal browser.</p>
 *
 * <p><b>Two bugs this class fixes (PROJECT.md P14):</b></p>
 * <ol>
 *   <li>The old check was {@code host.endsWith("duck.ai")}, which is also true for
 *       {@code evilduck.ai}. Now a host must be the domain itself or a real subdomain
 *       ({@code something.duck.ai}).</li>
 *   <li>MainActivity is "exported" (other apps may open it). For a "view this URL" request it loaded
 *       whatever address it was given, including {@code javascript:} or {@code file:} URLs and
 *       any website. Now such requests are checked with {@link #toInAppUrl} first.</li>
 * </ol>
 *
 * <p>Plain Java only ({@link java.net.URI}), so it can be tested on the computer
 * (NavigationPolicyTest). In Phase 1 the domain list will come from each provider's
 * {@code ProviderSpec} instead of this fixed list.</p>
 */
public final class NavigationPolicy {

    /** Domains (and their subdomains) that may load inside the app. */
    static final String[] IN_APP_DOMAINS = {"duck.ai", "duckduckgo.com"};

    private NavigationPolicy() {
        // Only static methods.
    }

    /**
     * @return true if {@code host} is one of {@link #IN_APP_DOMAINS} or a subdomain of one.
     *         Examples: "duck.ai" ✓, "www.duckduckgo.com" ✓, "evilduck.ai" ✗, "duck.ai.evil.com" ✗.
     */
    public static boolean isInAppHost(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1); // "duck.ai." is the same host as "duck.ai"
        }
        for (String domain : IN_APP_DOMAINS) {
            // The "." prefix is the key: "x.duck.ai" ends with ".duck.ai", "evilduck.ai" doesn't.
            if (h.equals(domain) || h.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks an address that another app asked us to open.
     *
     * @return the address to load (http is upgraded to https), or {@code null} if it must NOT be
     *         loaded in the app (wrong scheme such as javascript:/file:/intent:, unknown host,
     *         embedded user name/password, or unparsable).
     */
    public static String toInAppUrl(String url) {
        if (url == null) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (Exception e) {
            return null;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            return null;
        }
        // "https://duck.ai@evil.com/" looks like duck.ai but goes to evil.com. getHost() already
        // returns "evil.com" here; we additionally refuse any URL with a user-info part.
        if (uri.getRawUserInfo() != null || !isInAppHost(uri.getHost())) {
            return null;
        }
        if (scheme.equals("http")) {
            // The chat sites all use HTTPS; never load them unencrypted.
            return "https" + url.trim().substring(4);
        }
        return url.trim();
    }

    /**
     * Special Chromium addresses that deliberately crash ({@code chrome://crash}) or kill
     * ({@code chrome://kill}) the WebView renderer. Only allowed in DEBUG builds, so the owner can
     * test the renderer-recovery code on a phone (see CHANGES.md). Never used in release builds.
     */
    public static boolean isRendererTestUrl(String url) {
        return "chrome://crash".equals(url) || "chrome://kill".equals(url);
    }
}
