package org.llmcentral.app.theme;

import android.content.Context;
import android.webkit.JavascriptInterface;

/**
 * Gives the app's own HTML pages (settings, offline viewer, script manager, prompt box) the theme
 * colours, so they look like the rest of the app (PROJECT.md §4.1).
 *
 * <p><b>How:</b> MainActivity adds this object to those pages' WebViews under the name
 * {@code AndroidTheme}. Each page has, at the end of its {@code <head>}, a tiny script:
 * <pre>
 * if (window.AndroidTheme) document.write('&lt;style id="theme"&gt;' + AndroidTheme.css() + '&lt;/style&gt;');
 * </pre>
 * It runs before the page is first drawn, so there's no flash of wrong colours. The CSS it gets
 * ({@link Scheme#toCss()}) defines variables like {@code --md-primary} and overrides the pages'
 * older colour variables.</p>
 *
 * <p><b>Security:</b> it's only ever added to WebViews that show the app's own local pages, never
 * to provider websites, and it only <i>returns</i> colours: a page can't change anything with it.</p>
 */
public final class ThemeBridge {

    private final Context context;

    public ThemeBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Called from JavaScript (on a background thread): the current theme as CSS. */
    @JavascriptInterface
    public String css() {
        return ThemeManager.scheme(context).toCss();
    }
}
