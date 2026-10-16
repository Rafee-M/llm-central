package org.llmcentral.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import org.llmcentral.app.R;
import org.llmcentral.app.account.Account;
import org.llmcentral.app.provider.ProviderSpec;
import org.llmcentral.app.provider.Providers;
import org.llmcentral.app.theme.Scheme;

import java.util.List;

/**
 * The drop-down list that opens when the chip in the top bar is tapped (PROJECT.md R-SW-2):
 * <ol>
 *   <li><b>Accounts:</b> one row per visible account (provider badge + name, the provider underneath when
 *       it differs, e.g. "Work / ChatGPT"); the current account is highlighted. The list scrolls
 *       when it's long, so the controls below always stay on screen.</li>
 *   <li><b>Icon row</b> for the account on screen: Desktop site, Reload page, Reset text size (hidden while
 *       "Pinch zooms the page" is on), Unload tab (frees the tab's memory; duck.ai's pinned tab can't be
 *       unloaded), Clear cache (the same as in Settings; logins stay).</li>
 *   <li><b>Active accounts</b> switch: lists only the loaded accounts ({@link SwitcherModel#rows}).
 *       Flipping it redraws the list right away; the menu stays open.</li>
 * </ol>
 *
 * <p>Built with plain Android views ({@link PopupWindow} + {@link LinearLayout}), without any UI library,
 * to keep the app small (decision D1). Colours come from the current theme ({@link Scheme}), so the
 * menu follows the palette, Material You and dark mode. The decisions (which rows, which buttons are
 * usable) live in {@link SwitcherModel}, which is unit-tested; this class only draws.</p>
 */
public final class SwitcherPopup {

    /** What the menu needs from the app (implemented by MainActivity). */
    public interface Host {
        /** The user picked an account in the list. */
        void pick(Account account);

        /** Does this account have a loaded tab (web/TabManager.isLoaded)? */
        boolean isLoaded(String accountId);

        boolean isDesktopOn();

        void toggleDesktop();

        void reload();

        /** Settings → Behaviour → "Pinch zooms the page" (pref {@code pinch_zoom_page}). */
        boolean pinchZoomsPage();

        /** The current text size in percent (pref {@code text_zoom}). */
        int textZoom();

        void resetTextSize();

        /** Unloads that account's tab (the ✕ on its row; see {@link SwitcherModel#unloadButton}). */
        void unload(String accountId);

        /** The same as Settings → Data &amp; tools → Clear cache (storage/StorageBridge.clearAll); logins stay. */
        void clearCache();

        /** The "Active accounts" switch (pref {@code switcher_active_only}). */
        boolean activeOnly();

        void setActiveOnly(boolean on);
    }

    /** Material 3's "disabled" look: content at 38 % opacity. */
    private static final float DISABLED_ALPHA = 0.38f;

    private SwitcherPopup() {
        // Only static methods.
    }

    /**
     * Shows the menu just below {@code anchor} (the chip).
     *
     * @param accounts the visible accounts, in order
     * @param current  the account on screen (highlighted, and the one the icon row acts on)
     */
    public static void show(View anchor, List<Account> accounts, Account current, Scheme scheme, Host host) {
        Context context = anchor.getContext();
        int pad = ProviderBadge.dp(context, 8);

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad, pad, pad, pad);
        // Rounded "card" background, like a Material 3 menu.
        GradientDrawable background = new GradientDrawable();
        background.setColor(scheme.get(Scheme.SURFACE_CONTAINER));
        background.setCornerRadius(ProviderBadge.dp(context, 20));
        list.setBackground(background);

