package org.llmcentral.app.theme;

/**
 * Makes an in-between shade of a colour, used for Material You on Android 12 and 13.
 *
 * <p><b>Why:</b> Android 12–13 give apps the wallpaper palette only at fixed "tones" (lightness
 * steps 100, 99, 95, 90, 80 … 10, 0), but Material 3's background roles need tones like 98, 96, 94,
 * 92 (light) or 6, 4, 12, 17, 22 (dark). Android 14+ provides those directly. On 12–13 we blend the
 * two nearest provided tones.</p>
 *
 * <p><b>How:</b> Material's "tone" is the L* (lightness) value of the CIELAB colour space, which is
 * designed so that equal number steps look like equal lightness steps to the eye. We convert both
 * colours to L*a*b*, set L* to exactly the tone we want and blend a* and b* (the tint) proportionally,
 * then convert back. This is the approximation documented in PROJECT.md §4.2; it differs from
 * Android 14's exact values by at most a barely visible amount in tint, never in lightness.
 * Formulas: https://en.wikipedia.org/wiki/CIELAB_color_space (sRGB, D65 white point).</p>
 *
 * <p>Plain Java, checked by TonesTest.</p>
 */
final class Tones {

    // D65 reference white (the standard "daylight" white used by sRGB).
    private static final double WX = 95.047;
    private static final double WY = 100.0;
    private static final double WZ = 108.883;

    private Tones() {
    }

    /**
     * @param a    one provided colour (e.g. tone 99)
     * @param b    the other (e.g. tone 95)
     * @param tone the wanted tone (L*, 0–100), normally between the two
     * @return a colour with lightness {@code tone} and a tint blended from {@code a} and {@code b}
     */
    static int between(int a, int b, double tone) {
        double[] la = toLab(a);
        double[] lb = toLab(b);
        double span = lb[0] - la[0];
        double t = Math.abs(span) < 1e-6 ? 0 : (tone - la[0]) / span;
        t = Math.max(0, Math.min(1, t));
        return fromLab(tone, la[1] + (lb[1] - la[1]) * t, la[2] + (lb[2] - la[2]) * t);
    }

    /** @return the L* (tone) of a colour, 0 = black … 100 = white. */
    static double tone(int argb) {
        return toLab(argb)[0];
    }

    static double[] toLab(int argb) {
        double r = linear((argb >> 16) & 0xFF);
        double g = linear((argb >> 8) & 0xFF);
        double bl = linear(argb & 0xFF);
        // sRGB → XYZ (scaled so white has Y = 100).
        double x = (0.41233895 * r + 0.35762064 * g + 0.18051042 * bl) * 100;
        double y = (0.2126 * r + 0.7152 * g + 0.0722 * bl) * 100;
        double z = (0.01932141 * r + 0.11916382 * g + 0.95034478 * bl) * 100;
        double fx = labF(x / WX);
        double fy = labF(y / WY);
        double fz = labF(z / WZ);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    static int fromLab(double l, double a, double b) {
        double fy = (l + 16) / 116;
        double fx = fy + a / 500;
        double fz = fy - b / 200;
        double x = labInvF(fx) * WX / 100;
        double y = labInvF(fy) * WY / 100;
        double z = labInvF(fz) * WZ / 100;
        // XYZ → linear sRGB → sRGB bytes.
        double r = 3.2413774792388685 * x - 1.5376652402851851 * y - 0.49885366846268053 * z;
        double g = -0.9691452513005321 * x + 1.8758853451067872 * y + 0.04156585616912061 * z;
        double bl = 0.05562093689691305 * x - 0.20395524564742123 * y + 1.0571799111220335 * z;
        return 0xFF000000 | (delinear(r) << 16) | (delinear(g) << 8) | delinear(bl);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.040449936 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int delinear(double linear) {
        double c = linear <= 0.0031308 ? linear * 12.92 : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
        return (int) Math.max(0, Math.min(255, Math.round(c * 255)));
    }

    private static double labF(double t) {
        double e = 216.0 / 24389.0;
        double kappa = 24389.0 / 27.0;
        return t > e ? Math.cbrt(t) : (kappa * t + 16) / 116;
    }

    private static double labInvF(double ft) {
        double e = 216.0 / 24389.0;
        double kappa = 24389.0 / 27.0;
        double cube = ft * ft * ft;
        return cube > e ? cube : (116 * ft - 16) / kappa;
    }
}
