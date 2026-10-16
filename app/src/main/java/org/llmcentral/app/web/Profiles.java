package org.llmcentral.app.web;

import android.content.Context;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebView;

import androidx.webkit.HttpCache;
import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebStorageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.llmcentral.app.BuildConfig;
import org.llmcentral.app.account.Account;
import org.llmcentral.app.debug.RequestLogHooks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Account "containers": one WebView <b>profile</b> per account (PROJECT.md §5.2, R-ACC-3).
 *
 * <p><b>What a profile is:</b> a WebView profile is like a separate browser user. Each one has its
 * own cookies (= logins), local storage, IndexedDB, service workers and HTTP cache, stored in its own
 * folder. Two WebViews with different profiles can be open at the same time and never see each
 * other's data, so "ChatGPT · Work" and "ChatGPT · Personal" stay logged in side by side.
 * API docs: https://developer.android.com/reference/androidx/webkit/Profile</p>
 *
 * <p><b>Which profile:</b> duck.ai uses WebView's <i>default</i> profile (so chats saved by earlier
 * versions stay). Every other account uses its own profile named {@code acct_<account id>}
 * ({@link Account#profileName()}).</p>
 *
 * <p><b>Older WebView versions</b> (feature {@code MULTI_PROFILE} missing; it depends on the
 * "Android System WebView" app version, not on the Android version): everything uses the default
 * profile, so only one account per provider is allowed (the settings screen says so).</p>
 *
 * <p><b>Deleting an account's data</b> has a catch: {@link ProfileStore#deleteProfile} refuses to
 * delete a profile that this app run already used (verified in the androidx.webkit 1.17.1 source).
 * So {@link #forget} deletes the profile if it can, and otherwise wipes its contents (cookies,
 * storage, cache), which logs the account out immediately either way. The empty folder of a
 * <i>removed</i> account is deleted at the next app start by {@link #cleanUp}, before any WebView
 * exists; a signed-out account simply keeps its (now empty) container for the next login.</p>
 */
public final class Profiles {

    private static final String TAG = "Profiles";

    /** Profile names we create all start with this ({@link Account#profileName()}). */
    private static final String ACCOUNT_PREFIX = "acct_";

    /**
     * Upper limit for one account's HTTP cache (the sites' own program files, fonts and images; no
     * logins). Without a limit, every profile would get WebView's full automatic quota, which adds up
     * with several accounts (PROJECT.md R-PERF-3).
     */
    static final long MAX_CACHE_BYTES = 40L * 1024 * 1024;

    private Profiles() {
        // Only static methods.
    }

    /** @return true if this phone's WebView supports separate profiles (= several accounts per provider). */
    public static boolean isSupported() {
        return WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE);
    }

    /**
     * Puts a <b>brand-new</b> WebView into the account's container. Must be the first thing done with
     * the WebView (before settings, clients, {@code loadUrl} or {@code evaluateJavascript}); WebView
     * throws otherwise. TabManager calls it right after {@code new WebView(...)}.
     */
    public static void attach(WebView webView, Account account) {
        String name = account.profileName();
        if (name == null || !isSupported()) {
            return; // default profile: nothing to do
        }
        WebViewCompat.setProfile(webView, name); // creates the profile the first time
        limitCache(WebViewCompat.getProfile(webView));
        if (BuildConfig.DEBUG) {
            // Debug builds only: count this account's service-worker requests (debug/RequestLogHooks).
            RequestLogHooks.watchProfileWorkers(webView, account);
        }
    }

    /**
     * @return the cookie jar this WebView uses: its profile's, or the app-wide one for the default
     *         profile. Needed e.g. to send the right login cookies with a file download.
     */
    public static CookieManager cookieManager(WebView webView) {
        if (isSupported()) {
            try {
                return WebViewCompat.getProfile(webView).getCookieManager();
            } catch (RuntimeException e) {
                Log.w(TAG, "No profile for WebView; using the default cookie jar");
            }
        }
        return CookieManager.getInstance();
    }

    /**
     * Signs an account out: deletes its cookies, storage and cache. Call it <b>after</b> the account's
     * WebView was destroyed (TabManager.closeAccount). Works for "Sign out" (account stays) and for
     * "Remove" (account goes). Must run on the main thread.
     */
    public static void forget(Account account) {
        String name = account.profileName();
        if (name == null || !isSupported()) {
            return;
        }
        ProfileStore store = ProfileStore.getInstance();
        try {
            // Works if the profile wasn't used in this app run: the folder is deleted right away.
            store.deleteProfile(name);
            return;
        } catch (IllegalStateException inUse) {
            // Used in this app run (or still open in the app's other screen) → wipe its contents.
        }
        Profile profile = store.getProfile(name);
        if (profile != null) {
            wipe(profile);
        }
    }

    /**
     * Start-up housekeeping, called before any account WebView is created: deletes every
     * {@code acct_} profile whose account no longer exists (removed accounts whose folder couldn't be
     * deleted while the app was running, or leftovers if the app was closed in the middle).
     */
    public static void cleanUp(List<Account> accounts) {
        // (Checked here, not via isSupported(), so Android's lint tool can see the check.)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            return;
        }
        Set<String> keep = new HashSet<>();
        for (Account a : accounts) {
            if (a.profileName() != null) {
                keep.add(a.profileName());
            }
        }
        ProfileStore store = ProfileStore.getInstance();
        for (String name : store.getAllProfileNames()) {
            if (name.startsWith(ACCOUNT_PREFIX) && !keep.contains(name)) {
                try {
                    store.deleteProfile(name);
                } catch (RuntimeException e) {
                    // Still in use (e.g. the screen was just recreated in the same process): next start.
                    Log.w(TAG, "Leftover profile still in use; will retry at next start");
                }
            }
        }
    }

    /**
     * Caps the HTTP cache of WebView's <i>default</i> profile (duck.ai's) like every account's
     * ({@link #MAX_CACHE_BYTES}). Before round 6 only the account profiles had a cap. Call at start.
     */
    public static void limitDefaultCache() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            return; // older WebView: it manages the size itself
        }
        Profile profile = ProfileStore.getInstance().getProfile(Profile.DEFAULT_PROFILE_NAME);
        if (profile != null) {
            limitCache(profile);
        }
    }

    /**
     * Empties the HTTP cache (the websites' files, <b>not</b> cookies or logins) of every account whose
     * tab is closed right now. Tabs that are open are cleared through their own WebView by the caller.
     *
     * <p>How: {@code WebView.clearCache(true)} removes the cache of the WebView's own profile only
     * (verified in Chromium's {@code AwContents::ClearCache}: data type "cache", the WebView's browser
     * context). So a short-lived, never-shown WebView is put into each closed account's profile,
     * asked to clear, and destroyed. Main thread only.</p>
     *
     * @param openProfiles profile names of the tabs that are open (skipped)
     */
    public static void clearClosedProfileCaches(Context context, Set<String> openProfiles) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            return; // no account profiles: the open duck.ai tab covers the default profile
        }
        for (String name : ProfileStore.getInstance().getAllProfileNames()) {
            if (!name.startsWith(ACCOUNT_PREFIX) || openProfiles.contains(name)) {
                continue;
            }
            WebView temp = new WebView(context);
            try {
                WebViewCompat.setProfile(temp, name);
                temp.clearCache(true);
            } catch (RuntimeException e) {
                Log.w(TAG, "Couldn't clear an account's cache", e);
            } finally {
                temp.destroy();
            }
        }
    }

    /** Deletes everything a profile stored: cookies, local storage, IndexedDB, cache. */
    private static void wipe(Profile profile) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            return; // can't happen (callers check), but keeps lint's feature check satisfied
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            // Newer WebView: one call removes all of it (docs: WebStorageCompat.deleteBrowsingData).
            WebStorageCompat.deleteBrowsingData(profile.getWebStorage(), () -> { });
        } else {
            // Older WebView: the classic calls. They leave the HTTP cache, which holds only the
            // sites' own files (scripts, images), never logins.
            profile.getWebStorage().deleteAllData();
        }
        CookieManager cookies = profile.getCookieManager();
        cookies.removeAllCookies(null);
        cookies.flush();
    }

    /** Caps a profile's HTTP cache at {@link #MAX_CACHE_BYTES} (or WebView's own default if smaller). */
    private static void limitCache(Profile profile) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.HTTP_CACHE_MANAGER)) {
            return; // older WebView: it manages the size itself
        }
        try {
            HttpCache cache = profile.getHttpCache();
            cache.setQuotaBytes(Math.min(cache.getDefaultQuotaBytes(), MAX_CACHE_BYTES));
        } catch (RuntimeException e) {
            Log.w(TAG, "Couldn't set the cache limit", e);
        }
    }
}
