package org.llmcentral.app.debug;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.webkit.Profile;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.llmcentral.app.BuildConfig;
import org.llmcentral.app.account.Account;
import org.llmcentral.app.provider.ProviderSpec;
import org.llmcentral.app.provider.Providers;

import java.util.HashSet;
import java.util.Set;

/**
 * <b>Debug builds only:</b> the Android side of the request log ({@link RequestLog}). Every call to this class
 * sits behind {@code if (BuildConfig.DEBUG)}; in a release build that is {@code if (false)}, which the Java
 * compiler leaves out, so R8 removes this class and release builds never record anything.
 *
 * <p>Two places see a page's requests (plan {@code 2026-10-16-tracker-blocking.md} §4, L-4):</p>
 * <ul>
 *   <li>a tab's {@code WebViewClient.shouldInterceptRequest} (MainActivity's logging client calls
 *       {@link #recordPage}), and</li>
 *   <li>each account container's service worker client: a "service worker" is a site's background script, and
 *       its requests don't pass through the tab's WebViewClient
 *       (https://developer.android.com/reference/android/webkit/ServiceWorkerController).</li>
 * </ul>
 * Both run on WebView's background threads, so only thread-safe code ({@link RequestLog}) is called from them.
 */
public final class RequestLogHooks {

    // Profiles whose service workers are already watched (main thread only).
    private static final Set<String> watchedProfiles = new HashSet<>();

    private RequestLogHooks() {
    }

    /**
     * Which account a tab's WebView belongs to: its container (profile) is named {@code acct_<account id>}
     * (web/Profiles); duck.ai uses the default container. Main thread (WebView methods), when the tab is set up.
     */
    public static String accountOf(WebView webView, ProviderSpec provider) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                String name = WebViewCompat.getProfile(webView).getName();
                if (name.startsWith("acct_")) {
                    return name.substring("acct_".length());
                }
            } catch (RuntimeException e) {
                countFailure(e); // as web/Profiles.cookieManager: never let the log break setting up a tab
            }
        }
        // Default container: duck.ai, or (WebView without profiles) the provider's only account, whose id is the
        // provider id (account/AccountStore).
        return provider.isDuck() ? Providers.DUCK_ID : provider.id;
    }

    /** A tab's request (any thread). {@code blocked} = the app answered it instead of the network. */
    public static void recordPage(String accountId, ProviderSpec provider, WebResourceRequest request,
                                  String pageHost, boolean blocked) {
        try {
            RequestLog.get().record(accountId, provider.id, RequestLog.PAGE, String.valueOf(request.getUrl()),
                    request.getMethod(), request.isForMainFrame(), pageHost, blocked, System.currentTimeMillis());
        } catch (RuntimeException e) {
            countFailure(e);
        }
    }

    /** duck.ai's default container: watch its service workers once per app process (main thread). */
    public static void watchDefaultWorkers() {
        if (watchedProfiles.add(Profile.DEFAULT_PROFILE_NAME)) {
            watch(ServiceWorkerController.getInstance(), Providers.DUCK_ID, Providers.DUCK_ID);
        }
    }

    /** An account's own container (web/Profiles.attach, right after the WebView joined it; main thread). */
    public static void watchProfileWorkers(WebView webView, Account account) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            Profile profile = WebViewCompat.getProfile(webView);
            if (watchedProfiles.add(profile.getName())) {
                watch(profile.getServiceWorkerController(), account.id, account.providerId);
            }
        }
    }

    private static void watch(ServiceWorkerController controller, String accountId, String providerId) {
        controller.setServiceWorkerClient(new ServiceWorkerClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                try {
                    RequestLog.get().record(accountId, providerId, RequestLog.WORKER, String.valueOf(request.getUrl()),
                            request.getMethod(), false, null, false, System.currentTimeMillis());
                } catch (RuntimeException e) {
                    countFailure(e);
                }
                return null; // only counted: the request goes to the network as usual
            }
        });
    }

    /**
     * A bug in the log must never change how the app behaves: an exception on WebView's background thread would
     * switch the whole app to "safe mode" (MainActivity's uncaught-error handler, VERIFICATION.md O-M1), which
     * is exactly what the measurement must not cause. So it is only written to Logcat.
     */
    private static void countFailure(RuntimeException e) {
        android.util.Log.w("RequestLog", "Couldn't record a request", e);
    }

    /** First "#" line of the export: which app, Android and WebView versions were measured. */
    public static String about(Context context) {
        PackageInfo webView = WebViewCompat.getCurrentWebViewPackage(context);
        return "app " + BuildConfig.VERSION_NAME + " · Android " + Build.VERSION.RELEASE + " (API "
                + Build.VERSION.SDK_INT + ") · WebView " + (webView != null ? webView.versionName : "unknown")
                + " · multi-profile " + WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE);
    }
}
