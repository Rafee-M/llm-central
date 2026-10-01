package org.llmcentral.app.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;
import org.llmcentral.app.BuildConfig;
import org.llmcentral.app.theme.ThemeBridge;
import org.llmcentral.app.web.DialogWebViewClient;

import java.io.ByteArrayOutputStream;

/**
 * Settings → About: a full-screen window with the app icon, name, version, developer and source link.
 *
 * <p>The page itself is {@code assets/about.html} (texts and layout live there, so most changes are HTML
 * only). This class opens it the same way the user-scripts window is opened: a {@link Dialog} holding a
 * {@link WebView} that shows a local page. Settings stays open underneath, so Back returns to it.</p>
 *
 * <p>The icon is not a copy: it's the app's real launcher icon, drawn into a PNG when the window opens
 * and handed to the page as a {@code data:} address. A new launcher icon (RELEASE.md §2) therefore
 * shows up here by itself.</p>
 *
 * <p>Links (the GitHub address) open in the browser: {@link DialogWebViewClient} never loads web
 * pages inside the app's own windows.</p>
 */
public final class AboutWindow {

    private static final String TAG = "AboutWindow";
    // Icon size in pixels for the page (shown at 96 CSS px; 192 stays sharp on 2x screens).
    private static final int ICON_PX = 192;

    private AboutWindow() {
    }

    /**
     * Opens the window. Call on the main thread.
     *
     * <p>Lint warns about JavaScript in WebViews because a website could then use the bridge. Here it is
     * safe: the WebView only ever shows our own {@code about.html} (links go to the browser, file
     * access is off), so the warning is switched off for this method only.</p>
     */
    @SuppressLint("SetJavaScriptEnabled")
    public static void show(Activity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        WebView webView = new WebView(activity);
        webView.setWebViewClient(new DialogWebViewClient(dialog::dismiss));
        webView.setWebChromeClient(new WebChromeClient());
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);

        // Everything the page shows from the app, read once now (on the main thread, which is
        // where drawing a Drawable belongs). The bridge below only hands out this fixed text.
        final String info = infoJson(activity);
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public String info() {
                return info;
            }

            @JavascriptInterface
            public void close() {
                activity.runOnUiThread(dialog::dismiss);
            }
        }, "AndroidAbout");
        // Theme colours for the page (read-only, local page only; see theme/ThemeBridge).
        webView.addJavascriptInterface(new ThemeBridge(activity), "AndroidTheme");
        webView.loadUrl("file:///android_asset/about.html");

        dialog.setContentView(webView);
        // A WebView that isn't destroyed keeps its memory until Java's garbage collector finds it.
        dialog.setOnDismissListener(d -> webView.destroy());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    /** {"name", "version", "icon"}: the icon as a data: URL, or "" if it couldn't be drawn. */
    private static String infoJson(Activity activity) {
        JSONObject o = new JSONObject();
        try {
            o.put("name", String.valueOf(activity.getApplicationInfo().loadLabel(activity.getPackageManager())));
            o.put("version", BuildConfig.VERSION_NAME);
            o.put("icon", iconDataUrl(activity));
        } catch (Exception e) {
            Log.w(TAG, "About info incomplete", e);
        }
        return o.toString();
    }

    private static String iconDataUrl(Activity activity) {
        try {
            // On Android 8+ this is an "adaptive icon": drawing it applies the phone's icon shape.
            Drawable icon = activity.getPackageManager().getApplicationIcon(activity.getApplicationInfo());
            Bitmap bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, ICON_PX, ICON_PX);
            icon.draw(new Canvas(bitmap));
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, png);
            bitmap.recycle();
            return "data:image/png;base64," + Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            Log.w(TAG, "Could not draw the app icon", e);
            return "";
        }
    }
}
