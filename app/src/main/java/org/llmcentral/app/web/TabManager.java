package org.llmcentral.app.web;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import org.llmcentral.app.R;
import org.llmcentral.app.account.Account;
import org.llmcentral.app.account.AccountStore;
import org.llmcentral.app.provider.ProviderSpec;
import org.llmcentral.app.provider.Providers;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps one WebView per <b>account</b> ("tab") and switches between them like browser tabs
 * (PROJECT.md R-SW-3, §5.3).
 *
 * <p><b>How switching works:</b> all loaded WebViews sit on top of each other in the same container
 * (the layout area under the top bar). Only the selected one is {@code VISIBLE}; the others are
 * {@code GONE} (hidden and taking no space). Switching just flips visibility, so a tab you return to
 * is exactly as you left it: same chat, same scroll position, even half-typed text. No reload.</p>
 *
 * <p><b>Containers:</b> every account's WebView is put into that account's own WebView profile
 * ({@link Profiles#attach}) before anything else happens to it, so two accounts of the same provider
 * never share cookies or site data.</p>
 *
 * <p><b>Memory:</b> at most {@code maxLiveTabs} tabs stay loaded (default 3). When a new one would
 * exceed that, the least recently used one is closed and simply reloads when opened again
 * (see {@link TabLru}). Hidden tabs are paused and marked "less important", so Android frees their
 * memory first when it runs low. duck.ai is the "home" tab: it's the WebView from the layout, it
 * carries the app's extra features, and it's never closed.</p>
 *
 * <p><b>Unloading:</b> the ✕ on an account's row in the account menu closes that account's tab to free
 * its memory ({@link #unload}). The account on screen stays selected; a placeholder
 * (ui/UnloadedPlaceholder: black, app icon) is shown instead until it is opened again.</p>
 */
public final class TabManager {

    /**
     * Applies settings, clients and listeners to a new tab's WebView (implemented by MainActivity).
     * Gets the account (not only its provider) because some choices are per account, e.g. the
     * desktop site (web/DesktopMode).
     */
    public interface Configurer {
        void configure(WebView webView, Account account);
    }

    /** Told when the visible account changes (MainActivity updates the top bar). */
    public interface Listener {
        void onAccountShown(Account account);
        /** A tab crashed repeatedly and was closed; MainActivity shows a message. */
        void onTabGaveUp(Account account);
    }

    private final ViewGroup container;
    private final Configurer configurer;
    private final Listener listener;
    // account id → its WebView / its Account (the Account object is refreshed after renames).
    private final Map<String, WebView> liveTabs = new HashMap<>();
    private final Map<String, Account> liveAccounts = new HashMap<>();
    private final TabLru lru = new TabLru();
    private final ArrayDeque<Long> recoveryTimes = new ArrayDeque<>();
    private Account current;
    private int maxLiveTabs;
    // Shown instead of a page while the account on screen is unloaded (see unload); may be null.
    private View placeholder;
    // Shown instead of a page that failed to load (see markLoadFailed, O-M4); may be null.
    private View retryPlaceholder;
    // duck.ai's WebView exists from the start (it's in the layout), but its page loads only when duck.ai is needed
    // (web/DuckLoading): until then, and after the ✕ unloads it, duck.ai counts as "not loaded".
    private boolean duckPageLoaded;

    /**
     * @param duckView    the WebView from the layout, already configured; becomes the duck.ai tab
     * @param duck        the duck.ai account
     * @param maxLiveTabs how many tabs may stay loaded (setting {@code max_live_tabs}, 1–10)
     */
    public TabManager(WebView duckView, Account duck, Configurer configurer, Listener listener,
                      int maxLiveTabs) {
        this.container = (ViewGroup) duckView.getParent();
        this.configurer = configurer;
        this.listener = listener;
        this.maxLiveTabs = maxLiveTabs;
        this.current = duck;
        liveTabs.put(duck.id, duckView);
        liveAccounts.put(duck.id, duck);
        // Not lru.touch(duck.id) yet: a duck.ai without a page must not take one of the "kept loaded" places.
        // show() counts it once its page loads.
        lowerPriorityWhenHidden(duckView);
    }

    /** The account whose tab is on screen. */
    public Account current() {
        return current;
    }

    /** The provider of the account on screen. */
    public ProviderSpec currentProvider() {
        return Providers.byId(current.providerId);
    }

    /** The WebView on screen. */
    public WebView currentWebView() {
        return liveTabs.get(current.id);
    }

    /** @return the WebViews of all loaded tabs (e.g. to clear their cache). */
    public java.util.List<WebView> liveWebViews() {
        return new java.util.ArrayList<>(liveTabs.values());
    }

    /** @return the profile names used by the loaded tabs (duck.ai's default profile isn't named). */
    public java.util.Set<String> liveProfileNames() {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String id : liveTabs.keySet()) {
            Account account = liveAccounts.get(id);
            if (account != null && account.profileName() != null) {
                names.add(account.profileName());
            }
        }
        return names;
    }

    /**
     * The view to show while the account on screen is unloaded. It must already be in the tab container,
     * drawn above the WebViews; TabManager only switches it between VISIBLE and GONE.
     */
    public void setPlaceholder(View placeholder) {
        this.placeholder = placeholder;
        updatePlaceholder();
    }

    /**
     * The view to show while the page of the account on screen failed to load (O-M4: black, app icon, "Tap to
     * retry"). Like {@link #setPlaceholder}, it must already be in the tab container above the WebViews.
     */
    public void setRetryPlaceholder(View retryPlaceholder) {
        this.retryPlaceholder = retryPlaceholder;
        updatePlaceholder();
    }

    /**
     * A tab's page couldn't be loaded (web/LoadErrors): remember it on the tab's own WebView (so it goes away
     * with the tab) and cover the tab with the retry screen while it is on screen, also later when the user
     * switches to it. It stays until a page is drawn in the tab without an error ({@link #pageVisible}).
     */
    public void markLoadFailed(WebView view, String url) {
        LoadErrors.TabState state = loadState(view);
        if (state == null) {
            state = new LoadErrors.TabState();
            view.setTag(R.id.tag_load_failed, state);
        }
        state.failed(url);
        updatePlaceholder();
    }

    /** WebViewClient.onPageStarted: a new load began in this tab (the retry screen stays until it is drawn). */
    public void pageStarted(WebView view) {
        LoadErrors.TabState state = loadState(view);
        if (state != null) {
            state.pageStarted();
        }
    }

    /**
     * WebViewClient.onPageCommitVisible (and onPageFinished as a backstop): a page is drawn; without an error
     * during its load the retry screen goes.
     */
    public void pageVisible(WebView view) {
        LoadErrors.TabState state = loadState(view);
        if (state != null && state.pageVisible()) {
            updatePlaceholder();
        }
    }

    /**
     * The retry screen was tapped: load the page that failed again (or the provider's home page). The screen
     * stays up meanwhile (WebView's grey error page is under it) and goes once the page is drawn.
     */
    public void retryCurrent() {
        WebView view = currentWebView();
        LoadErrors.TabState state = view == null ? null : loadState(view);
        if (state == null || !state.showsRetry()) {
            return;
        }
        String url = !state.failedUrl().isEmpty() ? state.failedUrl() : Providers.byId(current.providerId).homeUrl;
        view.loadUrl(url);
    }

    private static LoadErrors.TabState loadState(WebView view) {
        Object tag = view.getTag(R.id.tag_load_failed);
        return tag instanceof LoadErrors.TabState ? (LoadErrors.TabState) tag : null;
    }

    /** @return true if the account on screen has no loaded tab (it was unloaded; the placeholder shows). */
    public boolean isCurrentUnloaded() {
        return !isLoaded(current.id);
    }

    /**
     * The account menu's ✕ on an account's row: closes that account's tab (its WebView is destroyed and its
     * memory freed). The account on screen stays selected, so the top bar still names it and the placeholder
     * shows; opening it again (account menu, Reload, or a tap on the placeholder) loads its home page. Any
     * other account simply has no tab any more and loads again when it's picked. duck.ai's tab is the pinned
     * home tab and is never unloaded.
     *
     * @return true if a tab was unloaded; false for duck.ai or an account without a loaded tab
     */
    public boolean unload(String accountId) {
        if (Providers.DUCK_ID.equals(accountId) || !liveTabs.containsKey(accountId)) {
            return false;
        }
        closeTab(accountId);
        updatePlaceholder(); // shows the placeholder if it was the account on screen
        return true;
    }

    /** @return true if the account's tab is loaded (showing it won't load a page). */
    public boolean isLoaded(String accountId) {
        if (Providers.DUCK_ID.equals(accountId)) {
            return duckPageLoaded; // its WebView always exists; what counts is whether its page was loaded
        }
        return liveTabs.containsKey(accountId);
    }

    /**
     * duck.ai's page came back without a load (MainActivity: {@code restoreState} after Android rebuilt the screen):
     * it counts as loaded again.
     */
    public void markDuckLoaded() {
        duckPageLoaded = true;
        lru.touch(Providers.DUCK_ID);
        updatePlaceholder();
    }

    /** @return the account a WebView belongs to, or null if it isn't one of our tabs. */
    public Account accountOf(WebView webView) {
        for (Map.Entry<String, WebView> e : liveTabs.entrySet()) {
            if (e.getValue() == webView) {
                return liveAccounts.get(e.getKey());
            }
        }
        return null;
    }

    /**
     * Shows an account's tab, creating and loading it on first use.
     *
     * @return the account's WebView (now visible)
     */
    public WebView show(Account account) {
        return show(account, null);
    }

    /**
     * Shows an account's tab and opens a page in it, e.g. a new chat for shared text.
     *
     * @param startUrl the page to open, or null to keep the tab's current page. A tab that didn't
     *                 exist yet opens {@code startUrl} directly (or the provider's home page if null),
     *                 so it never loads twice.
     * @return the account's WebView (now visible)
     */
    public WebView show(Account account, String startUrl) {
        WebView target = liveTabs.get(account.id);
        // "New" = its page must be loaded now: a tab that doesn't exist yet, or duck.ai's WebView without a page.
        boolean isNew = target == null || (account.isDuck() && !duckPageLoaded);
        if (target == null) {
            target = createTab(account);
        }
        if (account.isDuck()) {
            duckPageLoaded = true; // set before the load below, so callbacks during it already see "loaded"
        }
        liveAccounts.put(account.id, account); // keep the newest name
        if (!account.id.equals(current.id)) {
            WebView previous = currentWebView();
            if (previous != null) {
                previous.setVisibility(View.GONE);
                // Pauses animations, video and similar work in the hidden page (JavaScript keeps running).
                previous.onPause();
            }
        }
        target.setVisibility(View.VISIBLE);
        target.onResume();
        current = account;
        updatePlaceholder();
        lru.touch(account.id);
        if (isNew || startUrl != null) {
            target.loadUrl(startUrl != null ? startUrl : Providers.byId(account.providerId).homeUrl);
        }
        closeExcessTabs();
        if (listener != null) {
            listener.onAccountShown(account);
        }
        return target;
    }

    /** Changes the "tabs kept loaded" limit and closes extra tabs right away if it was lowered. */
    public void setMaxLiveTabs(int maxLiveTabs) {
        this.maxLiveTabs = maxLiveTabs;
        closeExcessTabs();
    }

    /**
     * The account list changed (settings, or the other screen of the app): use the new names, and
     * close tabs of accounts that no longer exist. If the visible one is gone, duck.ai is shown.
     */
    public void updateAccounts(AccountStore accounts) {
        for (String id : liveTabs.keySet().toArray(new String[0])) {
            Account fresh = accounts.find(id);
            if (fresh == null) {
                closeAccount(id);
            } else {
                liveAccounts.put(id, fresh);
            }
        }
        Account fresh = accounts.find(current.id);
        if (fresh == null) {
            // The account on screen was removed (its tab was closed above, or it was unloaded): duck.ai.
            show(accounts.duck());
            return;
        }
        current = fresh;
        // An unloaded account stays unloaded (placeholder); a loaded one stays as it is.
        if (listener != null) {
            listener.onAccountShown(current); // e.g. refresh the top bar after a rename
        }
    }

    /**
     * Closes an account's tab (destroying its WebView), e.g. before signing it out: its profile
     * can only be wiped once no WebView uses it. If it was on screen, duck.ai is shown instead.
     * The duck.ai tab is never closed.
     */
    public void closeAccount(String accountId) {
        if (Providers.DUCK_ID.equals(accountId) || !liveTabs.containsKey(accountId)) {
            return;
        }
        boolean wasCurrent = accountId.equals(current.id);
        closeTab(accountId);
        if (wasCurrent) {
            show(liveAccounts.get(Providers.DUCK_ID));
        }
    }

    /**
     * Low memory: close every hidden tab except duck.ai (PROJECT.md R-PERF-1). They reload when
     * opened again.
     */
    public void closeHiddenTabs() {
        for (String id : liveTabs.keySet().toArray(new String[0])) {
            if (!id.equals(current.id) && !Providers.DUCK_ID.equals(id)) {
                closeTab(id);
            }
        }
    }

    /**
     * Writes every open account's cookies (logins) to disk now. WebView also does this by itself
     * every few seconds; calling it when the app goes to the background makes sure a fresh login
     * survives even if Android closes the app right after.
     */
    public void flushCookies() {
        for (WebView webView : liveTabs.values()) {
            Profiles.cookieManager(webView).flush();
        }
    }

    /**
     * MainActivity replaced the duck.ai WebView after its renderer died: remember the new one and
     * keep it hidden if another tab is on screen.
     */
    public void replaceDuckView(WebView fresh) {
        liveTabs.put(Providers.DUCK_ID, fresh);
        lowerPriorityWhenHidden(fresh);
        fresh.setVisibility(current.isDuck() ? View.VISIBLE : View.GONE);
        updatePlaceholder(); // a fresh WebView has no "failed" mark
    }

    /**
     * The renderer behind a (non-duck.ai) tab died (see WebViewRecovery for the background).
     * A hidden tab is simply closed (it reloads when opened again). The visible tab is replaced by a
     * fresh WebView (in the same account container) that reloads the same page, unless it crashed
     * 3 times within a minute: then the tab is closed and duck.ai is shown instead, to avoid an
     * endless crash loop.
     */
    public void onRendererGone(WebView dead) {
        Account account = accountOf(dead);
        if (account == null) {
            WebViewRecovery.discard(dead);
            return;
        }
        if (!account.id.equals(current.id)) {
            closeTab(account.id);
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        recoveryTimes.addLast(now);
        while (!recoveryTimes.isEmpty() && now - recoveryTimes.peekFirst() > 60_000) {
            recoveryTimes.removeFirst();
        }
        if (recoveryTimes.size() >= 3) {
            closeTab(account.id);
            show(liveAccounts.get(Providers.DUCK_ID));
            if (listener != null) {
                listener.onTabGaveUp(account);
            }
            return;
        }
        String lastUrl = dead.getUrl(); // read before destroying
        WebView fresh = WebViewRecovery.replace(dead);
        prepare(fresh, account);
        liveTabs.put(account.id, fresh);
        updatePlaceholder(); // a fresh WebView has no "failed" mark
        boolean isWebPage = lastUrl != null && lastUrl.startsWith("https://");
        fresh.loadUrl(isWebPage ? lastUrl : Providers.byId(account.providerId).homeUrl);
    }

    /** Frees all account WebViews except duck.ai's (the activity destroys that one). Call from onDestroy. */
    public void destroyAccountTabs() {
        for (String id : liveTabs.keySet().toArray(new String[0])) {
            if (!Providers.DUCK_ID.equals(id)) {
                closeTab(id);
            }
        }
    }

    private WebView createTab(Account account) {
        WebView webView = new WebView(container.getContext());
        // Index 0 = drawn first (at the back), so the progress bar in the same container stays on top.
        container.addView(webView, 0, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        prepare(webView, account);
        liveTabs.put(account.id, webView);
        liveAccounts.put(account.id, account);
        return webView;
    }

    /** Container first (WebView requires it), then the normal set-up. */
    private void prepare(WebView webView, Account account) {
        Profiles.attach(webView, account);
        configurer.configure(webView, account);
        lowerPriorityWhenHidden(webView);
    }

    /**
     * Android 8.0+: while this WebView is hidden, its page process counts as "not important", so
     * Android reclaims it first when memory runs low (PROJECT.md R-PERF-2). If that happens,
     * onRenderProcessGone reloads/closes the tab (see {@link #onRendererGone}), which is why the
     * docs say to always pair this setting with that handler.
     * Docs: https://developer.android.com/reference/android/webkit/WebView#setRendererPriorityPolicy(int,%20boolean)
     */
    private static void lowerPriorityWhenHidden(WebView webView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
        }
    }

    /**
     * The unloaded placeholder is visible exactly while the account on screen has no loaded tab; the retry
     * screen while its loaded tab's page failed to load (and it isn't unloaded).
     */
    private void updatePlaceholder() {
        boolean unloaded = isCurrentUnloaded();
        if (placeholder != null) {
            placeholder.setVisibility(unloaded ? View.VISIBLE : View.GONE);
        }
        if (retryPlaceholder != null) {
            WebView view = currentWebView();
            LoadErrors.TabState state = view == null ? null : loadState(view);
            boolean failed = !unloaded && state != null && state.showsRetry();
            retryPlaceholder.setVisibility(failed ? View.VISIBLE : View.GONE);
        }
    }

    private void closeExcessTabs() {
        for (String id : lru.idsToClose(maxLiveTabs, Providers.DUCK_ID)) {
            closeTab(id);
        }
    }

    private void closeTab(String id) {
        WebView webView = liveTabs.remove(id);
        if (!Providers.DUCK_ID.equals(id)) {
            liveAccounts.remove(id);
        }
        lru.remove(id);
        if (webView != null) {
            WebViewRecovery.discard(webView); // removes it from the screen and frees its memory
        }
    }
}
