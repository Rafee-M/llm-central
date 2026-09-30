package org.llmcentral.app.account;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.llmcentral.app.provider.ProviderSpec;
import org.llmcentral.app.provider.Providers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The list of configured accounts and the rules for changing it (PROJECT.md §3.2, R-ACC-1…6).
 *
 * <p><b>Where it's saved:</b> as one small JSON text in the app's preferences
 * ({@link SharedPreferences}, key {@value #PREF_KEY}). Preferences are Android's simple key/value
 * storage (docs: https://developer.android.com/training/data-storage/shared-preferences). The JSON
 * looks like:
 * <pre>{"v":1,"accounts":[{"id":"duck","provider":"duck","name":"duck.ai","visible":true}, …]}</pre>
 * The {@code org.json} classes used to read and write it are built into Android, so they cost no APK
 * size (PROJECT.md R-SIZE-2).</p>
 *
 * <p><b>Rules enforced here</b> (so every screen that changes accounts follows them):</p>
 * <ul>
 *   <li>There is always exactly one duck.ai account; it can't be removed and no second one can be
 *       added (duck.ai needs no login, R-PROV-4).</li>
 *   <li>At least one account stays visible in the switcher menu (R-ACC-6).</li>
 *   <li>Names are cleaned: no control characters, no extra spaces, at most {@value #MAX_NAME_LENGTH}
 *       characters, never empty.</li>
 *   <li>Accounts of the same provider stay next to each other (the menu groups them); inside a
 *       provider the user can change their order ({@link #move}).</li>
 * </ul>
 *
 * <p><b>Threads:</b> the settings page calls into Java on a background thread while the screen uses
 * the main thread, so every method is {@code synchronized} (only one thread at a time inside).</p>
 *
 * <p>Plain Java apart from {@link #load}/{@link #save}, so AccountStoreTest checks it on the computer.</p>
 */
public final class AccountStore {

    /** Preference key holding the JSON. */
    public static final String PREF_KEY = "accounts";
    /** Longest allowed account name (in characters, emoji count as one). */
    public static final int MAX_NAME_LENGTH = 40;

    private static final int FORMAT_VERSION = 1;

    private final List<Account> accounts = new ArrayList<>();

    private AccountStore() {
    }

    // ---------------------------------------------------------------- loading and saving

    /** Reads the accounts from preferences (first start: one account per provider). */
    public static AccountStore load(SharedPreferences prefs) {
        return fromJson(prefs.getString(PREF_KEY, null));
    }

    /** Writes the accounts to preferences. {@code apply()} saves in the background (doesn't block the screen). */
    public synchronized void save(SharedPreferences prefs) {
        prefs.edit().putString(PREF_KEY, toJson()).apply();
    }

    /**
     * Builds a store from saved JSON. {@code null} or broken JSON → the first-start defaults: one
     * account per provider, named like the provider, all visible (the same set of tabs as before
     * accounts existed). The first account of each provider gets the provider's id as its id, so the
     * old "last opened provider" setting still points at the right account.
     */
    public static AccountStore fromJson(String json) {
        AccountStore store = new AccountStore();
        boolean parsed = false;
        if (json != null) {
            try {
                JSONArray list = new JSONObject(json).getJSONArray("accounts");
                Set<String> seenIds = new HashSet<>();
                for (int i = 0; i < list.length(); i++) {
                    JSONObject o = list.optJSONObject(i);
                    if (o == null) {
                        continue;
                    }
                    String id = o.optString("id", "");
                    ProviderSpec provider = Providers.find(o.optString("provider", ""));
                    // Skip anything we can't use: no id, duplicate id, unknown provider, second duck.ai.
                    if (id.isEmpty() || provider == null || !seenIds.add(id)
                            || (provider.isDuck() && store.findLocked(Providers.DUCK_ID) != null)
                            || (!provider.isDuck() && id.equals(Providers.DUCK_ID))) {
                        continue;
                    }
                    if (provider.isDuck()) {
                        id = Providers.DUCK_ID; // the duck.ai account always has this id
                    }
                    String name = cleanName(o.optString("name", ""));
                    store.accounts.add(new Account(id, provider.id,
                            name.isEmpty() ? provider.displayName : name, o.optBoolean("visible", true)));
                }
                parsed = true;
            } catch (Exception e) {
                // Broken JSON (should never happen): start again from the defaults below.
                store.accounts.clear();
            }
        }
        if (!parsed) {
            for (ProviderSpec provider : Providers.ALL) {
                store.accounts.add(new Account(provider.id, provider.id, provider.displayName, true));
            }
        }
        store.repairLocked();
        return store;
    }

    /** @return the accounts as JSON text (the format described in the class comment). */
    public synchronized String toJson() {
        try {
            JSONArray list = new JSONArray();
            for (Account a : accounts) {
                list.put(new JSONObject()
                        .put("id", a.id)
                        .put("provider", a.providerId)
                        .put("name", a.name)
                        .put("visible", a.visible));
            }
            return new JSONObject().put("v", FORMAT_VERSION).put("accounts", list).toString();
        } catch (Exception e) {
            // org.json only throws for invalid numbers (NaN); we only store text and booleans.
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- reading

    /** @return all accounts in menu order (a copy, so callers can't change the store by accident). */
    public synchronized List<Account> all() {
        return Collections.unmodifiableList(new ArrayList<>(accounts));
    }

    /** @return the accounts shown in the switcher menu, in order. Never empty (R-ACC-6). */
    public synchronized List<Account> visible() {
        List<Account> result = new ArrayList<>();
        for (Account a : accounts) {
            if (a.visible) {
                result.add(a);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** @return the accounts of one provider, in order (may be empty). */
    public synchronized List<Account> ofProvider(String providerId) {
        List<Account> result = new ArrayList<>();
        for (Account a : accounts) {
            if (a.providerId.equals(providerId)) {
                result.add(a);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** @return the account with this id, or {@code null}. */
    public synchronized Account find(String id) {
        return findLocked(id);
    }

    /** @return the duck.ai account (always exists). */
    public synchronized Account duck() {
        return findLocked(Providers.DUCK_ID);
    }

    // ---------------------------------------------------------------- changing

    /**
     * Adds an account for a provider (R-ACC-1). It's placed after that provider's other accounts.
     *
     * @param name  the user's name for it; empty → "ChatGPT", "ChatGPT 2", … (first free one)
     * @param newId a new unique id (use {@link #newId}); tests pass fixed ids
     * @return the new account, or {@code null} for duck.ai (only one allowed) or an unknown provider
     */
    public synchronized Account add(String providerId, String name, String newId) {
        ProviderSpec provider = Providers.find(providerId);
        if (provider == null || provider.isDuck() || newId == null || newId.isEmpty()
                || findLocked(newId) != null) {
            return null;
        }
        String clean = cleanName(name);
        Account added = new Account(newId, provider.id,
                clean.isEmpty() ? defaultNameLocked(provider) : clean, true);
        // Insert right after the provider's last account; if it has none, where the provider's
        // position in Providers.ALL says (keeps the menu grouped and in a predictable order).
        accounts.add(insertIndexLocked(provider), added);
        return added;
    }

    /** @return false if there's no such account or the cleaned name is empty. */
    public synchronized boolean rename(String id, String name) {
        int index = indexOfLocked(id);
        String clean = cleanName(name);
        if (index < 0 || clean.isEmpty()) {
            return false;
        }
        accounts.set(index, accounts.get(index).withName(clean));
        return true;
    }

    /**
     * Shows or hides an account in the switcher menu (R-ACC-4).
     *
     * @return false if there's no such account, or if hiding it would leave no visible account (R-ACC-6)
     */
    public synchronized boolean setVisible(String id, boolean visible) {
        int index = indexOfLocked(id);
        if (index < 0) {
            return false;
        }
        Account account = accounts.get(index);
        if (!visible && account.visible && countVisibleLocked() <= 1) {
            return false;
        }
        accounts.set(index, account.withVisible(visible));
        return true;
    }

    /**
     * Removes an account from the list. (Deleting its cookies and site data is done by the caller,
     * see {@code web/Profiles}; this class only manages the list.)
     *
     * @return the removed account, or {@code null} if it doesn't exist or is the duck.ai account
     */
    public synchronized Account remove(String id) {
        int index = indexOfLocked(id);
        if (index < 0 || accounts.get(index).isDuck()) {
            return null;
        }
        Account removed = accounts.remove(index);
        repairLocked(); // e.g. it was the last visible account → duck.ai becomes visible
        return removed;
    }

    /**
     * Moves an account one place up ({@code -1}) or down ({@code +1}) in the list, which is also the
     * order of the switcher menu (R-ACC-4). It only swaps with a neighbour of the <b>same provider</b>,
     * so a provider's accounts always stay together.
     *
     * @return false if there's no such account, or it's already first/last of its provider
     */
    public synchronized boolean move(String id, int delta) {
        int index = indexOfLocked(id);
        if (index < 0 || (delta != -1 && delta != 1)) {
            return false;
        }
        int other = index + delta;
        if (other < 0 || other >= accounts.size()
                || !accounts.get(other).providerId.equals(accounts.get(index).providerId)) {
            return false;
        }
        Collections.swap(accounts, index, other);
        return true;
    }

    // ---------------------------------------------------------------- helpers

    /** @return a new random account id like "chatgpt-3f9a1c2e" (never reused, so profiles never mix). */
    public static String newId(String providerId) {
        return providerId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Cleans a user-typed name: control characters (tabs, line breaks…) become spaces, repeated spaces
     * shrink to one, leading/trailing spaces go, and it's cut to {@value #MAX_NAME_LENGTH} characters.
     * Counting "code points" instead of Java chars means an emoji (2 chars in Java) counts as one and
     * is never cut in half.
     */
    public static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean lastWasSpace = true; // true = drop spaces at the start
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            boolean space = Character.isWhitespace(cp) || Character.isISOControl(cp)
                    || Character.isSpaceChar(cp);
            if (space) {
                if (!lastWasSpace) {
                    sb.append(' ');
                }
                lastWasSpace = true;
            } else {
                sb.appendCodePoint(cp);
                lastWasSpace = false;
            }
        }
        String s = sb.toString().trim();
        if (s.codePointCount(0, s.length()) > MAX_NAME_LENGTH) {
            s = s.substring(0, s.offsetByCodePoints(0, MAX_NAME_LENGTH)).trim();
        }
        return s;
    }

    /** Makes sure the rules hold after loading or removing: duck.ai exists, ≥1 account visible. */
    private void repairLocked() {
        if (findLocked(Providers.DUCK_ID) == null) {
            accounts.add(0, new Account(Providers.DUCK_ID, Providers.DUCK_ID, Providers.DUCK.displayName, true));
        }
        if (countVisibleLocked() == 0) {
            int duck = indexOfLocked(Providers.DUCK_ID);
            accounts.set(duck, accounts.get(duck).withVisible(true));
        }
    }

    private String defaultNameLocked(ProviderSpec provider) {
        Set<String> used = new HashSet<>();
        for (Account a : accounts) {
            used.add(a.name);
        }
        if (!used.contains(provider.displayName)) {
            return provider.displayName;
        }
        for (int n = 2; ; n++) {
            String candidate = provider.displayName + " " + n;
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
    }

    private int insertIndexLocked(ProviderSpec provider) {
        int lastOfProvider = -1;
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).providerId.equals(provider.id)) {
                lastOfProvider = i;
            }
        }
        if (lastOfProvider >= 0) {
            return lastOfProvider + 1;
        }
        // No account of this provider yet: before the first account of a provider listed later.
        int order = Providers.ALL.indexOf(provider);
        for (int i = 0; i < accounts.size(); i++) {
            if (Providers.ALL.indexOf(Providers.find(accounts.get(i).providerId)) > order) {
                return i;
            }
        }
        return accounts.size();
    }

    private int countVisibleLocked() {
        int count = 0;
        for (Account a : accounts) {
            if (a.visible) {
                count++;
            }
        }
        return count;
    }

    private Account findLocked(String id) {
        int index = indexOfLocked(id);
        return index < 0 ? null : accounts.get(index);
    }

    private int indexOfLocked(String id) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
