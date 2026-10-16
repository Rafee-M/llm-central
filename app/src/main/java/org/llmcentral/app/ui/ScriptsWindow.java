package org.llmcentral.app.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.llmcentral.app.scripts.UserScripts;
import org.llmcentral.app.theme.ThemeBridge;
import org.llmcentral.app.web.DialogWebViewClient;
import org.llmcentral.app.web.Scripts;

/**
 * Settings → Data &amp; tools → User scripts: a full-screen window showing {@code assets/scripts_manager.html}.
 *
 * <p>Opened the same way as {@link AboutWindow}: a {@link Dialog} on top of Settings, which stays open
 * underneath, so closing this window (its back arrow, or the phone's Back) returns to Settings. Until
 * 2026-10-15 opening it closed Settings first, so Back landed on the chat.</p>
 *
 * <p><b>Back</b> first closes what is open inside the page (a dialog, then the editor), like Settings:
 * the page's {@code handleBack()} answers "true" when it handled it. Without this, Back in the editor
 * closed the whole window and lost what was typed.</p>
 *
 * <p><b>Saving</b> only stores the list (checked by {@link UserScripts#sanitize}); changes apply when a
 * page loads. The page offers "Reload page", which reloads the account on screen.</p>
 */
public final class ScriptsWindow {

    private static final String PREFS = "duck_assist_prefs";

    private ScriptsWindow() {
    }

    /**
     * Opens the window. Call on the main thread.
     *
     * <p>JavaScript is safe here: the WebView only ever shows our own local page (links go to the
     * browser, file access is off), so lint's warning is switched off for this method only.</p>
     *
     * @param reloadPage reloads the tab on screen (the account menu's "Reload page")
     * @return the window, so the caller can avoid opening a second one while it is showing
     */
    @SuppressLint("SetJavaScriptEnabled")
    public static Dialog show(Activity activity, Runnable reloadPage) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        WebView webView = new WebView(activity);
        // Renderer death closes this window instead of the app; web links open in the browser.
        webView.setWebViewClient(new DialogWebViewClient(dialog::dismiss));
        webView.setWebChromeClient(new WebChromeClient());
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);

        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        webView.addJavascriptInterface(new Object() {
            /** The saved list; the first time ever, the switched-off example (and it is saved, so
             *  deleting every script later leaves an empty list instead of bringing the example back). */
            @JavascriptInterface
            public String getScriptsJson() {
                String json = prefs.getString(UserScripts.PREF, null);
                if (json == null) {
                    json = UserScripts.defaultList(Scripts.get(activity, Scripts.USER_SCRIPT_SAMPLE));
                    prefs.edit().putString(UserScripts.PREF, json).apply();
                }
                return json;
            }

            /** Stores the list if it is a JSON list of objects. @return false if it was refused */
            @JavascriptInterface
            public boolean saveScriptsJson(String json) {
                String checked = UserScripts.sanitize(json);
                if (checked == null) {
                    return false;
                }
                prefs.edit().putString(UserScripts.PREF, checked).apply();
                return true;
            }

            @JavascriptInterface
            public void reloadPage() {
                activity.runOnUiThread(reloadPage);
            }

            @JavascriptInterface
            public void close() {
                activity.runOnUiThread(dialog::dismiss);
            }
        }, "AndroidScripts");
        // Theme colours for the page (read-only, local page only; see theme/ThemeBridge).
        webView.addJavascriptInterface(new ThemeBridge(activity), "AndroidTheme");

        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                webView.evaluateJavascript("window.handleBack ? handleBack() : false", handled -> {
                    if (!"true".equals(handled)) {
                        dialog.dismiss();
                    }
                });
            }
            return true;
        });
        webView.loadUrl("file:///android_asset/scripts_manager.html");

        dialog.setContentView(webView);
        // A WebView that isn't destroyed keeps its memory until Java's garbage collector finds it.
        dialog.setOnDismissListener(d -> webView.destroy());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }
}