        // A PopupWindow is a small floating window; "focusable" means a tap outside or Back closes it.
        PopupWindow popup = new PopupWindow(list, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(ProviderBadge.dp(context, 6)); // soft shadow
        popup.setOutsideTouchable(true);

        // 1) The accounts, in a list that scrolls once it would take more than half the screen.
        int maxListHeight = context.getResources().getDisplayMetrics().heightPixels / 2;
        ScrollView scroller = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                // At most half the screen, and never more than the menu window allows (e.g. landscape).
                int limit = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED ? maxListHeight
                        : Math.min(maxListHeight, MeasureSpec.getSize(heightMeasureSpec));
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
            }
        };
        // Set further down (they need views made later); the rows' ✕ calls them after an unload.
        Runnable[] refreshAfterUnload = new Runnable[1];
        Runnable[] showCount = new Runnable[1];
        LinearLayout accountsBox = new LinearLayout(context);
        accountsBox.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(accountsBox);
        list.addView(scroller);
        Runnable fillAccounts = () -> {
            accountsBox.removeAllViews(); // never add rows twice (the switch can be flipped many times)
            for (Account account : SwitcherModel.rows(accounts, host::isLoaded, host.activeOnly(), current.id)) {
                accountsBox.addView(createAccountRow(context, scheme, account, account.id.equals(current.id),
                        host.isLoaded(account.id), v -> {
                            popup.dismiss();
                            host.pick(account);
                        }, () -> {
                            // ✕ on the row: unload that account; the menu stays open and is redrawn, so several
                            // accounts can be unloaded in a row. The window wraps its content (it resizes by
                            // itself); update() re-places it at the chip.
                            host.unload(account.id);
                            refreshAfterUnload[0].run();
                        }));
            }
        };
        fillAccounts.run();

        // 2) Icon row for the account on screen.
        list.addView(divider(context, scheme, pad));
        LinearLayout icons = new LinearLayout(context);
        icons.setOrientation(LinearLayout.HORIZONTAL);
        ImageButton desktop = iconButton(context, scheme, R.drawable.ic_desktop, R.string.desktop_site, true,
                popup, host::toggleDesktop);
        NativeTheme.styleDesktopButton(desktop, host.isDesktopOn(), scheme); // outlined = off, filled = on
        addEvenly(icons, desktop);
        addEvenly(icons, iconButton(context, scheme, R.drawable.ic_refresh, R.string.reload_page, true,
                popup, host::reload));
        if (SwitcherModel.showsResetTextSize(host.pinchZoomsPage())) {
            addEvenly(icons, iconButton(context, scheme, R.drawable.ic_format_size, R.string.reset_text_size,
                    SwitcherModel.canResetTextSize(host.textZoom()), popup, host::resetTextSize));
        }
        addEvenly(icons, iconButton(context, scheme, R.drawable.ic_close, R.string.unload_tab,
                SwitcherModel.unloadButton(current, host.isLoaded(current.id)) == SwitcherModel.UnloadButton.ENABLED,
                popup, () -> host.unload(current.id)));
        addEvenly(icons, iconButton(context, scheme, R.drawable.ic_delete_sweep, R.string.clear_cache,
                true, popup, host::clearCache));
        list.addView(icons);

        // 3) "Active accounts": a row with a small Material 3 switch; the menu stays open.
        list.addView(divider(context, scheme, pad));
        LinearLayout activeRow = baseRow(context, null);
        ImageView filterIcon = rowIcon(context, scheme, R.drawable.ic_filter_list);
        activeRow.addView(filterIcon, new LinearLayout.LayoutParams(ProviderBadge.dp(context, 32),
                ProviderBadge.dp(context, 32)));
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(ProviderBadge.dp(context, 14));
        activeRow.addView(texts, textParams);
        texts.addView(text(context, context.getString(R.string.active_accounts), 16, scheme.get(Scheme.ON_SURFACE)));
        TextView activeSub = text(context, "", 13, scheme.get(Scheme.ON_SURFACE_VARIANT));
        texts.addView(activeSub);
        M3Switch toggle = new M3Switch(context);
        toggle.setScheme(scheme);
        toggle.setChecked(host.activeOnly(), false);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toggleParams.setMarginStart(ProviderBadge.dp(context, 12));
        activeRow.addView(toggle, toggleParams);
        M3Switch.describeRowAsSwitch(activeRow, toggle);
        refreshAfterUnload[0] = () -> {
            fillAccounts.run();
            showCount[0].run();
            popup.update(anchor, 0, ProviderBadge.dp(context, 4), -1, -1);
        };
        showCount[0] = () -> activeSub.setText(host.activeOnly()
                ? context.getString(R.string.active_accounts_count, accountsBox.getChildCount(), accounts.size())
                : context.getString(R.string.active_accounts_off));
        showCount[0].run();
        activeRow.setOnClickListener(v -> {
            boolean on = !host.activeOnly();
            host.setActiveOnly(on);
            toggle.setChecked(on, true);
            fillAccounts.run();
            showCount[0].run();
            v.setContentDescription(context.getString(R.string.active_accounts) + ", " + activeSub.getText());
            // The window wraps its content, so it resizes by itself; update() re-places it at the chip.
            popup.update(anchor, 0, ProviderBadge.dp(context, 4), -1, -1);
        });
        activeRow.setContentDescription(context.getString(R.string.active_accounts) + ", " + activeSub.getText());
        list.addView(activeRow);

        popup.showAsDropDown(anchor, 0, ProviderBadge.dp(context, 4), Gravity.START);
    }

    /**
     * One row: [badge] Name (provider) [✕] — at least 56dp tall so it's easy to tap. The ✕ (only on loaded
     * accounts, greyed out for duck.ai; {@link SwitcherModel#unloadButton}) is its own 48dp button: tapping it
     * unloads the account, tapping anywhere else on the row opens it.
     */
    private static View createAccountRow(Context context, Scheme scheme, Account account, boolean isCurrent,
                                         boolean loaded, View.OnClickListener onClick, Runnable onUnload) {
        ProviderSpec provider = Providers.byId(account.providerId);
        LinearLayout row = baseRow(context, onClick);
        if (isCurrent) {
            // Material 3 marks the selected menu item with a tinted, rounded background.
            GradientDrawable selected = new GradientDrawable();
            selected.setColor(scheme.get(Scheme.SECONDARY_CONTAINER));
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

        int nameColor = scheme.get(isCurrent ? Scheme.ON_SECONDARY_CONTAINER : Scheme.ON_SURFACE);
        texts.addView(text(context, account.name, 16, nameColor));
        String description = account.name;
        if (!account.name.equals(provider.displayName)) {
            // Second line tells which website this named account belongs to.
            texts.addView(text(context, provider.displayName, 13, scheme.get(Scheme.ON_SURFACE_VARIANT)));
            description = account.name + ", " + provider.displayName;
        }
        if (loaded) {
            // Screen readers hear that this account's tab is loaded (switching to it is instant).
            description = description + ", " + context.getString(R.string.account_loaded);
        }
        SwitcherModel.UnloadButton unload = SwitcherModel.unloadButton(account, loaded);
        if (unload != SwitcherModel.UnloadButton.NONE) {
            ImageButton close = new ImageButton(context);
            close.setImageResource(R.drawable.ic_close);
            close.setImageTintList(ColorStateList.valueOf(scheme.get(isCurrent
                    ? Scheme.ON_SECONDARY_CONTAINER : Scheme.ON_SURFACE_VARIANT)));
            close.setScaleType(ImageView.ScaleType.CENTER);
            TypedValue ripple = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
            close.setBackgroundResource(ripple.resourceId);
            String label = context.getString(R.string.unload_account, account.name);
            close.setContentDescription(label);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                close.setTooltipText(label);
            }
            boolean enabled = unload == SwitcherModel.UnloadButton.ENABLED;
            close.setEnabled(enabled);
            close.setAlpha(enabled ? 1f : DISABLED_ALPHA);
            close.setOnClickListener(v -> onUnload.run());
            int size = ProviderBadge.dp(context, 48);
            LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(size, size);
            closeParams.setMarginStart(ProviderBadge.dp(context, 4));
            closeParams.setMarginEnd(-ProviderBadge.dp(context, 12)); // sits in the row's end padding
            row.addView(close, closeParams);
        }
        row.setContentDescription(isCurrent
                ? description + ", " + context.getString(R.string.current_account) : description);
        return row;
    }

    /**
     * A 48 × 48dp icon button (Android's minimum touch size) with a 24dp icon, the size the menu's rows
     * used. Disabled buttons are drawn at 38 % opacity and ignore taps. A long press shows the name.
     */
    private static ImageButton iconButton(Context context, Scheme scheme, int iconRes, int labelRes,
                                          boolean enabled, PopupWindow popup, Runnable action) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(iconRes);
        button.setImageTintList(ColorStateList.valueOf(scheme.get(Scheme.ON_SURFACE_VARIANT)));
        button.setScaleType(ImageView.ScaleType.CENTER);
        TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        button.setBackgroundResource(ripple.resourceId);
        String label = context.getString(labelRes);
        button.setContentDescription(label);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            button.setTooltipText(label);
        }
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : DISABLED_ALPHA);
        button.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        return button;
    }

    /** Puts a 48dp button in the middle of an equal-width cell, so the buttons spread evenly. */
    private static void addEvenly(LinearLayout row, View button) {
        FrameLayout cell = new FrameLayout(row.getContext());
        int size = ProviderBadge.dp(row.getContext(), 48);
        cell.addView(button, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        row.addView(cell, new LinearLayout.LayoutParams(0, size, 1f));
    }

    /** A 24dp icon centred in a badge-sized (32dp) box, so the text lines up with the account names. */
    private static ImageView rowIcon(Context context, Scheme scheme, int iconRes) {
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(scheme.get(Scheme.ON_SURFACE_VARIANT)));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return icon;
    }

    /** A thin line between the menu's parts. */
    private static View divider(Context context, Scheme scheme, int margin) {
        View divider = new View(context);
        divider.setBackgroundColor(scheme.get(Scheme.OUTLINE_VARIANT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, ProviderBadge.dp(context, 1)));
        params.setMargins(margin, margin / 2, margin, margin / 2);
        divider.setLayoutParams(params);
        return divider;
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
        if (onClick != null) {
            row.setOnClickListener(onClick);
        }
        // Standard touch ripple effect from the system theme.
        TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        row.setForeground(context.getDrawable(ripple.resourceId));
        return row;
    }

    private static TextView text(Context context, String value, int sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }
}
