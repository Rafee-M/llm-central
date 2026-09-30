package org.llmcentral.app.account;

import android.content.Intent;

/**
 * Which account opens when the app is started from outside (PROJECT.md R-AST-1…4): by the
 * assistant gesture, by "Share" / the text-selection action "Ask AI", or from the launcher icon.
 * The user picks them in Settings → "Assistant &amp; sharing" (and "Behaviour" for the start).
 *
 * <p><b>How Android starts the app:</b> every start comes with an <i>intent</i>, a small message
 * with an "action" that says what's wanted: {@code ASSIST} (assistant gesture), {@code SEND}
 * (Share), {@code PROCESS_TEXT} (text selection → "Ask AI"), {@code VIEW} (open a link) or
 * {@code MAIN} (launcher icon). {@link #purposeOf} turns the action into one of our
 * {@link Purpose}s, and {@link #resolve} / {@link #launchAccount} into an account.
 * Docs: https://developer.android.com/guide/components/intents-filters</p>
 *
 * <p><b>Saved as:</b> account ids in the preferences ({@value #PREF_ASSISTANT},
 * {@value #PREF_SHARE}, {@value #PREF_LAUNCH}). If a saved account was removed later, duck.ai is
 * used instead, so a start never fails.</p>
 *
 * <p>Plain Java (the {@code Intent.ACTION_…} names are fixed texts that the compiler copies in),
 * so DefaultAccountsTest checks it on the computer.</p>
 */
public final class DefaultAccounts {

    /** Account opened by the assistant gesture. Default: duck.ai. */
    public static final String PREF_ASSISTANT = "assistant_account";
    /** Account that receives shared text/files and "Ask AI". Default: duck.ai. */
    public static final String PREF_SHARE = "share_account";
    /** Account shown when the app is opened from the launcher. Default: {@link #LAUNCH_LAST}. */
    public static final String PREF_LAUNCH = "launch_account";
    /** Launch choice meaning "the account I used last time" (the behaviour before this setting). */
    public static final String LAUNCH_LAST = "";

    /** Why the app was started. */
    public enum Purpose {
        /** Assistant gesture (long-press home / swipe from a corner). */
        ASSISTANT,
        /** Share sheet or the "Ask AI" text-selection action. */
        SHARE,
        /** Another app opened a duck.ai link: always duck.ai (the link is a duck.ai address). */
        LINK,
        /** Launcher icon (or anything else). */
        LAUNCH
    }

    private DefaultAccounts() {
        // Only static methods.
    }

    /** @return the purpose behind an intent action (null counts as a normal launch). */
    public static Purpose purposeOf(String action) {
        if (Intent.ACTION_ASSIST.equals(action)) {
            return Purpose.ASSISTANT;
        }
        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)
                || Intent.ACTION_PROCESS_TEXT.equals(action)) {
            return Purpose.SHARE;
        }
        if (Intent.ACTION_VIEW.equals(action)) {
            return Purpose.LINK;
        }
        return Purpose.LAUNCH;
    }

    /**
     * @param savedId the account id saved for the assistant or for sharing (may be null)
     * @return that account, or duck.ai if it's missing (never set, or the account was removed)
     */
    public static Account resolve(AccountStore store, String savedId) {
        Account account = savedId == null ? null : store.find(savedId);
        return account != null ? account : store.duck();
    }

    /**
     * The account for an assistant / share / link request.
     *
     * @param assistantId the saved assistant choice ({@link #PREF_ASSISTANT}, may be null)
     * @param shareId     the saved share choice ({@link #PREF_SHARE}, may be null)
     * @return the chosen account (duck.ai if missing); links always go to duck.ai
     */
    public static Account target(AccountStore store, Purpose purpose, String assistantId, String shareId) {
        switch (purpose) {
            case ASSISTANT:
                return resolve(store, assistantId);
            case SHARE:
                return resolve(store, shareId);
            default:
                return store.duck();
        }
    }

    /**
     * The account to show when the app is opened from the launcher.
     *
     * @param launchChoice the saved choice: an account id, or {@link #LAUNCH_LAST}
     * @param lastUsedId   the account on screen when the app was last used (may be null)
     */
    public static Account launchAccount(AccountStore store, String launchChoice, String lastUsedId) {
        if (launchChoice != null && !launchChoice.isEmpty()) {
            Account chosen = store.find(launchChoice);
            if (chosen != null) {
                return chosen;
            }
        }
        return resolve(store, lastUsedId);
    }

    /**
     * Checks a choice coming from the settings page before it's saved.
     *
     * @param allowLast true for the launch setting, where "" ({@link #LAUNCH_LAST}) is a valid choice
     * @return the id to save, or null if it's not acceptable (unknown account)
     */
    public static String validChoice(AccountStore store, String id, boolean allowLast) {
        if (id == null) {
            return null;
        }
        if (id.isEmpty()) {
            return allowLast ? LAUNCH_LAST : null;
        }
        return store.find(id) != null ? id : null;
    }
}
