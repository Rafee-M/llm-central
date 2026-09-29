package org.llmcentral.app.theme;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.HashMap;
import java.util.Map;

/**
 * "Material You": builds a {@link Scheme} from the phone's own wallpaper-based colours
 * (Android 12+ only; PROJECT.md §4.2).
 *
 * <p>Android exposes those colours as resources like {@code android.R.color.system_accent1_600}.
 * Which resource belongs to which Material 3 role comes from the official table in Material
 * Components' documentation (docs/theming/Color.md, "Dynamic color" columns):
 * https://github.com/material-components/material-components-android/blob/master/docs/theming/Color.md</p>
 * <ul>
 *   <li><b>Android 14+ (API 34):</b> one resource per role, e.g. {@code system_surface_container_high_light}.</li>
 *   <li><b>Android 12–13 (API 31–33):</b> only tonal palettes: {@code system_accent1_*} (primary),
 *       {@code accent2} (secondary), {@code accent3} (tertiary), {@code neutral1/2} (greys), each at
 *       fixed tones. Background tones that aren't provided are blended with {@link Tones}. Error
 *       colours aren't provided at all, so the standard Material error red is used.</li>
 * </ul>
 */
@RequiresApi(Build.VERSION_CODES.S)
final class DynamicColors {

    private DynamicColors() {
    }

