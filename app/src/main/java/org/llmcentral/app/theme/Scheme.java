package org.llmcentral.app.theme;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One complete Material 3 colour scheme: a colour for each <b>role</b> (PROJECT.md §4.1).
 *
 * <p><b>Colour roles</b> are Material 3's idea of naming colours by their job instead of their hue:
 * {@code surface} = background of screens, {@code onSurface} = text on it, {@code primary} = the
 * main accent (buttons, switches), {@code secondaryContainer} = a soft highlight (selected item),
 * and so on. Every screen uses roles, so switching the whole app to another palette, to dark mode or
 * to the wallpaper colours only means swapping this one object.
 * Guide: https://m3.material.io/styles/color/roles</p>
 *
 * <p>Colours are stored as ARGB {@code int}s (Android's colour format: 0xAARRGGBB). Plain Java, so
 * SchemeTest checks it on the computer.</p>
 */
public final class Scheme {

    // Role names. They match tools/palettes/generate.mjs and assets/palettes.json.
    public static final String PRIMARY = "primary";
    public static final String ON_PRIMARY = "onPrimary";
    public static final String INVERSE_PRIMARY = "inversePrimary";
    public static final String PRIMARY_CONTAINER = "primaryContainer";
    public static final String ON_PRIMARY_CONTAINER = "onPrimaryContainer";
    public static final String SECONDARY_CONTAINER = "secondaryContainer";
    public static final String ON_SECONDARY_CONTAINER = "onSecondaryContainer";
    public static final String SURFACE = "surface";
    public static final String ON_SURFACE = "onSurface";
    public static final String ON_SURFACE_VARIANT = "onSurfaceVariant";
    public static final String SURFACE_CONTAINER = "surfaceContainer";
    public static final String SURFACE_CONTAINER_HIGH = "surfaceContainerHigh";
    public static final String OUTLINE = "outline";
    public static final String OUTLINE_VARIANT = "outlineVariant";

    /** Every role a scheme must have, in the order of palettes.json. */
    public static final String[] ROLES = {
            "primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "inversePrimary",
            "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
            "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
            "error", "onError", "errorContainer", "onErrorContainer",
            "background", "onBackground", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
            "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer",
            "surfaceContainerHigh", "surfaceContainerHighest",
            "inverseSurface", "inverseOnSurface", "outline", "outlineVariant",
    };

    /** true = a dark scheme (light text on dark surfaces). */
    public final boolean dark;
    private final Map<String, Integer> colors;

    /**
     * @param colors role → ARGB colour; must contain every name in {@link #ROLES}
     * @throws IllegalArgumentException if a role is missing
     */
    public Scheme(boolean dark, Map<String, Integer> colors) {
        for (String role : ROLES) {
            if (!colors.containsKey(role)) {
                throw new IllegalArgumentException("Missing colour role: " + role);
            }
        }
        this.dark = dark;
        this.colors = new HashMap<>(colors);
    }

    /** @return the ARGB colour of a role, e.g. {@code get(Scheme.SURFACE)}. */
    public int get(String role) {
        Integer color = colors.get(role);
        if (color == null) {
            throw new IllegalArgumentException("Unknown colour role: " + role);
        }
        return color;
    }

    /**
     * The "Black (OLED)" variant of a dark scheme (PROJECT.md §4.3): backgrounds become pure black
     * (#000000). On OLED screens black pixels are switched off, which saves battery and looks deep.
     * Cards and menus keep a very slight grey step (#0B0B0B → #1C1C1C) so they stay visible.
     */
    public Scheme toOled() {
        Map<String, Integer> c = new HashMap<>(colors);
        c.put("background", 0xFF000000);
        c.put("surface", 0xFF000000);
        c.put("surfaceContainerLowest", 0xFF000000);
        c.put("surfaceContainerLow", 0xFF0B0B0B);
        c.put("surfaceContainer", 0xFF111111);
        c.put("surfaceContainerHigh", 0xFF171717);
        c.put("surfaceContainerHighest", 0xFF1C1C1C);
        return new Scheme(true, c);
    }

    /**
     * The scheme as CSS custom properties ("CSS variables") for the app's own HTML pages, e.g.
     * {@code --md-surface-container-high:#ECE6F0}. Pages use them like {@code var(--md-primary)}.
     *
     * <p>It also sets the older variable names used by the pages from duckAssist (offline viewer,
     * script manager, prompt box: {@code --bg-color}, {@code --accent-color}…) so those follow the
     * theme too without rewriting them. Their buttons use white text on the accent colour, so the
     * accent is always the tone-40 primary (readable with white text in light and dark mode):
     * {@code primary} in a light scheme, {@code inversePrimary} in a dark one.</p>
     */
    public String toCss() {
        StringBuilder css = new StringBuilder(":root{");
        css.append("color-scheme:").append(dark ? "dark" : "light").append(';');
        for (String role : ROLES) {
            css.append("--md-").append(kebab(role)).append(':').append(hex(get(role))).append(';');
        }
        String accent = hex(get(dark ? INVERSE_PRIMARY : PRIMARY));
        appendAlias(css, "--bg-color", SURFACE);
        appendAlias(css, "--card-bg", SURFACE_CONTAINER);
        appendAlias(css, "--card-hover", SURFACE_CONTAINER_HIGH);
        appendAlias(css, "--input-bg", "surfaceContainerHighest");
        appendAlias(css, "--toggle-bg", OUTLINE);
        appendAlias(css, "--text-primary", ON_SURFACE);
        appendAlias(css, "--text-secondary", ON_SURFACE_VARIANT);
        appendAlias(css, "--border-color", OUTLINE_VARIANT);
        appendAlias(css, "--badge-bg", SECONDARY_CONTAINER);
        appendAlias(css, "--badge-text", ON_SECONDARY_CONTAINER);
        appendAlias(css, "--danger-color", "error");
        appendAlias(css, "--danger-hover", "error");
        css.append("--accent-color:").append(accent).append(';');
        css.append("--accent-hover:").append(accent).append(';');
        css.append("--border-focus:").append(accent).append(';');
        css.append("--bubble-user:").append(accent).append(';');
        css.append("--bubble-ai:transparent;"); // the viewer's answer bubbles have no background
        return css.append('}').toString();
    }

    private void appendAlias(StringBuilder css, String name, String role) {
        css.append(name).append(':').append(hex(get(role))).append(';');
    }

    /** 0xFF1A2B3C → "#1A2B3C" (alpha dropped; all scheme colours are opaque). */
    public static String hex(int argb) {
        return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }

    /** "#1a2b3c" → 0xFF1A2B3C. */
    public static int parseHex(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6) {
            throw new IllegalArgumentException("Expected #RRGGBB: " + hex);
        }
        return 0xFF000000 | Integer.parseInt(h, 16);
    }

    /** "surfaceContainerHigh" → "surface-container-high" (CSS naming style). */
    static String kebab(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char ch : camel.toCharArray()) {
            if (Character.isUpperCase(ch)) {
                sb.append('-').append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
