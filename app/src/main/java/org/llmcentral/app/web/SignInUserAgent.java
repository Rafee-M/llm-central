package org.llmcentral.app.web;

import android.net.Uri;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.llmcentral.app.R;
import org.llmcentral.app.provider.SignInHosts;

/**
 * "Continue with Google" inside the app (owner, 2026-10-29). Google refuses its sign-in in Android WebViews: it reads
 * the WebView mark {@code "; wv"} in the user agent (the "who is asking" text every request carries) and answers
 * "Error 403: disallowed_useragent". The owner chose geminiAssist's method (AcideFluorhydrique/geminiAssist,
 * GPL-3.0, {@code MainActivity.modUserAgent}): the first bracket of the user agent, e.g.
 * {@code (Linux; Android 14; Pixel 7; wv)}, is replaced with {@code (X11; Linux aarch64)}.
 *
 * <p><b>Only where needed:</b> unlike geminiAssist (whole app), the changed user agent is used only by the one
 * WebView (tab or pop-up) that is showing Google's sign-in host ({@link SignInHosts#needsBrowserUserAgent}), and only
 * while it shows it. Every other page, account and provider keeps the normal one (sites' bot checks such as
 * Cloudflare compare it with the rest of the browser, PROJECT.md §5.5). Apple's sign-in works with the normal one.</p>
 *
 * <p><b>How:</b> {@code setUserAgentString} doesn't reload anything; it only applies to the <i>next</i> page load
 * (Chromium {@code aw_settings.cc} {@code UpdateUserAgentLocked}). So the callers start the same address again
 * right after a switch ({@link #apply}). Not changed: WebView's {@code X-Requested-With} header (it names the app;
 * geminiAssist works with it); if Google ever starts checking it, this stops working. [unverified: works on a
 * phone for each provider]</p>
 */
public final class SignInUserAgent {

    /** What {@link #apply} did. */
    public enum Change {
        /** Nothing to do. */
        NONE,
        /** The WebView now uses the browser-like user agent: load the address again. */
        DISGUISE,
        /** The WebView has its own user agent back. */
        RESTORE
    }

    /** What a WebView carries while disguised (view tag {@code R.id.tag_sign_in_ua}): its own and the changed text. */
    private static final class State {
        final String normal;
        final String disguised;

        State(String normal, String disguised) {
            this.normal = normal;
            this.disguised = disguised;
        }
    }

    private SignInUserAgent() {
    }

    /**
     * geminiAssist's rule: the first {@code (…)} of {@code userAgent} becomes {@code (X11; Linux <arch>)}; the rest
     * (browser version, "Mobile Safari") stays. A user agent without brackets is returned unchanged.
     *
     * @param arch the processor name ({@code os.arch}, e.g. "aarch64"); null/empty = "aarch64"
     */
    static String disguise(String userAgent, String arch) {
        if (userAgent == null) {
            return "";
        }
        int start = userAgent.indexOf('(');
        int end = start < 0 ? -1 : userAgent.indexOf(')', start);
        if (end < 0) {
            return userAgent;
        }
        String cpu = arch == null || arch.isEmpty() ? "aarch64" : arch;
        return userAgent.substring(0, start) + "(X11; Linux " + cpu + ")" + userAgent.substring(end + 1);
    }

    /**
     * The rule, in plain Java.
     *
     * @param savedDisguise the changed user agent this WebView was given, or null if it isn't disguised
     * @param current       the user agent the WebView has now
     * @param host          where it is about to go
     */
    static Change change(String savedDisguise, String current, String host) {
        // Anything else (Desktop site) changing the user agent since we set it wins: the old disguise is forgotten.
        boolean disguised = savedDisguise != null && savedDisguise.equals(current);
        boolean wanted = SignInHosts.needsBrowserUserAgent(host);
        if (wanted == disguised) {
            return Change.NONE;
        }
        return wanted ? Change.DISGUISE : Change.RESTORE;
    }

    /**
     * Gives {@code view} the user agent {@code url} needs. Call before the address loads (tabs:
     * {@code shouldOverrideUrlLoading}; also {@code onPageStarted}, because a new window's first address and
     * Back/Forward don't pass through it). After {@link Change#DISGUISE} load {@code url} again so the request goes
     * out with the new text; after {@link Change#RESTORE} that is optional (never for a form sent with POST).
     */
    public static Change apply(WebView view, String url) {
        WebSettings settings = view.getSettings();
        String current = settings.getUserAgentString();
        Object tag = view.getTag(R.id.tag_sign_in_ua);
        State state = tag instanceof State ? (State) tag : null;
        Change change = change(state != null ? state.disguised : null, current, url == null ? null : Uri.parse(url).getHost());
        if (change == Change.DISGUISE) {
            String disguised = disguise(current, System.getProperty("os.arch"));
            settings.setUserAgentString(disguised);
            view.setTag(R.id.tag_sign_in_ua, new State(current, disguised));
        } else if (change == Change.RESTORE) {
            view.setTag(R.id.tag_sign_in_ua, null);
            // null = WebView's own default (what a tab without "Desktop site" uses), else Desktop site's text.
            boolean isDefault = state.normal.equals(WebSettings.getDefaultUserAgent(view.getContext()));
            settings.setUserAgentString(isDefault ? null : state.normal);
        } else if (state != null && !state.disguised.equals(current)) {
            view.setTag(R.id.tag_sign_in_ua, null); // someone else changed the user agent: forget ours
        }
        return change;
    }
}
