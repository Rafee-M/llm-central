package org.llmcentral.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.TextView;
import android.app.AlertDialog;
import android.content.DialogInterface;
import androidx.core.content.FileProvider;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import java.io.File;
import android.Manifest;
import android.graphics.Color;
import android.content.pm.PackageManager;
import android.webkit.PermissionRequest;

import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.content.SharedPreferences;
import android.os.Looper;



import org.llmcentral.app.scripts.UserScriptMatcher;
import org.llmcentral.app.downloads.Downloader;
import org.llmcentral.app.storage.AppCache;
import org.llmcentral.app.storage.BackupImages;
import org.llmcentral.app.storage.OfflineChats;
import org.llmcentral.app.storage.StorageBridge;
import org.llmcentral.app.storage.UploadCopies;
import org.llmcentral.app.web.DesktopMode;
import org.llmcentral.app.web.MicrophoneAccess;
import org.llmcentral.app.web.DialogWebViewClient;
import org.llmcentral.app.web.AppBridge;
import org.llmcentral.app.web.NavigationPolicy;
import org.llmcentral.app.web.PageZoom;
import org.llmcentral.app.web.Scripts;
import org.llmcentral.app.web.TabLru;
import org.llmcentral.app.web.TabManager;
import org.llmcentral.app.account.Account;
import org.llmcentral.app.account.AccountStore;
import org.llmcentral.app.provider.PromptPrefill;
import org.llmcentral.app.provider.ProviderSpec;
import org.llmcentral.app.provider.Providers;
import org.llmcentral.app.ui.AboutWindow;
import org.llmcentral.app.ui.ProviderBadge;
import org.llmcentral.app.ui.SwitcherPopup;
import org.llmcentral.app.account.AccountsBridge;
import org.llmcentral.app.account.DefaultAccounts;
import org.llmcentral.app.theme.AppearanceBridge;
import org.llmcentral.app.theme.Scheme;
import org.llmcentral.app.theme.ThemeBridge;
import org.llmcentral.app.theme.ThemeManager;
import org.llmcentral.app.ui.NativeTheme;
import org.llmcentral.app.web.PopupWindows;
import org.llmcentral.app.web.Profiles;
import org.llmcentral.app.web.PromptFiller;
import org.llmcentral.app.web.WebViewRecovery;


public class MainActivity extends Activity {

    private WebView chatWebView;
    private float currentZoomLevel = 100f;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> mUploadMessage;
    private final static int FILE_CHOOSER_REQUEST_CODE = 1;
    private final static int CAMERA_REQUEST_CODE = 2;
    private final static int CAMERA_PERMISSION_REQUEST_CODE = 124;
    private Uri cameraImageUri = null;
    private final String TAG = "LLMCentral";
    private final boolean restricted = false;
    private boolean pendingVoiceChat = false;
    private boolean pendingAutoFocus = false;
    private boolean pendingContinueLastChat = false;
    private Uri pendingSharedFileUri = null;
    private java.util.List<Uri> pendingSharedFileUris = null;
    private static boolean isSafeMode = false;
    // One WebView per account ("tab"); chatWebView above is always the duck.ai tab. See web/TabManager.
    private TabManager tabManager;
    // Types shared prompts into ChatGPT/Claude/DeepSeek/Z.ai's message box (web/PromptFiller).
    private PromptFiller promptFiller;
    // The user's accounts (names, which ones show in the menu). See account/AccountStore.
    // "volatile": the Settings page's account actions (account/AccountsBridge) run on WebView's bridge
    // thread and read this field, while onResume may replace it on the main thread; volatile makes
    // that thread see the new object. AccountStore's own methods are already synchronized.
    private volatile AccountStore accounts;
    // Account shown last time, reopened on a normal launch. Round 3 saved a provider id under
    // "last_provider"; first accounts use their provider's id, so that old value still works.
    private static final String PREF_LAST_ACCOUNT = "last_account";
    private static final String PREF_LAST_PROVIDER = "last_provider";
    // Light or dark when this screen was built. If the theme setting flips it, the screen is rebuilt
    // (recreate()) so websites and system dialogs switch too. See theme/ThemeManager.
    private boolean appliedNight;
    private static final String PREF_MAX_LIVE_TABS = "max_live_tabs";
    // Android's original "uncaught exception" handler (the one that closes a crashed app). See onCreate.
    private static Thread.UncaughtExceptionHandler systemCrashHandler;
    private boolean isImageZoomActive = false;
    private boolean hasInjectedProgressJs = false;

    /**
     * Returns one of the JavaScript snippets injected into the chat page.
     * They live in app/src/main/assets/js/ (see web/Scripts.java); until Phase 0.4 they were
     * long Java strings in this file.
     */
    private String js(String name) {
        return Scripts.get(this, name);
    }

