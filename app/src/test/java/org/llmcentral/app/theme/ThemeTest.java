package org.llmcentral.app.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks the theme building blocks on the computer: the shipped palettes file, the OLED variant, the
 * CSS given to HTML pages, and the tone blending used for Material You on Android 12–13.
 */
public class ThemeTest {

    private static List<Palettes.Palette> shippedPalettes() throws Exception {
        try (InputStream in = new FileInputStream(new File("src/main/assets/palettes.json"))) {
            return Palettes.parse(Palettes.readUtf8(in));
        }
    }

    @Test
    public void shippedPalettesAreTheEightFromTheSpecWithAllRoles() throws Exception {
        List<Palettes.Palette> palettes = shippedPalettes();
        Set<String> ids = new HashSet<>();
        for (Palettes.Palette p : palettes) {
            ids.add(p.id);
            assertFalse(p.light.dark);
            assertTrue(p.dark.dark);
            // Light schemes have light backgrounds and dark text; dark schemes the opposite.
            assertTrue(p.id, Tones.tone(p.light.get(Scheme.SURFACE)) > 90);
            assertTrue(p.id, Tones.tone(p.light.get(Scheme.ON_SURFACE)) < 20);
            assertTrue(p.id, Tones.tone(p.dark.get(Scheme.SURFACE)) < 10);
            assertTrue(p.id, Tones.tone(p.dark.get(Scheme.ON_SURFACE)) > 80);
        }
        // PROJECT.md §4.4 (ids are stored in settings).
        assertEquals(new HashSet<>(Arrays.asList(
                "duck", "ocean", "forest", "lavender", "rose", "teal", "amber", "graphite")), ids);
        assertEquals(Palettes.DEFAULT_ID, palettes.get(0).id);
        assertEquals(0xFFDE5833, palettes.get(0).seed);
    }

    @Test
    public void oledMakesBackgroundsBlackButKeepsCardsVisible() throws Exception {
        Scheme dark = shippedPalettes().get(1).dark;
        Scheme oled = dark.toOled();
        assertTrue(oled.dark);
        assertEquals(0xFF000000, oled.get(Scheme.SURFACE));
        assertEquals(0xFF000000, oled.get("background"));
        assertTrue(oled.get(Scheme.SURFACE_CONTAINER) != 0xFF000000);
        assertEquals("accent colours are unchanged", dark.get(Scheme.PRIMARY), oled.get(Scheme.PRIMARY));
    }

    @Test
    public void cssHasEveryRoleAndReadableLegacyAccent() throws Exception {
        Palettes.Palette ocean = shippedPalettes().get(1);
        String css = ocean.light.toCss();
        assertTrue(css.startsWith(":root{color-scheme:light;"));
        for (String role : Scheme.ROLES) {
            assertTrue(role, css.contains("--md-" + Scheme.kebab(role) + ":#"));
        }
        assertTrue(css.contains("--md-surface-container-high:" + Scheme.hex(ocean.light.get(Scheme.SURFACE_CONTAINER_HIGH))));
        // Old pages put white text on --accent-color: it must be the tone-40 primary in both modes.
        assertTrue(css.contains("--accent-color:" + Scheme.hex(ocean.light.get(Scheme.PRIMARY)) + ";"));
        assertTrue(ocean.dark.toCss().contains("--accent-color:" + Scheme.hex(ocean.dark.get(Scheme.INVERSE_PRIMARY)) + ";"));
        double accentTone = Tones.tone(ocean.dark.get(Scheme.INVERSE_PRIMARY));
        assertTrue("tone " + accentTone, accentTone > 35 && accentTone < 45);
    }

    @Test
    public void hexHelpers() {
        assertEquals("#1A2B3C", Scheme.hex(0xFF1A2B3C));
        assertEquals(0xFF1A2B3C, Scheme.parseHex("#1a2b3c"));
        assertEquals("surface-container-high", Scheme.kebab("surfaceContainerHigh"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void incompleteSchemeIsRejected() {
        new Scheme(false, new java.util.HashMap<>());
    }

    @Test
    public void labRoundTripAndKnownTones() {
        // White is tone 100, black tone 0, mid grey #777777 ≈ tone 50 (Material's definition).
        assertEquals(100, Tones.tone(0xFFFFFFFF), 0.01);
        assertEquals(0, Tones.tone(0xFF000000), 0.01);
        assertEquals(50, Tones.tone(0xFF777777), 0.5);
        // Reference tones computed with Google's Material Color Utilities 0.3.0 (Hct.fromInt(c).tone).
        assertEquals(54.812, Tones.tone(0xFFDE5833), 0.01);
        assertEquals(47.822, Tones.tone(0xFF1E6FD9), 0.01);
        assertEquals(46.283, Tones.tone(0xFF2E7D32), 0.01);
        assertEquals(93.968, Tones.tone(0xFFFCEAE5), 0.01);
        assertEquals(11.886, Tones.tone(0xFF271D1B), 0.01);
        for (int color : new int[]{0xFFDE5833, 0xFF1E6FD9, 0xFF2E7D32, 0xFF808080}) {
            double[] lab = Tones.toLab(color);
            assertEquals(Scheme.hex(color), Scheme.hex(Tones.fromLab(lab[0], lab[1], lab[2])));
        }
    }

    @Test
    public void blendedToneHasExactlyTheWantedLightness() throws Exception {
        // Like Android 12–13: build tone 98 from tones ~99 and ~95 of a real palette's neutral colours.
        Scheme light = shippedPalettes().get(0).light;
        int t99 = light.get("surfaceContainerLowest");     // ≈ tone 100
        int t90 = light.get("surfaceContainerHighest");    // ≈ tone 90
        int t94 = Tones.between(t99, t90, 94);
        assertEquals(94, Tones.tone(t94), 0.35); // 1 unit of rounding to 8-bit RGB
        // Outside the two inputs the lightness is still exact; only the tint stops at the nearer input.
        int t80 = Tones.between(t99, t90, 80);
        assertEquals(80, Tones.tone(t80), 0.35);
        assertEquals(Tones.toLab(t90)[1], Tones.toLab(t80)[1], 1.0);
    }
}
