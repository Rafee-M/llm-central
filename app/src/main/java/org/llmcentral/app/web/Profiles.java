package org.llmcentral.app.web;

import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebView;

import androidx.webkit.HttpCache;
import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebStorageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.llmcentral.app.account.Account;

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
 * So signing out / removing works in two parts:
 * <ol>
 *   <li>right away: the profile's cookies, storage and cache are wiped ({@link #forget}), so the
 *       account is logged out immediately;</li>
 *   <li>on the next app start, before any WebView exists: the now-empty profile folder is deleted
 *       ({@link #cleanUp}).</li>
 * </ol></p>
 */
public final class Profiles {

    private static final String TAG = "Profiles";

    /** Preferences key: profile names whose folders are deleted on the next start. */
    static final String PREF_PENDING_DELETES = "pending_profile_deletes";

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
    public static void attach(WebView webView, Account account, SharedPreferences prefs) {
        String name = account.profileName();
        if (name == null || !isSupported()) {
            return; // default profile: nothing to do
        }
        WebViewCompat.setProfile(webView, name); // creates the profile the first time
        // The account is in use (again), so its folder must not be deleted at the next start.
        cancelPendingDelete(prefs, name);
        limitCache(WebViewCompat.getProfile(webView));
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
    public static void forget(Account account, SharedPreferences prefs) {
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
            // Used in this app run → wipe the contents now, delete the folder at the next start.
        }
        Profile profile = store.getProfile(name);
        if (profile != null) {
            wipe(profile);
        }
        schedulePendingDelete(prefs, name);
    }

    /**
     * Start-up housekeeping, called before any account WebView is created: deletes the folders queued
     * by {@link #forget} and any leftover {@code acct_} profile whose account no longer exists (e.g.
     * if the app was closed in the middle of removing one).
     */
    public static void cleanUp(SharedPreferences prefs, List<Account> accounts) {
        Set<String> stillPending = new HashSet<>();
        // (Checked here, not via isSupported(), so Android's lint tool can see the check.)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            Set<String> keep = new HashSet<>();
            for (Account a : accounts) {
                if (a.profileName() != null) {
                    keep.add(a.profileName());
                }
            }
            ProfileStore store = ProfileStore.getInstance();
            Set<String> toDelete = new HashSet<>(prefs.getStringSet(PREF_PENDING_DELETES, new HashSet<>()));
            for (String name : store.getAllProfileNames()) {
                if (name.startsWith(ACCOUNT_PREFIX) && !keep.contains(name)) {
                    toDelete.add(name); // orphan: its account was removed
                }
            }
            for (String name : toDelete) {
                try {
                    store.deleteProfile(name); // false = didn't exist; fine either way
                } catch (RuntimeException e) {
                    // Still in use in this process (e.g. the screen was recreated): try again next start.
                    stillPending.add(name);
                }
            }
        }
        prefs.edit().putStringSet(PREF_PENDING_DELETES, stillPending).apply();
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
            // Older WebView: the classic calls; the leftover cache goes with the folder at next start.
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

    private static void schedulePendingDelete(SharedPreferences prefs, String name) {
        // getStringSet returns a set we must not modify, hence the copy.
        Set<String> pending = new HashSet<>(prefs.getStringSet(PREF_PENDING_DELETES, new HashSet<>()));
        pending.add(name);
        prefs.edit().putStringSet(PREF_PENDING_DELETES, pending).apply();
    }

    private static void cancelPendingDelete(SharedPreferences prefs, String name) {
        Set<String> pending = prefs.getStringSet(PREF_PENDING_DELETES, null);
        if (pending != null && pending.contains(name)) {
            Set<String> copy = new HashSet<>(pending);
            copy.remove(name);
            prefs.edit().putStringSet(PREF_PENDING_DELETES, copy).apply();
        }
    }
}
