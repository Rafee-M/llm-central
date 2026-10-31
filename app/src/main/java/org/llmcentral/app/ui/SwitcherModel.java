package org.llmcentral.app.ui;

import org.llmcentral.app.account.Account;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The rules behind the account menu ({@link SwitcherPopup}): which accounts it lists and which of its
 * buttons can be used right now (PROJECT.md R-SW-2).
 *
 * <p>"Loaded" (or "active") means the account has a live WebView tab in memory
 * ({@code web/TabManager.isLoaded}): switching to it is instant. The setting
 * Settings → Behaviour → "Accounts kept loaded" decides how many stay loaded.</p>
 *
 * <p>Plain Java on purpose (no Android classes), so these rules run in fast JVM unit tests
 * ({@code SwitcherModelTest}), like {@code web/TabLru}. The popup only draws what this class decides.</p>
 */
public final class SwitcherModel {

    /** Text size the "Reset text size" button goes back to (pref {@code text_zoom}; 100 = the site's own size). */
    public static final int DEFAULT_TEXT_ZOOM = 100;

    private SwitcherModel() {
        // Only static methods.
    }

    /**
     * @param accounts   every account, in the user's order (shown and hidden ones; {@code AccountStore.all()})
     * @param isLoaded   answers "does this account id have a loaded tab?"
     * @param activeOnly the menu's "Active accounts" switch
     * @param currentId  the account on screen
     * @return a new list: first the shown accounts in the user's order (every one when {@code activeOnly} is off;
     *         otherwise only loaded ones plus the account on screen, even if it isn't loaded, e.g. it was just
     *         unloaded, so the menu always shows where you are); then, at the bottom, the <b>hidden</b> accounts
     *         that are loaded or on screen (owner, 2026-10-30). Settings → Manage accounts can open a hidden
     *         account, so it must be reachable here while it uses memory: its ✕ unloads it. Unloaded while on
     *         screen, it stays listed (its "Tap to load" screen too) until another account comes on screen.
     *         Never adds an account that isn't in {@code accounts}, and never lists one twice.
     */
    public static List<Account> rows(List<Account> accounts, Predicate<String> isLoaded, boolean activeOnly,
                                     String currentId) {
        List<Account> rows = new ArrayList<>(accounts.size());
        List<Account> hidden = new ArrayList<>();
        for (Account account : accounts) {
            boolean active = account.id.equals(currentId) || isLoaded.test(account.id);
            if (!account.visible) {
                if (active) {
                    hidden.add(account);
                }
            } else if (!activeOnly || active) {
                rows.add(account);
            }
        }
        rows.addAll(hidden);
        return rows;
    }

    /** The ✕ (unload) at the end of an account's row in the menu. */
    public enum UnloadButton {
        /** The account has no loaded tab: nothing to unload, no button. */
        NONE,
        /** Tapping it unloads that account's tab, whether it is on screen or not. duck.ai too since 2026-10-27
         *  (owner; until then its ✕ was greyed out): its page is freed after its offline backup (MainActivity). */
        ENABLED
    }

    /**
     * Which ✕ an account's row gets. It sits where the green "loaded" dot used to be, so a ✕ also shows that
     * the account is loaded; it lets you unload other accounts while you use a different one.
     */
    public static UnloadButton unloadButton(Account account, boolean loaded) {
        return loaded ? UnloadButton.ENABLED : UnloadButton.NONE;
    }

    /**
     * "Reset text size" is shown only while pinching changes the text size. With Settings → Behaviour →
     * "Pinch zooms the page" on, a pinch magnifies the page instead (web/PageZoom), so the button would
     * reset something the pinch no longer sets; it is hidden then (the Text size slider stays in Settings).
     */
    public static boolean showsResetTextSize(boolean pinchZoomsPage) {
        return !pinchZoomsPage;
    }

    /** "Reset text size" only does something when the text size isn't the default already. */
    public static boolean canResetTextSize(int textZoom) {
        return textZoom != DEFAULT_TEXT_ZOOM;
    }
}
