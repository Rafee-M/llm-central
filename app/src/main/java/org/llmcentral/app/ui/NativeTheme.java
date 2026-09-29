package org.llmcentral.app.ui;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.llmcentral.app.R;
import org.llmcentral.app.theme.Scheme;

/**
 * Paints the app's few native (non-HTML) views with the current theme {@link Scheme}: the top bar,
 * the loading bar and the assistant drawer (PROJECT.md §4.1).
 *
 * <p>Colours are set in code rather than in XML because they change at runtime (palette, Material
 * You, dark mode) and come from {@code theme/ThemeManager}, not from resource files. Shapes are
 * {@link GradientDrawable}s: rectangles/pills with rounded corners drawn by Android, no image files.</p>
 *
 * <p>Style: Material 3 "minimal" — the bar has the same colour as the page background (no shadow,
 * no line), the account chip is a soft rounded pill, icons use the quieter {@code onSurfaceVariant}.</p>
 */
public final class NativeTheme {

    private NativeTheme() {
    }

    /** Colours the top bar from layout/top_bar.xml (does nothing if the layout has none). */
    public static void styleTopBar(View topBar, Scheme scheme) {
        if (topBar == null) {
            return;
        }
        topBar.setBackgroundColor(scheme.get(Scheme.SURFACE));

        View chip = topBar.findViewById(R.id.providerChip);
        if (chip != null) {
            chip.setBackground(pill(scheme.get(Scheme.SURFACE_CONTAINER_HIGH), ProviderBadge.dp(chip.getContext(), 20)));
        }
        TextView name = topBar.findViewById(R.id.providerName);
        if (name != null) {
            name.setTextColor(scheme.get(Scheme.ON_SURFACE));
        }
        TextView arrow = topBar.findViewById(R.id.providerArrow);
        if (arrow != null) {
            arrow.setTextColor(scheme.get(Scheme.ON_SURFACE_VARIANT));
        }
        ImageView settings = topBar.findViewById(R.id.settingsButton);
        if (settings != null) {
            settings.setImageTintList(ColorStateList.valueOf(scheme.get(Scheme.ON_SURFACE_VARIANT)));
        }
    }

    /** The thin loading bar at the top of the page, in the accent colour. */
    public static void styleProgressBar(ProgressBar bar, Scheme scheme) {
        if (bar != null) {
            ColorStateList accent = ColorStateList.valueOf(scheme.get(Scheme.PRIMARY));
            bar.setIndeterminateTintList(accent);
            bar.setProgressTintList(accent);
        }
    }

    /**
     * The assistant's bottom drawer (activity_chat.xml): a sheet with large rounded top corners
     * (Material 3 bottom sheets use 28dp) and a small "grab handle" line.
     */
    public static void styleDrawer(View card, View handle, Scheme scheme) {
        if (card != null) {
            float r = ProviderBadge.dp(card.getContext(), 28);
            GradientDrawable sheet = new GradientDrawable();
            sheet.setColor(scheme.get(Scheme.SURFACE));
            // Corner order: top-left, top-right, bottom-right, bottom-left (x and y radius each).
            sheet.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            card.setBackground(sheet);
            card.setClipToOutline(true); // the page inside is cut to the rounded corners too
        }
        if (handle != null) {
            // Material 3 drag handle: onSurfaceVariant at 40% opacity, fully rounded.
            int color = (scheme.get(Scheme.ON_SURFACE_VARIANT) & 0x00FFFFFF) | 0x66000000;
            handle.setBackground(pill(color, ProviderBadge.dp(handle.getContext(), 2)));
        }
    }

    /** A filled rectangle with rounded corners ("pill" when the radius is half the height). */
    public static GradientDrawable pill(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }
}
