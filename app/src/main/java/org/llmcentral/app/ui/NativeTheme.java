package org.llmcentral.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
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

    /**
     * The "Desktop site" toggle in the account menu's icon row (ui/SwitcherPopup), drawn as a Material 3
     * toggle icon button (the button must be 48 × 48dp):
     * <ul>
     *   <li><b>off</b>: outlined monitor in {@code onSurfaceVariant}, no background (like the gear);</li>
     *   <li><b>on</b>: filled monitor in {@code onSecondaryContainer} on a round
     *       {@code secondaryContainer} background (the "selected" look of a tonal icon button).</li>
     * </ul>
     * Also tells screen readers whether it's on ("state description", Android 11+; older versions
     * hear it in the button's label). Docs: https://m3.material.io/components/icon-buttons/specs
     */
    public static void styleDesktopButton(ImageView button, boolean on, Scheme scheme) {
        styleToggleButton(button, on, scheme, R.drawable.ic_desktop, R.drawable.ic_desktop_on, R.string.desktop_site);
    }

    /**
     * Any on/off icon button of the account menu's icon row, drawn like {@link #styleDesktopButton} (outlined
     * icon when off; icon on a filled {@code secondaryContainer} circle when on), with the state for screen
     * readers. Used by Desktop site and Hide status bar.
     *
     * @param offIcon icon while off; {@code onIcon} while on (may be the same drawable)
     */
    public static void styleToggleButton(ImageView button, boolean on, Scheme scheme, int offIcon, int onIcon,
                                         int labelRes) {
        Context context = button.getContext();
        button.setSelected(on);
        button.setImageResource(on ? onIcon : offIcon);
        button.setImageTintList(ColorStateList.valueOf(
                scheme.get(on ? Scheme.ON_SECONDARY_CONTAINER : Scheme.ON_SURFACE_VARIANT)));
        // A 40dp circle inside the 48dp button, plus the usual touch ripple on top of it.
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(scheme.get(Scheme.SECONDARY_CONTAINER));
        int inset = ProviderBadge.dp(context, 4);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(0xFFFFFFFF);
        int ripple = (scheme.get(Scheme.ON_SURFACE) & 0x00FFFFFF) | 0x1F000000; // onSurface at 12%
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple),
                on ? new InsetDrawable(circle, inset) : null, new InsetDrawable(mask, inset)));
        String label = context.getString(labelRes);
        String state = context.getString(on ? R.string.state_on : R.string.state_off);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            button.setContentDescription(label);
            button.setStateDescription(state);
        } else {
            button.setContentDescription(label + ", " + state);
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
