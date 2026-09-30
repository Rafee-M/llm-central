package org.llmcentral.app.downloads;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.webkit.URLUtilCompat;

import org.llmcentral.app.R;
import org.llmcentral.app.provider.ProviderSpec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;

/**
 * Saves files from the chat websites into the phone's Downloads folder (moved out of MainActivity in
 * round 6; PROJECT.md §5.1 "downloads/").
 *
 * <p><b>Two kinds of downloads:</b></p>
 * <ul>
 *   <li><b>Normal links</b> (https://…): Android's own {@link DownloadManager} fetches the file in the
 *       background and shows its progress notification. It gets the tab's login cookies and browser
 *       identity (user agent), because many files are only served to the signed-in account.</li>
 *   <li><b>Files made by the page</b> (e.g. a CSV or an exported chat): they exist only inside the web
 *       page, so the page hands them over as a {@code data:} text (see web/AppBridge and
 *       assets/js/blob_fetch.js). Here they are decoded and written <i>on a background thread</i>,
 *       so a large file never freezes the screen.</li>
 * </ul>
 * <p>Either way the user first sees a "Save file" box with a suggested name ({@link DownloadNames}).</p>
 *
 * <p><b>Permissions:</b> Android 7–9 need the storage permission to write into Downloads, and Android
 * 13+ asks before showing notifications. If a permission is missing, the download waits (as one
 * "pending" action) until the user answers ({@link #onPermissionResult}).</p>
 */
public final class Downloader {

    private static final String TAG = "Downloader";

    /** Request code for the permission question; MainActivity passes the answer back. */
    public static final int PERMISSION_REQUEST_CODE = 456;

    /**
     * Largest file the page may hand over (as {@code data:} text, about 4 characters per 3 bytes):
     * ~75 MB. Decoding needs the whole file in memory twice, so bigger ones are refused with a message.
     */
    static final int MAX_DATA_URL_CHARS = 100_000_000;

    private final Activity activity;
    private final Executor background;
    // A download waiting for the permission answer (only the newest one is kept).
    private Runnable waitingForPermission;

    /**
     * @param background a background thread for decoding and writing files (MainActivity's shared one)
     */
    public Downloader(Activity activity, Executor background) {
        this.activity = activity;
        this.background = background;
    }

    /**
     * A normal https download.
     *
     * @param cookies the tab's own cookie jar (each account has its own, see web/Profiles)
     */
    public void download(ProviderSpec provider, CookieManager cookies, String url, String userAgent,
                         String contentDisposition, String mimetype) {
        whenAllowed(() -> askName(provider, "Download File", "Download",
                nameFromWeb(url, contentDisposition, mimetype), mimetype,
                (name, type) -> enqueue(provider, cookies, url, userAgent, name, type)));
    }

