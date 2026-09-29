package org.llmcentral.app.web;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.RequiresApi;

/**
 * WebViewClient for the app's own pop-up pages (settings, offline chat viewer, script manager, prompt
 * box). Those pages are local HTML files from {@code assets/} shown inside a dialog.
 *
 * <p><b>Why these dialogs need a client now:</b> they used to have no WebViewClient at all. That has
 * a hidden danger: when the WebView renderer process dies (see {@link WebViewRecovery}), a WebView
 * without a client can't handle it, so Android kills the whole app, even if the dialog isn't
 * visible. This client handles it by closing the dialog.</p>
 *
 * <p><b>Keeping link behaviour the same:</b> without a client, Android opened any link tapped in these
 * pages in an external app (normally the browser); e.g. links inside saved chats in the offline viewer.
 * Setting a client would normally make links open <i>inside</i> the dialog instead, which would also
 * expose the dialog's JavaScript bridge to that website. So {@link #shouldOverrideUrlLoading} sends
 * web links to the browser explicitly and only lets local content load in the dialog.</p>
 */
public class DialogWebViewClient extends WebViewClient {

    private static final String TAG = "DialogWebViewClient";

    private final Runnable onRendererGone;

    /**
     * @param onRendererGone what to do after the dead WebView has been discarded; normally
     *                       {@code dialog::dismiss}. Runs on the main thread.
     */
    public DialogWebViewClient(Runnable onRendererGone) {
        this.onRendererGone = onRendererGone;
    }

    // Android 7.0+ (API 24, our minSdk) calls this version.
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return handleLink(view, request.getUrl());
    }

    // Older signature, kept because some WebView versions still call it. "Deprecated" means
    // Android recommends the version above, not that it stopped working.
    @Override
    @SuppressWarnings("deprecation")
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return handleLink(view, Uri.parse(url));
    }

    /**
     * @return true = "the app handled it, WebView must NOT load it"; false = "load it in this WebView".
     */
    private boolean handleLink(WebView view, Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        switch (scheme) {
            case "file":        // our own asset pages
            case "about":       // about:blank
            case "data":
            case "blob":
            case "javascript":
                return false;
            default:
                // http(s), mailto:, tel:, … → hand over to the system (browser, mail app, …).
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    view.getContext().startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.w(TAG, "No app can open link with scheme " + scheme);
                }
                return true; // never load outside content inside the dialog
        }
    }

    /**
     * Called (Android 8.0+) when the renderer process behind this WebView died.
     * Returning true tells Android "handled, don't kill the app".
     */
    @Override
    @RequiresApi(Build.VERSION_CODES.O)
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        Log.w(TAG, "Dialog WebView renderer gone (crashed=" + detail.didCrash() + "); closing dialog");
        WebViewRecovery.discard(view);
        if (onRendererGone != null) {
            onRendererGone.run();
        }
        return true;
    }
}
