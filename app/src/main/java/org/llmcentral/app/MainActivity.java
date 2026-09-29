package org.llmcentral.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.EditText;
import android.app.AlertDialog;
import android.content.DialogInterface;
import androidx.core.content.FileProvider;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.File;
import android.Manifest;
import android.graphics.pdf.PdfRenderer;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.ParcelFileDescriptor;
import java.io.InputStream;
import android.content.pm.PackageManager;
import android.webkit.PermissionRequest;

import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.os.StrictMode;
import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.webkit.URLUtilCompat;

import org.llmcentral.app.scripts.UserScriptMatcher;
import org.llmcentral.app.web.Scripts;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class MainActivity extends Activity {

    private WebView chatWebView;
    private float currentZoomLevel = 100f;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> mUploadMessage;
    private final static int FILE_CHOOSER_REQUEST_CODE = 1;
    private final static int CAMERA_REQUEST_CODE = 2;
    private final static int CAMERA_PERMISSION_REQUEST_CODE = 124;
    private Uri cameraImageUri = null;
    private final String TAG = "duckAssist";
    private final boolean restricted = false;
    private boolean pendingVoiceChat = false;
    private boolean pendingAutoFocus = false;
    private boolean pendingContinueLastChat = false;
    private Uri pendingSharedFileUri = null;
    private java.util.List<Uri> pendingSharedFileUris = null;
    private static boolean isSafeMode = false;
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

    private String pendingDownloadUrl;
    private String pendingDownloadUserAgent;
    private String pendingDownloadContentDisposition;
    private String pendingDownloadMimetype;
    private long pendingDownloadContentLength;

    private boolean isPendingBlob = false;
    private String pendingBlobData;
    private String pendingBlobMimetype;
    private String pendingBlobContentDisposition;
    private String pendingBlobCurrentUrl;

    private static final int DOWNLOAD_PERMISSION_REQUEST_CODE = 456;
    private String lastFetchedChatsJson = "{}";
    private android.app.Dialog chatsViewerDialog = null;
    private android.app.Dialog scriptsManagerDialog = null;
    private boolean isRequestingViewer = false;

    private void clearCacheData() {
        if (chatWebView != null) {
            chatWebView.clearCache(true);
        }
        Log.d(TAG, "Cache cleared.");
    }

    private Uri saveUriToTempFile(Uri uri, String extension) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return null;
            File tempFile = new File(getCacheDir(), "shared_file_" + System.currentTimeMillis() + extension);
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
            fos.close();
            is.close();
            return Uri.fromFile(tempFile);
        } catch (Exception e) {
            Log.e(TAG, "Error saving shared file to temp file", e);
            return null;
        }
    }

    protected void setActivityTheme() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setTheme(android.R.style.Theme_DeviceDefault_DayNight);
        }
    }

    protected int getLayoutResourceId() {
        return R.layout.activity_main;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StrictMode.VmPolicy.Builder StrictBuilder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(StrictBuilder.build());

        setActivityTheme();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
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

        progressBar = findViewById(R.id.progressBar);
        chatWebView = findViewById(R.id.chatWebView);

        WebSettings webSettings = chatWebView.getSettings();
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

        SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String cached = ChatDatabaseHelper.getInstance(MainActivity.this).getCachedChatsJson();
                if (cached != null && !cached.equals("{}") && !cached.isEmpty()) {
                    lastFetchedChatsJson = cached;
                } else {
                    SharedPreferences p = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                    String oldPrefsJson = p.getString("cached_chats_json", null);
                    if (oldPrefsJson != null && !oldPrefsJson.equals("{}") && !oldPrefsJson.isEmpty()) {
                        lastFetchedChatsJson = oldPrefsJson;
                        ChatDatabaseHelper.getInstance(MainActivity.this).saveCachedChatsJson(oldPrefsJson);
                    } else {
                        lastFetchedChatsJson = "{}";
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to load cached_chats_json in background", t);
                lastFetchedChatsJson = "{}";
            }
        });
        int savedZoom = prefs.getInt("text_zoom", 100);
        currentZoomLevel = (float) savedZoom;
        webSettings.setTextZoom(savedZoom);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(chatWebView, false);

        chatWebView.setWebViewClient(new MyWebViewClient());
        chatWebView.setWebChromeClient(new MyWebChromeClient());

        chatWebView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (url.startsWith("blob:")) {
                // Values that come from the web page (URL, file name, type) are turned into safe JS string
                // literals with JSONObject.quote(), which escapes quotes, backslashes and line breaks.
                // Pasting them in raw (as before) would let a crafted file name inject its own JavaScript.
                String jsUrl = org.json.JSONObject.quote(url);
                String jsCD = org.json.JSONObject.quote(contentDisposition != null ? contentDisposition : "");
                String jsMime = org.json.JSONObject.quote(mimetype != null ? mimetype : "");
                chatWebView.evaluateJavascript(
                        "(function() {" +
                                "  var url = " + jsUrl + ";" +
                                "  var blob = window.blobMap ? window.blobMap.get(url) : null;" +
                                "  console.log('Download request for: ' + url + ' (Map found: ' + (window.blobMap !== undefined) + ')');"
                                +
                                "  if (blob) {" +
                                "    var reader = new FileReader();" +
                                "    reader.onloadend = function() {" +
                                "      Android.processBlob(reader.result, blob.type, " + jsCD
                                + ", window.location.href);" +
                                "    };" +
                                "    reader.readAsDataURL(blob);" +
                                "  } else {" +
                                "    console.warn('Blob not found in map, trying XHR fallback...');" +
                                "    var xhr = new XMLHttpRequest();" +
                                "    xhr.open('GET', url, true);" +
                                "    xhr.responseType = 'blob';" +
                                "    xhr.onload = function() {" +
                                "      if (this.status == 200) {" +
                                "        var reader = new FileReader();" +
                                "        reader.readAsDataURL(this.response);" +
                                "        reader.onloadend = function() {" +
                                "          Android.processBlob(reader.result, " + jsMime + ", " + jsCD
                                + ", window.location.href);" +
                                "        };" +
                                "      }" +
                                "    };" +
                                "    xhr.onerror = function() { console.error('Blob fetch failed: CSP or not found'); };"
                                +
                                "    xhr.send();" +
                                "  }" +
                                "})();",
                        null);
                return;
            } else if (url.startsWith("data:")) {
                processBlob(url, mimetype, contentDisposition, url);
                return;
            }
            if (checkDownloadPermissions()) {
                promptAndStartStandardDownload(url, userAgent, contentDisposition, mimetype, contentLength);
            } else {
                pendingDownloadUrl = url;
                pendingDownloadUserAgent = userAgent;
                pendingDownloadContentDisposition = contentDisposition;
                pendingDownloadMimetype = mimetype;
                pendingDownloadContentLength = contentLength;
                isPendingBlob = false;
            }
        });

        chatWebView.addJavascriptInterface(this, "Android");

        ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scale = detector.getScaleFactor();
                currentZoomLevel = currentZoomLevel * scale;
                // Clamp text zoom between 50% and 300%
                currentZoomLevel = Math.max(50f, Math.min(currentZoomLevel, 300f));
                int newZoom = Math.round(currentZoomLevel);
                chatWebView.getSettings().setTextZoom(newZoom);
                
                SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                prefs.edit().putInt("text_zoom", newZoom).apply();
                return true;
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            scaleGestureDetector.setQuickScaleEnabled(false);
        }

        chatWebView.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isImageZoomActive) {
                    return false;
                }
                scaleGestureDetector.onTouchEvent(event);
                return event.getPointerCount() > 1 || scaleGestureDetector.isInProgress();
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, 123);
            }
        }

        boolean restored = false;
        if (savedInstanceState != null) {
            if (chatWebView.restoreState(savedInstanceState) != null) {
                restored = true;
            }
        }
        if (!restored) {
            handleIntent(getIntent(), false);
        }
    }

    @JavascriptInterface
    public void showToast(final String message) {
        runOnUiThread(() -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @JavascriptInterface
    public void showSoftKeyboard() {
        runOnUiThread(() -> {
            chatWebView.requestFocus();
            android.view.inputmethod.InputMethodManager imm = 
                (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(chatWebView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    @JavascriptInterface
    public void requestWebViewFocus() {
        runOnUiThread(() -> {
            chatWebView.requestFocus();
            chatWebView.requestFocusFromTouch();
        });
    }

    @JavascriptInterface
    public void triggerShortcut() {
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "Triggering Ctrl+Shift+O...", Toast.LENGTH_SHORT).show();
            if (chatWebView != null) {
                chatWebView.evaluateJavascript(js(Scripts.AUTO_FOCUS), null);
                long now = android.os.SystemClock.uptimeMillis();
                int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON;
                chatWebView.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_O, 0, meta));
                chatWebView.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_O, 0, meta));
            }
        });
    }

    @JavascriptInterface
    public void triggerShortcutNative() {
        runOnUiThread(() -> {
            if (chatWebView != null) {
                chatWebView.requestFocus();
                long now = android.os.SystemClock.uptimeMillis();
                int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON;
                chatWebView.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_O, 0, meta));
                chatWebView.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_O, 0, meta));
            }
        });
    }

    @JavascriptInterface
    public void setImageZoomActive(boolean active) {
        runOnUiThread(() -> {
            isImageZoomActive = active;
        });
    }

    @JavascriptInterface
    public String getChatsJson() {
        if (lastFetchedChatsJson == null || lastFetchedChatsJson.equals("{}") || lastFetchedChatsJson.isEmpty()) {
            lastFetchedChatsJson = ChatDatabaseHelper.getInstance(MainActivity.this).getCachedChatsJson();
            if (lastFetchedChatsJson == null || lastFetchedChatsJson.equals("{}") || lastFetchedChatsJson.isEmpty()) {
                SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                lastFetchedChatsJson = prefs.getString("cached_chats_json", "{}");
            }
        }
        return lastFetchedChatsJson;
    }

    @JavascriptInterface
    public void dismissViewer() {
        runOnUiThread(() -> {
            if (chatsViewerDialog != null && chatsViewerDialog.isShowing()) {
                chatsViewerDialog.dismiss();
            } else {
                chatWebView.loadUrl("https://duck.ai/");
            }
        });
    }

    @JavascriptInterface
    public void onContinueLastChatSuccess() {
        runOnUiThread(() -> pendingContinueLastChat = false);
    }

    @JavascriptInterface
    public void setSwipeEnabled(final boolean enabled) {
        // No-op
    }

    @JavascriptInterface
    public void copyToClipboard(final String text) {
        runOnUiThread(() -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Copied Text", text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, R.string.url_copied, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @JavascriptInterface
    public void processBlob(String base64Data, String mimetype, String contentDisposition, String currentUrl) {
        if (checkDownloadPermissions()) {
            promptAndSaveBlob(base64Data, mimetype, contentDisposition, currentUrl);
        } else {
            isPendingBlob = true;
            pendingBlobData = base64Data;
            pendingBlobMimetype = mimetype;
            pendingBlobContentDisposition = contentDisposition;
            pendingBlobCurrentUrl = currentUrl;
        }
    }

    @JavascriptInterface
    public void showSettingsDialog() {
        runOnUiThread(() -> {
            android.app.Dialog dialog = new android.app.Dialog(MainActivity.this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            
            WebView webView = new WebView(MainActivity.this);
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
                    } catch (Exception e) {
                        Log.e(TAG, "Error generating settings JSON", e);
                    }
                    return obj.toString();
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
                             .apply();
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
                             .apply();
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

                @JavascriptInterface
                public void openChatsViewer() {
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        fetchChatsAndShowViewer();
                    });
                }

                @JavascriptInterface
                public void openScriptsManager() {
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        showScriptsManagerDialog();
                    });
                }
            }, "AndroidSettings");

            webView.loadUrl("file:///android_asset/settings.html");
            
            dialog.setContentView(webView);
            dialog.show();
            
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            }
        });
    }

    @JavascriptInterface
    public void onChatsFetched(String jsonStr) {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
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
                        
                        // Merge blobMap
                        org.json.JSONObject oldBlobMap = oldObj.optJSONObject("blobMap");
                        org.json.JSONObject newBlobMap = newObj.optJSONObject("blobMap");
                        if (oldBlobMap != null) {
                            if (newBlobMap == null) {
                                newObj.put("blobMap", oldBlobMap);
                            } else {
                                java.util.Iterator<String> keys = oldBlobMap.keys();
                                while (keys.hasNext()) {
                                    String k = keys.next();
                                    if (!newBlobMap.has(k)) {
                                        newBlobMap.put(k, oldBlobMap.get(k));
                                    }
                                }
                            }
                        }

                        // Merge domImages
                        org.json.JSONArray oldDomImgs = oldObj.optJSONArray("domImages");
                        org.json.JSONArray newDomImgs = newObj.optJSONArray("domImages");
                        if (oldDomImgs != null && oldDomImgs.length() > 0) {
                            if (newDomImgs == null) {
                                newObj.put("domImages", oldDomImgs);
                            } else {
                                java.util.Set<String> existingSrcs = new java.util.HashSet<>();
                                for (int i = 0; i < newDomImgs.length(); i++) {
                                    org.json.JSONObject item = newDomImgs.optJSONObject(i);
                                    if (item != null && item.has("src")) {
                                        existingSrcs.add(item.optString("src"));
                                    }
                                }
                                for (int i = 0; i < oldDomImgs.length(); i++) {
                                    org.json.JSONObject item = oldDomImgs.optJSONObject(i);
                                    if (item != null) {
                                        String src = item.optString("src");
                                        if (src == null || !existingSrcs.contains(src)) {
                                            newDomImgs.put(item);
                                            if (src != null) existingSrcs.add(src);
                                        }
                                    }
                                }
                            }
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
            lastFetchedChatsJson = finalJsonStr;
            ChatDatabaseHelper.getInstance(MainActivity.this).saveCachedChatsJson(finalJsonStr);
            if (isRequestingViewer || (chatsViewerDialog != null && chatsViewerDialog.isShowing())) {
                isRequestingViewer = false;
                runOnUiThread(this::showChatsViewerDialog);
            }
        });
    }

    private void fetchChatsAndShowViewer() {
        runOnUiThread(() -> {
            isRequestingViewer = true;
            String currentUrl = chatWebView.getUrl();
            if (currentUrl != null && (currentUrl.startsWith("https://duck.ai") || currentUrl.startsWith("https://duckduckgo.com"))) {
                chatWebView.evaluateJavascript(js(Scripts.DUMP_CHATS), null);
            } else {
                showChatsViewerDialog();
            }
        });
    }

    private void showChatsViewerDialog() {
        runOnUiThread(() -> {
            if (chatsViewerDialog != null && chatsViewerDialog.isShowing()) {
                chatsViewerDialog.dismiss();
            }
            chatsViewerDialog = new android.app.Dialog(MainActivity.this);
            chatsViewerDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            
            WebView webView = new WebView(MainActivity.this);
            WebSettings ws = webView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setAllowFileAccess(false);
            ws.setAllowContentAccess(false);
            
            webView.addJavascriptInterface(new Object() {
                @JavascriptInterface
                public String getChatsJson() {
                    return lastFetchedChatsJson;
                }
                
                @JavascriptInterface
                public void dismissViewer() {
                    runOnUiThread(() -> {
                        if (chatsViewerDialog != null) {
                            chatsViewerDialog.dismiss();
                        }
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
                    MainActivity.this.processBlob(base64Data, mimetype, contentDisposition, currentUrl);
                }
            }, "AndroidChatsViewer");
            
            webView.loadUrl("file:///android_asset/chats_viewer.html");
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
            script.put("id", "default_duck_clean");
            script.put("name", "Duck.ai Ultra Clean Unified");
            script.put("author", "iJahangard (https://github.com/iJahangard)");
            script.put("description", "Unified, clean split-screen with centered items and coordinated floating buttons. Created by iJahangard.");
            script.put("match", "*://*.duck.ai/*, *://*.duckduckgo.com/*");
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

    private String formatMarkdownFilename(String filename, String mimetype) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());

        if (filename == null || filename.isEmpty()) {
            return "Duck_AI_Chat_" + timestamp + ".md";
        }

        // Change .txt, .text, or .bin extensions to .md
        if (filename.toLowerCase().endsWith(".txt")) {
            filename = filename.substring(0, filename.length() - 4) + ".md";
        } else if (filename.toLowerCase().endsWith(".text")) {
            filename = filename.substring(0, filename.length() - 5) + ".md";
        } else if (filename.toLowerCase().endsWith(".bin")) {
            filename = filename.substring(0, filename.length() - 4) + ".md";
        }

        // If no extension exists, append .md
        if (!filename.contains(".")) {
            filename += ".md";
        }

        int dotIndex = filename.lastIndexOf('.');
        String baseName = (dotIndex > 0) ? filename.substring(0, dotIndex).trim() : filename;
        String ext = (dotIndex > 0) ? filename.substring(dotIndex) : "";

        // Ensure timestamp is present on markdown exports and generic names
        if (baseName.equalsIgnoreCase("download") || baseName.equalsIgnoreCase("chat") || baseName.equalsIgnoreCase("export") || baseName.equalsIgnoreCase("duckduckgo_chat") || baseName.equalsIgnoreCase("duck_ai_chat")) {
            filename = "Duck_AI_Chat_" + timestamp + ext;
        } else if (ext.equalsIgnoreCase(".md") && !baseName.matches(".*_\\d{8}_\\d{6}$")) {
            filename = baseName + "_" + timestamp + ext;
        }

        return filename;
    }

    private void promptAndSaveBlob(String base64Data, String mimetype, String contentDisposition, String currentUrl) {
        runOnUiThread(() -> {
            String filename = URLUtilCompat.getFilenameFromContentDisposition(contentDisposition);
            if (filename == null || filename.isEmpty()) {
                filename = URLUtilCompat.guessFileName(currentUrl, contentDisposition, mimetype);
            }
            filename = formatMarkdownFilename(filename, mimetype);
            final String defaultFilename = filename;
            final String finalMimetype = defaultFilename.endsWith(".md") ? "text/markdown" : mimetype;

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Save File");

            final EditText input = new EditText(MainActivity.this);
            input.setSingleLine(true);
            input.setText(defaultFilename);
            int dotIndex = defaultFilename.lastIndexOf('.');
            if (dotIndex > 0) {
                input.setSelection(0, dotIndex);
            } else {
                input.selectAll();
            }

            android.widget.FrameLayout container = new android.widget.FrameLayout(MainActivity.this);
            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = (int) (20 * getResources().getDisplayMetrics().density);
            params.rightMargin = (int) (20 * getResources().getDisplayMetrics().density);
            params.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
            params.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
            input.setLayoutParams(params);
            container.addView(input);
            builder.setView(container);

            builder.setPositiveButton("Save", (dialog, which) -> {
                String chosenName = input.getText().toString().trim();
                if (chosenName.isEmpty()) {
                    chosenName = defaultFilename;
                }
                if (defaultFilename.endsWith(".md") && !chosenName.toLowerCase().endsWith(".md")) {
                    chosenName += ".md";
                }
                executeSaveBlobToFile(base64Data, finalMimetype, chosenName);
            });
            builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
            builder.show();
        });
    }

    private void executeSaveBlobToFile(String base64Data, String mimetype, String filename) {
        if (base64Data.contains(",")) {
            base64Data = base64Data.split(",")[1];
        }

        if (filename.endsWith(".md")) {
            mimetype = "text/markdown";
        }

        final String finalFilename = filename;
        final String finalMimetype = mimetype;
        try {
            Uri fileUri = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimetype);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "duck.ai");

                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                        byte[] data = Base64.decode(base64Data, Base64.DEFAULT);
                        outputStream.write(data);
                        fileUri = uri;
                    }
                }
            } else {
                File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "duck.ai");
                if (!path.exists()) {
                    path.mkdirs();
                }
                File file = new File(path, filename);
                try (FileOutputStream os = new FileOutputStream(file)) {
                    byte[] data = Base64.decode(base64Data, Base64.DEFAULT);
                    os.write(data);
                    fileUri = Uri.fromFile(file);
                }
                MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, new String[]{mimetype}, null);
            }

            if (fileUri != null) {
                final Uri finalUri = fileUri;
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, getString(R.string.download) + " " + finalFilename, Toast.LENGTH_SHORT).show();
                    showDownloadNotification(finalFilename, finalMimetype, finalUri);
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Blob download failed", e);
        }
    }

    private void promptAndStartStandardDownload(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
        runOnUiThread(() -> {
            String filename = URLUtilCompat.getFilenameFromContentDisposition(contentDisposition);
            if (filename == null || filename.isEmpty()) {
                filename = URLUtilCompat.guessFileName(url, contentDisposition, mimetype);
            }
            filename = formatMarkdownFilename(filename, mimetype);
            final String defaultFilename = filename;
            final String finalMimetype = defaultFilename.endsWith(".md") ? "text/markdown" : mimetype;

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Download File");

            final EditText input = new EditText(MainActivity.this);
            input.setSingleLine(true);
            input.setText(defaultFilename);
            int dotIndex = defaultFilename.lastIndexOf('.');
            if (dotIndex > 0) {
                input.setSelection(0, dotIndex);
            } else {
                input.selectAll();
            }

            android.widget.FrameLayout container = new android.widget.FrameLayout(MainActivity.this);
            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = (int) (20 * getResources().getDisplayMetrics().density);
            params.rightMargin = (int) (20 * getResources().getDisplayMetrics().density);
            params.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
            params.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
            input.setLayoutParams(params);
            container.addView(input);
            builder.setView(container);

            builder.setPositiveButton("Download", (dialog, which) -> {
                String chosenName = input.getText().toString().trim();
                if (chosenName.isEmpty()) {
                    chosenName = defaultFilename;
                }
                if (defaultFilename.endsWith(".md") && !chosenName.toLowerCase().endsWith(".md")) {
                    chosenName += ".md";
                }
                executeStandardDownload(url, userAgent, finalMimetype, chosenName);
            });
            builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
            builder.show();
        });
    }

    private void executeStandardDownload(String url, String userAgent, String mimetype, String filename) {
        Uri source = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(source);
        request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        if (filename.endsWith(".md")) {
            mimetype = "text/markdown";
            request.setMimeType(mimetype);
        }

        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "duck.ai" + File.separator + filename);
        Toast.makeText(this, getString(R.string.download) + " " + filename, Toast.LENGTH_SHORT).show();
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (dm != null)
            dm.enqueue(request);
    }

    private boolean checkDownloadPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, DOWNLOAD_PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, DOWNLOAD_PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    private void showDownloadNotification(String filename, String mimeType, Uri uri) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "duck_downloads";
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType != null ? mimeType : "*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, channelId);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(filename)
                .setContentText("Download completed")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == DOWNLOAD_PERMISSION_REQUEST_CODE) {
            boolean writeGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissions[i])) {
                    writeGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                }
            }
            if (writeGranted) {
                if (isPendingBlob) {
                    if (pendingBlobData != null) {
                        promptAndSaveBlob(pendingBlobData, pendingBlobMimetype, pendingBlobContentDisposition, pendingBlobCurrentUrl);
                    }
                } else {
                    if (pendingDownloadUrl != null) {
                        promptAndStartStandardDownload(pendingDownloadUrl, pendingDownloadUserAgent, pendingDownloadContentDisposition, pendingDownloadMimetype, pendingDownloadContentLength);
                    }
                }
            } else {
                Toast.makeText(this, "Permission denied. Cannot download file.", Toast.LENGTH_SHORT).show();
            }
            clearPendingDownload();
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

    private void clearPendingDownload() {
        pendingDownloadUrl = null;
        pendingDownloadUserAgent = null;
        pendingDownloadContentDisposition = null;
        pendingDownloadMimetype = null;
        pendingDownloadContentLength = 0;
        isPendingBlob = false;
        pendingBlobData = null;
        pendingBlobMimetype = null;
        pendingBlobContentDisposition = null;
        pendingBlobCurrentUrl = null;
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
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        runOnUiThread(() -> {
            if (chatWebView != null && chatWebView.getUrl() != null && chatWebView.getUrl().startsWith("https://duck")) {
                chatWebView.evaluateJavascript(js(Scripts.DUMP_CHATS), null);
            }
        });
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
        Log.d(TAG, "handleIntent: action = " + action + ", type = " + type + ", data = " + (data != null ? data.toString() : "null"));
        if (intent.getExtras() != null) {
            for (String key : intent.getExtras().keySet()) {
                Log.d(TAG, "  extra: " + key + " = " + intent.getExtras().get(key));
            }
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
                    try {
                        org.json.JSONObject handoffObj = new org.json.JSONObject();
                        handoffObj.put("aiChatPrompt", query);
                        handoffObj.put("aiChatAutoPrompt", false);
                        String handoffJson = handoffObj.toString();
                        
                        Uri.Builder builder = Uri.parse("https://duck.ai/chat").buildUpon()
                                .appendQueryParameter("q", query)
                                .appendQueryParameter("handoff", handoffJson);
                        chatWebView.loadUrl(builder.build().toString());
                    } catch (org.json.JSONException e) {
                        Log.e(TAG, "Error building handoff JSON", e);
                        chatWebView.loadUrl("https://duck.ai/chat?q=" + Uri.encode(query));
                    }
                } else {
                    chatWebView.loadUrl(data.toString());
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
                String ext = DocConverter.getFileExtension(this, streamUri, type);
                if ("pdf".equalsIgnoreCase(ext) || "application/pdf".equals(type) || (type != null && type.contains("pdf"))) {
                    String filename = DocConverter.getFileName(this, streamUri);
                    DocConverter.ProgressDialogController progress = DocConverter.showProgressDialog(this, "Checking " + filename + "...", null);
                    DocConverter.getInstance(this).processNativePdf(streamUri, new DocConverter.DocumentProcessingCallback() {
                        @Override
                        public void onStatusUpdate(String status) {
                            progress.setMessage(status);
                        }

                        @Override
                        public void onTextReady(String text) {}

                        @Override
                        public void onPdfReady(java.util.List<Uri> pdfUris, String originalName) {
                            progress.dismiss();
                            pendingSharedFileUris = pdfUris;
                            pendingSharedFileUri = (pdfUris != null && !pdfUris.isEmpty()) ? pdfUris.get(0) : null;
                            if (pdfUris != null && pdfUris.size() > 1) {
                                showCustomBanner("PDF split into " + pdfUris.size() + " parts (15 pages max each)! Tap 📎 to attach");
                            } else {
                                showCustomBanner("Tap 📎 to attach the shared file");
                            }
                            SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                            boolean continueLastChat = prefs.getBoolean("continue_last_chat", false);
                            if (continueLastChat) {
                                pendingContinueLastChat = true;
                            }
                            String docSuffix = prefs.getString("shared_doc_suffix", "");
                            if (docSuffix != null && !docSuffix.trim().isEmpty()) {
                                loadDuckChatPrompt(docSuffix);
                            } else {
                                chatWebView.loadUrl("https://duck.ai/chat");
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            progress.dismiss();
                            pendingSharedFileUri = saveUriToTempFile(streamUri, ".pdf");
                        }
                    });
                    return;
                } else if (DocConverter.isSupportedDoc(this, streamUri, type) || DocConverter.isPlainTextDoc(this, streamUri, type)) {
                    String filename = DocConverter.getFileName(this, streamUri);
                    DocConverter.ProgressDialogController progress = DocConverter.showProgressDialog(this, "Processing " + filename + "...", null);
                    DocConverter.getInstance(this).processDocument(streamUri, type, new DocConverter.DocumentProcessingCallback() {
                        @Override
                        public void onStatusUpdate(String status) {
                            progress.setMessage(status);
                        }

                        @Override
                        public void onTextReady(String text) {
                            progress.dismiss();
                            loadDuckChatPrompt(text);
                        }

                        @Override
                        public void onPdfReady(java.util.List<Uri> pdfUris, String originalName) {
                            progress.dismiss();
                            pendingSharedFileUris = pdfUris;
                            pendingSharedFileUri = (pdfUris != null && !pdfUris.isEmpty()) ? pdfUris.get(0) : null;
                            if (pdfUris != null && pdfUris.size() > 1) {
                                showCustomBanner("Document split into " + pdfUris.size() + " parts (15 pages max each)! Tap 📎 to attach");
                            } else {
                                showCustomBanner("Tap 📎 to attach the converted document");
                            }
                            SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                            String docSuffix = prefs.getString("shared_doc_suffix", "");
                            if (docSuffix != null && !docSuffix.trim().isEmpty()) {
                                loadDuckChatPrompt(docSuffix);
                            } else {
                                chatWebView.loadUrl("https://duck.ai/chat");
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            progress.dismiss();
                            Toast.makeText(MainActivity.this, "Failed: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                } else {
                    if (type != null && type.startsWith("image/")) {
                        String imgExt = ".jpg";
                        if (type.contains("png")) imgExt = ".png";
                        else if (type.contains("webp")) imgExt = ".webp";
                        pendingSharedFileUri = saveUriToTempFile(streamUri, imgExt);
                    }
                }
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
                SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                boolean continueLastChat = prefs.getBoolean("continue_last_chat", false);
                if (continueLastChat) {
                    pendingContinueLastChat = true;
                }
                String docSuffix = prefs.getString("shared_doc_suffix", "");
                if (docSuffix != null && !docSuffix.trim().isEmpty()) {
                    loadDuckChatPrompt(docSuffix);
                } else {
                    chatWebView.loadUrl("https://duck.ai/chat");
                }
            } else if (sharedText != null) {
                loadDuckChatPrompt(sharedText);
            } else {
                chatWebView.loadUrl("https://duck.ai/");
            }
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
            boolean autoFocus = prefs.getBoolean("auto_focus_keyboard", false);
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

    private void loadDuckChatPrompt(String promptText) {
        if (promptText == null || promptText.trim().isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
        boolean continueLastChat = prefs.getBoolean("continue_last_chat", false);
        if (continueLastChat) {
            pendingContinueLastChat = true;
        }
        String suffix = prefs.getString("shared_doc_suffix", "");
        if (suffix == null || suffix.trim().isEmpty()) {
            suffix = prefs.getString("ask_duck_suffix", "");
        }
        String fullPrompt = promptText;
        if (suffix != null && !suffix.trim().isEmpty()) {
            fullPrompt = promptText + "\n\n" + suffix;
        }

        try {
            org.json.JSONObject handoffObj = new org.json.JSONObject();
            handoffObj.put("aiChatPrompt", fullPrompt);
            handoffObj.put("aiChatAutoPrompt", false);
            String handoffJson = handoffObj.toString();

            Uri.Builder builder = Uri.parse("https://duck.ai/chat").buildUpon()
                    .appendQueryParameter("q", fullPrompt)
                    .appendQueryParameter("handoff", handoffJson);
            chatWebView.loadUrl(builder.build().toString());
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Error building handoff JSON", e);
            chatWebView.loadUrl("https://duck.ai/chat?q=" + Uri.encode(fullPrompt));
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
                        
                        try {
                            org.json.JSONObject handoffObj = new org.json.JSONObject();
                            handoffObj.put("aiChatPrompt", promptText);
                            handoffObj.put("aiChatAutoPrompt", true);
                            String handoffJson = handoffObj.toString();
                            
                            Uri.Builder builder = Uri.parse("https://duck.ai/chat").buildUpon()
                                    .appendQueryParameter("q", promptText)
                                    .appendQueryParameter("handoff", handoffJson);
                            chatWebView.loadUrl(builder.build().toString());
                        } catch (org.json.JSONException e) {
                            Log.e(TAG, "Error building handoff JSON", e);
                            chatWebView.loadUrl("https://duck.ai/chat?q=" + Uri.encode(promptText));
                        }
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

    private class MyWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url == null) return false;
            if (url.startsWith("blob:") || url.startsWith("data:") || url.startsWith("javascript:") || url.startsWith("file:")) {
                return false;
            }
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            
            // Allow duck.ai and duckduckgo.com links to load inside the app
            if (host != null && (host.endsWith("duck.ai") || host.endsWith("duckduckgo.com"))) {
                return false;
            }

            // Redirect all other external links to the default browser
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            } catch (Throwable t) {
                Log.e(TAG, "Error opening external link: " + url, t);
            }
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            hasInjectedProgressJs = false;
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
            if (isSafeMode) return;
            safeEvaluateJavascript(view, js(Scripts.BLOB_DOWNLOAD_PATCH));
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
            safeEvaluateJavascript(view, js(Scripts.SETTINGS_BUTTON));
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
                if (request.isForMainFrame()) {
                    String url = request.getUrl().toString();
                    if (url.contains("duck.ai") || url.contains("duckduckgo.com")) {
                        Log.d(TAG, "Connection failed for main frame, replacing with local chats: " + error.getDescription());
                        runOnUiThread(() -> {
                            view.loadUrl("file:///android_asset/chats_viewer.html");
                        });
                        return; // Prevent calling super, which displays the default "Webpage not available" error page
                    }
                }
            }
            super.onReceivedError(view, request, error);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            if (failingUrl.contains("duck.ai") || failingUrl.contains("duckduckgo.com")) {
                Log.d(TAG, "Connection failed (legacy), replacing with local chats: " + description);
                runOnUiThread(() -> {
                    view.loadUrl("file:///android_asset/chats_viewer.html");
                });
                return; // Prevent calling super
            }
            super.onReceivedError(view, errorCode, description, failingUrl);
        }
    }

    private class MyWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            if (isSafeMode) return;
            if (newProgress > 5 && !hasInjectedProgressJs) {
                hasInjectedProgressJs = true;
                safeEvaluateJavascript(view, js(Scripts.BLOB_DOWNLOAD_PATCH));
                safeEvaluateJavascript(view, js(Scripts.CLIPBOARD_PATCH));
                safeEvaluateJavascript(view, js(Scripts.SETTINGS_BUTTON));
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

        @Override
        public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
            Log.d(TAG, "JS Console: " + consoleMessage.message() + " (Line " + consoleMessage.lineNumber() + ")");
            return true;
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            MainActivity.this.runOnUiThread(() -> {
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                        request.grant(new String[] { resource });
                        return;
                    }
                }
                request.deny();
            });
        }

        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback,
                WebChromeClient.FileChooserParams fileChooserParams) {
            if (pendingSharedFileUris != null && !pendingSharedFileUris.isEmpty()) {
                filePathCallback.onReceiveValue(pendingSharedFileUris.toArray(new Uri[0]));
                pendingSharedFileUris = null;
                pendingSharedFileUri = null;
                return true;
            }
            if (pendingSharedFileUri != null) {
                filePathCallback.onReceiveValue(new Uri[] { pendingSharedFileUri });
                pendingSharedFileUri = null;
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

            if (result != null && result.length > 0 && result[0] != null) {
                Uri fileUri = result[0];
                String ext = DocConverter.getFileExtension(this, fileUri, null);
                if ("pdf".equalsIgnoreCase(ext)) {
                    DocConverter.ProgressDialogController progress = DocConverter.showProgressDialog(this, "Checking PDF pages...", null);
                    DocConverter.getInstance(this).processNativePdf(fileUri, new DocConverter.DocumentProcessingCallback() {
                        @Override
                        public void onStatusUpdate(String status) {
                            progress.setMessage(status);
                        }

                        @Override
                        public void onTextReady(String text) {}

                        @Override
                        public void onPdfReady(java.util.List<Uri> pdfUris, String originalName) {
                            progress.dismiss();
                            if (pdfUris != null && pdfUris.size() > 1) {
                                if (mUploadMessage != null) {
                                    mUploadMessage.onReceiveValue(null);
                                    mUploadMessage = null;
                                }
                                pendingSharedFileUris = pdfUris;
                                pendingSharedFileUri = pdfUris.get(0);
                                showCustomBanner("PDF split into " + pdfUris.size() + " parts (15 pages max each)! Tap 📎 to attach");
                            } else {
                                if (mUploadMessage != null) {
                                    mUploadMessage.onReceiveValue(new Uri[]{ fileUri });
                                    mUploadMessage = null;
                                }
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            progress.dismiss();
                            if (mUploadMessage != null) {
                                mUploadMessage.onReceiveValue(new Uri[]{ fileUri });
                                mUploadMessage = null;
                            }
                        }
                    });
                    return;
                }
                if (DocConverter.isSupportedDoc(this, fileUri, null) || DocConverter.isPlainTextDoc(this, fileUri, null)) {
                    mUploadMessage.onReceiveValue(null);
                    mUploadMessage = null;
                    String filename = DocConverter.getFileName(this, fileUri);
                    DocConverter.ProgressDialogController progress = DocConverter.showProgressDialog(this, "Processing " + filename + "...", null);
                    DocConverter.getInstance(this).processDocument(fileUri, null, new DocConverter.DocumentProcessingCallback() {
                        @Override
                        public void onStatusUpdate(String status) {
                            progress.setMessage(status);
                        }

                        @Override
                        public void onTextReady(String text) {
                            progress.dismiss();
                            loadDuckChatPrompt(text);
                        }

                        @Override
                        public void onPdfReady(java.util.List<Uri> pdfUris, String originalName) {
                            progress.dismiss();
                            pendingSharedFileUris = pdfUris;
                            pendingSharedFileUri = (pdfUris != null && !pdfUris.isEmpty()) ? pdfUris.get(0) : null;
                            if (pdfUris != null && pdfUris.size() > 1) {
                                showCustomBanner("Document split into " + pdfUris.size() + " parts (15 pages max each)! Tap 📎 to attach");
                            } else {
                                showCustomBanner("Document converted to PDF! Tap 📎 to attach it");
                            }
                            SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                            String docSuffix = prefs.getString("shared_doc_suffix", "");
                            if (docSuffix != null && !docSuffix.trim().isEmpty()) {
                                loadDuckChatPrompt(docSuffix);
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            progress.dismiss();
                            Toast.makeText(MainActivity.this, "Conversion failed: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }
            }

            mUploadMessage.onReceiveValue(result);
            mUploadMessage = null;
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
                if (chatWebView.canGoBack()) {
                    chatWebView.goBack();
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
        //clearCacheData();
        if (chatWebView != null) {
            chatWebView.destroy();
        }
        super.onDestroy();
    }
}
