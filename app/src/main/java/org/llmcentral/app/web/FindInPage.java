package org.llmcentral.app.web;

import android.view.KeyEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Rules for the top bar's search button, "find in page" (plain Java, tested by FindInPageTest).
 *
 * <p><b>Two ways to search.</b> Some chat sites have their own search that opens with Ctrl+F (owner, 2026-10-29:
 * claude.ai). For those, the button sends Ctrl+F to the page, exactly like a hardware keyboard would. Every other
 * provider gets the app's own find bar ({@code ui/FindBar}), which uses WebView's built-in find: Chromium's own
 * find-in-page engine, the same one behind Chrome's "Find in page" (WebView.findAllAsync).</p>
 *
 * <p><b>Safety net.</b> If a site in {@link #SITE_SEARCH} ever stops reacting to Ctrl+F, Chromium hands the key back
 * to the app (it does that only for keys the page did not "consume", Chromium {@code render_widget_host_impl.cc}
 * {@code OnKeyboardEventAck} → {@code WebViewClient.onUnhandledKeyEvent}), and the app opens its own bar instead.
 * So a wrong entry here costs nothing worse than the app's bar.</p>
 *
 * <p><b>New providers</b> (e.g. Gemini, being added in a parallel session) are not in the list, so they get the
 * app's bar at once. To give one its site search, check on a phone that Ctrl+F opens the site's own search, then add
 * the provider's id below.</p>
 */
public final class FindInPage {

    /** Provider ids whose site opens its own search on Ctrl+F (checked by the owner on a phone). */
    static final Set<String> SITE_SEARCH = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "claude")));

    private FindInPage() {
    }

    /** @return true if this provider's search button should send Ctrl+F to the page instead of opening the app's bar. */
    public static boolean usesSiteSearch(String providerId) {
        return providerId != null && SITE_SEARCH.contains(providerId);
    }

    /**
     * @return true for Ctrl+F being pressed (a hardware keyboard, or the key the search button sends). Ctrl+Shift+F
     *         and Ctrl+Alt+F are left to the page: they mean other things on some sites.
     */
    public static boolean isFindShortcut(int action, int keyCode, int metaState) {
        return action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_F
                && (metaState & KeyEvent.META_CTRL_ON) != 0
                && (metaState & (KeyEvent.META_SHIFT_ON | KeyEvent.META_ALT_ON)) == 0;
    }

    /**
     * The find bar's count, e.g. "3/12".
     *
     * @param activeOrdinal the current match as WebView reports it: counted from 0
     * @param total         the number of matches found so far
     * @param done          false while Chromium is still counting (it counts in small slices so the page never
     *                      freezes, and reports partial totals on the way)
     * @return "current/total", "0/0" when nothing was found, or "" while nothing was found yet
     */
    public static String countText(int activeOrdinal, int total, boolean done) {
        if (total <= 0) {
            return done ? "0/0" : "";
        }
        return (activeOrdinal + 1) + "/" + total;
    }
}
