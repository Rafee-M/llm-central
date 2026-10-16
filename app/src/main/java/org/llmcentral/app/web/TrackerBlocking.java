package org.llmcentral.app.web;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Settings → Data &amp; tools → "Block trackers" (on by default; plan
 * {@code docs/superpowers/plans/2026-10-16-tracker-blocking.md} §7): the Android side of {@link TrackerBlocker}.
 *
 * <p><b>Where it runs:</b> {@code WebViewClient.shouldInterceptRequest}, which WebView calls for every request a
 * tab's page makes (scripts, pictures, fetch/XHR, beacons), on one of its background threads, before anything is
 * sent. Returning {@code null} lets the request go to the network; returning a response answers it locally, so
 * nothing leaves the phone. https://developer.android.com/reference/android/webkit/WebViewClient#shouldInterceptRequest(android.webkit.WebView,%20android.webkit.WebResourceRequest)</p>
 *
 * <p><b>The answer</b> is an empty "403 Blocked" without CORS headers: to the page it looks like a refused or failed
 * request, the same situation uBlock Origin creates and the sites already handle (decision B-3).</p>
 */
public final class TrackerBlocking {

    private static final String TAG = "TrackerBlocking";

    /** The setting, in the app's settings file {@code duck_assist_prefs}; missing = on. */
    public static final String PREF = "block_trackers";

    private static final String PREFS = "duck_assist_prefs";
    private static final String LIST = "trackers.txt";

    // Read from the app's assets on first use (a few KB; about 0.1 µs per request to match, measured 2026-10-17).
    private static volatile TrackerBlocker list;

    /*
     * The setting, kept in memory after the first read (null = not read yet). Why: answer() runs for every request
     * of every tab, and each getSharedPreferences(name) call takes a lock shared by the whole app twice (AOSP
     * ContextImpl: synchronized (ContextImpl.class)), the same lock the main thread takes whenever it opens a settings
     * file. Safe because the app runs in one process (both screens share this field) and setEnabled is the only code
     * that changes the setting. "volatile" makes the switch's change (made on the Settings page's bridge thread) visible to WebView's request
     * threads at once.
     */
    private static volatile Boolean enabled;

    private TrackerBlocking() {
    }

    public static boolean isEnabled(Context context) {
        Boolean on = enabled;
        if (on == null) {
            on = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF, true);
            enabled = on;
        }
        return on;
    }

    public static void setEnabled(Context context, boolean on) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(PREF, on).apply();
        enabled = on;
    }

    /**
     * @return an empty "blocked" answer if the setting is on and the request goes to a tracker; otherwise null
     *         (the request goes ahead). Any thread. A change in Settings applies to the next request, in both
     *         screens (they share {@link #enabled}).
     */
    public static WebResourceResponse answer(Context context, WebResourceRequest request) {
        if (!isEnabled(context)) {
            return null;
        }
        Uri url = request.getUrl();
        String scheme = url.getScheme();
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            return null; // data:, blob:, the app's own pages… are never trackers
        }
        if (!list(context).shouldBlock(request.isForMainFrame(), url.getHost(), url.getPath(), url.getEncodedQuery())) {
            return null;
        }
        return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked",
                Collections.<String, String>emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    private static TrackerBlocker list(Context context) {
        TrackerBlocker loaded = list;
        if (loaded == null) {
            synchronized (TrackerBlocking.class) {
                loaded = list;
                if (loaded == null) {
                    loaded = TrackerBlocker.parse(read(context));
                    list = loaded;
                }
            }
        }
        return loaded;
    }

    /** The list's text, or "" (blocks nothing) if it can't be read: a broken list must never break the pages. */
    private static String read(Context context) {
        try (InputStream in = context.getAssets().open(LIST)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.w(TAG, "Tracker list missing; nothing is blocked", e);
            return "";
        }
    }
}
