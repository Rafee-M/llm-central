package org.llmcentral.app.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.llmcentral.app.R;
import org.llmcentral.app.provider.ProviderSpec;

import java.util.List;

/**
 * The drop-down list that opens when the provider chip in the top bar is tapped
 * (PROJECT.md R-SW-2). One row per provider: badge + name, and a check mark on the current one.
 *
 * <p>Built with plain Android views ({@link PopupWindow} + {@link LinearLayout}), without any UI
 * library, to keep the app small (decision D1). The rounded Material 3-style look comes from a
 * {@link GradientDrawable} with rounded corners and the colours in {@code res/values/colors.xml}
 * (and {@code values-night/} for dark mode).</p>
 */
public final class SwitcherPopup {

    /** Called with the provider the user picked. */
    public interface OnPick {
        void onPick(ProviderSpec provider);
    }

    private SwitcherPopup() {
        // Only static methods.
    }

    /**
     * Shows the list just below {@code anchor} (the chip).
     *
     * @param providers providers to list, in order
     * @param current   the provider on screen (gets a check mark)
     */
    public static void show(View anchor, List<ProviderSpec> providers, ProviderSpec current, OnPick onPick) {
        Context context = anchor.getContext();
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = ProviderBadge.dp(context, 8);
        list.setPadding(0, pad, 0, pad);

        // Rounded "card" background, like a Material 3 menu (corner radius 16dp).
        GradientDrawable background = new GradientDrawable();
        background.setColor(context.getColor(R.color.menu_background));
        background.setCornerRadius(ProviderBadge.dp(context, 16));
        list.setBackground(background);

        // A PopupWindow is a small floating window; "focusable" means a tap outside or Back closes it.
        PopupWindow popup = new PopupWindow(list, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(ProviderBadge.dp(context, 8)); // drop shadow
        popup.setOutsideTouchable(true);

        for (ProviderSpec provider : providers) {
            list.addView(createRow(context, provider, provider == current, v -> {
                popup.dismiss();
                onPick.onPick(provider);
            }));
        }
        popup.showAsDropDown(anchor, 0, ProviderBadge.dp(context, 4), Gravity.START);
    }

    /** One row: [badge] Name ✓ — at least 48dp tall so it's easy to tap (accessibility guideline). */
    private static View createRow(Context context, ProviderSpec provider, boolean isCurrent,
                                 View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(ProviderBadge.dp(context, 48));
        row.setMinimumWidth(ProviderBadge.dp(context, 220));
        int h = ProviderBadge.dp(context, 16);
        row.setPadding(h, 0, h, 0);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(onClick);
        // Standard touch ripple effect from the system theme.
        TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        row.setForeground(context.getDrawable(ripple.resourceId));

        TextView badge = new TextView(context);
        int badgeSize = ProviderBadge.dp(context, 28);
        row.addView(badge, new LinearLayout.LayoutParams(badgeSize, badgeSize));
        ProviderBadge.apply(badge, provider, badgeSize);

        TextView name = new TextView(context);
        name.setText(provider.displayName);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTextColor(context.getColor(R.color.on_surface));
        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMarginStart(ProviderBadge.dp(context, 16));
        row.addView(name, nameParams);

        if (isCurrent) {
            TextView check = new TextView(context);
            check.setText("✓"); // ✓
            check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            check.setTextColor(context.getColor(R.color.accent));
            check.setContentDescription("current");
            row.addView(check);
            row.setSelected(true);
        }
        return row;
    }
}
