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
     * @param visible    the accounts the menu may show, in the user's order (hidden accounts already removed)
     * @param isLoaded   answers "does this account id have a loaded tab?"
     * @param activeOnly the menu's "Active accounts" switch
     * @param currentId  the account on screen
     * @return a new list in the order of {@code visible}: every account when {@code activeOnly} is off;
     *         otherwise only loaded accounts, plus the account on screen even if it isn't loaded (e.g. it
     *         was just unloaded), so the menu always shows where you are. Never adds an account that
     *         isn't in {@code visible}, and never lists one twice.
     */
    public static List<Account> rows(List<Account> visible, Predicate<String> isLoaded, boolean activeOnly,
                                     String currentId) {
        List<Account> rows = new ArrayList<>(visible.size());
        for (Account account : visible) {
            if (!activeOnly || account.id.equals(currentId) || isLoaded.test(account.id)) {
                rows.add(account);
            }
        }
        return rows;
    }

    /**
     * "Unload tab" is allowed for a loaded tab of any account except duck.ai: duck.ai's tab is the app's
     * pinned "home" tab (assistant, offline chats, share target) and is never closed (web/TabManager).
     */
    public static boolean canUnload(Account current, boolean currentLoaded) {
        return currentLoaded && !current.isDuck();
    }

    /** "Reset text size" only does something when the text size isn't the default already. */
    public static boolean canResetTextSize(int textZoom) {
        return textZoom != DEFAULT_TEXT_ZOOM;
    }
}
