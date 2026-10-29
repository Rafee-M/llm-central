package org.llmcentral.app.ui;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.widget.TextView;

import org.llmcentral.app.R;
import org.llmcentral.app.provider.ProviderSpec;
import org.llmcentral.app.provider.Providers;

/**
 * Draws a provider's "icon": a circle in the provider's colour with its logo in white (duck.ai,
 * ChatGPT, Claude, DeepSeek, Z.ai), or with 1–2 letters if a provider has no logo (its
 * {@code badgeText}, e.g. "DS").
 *
 * <p><b>How:</b> the circle is a {@link GradientDrawable}, a shape Android draws at runtime. The
 * logos are vector drawables built into the app ({@code res/drawable/ic_logo_*.xml}; nothing is
 * downloaded), so they scale sharply to any size. A {@link LayerDrawable} stacks the logo on the
 * circle. To give another provider a logo: add its {@code ic_logo_<id>.xml} and one line in
 * {@link #logoFor}. The Settings page draws the same badges from its own copy of the logo paths
 * ({@code settings.html}, {@code LOGOS}).</p>
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
        int logo = logoFor(provider);
        if (logo != 0) {
            // Circle underneath, logo on top with a margin all round (the logo fills ~56% of the circle).
            Drawable glyph = view.getContext().getDrawable(logo);
            LayerDrawable layers = new LayerDrawable(new Drawable[]{circle, glyph});
            int inset = Math.round(sizePx * 0.22f);
            layers.setLayerInset(1, inset, inset, inset, inset);
            view.setBackground(layers);
            view.setText("");
        } else {
            view.setBackground(circle);
            view.setText(provider.badgeText);
        }
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

    /** @return the provider's built-in logo, or 0 if it has none (it then shows its letters). */
    static int logoFor(ProviderSpec provider) {
        if (provider == Providers.DUCK) {
            return R.drawable.ic_logo_duck;
        } else if (provider == Providers.CHATGPT) {
            return R.drawable.ic_logo_chatgpt;
        } else if (provider == Providers.CLAUDE) {
            return R.drawable.ic_logo_claude;
        } else if (provider == Providers.DEEPSEEK) {
            return R.drawable.ic_logo_deepseek;
        } else if (provider == Providers.ZAI) {
            return R.drawable.ic_logo_zai;
        } else if (provider == Providers.GEMINI) {
            return R.drawable.ic_logo_gemini;
        }
        return 0;
    }

    /**
     * Converts dp ("density-independent pixels", Android's size unit that looks the same on every
     * screen) to real pixels for this device.
     */
    public static int dp(android.content.Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
