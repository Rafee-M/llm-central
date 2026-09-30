package org.llmcentral.app.web;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.webkit.ScriptHandler;
import androidx.webkit.UserAgentMetadata;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.llmcentral.app.R;
import org.llmcentral.app.provider.ProviderSpec;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * "Desktop site" for one account, switched with the monitor button in the top bar, like Chrome's
 * "Desktop site" menu item.
 *
 * <p><b>What WebView offers:</b> there is no single "desktop" switch in Android's WebView (checked
 * in the WebSettings and androidx.webkit 1.17.1 docs). A website decides between its phone and
 * computer layout from three things, so the app changes all three:</p>
 * <ol>
 *   <li><b>User agent</b>, the "who am I" text sent with every request
 *       ({@link WebSettings#setUserAgentString}). {@link #desktopUserAgent} turns this WebView's own
 *       text into a desktop one: "Linux; Android 14; …" becomes "X11; Linux x86_64" and the word
 *       "Mobile" is dropped. It keeps the "; wv" mark that says "this is an app's WebView", so the
 *       app stays honest (CLAUDE.md: never pretend to be a browser to get around Google's sign-in
 *       block).</li>
 *   <li><b>Client hints</b>, a newer, structured version of the same information
 *       ({@code Sec-CH-UA-Mobile} etc.). With {@code USER_AGENT_METADATA} support,
 *       {@link WebSettingsCompat#setUserAgentMetadata} says "not mobile, Linux". (Without it, a
 *       changed user agent only sends the basic hints, per the androidx.webkit docs.)</li>
 *   <li><b>Screen width.</b> The chat sites are "responsive": their CSS picks the layout from the
 *       page width, which a phone reports as ~400 px. {@code assets/js/desktop_viewport.js} sets the
 *       page's viewport to {@value #VIEWPORT_WIDTH} px; WebView then shows the whole width zoomed out
 *       (the tabs use {@code setLoadWithOverviewMode(true)}), and pinching zooms the page.
 *       Where WebView supports {@code DOCUMENT_START_SCRIPT} the script runs before the page's own
 *       code (no flash of the phone layout), only on the provider's own sites; it's also run after
 *       every page load as a fallback.</li>
 * </ol>
 * <p>Changing the user agent only affects the next page load, so the tab is reloaded after a
 * switch. The choice is remembered per account (preference {@value #PREF}).</p>
 *
 * <p>{@link #desktopUserAgent} and the saved-list helpers are plain Java (DesktopModeTest).</p>
 */
public final class DesktopMode {

    /** Preference: JSON list of the ids of accounts that use the desktop site. */
    static final String PREF = "desktop_accounts";
    /** Page width (CSS pixels) given to sites in desktop mode: wide enough for their computer layout. */
    static final int VIEWPORT_WIDTH = 1024;

    /**
     * What a tab currently uses, kept on the tab's WebView itself ({@code View.setTag}), so it goes
     * away together with the tab (a static list of WebViews could keep closed tabs in memory).
     */
    private static final class State {
        boolean on;
        ScriptHandler viewportScript;   // to remove the viewport script when switching back
        UserAgentMetadata phoneMetadata; // to put the phone client hints back
    }

    private static State state(WebView webView) {
        Object tag = webView.getTag(R.id.tag_desktop_mode);
        if (tag instanceof State) {
            return (State) tag;
        }
        State fresh = new State();
        webView.setTag(R.id.tag_desktop_mode, fresh);
        return fresh;
    }

    private DesktopMode() {
        // Only static methods.
    }

    // ------------------------------------------------------------------ the saved choice

    /** @return true if the account is set to use the desktop site. */
    public static boolean isOn(SharedPreferences prefs, String accountId) {
        return ids(prefs.getString(PREF, null)).contains(accountId);
    }

    /** Switches the account's choice and saves it. @return the new state */
    public static boolean toggle(SharedPreferences prefs, String accountId) {
        String json = prefs.getString(PREF, null);
        boolean on = !ids(json).contains(accountId);
        prefs.edit().putString(PREF, with(json, accountId, on)).apply();
        return on;
    }

    /** @return the account ids in the saved JSON list (empty for null/broken). */
    static Set<String> ids(String json) {
        Set<String> result = new LinkedHashSet<>();
        if (json != null) {
            try {
                JSONArray list = new JSONArray(json);
                for (int i = 0; i < list.length(); i++) {
                    result.add(list.getString(i));
                }
            } catch (Exception e) {
                return Collections.emptySet();
            }
        }
        return result;
    }

    /** @return the JSON list with {@code accountId} added (on) or removed (off). */
    static String with(String json, String accountId, boolean on) {
        Set<String> set = new LinkedHashSet<>(ids(json));
        if (on) {
            set.add(accountId);
        } else {
            set.remove(accountId);
        }
        return new JSONArray(set).toString();
    }

    // ------------------------------------------------------------------ the user agent

    /**
     * Turns a phone WebView user agent into a desktop one, keeping everything else (browser version,
     * the WebView marks "; wv" and "Version/4.0"). Example:
     * <pre>
     * Mozilla/5.0 (Linux; Android 14; Pixel 7; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/140.0.0.0 Mobile Safari/537.36
     * Mozilla/5.0 (X11; Linux x86_64; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/140.0.0.0 Safari/537.36
     * </pre>
     */
    public static String desktopUserAgent(String phoneUserAgent) {
        String ua = phoneUserAgent == null ? "" : phoneUserAgent;
        int start = ua.indexOf("(Linux; Android");
        int end = start < 0 ? -1 : ua.indexOf(')', start);
        if (start >= 0 && end > start) {
            boolean webView = ua.substring(start, end).contains("; wv");
            ua = ua.substring(0, start) + "(X11; Linux x86_64" + (webView ? "; wv" : "") + ")" + ua.substring(end + 1);
        }
        // "Mobile Safari/537.36" → "Safari/537.36" (the word sites look for to pick the phone layout).
        return ua.replace(" Mobile ", " ").replace(" Mobile/", " ").trim();
    }

    // ------------------------------------------------------------------ applying it to a tab

    /** @return true if the tab currently uses the desktop site. */
    public static boolean isActive(WebView webView) {
        Object tag = webView.getTag(R.id.tag_desktop_mode);
        return tag instanceof State && ((State) tag).on;
    }

    /**
     * Sets up a tab for the phone or desktop site. Call before the tab loads a page (while
     * configuring it) or, after a switch, followed by {@code webView.reload()}.
     */
    public static void apply(Context context, WebView webView, ProviderSpec provider, boolean on) {
        if (isActive(webView) == on) {
            return; // nothing changes (a new tab starts as "phone": nothing to undo)
        }
        State state = state(webView);
        WebSettings settings = webView.getSettings();
        // null = WebView's own default user agent.
        settings.setUserAgentString(on ? desktopUserAgent(WebSettings.getDefaultUserAgent(context)) : null);
        applyClientHints(settings, state, on);
        applyViewportScript(context, webView, state, provider, on);
        state.on = on;
    }

    /** Call from the tab's onPageFinished: makes sure the wide viewport is set (fallback). */
    public static void onPageFinished(Context context, WebView webView) {
        if (isActive(webView)) {
            webView.evaluateJavascript(Scripts.get(context, Scripts.DESKTOP_VIEWPORT), null);
        }
    }

    private static void applyClientHints(WebSettings settings, State state, boolean on) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            if (on) {
                UserAgentMetadata phone = WebSettingsCompat.getUserAgentMetadata(settings);
                state.phoneMetadata = phone; // to put back when switching off
                WebSettingsCompat.setUserAgentMetadata(settings, new UserAgentMetadata.Builder(phone)
                        .setMobile(false)
                        .setPlatform("Linux")
                        .setPlatformVersion("")
                        .setModel("")
                        .build());
            } else {
                if (state.phoneMetadata != null) {
                    WebSettingsCompat.setUserAgentMetadata(settings, state.phoneMetadata);
                    state.phoneMetadata = null;
                }
            }
        }
    }

    private static void applyViewportScript(Context context, WebView webView, State state,
                                            ProviderSpec provider, boolean on) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            if (on) {
                // Only the provider's own https sites get it (the same allow-list idea as DuckBridge).
                Set<String> rules = new LinkedHashSet<>();
                for (String domain : provider.inAppDomains()) {
                    rules.add("https://" + domain);
                    rules.add("https://*." + domain);
                }
                state.viewportScript = WebViewCompat.addDocumentStartJavaScript(webView,
                        Scripts.get(context, Scripts.DESKTOP_VIEWPORT), rules);
            } else if (state.viewportScript != null) {
                state.viewportScript.remove();
                state.viewportScript = null;
            }
        }
    }
}
