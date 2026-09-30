package org.llmcentral.app.web;

import android.util.Log;
import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;
import org.llmcentral.app.provider.ProviderSpec;
import org.llmcentral.app.provider.Providers;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The safe "phone line" between the chat websites and the app (PROJECT.md P1, §5.4). Round 5 built it
 * for duck.ai ("DuckBridge"); round 6 renamed it and added a second, much smaller <b>scope</b>:
 * <ul>
 *   <li><b>duck.ai</b> ({@link #attachDuck}): all the message types below, from duck.ai's pages;</li>
 *   <li><b>downloads only</b> ({@link #attachDownloads}), for ChatGPT, Claude, DeepSeek and Z.ai: the
 *       single message type {@code blob} (hand over a file the page made), and only from the
 *       provider's own chat site, e.g. {@code https://chatgpt.com}, not from its sign-in sites.</li>
 * </ul>
 * <p>Everything below applies to both scopes.</p>
 *
 * <p><b>What it replaces:</b> until round 5 the duck.ai tab had {@code addJavascriptInterface(this,
 * "Android")}. That puts a Java object into <i>every frame of every page</i> loaded in the tab,
 * including invisible frames from other websites (adverts, analytics, a sign-in check…). Any of
 * them could call {@code Android.getChatsJson()} and read all saved chats, or save files.</p>
 *
 * <p><b>How the new line works:</b> {@link WebViewCompat#addWebMessageListener} gives pages a
 * JavaScript object named {@value #JS_OBJECT} with one method, {@code postMessage(text)}. Three
 * things make it safer:</p>
 * <ol>
 *   <li><b>Only duck.ai gets it.</b> WebView injects the object only into frames whose origin
 *       (scheme + host + port) matches our allow-list: {@code https://duck.ai},
 *       {@code https://*.duck.ai}, {@code https://duckduckgo.com}, {@code https://*.duckduckgo.com}.
 *       A frame from any other site simply has no {@code AppBridge}.</li>
 *   <li><b>WebView tells us who is calling.</b> Every message arrives with the sender's origin and
 *       whether it came from the main page or an embedded frame. We check both again in Java
 *       (belt and braces) and accept only duck.ai's main page.</li>
 *   <li><b>Messages, not method calls.</b> The page can only send text. Java reads it, accepts
 *       only the few message types listed below, checks their size and format, and ignores
 *       everything else. Nothing can be <i>read back</i> through it (there is no
 *       {@code getChatsJson} any more on the website side).</li>
 * </ol>
 *
 * <p><b>Message format</b> (one string): {@code "<type>:<payload>"}, for example
 * {@code "copy:Hello"} or {@code "zoom:true"}. A plain prefix instead of a JSON wrapper keeps the
 * big saved-chats message ({@code "chats:{…}"}, can be many MB) from being parsed twice.</p>
 * <table>
 *   <tr><th>type</th><th>payload</th><th>sent by</th></tr>
 *   <tr><td>copy</td><td>the text to copy</td><td>js/clipboard_patch.js</td></tr>
 *   <tr><td>chats</td><td>JSON object with the chat dump</td><td>js/dump_chats.js</td></tr>
 *   <tr><td>blob</td><td>JSON {data, mime, disposition, url}</td><td>js/blob_fetch.js</td></tr>
 *   <tr><td>zoom</td><td>true / false</td><td>js/image_zoom_monitor.js</td></tr>
 *   <tr><td>keyboard</td><td>(empty)</td><td>js/auto_focus.js</td></tr>
 *   <tr><td>shortcut</td><td>(empty)</td><td>js/auto_focus.js</td></tr>
 *   <tr><td>continued</td><td>(empty)</td><td>js/continue_last_chat.js</td></tr>
 * </table>
 *
 * <p>Docs: https://developer.android.com/reference/androidx/webkit/WebViewCompat#addWebMessageListener(android.webkit.WebView,java.lang.String,java.util.Set%3Cjava.lang.String%3E,androidx.webkit.WebViewCompat.WebMessageListener)
 * ("treat received messages as untrustworthy and validate carefully").</p>
 *
 * <p>Everything except {@link #attach} is plain Java, so AppBridgeTest checks the rules on the
 * computer.</p>
 */
public final class AppBridge {

    private static final String TAG = "AppBridge";

    /** Name of the object pages see: {@code window.AppBridge.postMessage("copy:hi")}. */
    public static final String JS_OBJECT = "AppBridge";

    static final String COPY = "copy";
    static final String CHATS = "chats";
    static final String BLOB = "blob";
    static final String ZOOM = "zoom";
    static final String KEYBOARD = "keyboard";
    static final String SHORTCUT = "shortcut";
    static final String CONTINUED = "continued";

    /** Longest text the page may put on the clipboard (1 million characters is plenty for a chat). */
    static final int MAX_COPY_CHARS = 1_000_000;

    /**
     * Who may use a bridge, and for what: the allowed web domains and message types.
     * {@link #duck()} or {@link #downloads}.
     */
    static final class Scope {
        final String[] domains;
        final Set<String> types;

        Scope(String[] domains, Set<String> types) {
            this.domains = domains;
            this.types = types;
        }

        /** duck.ai: its own domains, every message type. */
        static Scope duck() {
            return new Scope(Providers.DUCK.inAppDomains(), new HashSet<>(Arrays.asList(
                    COPY, CHATS, BLOB, ZOOM, KEYBOARD, SHORTCUT, CONTINUED)));
        }

        /**
         * Another provider: only its chat site (the host of its home page, e.g. chatgpt.com or
         * chat.deepseek.com, and that host's subdomains), only file downloads.
         */
        static Scope downloads(ProviderSpec provider) {
            String host;
            try {
                host = new URI(provider.homeUrl).getHost();
            } catch (Exception e) {
                host = null;
            }
            return new Scope(host == null ? new String[0] : new String[]{host},
                    Collections.singleton(BLOB));
        }
    }

    /** What the bridge can ask the app to do (implemented by MainActivity). Called on the main thread. */
    public interface Host {
        void copyToClipboard(String text);

        /** The duck.ai chat dump (JSON text) for the offline viewer. */
        void onChatsFetched(String json);

        /** A downloaded file as a {@code data:} URL, to be saved in Downloads. */
        void processBlob(String dataUrl, String mimetype, String contentDisposition, String pageUrl);

        void showSoftKeyboard();

        /** duck.ai's "new chat" keyboard shortcut (Ctrl+Shift+O), sent as real key presses. */
        void triggerNewChatShortcut();

        void setImageZoomActive(boolean active);

        void onContinueLastChatSuccess();
    }

    private AppBridge() {
        // Only static methods.
    }

    /** Connects the full bridge to duck.ai's WebView (see {@link #attach}). */
    public static boolean attachDuck(WebView webView, Host host) {
        return attach(webView, Scope.duck(), host);
    }

    /**
     * Connects the downloads-only bridge to another provider's WebView (see {@link #attach}): the
     * page can hand over files it made itself (a generated CSV, an exported chat), nothing else.
     */
    public static boolean attachDownloads(WebView webView, ProviderSpec provider, Host host) {
        return attach(webView, Scope.downloads(provider), host);
    }

    /**
     * Connects a bridge to a tab's WebView. Must be called <b>before</b> the page loads (WebView
     * injects the object on every navigation after this call). Needs a WebView with the
     * {@code WEB_MESSAGE_LISTENER} feature (WebView 83+, from 2020); older ones get no bridge at all,
     * which only switches off the extras (copy button fix, saved chats, saving files the page made).
     *
     * @return true if the bridge was attached
     */
    private static boolean attach(WebView webView, Scope scope, Host host) {
        // The feature check must be written in this method, as a plain "if" around the call, so
        // Android's lint can see it (see CLAUDE.md §4).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, JS_OBJECT, originRules(scope),
                    (view, message, sourceOrigin, isMainFrame, replyProxy) ->
                            handle(message.getData(), String.valueOf(sourceOrigin), isMainFrame, host, scope));
            return true;
        }
        Log.w(TAG, "WebView too old for the message bridge; its extras are off");
        return false;
    }

    /** @return true if this phone's WebView can use the bridge at all. */
    public static boolean isSupported() {
        return WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER);
    }

    /**
     * The allow-list given to WebView, built from the scope's domains. Each domain needs two rules:
     * {@code https://*.duck.ai} matches subdomains only, not {@code https://duck.ai} itself (verified
     * in the androidx.webkit 1.17.1 docs).
     */
    static Set<String> originRules(Scope scope) {
        Set<String> rules = new LinkedHashSet<>();
        for (String domain : scope.domains) {
            rules.add("https://" + domain);
            rules.add("https://*." + domain);
        }
        return rules;
    }

    /**
     * @return true if a message from {@code origin} may be trusted: HTTPS, the normal port and one of
     *         the scope's hosts (same exact-domain rule as navigation, {@link NavigationPolicy#hostMatches}).
     */
    static boolean isTrustedOrigin(String origin, Scope scope) {
        if (origin == null) {
            return false;
        }
        try {
            URI uri = new URI(origin);
            return "https".equals(uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT))
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getRawUserInfo() == null
                    && NavigationPolicy.hostMatches(uri.getHost(), scope.domains);
        } catch (Exception e) {
            return false; // "null" (a file: page) or anything unparsable
        }
    }

    /**
     * Checks one message and, if it's acceptable, asks the host to act on it.
     *
     * @param raw         the text the page posted
     * @param origin      where it came from, as WebView reports it (e.g. "https://duck.ai")
     * @param isMainFrame false for embedded frames (never trusted)
     * @param scope       which sites and message types this bridge accepts
     * @return true if the message was accepted (used by the tests)
     */
    static boolean handle(String raw, String origin, boolean isMainFrame, Host host, Scope scope) {
        if (raw == null || !isMainFrame || !isTrustedOrigin(origin, scope)) {
            return false;
        }
        int colon = raw.indexOf(':');
        String type = colon < 0 ? raw : raw.substring(0, colon);
        String payload = colon < 0 ? "" : raw.substring(colon + 1);
        if (!scope.types.contains(type)) {
            return false; // e.g. "copy" on ChatGPT's downloads-only bridge
        }
        switch (type) {
            case COPY:
                if (payload.length() > MAX_COPY_CHARS) {
                    return false;
                }
                host.copyToClipboard(payload);
                return true;
            case CHATS:
                // The dump is a JSON object; MainActivity parses and merges it off the main thread.
                if (!payload.startsWith("{")) {
                    return false;
                }
                host.onChatsFetched(payload);
                return true;
            case BLOB:
                return handleBlob(payload, host);
            case ZOOM:
                if (!payload.equals("true") && !payload.equals("false")) {
                    return false;
                }
                host.setImageZoomActive(payload.equals("true"));
                return true;
            case KEYBOARD:
                host.showSoftKeyboard();
                return true;
            case SHORTCUT:
                host.triggerNewChatShortcut();
                return true;
            case CONTINUED:
                host.onContinueLastChatSuccess();
                return true;
            default:
                return false; // unknown type: ignore
        }
    }

    /** {"data":"data:…;base64,…","mime":"…","disposition":"…","url":"https://duck.ai/…"} */
    private static boolean handleBlob(String payload, Host host) {
        try {
            JSONObject o = new JSONObject(payload);
            String data = o.optString("data", "");
            // Only file contents encoded as a data: URL are accepted (never a link to fetch).
            if (!data.startsWith("data:")) {
                return false;
            }
            host.processBlob(data, o.optString("mime", ""), o.optString("disposition", ""),
                    o.optString("url", ""));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
