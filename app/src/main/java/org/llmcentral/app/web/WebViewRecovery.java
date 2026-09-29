package org.llmcentral.app.web;

import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebView;

/**
 * Helpers for recovering when a WebView's <b>renderer process</b> dies.
 *
 * <p><b>Background (important Android concept):</b> since Android 8.0 a WebView doesn't draw web pages
 * inside the app's own process. The page runs in a separate "renderer" process (for security and
 * stability), and in practice all WebViews of an app share one renderer. That process can disappear:
 * Android may kill it to free memory (common when the app is in the background), or it can crash.
 * The app is then told through {@code WebViewClient.onRenderProcessGone()}.
 * If a WebView doesn't handle that callback (or has no WebViewClient at all), Android kills the
 * <b>whole app</b>. Once its renderer is gone a WebView object is dead for good: it can't be reloaded,
 * only removed, destroyed, and replaced by a new one.
 * Docs: https://developer.android.com/develop/ui/views/layout/webapps/managing-webview#termination-handle</p>
 *
 * <p>These methods only deal with the {@link WebView} objects. What to load afterwards is decided by
 * the code that owns the WebView (MainActivity, the dialogs, DocConverter).</p>
 */
public final class WebViewRecovery {

    private WebViewRecovery() {
        // Only static methods.
    }

    /**
     * Removes a dead WebView from the screen and frees its memory. Must be called on the main (UI)
     * thread, which is where {@code onRenderProcessGone} is delivered.
     */
    public static void discard(WebView deadView) {
        ViewParent parent = deadView.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(deadView);
        }
        // destroy() releases the WebView's internal resources. Never use the object after this.
        deadView.destroy();
    }

    /**
     * Creates a new, empty WebView and puts it exactly where the dead one was (same parent, same
     * position among its siblings, same size/layout rules and same view id), then discards the dead one.
     *
     * <p>The new WebView has default settings: the caller must configure it (settings, clients,
     * JavaScript bridge...) and load a page.</p>
     *
     * @return the replacement WebView, already attached to the screen if the dead one was
     */
    public static WebView replace(WebView deadView) {
        WebView fresh = new WebView(deadView.getContext());
        fresh.setId(deadView.getId());
        ViewParent parent = deadView.getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            int index = group.indexOfChild(deadView);
            ViewGroup.LayoutParams params = deadView.getLayoutParams();
            group.removeView(deadView);
            // Same index keeps the drawing order, e.g. the progress bar stays on top of the page.
            group.addView(fresh, index, params);
        }
        deadView.destroy();
        return fresh;
    }
}
