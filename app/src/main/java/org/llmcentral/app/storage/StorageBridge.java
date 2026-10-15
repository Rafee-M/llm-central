package org.llmcentral.app.storage;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;

import java.util.Set;
import java.util.concurrent.Executor;

/**
 * What the Settings page's "Clear cache" row (Data &amp; tools) can ask the app to do (round 6).
 * Added to the settings WebView as {@code AndroidStorage} (a local page only; see AccountsBridge for
 * how these bridges work).
 *
 * <p>Both methods return at once and answer later by calling {@code window.onCacheSize(bytes)} in the
 * page: measuring walks through thousands of cache files, which must not freeze the page.</p>
 */
public final class StorageBridge {

    /** Wait before measuring again after clearing: WebView deletes its cache in the background. */
    static final long MEASURE_AFTER_CLEAR_MS = 1500;

    /** What the bridge needs from the screen (implemented by MainActivity). */
    public interface Host {
        void runOnUiThread(Runnable action);

        /** Main thread: empty the HTTP cache of every account (open tabs and closed ones). */
        void clearWebViewCaches();

        /** Main thread: files in the cache folder that are still needed (e.g. a shared file). */
        Set<String> filesInUse();

        /** Main thread: run a line of JavaScript in the settings page. */
        void sendToPage(String script);
    }

    private final Context context;
    private final Executor background;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());

    public StorageBridge(Context context, Executor background, Host host) {
        this.context = context.getApplicationContext();
        this.background = background;
        this.host = host;
    }

    /** Measures the cache folders; the page gets {@code onCacheSize(bytes)}. */
    @JavascriptInterface
    public void measureCache() {
        background.execute(() -> {
            long bytes = AppCache.sizeBytes(context);
            host.runOnUiThread(() -> host.sendToPage("window.onCacheSize && onCacheSize(" + bytes + ")"));
        });
    }

    /**
     * Clears every account's website cache and the app's temporary files. Logins, accounts,
     * settings and saved chats are not touched. The page gets the new size afterwards.
     */
    @JavascriptInterface
    public void clearCache() {
        host.runOnUiThread(() -> {
            clearAll(context, background, host);
            main.postDelayed(this::measureCache, MEASURE_AFTER_CLEAR_MS);
        });
    }

    /**
     * The clearing itself, shared by this Settings row and the account menu's "Clear cache" icon
     * (ui/SwitcherPopup), so both always do exactly the same: every account's website cache (open
     * tabs and closed accounts), then the app's temporary files on a background thread, keeping the
     * ones still in use (e.g. a file being uploaded). Call on the main thread.
     */
    public static void clearAll(Context context, Executor background, Host host) {
        host.clearWebViewCaches();
        Set<String> keep = host.filesInUse();
        Context app = context.getApplicationContext();
        background.execute(() -> AppCache.deleteTempFiles(app, 0, keep));
    }
}
