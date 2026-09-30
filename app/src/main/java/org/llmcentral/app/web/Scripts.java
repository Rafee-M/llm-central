package org.llmcentral.app.web;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the JavaScript snippets that the app injects into chat web pages.
 *
 * <p><b>Why this exists:</b> duckAssist kept ~42 KB of JavaScript as Java string literals inside
 * MainActivity ({@code "(function() {" + "  var x = ..." + ...}). That is hard to read, hard to edit
 * (every quote and backslash must be escaped for Java) and can't be checked by JavaScript tools.
 * The scripts now live as normal {@code .js} files in {@code app/src/main/assets/js/}, and this class
 * reads them.</p>
 *
 * <p><b>What "assets" are:</b> files under {@code src/main/assets/} are copied into the APK unchanged,
 * and the app reads them at runtime through {@link android.content.res.AssetManager}
 * (docs: https://developer.android.com/reference/android/content/res/AssetManager).</p>
 *
 * <p><b>Performance:</b> each file is read once, the first time it's needed, and then kept in memory
 * (a small cache; all files together are ~50 KB of text). Reading a few KB from the APK is fast
 * enough to do on the main thread.</p>
 *
 * <p><b>Usage:</b> {@code webView.evaluateJavascript(Scripts.get(context, Scripts.VOICE_CHAT), null);}</p>
 */
public final class Scripts {

    private static final String TAG = "Scripts";

    // File names inside assets/js/. Using constants (instead of typing the names everywhere) means a
    // typo is a compile error, and ScriptsTest can check that every listed file really exists.
    public static final String BLOB_DOWNLOAD_PATCH = "blob_download_patch.js";
    public static final String IMAGE_ZOOM_MONITOR = "image_zoom_monitor.js";
    public static final String AUTO_FOCUS = "auto_focus.js";
    public static final String CLIPBOARD_PATCH = "clipboard_patch.js";
    public static final String SWIPE_SCROLL = "swipe_scroll.js";
    public static final String RTL_RESOLVER = "rtl_resolver.js";
    public static final String RTL_CLEANUP = "rtl_cleanup.js";
    public static final String VOICE_CHAT = "voice_chat.js";
    public static final String CONTINUE_LAST_CHAT = "continue_last_chat.js";
    public static final String DUMP_CHATS = "dump_chats.js";
    /** A function expression: the app appends "(url, contentDisposition, mimetype)". */
    public static final String BLOB_FETCH = "blob_fetch.js";
    /** "Desktop site": widens the page's viewport (web/DesktopMode). */
    public static final String DESKTOP_VIEWPORT = "desktop_viewport.js";
    /** A function expression: the app appends "(prompt)". Run by PromptFiller on other providers. */
    public static final String PREFILL_PROMPT = "prefill_prompt.js";
    /** Not injected automatically: the example shown in the user-script manager (off by default). */
    public static final String USER_SCRIPT_SAMPLE = "user_script_sample.js";

    /** Every script file above; used by the unit test. Add new names here too. */
    public static final String[] ALL = {
            BLOB_DOWNLOAD_PATCH, IMAGE_ZOOM_MONITOR, AUTO_FOCUS, CLIPBOARD_PATCH, SWIPE_SCROLL,
            RTL_RESOLVER, RTL_CLEANUP, VOICE_CHAT, CONTINUE_LAST_CHAT, DUMP_CHATS, BLOB_FETCH,
            PREFILL_PROMPT, DESKTOP_VIEWPORT, USER_SCRIPT_SAMPLE
    };

    /** Folder inside assets/ that holds the scripts. */
    static final String ASSET_DIR = "js/";

    // file name -> file content. "static" = one cache shared by the whole app.
    private static final Map<String, String> cache = new HashMap<>();

    private Scripts() {
        // Only static methods; never instantiated.
    }

    /**
     * Returns the content of {@code assets/js/<name>}.
     *
     * <p>Never returns null: if the file can't be read (which would mean it's missing from the APK,
     * a build mistake), it logs an error and returns an empty string, because running "" in a
     * WebView is harmless while passing null to {@code evaluateJavascript} could crash.</p>
     *
     * <p>{@code synchronized} makes the cache safe if two threads ask at the same moment.</p>
     */
    public static synchronized String get(Context context, String name) {
        String cached = cache.get(name);
        if (cached != null) {
            return cached;
        }
        String content;
        try (InputStream in = context.getApplicationContext().getAssets().open(ASSET_DIR + name)) {
            content = readUtf8(in);
        } catch (Exception e) {
            Log.e(TAG, "Missing injected script: " + name, e);
            return "";
        }
        cache.put(name, content);
        return content;
    }

    /** Reads a whole stream as UTF-8 text. Plain Java (no Android classes), so it's unit-testable. */
    static String readUtf8(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
