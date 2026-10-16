package org.llmcentral.app.theme;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import java.util.Arrays;
import java.util.List;

/**
 * The app's theme: light/dark mode, colour palette and Material You (PROJECT.md §4, Phase 3).
 *
 * <p><b>Three settings</b> (stored in the app's preferences):</p>
 * <ul>
 *   <li>{@value #PREF_MODE}: {@code system} (default, follows the phone), {@code light},
 *       {@code dark} or {@code oled} (dark with pure-black backgrounds);</li>
 *   <li>{@value #PREF_PALETTE}: one of the 8 preset palettes ({@link Palettes});</li>
 *   <li>{@value #PREF_MATERIAL_YOU}: use the wallpaper colours instead of the palette (Android 12+;
 *       on by default there).</li>
 * </ul>
 *
 * <p><b>Where the colours go:</b> the native parts (window, status and navigation bars, top bar,
 * menus) are coloured in code with {@link #scheme}; the app's HTML pages receive the same colours as
 * CSS variables ({@link Scheme#toCss()}, see {@code ThemeBridge}). No Material library is used.</p>
 *
 * <p><b>Forcing light or dark:</b> a phone in dark mode can still show the app in light mode (and
 * the reverse) by giving the screen an "override configuration" with the chosen night mode
 * ({@link #nightOverride}). On Android 13+ that should also make websites that support dark mode
 * follow the app (to be confirmed on a device). Changing it needs the screen to be rebuilt once
 * ({@code Activity.recreate()}).</p>
 */
public final class ThemeManager {

    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String MODE_OLED = "oled";
    public static final List<String> MODES = Arrays.asList(MODE_SYSTEM, MODE_LIGHT, MODE_DARK, MODE_OLED);

    static final String PREF_MODE = "theme_mode";
    static final String PREF_PALETTE = "theme_palette";
    static final String PREF_MATERIAL_YOU = "material_you";
    /** Appearance → "Hide status bar" (frameless): more room for the page. Off by default. */
    static final String PREF_HIDE_STATUS_BAR = "hide_status_bar";
    private static final String PREFS = "duck_assist_prefs";

    private ThemeManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ settings

    /** @return true on Android 12+, where the phone provides wallpaper-based colours. */
    public static boolean isMaterialYouAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static String mode(Context context) {
        String mode = prefs(context).getString(PREF_MODE, MODE_SYSTEM);
        return MODES.contains(mode) ? mode : MODE_SYSTEM;
    }

    public static String paletteId(Context context) {
        return prefs(context).getString(PREF_PALETTE, Palettes.DEFAULT_ID);
    }

    /** @return true if the wallpaper colours are used (always false below Android 12). */
    public static boolean isMaterialYou(Context context) {
        return isMaterialYouAvailable() && prefs(context).getBoolean(PREF_MATERIAL_YOU, true);
    }

    /** Saves the theme mode; unknown values are ignored. */
    public static void setMode(Context context, String mode) {
        if (MODES.contains(mode)) {
            prefs(context).edit().putString(PREF_MODE, mode).apply();
        }
    }

    /** Picks a preset palette (and turns Material You off, as the Color popup describes). */
    public static void setPalette(Context context, String paletteId) {
        for (Palettes.Palette p : Palettes.all(context)) {
            if (p.id.equals(paletteId)) {
                prefs(context).edit().putString(PREF_PALETTE, paletteId)
                        .putBoolean(PREF_MATERIAL_YOU, false).apply();
                return;
            }
        }
    }

    /** @return true if the status bar (clock, notifications) is hidden in the full-screen app. */
    public static boolean isStatusBarHidden(Context context) {
        return prefs(context).getBoolean(PREF_HIDE_STATUS_BAR, false);
    }

    public static void setStatusBarHidden(Context context, boolean hidden) {
        prefs(context).edit().putBoolean(PREF_HIDE_STATUS_BAR, hidden).apply();
    }

    public static void setMaterialYou(Context context, boolean on) {
        if (isMaterialYouAvailable()) {
            prefs(context).edit().putBoolean(PREF_MATERIAL_YOU, on).apply();
        }
    }

    // ------------------------------------------------------------------ resolving

    /**
     * @return true if the app should be dark now. For "system" this asks the phone's own setting via
     *         {@link Resources#getSystem()}: the app's resources can't be used, because our own
     *         override ({@link #nightOverride}) may have changed them.
     */
    public static boolean isNight(Context context) {
        switch (mode(context)) {
            case MODE_LIGHT:
                return false;
            case MODE_DARK:
            case MODE_OLED:
                return true;
            default:
                int night = Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                return night == Configuration.UI_MODE_NIGHT_YES;
        }
    }

    /** @return the colours to use right now (mode + palette or wallpaper colours). */
    public static Scheme scheme(Context context) {
        boolean dark = isNight(context);
        Palettes.Palette palette = Palettes.byId(context, paletteId(context));
        Scheme base = dark ? palette.dark : palette.light;
        if (isMaterialYou(context)) { // implies Android 12+ (lint verifies this)
            base = DynamicColors.scheme(context, dark, base);
        }
        return MODE_OLED.equals(mode(context)) ? base.toOled() : base;
    }

    /**
     * @return the wallpaper-based scheme for the current light/dark mode (for the Material You tile in
     *         the Color popup, even while a palette is selected), or {@code null} below Android 12
     */
    public static Scheme materialYouPreview(Context context) {
        if (!isMaterialYouAvailable()) {
            return null;
        }
        boolean dark = isNight(context);
        Palettes.Palette fallback = Palettes.byId(context, Palettes.DEFAULT_ID);
        return DynamicColors.scheme(context, dark, dark ? fallback.dark : fallback.light);
    }

