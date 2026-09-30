package org.llmcentral.app.theme;

import android.content.Context;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * What the Settings page's "Appearance" section can ask the app to do (PROJECT.md §4.3–4.4):
 * read and change the Theme mode, the Color palette, the Material You switch and "Hide status bar".
 *
 * <p>Added to the settings WebView as {@code AndroidAppearance} (see AccountsBridge for how
 * JavaScript bridges work). Every setter validates its input in {@link ThemeManager}, applies the
 * change to the app's native parts right away ({@code onChanged}, on the main thread) and returns the
 * new state including the new CSS, so the page recolours itself immediately too.</p>
 */
public final class AppearanceBridge {

    private static final String TAG = "AppearanceBridge";

    /** Main-thread callback after a change (MainActivity repaints its native views). */
    public interface Host {
        void runOnUiThread(Runnable action);

        void onThemeChanged();
    }

    private final Context context;
    private final Host host;

    public AppearanceBridge(Context context, Host host) {
        this.context = context.getApplicationContext();
        this.host = host;
    }

    /**
     * @return {@code {"mode":"system","materialYou":true,"materialYouAvailable":true,"palette":"duck",
     *         "css":":root{…}","dynamic":["#…","#…","#…"] or null,
     *         "palettes":[{"id","name","colors":["#primary","#secondaryContainer","#tertiaryContainer"]}]}}
     */
    @JavascriptInterface
    public String getAppearanceJson() {
        try {
            boolean dark = ThemeManager.isNight(context);
            JSONObject root = new JSONObject();
            root.put("mode", ThemeManager.mode(context));
            root.put("materialYou", ThemeManager.isMaterialYou(context));
            root.put("materialYouAvailable", ThemeManager.isMaterialYouAvailable());
            root.put("palette", ThemeManager.paletteId(context));
            root.put("hideStatusBar", ThemeManager.isStatusBarHidden(context));
            root.put("css", ThemeManager.scheme(context).toCss());
            Scheme dynamic = ThemeManager.materialYouPreview(context);
            root.put("dynamic", dynamic == null ? JSONObject.NULL : swatch(dynamic));
            JSONArray palettes = new JSONArray();
            for (Palettes.Palette p : Palettes.all(context)) {
                palettes.put(new JSONObject().put("id", p.id).put("name", p.name)
                        .put("colors", swatch(dark ? p.dark : p.light)));
            }
            root.put("palettes", palettes);
            return root.toString();
        } catch (Exception e) {
            Log.e(TAG, "Couldn't build the appearance JSON", e);
            return "{}";
        }
    }

    /** "system", "light", "dark" or "oled"; anything else is ignored. */
    @JavascriptInterface
    public String setThemeMode(String mode) {
        ThemeManager.setMode(context, mode);
        return changed();
    }

    /** Picks one of the 8 palettes (turns Material You off). Unknown ids are ignored. */
    @JavascriptInterface
    public String setPalette(String paletteId) {
        ThemeManager.setPalette(context, paletteId);
        return changed();
    }

    /** Material You on/off (no effect below Android 12). */
    @JavascriptInterface
    public String setMaterialYou(boolean on) {
        ThemeManager.setMaterialYou(context, on);
        return changed();
    }

    /** Appearance → "Hide status bar" (frameless full-screen app). */
    @JavascriptInterface
    public String setHideStatusBar(boolean hide) {
        ThemeManager.setStatusBarHidden(context, hide);
        return changed();
    }

    private String changed() {
        host.runOnUiThread(host::onThemeChanged);
        return getAppearanceJson();
    }

    /** Three colours that sum up a scheme, drawn as the palette's round preview tile. */
    private static JSONArray swatch(Scheme s) {
        return new JSONArray()
                .put(Scheme.hex(s.get(Scheme.PRIMARY)))
                .put(Scheme.hex(s.get(Scheme.SECONDARY_CONTAINER)))
                .put(Scheme.hex(s.get("tertiaryContainer")));
    }
}
