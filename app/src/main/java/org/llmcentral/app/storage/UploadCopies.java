package org.llmcentral.app.storage;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;

import org.llmcentral.app.downloads.DownloadNames;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Copies of the files the user attaches (shared to the app, or picked with the page's 📎 button),
 * handed to the page instead of the other app's file address. Same name, same bytes.
 *
 * <p><b>Why a copy:</b> a shared or picked file arrives as a {@code content://} address that belongs
 * to another app. WebView reads such a file straight from that address while uploading it, and just
 * before sending it checks that the file's "last modified" time hasn't changed since it was picked
 * (Chromium's {@code UploadFileElementReader}: otherwise {@code ERR_UPLOAD_FILE_CHANGED}). Chromium's
 * own comment in {@code File::GetInfo} says some Android addresses report the <i>current time</i> as
 * "last modified", which makes that check fail. On the phone this broke .zip uploads to Claude
 * ("The upload was interrupted…") while the same file worked in Firefox, and worked in the app after a
 * restart, when the page used its own stored copy. A file in the app's internal cache folder is an
 * ordinary file whose time stays put, so the check passes.</p>
 *
 * <p>Copies live in {@code cache/uploads/<number>/<original name>} (a folder each, so the original
 * name can be kept even when two files share it). {@link AppCache} deletes them after a day, and
 * Settings → Clear cache deletes them at once.</p>
 */
public final class UploadCopies {

    private static final String TAG = "UploadCopies";
    /** Folder inside the app's (internal) cache folder. */
    static final String DIR = "uploads";

    private static final AtomicLong counter = new AtomicLong();
    /**
     * Its own threads, not the app's shared background thread: picking a file sends the app to the
     * background, which starts duck.ai's offline-chat save on that thread, and the copy mustn't wait
     * behind it. A "cached" pool ends its idle threads after a minute, so nothing lingers.
     */
    private static final ExecutorService copier = Executors.newCachedThreadPool();

    /** Receives the files to give the page. */
    public interface Done {
        void onDone(Uri[] files);
    }

    private UploadCopies() {
        // Only static methods.
    }

    /**
     * Copies the files on a background thread (a big file takes a moment) and answers on the main
     * thread. A file that can't be copied is passed on unchanged, as before.
     */
    public static void prepare(Context context, Uri[] files, Done done) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        copier.execute(() -> {
            Uri[] result = new Uri[files.length];
            for (int i = 0; i < files.length; i++) {
                result[i] = copy(app, files[i]);
            }
            main.post(() -> done.onDone(result));
        });
    }

    /** @return a {@code file://} address of the copy, or {@code uri} itself if it isn't copied. */
    private static Uri copy(Context context, Uri uri) {
        if (uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            return uri; // already a plain file (e.g. one of ours)
        }
        File folder = new File(new File(context.getCacheDir(), DIR),
                System.currentTimeMillis() + "_" + counter.incrementAndGet());
        try {
            if (!folder.mkdirs()) {
                return uri;
            }
            // The other app's name for the file, made safe for a file name ("/" etc. become "_").
            File target = new File(folder, DownloadNames.clean(displayName(context, uri), "file"));
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(target)) {
                if (in == null) {
                    throw new java.io.IOException("no stream");
                }
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            return Uri.fromFile(target);
        } catch (Exception e) {
            Log.w(TAG, "Couldn't copy an attached file; passing the original address", e);
            AppCache.deleteFolder(folder);
            return uri;
        }
    }

    /** The name the other app gives the file (what WebView would have shown), or null. */
    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            Log.w(TAG, "No display name", e);
        }
        String last = uri.getLastPathSegment();
        return last != null && last.contains(".") ? last : null;
    }
}