    // ------------------------------------------------------------------ applying

    /**
     * For {@code Activity.attachBaseContext}: a configuration that forces the chosen light/dark
     * mode, or {@code null} for "system".
     *
     * <p>Only the night-mode bits are set; every other field stays "undefined", which means "keep the
     * phone's value". {@code fontScale} must be set to 0 explicitly, otherwise the default of 1.0
     * would override the user's font size (the same trick AndroidX AppCompat uses).</p>
     */
    public static Configuration nightOverride(Context base) {
        if (MODE_SYSTEM.equals(mode(base))) {
            return null;
        }
        Configuration config = new Configuration();
        config.fontScale = 0;
        config.uiMode = isNight(base) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        return config;
    }

    /** @return the framework theme for a full-screen activity (light/dark system dialogs follow it). */
    public static int activityTheme(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return android.R.style.Theme_DeviceDefault_DayNight; // follows the (overridden) night mode
        }
        return isNight(context) ? android.R.style.Theme_DeviceDefault : android.R.style.Theme_DeviceDefault_Light;
    }

    /**
     * Hides or shows the status bar (the strip with the clock and notification icons) of a
     * full-screen activity, as chosen in Appearance → "Hide status bar". While it's hidden, a swipe
     * down from the top edge shows it for a moment (Android's "transient" bars).
     *
     * <ul>
     *   <li><b>Android 11+:</b> {@link WindowInsetsController#hide} with the status-bar type. The
     *       window may also use the area around a camera cut-out (Android 15 already allows it for
     *       apps targeting it). Until 2026-10-15 the layout's {@code fitsSystemWindows} still padded
     *       the top by the cut-out, so an empty strip stayed where the status bar had been; now
     *       {@link #fitContent} moves the content up into it.</li>
     *   <li><b>Android 7–10:</b> the older {@code FLAG_FULLSCREEN} window flag. On phones with a notch
     *       (Android 9–10) the notch area then stays black: Android's default, left as it is.</li>
     * </ul>
     * Docs: https://developer.android.com/develop/ui/views/layout/immersive
     */
    @SuppressWarnings("deprecation") // FLAG_FULLSCREEN: still the way below Android 11
    public static void applyStatusBar(Activity activity) {
        boolean hide = isStatusBarHidden(activity);
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                WindowManager.LayoutParams attrs = window.getAttributes();
                int mode = hide ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
                if (attrs.layoutInDisplayCutoutMode != mode) {
                    attrs.layoutInDisplayCutoutMode = mode;
                    window.setAttributes(attrs);
                }
            }
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                if (hide) {
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    controller.hide(WindowInsets.Type.statusBars());
                } else {
                    controller.show(WindowInsets.Type.statusBars());
                }
            }
        } else if (hide) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    /**
     * Keeps the main screen's content clear of the system bars, the keyboard and the camera cut-out,
     * and lets it use the top strip while the status bar is hidden ("Hide status bar", 2026-10-15).
     * The rule itself is in {@link ContentInsets}.
     *
     * <p>How: a "window insets" listener on the layout's root view. Android calls it with the space
     * each edge loses whenever that changes (status bar hidden or shown, keyboard opened, phone turned),
     * and while a listener is set Android doesn't apply {@code fitsSystemWindows} to that view. The
     * listener sets the padding itself and reports the space as used ({@code CONSUMED}), as
     * {@code fitsSystemWindows} did, so nothing inside adds it a second time.
     * Docs: https://developer.android.com/develop/ui/views/layout/display-cutout</p>
     *
     * <p>Android 11+ only: the per-type inset API it needs starts there. Android 7–10 keep the
     * layout's {@code fitsSystemWindows} (the notch area stays black while the bar is hidden there).</p>
     */
    public static void fitContent(View root) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            // The same kinds fitsSystemWindows used (Android's InsetsState: system bars, keyboard for an
            // "adjustResize" screen, cut-out), read separately so the cut-out can be left out at the top.
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            android.graphics.Insets cutout = insets.getInsets(WindowInsets.Type.displayCutout());
            int[] padding = ContentInsets.padding(insets.isVisible(WindowInsets.Type.statusBars()),
                    new int[]{bars.left, bars.top, bars.right, bars.bottom},
                    new int[]{cutout.left, cutout.top, cutout.right, cutout.bottom});
            view.setPadding(padding[0], padding[1], padding[2], padding[3]);
            return WindowInsets.CONSUMED;
        });
        root.requestApplyInsets();
    }

    /**
     * Colours a full-screen activity's window: background = {@code surface}, and the status and
     * navigation bars blend into it (dark icons on light backgrounds and the reverse).
     *
     * <p>On Android 15+ apps are always "edge-to-edge": the bars are transparent and show the window
     * background behind them, so setting the background is enough there.</p>
     */
    @SuppressWarnings("deprecation") // status/nav bar colours: still needed below Android 15
    public static void styleWindow(Activity activity, Scheme scheme) {
        Window window = activity.getWindow();
        int background = scheme.get(Scheme.SURFACE);
        window.setBackgroundDrawable(new ColorDrawable(background));
        boolean lightBars = !scheme.dark;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(background);
            // Dark navigation-bar icons exist only from Android 8.0; before that, keep the bar dark
            // in light mode so its white icons stay visible.
            if (!lightBars || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.setNavigationBarColor(background);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightBars ? mask : 0, mask);
            }
        } else {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                lightFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(lightBars ? (flags | lightFlags) : (flags & ~lightFlags));
        }
    }
}
