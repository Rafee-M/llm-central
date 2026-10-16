package org.llmcentral.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.llmcentral.app.R;

/**
 * What the screen shows while the account on screen is unloaded (account menu → the ✕ on its row,
 * web/TabManager.unload): a black screen with the app's icon in the centre and a short hint.
 * Tapping anywhere loads the account again. The same screen with "Tap to retry" covers a page that failed
 * to load (web/LoadErrors, O-M4).
 *
 * <p>Built from plain framework views (no layout file, no UI library), like the account menu. The
 * background is pure black on purpose (also in the light theme): it reads as "nothing loaded here" and
 * costs nothing to draw.</p>
 */
public final class UnloadedPlaceholder {

    /** Light grey for the hint: about 7.6:1 contrast on black, above the 4.5:1 that WCAG asks for. */
    private static final int HINT_COLOR = 0xFF9AA0A6;

    private UnloadedPlaceholder() {
        // Only static methods.
    }

    /**
     * @param onTap runs when the placeholder is tapped (MainActivity loads the account again)
     * @return a full-size view, initially GONE; web/TabManager makes it visible while needed
     */
    public static View create(Context context, Runnable onTap) {
        return create(context, R.string.tab_unloaded, onTap);
    }

    /**
     * The same black screen with another hint, e.g. the retry screen after a page failed to load (O-M4:
     * "Couldn't load this page. Tap to retry").
     *
     * @param textRes the hint, also what screen readers say
     */
    public static View create(Context context, int textRes, Runnable onTap) {
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Color.BLACK);
        root.setVisibility(View.GONE);
        root.setClickable(true); // also stops taps from reaching hidden pages behind it
        root.setFocusable(true);
        root.setContentDescription(context.getString(textRes));
        root.setOnClickListener(v -> onTap.run());

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        // The launcher icon, as the phone draws it: on Android 8+ an "adaptive icon", which applies the
        // phone's icon shape (circle, squircle, …) by itself (the same trick as ui/AboutWindow).
        ImageView icon = new ImageView(context);
        Drawable appIcon = context.getPackageManager().getApplicationIcon(context.getApplicationInfo());
        icon.setImageDrawable(appIcon);
        int size = ProviderBadge.dp(context, 96);
        column.addView(icon, new LinearLayout.LayoutParams(size, size));

        TextView hint = new TextView(context);
        hint.setText(textRes);
        hint.setTextColor(HINT_COLOR);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = ProviderBadge.dp(context, 16);
        column.addView(hint, hintParams);

        root.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return root;
    }
}