    private void safeEvaluateJavascript(WebView view, String script) {
        if (isSafeMode || view == null || script == null) return;
        try {
            view.evaluateJavascript(script, null);
        } catch (Throwable t) {
            Log.e(TAG, "Error executing custom tweak script, enabling Safe Mode fallback", t);
            isSafeMode = true;
            
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Script crash caught! Activating Safe Mode fallback...", Toast.LENGTH_LONG).show();
                if (chatWebView != null) {
                    chatWebView.loadUrl("https://duck.ai/");
                }
            });
        }
    }

    // One background thread for the app's slower work (saving files, offline chats, cache clean-up),
    // so the screen never waits for it. "static" = one thread for the whole app, shared by both
    // screens and kept while the app runs, so work handed over just before a screen closes (e.g. the
    // offline chat save) still finishes. Until round 6 every offline-chat save and start created a
    // new thread pool that was never shut down: one leaked thread each time.
    private static final java.util.concurrent.ExecutorService background =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    // Set when duck.ai was on screen since its chats were last saved for offline use (onStop).
    private boolean duckUsedSinceDump = false;
    // Saves files from the websites into Downloads (downloads/Downloader).
    private Downloader downloader;
    // Microphone for voice chat: asks Android only when a page wants it (web/MicrophoneAccess).
    private final MicrophoneAccess microphone = new MicrophoneAccess(this);
    // (Until 2026-10-15 a field here kept the whole saved-chats copy in memory for as long as the screen
    // lived, although only the saved-chats window reads it; that window now reads the file when it opens.)
    private android.app.Dialog chatsViewerDialog = null;
    // true while duck.ai failed to load and the saved-chats window stands in for it (showOfflineChats).
    private boolean duckOffline = false;
    private android.app.Dialog scriptsManagerDialog = null;
    private boolean isRequestingViewer = false;

    /** Paths of shared files still waiting for the page's 📎 button (not to be deleted as "cache"). */
    private java.util.Set<String> sharedFilesInUse() {
        java.util.Set<String> paths = new java.util.HashSet<>();
        java.util.List<Uri> uris = new java.util.ArrayList<>();
        if (pendingSharedFileUris != null) {
            uris.addAll(pendingSharedFileUris);
        }
        if (pendingSharedFileUri != null) {
            uris.add(pendingSharedFileUri);
        }
        for (Uri uri : uris) {
            if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
                paths.add(new File(uri.getPath()).getAbsolutePath());
            }
        }
        return paths;
    }

    protected void setActivityTheme() {
        // Framework light/dark theme matching the app's Theme setting (system dialogs follow it).
        setTheme(ThemeManager.activityTheme(this));
    }

    /**
     * Runs before anything else in the activity, even before onCreate. If the user chose "Light" or
     * "Dark" instead of "System default", this makes the whole screen use that mode whatever the phone
     * is set to (and, on Android 13+, should make websites that support dark mode follow). See
     * ThemeManager.nightOverride.
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        android.content.res.Configuration override = ThemeManager.nightOverride(newBase);
        if (override != null) {
            applyOverrideConfiguration(override);
        }
    }

    /** @return true when this screen is the assistant's bottom drawer (ChatActivity overrides it). */
    protected boolean isDrawerLayout() {
        return false;
    }

    /**
     * Paints the app's native parts with the current theme (PROJECT.md §4): window and system bars,
     * top bar and loading bar. Called at start and whenever a theme setting changes.
     */
    protected void applyTheme() {
        Scheme scheme = ThemeManager.scheme(this);
        if (!isDrawerLayout()) {
            ThemeManager.styleWindow(this, scheme); // the drawer's window must stay see-through
            ThemeManager.applyStatusBar(this);      // Appearance → "Hide status bar"
        }
        NativeTheme.styleTopBar(findViewById(R.id.topBar), scheme);
        NativeTheme.styleProgressBar(progressBar, scheme);
    }

    protected int getLayoutResourceId() {
        return R.layout.activity_main;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // No StrictMode override any more (2026-10-01, VERIFICATION O-H4): until then an empty policy
        // here switched off Android's check against file:// addresses leaving the app, only so the
        // Android 7–9 download notification worked. That now uses a content:// address
        // (downloads/Downloader.sharedAddress), so Android's default check stays on.
        setActivityTheme();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        
        // Remember Android's own crash handler (only the first time: later onCreate calls would
        // otherwise capture our own handler from the previous activity instance).
        if (systemCrashHandler == null) {
            systemCrashHandler = Thread.getDefaultUncaughtExceptionHandler();
        }
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (thread == android.os.Looper.getMainLooper().getThread() && systemCrashHandler != null) {
                // The main (UI) thread is dead: nothing can run on it any more, so the "safe mode"
                // reload below could never happen and the app would just freeze. Let Android handle
                // it the normal way (close the app so it can be reopened cleanly). PROJECT.md P6.
                systemCrashHandler.uncaughtException(thread, throwable);
                return;
            }
            // A background thread failed; the app itself still works. Keep the original behaviour:
            // switch to safe mode (no injected scripts) and reload duck.ai.
            Log.e(TAG, "Uncaught error/exception intercepted. Activating Safe Mode fallback to pure duck.ai webview", throwable);
            isSafeMode = true;
            runOnUiThread(() -> {
                try {
                    if (chatWebView != null) {
                        chatWebView.loadUrl("https://duck.ai/");
                    }
                } catch (Throwable ignored) {}
            });
        });

        setContentView(getLayoutResourceId());
        if (!isDrawerLayout()) {
            // Content clear of the bars and the keyboard; with "Hide status bar" it uses the top strip too.
            ThemeManager.fitContent(findViewById(R.id.mainRoot));
        }

        progressBar = findViewById(R.id.progressBar);
        chatWebView = findViewById(R.id.chatWebView);

        SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
        background.execute(() -> {
            if (!OfflineChats.isEnabled(MainActivity.this)) {
                return; // setting "Offline chats" is off: nothing saved to move
            }
            // Very old versions kept the saved chats in the preferences: move them into the file once.
            // (Until 2026-10-15 every start also read the whole saved copy into memory and kept it there;
            // the saved-chats window now reads the file itself when it opens: getChatsJson below.)
            try {
                SharedPreferences p = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                String oldPrefsJson = p.getString("cached_chats_json", null);
                if (oldPrefsJson != null && !oldPrefsJson.equals("{}") && !oldPrefsJson.isEmpty()) {
                    ChatDatabaseHelper helper = ChatDatabaseHelper.getInstance(MainActivity.this);
                    String cached = helper.getCachedChatsJson();
                    if (cached == null || cached.isEmpty() || cached.equals("{}")) {
                        helper.saveCachedChatsJson(oldPrefsJson);
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to move cached_chats_json in background", t);
            }
        });
        currentZoomLevel = (float) prefs.getInt("text_zoom", 100);

        promptFiller = new PromptFiller(this);
        downloader = new Downloader(this, background);
        configureWebView(chatWebView, Providers.DUCK);

        accounts = AccountStore.load(prefs);
        // Before any account WebView exists: delete the data folders of removed accounts
        // (see web/Profiles for why this waits for a fresh start).
        Profiles.cleanUp(accounts.all());
        // duck.ai's (default) profile gets the same cache cap as the accounts; old temporary files
        // (camera photos, and copies older versions left) are deleted in the background.
        Profiles.limitDefaultCache();
        long startedAt = System.currentTimeMillis(); // taken now, before the job waits in the queue
        background.execute(() -> {
            AppCache.deleteTempFiles(this, AppCache.TEMP_MAX_AGE_MS, null);
            // Copies of attached files from earlier runs: no page can still read them (UploadCopies).
            UploadCopies.cleanUpAtStart(this, startedAt);
            // The offline document converter was removed (shared files are now sent as they are); its
            // 6.7 MB engine, downloaded by older versions, is no longer needed.
            new File(getFilesDir(), "anydoc_wasm_bg.wasm").delete();
        });

        // The WebView from the layout is the duck.ai tab; other accounts get their own WebView (in
        // their own profile) the first time they're opened. Default: 3 tabs stay loaded (R-PERF-1).
        tabManager = new TabManager(chatWebView, accounts.duck(), this::setUpTab, new TabManager.Listener() {
            @Override
            public void onAccountShown(Account account) {
                onTabShown(account);
            }

            @Override
            public void onTabGaveUp(Account account) {
                Toast.makeText(MainActivity.this, account.name
                        + " keeps crashing and was closed.", Toast.LENGTH_LONG).show();
            }
        }, prefs.getInt(PREF_MAX_LIVE_TABS, 3));
        DesktopMode.applySaved(this, prefs, chatWebView, accounts.duck()); // before duck.ai's first page load
        setupTopBar();
        updateTopBar(tabManager.current());
        appliedNight = ThemeManager.isNight(this);
        applyTheme();

        // The microphone is no longer asked for here: web/MicrophoneAccess asks when a page wants it.

        boolean restored = false;
        if (savedInstanceState != null) {
            if (chatWebView.restoreState(savedInstanceState) != null) {
                restored = true;
            }
        }
        if (!restored) {
            handleIntent(getIntent(), false);
        }

        // Normal app launch: open the account chosen in Settings → Behaviour ("Open at start"),
        // by default the one the user was using last time (see account/DefaultAccounts). When the
        // screen is only being rebuilt (e.g. after switching light/dark), stay on the account in use.
        String launchAction = getIntent() != null ? getIntent().getAction() : null;
        if (DefaultAccounts.purposeOf(launchAction) == DefaultAccounts.Purpose.LAUNCH) {
            String lastId = prefs.getString(PREF_LAST_ACCOUNT,
                    prefs.getString(PREF_LAST_PROVIDER, Providers.DUCK_ID));
            String choice = restored ? DefaultAccounts.LAUNCH_LAST
                    : prefs.getString(DefaultAccounts.PREF_LAUNCH, DefaultAccounts.LAUNCH_LAST);
            Account start = DefaultAccounts.launchAccount(accounts, choice, lastId);
            if (!start.isDuck()) {
                tabManager.show(start);
            }
        }
    }

    /**
     * Connects the native top bar (layout/top_bar.xml): the chip opens the account switcher, the gear
     * opens settings. Works the same for every provider (PROJECT.md R-SW-1/2), unlike duckAssist's
     * settings button, which was injected into duck.ai's page only.
     */
    private void setupTopBar() {
        View chip = findViewById(R.id.providerChip);
        if (chip != null) {
            chip.setOnClickListener(v -> SwitcherPopup.show(v, accounts.visible(), tabManager.current(),
                    ThemeManager.scheme(this), account -> tabManager.show(account),
                    this::reloadCurrentTab, () -> openSettings("accounts")));
        }
        View settings = findViewById(R.id.settingsButton);
        if (settings != null) {
            settings.setOnClickListener(v -> openSettings(null));
        }
        View desktop = findViewById(R.id.desktopButton);
        if (desktop != null) {
            desktop.setOnClickListener(v -> toggleDesktopMode());
        }
    }

    /**
     * The account menu's "Reload page": loads the page on screen again (e.g. when it's stuck). A tab
     * with no web page yet (blank) opens its provider's home page instead.
     */
    private void reloadCurrentTab() {
        WebView tab = tabManager.currentWebView();
        if (tab == null) {
            return;
        }
        String url = tab.getUrl();
        if (url == null || url.isEmpty() || url.startsWith("about:")) {
            tab.loadUrl(tabManager.currentProvider().homeUrl);
        } else {
            tab.reload();
        }
    }

    /** Turns evaluateJavascript's answer (a JSON value like "\"report.csv\"" or "null") into text. */
    private static String jsString(String jsonValue) {
        try {
            Object value = new org.json.JSONTokener(jsonValue).nextValue();
            return value instanceof String ? (String) value : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Full set-up of a tab: settings, clients, bridges, plus the account's desktop-site choice. */
    private void setUpTab(WebView webView, Account account) {
        configureWebView(webView, Providers.byId(account.providerId));
        DesktopMode.applySaved(this, getSharedPreferences("duck_assist_prefs", MODE_PRIVATE), webView, account);
    }

    /** The monitor button: switch the account on screen between the phone and desktop site. */
    private void toggleDesktopMode() {
        DesktopMode.toggle(getSharedPreferences("duck_assist_prefs", MODE_PRIVATE), tabManager.current().id);
        syncDesktopMode(tabManager.current());
    }

    /** The tab on screen and the monitor button follow the account's desktop-site choice (web/DesktopMode). */
    private void syncDesktopMode(Account account) {
        boolean on = DesktopMode.sync(this, getSharedPreferences("duck_assist_prefs", MODE_PRIVATE),
                tabManager.currentWebView(), account);
        android.widget.ImageView button = findViewById(R.id.desktopButton);
        if (button != null) {
            NativeTheme.styleDesktopButton(button, on, ThemeManager.scheme(this));
        }
    }

    /**
     * Makes every open tab follow "Pinch zooms the page" (web/PageZoom). A tab whose mode changes reloads
     * once; nothing happens when they already match (e.g. on every return to the app).
     */
    private void syncPageZoom() {
        if (tabManager == null) {
            return;
        }
        boolean on = PageZoom.isOn(getSharedPreferences("duck_assist_prefs", MODE_PRIVATE));
        for (WebView tab : tabManager.liveWebViews()) {
            Account account = tabManager.accountOf(tab);
            PageZoom.sync(this, tab, account != null ? Providers.byId(account.providerId) : Providers.DUCK, on);
        }
    }

    /** Text size for every open tab (Settings → Appearance → Text size, or the other screen changed it). */
    private void applyTextZoom(int percent) {
        currentZoomLevel = percent;
        if (tabManager != null) {
            for (WebView tab : tabManager.liveWebViews()) {
                tab.getSettings().setTextZoom(percent);
            }
        }
    }

    /** Shows the current account's provider badge and name in the top bar chip. */
    private void updateTopBar(Account account) {
        TextView badge = findViewById(R.id.providerBadge);
        TextView name = findViewById(R.id.providerName);
        View chip = findViewById(R.id.providerChip);
        if (badge == null || name == null || chip == null) {
            return;
        }
        ProviderSpec provider = Providers.byId(account.providerId);
        ProviderBadge.apply(badge, provider, ProviderBadge.dp(this, 28));
        name.setText(account.name);
        // What screen readers announce for the chip, e.g. "Switch account: Work, ChatGPT".
        String label = account.name.equals(provider.displayName)
                ? account.name : account.name + ", " + provider.displayName;
        chip.setContentDescription(getString(R.string.switch_provider) + ": " + label);
    }

    /** Called by TabManager after an account's tab became visible (or was renamed). */
    private void onTabShown(Account account) {
        updateTopBar(account);
        if (account.isDuck()) {
            duckUsedSinceDump = true;
        }
        syncDesktopMode(account);
        if (account.isDuck() && duckOffline) {
            showOfflineChats(chatWebView);
        }
        // A half-finished loading bar from the previous tab must not stay on screen.
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        getSharedPreferences("duck_assist_prefs", MODE_PRIVATE).edit()
                .putString(PREF_LAST_ACCOUNT, account.id).apply();
    }

    /** Brings the duck.ai tab to the front (the app's duck.ai-only features load pages there). */
    private void showDuckTab() {
        if (tabManager != null && !tabManager.current().isDuck()) {
            tabManager.show(accounts.duck());
        }
    }

    /** @return true if this WebView is the tab on screen (hidden tabs must not move the loading bar). */
    private boolean isVisibleTab(WebView view) {
        return tabManager == null || view == tabManager.currentWebView();
    }

    /**
     * Applies all settings, listeners and bridges to a provider's WebView ("tab").
     *
     * <p>Called for duck.ai's WebView from the layout (onCreate), for every other provider's WebView
     * when TabManager creates it, and for replacement WebViews after the renderer died. Keeping this
     * in one method guarantees they all get the same setup.</p>
     *
     * <p><b>duck.ai vs. the others:</b> only duck.ai's tab gets the app's JavaScript bridge
     * ({@code window.Android}) and the injected helper scripts. The bridge can read saved duck.ai
     * chats and save files, so it must never be reachable by other websites (PROJECT.md P1).
     * Other providers get a plain, secure browser tab.</p>
     */
    private void configureWebView(WebView webView, ProviderSpec provider) {
        WebSettings webSettings = webView.getSettings();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        }
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            webSettings.setMediaPlaybackRequiresUserGesture(false);
        }
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setDatabaseEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setSaveFormData(false);
        webSettings.setGeolocationEnabled(false);

        // Text size chosen by the user (pinch gesture or Settings → Appearance → Text size), see currentZoomLevel.
        webSettings.setTextZoom(Math.round(currentZoomLevel));
        // "Pinch zooms the page" (web/PageZoom): set up before the tab loads its first page.
        PageZoom.apply(this, webView, provider,
                PageZoom.isOn(getSharedPreferences("duck_assist_prefs", MODE_PRIVATE)));

        // The app draws its own provider icons, so the sites' favicons (tab icons) are never needed:
        // skipping their download saves data and a little time (PROJECT.md R-PERF-4).
        if (androidx.webkit.WebViewFeature.isFeatureSupported(
                androidx.webkit.WebViewFeature.DOWNLOAD_FAVICONS_ENABLED)) {
            androidx.webkit.WebSettingsCompat.setDownloadFaviconsEnabled(webSettings, false);
        }

        // Each account has its own cookie jar (its WebView profile, see web/Profiles), so ask this
        // WebView's profile for it instead of the app-wide CookieManager.getInstance().
        CookieManager cookieManager = Profiles.cookieManager(webView);
        cookieManager.setAcceptCookie(true);
        // duck.ai needs no third-party cookies. Sign-in pages of other providers often use a
        // security check (e.g. Cloudflare) inside an embedded frame, which needs them.
        cookieManager.setAcceptThirdPartyCookies(webView, !provider.isDuck());

        webView.setWebViewClient(new MyWebViewClient(provider));
        webView.setWebChromeClient(new MyWebChromeClient(provider));
        if (!provider.isDuck()) {
            // Sign-in pop-up windows open in the same account's container (web/PopupWindows).
            PopupWindows.enable(webSettings);
        }

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (url.startsWith("blob:")) {
                // A file the page made itself: only the page can read it, so a small script
                // (blob_fetch.js) reads it and hands it over through the tab's bridge (web/AppBridge;
                // duck.ai: the full bridge, other providers: the downloads-only one).
                if (!AppBridge.isSupported()) {
                    Toast.makeText(MainActivity.this, R.string.download_needs_newer_webview, Toast.LENGTH_LONG).show();
                    return;
                }
                // The script (assets/js/blob_fetch.js) is one function; the three values from the web
                // page are passed in as safe JS string literals made by JSONObject.quote(), which
                // escapes quotes, backslashes and line breaks, so a crafted file name can't inject
                // its own JavaScript (PROJECT.md P8).
                String args = "(" + org.json.JSONObject.quote(url) + ","
                        + org.json.JSONObject.quote(contentDisposition != null ? contentDisposition : "") + ","
                        + org.json.JSONObject.quote(mimetype != null ? mimetype : "") + ")";
                webView.evaluateJavascript(js(Scripts.BLOB_FETCH) + args, null); // in the tab it came from
                return;
            } else if (url.startsWith("data:")) {
                if (provider.isDuck()) {
                    downloader.saveDataUrl(provider, url, mimetype, contentDisposition, url, null);
                } else {
                    // WebView doesn't pass on the name the page gave the file (<a download="…">), but
                    // blob_capture.js noted it when the page clicked the link: ask the page for it
                    // (evaluateJavascript hands the script's answer back), then save.
                    webView.evaluateJavascript(js(Scripts.DOWNLOAD_NAME), name ->
                            downloader.saveDataUrl(provider, url, mimetype, contentDisposition, url, jsString(name)));
                }
                return;
            }
            // A normal link: Android's download manager fetches it with this tab's login cookies.
            downloader.download(provider, cookieManager, url, userAgent, contentDisposition, mimetype);
        });

        if (provider.isDuck()) {
            // duck.ai's helper scripts talk to the app through a message bridge that only duck.ai's
            // own main page can use (web/AppBridge, PROJECT.md P1). Before round 5 this was
            // addJavascriptInterface(this, "Android"), which every frame of the page could call.
            AppBridge.attachDuck(webView, bridgeHost(provider));
        } else {
            // Other providers: a downloads-only bridge (one message type, their own chat site only),
            // so files the page makes can be saved.
            AppBridge.attachDownloads(webView, provider, bridgeHost(provider));
        }

        ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scale = detector.getScaleFactor();
                currentZoomLevel = currentZoomLevel * scale;
                // Clamp text zoom between 50% and 300%
                currentZoomLevel = Math.max(50f, Math.min(currentZoomLevel, 300f));
                int newZoom = Math.round(currentZoomLevel);
                webView.getSettings().setTextZoom(newZoom);
                
                SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                prefs.edit().putInt("text_zoom", newZoom).apply();
                return true;
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            scaleGestureDetector.setQuickScaleEnabled(false);
        }

        webView.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // duck.ai's image viewer, the desktop site, or the setting "Pinch zooms the page"
                // (web/PageZoom): pinching zooms the page itself (WebView handles it) instead of
                // changing the text size.
                if (isImageZoomActive || DesktopMode.isActive(webView) || PageZoom.isActive(webView)) {
                    return false;
                }
                scaleGestureDetector.onTouchEvent(event);
                return event.getPointerCount() > 1 || scaleGestureDetector.isInProgress();
            }
        });
    }

    /**
     * What a page may ask the app to do, through its message bridge (web/AppBridge checks every
     * message first: it must come from the provider's own main page and have a type its scope
     * allows; other providers' bridges only ever pass on "blob"). These run on the main thread.
     */
    private AppBridge.Host bridgeHost(ProviderSpec provider) {
        return new AppBridge.Host() {
            @Override
            public void copyToClipboard(String text) {
                MainActivity.this.copyToClipboard(text);
            }

            @Override
            public void onChatsFetched(String json) {
                MainActivity.this.onChatsFetched(json);
            }

            @Override
            public void processBlob(String dataUrl, String mimetype, String contentDisposition, String pageUrl,
                                    String fileName) {
                downloader.saveDataUrl(provider, dataUrl, mimetype, contentDisposition, pageUrl, fileName);
            }

            @Override
            public void showSoftKeyboard() {
                chatWebView.requestFocus();
                android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(chatWebView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }

            @Override
            public void triggerNewChatShortcut() {
                if (chatWebView != null) {
                    chatWebView.requestFocus();
                    long now = android.os.SystemClock.uptimeMillis();
                    int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON;
                    chatWebView.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_O, 0, meta));
                    chatWebView.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_O, 0, meta));
                }
            }

            @Override
            public void setImageZoomActive(boolean active) {
                isImageZoomActive = active;
            }

            @Override
            public void onContinueLastChatSuccess() {
                pendingContinueLastChat = false;
            }
        };
    }

    private void copyToClipboard(final String text) {
        runOnUiThread(() -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Copied Text", text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, R.string.url_copied, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Opens the settings page (assets/settings.html) full screen.
     *
     * @param section id of a section to scroll to ("accounts" from the switcher's "Manage accounts"),
     *                or null for the top
     */
    private void openSettings(String section) {
        runOnUiThread(() -> {
            android.app.Dialog dialog = new android.app.Dialog(MainActivity.this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            
            WebView webView = new WebView(MainActivity.this);
            // Handles "renderer died" (closes this dialog instead of the app crashing) and keeps
            // web links opening in the browser. See web/DialogWebViewClient.java.
            webView.setWebViewClient(new DialogWebViewClient(dialog::dismiss));
            WebSettings ws = webView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setAllowFileAccess(false);
            ws.setAllowContentAccess(false);
            
            webView.addJavascriptInterface(new Object() {
                @JavascriptInterface
                public String getSettingsJson() {
                    SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                    org.json.JSONObject obj = new org.json.JSONObject();
                    try {
                        obj.put("use_drawer_assistant", prefs.getBoolean("use_drawer_assistant", true));
                        obj.put("use_drawer_shared", prefs.getBoolean("use_drawer_shared", true));
                        obj.put("trigger_voice_assistant", prefs.getBoolean("trigger_voice_assistant", true));
                        obj.put("continue_last_chat", prefs.getBoolean("continue_last_chat", false));
                        obj.put("auto_focus_keyboard", prefs.getBoolean("auto_focus_keyboard", false));
                        obj.put("rtl_resolver", prefs.getBoolean("rtl_resolver", false));
                        obj.put("prompt_on_launch", prefs.getBoolean("prompt_on_launch", false));
                        obj.put("ask_duck_suffix", prefs.getString("ask_duck_suffix", ""));
                        obj.put("shared_doc_suffix", prefs.getString("shared_doc_suffix", ""));
                        obj.put("max_live_tabs", prefs.getInt(PREF_MAX_LIVE_TABS, 3));
                        obj.put("offline_chats", OfflineChats.isEnabled(MainActivity.this));
                        obj.put(PageZoom.PREF, PageZoom.isOn(prefs));
                        obj.put("text_zoom", prefs.getInt("text_zoom", 100));
                    } catch (Exception e) {
                        Log.e(TAG, "Error generating settings JSON", e);
                    }
                    return obj.toString();
                }

                /** Settings → Behaviour → "Pinch zooms the page" (web/PageZoom): open tabs reload once. */
                @JavascriptInterface
                public void setPinchZoomPage(boolean on) {
                    PageZoom.setOn(getSharedPreferences("duck_assist_prefs", MODE_PRIVATE), on);
                    runOnUiThread(MainActivity.this::syncPageZoom);
                }

                /** Settings → Appearance → Text size (50–300 %, the same value the text-size pinch sets). */
                @JavascriptInterface
                public void setTextZoom(int percent) {
                    int value = Math.max(50, Math.min(300, percent));
                    getSharedPreferences("duck_assist_prefs", MODE_PRIVATE).edit().putInt("text_zoom", value).apply();
                    runOnUiThread(() -> applyTextZoom(value));
                }

                @JavascriptInterface
                public void saveSettings(String jsonStr) {
                    try {
                        org.json.JSONObject obj = new org.json.JSONObject(jsonStr);
                        boolean autoFocus = obj.optBoolean("auto_focus_keyboard", false);
                        boolean promptLaunch = obj.optBoolean("prompt_on_launch", false);
                        if (autoFocus) {
                            promptLaunch = false;
                        } else if (promptLaunch) {
                            autoFocus = false;
                        }
                        SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                        // "Accounts kept loaded" (1–10); anything else from the page is clamped.
                        int maxTabs = Math.max(TabLru.MIN_LIVE_TABS,
                                Math.min(TabLru.MAX_LIVE_TABS, obj.optInt("max_live_tabs", 3)));
                        boolean oldRtl = prefs.getBoolean("rtl_resolver", false);
                        boolean newRtl = obj.optBoolean("rtl_resolver", false);
                        prefs.edit()
                             .putBoolean("use_drawer_assistant", obj.optBoolean("use_drawer_assistant", true))
                             .putBoolean("use_drawer_shared", obj.optBoolean("use_drawer_shared", true))
                             .putBoolean("trigger_voice_assistant", obj.optBoolean("trigger_voice_assistant", true))
                             .putBoolean("continue_last_chat", obj.optBoolean("continue_last_chat", false))
                             .putBoolean("auto_focus_keyboard", autoFocus)
                             .putBoolean("rtl_resolver", newRtl)
                             .putBoolean("prompt_on_launch", promptLaunch)
                             .putString("ask_duck_suffix", obj.optString("ask_duck_suffix", ""))
                             .putString("shared_doc_suffix", obj.optString("shared_doc_suffix", ""))
                             .putInt(PREF_MAX_LIVE_TABS, maxTabs)
                             .apply();
                        // Apply right away: lowering the number closes the least recently used tabs.
                        runOnUiThread(() -> {
                            if (tabManager != null) {
                                tabManager.setMaxLiveTabs(maxTabs);
                            }
                        });
                        if (newRtl != oldRtl && chatWebView != null) {
                            runOnUiThread(() -> {
                                if (newRtl) {
                                    safeEvaluateJavascript(chatWebView, js(Scripts.RTL_RESOLVER));
                                } else {
                                    safeEvaluateJavascript(chatWebView, js(Scripts.RTL_CLEANUP));
                                }
                            });
                        }
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            Toast.makeText(MainActivity.this, "Settings saved successfully", Toast.LENGTH_SHORT).show();
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error saving settings", e);
                    }
                }

                @JavascriptInterface
                public void saveSettingsAuto(String jsonStr) {
                    try {
                        org.json.JSONObject obj = new org.json.JSONObject(jsonStr);
                        boolean autoFocus = obj.optBoolean("auto_focus_keyboard", false);
                        boolean promptLaunch = obj.optBoolean("prompt_on_launch", false);
                        if (autoFocus) {
                            promptLaunch = false;
                        } else if (promptLaunch) {
                            autoFocus = false;
                        }
                        SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                        // "Accounts kept loaded" (1–10); anything else from the page is clamped.
                        int maxTabs = Math.max(TabLru.MIN_LIVE_TABS,
                                Math.min(TabLru.MAX_LIVE_TABS, obj.optInt("max_live_tabs", 3)));
                        boolean oldRtl = prefs.getBoolean("rtl_resolver", false);
                        boolean newRtl = obj.optBoolean("rtl_resolver", false);
                        prefs.edit()
                             .putBoolean("use_drawer_assistant", obj.optBoolean("use_drawer_assistant", true))
                             .putBoolean("use_drawer_shared", obj.optBoolean("use_drawer_shared", true))
                             .putBoolean("trigger_voice_assistant", obj.optBoolean("trigger_voice_assistant", true))
                             .putBoolean("continue_last_chat", obj.optBoolean("continue_last_chat", false))
                             .putBoolean("auto_focus_keyboard", autoFocus)
                             .putBoolean("rtl_resolver", newRtl)
                             .putBoolean("prompt_on_launch", promptLaunch)
                             .putString("ask_duck_suffix", obj.optString("ask_duck_suffix", ""))
                             .putString("shared_doc_suffix", obj.optString("shared_doc_suffix", ""))
                             .putInt(PREF_MAX_LIVE_TABS, maxTabs)
                             .apply();
                        // Apply right away: lowering the number closes the least recently used tabs.
                        runOnUiThread(() -> {
                            if (tabManager != null) {
                                tabManager.setMaxLiveTabs(maxTabs);
                            }
                        });
                        if (newRtl != oldRtl && chatWebView != null) {
                            runOnUiThread(() -> {
                                if (newRtl) {
                                    safeEvaluateJavascript(chatWebView, js(Scripts.RTL_RESOLVER));
                                } else {
                                    safeEvaluateJavascript(chatWebView, js(Scripts.RTL_CLEANUP));
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error auto-saving settings", e);
                    }
                }

                @JavascriptInterface
                public void dismissSettings() {
                    runOnUiThread(() -> dialog.dismiss());
                }

                /**
                 * Settings → Data & tools → "Offline chats (duck.ai)" (storage/OfflineChats). The page
                 * asks for confirmation before turning it off, because off deletes the saved chats.
                 */
                @JavascriptInterface
                public void setOfflineChats(boolean on) {
                    OfflineChats.setEnabled(MainActivity.this, on);
                    if (!on) {
                        // Same single background thread as the chat save: a save already running
                        // finishes first and is then deleted; a later one sees the setting is off.
                        background.execute(() -> ChatDatabaseHelper.getInstance(MainActivity.this).deleteAll());
                    }
                    runOnUiThread(() -> {
                        if (chatWebView != null) {
                            safeEvaluateJavascript(chatWebView, OfflineChats.pageSwitch(on));
                        }
                        if (!on) {
                            retryDuckIfOffline(); // the saved chats were standing in for duck.ai
                        }
                    });
                }

                @JavascriptInterface
                public void openChatsViewer() {
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        fetchChatsAndShowViewer();
                    });
                }

                /** Settings → About: opens on top; Settings stays open underneath (ui/AboutWindow). */
                @JavascriptInterface
                public void openAbout() {
                    runOnUiThread(() -> AboutWindow.show(MainActivity.this));
                }

                @JavascriptInterface
                public void openScriptsManager() {
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        showScriptsManagerDialog();
                    });
                }
            }, "AndroidSettings");

            // Theme colours for the page (read-only, local page only; see theme/ThemeBridge).
            webView.addJavascriptInterface(new ThemeBridge(this), "AndroidTheme");
            // Data & tools → "Clear cache" (see storage/StorageBridge).
            webView.addJavascriptInterface(new StorageBridge(this, background, new StorageBridge.Host() {
                @Override
                public void runOnUiThread(Runnable action) {
                    MainActivity.this.runOnUiThread(action);
                }

                @Override
                public void clearWebViewCaches() {
                    for (WebView tab : tabManager.liveWebViews()) {
                        tab.clearCache(true); // this tab's own account (profile) only
                    }
                    Profiles.clearClosedProfileCaches(MainActivity.this, tabManager.liveProfileNames());
                }

                @Override
                public java.util.Set<String> filesInUse() {
                    return sharedFilesInUse();
                }

                @Override
                public void sendToPage(String script) {
                    webView.evaluateJavascript(script, null);
                }
            }), "AndroidStorage");
            // Accounts and Appearance sections (see account/AccountsBridge, theme/AppearanceBridge).
            webView.addJavascriptInterface(new AccountsBridge(new AccountsBridge.Host() {
                @Override
                public AccountStore accounts() {
                    return accounts;
                }

                @Override
                public SharedPreferences prefs() {
                    return getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                }

                @Override
                public Account currentAccount() {
                    return tabManager.current();
                }

                @Override
                public void runOnUiThread(Runnable action) {
                    MainActivity.this.runOnUiThread(action);
                }

                @Override
                public void onAccountsChanged() {
                    tabManager.updateAccounts(accounts); // new names, removed tabs, top bar
                }

                @Override
                public void closeAccountTab(String accountId) {
                    tabManager.closeAccount(accountId);
                }

                @Override
                public void openAccount(Account account) {
                    dialog.dismiss();
                    tabManager.show(account);
                }
            }), "AndroidAccounts");
            webView.addJavascriptInterface(new AppearanceBridge(this, new AppearanceBridge.Host() {
                @Override
                public void runOnUiThread(Runnable action) {
                    MainActivity.this.runOnUiThread(action);
                }

                @Override
                public void onThemeChanged() {
                    applyTheme(); // top bar, system bars… repaint at once
                }
            }), "AndroidAppearance");
            // Light ↔ dark can only fully switch (websites, system dialogs) by rebuilding the screen:
            // do it when the settings close, so the settings page itself isn't interrupted.
            dialog.setOnDismissListener(d -> {
                if (appliedNight != ThemeManager.isNight(MainActivity.this)) {
                    recreate();
                }
            });
            // Back first closes a pop-up inside the page (rename box, account menu…), then the page.
            dialog.setOnKeyListener((d, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_BACK) {
                    return false;
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    webView.evaluateJavascript("window.handleBack ? handleBack() : false", handled -> {
                        if (!"true".equals(handled)) {
                            dialog.dismiss();
                        }
                    });
                }
                return true;
            });

            // Only a plain section name may be appended (never anything from outside the app).
            boolean validSection = section != null && section.matches("[a-z]{1,20}");
            webView.loadUrl("file:///android_asset/settings.html" + (validSection ? "#" + section : ""));
            
            dialog.setContentView(webView);
            dialog.show();
            
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            }
        });
    }

    /** Receives duck.ai's chat dump (from the message bridge), merges it with the saved copy and stores it. */
    private void onChatsFetched(String jsonStr) {
        background.execute(() -> {
            if (!OfflineChats.isEnabled(MainActivity.this)) {
                isRequestingViewer = false;
                return; // setting "Offline chats" is off: never store chats
            }
            if (jsonStr == null || jsonStr.isEmpty() || jsonStr.equals("{}")) {
                if (isRequestingViewer) {
                    isRequestingViewer = false;
                    runOnUiThread(this::showChatsViewerDialog);
                }
                return;
            }
            try {
                org.json.JSONObject obj = new org.json.JSONObject(jsonStr);
                if (obj.has("error")) {
                    Log.w(TAG, "Fetched chats root contains error, skipping overwrite: " + obj.getString("error"));
                    if (isRequestingViewer) {
                        isRequestingViewer = false;
                        runOnUiThread(this::showChatsViewerDialog);
                    }
                    return;
                }
            } catch (Throwable e) {
                Log.w(TAG, "Fetched chats could not be parsed or caused OutOfMemoryError, skipping overwrite", e);
                if (isRequestingViewer) {
                    isRequestingViewer = false;
                    runOnUiThread(this::showChatsViewerDialog);
                }
                return;
            }
            String finalJsonStr = jsonStr;
            try {
                org.json.JSONObject newObj = new org.json.JSONObject(jsonStr);
                String oldJson = ChatDatabaseHelper.getInstance(MainActivity.this).getCachedChatsJson();
                if (oldJson != null && !oldJson.isEmpty() && !oldJson.equals("{}")) {
                    try {
                        org.json.JSONObject oldObj = new org.json.JSONObject(oldJson);
                        
                        // Pictures only the older saved copy has: kept newest first, up to a budget
                        // (storage/BackupImages). They used to be kept forever, and duck.ai's own pictures
                        // get a new blob: address on every visit, so the saved file only ever grew.
                        BackupImages oldPictures = new BackupImages(BackupImages.OLD_BUDGET_CHARS);
                        org.json.JSONObject blobMap = oldPictures.mergeMap(
                                newObj.optJSONObject("blobMap"), oldObj.optJSONObject("blobMap"));
                        if (blobMap != null) {
                            newObj.put("blobMap", blobMap);
                        }
                        org.json.JSONArray domImages = oldPictures.mergeList(
                                newObj.optJSONArray("domImages"), oldObj.optJSONArray("domImages"));
                        if (domImages != null) {
                            newObj.put("domImages", domImages);
                        }

                        // Merge indexedDB (never lose previous chats or images on partial dumps)
                        org.json.JSONObject oldIdb = oldObj.optJSONObject("indexedDB");
                        org.json.JSONObject newIdb = newObj.optJSONObject("indexedDB");
                        if (oldIdb != null) {
                            if (newIdb == null) {
                                newObj.put("indexedDB", oldIdb);
                            } else {
                                java.util.Iterator<String> dbNames = oldIdb.keys();
                                while (dbNames.hasNext()) {
                                    String dbName = dbNames.next();
                                    org.json.JSONObject oldStores = oldIdb.optJSONObject(dbName);
                                    org.json.JSONObject newStores = newIdb.optJSONObject(dbName);
                                    if (oldStores != null) {
                                        if (newStores == null) {
                                            newIdb.put(dbName, oldStores);
                                        } else {
                                            java.util.Iterator<String> storeNames = oldStores.keys();
                                            while (storeNames.hasNext()) {
                                                String sName = storeNames.next();
                                                org.json.JSONArray oldItems = oldStores.optJSONArray(sName);
                                                org.json.JSONArray newItems = newStores.optJSONArray(sName);
                                                if (oldItems != null && oldItems.length() > 0) {
                                                    if (newItems == null) {
                                                        newStores.put(sName, oldItems);
                                                    } else {
                                                        java.util.Map<String, org.json.JSONObject> itemMap = new java.util.LinkedHashMap<>();
                                                        for (int i = 0; i < oldItems.length(); i++) {
                                                            org.json.JSONObject it = oldItems.optJSONObject(i);
                                                            if (it != null) {
                                                                String key = it.optString("chatId", it.optString("uuid", it.optString("id", it.optString("key", String.valueOf(i)))));
                                                                itemMap.put(key, it);
                                                            }
                                                        }
                                                        for (int i = 0; i < newItems.length(); i++) {
                                                            org.json.JSONObject it = newItems.optJSONObject(i);
                                                            if (it != null) {
                                                                String key = it.optString("chatId", it.optString("uuid", it.optString("id", it.optString("key", String.valueOf(i)))));
                                                                itemMap.put(key, it);
                                                            }
                                                        }
                                                        org.json.JSONArray mergedArr = new org.json.JSONArray();
                                                        for (org.json.JSONObject it : itemMap.values()) {
                                                            mergedArr.put(it);
                                                        }
                                                        newStores.put(sName, mergedArr);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignore) {}
                }
                finalJsonStr = newObj.toString();
            } catch (Throwable e) {
                Log.w(TAG, "Fetched chats merge error", e);
            }
            ChatDatabaseHelper.getInstance(MainActivity.this).saveCachedChatsJson(finalJsonStr);
            if (isRequestingViewer || (chatsViewerDialog != null && chatsViewerDialog.isShowing())) {
                isRequestingViewer = false;
                runOnUiThread(this::showChatsViewerDialog);
            }
        });
    }

    private void fetchChatsAndShowViewer() {
        runOnUiThread(() -> {
            if (!OfflineChats.isEnabled(this)) {
                Toast.makeText(this, R.string.offline_chats_off, Toast.LENGTH_SHORT).show();
                return;
            }
            isRequestingViewer = true;
            String currentUrl = chatWebView.getUrl();
            if (currentUrl != null && (currentUrl.startsWith("https://duck.ai") || currentUrl.startsWith("https://duckduckgo.com"))) {
                chatWebView.evaluateJavascript(js(Scripts.DUMP_CHATS), null);
            } else {
                showChatsViewerDialog();
            }
        });
    }

    /**
     * duck.ai couldn't load (offline) or keeps crashing: show the chats saved on the phone instead.
     *
     * <p>Until round 5 the viewer page was loaded <i>inside</i> the duck.ai tab and used the tab's
     * {@code Android} bridge. The new bridge (web/AppBridge) only works for https://duck.ai pages,
     * never for a local {@code file:} page, so the viewer now opens in its own window, which has its
     * own small local-only bridge. Closing it tries duck.ai again (as the old in-tab close did).</p>
     * If the duck.ai tab isn't on screen, the window waits until the user switches to it.
     */
    private void showOfflineChats(WebView duckView) {
        if (!OfflineChats.isEnabled(this)) {
            return; // setting off: WebView's own error page stays
        }
        duckOffline = true;
        if (isVisibleTab(duckView) && (chatsViewerDialog == null || !chatsViewerDialog.isShowing())) {
            showChatsViewerDialog();
        }
    }

    /** After the offline fallback was closed by the user: load duck.ai again. */
    private void retryDuckIfOffline() {
        if (duckOffline && chatWebView != null) {
            duckOffline = false;
            chatWebView.loadUrl("https://duck.ai/");
        }
    }

    private void showChatsViewerDialog() {
        runOnUiThread(() -> {
            if (chatsViewerDialog != null && chatsViewerDialog.isShowing()) {
                chatsViewerDialog.dismiss();
            }
            chatsViewerDialog = new android.app.Dialog(MainActivity.this);
            chatsViewerDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            
            WebView webView = new WebView(MainActivity.this);
            // Handles "renderer died" (closes this dialog instead of the app crashing) and keeps
            // web links opening in the browser. See web/DialogWebViewClient.java.
            final android.app.Dialog thisDialog = chatsViewerDialog;
            webView.setWebViewClient(new DialogWebViewClient(thisDialog::dismiss));
            WebSettings ws = webView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setAllowFileAccess(false);
            ws.setAllowContentAccess(false);
            
            webView.addJavascriptInterface(new Object() {
                /**
                 * Read from the saved file each time the window asks (on a background thread: calls from
                 * a page's JavaScript never run on the main thread), so the copy isn't kept in memory.
                 * ChatDatabaseHelper's methods are synchronized: a save in progress finishes first.
                 */
                @JavascriptInterface
                public String getChatsJson() {
                    if (!OfflineChats.isEnabled(MainActivity.this)) {
                        return "{}";
                    }
                    String json = ChatDatabaseHelper.getInstance(MainActivity.this).getCachedChatsJson();
                    return json != null && !json.isEmpty() ? json : "{}";
                }
                
                @JavascriptInterface
                public void dismissViewer() {
                    runOnUiThread(() -> {
                        if (chatsViewerDialog != null) {
                            chatsViewerDialog.dismiss();
                        }
                        retryDuckIfOffline();
                    });
                }

                @JavascriptInterface
                public void copyToClipboard(String text) {
                    runOnUiThread(() -> {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Copied JSON", text);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(MainActivity.this, "Copied debug info to clipboard!", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @JavascriptInterface
                public void processBlob(String base64Data, String mimetype, String contentDisposition, String currentUrl) {
                    // Saving a chat from the offline viewer (duck.ai chats: duck.ai's file names).
                    runOnUiThread(() -> downloader.saveDataUrl(Providers.DUCK, base64Data, mimetype,
                            contentDisposition, currentUrl, null));
                }
            }, "AndroidChatsViewer");
            
            // Theme colours for the page (read-only, local page only; see theme/ThemeBridge).
            webView.addJavascriptInterface(new ThemeBridge(this), "AndroidTheme");
            webView.loadUrl("file:///android_asset/chats_viewer.html");
            // Back closes it ("cancel"); after an offline fallback that also retries duck.ai.
            chatsViewerDialog.setOnCancelListener(d -> retryDuckIfOffline());
            chatsViewerDialog.setContentView(webView);
            chatsViewerDialog.show();
            
            Window window = chatsViewerDialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            }
        });
    }

    private void showScriptsManagerDialog() {
        runOnUiThread(() -> {
            if (scriptsManagerDialog != null && scriptsManagerDialog.isShowing()) {
                scriptsManagerDialog.dismiss();
            }
            scriptsManagerDialog = new android.app.Dialog(MainActivity.this);
            scriptsManagerDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            WebView webView = new WebView(MainActivity.this);
            // Handles "renderer died" (closes this dialog instead of the app crashing) and keeps
            // web links opening in the browser. See web/DialogWebViewClient.java.
            final android.app.Dialog thisDialog = scriptsManagerDialog;
            webView.setWebViewClient(new DialogWebViewClient(thisDialog::dismiss));
            WebSettings ws = webView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setAllowFileAccess(false);
            ws.setAllowContentAccess(false);
            webView.setWebChromeClient(new WebChromeClient());

            webView.addJavascriptInterface(new Object() {
                @JavascriptInterface
                public String getScriptsJson() {
                    SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                    String json = prefs.getString("user_scripts", null);
                    if (json == null || json.trim().isEmpty()) {
                        json = getDefaultScriptsJson();
                        prefs.edit().putString("user_scripts", json).apply();
                    }
                    return json;
                }

                @JavascriptInterface
                public void saveScriptsJson(String json) {
                    SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                    prefs.edit().putString("user_scripts", json).apply();
                    if (chatWebView != null) {
                        runOnUiThread(() -> {
                            String currentUrl = chatWebView.getUrl();
                            injectUserScripts(chatWebView, currentUrl);
                        });
                    }
                }

                @JavascriptInterface
                public void dismissScriptsManager() {
                    runOnUiThread(() -> {
                        if (scriptsManagerDialog != null) {
                            scriptsManagerDialog.dismiss();
                        }
                    });
                }
            }, "AndroidScripts");

            // Theme colours for the page (read-only, local page only; see theme/ThemeBridge).
            webView.addJavascriptInterface(new ThemeBridge(this), "AndroidTheme");
            webView.loadUrl("file:///android_asset/scripts_manager.html");
            scriptsManagerDialog.setContentView(webView);
            scriptsManagerDialog.show();

            Window window = scriptsManagerDialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            }
        });
    }

    private String getDefaultScriptsJson() {
        try {
            org.json.JSONArray array = new org.json.JSONArray();
            org.json.JSONObject script = new org.json.JSONObject();
            // An example written for LLM Central (GPL-3.0-only, like the app). Until 2026-10-01 this was a
            // third-party script without a licence (F-Droid requires every file to be free).
            script.put("id", "example_larger_text");
            script.put("name", "Example: larger text on duck.ai");
            script.put("author", "LLM Central");
            script.put("description", "Shows how user scripts work: makes duck.ai's text 10 % larger. Edit or delete it as you like.");
            script.put("match", "*://*.duck.ai/*");
            script.put("enabled", false);
            script.put("code", js(Scripts.USER_SCRIPT_SAMPLE));
            array.put(script);
            return array.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error building default scripts JSON", e);
            return "[]";
        }
    }

    private void injectUserScripts(WebView view, String url) {
        if (isSafeMode || view == null) return;
        try {
            SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
            String scriptsJson = prefs.getString("user_scripts", null);
            if (scriptsJson == null || scriptsJson.trim().isEmpty()) {
                scriptsJson = getDefaultScriptsJson();
                prefs.edit().putString("user_scripts", scriptsJson).apply();
            }
            org.json.JSONArray array = new org.json.JSONArray(scriptsJson);
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject item = array.getJSONObject(i);
                if (!item.optBoolean("enabled", false)) {
                    continue;
                }
                String code = item.optString("code", "");
                if (code.trim().isEmpty()) {
                    continue;
                }

                String matchPatterns = item.optString("match", "*://*.duck.ai/*");
                // Only run the script on the sites listed in its "match" setting (see UserScriptMatcher).
                if (!UserScriptMatcher.matchesAny(url, matchPatterns)) {
                    continue;
                }

                String wrapped = wrapUserScriptCode(code);
                safeEvaluateJavascript(view, wrapped);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error injecting user scripts", e);
        }
    }

    private String wrapUserScriptCode(String code) {
        return "(function() {\n" +
               "  try {\n" +
               "    if (typeof window.GM_addStyle === 'undefined') {\n" +
               "      window.GM_addStyle = function(css) {\n" +
               "        var head = document.getElementsByTagName('head')[0] || document.documentElement;\n" +
               "        var style = document.createElement('style');\n" +
               "        style.type = 'text/css';\n" +
               "        style.appendChild(document.createTextNode(css));\n" +
               "        head.appendChild(style);\n" +
               "        return style;\n" +
               "      };\n" +
               "    }\n" +
               "    if (typeof GM_addStyle === 'undefined') {\n" +
               "      var GM_addStyle = window.GM_addStyle;\n" +
               "    }\n" +
               "    if (typeof unsafeWindow === 'undefined') {\n" +
               "      var unsafeWindow = window;\n" +
               "    }\n" +
               code + "\n" +
               "  } catch (err) {\n" +
               "    console.error('UserScript execution error:', err);\n" +
               "  }\n" +
               "})();";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Downloader.PERMISSION_REQUEST_CODE) {
            downloader.onPermissionResult(permissions, grantResults);
        } else if (requestCode == MicrophoneAccess.REQUEST_CODE) {
            microphone.onAndroidResult(grantResults);
        } else if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_SHORT).show();
                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(null);
                    mUploadMessage = null;
                }
            }
        }
    }

    private void showCustomBanner(String message) {
        runOnUiThread(() -> {
            View root = findViewById(android.R.id.content);
            if (root instanceof android.view.ViewGroup) {
                android.view.ViewGroup viewGroup = (android.view.ViewGroup) root;
                
                View oldBanner = viewGroup.findViewWithTag("attention_banner");
                if (oldBanner != null) {
                    viewGroup.removeView(oldBanner);
                }
                
                android.widget.LinearLayout bannerCard = new android.widget.LinearLayout(this);
                bannerCard.setTag("attention_banner");
                bannerCard.setOrientation(android.widget.LinearLayout.VERTICAL);
                
                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                shape.setColor(Color.parseColor("#d32f2f"));
                shape.setCornerRadius(12 * getResources().getDisplayMetrics().density);
                bannerCard.setBackground(shape);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    bannerCard.setElevation(16 * getResources().getDisplayMetrics().density);
                }
                
                android.widget.TextView textView = new android.widget.TextView(this);
                textView.setText(message);
                textView.setTextColor(Color.WHITE);
                textView.setTextSize(16);
                textView.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
                int padding = (int) (16 * getResources().getDisplayMetrics().density);
                textView.setPadding(padding, padding, padding, padding);
                textView.setGravity(android.view.Gravity.CENTER);
                
                bannerCard.addView(textView);
                
                android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                );
                int margin = (int) (16 * getResources().getDisplayMetrics().density);
                lp.setMargins(margin, margin + (int)(24 * getResources().getDisplayMetrics().density), margin, margin);
                lp.gravity = android.view.Gravity.TOP;
                
                bannerCard.setTranslationY(-300);
                viewGroup.addView(bannerCard, lp);
                
                bannerCard.animate()
                        .translationY(0)
                        .setDuration(400)
                        .setInterpolator(new android.view.animation.OvershootInterpolator())
                        .start();
                
                bannerCard.postDelayed(() -> {
                    bannerCard.animate()
                            .translationY(-400)
                            .setDuration(300)
                            .withEndAction(() -> viewGroup.removeView(bannerCard))
                            .start();
                }, 6000);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The theme may have been changed in the app's other screen (assistant drawer): rebuild if
        // light/dark flipped, otherwise just repaint (palette / Material You).
        if (tabManager != null) {
            if (appliedNight != ThemeManager.isNight(this)) {
                recreate();
                return;
            }
            applyTheme();
        }
        refreshAccounts();
        if (tabManager != null) {
            // Settings changed in the app's other screen (assistant drawer): text size and pinch mode.
            int textZoom = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE).getInt("text_zoom", 100);
            if (textZoom != Math.round(currentZoomLevel)) {
                applyTextZoom(textZoom);
            }
            syncPageZoom();
            syncDesktopMode(tabManager.current());
            if (tabManager.current().isDuck()) {
                duckUsedSinceDump = true;
            }
        }
    }

    /**
     * The account list may have been changed by the app's other screen (the assistant drawer is a
     * separate activity with its own copy): reload it and refresh tabs + top bar if it differs.
     */
    private void refreshAccounts() {
        if (accounts != null && tabManager != null) {
            AccountStore fresh = AccountStore.load(getSharedPreferences("duck_assist_prefs", MODE_PRIVATE));
            if (!fresh.toJson().equals(accounts.toJson())) {
                accounts = fresh;
                tabManager.updateAccounts(accounts);
            }
        }
    }

    /**
     * The manifest lists "uiMode" in configChanges, so switching the phone's dark mode doesn't rebuild
     * this screen; Android calls this instead. With the "System default" theme, repaint in the new mode.
     */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (tabManager != null && ThemeManager.MODE_SYSTEM.equals(ThemeManager.mode(this))) {
            appliedNight = ThemeManager.isNight(this);
            applyTheme();
        }
    }

    /**
     * Android asks running apps to free memory when the phone runs low. Closing the hidden account
     * tabs (not duck.ai, not the visible one) frees the most; they reload when opened again
     * (PROJECT.md R-PERF-1). Docs: https://developer.android.com/reference/android/content/ComponentCallbacks2
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (tabManager != null && (level >= TRIM_MEMORY_BACKGROUND || level == TRIM_MEMORY_RUNNING_CRITICAL)) {
            tabManager.closeHiddenTabs();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Save every open account's login cookies to disk now (the app may be closed by Android soon).
        if (tabManager != null) {
            tabManager.flushCookies();
        }
        // Copies of attached files older than an hour (storage/UploadCopies): the process may live for days.
        UploadCopies.cleanUpExpired(this);
        // Save duck.ai's chats for offline reading, but only if duck.ai was used since the last save:
        // the dump reads every chat and image, too much work to repeat when only other accounts were
        // used (round 6).
        if (duckUsedSinceDump && OfflineChats.isEnabled(this) && chatWebView != null && chatWebView.getUrl() != null
                && chatWebView.getUrl().startsWith("https://duck")) {
            duckUsedSinceDump = false;
            chatWebView.evaluateJavascript(js(Scripts.DUMP_CHATS), null);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (chatWebView != null) {
            chatWebView.saveState(outState);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent, true);
    }

    private void handleIntent(Intent intent, boolean isResuming) {
        if (intent == null)
            return;
        String action = intent.getAction();
        String type = intent.getType();
        Uri data = intent.getData();
        // Debug builds only: the data URL and extras can contain the user's prompt or shared text,
        // which must not end up in the system log of release builds (PROJECT.md P15, R-SEC-3).
        if (BuildConfig.DEBUG) Log.d(TAG, "handleIntent: action = " + action + ", type = " + type + ", data = " + (data != null ? data.toString() : "null"));
        if (BuildConfig.DEBUG && intent.getExtras() != null) {
            for (String key : intent.getExtras().keySet()) {
                Log.d(TAG, "  extra: " + key + " = " + intent.getExtras().get(key));
            }
        }

        // Which account handles this request: the one chosen in Settings → "Assistant & sharing"
        // (duck.ai unless changed; see account/DefaultAccounts). Links from other apps are duck.ai
        // addresses, so they always go to duck.ai. A normal launch (MAIN) keeps whatever tab is open.
        refreshAccounts(); // the other screen (assistant drawer) may have changed the list
        DefaultAccounts.Purpose purpose = DefaultAccounts.purposeOf(action);
        SharedPreferences choices = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
        final Account target = DefaultAccounts.target(accounts, purpose,
                choices.getString(DefaultAccounts.PREF_ASSISTANT, null),
                choices.getString(DefaultAccounts.PREF_SHARE, null));
        if (purpose == DefaultAccounts.Purpose.ASSISTANT || purpose == DefaultAccounts.Purpose.LINK
                || (purpose == DefaultAccounts.Purpose.SHARE && tabManager.isLoaded(target.id))) {
            // (A share to an account whose tab isn't loaded yet opens it below, straight at the new
            // chat page, instead of loading its home page first.)
            tabManager.show(target);
        }

        if (Intent.ACTION_VIEW.equals(action)) {
            data = intent.getData();
            if (data != null) {
                String query = data.getQueryParameter("q");
                if (query != null && !query.isEmpty()) {
                    SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                    boolean continueLastChat = prefs.getBoolean("continue_last_chat", false);
                    if (continueLastChat) {
                        pendingContinueLastChat = true;
                    }
                    chatWebView.loadUrl(PromptPrefill.duckUrl(query, false));
                } else {
                    // Other apps can open this screen with any address (the activity is "exported").
                    // Only load chat-site addresses in-app; everything else (javascript:, file:, other
                    // websites...) would run with the app's JavaScript bridge (PROJECT.md P14).
                    String requested = data.toString();
                    String safeUrl = NavigationPolicy.toInAppUrl(requested);
                    if (safeUrl != null) {
                        chatWebView.loadUrl(safeUrl);
                    } else if (BuildConfig.DEBUG && NavigationPolicy.isRendererTestUrl(requested)) {
                        // Debug builds only: lets the owner test renderer-crash recovery with
                        // adb (see CHANGES.md, "How to test the crash recovery").
                        chatWebView.loadUrl(requested);
                    } else {
                        Log.w(TAG, "Refused to load a non-chat address requested by another app");
                        chatWebView.loadUrl("https://duck.ai/");
                    }
                }
            } else {
                chatWebView.loadUrl("https://duck.ai/");
            }
        } else if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action) || Intent.ACTION_PROCESS_TEXT.equals(action)) {
            String sharedText = null;
            java.util.ArrayList<Uri> streamUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            Uri streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (streamUris != null && !streamUris.isEmpty()) {
                pendingSharedFileUris = streamUris;
                pendingSharedFileUri = streamUris.get(0);
            } else if (streamUri != null) {
                // Any shared file (document, PDF, image…) is offered to the page exactly as it is: the
                // page's 📎 button receives this address (onShowFileChooser) and WebView reads the file
                // itself, with its real name and type. No conversion, splitting or renamed copy.
                pendingSharedFileUris = null; // an older share not attached yet must not win
                pendingSharedFileUri = streamUri;
            } else if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            } else if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
                CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
                if (text != null)
                    sharedText = text.toString();
            }

            if (pendingSharedFileUris != null || pendingSharedFileUri != null) {
                if (pendingSharedFileUris != null && pendingSharedFileUris.size() > 1) {
                    showCustomBanner("Shared " + pendingSharedFileUris.size() + " files! Tap 📎 to attach");
                } else {
                    showCustomBanner("Tap 📎 to attach the shared file");
                }
                openChatForSharedFiles(target);
            } else if (sharedText != null) {
                sendPrompt(target, sharedText);
            } else if (target.isDuck()) {
                chatWebView.loadUrl("https://duck.ai/");
            } else {
                tabManager.show(target);
            }
        } else if (Intent.ACTION_ASSIST.equals(action) && !target.isDuck()) {
            // Another provider is the assistant: its tab is already on screen (shown above). Voice
            // chat start-up is a duck.ai feature (its page's voice button), so nothing more to do.
            pendingVoiceChat = false;
        } else if (Intent.ACTION_ASSIST.equals(action)) {
            Log.d(TAG, "Assistance shortcut triggered");
            pendingAutoFocus = false;
            SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
            boolean triggerVoice = prefs.getBoolean("trigger_voice_assistant", true);
            if (triggerVoice) {
                pendingVoiceChat = true;
                String currentUrl = chatWebView.getUrl();
                if (currentUrl != null && currentUrl.startsWith("https://duck.ai")) {
                    safeEvaluateJavascript(chatWebView, "window.isVoiceChatActive = true;");
                    chatWebView.evaluateJavascript(js(Scripts.VOICE_CHAT), null);
                } else {
                    chatWebView.loadUrl("https://duck.ai/");
                }
            } else {
                pendingVoiceChat = false;
                if (chatWebView.getUrl() == null || chatWebView.getUrl().isEmpty()
                        || chatWebView.getUrl().equals("about:blank")) {
                    chatWebView.loadUrl("https://duck.ai/");
                }
            }
        } else if (Intent.ACTION_MAIN.equals(action) || action == null) {
            pendingVoiceChat = false;
            safeEvaluateJavascript(chatWebView, "window.isVoiceChatActive = false;");
            SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
            // "Auto-focus keyboard" only applies while duck.ai is the tab on screen.
            boolean autoFocus = prefs.getBoolean("auto_focus_keyboard", false)
                    && (tabManager == null || tabManager.current().isDuck());
            if (autoFocus) {
                String currentUrl = chatWebView.getUrl();
                if (currentUrl != null && currentUrl.startsWith("https://duck.ai")) {
                    chatWebView.evaluateJavascript(js(Scripts.AUTO_FOCUS), null);
                } else {
                    pendingAutoFocus = true;
                    chatWebView.loadUrl("https://duck.ai/");
                }
            } else {
                if (chatWebView.getUrl() == null || chatWebView.getUrl().isEmpty()
                        || chatWebView.getUrl().equals("about:blank")) {
                    chatWebView.loadUrl("https://duck.ai/");
                }
            }
            if (prefs.getBoolean("prompt_on_launch", false)) {
                showPromptOnLaunchDialog();
            }
        } else {
            if (chatWebView.getUrl() == null || chatWebView.getUrl().isEmpty()
                    || chatWebView.getUrl().equals("about:blank")) {
                chatWebView.loadUrl("https://duck.ai/");
            }
        }
    }

    /**
     * Opens a new chat with {@code promptText} (plus the user's "text added to prompts", if any) in
     * the account's tab. The text appears in the chat box, not yet sent (see provider/PromptPrefill).
     */
    private void sendPrompt(Account account, String promptText) {
        if (promptText == null || promptText.trim().isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
        String suffix = prefs.getString("shared_doc_suffix", "");
        if (suffix == null || suffix.trim().isEmpty()) {
            suffix = prefs.getString("ask_duck_suffix", "");
        }
        String fullPrompt = promptText;
        if (suffix != null && !suffix.trim().isEmpty()) {
            fullPrompt = promptText + "\n\n" + suffix;
        }
        ProviderSpec provider = Providers.byId(account.providerId);
        if (account.isDuck() && prefs.getBoolean("continue_last_chat", false)) {
            pendingContinueLastChat = true;
        }
        // duck.ai: its handoff link; Claude: its "?q=" link; others: their home page (PromptPrefill).
        WebView tab = tabManager.show(account, PromptPrefill.startUrl(provider, fullPrompt));
        if (!account.isDuck()) {
            // Types the text into the site's message box once the page is ready (web/PromptFiller).
            promptFiller.request(tab, provider, fullPrompt);
        }
    }

    /**
     * Files were shared: open a new chat in the target account, so the user can attach them with the
     * page's 📎 button (the app hands over the waiting files when the page asks, see
     * onShowFileChooser). The "text added to shared documents" is filled in, if set.
     */
    private void openChatForSharedFiles(Account account) {
        SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
        String docSuffix = prefs.getString("shared_doc_suffix", "");
        if (docSuffix != null && !docSuffix.trim().isEmpty()) {
            sendPrompt(account, docSuffix);
            return;
        }
        if (account.isDuck()) {
            if (prefs.getBoolean("continue_last_chat", false)) {
                pendingContinueLastChat = true;
            }
            tabManager.show(account, "https://duck.ai/chat");
        } else {
            tabManager.show(account, Providers.byId(account.providerId).homeUrl);
        }
    }

    private android.app.Dialog promptDialog = null;

    public void showPromptOnLaunchDialog() {
        runOnUiThread(() -> {
            if (promptDialog != null && promptDialog.isShowing()) {
                promptDialog.dismiss();
            }
            promptDialog = new android.app.Dialog(MainActivity.this);
            promptDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            
            WebView webView = new WebView(MainActivity.this);
            // Handles "renderer died" (closes this dialog instead of the app crashing) and keeps
            // web links opening in the browser. See web/DialogWebViewClient.java.
            final android.app.Dialog thisDialog = promptDialog;
            webView.setWebViewClient(new DialogWebViewClient(thisDialog::dismiss));
            webView.setBackgroundColor(Color.TRANSPARENT);
            WebSettings ws = webView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setAllowFileAccess(false);
            ws.setAllowContentAccess(false);
            
            webView.addJavascriptInterface(new Object() {
                @JavascriptInterface
                public void sendPrompt(String text) {
                    runOnUiThread(() -> {
                        showDuckTab();
                        if (promptDialog != null && promptDialog.isShowing()) {
                            promptDialog.dismiss();
                        }
                        if (text == null || text.trim().isEmpty()) return;
                        
                        SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                        String suffix = prefs.getString("ask_duck_suffix", "");
                        String promptText = text.trim();
                        if (suffix != null && !suffix.trim().isEmpty()) {
                            promptText = promptText + "\n\n" + suffix;
                        }
                        
                        chatWebView.loadUrl(PromptPrefill.duckUrl(promptText, true));
                    });
                }

                @JavascriptInterface
                public void dismissPrompt() {
                    runOnUiThread(() -> {
                        if (promptDialog != null && promptDialog.isShowing()) {
                            promptDialog.dismiss();
                        }
                    });
                }

                @JavascriptInterface
                public void showSoftKeyboard() {
                    runOnUiThread(() -> {
                        webView.requestFocus();
                        android.view.inputmethod.InputMethodManager imm = 
                            (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showSoftInput(webView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                        }
                    });
                }
            }, "AndroidPrompt");

            // Theme colours for the page (read-only, local page only; see theme/ThemeBridge).
            webView.addJavascriptInterface(new ThemeBridge(this), "AndroidTheme");
            webView.loadUrl("file:///android_asset/prompt_dialog.html");
            promptDialog.setContentView(webView);
            promptDialog.show();
            
            Window window = promptDialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        });
    }

    // Times (ms since boot) of recent renderer recoveries, used to detect a crash loop.
    private final java.util.ArrayDeque<Long> rendererRecoveryTimes = new java.util.ArrayDeque<>();
    private static final int MAX_RECOVERIES_PER_MINUTE = 3;

    private class MyWebViewClient extends WebViewClient {

        /** The provider whose tab this client serves (decides allowed links and injected scripts). */
        private final ProviderSpec provider;

        MyWebViewClient(ProviderSpec provider) {
            this.provider = provider;
        }

        /**
         * Android 8.0+ calls this when the renderer process behind the chat WebView died, either
         * because Android reclaimed memory ({@code didCrash() == false}, common in the background) or
         * because it crashed ({@code didCrash() == true}). Before this handler existed, the whole app
         * was killed in both cases (PROJECT.md P2).
         *
         * <p>Recovery: replace the dead WebView with a fresh one in the same spot, configure it exactly
         * like the original ({@link #configureWebView}) and reload the page the user was on. If
         * the page keeps crashing (3 times within a minute), load the local offline chat viewer
         * instead, to avoid an endless crash-and-reload loop.</p>
         *
         * @return true = "handled, don't kill the app"
         */
        @Override
        @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
        public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
            if (!provider.isDuck()) {
                // Other providers' tabs are recovered (or closed) by TabManager.
                if (tabManager != null) {
                    tabManager.onRendererGone(view);
                } else {
                    WebViewRecovery.discard(view);
                }
                return true;
            }
            boolean crashed = detail.didCrash();
            Log.w(TAG, "Chat WebView renderer gone (crashed=" + crashed + "); recovering");

            if (view != chatWebView) {
                // A WebView that is no longer the current one (shouldn't happen): just free it.
                WebViewRecovery.discard(view);
                return true;
            }

            String lastUrl = view.getUrl(); // read before the dead view is destroyed

            // The old file-picker callback belongs to the dead WebView: forget it (don't call it).
            mUploadMessage = null;
            hasInjectedProgressJs = false;
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }

            chatWebView = WebViewRecovery.replace(view);
            configureWebView(chatWebView, Providers.DUCK);
            if (accounts != null) {
                DesktopMode.applySaved(MainActivity.this,
                        getSharedPreferences("duck_assist_prefs", MODE_PRIVATE), chatWebView, accounts.duck());
            }
            if (tabManager != null) {
                tabManager.replaceDuckView(chatWebView); // keeps it hidden if another tab is on screen
            }

            // Crash-loop guard: keep only recoveries from the last 60 seconds.
            long now = android.os.SystemClock.elapsedRealtime();
            rendererRecoveryTimes.addLast(now);
            while (!rendererRecoveryTimes.isEmpty() && now - rendererRecoveryTimes.peekFirst() > 60_000) {
                rendererRecoveryTimes.removeFirst();
            }

            if (rendererRecoveryTimes.size() >= MAX_RECOVERIES_PER_MINUTE) {
                Toast.makeText(MainActivity.this, OfflineChats.isEnabled(MainActivity.this)
                        ? "The chat page keeps crashing. Showing saved chats instead."
                        : "The chat page keeps crashing. Try \"Reload page\" in the account menu later.",
                        Toast.LENGTH_LONG).show();
                chatWebView.loadUrl("about:blank");
                showOfflineChats(chatWebView);
            } else {
                if (crashed) {
                    Toast.makeText(MainActivity.this,
                            "The chat page stopped unexpectedly and was reloaded.", Toast.LENGTH_SHORT).show();
                }
                // Only reload real web pages; anything else (or nothing) → start page.
                boolean isWebPage = lastUrl != null
                        && (lastUrl.startsWith("https://") || lastUrl.startsWith("http://"));
                chatWebView.loadUrl(isWebPage ? lastUrl : "https://duck.ai/");
            }
            return true;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url == null) return false;
            if (url.startsWith("blob:") || url.startsWith("data:") || url.startsWith("javascript:") || url.startsWith("file:")) {
                return false;
            }
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            
            // Only this provider's own sites and login pages load inside its tab (exact domain match,
            // see ProviderSpec.ownsHost / PROJECT.md P14). Everything else opens in the browser.
            if (provider.ownsHost(host)) {
                return false;
            }

            // Redirect all other external links to the default browser. CATEGORY_BROWSABLE: only apps
            // that agree to be opened from web links may answer (what browsers do; a chat page can't
            // start any other app this way). The address isn't logged: it may contain private text.
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                startActivity(intent);
            } catch (Throwable t) {
                Log.e(TAG, "Error opening external link", t);
            }
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            hasInjectedProgressJs = false;
            if (isVisibleTab(view)) {
                progressBar.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            if (isVisibleTab(view)) {
                progressBar.setVisibility(View.GONE);
            }
            if (isSafeMode) return;
            DesktopMode.onPageFinished(MainActivity.this, view); // wide page in desktop mode
            PageZoom.onPageFinished(MainActivity.this, view);    // fallback for old WebViews
            if (!provider.isDuck()) {
                // Other providers: no duck.ai helper scripts (they target duck.ai's page and need the
                // bridge). Files the page makes become downloadable (blob_capture.js); a shared prompt
                // waiting for this tab is typed in; the user's own scripts still run where their
                // "match" setting allows.
                safeEvaluateJavascript(view, js(Scripts.BLOB_CAPTURE));
                promptFiller.onPageFinished(view, url);
                injectUserScripts(view, url);
                return;
            }
            safeEvaluateJavascript(view, OfflineChats.pageFlag(OfflineChats.isEnabled(MainActivity.this))
                    + js(Scripts.BLOB_DOWNLOAD_PATCH));
            safeEvaluateJavascript(view, js(Scripts.CLIPBOARD_PATCH));
            safeEvaluateJavascript(view, js(Scripts.IMAGE_ZOOM_MONITOR));
            SharedPreferences prefs = view.getContext().getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
            if (prefs.getBoolean("rtl_resolver", false)) {
                safeEvaluateJavascript(view, js(Scripts.RTL_RESOLVER));
            }
            if (!pendingVoiceChat && (pendingAutoFocus || prefs.getBoolean("auto_focus_keyboard", false))) {
                safeEvaluateJavascript(view, js(Scripts.AUTO_FOCUS));
                pendingAutoFocus = false;
            }
            safeEvaluateJavascript(view, js(Scripts.SWIPE_SCROLL));
            injectUserScripts(view, url);
            if (pendingContinueLastChat) {
                safeEvaluateJavascript(view, js(Scripts.CONTINUE_LAST_CHAT));
            }
            if (pendingVoiceChat) {
                safeEvaluateJavascript(view, "window.isVoiceChatActive = true;");
                safeEvaluateJavascript(view,
                        "setTimeout(function() {" +
                                js(Scripts.VOICE_CHAT) +
                                "}, 1500);");
                pendingVoiceChat = false;
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (request.isForMainFrame() && provider.isDuck()) {
                    Log.d(TAG, "Connection failed for duck.ai, showing saved chats: " + error.getDescription());
                    runOnUiThread(() -> showOfflineChats(view));
                    return; // the saved chats are shown instead
                }
            }
            super.onReceivedError(view, request, error);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            if (provider.isDuck()) {
                Log.d(TAG, "Connection failed (legacy), showing saved chats: " + description);
                runOnUiThread(() -> showOfflineChats(view));
                return;
            }
            super.onReceivedError(view, errorCode, description, failingUrl);
        }
    }

    private class MyWebChromeClient extends WebChromeClient {

        private final ProviderSpec provider;

        MyWebChromeClient(ProviderSpec provider) {
            this.provider = provider;
        }
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            if (isSafeMode || !provider.isDuck()) return; // helper scripts are duck.ai-only
            if (newProgress > 5 && !hasInjectedProgressJs) {
                hasInjectedProgressJs = true;
                safeEvaluateJavascript(view, OfflineChats.pageFlag(OfflineChats.isEnabled(MainActivity.this))
                        + js(Scripts.BLOB_DOWNLOAD_PATCH));
                safeEvaluateJavascript(view, js(Scripts.CLIPBOARD_PATCH));
                safeEvaluateJavascript(view, js(Scripts.SWIPE_SCROLL));
                SharedPreferences prefs = view.getContext().getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                if (prefs.getBoolean("rtl_resolver", false)) {
                    safeEvaluateJavascript(view, js(Scripts.RTL_RESOLVER));
                }
            }
            if (newProgress == 100) {
                SharedPreferences prefs = view.getContext().getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                if (!pendingVoiceChat && prefs.getBoolean("auto_focus_keyboard", false)) {
                    safeEvaluateJavascript(view, js(Scripts.AUTO_FOCUS));
                }
            }
        }

        /** A provider page opens a new window (usually a sign-in pop-up), see web/PopupWindows. */
        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                      android.os.Message resultMsg) {
            Account account = tabManager != null ? tabManager.accountOf(view) : null;
            if (provider.isDuck() || account == null) {
                return false;
            }
            return PopupWindows.open(view, provider, account, isUserGesture, resultMsg);
        }

        @Override
        public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
            // Page console messages can contain chat text, so they go to the system log (logcat)
            // only in debug builds, never in release builds (PROJECT.md P15, R-SEC-3).
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "JS Console: " + consoleMessage.message() + " (Line " + consoleMessage.lineNumber() + ")");
            }
            return true;
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            // Microphone (voice chat): only for the provider's own pages; Android's permission is
            // asked at this moment if the app doesn't have it yet (web/MicrophoneAccess).
            MainActivity.this.runOnUiThread(() -> microphone.onPageRequest(request, provider));
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            MainActivity.this.runOnUiThread(() -> microphone.onPageRequestCanceled(request));
        }

        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback,
                WebChromeClient.FileChooserParams fileChooserParams) {
            // Files shared to the app are handed over now, as copies in the app's own storage (same
            // name, same bytes; see storage/UploadCopies for why a copy).
            if (pendingSharedFileUris != null && !pendingSharedFileUris.isEmpty()) {
                Uri[] files = pendingSharedFileUris.toArray(new Uri[0]);
                pendingSharedFileUris = null;
                pendingSharedFileUri = null;
                UploadCopies.prepare(MainActivity.this, files, filePathCallback::onReceiveValue);
                return true;
            }
            if (pendingSharedFileUri != null) {
                Uri[] files = { pendingSharedFileUri };
                pendingSharedFileUri = null;
                UploadCopies.prepare(MainActivity.this, files, filePathCallback::onReceiveValue);
                return true;
            }
            if (mUploadMessage != null) {
                mUploadMessage.onReceiveValue(null);
            }
            mUploadMessage = filePathCallback;

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Select Option");
            builder.setItems(new CharSequence[]{"Camera", "File Manager"}, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
                        } else {
                            openCamera();
                        }
                    } else {
                        openFileManager();
                    }
                }
            });
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    if (mUploadMessage != null) {
                        mUploadMessage.onReceiveValue(null);
                        mUploadMessage = null;
                    }
                }
            });
            builder.show();
            return true;
        }
    }

    private void openCamera() {
        try {
            File photoFile = new File(getExternalCacheDir(), "camera_photo_" + System.currentTimeMillis() + ".jpg");
            cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            startActivityForResult(intent, CAMERA_REQUEST_CODE);
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
            Toast.makeText(this, "Failed to open camera", Toast.LENGTH_SHORT).show();
            if (mUploadMessage != null) {
                mUploadMessage.onReceiveValue(null);
                mUploadMessage = null;
            }
        }
    }

    private void openFileManager() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(i, "File Chooser"), FILE_CHOOSER_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (null == mUploadMessage)
                return;
            Uri[] result = null;
            if (resultCode == RESULT_OK && intent != null) {
                try {
                    result = WebChromeClient.FileChooserParams.parseResult(resultCode, intent);
                } catch (Exception ignored) {}
                if (result == null) {
                    if (intent.getClipData() != null) {
                        int count = intent.getClipData().getItemCount();
                        result = new Uri[count];
                        for (int idx = 0; idx < count; idx++) {
                            result[idx] = intent.getClipData().getItemAt(idx).getUri();
                        }
                    } else if (intent.getData() != null) {
                        result = new Uri[] { intent.getData() };
                    } else if (intent.getDataString() != null) {
                        result = new Uri[] { Uri.parse(intent.getDataString()) };
                    }
                }
            }

            // The chosen files go to the page as copies in the app's own storage: same name, same
            // bytes, no conversion (see storage/UploadCopies for why a copy).
            ValueCallback<Uri[]> callback = mUploadMessage;
            mUploadMessage = null;
            if (result != null && result.length > 0) {
                UploadCopies.prepare(this, result, callback::onReceiveValue);
            } else {
                callback.onReceiveValue(result);
            }
        } else if (requestCode == CAMERA_REQUEST_CODE) {
            if (null == mUploadMessage)
                return;
            Uri[] result = null;
            if (resultCode == RESULT_OK && cameraImageUri != null) {
                result = new Uri[] { cameraImageUri };
            }
            mUploadMessage.onReceiveValue(result);
            mUploadMessage = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                WebView current = tabManager != null ? tabManager.currentWebView() : chatWebView;
                if (current != null && current.canGoBack()) {
                    current.goBack();
                } else {
                    finish();
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (tabManager != null) {
            tabManager.destroyAccountTabs();
        }
        if (chatWebView != null) {
            chatWebView.destroy();
        }
        super.onDestroy();
    }
}
