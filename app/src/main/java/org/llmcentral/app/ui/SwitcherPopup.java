package org.llmcentral.app.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.llmcentral.app.R;
import org.llmcentral.app.account.Account;
import org.llmcentral.app.provider.ProviderSpec;
import org.llmcentral.app.provider.Providers;

import java.util.List;

/**
 * The drop-down list that opens when the chip in the top bar is tapped (PROJECT.md R-SW-2).
 * One row per <b>visible account</b>: provider badge + the name the user gave it (and the provider's
 * name underneath when it differs, e.g. "Work / ChatGPT"). The current account is highlighted.
 * The last row, "Manage accounts", opens the Accounts part of the settings.
 *
 * <p>Built with plain Android views ({@link PopupWindow} + {@link LinearLayout}), without any UI
 * library, to keep the app small (decision D1). The rounded Material 3-style look comes from
 * {@link GradientDrawable}s and the colours in {@code res/values/colors.xml}.</p>
 */
public final class SwitcherPopup {

    /** Called with the account the user picked. */
    public interface OnPick {
        void onPick(Account account);
    }

    private SwitcherPopup() {
        // Only static methods.
    }

    /**
     * Shows the list just below {@code anchor} (the chip).
     *
     * @param accounts accounts to list, in order (the visible ones)
     * @param current  the account on screen (highlighted)
     * @param onManage runs when "Manage accounts" is tapped
     */
    public static void show(View anchor, List<Account> accounts, Account current, OnPick onPick,
                            Runnable onManage) {
        Context context = anchor.getContext();
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = ProviderBadge.dp(context, 8);
        list.setPadding(pad, pad, pad, pad);

        // Rounded "card" background, like a Material 3 menu.
        GradientDrawable background = new GradientDrawable();
        background.setColor(context.getColor(R.color.menu_background));
        background.setCornerRadius(ProviderBadge.dp(context, 20));
        list.setBackground(background);

        // A PopupWindow is a small floating window; "focusable" means a tap outside or Back closes it.
        PopupWindow popup = new PopupWindow(list, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(ProviderBadge.dp(context, 6)); // soft shadow
        popup.setOutsideTouchable(true);

        for (Account account : accounts) {
            boolean isCurrent = account.id.equals(current.id);
            list.addView(createAccountRow(context, account, isCurrent, v -> {
                popup.dismiss();
                onPick.onPick(account);
            }));
        }

        // Thin divider, then "Manage accounts".
        View divider = new View(context);
        divider.setBackgroundColor(context.getColor(R.color.outline_variant));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, ProviderBadge.dp(context, 1)));
        dividerParams.setMargins(pad, pad, pad, pad);
        list.addView(divider, dividerParams);
        list.addView(createManageRow(context, v -> {
            popup.dismiss();
            onManage.run();
        }));

        popup.showAsDropDown(anchor, 0, ProviderBadge.dp(context, 4), Gravity.START);
    }

    /** One row: [badge] Name (provider) — at least 56dp tall so it's easy to tap. */
    private static View createAccountRow(Context context, Account account, boolean isCurrent,
                                         View.OnClickListener onClick) {
        ProviderSpec provider = Providers.byId(account.providerId);
        LinearLayout row = baseRow(context, onClick);
        if (isCurrent) {
            // Material 3 marks the selected menu item with a tinted, rounded background.
            GradientDrawable selected = new GradientDrawable();
            selected.setColor(context.getColor(R.color.selected_background));
            selected.setCornerRadius(ProviderBadge.dp(context, 14));
            row.setBackground(selected);
            row.setSelected(true);
        }

        TextView badge = new TextView(context);
        int badgeSize = ProviderBadge.dp(context, 32);
        row.addView(badge, new LinearLayout.LayoutParams(badgeSize, badgeSize));
        ProviderBadge.apply(badge, provider, badgeSize);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(ProviderBadge.dp(context, 14));
        row.addView(texts, textParams);

        TextView name = text(context, account.name, 16, R.color.on_surface);
        texts.addView(name);
        String description = account.name;
        if (!account.name.equals(provider.displayName)) {
            // Second line tells which website this named account belongs to.
            texts.addView(text(context, provider.displayName, 13, R.color.on_surface_variant));
            description = account.name + ", " + provider.displayName;
        }
        row.setContentDescription(isCurrent
                ? description + ", " + context.getString(R.string.current_account) : description);
        return row;
    }

    private static View createManageRow(Context context, View.OnClickListener onClick) {
        LinearLayout row = baseRow(context, onClick);
        // The same gear icon as the settings button, centred in a badge-sized box so the text lines up.
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_settings);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int size = ProviderBadge.dp(context, 32);
        row.addView(icon, new LinearLayout.LayoutParams(size, size));
        TextView label = text(context, context.getString(R.string.manage_accounts), 16, R.color.on_surface);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginStart(ProviderBadge.dp(context, 14));
        row.addView(label, params);
        return row;
    }

    private static LinearLayout baseRow(Context context, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(ProviderBadge.dp(context, 56));
        row.setMinimumWidth(ProviderBadge.dp(context, 240));
        int h = ProviderBadge.dp(context, 12);
        row.setPadding(h, 0, ProviderBadge.dp(context, 16), 0);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(onClick);
        // Standard touch ripple effect from the system theme.
        TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        row.setForeground(context.getDrawable(ripple.resourceId));
        return row;
    }

    private static TextView text(Context context, String value, int sizeSp, int colorRes) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(context.getColor(colorRes));
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }
}
