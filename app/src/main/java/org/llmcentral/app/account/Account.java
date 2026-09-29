package org.llmcentral.app.account;

import org.llmcentral.app.provider.Providers;

/**
 * One configured account, e.g. "ChatGPT · Work" (PROJECT.md R-ACC-2).
 *
 * <p><b>Account vs. provider:</b> a <i>provider</i> is a website (ChatGPT, Claude...; see
 * {@code provider/ProviderSpec}). An <i>account</i> is one login on that website. You can have several
 * accounts for the same provider ("Work" and "Personal"), and each one lives in its own isolated
 * "container" (a WebView profile, see {@code web/Profiles}), so their logins never mix.</p>
 *
 * <p><b>Immutable:</b> the fields never change after creation ({@code final}). To rename an account,
 * {@link AccountStore} replaces it with a copy made by {@link #withName}. Immutable objects are
 * easy to reason about: nobody can change one "behind your back".</p>
 */
public final class Account {

    /** Unique, never reused id. The first account of each provider uses the provider's id (e.g. "chatgpt"). */
    public final String id;
    /** Which website, e.g. "chatgpt" (see {@code Providers}). */
    public final String providerId;
    /** The name the user gave it, e.g. "Work". Shown in the top bar and the switcher menu. */
    public final String name;
    /** Shown in the switcher menu? Hidden accounts stay configured and logged in (R-ACC-4). */
    public final boolean visible;

    public Account(String id, String providerId, String name, boolean visible) {
        this.id = id;
        this.providerId = providerId;
        this.name = name;
        this.visible = visible;
    }

    /** @return true for the duck.ai account (there is exactly one, and it needs no login). */
    public boolean isDuck() {
        return Providers.DUCK_ID.equals(providerId);
    }

    /**
     * The name of this account's WebView profile, its private "container" for cookies and site data
     * (PROJECT.md §5.2), or {@code null} for the WebView's default profile.
     *
     * <p>duck.ai uses the default profile, so the chats and settings it saved before this version
     * are kept. Every other account gets its own profile named {@code "acct_" + id}.</p>
     */
    public String profileName() {
        return isDuck() ? null : "acct_" + id;
    }

    Account withName(String newName) {
        return new Account(id, providerId, newName, visible);
    }

    Account withVisible(boolean newVisible) {
        return new Account(id, providerId, name, newVisible);
    }
}
