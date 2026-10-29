package org.llmcentral.app.web;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import org.llmcentral.app.R;
import org.llmcentral.app.account.Account;
import org.llmcentral.app.provider.ProviderSpec;
import org.llmcentral.app.theme.Scheme;
import org.llmcentral.app.theme.ThemeManager;
import org.llmcentral.app.ui.ProviderBadge;

/**
 * Pop-up windows ({@code window.open}) on ChatGPT, Claude, DeepSeek and Z.ai, mainly for signing
 * in: "Continue with Microsoft/Apple/GitHub" often opens the login page in a small new window that
 * reports back to the chat page when it's done (PROJECT.md §5.5).
 *
 * <p><b>Before:</b> the tabs didn't support extra windows, so WebView loaded such a pop-up's page
 * <i>in place of</i> the chat page. The login then had no "opener" window to report back to and
 * could get stuck.</p>
 *
 * <p><b>Now:</b> with {@link WebSettings#setSupportMultipleWindows} on, WebView asks
 * {@code WebChromeClient.onCreateWindow} for a new WebView. {@link #open} makes one:</p>
 * <ol>
 *   <li>puts it into the <b>same account container</b> ({@link Profiles#attach}) before anything else,
 *       so the login's cookies land in the right account;</li>
 *   <li>hands it to WebView through the {@code WebViewTransport} "envelope" in the message;</li>
 *   <li>waits for the pop-up's first page. If it belongs to the provider (its own or its login
 *       sites, or the shared Apple/Google sign-in pages: {@link ProviderSpec#loadsInApp}), the pop-up is shown as a full-screen sheet with a
 *       close button. Anything else was just a link that opens a new window: it goes to the
 *       browser (only if the user tapped something) and the pop-up is thrown away unseen.</li>
 * </ol>
 * <p>The page closing its window ({@code window.close()} → {@code onCloseWindow}), the close button
 * and Back all close the sheet. Docs:
 * https://developer.android.com/reference/android/webkit/WebChromeClient#onCreateWindow(android.webkit.WebView,%20boolean,%20boolean,%20android.os.Message)</p>
 *
 * <p><b>Downloads</b> (2026-10-29): some links open a file in a new window, e.g. Claude's uploaded files
 * ({@code claude.ai/api/…/files/…}). The address is the provider's, so the sheet appears, but the answer is a file:
 * WebView turns the navigation into a download and hands it to the pop-up's {@code DownloadListener}, and calls
 * nothing at all when there is none (Chromium {@code WebViewContentsClientAdapter.onDownloadStart}). So the pop-up
 * gets a listener that passes the file to the tab that opened it (same account, same cookies: {@code
 * MainActivity.startDownload}), and the sheet closes again if it never showed a web page: a download never "commits"
 * a page, so {@code onPageCommitVisible} never comes for it (Chromium {@code AwWebContentsObserver}). A short flash of
 * the sheet before it closes is expected. A sign-in page the user is looking at stays open.</p>
 *
 * <p>[unverified] Whether each provider's sign-in pop-up works inside WebView must be tried on a
 * phone; the androidx.webkit docs say nothing about pop-ups and profiles.</p>
 */
public final class PopupWindows {

    private static final String TAG = "PopupWindows";

    private PopupWindows() {
        // Only static methods.
    }

    /** What to do with an address the pop-up wants to load (see {@link #decide}). */
    enum Decision {
        /** Load it in the pop-up (and show the pop-up if it isn't yet). */
        LOAD,
        /** Open it in the user's browser instead. */
        BROWSER,
        /** Ignore it. */
        BLOCK
    }

    /**
     * The pop-up's rule, in plain Java so PopupWindowsTest can check it.
     *
     * @param url           the address the pop-up wants to load
     * @param shown         true once the pop-up is on screen (its first real page was the provider's)
     * @param isUserGesture true if the user tapped something to open the pop-up
     */
    static Decision decide(String url, ProviderSpec provider, boolean shown, boolean isUserGesture) {
        String scheme;
        String host;
        try {
            java.net.URI uri = new java.net.URI(url);
            scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            host = uri.getHost();
        } catch (Exception e) {
            return Decision.BLOCK;
        }
        if (scheme.equals("about") || scheme.equals("blob") || scheme.equals("data")) {
            return Decision.LOAD; // blank start page or page-made content: stays in the pop-up
        }
        if ((scheme.equals("https") || scheme.equals("http")) && provider.loadsInApp(host)) {
            return Decision.LOAD;
        }
        // Another website (or another kind of link). After a tap, or from a sign-in page the user
        // is looking at: the browser. From a script nobody asked for (perhaps an advert): ignore.
        boolean web = scheme.equals("https") || scheme.equals("http");
        return web && (isUserGesture || shown) ? Decision.BROWSER : Decision.BLOCK;
    }

