package org.llmcentral.app.web;

import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import org.llmcentral.app.provider.ProviderSpec;
import org.llmcentral.app.provider.Providers;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps one WebView per provider ("tab") and switches between them like browser tabs
 * (PROJECT.md R-SW-3, §5.3).
 *
 * <p><b>How switching works:</b> all loaded WebViews sit on top of each other in the same container
 * (the layout area under the top bar). Only the selected one is {@code VISIBLE}; the others are
 * {@code GONE} (hidden and taking no space). Switching just flips visibility, so a tab you return to
 * is exactly as you left it: same chat, same scroll position, even half-typed text. No reload.</p>
 *
 * <p><b>Memory:</b> at most {@code maxLiveTabs} tabs stay loaded (default 3). When a new one would
 * exceed that, the least recently used one is closed and simply reloads when opened again
 * (see {@link TabLru}). duck.ai is the "home" tab: it's the WebView from the layout, it carries the
 * app's extra features, and it's never closed.</p>
 *
 * <p><b>Accounts:</b> this round has one account per provider. Each provider's login cookies are
 * separate anyway, because browsers keep cookies per website. Several accounts per provider
 * (WebView profiles) come in a later round.</p>
 */
public final class TabManager {

    /** Applies settings, clients and listeners to a new provider WebView (implemented by MainActivity). */
    public interface Configurer {
        void configure(WebView webView, ProviderSpec provider);
    }

    /** Told when the selected provider changes (MainActivity updates the top bar). */
    public interface Listener {
        void onProviderShown(ProviderSpec provider);
        /** A tab crashed repeatedly and was closed; MainActivity shows a message. */
        void onTabGaveUp(ProviderSpec provider);
    }

    private final ViewGroup container;
    private final Configurer configurer;
    private final Listener listener;
    private final Map<String, WebView> liveTabs = new HashMap<>();
    private final TabLru lru = new TabLru();
    private final ArrayDeque<Long> recoveryTimes = new ArrayDeque<>();
    private ProviderSpec current = Providers.DUCK;
    private int maxLiveTabs;

    /**
     * @param duckView    the WebView from the layout, already configured; becomes the duck.ai tab
     * @param maxLiveTabs how many tabs may stay loaded (setting {@code max_live_tabs}, 1–5)
     */
    public TabManager(WebView duckView, Configurer configurer, Listener listener, int maxLiveTabs) {
        this.container = (ViewGroup) duckView.getParent();
        this.configurer = configurer;
        this.listener = listener;
        this.maxLiveTabs = maxLiveTabs;
        liveTabs.put(Providers.DUCK_ID, duckView);
        lru.touch(Providers.DUCK_ID);
    }

    /** The provider whose tab is on screen. */
    public ProviderSpec current() {
        return current;
    }

    /** The WebView on screen. */
    public WebView currentWebView() {
        return liveTabs.get(current.id);
    }

    /** @return the provider a WebView belongs to, or null if it isn't one of our tabs. */
    public ProviderSpec providerOf(WebView webView) {
        for (Map.Entry<String, WebView> e : liveTabs.entrySet()) {
            if (e.getValue() == webView) {
                return Providers.byId(e.getKey());
            }
        }
        return null;
    }

    /**
     * Shows a provider's tab, creating and loading it on first use.
     *
     * @return the provider's WebView (now visible)
     */
    public WebView show(ProviderSpec provider) {
        WebView target = liveTabs.get(provider.id);
        boolean isNew = target == null;
        if (isNew) {
            target = createTab(provider);
        }
        if (provider != current) {
            WebView previous = currentWebView();
            if (previous != null) {
                previous.setVisibility(View.GONE);
                // Pauses animations, video and similar work in the hidden page (JavaScript keeps running).
                previous.onPause();
            }
        }
        target.setVisibility(View.VISIBLE);
        target.onResume();
        current = provider;
        lru.touch(provider.id);
        if (isNew) {
            target.loadUrl(provider.homeUrl);
        }
        closeExcessTabs();
        if (listener != null) {
            listener.onProviderShown(provider);
        }
        return target;
    }

    /** Changes the "tabs kept loaded" limit and closes extra tabs right away if it was lowered. */
    public void setMaxLiveTabs(int maxLiveTabs) {
        this.maxLiveTabs = maxLiveTabs;
        closeExcessTabs();
    }

    /**
     * MainActivity replaced the duck.ai WebView after its renderer died: remember the new one and
     * keep it hidden if another tab is on screen.
     */
    public void replaceDuckView(WebView fresh) {
        liveTabs.put(Providers.DUCK_ID, fresh);
        fresh.setVisibility(current.isDuck() ? View.VISIBLE : View.GONE);
    }

    /**
     * The renderer behind a (non-duck.ai) tab died (see WebViewRecovery for the background).
     * A hidden tab is simply closed (it reloads when opened again). The visible tab is replaced by a
     * fresh WebView that reloads the same page, unless it crashed 3 times within a minute: then
     * the tab is closed and duck.ai is shown instead, to avoid an endless crash loop.
     */
    public void onRendererGone(WebView dead) {
        ProviderSpec provider = providerOf(dead);
        if (provider == null) {
            WebViewRecovery.discard(dead);
            return;
        }
        if (provider != current) {
            closeTab(provider.id);
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        recoveryTimes.addLast(now);
        while (!recoveryTimes.isEmpty() && now - recoveryTimes.peekFirst() > 60_000) {
            recoveryTimes.removeFirst();
        }
        if (recoveryTimes.size() >= 3) {
            closeTab(provider.id);
            show(Providers.DUCK);
            if (listener != null) {
                listener.onTabGaveUp(provider);
            }
            return;
        }
        String lastUrl = dead.getUrl(); // read before destroying
        WebView fresh = WebViewRecovery.replace(dead);
        configurer.configure(fresh, provider);
        liveTabs.put(provider.id, fresh);
        boolean isWebPage = lastUrl != null && lastUrl.startsWith("https://");
        fresh.loadUrl(isWebPage ? lastUrl : provider.homeUrl);
    }

    /** Frees all provider WebViews except duck.ai's (the activity destroys that one). Call from onDestroy. */
    public void destroyProviderTabs() {
        for (String id : liveTabs.keySet().toArray(new String[0])) {
            if (!Providers.DUCK_ID.equals(id)) {
                closeTab(id);
            }
        }
    }

    private WebView createTab(ProviderSpec provider) {
        WebView webView = new WebView(container.getContext());
        // Index 0 = drawn first (at the back), so the progress bar in the same container stays on top.
        container.addView(webView, 0, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        configurer.configure(webView, provider);
        liveTabs.put(provider.id, webView);
        return webView;
    }

    private void closeExcessTabs() {
        for (String id : lru.idsToClose(maxLiveTabs, Providers.DUCK_ID)) {
            closeTab(id);
        }
    }

    private void closeTab(String id) {
        WebView webView = liveTabs.remove(id);
        lru.remove(id);
        if (webView != null) {
            WebViewRecovery.discard(webView); // removes it from the screen and frees its memory
        }
    }
}
