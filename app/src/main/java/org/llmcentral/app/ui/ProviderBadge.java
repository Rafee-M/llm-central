package org.llmcentral.app.ui;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;

import org.llmcentral.app.provider.ProviderSpec;

/**
 * Draws a provider's "icon": a coloured circle with 1–2 letters (e.g. an orange "D" for duck.ai).
 *
 * <p><b>Why letters instead of logos:</b> real logos are trademarks and would add image files
 * (APK size). A {@link GradientDrawable} is a shape drawn by Android at runtime: it costs no file
 * and scales sharply to any screen. Real icons (e.g. the sites' favicons) can replace this later
 * without changing any other code.</p>
 */
public final class ProviderBadge {

    private ProviderBadge() {
        // Only static methods.
    }

    /**
     * Turns {@code view} into the badge of {@code provider}.
     *
     * @param sizePx diameter in pixels (convert from dp with {@link #dp})
     */
    public static void apply(TextView view, ProviderSpec provider, int sizePx) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(provider.badgeColor);
        view.setBackground(circle);
        view.setText(provider.badgeText);
        view.setTextColor(0xFFFFFFFF);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        // Letters take ~40% of the circle; two letters ("DS") a bit smaller so they fit.
        float textPx = sizePx * (provider.badgeText.length() > 1 ? 0.34f : 0.42f);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textPx);
        view.setIncludeFontPadding(false);
        view.setMinimumWidth(sizePx);
        view.setMinimumHeight(sizePx);
        if (view.getLayoutParams() != null) {
            view.getLayoutParams().width = sizePx;
            view.getLayoutParams().height = sizePx;
        }
        // Screen readers read the provider name instead of the letters.
        view.setContentDescription(provider.displayName);
    }

    /**
     * Converts dp ("density-independent pixels", Android's size unit that looks the same on every
     * screen) to real pixels for this device.
     */
    public static int dp(android.content.Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
