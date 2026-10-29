package org.llmcentral.app.web;

import android.content.Context;

import org.llmcentral.app.provider.ProviderSpec;

/**
 * Settings → Data &amp; tools → "Allow third-party cookies", <b>off by default</b> (owner, 2026-10-29).
 *
 * <p><b>What it means:</b> a "third-party" cookie belongs to another website embedded in the page (a frame or a
 * request to another site), e.g. a captcha or a sign-in helper inside the chat site. Such cookies can follow you
 * across sites, so they are blocked unless the user turns this on. The sites' <i>own</i> cookies (your logins) are
 * never affected, and a sign-in pop-up's page is a site of its own (first-party there). Blocked cookies are only
 * hidden from embedded sites, not deleted: turning the switch on brings them back.</p>
 *
 * <p><b>Where it applies:</b> ChatGPT, Claude, DeepSeek and Z.ai tabs and their pop-ups (web/PopupWindows copies its
 * tab's value). duck.ai never gets them. Android stores the choice per WebView
 * ({@code CookieManager.setAcceptThirdPartyCookies(webView, …)}), and WebView reads it on every cookie access, so a
 * change applies to the next request without a reload (Chromium {@code aw_cookie_access_policy.cc}).
 * https://developer.android.com/reference/android/webkit/CookieManager#setAcceptThirdPartyCookies(android.webkit.WebView,%20boolean)</p>
 *
 * <p>Downloads are not affected: the download manager gets the file's own cookies with {@code getCookie(url)}, which
 * ignores this setting (Chromium {@code cookie_manager.cc}).</p>
 */
public final class ThirdPartyCookies {

    /** Preference key in {@code duck_assist_prefs}; missing = off (blocked). */
    public static final String PREF = "third_party_cookies";

    private ThirdPartyCookies() {
    }

    public static boolean isOn(Context context) {
        return context.getSharedPreferences("duck_assist_prefs", Context.MODE_PRIVATE).getBoolean(PREF, false);
    }

    public static void setOn(Context context, boolean on) {
        context.getSharedPreferences("duck_assist_prefs", Context.MODE_PRIVATE).edit().putBoolean(PREF, on).apply();
    }

    /** The rule: true if this provider's tab may use third-party cookies. duck.ai never needs them. */
    public static boolean allowed(ProviderSpec provider, boolean settingOn) {
        return settingOn && !provider.isDuck();
    }
}
