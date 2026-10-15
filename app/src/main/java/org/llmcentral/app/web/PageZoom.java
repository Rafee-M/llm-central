package org.llmcentral.app.web;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.WebView;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.llmcentral.app.R;
import org.llmcentral.app.provider.ProviderSpec;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Setting "Pinch zooms the page" (Settings → Behaviour, 2026-10-15): a two-finger pinch zooms the
 * whole page freely, like in a phone browser (the page is magnified as it is, nothing re-flows),
 * instead of changing the text size.
 *
 * <p><b>Two things make it work:</b></p>
 * <ol>
 *   <li>The app's own pinch handler ({@code MainActivity.configureWebView}, text size) lets the
 *       gesture through to WebView while this is on ({@link #isActive}), as it already did in
 *       desktop mode and in duck.ai's picture viewer. WebView's own zoom is switched on for every tab
 *       ({@code setSupportZoom}, built-in zoom controls, wide viewport).</li>
 *   <li>Most chat sites forbid zooming in their {@code <meta name="viewport">}. Android's WebView has
 *       no "force zoom" switch (unlike Chrome's accessibility option; checked in Chromium's
 *       {@code AwSettings}), so {@code assets/js/zoom_viewport.js} lifts the ban. It must run at
 *       <b>document start</b>, before the page's own code: measured in Chromium, changing the tag
 *       after the page has loaded no longer changes the zoom limits. So a tab whose setting changes is
 *       reloaded once ({@link #sync}), the same as the Desktop site button does.</li>
 * </ol>
 *
 * <p>One setting for all accounts (preference {@value #PREF}, off by default). The text size stays
 * adjustable in Settings → Appearance → Text size.</p>
 * <p>Docs: https://developer.android.com/reference/androidx/webkit/WebViewCompat#addDocumentStartJavaScript(android.webkit.WebView,java.lang.String,java.util.Set%3Cjava.lang.String%3E)</p>
 */
public final class PageZoom {

    /** Preference: true = pinch zooms the page, false = pinch changes the text size (the old behaviour). */
    public static final String PREF = "pinch_zoom_page";

    /** What a tab currently uses, kept on the tab's WebView itself (freed together with the tab). */
    private static final class State {
        boolean on;
        ScriptHandler script; // to remove the script when switching back
    }

    private PageZoom() {
        // Only static methods.
    }

    /** @return the saved setting. */
    public static boolean isOn(SharedPreferences prefs) {
        return prefs.getBoolean(PREF, false);
    }

    /** Saves the setting (call {@link #sync} for the open tabs afterwards). */
    public static void setOn(SharedPreferences prefs, boolean on) {
        prefs.edit().putBoolean(PREF, on).apply();
    }

    /** @return true if a pinch on this tab should zoom the page (the app's text-size pinch stays out). */
    public static boolean isActive(WebView webView) {
        Object tag = webView.getTag(R.id.tag_page_zoom);
        return tag instanceof State && ((State) tag).on;
    }

    /**
     * Sets a tab up for page zoom or text-size pinch. Call while configuring a new tab (before it loads),
     * or through {@link #sync} for a tab that is already open.
     */
    public static void apply(Context context, WebView webView, ProviderSpec provider, boolean on) {
        if (isActive(webView) == on) {
            return; // nothing changes (a new tab starts as "off": nothing to undo)
        }
        Object tag = webView.getTag(R.id.tag_page_zoom);
        State state = tag instanceof State ? (State) tag : new State();
        webView.setTag(R.id.tag_page_zoom, state);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            if (on) {
                // Only the provider's own https sites get it (the same allow-list idea as DesktopMode).
                Set<String> rules = new LinkedHashSet<>();
                for (String domain : provider.inAppDomains()) {
                    rules.add("https://" + domain);
                    rules.add("https://*." + domain);
                }
                state.script = WebViewCompat.addDocumentStartJavaScript(webView,
                        Scripts.get(context, Scripts.ZOOM_VIEWPORT), rules);
            } else if (state.script != null) {
                state.script.remove();
                state.script = null;
            }
        }
        state.on = on;
    }

    /**
     * Makes an open tab follow the setting. A tab that changes is reloaded once: the zoom limits come
     * from the page's viewport when it loads (see the class comment).
     */
    public static void sync(Context context, WebView tab, ProviderSpec provider, boolean on) {
        if (tab != null && isActive(tab) != on) {
            apply(context, tab, provider, on);
            tab.reload();
        }
    }

    /**
     * Call from the tab's onPageFinished. Only a fallback for old WebViews without document-start
     * scripts (best effort: see the class comment); with them it just re-applies, which changes nothing.
     */
    public static void onPageFinished(Context context, WebView webView) {
        if (isActive(webView)) {
            webView.evaluateJavascript(Scripts.get(context, Scripts.ZOOM_VIEWPORT), null);
        }
    }
}
