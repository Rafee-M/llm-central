package org.llmcentral.app.account;

import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;
import org.llmcentral.app.provider.ProviderSpec;
import org.llmcentral.app.provider.Providers;
import org.llmcentral.app.theme.Scheme;
import org.llmcentral.app.web.Profiles;

/**
 * What the Settings page's "Accounts" section can ask the app to do (PROJECT.md R-ACC-4): list,
 * add, rename, show/hide in the switcher menu, open, sign out and remove accounts.
 *
 * <p><b>How a web page talks to Java:</b> MainActivity adds this object to the settings WebView with
 * {@code addJavascriptInterface(bridge, "AndroidAccounts")}. The page's JavaScript can then call
 * {@code AndroidAccounts.rename("chatgpt-2", "Work")}; only methods marked
 * {@link JavascriptInterface} are reachable. The settings page is a local file of the app, never a
 * website (see DialogWebViewClient), but every value from it is still checked here.
 * Docs: https://developer.android.com/develop/ui/views/layout/webapps/webview#BindingJavaScript</p>
 *
 * <p><b>Threads:</b> these methods run on a background thread ("JavaBridge"). The account list is
 * thread-safe ({@link AccountStore} is synchronized); anything touching the screen or WebViews is
 * sent to the main thread with {@link Host#runOnUiThread}.</p>
 *
 * <p>Every method that changes something returns the updated list (same JSON as
 * {@link #getAccountsJson()}), so the page can redraw at once. On a refused request it contains an
 * {@code "error"} text to show.</p>
 */
public final class AccountsBridge {

    private static final String TAG = "AccountsBridge";

    /** What the bridge needs from the screen that shows the settings (implemented by MainActivity). */
    public interface Host {
        AccountStore accounts();

        SharedPreferences prefs();

        /** The account currently on screen. */
        Account currentAccount();

        void runOnUiThread(Runnable action);

        /** Main thread: the list changed and was saved → refresh tabs and top bar. */
        void onAccountsChanged();

        /** Main thread: close (destroy) the account's tab, so its data can be wiped. */
        void closeAccountTab(String accountId);

        /** Main thread: close the settings and show this account. */
        void openAccount(Account account);
    }

    private final Host host;

    public AccountsBridge(Host host) {
        this.host = host;
    }

    /**
     * @return {@code {"multiAccount":true, "current":"chatgpt", "providers":[{"id","name","badge",
     *         "color","canAdd","accounts":[{"id","name","visible"}]}]}}
     */
    @JavascriptInterface
    public String getAccountsJson() {
        return json(null);
    }

    /** Adds an account (empty name → "ChatGPT 2" …). Several per provider need WebView profiles. */
    @JavascriptInterface
    public String addAccount(String providerId, String name) {
        AccountStore store = host.accounts();
        ProviderSpec provider = Providers.find(providerId);
        if (provider == null || provider.isDuck()) {
            return json("This provider can't have more accounts.");
        }
        if (!canAdd(store, provider)) {
            return json("Several accounts need a newer \"Android System WebView\". Update it from the Play Store.");
        }
        Account added = store.add(provider.id, name, AccountStore.newId(provider.id));
        if (added == null) {
            return json("Couldn't add the account.");
        }
        saveAndRefresh(store);
        return json(null);
    }

    @JavascriptInterface
    public String renameAccount(String id, String name) {
        AccountStore store = host.accounts();
        if (!store.rename(id, name)) {
            return json("Please enter a name.");
        }
        saveAndRefresh(store);
        return json(null);
    }

    /** Shows or hides an account in the switcher menu; the last visible one can't be hidden (R-ACC-6). */
    @JavascriptInterface
    public String setAccountVisible(String id, boolean visible) {
        AccountStore store = host.accounts();
        if (!store.setVisible(id, visible)) {
            return json("At least one account must stay in the menu.");
        }
        saveAndRefresh(store);
        return json(null);
    }

    /** Closes the settings and switches to the account. */
    @JavascriptInterface
    public void openAccount(String id) {
        Account account = host.accounts().find(id);
        if (account != null) {
            host.runOnUiThread(() -> host.openAccount(account));
        }
    }

    /**
     * Signs out: closes the account's tab and deletes its cookies and site data (its container is
     * emptied, see web/Profiles). The account stays in the list. duck.ai has no login.
     */
    @JavascriptInterface
    public String signOutAccount(String id) {
        Account account = host.accounts().find(id);
        if (account == null || account.isDuck() || !Profiles.isSupported()) {
            return json("This account can't be signed out here.");
        }
        host.runOnUiThread(() -> {
            host.closeAccountTab(account.id);
            Profiles.forget(account, host.prefs());
        });
        return json(null);
    }

    /** Removes the account and deletes all its data (R-SEC-6). duck.ai can't be removed. */
    @JavascriptInterface
    public String removeAccount(String id) {
        AccountStore store = host.accounts();
        Account removed = store.remove(id);
        if (removed == null) {
            return json("This account can't be removed.");
        }
        store.save(host.prefs());
        host.runOnUiThread(() -> {
            host.closeAccountTab(removed.id);
            Profiles.forget(removed, host.prefs());
            host.onAccountsChanged();
        });
        return json(null);
    }

    private void saveAndRefresh(AccountStore store) {
        store.save(host.prefs());
        host.runOnUiThread(host::onAccountsChanged);
    }

    /** One account per provider without profiles (they would share the same cookies). */
    private static boolean canAdd(AccountStore store, ProviderSpec provider) {
        return !provider.isDuck() && (Profiles.isSupported() || store.ofProvider(provider.id).isEmpty());
    }

    private String json(String error) {
        AccountStore store = host.accounts();
        try {
            JSONObject root = new JSONObject();
            root.put("multiAccount", Profiles.isSupported());
            Account current = host.currentAccount();
            root.put("current", current != null ? current.id : Providers.DUCK_ID);
            JSONArray providers = new JSONArray();
            for (ProviderSpec provider : Providers.ALL) {
                JSONArray list = new JSONArray();
                for (Account a : store.ofProvider(provider.id)) {
                    list.put(new JSONObject().put("id", a.id).put("name", a.name).put("visible", a.visible));
                }
                providers.put(new JSONObject()
                        .put("id", provider.id)
                        .put("name", provider.displayName)
                        .put("badge", provider.badgeText)
                        .put("color", Scheme.hex(provider.badgeColor))
                        .put("canAdd", canAdd(store, provider))
                        .put("accounts", list));
            }
            root.put("providers", providers);
            if (error != null) {
                root.put("error", error);
            }
            return root.toString();
        } catch (Exception e) {
            Log.e(TAG, "Couldn't build the accounts JSON", e);
            return "{\"providers\":[]}";
        }
    }
}