    /**
     * @param errorSource a scheme to take the error colours from on Android 12–13 (they're the same
     *                    standard red in every Material palette)
     */
    static Scheme scheme(Context context, boolean dark, Scheme errorSource) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? fromRoles(context, dark)
                : fromTonalPalettes(context, dark, errorSource);
    }

    // ------------------------------------------------------------------ Android 14+

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private static Scheme fromRoles(Context c, boolean dark) {
        Map<String, Integer> m = new HashMap<>();
        // {role, light resource, dark resource}
        Object[][] table = {
                {"primary", android.R.color.system_primary_light, android.R.color.system_primary_dark},
                {"onPrimary", android.R.color.system_on_primary_light, android.R.color.system_on_primary_dark},
                {"primaryContainer", android.R.color.system_primary_container_light, android.R.color.system_primary_container_dark},
                {"onPrimaryContainer", android.R.color.system_on_primary_container_light, android.R.color.system_on_primary_container_dark},
                {"inversePrimary", android.R.color.system_primary_dark, android.R.color.system_primary_light},
                {"secondary", android.R.color.system_secondary_light, android.R.color.system_secondary_dark},
                {"onSecondary", android.R.color.system_on_secondary_light, android.R.color.system_on_secondary_dark},
                {"secondaryContainer", android.R.color.system_secondary_container_light, android.R.color.system_secondary_container_dark},
                {"onSecondaryContainer", android.R.color.system_on_secondary_container_light, android.R.color.system_on_secondary_container_dark},
                {"tertiary", android.R.color.system_tertiary_light, android.R.color.system_tertiary_dark},
                {"onTertiary", android.R.color.system_on_tertiary_light, android.R.color.system_on_tertiary_dark},
                {"tertiaryContainer", android.R.color.system_tertiary_container_light, android.R.color.system_tertiary_container_dark},
                {"onTertiaryContainer", android.R.color.system_on_tertiary_container_light, android.R.color.system_on_tertiary_container_dark},
                {"error", android.R.color.system_error_light, android.R.color.system_error_dark},
                {"onError", android.R.color.system_on_error_light, android.R.color.system_on_error_dark},
                {"errorContainer", android.R.color.system_error_container_light, android.R.color.system_error_container_dark},
                {"onErrorContainer", android.R.color.system_on_error_container_light, android.R.color.system_on_error_container_dark},
                {"background", android.R.color.system_background_light, android.R.color.system_background_dark},
                {"onBackground", android.R.color.system_on_background_light, android.R.color.system_on_background_dark},
                {"surface", android.R.color.system_surface_light, android.R.color.system_surface_dark},
                {"onSurface", android.R.color.system_on_surface_light, android.R.color.system_on_surface_dark},
                {"surfaceVariant", android.R.color.system_surface_variant_light, android.R.color.system_surface_variant_dark},
                {"onSurfaceVariant", android.R.color.system_on_surface_variant_light, android.R.color.system_on_surface_variant_dark},
                {"surfaceContainerLowest", android.R.color.system_surface_container_lowest_light, android.R.color.system_surface_container_lowest_dark},
                {"surfaceContainerLow", android.R.color.system_surface_container_low_light, android.R.color.system_surface_container_low_dark},
                {"surfaceContainer", android.R.color.system_surface_container_light, android.R.color.system_surface_container_dark},
                {"surfaceContainerHigh", android.R.color.system_surface_container_high_light, android.R.color.system_surface_container_high_dark},
                {"surfaceContainerHighest", android.R.color.system_surface_container_highest_light, android.R.color.system_surface_container_highest_dark},
                {"inverseSurface", android.R.color.system_surface_dark, android.R.color.system_surface_light},
                {"inverseOnSurface", android.R.color.system_on_surface_dark, android.R.color.system_on_surface_light},
                {"outline", android.R.color.system_outline_light, android.R.color.system_outline_dark},
                {"outlineVariant", android.R.color.system_outline_variant_light, android.R.color.system_outline_variant_dark},
        };
        for (Object[] row : table) {
            m.put((String) row[0], c.getColor((Integer) row[dark ? 2 : 1]));
        }
        return new Scheme(dark, m);
    }

    // ------------------------------------------------------------------ Android 12–13

    private static Scheme fromTonalPalettes(Context c, boolean dark, Scheme errorSource) {
        Map<String, Integer> m = new HashMap<>();
        // Accent roles: {role, light tone resource, dark tone resource} (MDC table, API 31 columns).
        int[][] a1 = {
                {android.R.color.system_accent1_600, android.R.color.system_accent1_200},  // primary
                {android.R.color.system_accent1_0, android.R.color.system_accent1_800},    // onPrimary
                {android.R.color.system_accent1_100, android.R.color.system_accent1_700},  // primaryContainer
                {android.R.color.system_accent1_900, android.R.color.system_accent1_100},  // onPrimaryContainer
        };
        int[][] a2 = {
                {android.R.color.system_accent2_600, android.R.color.system_accent2_200},
                {android.R.color.system_accent2_0, android.R.color.system_accent2_800},
                {android.R.color.system_accent2_100, android.R.color.system_accent2_700},
                {android.R.color.system_accent2_900, android.R.color.system_accent2_100},
        };
        int[][] a3 = {
                {android.R.color.system_accent3_600, android.R.color.system_accent3_200},
                {android.R.color.system_accent3_0, android.R.color.system_accent3_800},
                {android.R.color.system_accent3_100, android.R.color.system_accent3_700},
                {android.R.color.system_accent3_900, android.R.color.system_accent3_100},
        };
        putAccent(c, m, dark, a1, "primary", "onPrimary", "primaryContainer", "onPrimaryContainer");
        putAccent(c, m, dark, a2, "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer");
        putAccent(c, m, dark, a3, "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer");
        m.put("inversePrimary", c.getColor(dark ? android.R.color.system_accent1_600 : android.R.color.system_accent1_200));

        for (String role : new String[]{"error", "onError", "errorContainer", "onErrorContainer"}) {
            m.put(role, errorSource.get(role));
        }

        // Greys. neutral1 = text greys, neutral2 = slightly tinted background greys.
        int onSurface = c.getColor(dark ? android.R.color.system_neutral1_100 : android.R.color.system_neutral1_900);
        m.put("onSurface", onSurface);
        m.put("onBackground", onSurface);
        m.put("surfaceVariant", c.getColor(dark ? android.R.color.system_neutral2_700 : android.R.color.system_neutral2_100));
        m.put("onSurfaceVariant", c.getColor(dark ? android.R.color.system_neutral2_200 : android.R.color.system_neutral2_700));
        m.put("inverseSurface", c.getColor(dark ? android.R.color.system_neutral1_100 : android.R.color.system_neutral1_800));
        m.put("inverseOnSurface", c.getColor(dark ? android.R.color.system_neutral1_800 : android.R.color.system_neutral1_50));
        m.put("outline", c.getColor(dark ? android.R.color.system_neutral2_400 : android.R.color.system_neutral2_500));
        m.put("outlineVariant", c.getColor(dark ? android.R.color.system_neutral2_700 : android.R.color.system_neutral2_200));

        // Background tones (neutral2 provides 100, 99, 95, 90 … 10, 0; the rest is blended).
        int t100 = c.getColor(android.R.color.system_neutral2_0);
        int t99 = c.getColor(android.R.color.system_neutral2_10);
        int t95 = c.getColor(android.R.color.system_neutral2_50);
        int t90 = c.getColor(android.R.color.system_neutral2_100);
        int t30 = c.getColor(android.R.color.system_neutral2_700);
        int t20 = c.getColor(android.R.color.system_neutral2_800);
        int t10 = c.getColor(android.R.color.system_neutral2_900);
        int t0 = c.getColor(android.R.color.system_neutral2_1000);
        int surface = dark ? Tones.between(t10, t0, 6) : Tones.between(t99, t95, 98);
        m.put("surface", surface);
        m.put("background", surface);
        m.put("surfaceContainerLowest", dark ? Tones.between(t10, t0, 4) : t100);
        m.put("surfaceContainerLow", dark ? t10 : Tones.between(t99, t95, 96));
        m.put("surfaceContainer", dark ? Tones.between(t20, t10, 12) : Tones.between(t95, t90, 94));
        m.put("surfaceContainerHigh", dark ? Tones.between(t20, t10, 17) : Tones.between(t95, t90, 92));
        m.put("surfaceContainerHighest", dark ? Tones.between(t30, t20, 22) : t90);
        return new Scheme(dark, m);
    }

    private static void putAccent(Context c, Map<String, Integer> m, boolean dark, int[][] res,
                                  String role, String onRole, String container, String onContainer) {
        String[] roles = {role, onRole, container, onContainer};
        for (int i = 0; i < roles.length; i++) {
            m.put(roles[i], c.getColor(res[i][dark ? 1 : 0]));
        }
    }
}