    /**
     * A file the page handed over as a {@code data:} URL ("data:text/csv;base64,…").
     *
     * @param pageUrl the page it came from (only used to guess a name)
     */
    public void saveDataUrl(ProviderSpec provider, String dataUrl, String mimetype,
                            String contentDisposition, String pageUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:")) {
            return;
        }
        if (dataUrl.length() > MAX_DATA_URL_CHARS) {
            Toast.makeText(activity, R.string.download_too_large, Toast.LENGTH_LONG).show();
            return;
        }
        whenAllowed(() -> askName(provider, "Save File", "Save",
                nameFromWeb(pageUrl, contentDisposition, mimetype), mimetype,
                (name, type) -> background.execute(() -> writeDataUrl(provider, dataUrl, name, type))));
    }

    /** Pass on MainActivity's onRequestPermissionsResult for {@link #PERMISSION_REQUEST_CODE}. */
    public void onPermissionResult(String[] permissions, int[] grantResults) {
        boolean writeGranted = true; // only asked for on Android 7–9
        for (int i = 0; i < permissions.length && i < grantResults.length; i++) {
            if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissions[i])) {
                writeGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
            }
        }
        Runnable waiting = waitingForPermission;
        waitingForPermission = null;
        if (waiting == null) {
            return;
        }
        if (writeGranted) {
            // Not having the notification permission (Android 13+) only hides the "done" notification.
            waiting.run();
        } else {
            Toast.makeText(activity, "Permission denied. Cannot download file.", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ steps

    /** Runs the action now if the permissions are there, else asks and runs it after the answer. */
    private void whenAllowed(Runnable action) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            waitingForPermission = action;
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            waitingForPermission = action;
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
            return;
        }
        action.run();
    }

    private interface OnName {
        void onName(String fileName, String mimetype);
    }

    /** The "Save file" box: an editable name, the part before the extension pre-selected. */
    private void askName(ProviderSpec provider, String title, String button, String webName,
                         String mimetype, OnName onName) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
        String suggested = DownloadNames.suggested(provider, webName, timestamp);

        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText(suggested);
        int dotIndex = suggested.lastIndexOf('.');
        if (dotIndex > 0) {
            input.setSelection(0, dotIndex);
        } else {
            input.selectAll();
        }
        FrameLayout container = new FrameLayout(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        float density = activity.getResources().getDisplayMetrics().density;
        params.leftMargin = (int) (20 * density);
        params.rightMargin = (int) (20 * density);
        params.topMargin = (int) (8 * density);
        params.bottomMargin = (int) (8 * density);
        container.addView(input, params);

        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(container)
                .setPositiveButton(button, (dialog, which) -> {
                    String name = DownloadNames.chosen(provider, suggested, input.getText().toString());
                    onName.onName(name, DownloadNames.mimeType(name, mimetype));
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                .show();
    }

    /** Hands a normal download to Android's DownloadManager. */
    private void enqueue(ProviderSpec provider, CookieManager cookies, String url, String userAgent,
                         String fileName, String mimetype) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            // Send the login cookies of the account that started the download (its own cookie jar).
            String cookieHeader = (cookies != null ? cookies : CookieManager.getInstance()).getCookie(url);
            if (cookieHeader != null) {
                request.addRequestHeader("Cookie", cookieHeader);
            }
            // The same browser identity the page uses: some file servers check it.
            if (userAgent != null && !userAgent.isEmpty()) {
                request.addRequestHeader("User-Agent", userAgent);
            }
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (fileName.endsWith(".md")) {
                request.setMimeType(mimetype);
            }
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    DownloadNames.folder(provider) + File.separator + fileName);
            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(activity, activity.getString(R.string.download) + " " + fileName, Toast.LENGTH_SHORT).show();
            }
        } catch (RuntimeException e) {
            // e.g. an address DownloadManager can't handle
            Log.e(TAG, "Download failed", e);
            Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** Background thread: decodes the data: URL and writes the file into Downloads/<folder>/. */
    private void writeDataUrl(ProviderSpec provider, String dataUrl, String fileName, String mimetype) {
        Uri fileUri = null;
        try {
            // "data:<type>;base64,<data>": the file is everything after the first comma.
            byte[] data = Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), Base64.DEFAULT);
            String folder = DownloadNames.folder(provider);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimetype);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + folder);
                Uri uri = activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
                        if (out != null) {
                            out.write(data);
                            fileUri = uri;
                        }
                    }
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folder);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File file = new File(dir, fileName);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(data);
                    fileUri = Uri.fromFile(file);
                }
                MediaScannerConnection.scanFile(activity, new String[]{file.getAbsolutePath()}, new String[]{mimetype}, null);
            }
        } catch (Exception | OutOfMemoryError e) {
            Log.e(TAG, "Saving the file failed", e);
        }
        final Uri saved = fileUri;
        activity.runOnUiThread(() -> {
            if (saved == null) {
                Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(activity, activity.getString(R.string.download) + " " + fileName, Toast.LENGTH_SHORT).show();
            showNotification(fileName, mimetype, saved);
        });
    }

    /** "Download completed" notification; tapping it opens the file. */
    private void showNotification(String fileName, String mimeType, Uri uri) {
        NotificationManager manager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        String channelId = "duck_downloads"; // kept from duckAssist, so existing notification settings stay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                    new NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType != null && !mimeType.isEmpty() ? mimeType : "*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(activity, 0, intent, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(activity, channelId)
                : new Notification.Builder(activity);
        builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(fileName)
                .setContentText("Download completed")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    /** The file name the website suggests (header "Content-Disposition", else the address). */
    private static String nameFromWeb(String url, String contentDisposition, String mimetype) {
        String name = URLUtilCompat.getFilenameFromContentDisposition(contentDisposition);
        if (name == null || name.isEmpty()) {
            name = URLUtilCompat.guessFileName(url, contentDisposition, mimetype);
        }
        return name;
    }
}
