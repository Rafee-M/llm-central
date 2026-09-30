package org.llmcentral.app.web;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.WebView;

import org.json.JSONObject;
import org.llmcentral.app.provider.ProviderSpec;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Types a prompt into the message box of ChatGPT, Claude, DeepSeek or Z.ai after their page has
 * loaded (PROJECT.md R-AST-5). duck.ai doesn't need this: its "handoff" link does it.
 *
 * <p><b>How it works, without giving these websites any bridge to the app:</b> after each page
 * load in the tab, it runs {@code assets/js/prefill_prompt.js} with
 * {@link WebView#evaluateJavascript}. That method hands back the script's answer ("filled", "busy"
 * or "none") to Java. On "none" (the page is still building its message box) it asks again every
 * {@value #RETRY_MS} ms, up to {@value #TRIES_PER_PAGE} times per page. Docs:
 * https://developer.android.com/reference/android/webkit/WebView#evaluateJavascript(java.lang.String,%20android.webkit.ValueCallback%3Cjava.lang.String%3E)</p>
 *
 * <p><b>Signing in first:</b> if the account isn't logged in, the site shows its sign-in page. The
 * request stays waiting for {@value #KEEP_MS} ms (3 minutes) and is tried again on every page load
 * of the provider's own site, so after signing in the prompt still arrives. Sign-in forms use
 * {@code <input>} fields, which the script never fills.</p>
 *
 * <p>Runs on the main thread only (WebView's rule), so no locking is needed.</p>
 */
public final class PromptFiller {

    static final long RETRY_MS = 500;
    static final int TRIES_PER_PAGE = 30;          // 15 seconds per page
    static final long KEEP_MS = 3 * 60 * 1000;     // give up after 3 minutes

    /** One waiting prompt for one tab. */
    private static final class Request {
        final ProviderSpec provider;
        final String script;
        final long since = SystemClock.elapsedRealtime();
        int page; // counts page loads, so an older page's retries stop when a new page loads

        Request(ProviderSpec provider, String script) {
            this.provider = provider;
            this.script = script;
        }
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    // "Weak": a WebView that was closed and destroyed can be freed; its request disappears with it.
    private final Map<WebView, Request> waiting = new WeakHashMap<>();

    public PromptFiller(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Remembers a prompt for the tab; it's typed in after the next page load (see
     * {@link #onPageFinished}). Replaces a request that was still waiting for the same tab.
     */
    public void request(WebView tab, ProviderSpec provider, String prompt) {
        // JSONObject.quote turns the prompt into a safe JavaScript string literal (CLAUDE.md §7).
        String script = Scripts.get(context, Scripts.PREFILL_PROMPT) + "(" + JSONObject.quote(prompt) + ")";
        waiting.put(tab, new Request(provider, script));
    }

    /** Call from the tab's WebViewClient.onPageFinished. */
    public void onPageFinished(WebView tab, String url) {
        Request request = waiting.get(tab);
        if (request == null) {
            return;
        }
        if (SystemClock.elapsedRealtime() - request.since > KEEP_MS) {
            waiting.remove(tab); // too old: the user has moved on
            return;
        }
        // Only on the provider's own https pages (not a sign-in page of another site).
        Uri uri = url == null ? null : Uri.parse(url);
        if (uri == null || !"https".equals(uri.getScheme()) || !request.provider.ownsHost(uri.getHost())) {
            return;
        }
        request.page++;
        tryFill(tab, request, request.page, 0);
    }

    private void tryFill(WebView tab, Request request, int page, int attempt) {
        if (waiting.get(tab) != request || request.page != page || tab.getParent() == null) {
            return; // done, replaced, a newer page loaded, or the tab was closed
        }
        tab.evaluateJavascript(request.script, result -> {
            // evaluateJavascript returns the script's result as JSON text: "\"filled\"" etc.
            if ("\"filled\"".equals(result) || "\"busy\"".equals(result)) {
                if (waiting.get(tab) == request) {
                    waiting.remove(tab);
                }
            } else if (attempt + 1 < TRIES_PER_PAGE) {
                handler.postDelayed(() -> tryFill(tab, request, page, attempt + 1), RETRY_MS);
            }
        });
    }
}