    /**
     * @return true for an http(s) address: only such a page counts as "the pop-up showed something" (not the blank
     *         start page or a page-made blob:/data: document).
     */
    static boolean isWebPage(String url) {
        return url != null && (url.regionMatches(true, 0, "https:", 0, 6) || url.regionMatches(true, 0, "http:", 0, 5));
    }

    /** Lets a provider tab open pop-up windows (call while configuring the tab). */
    public static void enable(WebSettings settings) {
        settings.setSupportMultipleWindows(true);
        // Sign-in pages sometimes open their window from a script rather than directly from a tap.
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
    }

    /**
     * Handles {@code onCreateWindow} for a provider tab.
     *
     * @param opener        the tab that wants a new window
     * @param account       the tab's account (its container is used for the pop-up too)
     * @param isUserGesture true if the user tapped something to open it
     * @param resultMsg     WebView's message carrying the {@code WebViewTransport}
     * @param downloads     handles files the pop-up downloads (the opener tab's download code)
     * @return true if a pop-up was created (WebView then loads the page into it)
     */
    public static boolean open(WebView opener, ProviderSpec provider, Account account,
                               boolean isUserGesture, Message resultMsg, DownloadListener downloads) {
        Context context = opener.getContext();
        WebView popup = new WebView(context);
        try {
            Profiles.attach(popup, account); // first, before anything else touches the WebView
        } catch (RuntimeException e) {
            Log.w(TAG, "Couldn't put the pop-up into the account's container", e);
            popup.destroy();
            return false; // the page's window.open() gets null, as with a pop-up blocker
        }
        configure(popup, opener);
        PopupSheet sheet = new PopupSheet(context, popup, provider, isUserGesture);
        popup.setWebViewClient(sheet.client);
        popup.setWebChromeClient(sheet.chrome);
        popup.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            downloads.onDownloadStart(url, userAgent, contentDisposition, mimetype, contentLength);
            sheet.closeIfNoPage(); // the file was all it opened: don't leave an empty sheet behind
        });

        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(popup);
        resultMsg.sendToTarget();
        // A pop-up that never loads a real page (stays blank) is freed after 30 seconds.
        new Handler(Looper.getMainLooper()).postDelayed(sheet::closeIfNeverShown, 30_000);
        return true;
    }

    // The pop-up shows the provider's own pages, which need JavaScript (like the tab itself).
    @SuppressLint("SetJavaScriptEnabled")
    private static void configure(WebView popup, WebView opener) {
        WebSettings s = popup.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportMultipleWindows(false); // a pop-up's own pop-ups load in the same sheet
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSaveFormData(false);
        s.setGeolocationEnabled(false);
        // The same third-party cookie choice as the tab that opened it (Settings → "Allow third-party cookies",
        // web/ThirdPartyCookies; the tabs are kept up to date, so this copies the current setting).
        CookieManager cookies = Profiles.cookieManager(popup);
        cookies.setAcceptThirdPartyCookies(popup, cookies.acceptThirdPartyCookies(opener));
    }

    /** One pop-up: its WebView, the sheet it's shown in, and its clients. */
    private static final class PopupSheet {
        private final Context context;
        private final WebView popup;
        private final ProviderSpec provider;
        private final boolean isUserGesture;
        private Dialog dialog;       // created when the first page turns out to be the provider's
        private TextView title;
        private boolean decided;     // first page seen?
        private boolean pageShown;   // a web page was committed and drawn (a download never is)
        private boolean closed;

        PopupSheet(Context context, WebView popup, ProviderSpec provider, boolean isUserGesture) {
            this.context = context;
            this.popup = popup;
            this.provider = provider;
            this.isUserGesture = isUserGesture;
        }

        final WebViewClient client = new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !allow(request.getUrl());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                // Also checked here: the very first page of a new window doesn't always pass
                // through shouldOverrideUrlLoading.
                Uri uri = Uri.parse(url);
                if (!allow(uri)) {
                    view.stopLoading();
                    return;
                }
                if (title != null && uri.getHost() != null) {
                    title.setText(uri.getHost());
                }
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                if (isWebPage(url)) {
                    pageShown = true;
                }
            }

            @Override
            @RequiresApi(Build.VERSION_CODES.O)
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                close(); // never let a dead pop-up take the app down (all WebViews share one renderer)
                return true;
            }
        };

        final WebChromeClient chrome = new WebChromeClient() {
            @Override
            public void onCloseWindow(WebView window) {
                close(); // the page called window.close(), e.g. after signing in
            }
        };

        /**
         * @return true if the address may load in the pop-up. The first address decides whether the
         *         pop-up is shown at all.
         */
        private boolean allow(Uri uri) {
            if (closed) {
                return false;
            }
            Decision decision = decide(uri.toString(), provider, decided, isUserGesture);
            boolean isPage = isWebPage(uri.toString());
            if (decision == Decision.LOAD) {
                if (!decided && isPage) {
                    decided = true; // the first real page is the provider's: show the pop-up
                    show(uri.getHost());
                }
                return true;
            }
            if (decision == Decision.BROWSER) {
                try {
                    context.startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (RuntimeException e) {
                    Log.w(TAG, "No app to open the link");
                }
            }
            if (!decided) {
                decided = true;
                close(); // it was only ever meant to open another site (or nothing at all)
            }
            return false;
        }

        /** Shows the pop-up as a full-screen sheet: a bar with a close button, the page below. */
        private void show(String host) {
            Scheme scheme = ThemeManager.scheme(context);
            dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(scheme.get(Scheme.SURFACE));

            LinearLayout bar = new LinearLayout(context);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(ProviderBadge.dp(context, 4), 0, ProviderBadge.dp(context, 16), 0);
            ImageButton closeButton = new ImageButton(context);
            closeButton.setImageResource(R.drawable.ic_close);
            closeButton.setImageTintList(ColorStateList.valueOf(scheme.get(Scheme.ON_SURFACE_VARIANT)));
            TypedValue ripple = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
            closeButton.setBackgroundResource(ripple.resourceId);
            closeButton.setContentDescription(context.getString(R.string.close_window));
            closeButton.setOnClickListener(v -> close());
            int button = ProviderBadge.dp(context, 48);
            bar.addView(closeButton, new LinearLayout.LayoutParams(button, button));
            title = new TextView(context);
            title.setText(host);
            title.setTextColor(scheme.get(Scheme.ON_SURFACE));
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            LinearLayout.LayoutParams titleParams =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            titleParams.setMarginStart(ProviderBadge.dp(context, 8));
            bar.addView(title, titleParams);
            root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, button));
            root.addView(popup, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            dialog.setContentView(root);
            // Back goes back inside the pop-up first, then closes it.
            dialog.setOnKeyListener((d, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_BACK || event.getAction() != KeyEvent.ACTION_UP) {
                    return keyCode == KeyEvent.KEYCODE_BACK;
                }
                if (popup.canGoBack()) {
                    popup.goBack();
                } else {
                    close();
                }
                return true;
            });
            dialog.setOnDismissListener(d -> close());
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                window.setBackgroundDrawable(new ColorDrawable(scheme.get(Scheme.SURFACE)));
            }
        }

        void closeIfNeverShown() {
            if (!decided) {
                close();
            }
        }

        void closeIfNoPage() {
            if (!pageShown) {
                close();
            }
        }

        /** Closes the sheet (if shown) and frees the pop-up's WebView. Safe to call more than once. */
        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
            // Destroy after the current WebView callback has finished (never from inside it). A
            // Handler, not popup.post(): a pop-up that was never shown isn't attached to a window,
            // and View.post would then wait forever.
            new Handler(Looper.getMainLooper()).post(() -> WebViewRecovery.discard(popup));
        }
    }
}
