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
 * name can be kept even when two files share it).</p>
 *
 * <p><b>When copies are deleted.</b> WebView doesn't tell the app when a page has finished reading
 * (uploading) a file, so a copy can't be deleted "right after the upload". Instead:</p>
 * <ul>
 *   <li>at every app start, all copies from earlier runs ({@link #cleanUpAtStart}): after a restart
 *       no page can still be reading them;</li>
 *   <li>whenever a file is attached, copies older than {@link #KEEP_MS} (1 hour): sites start
 *       uploading as soon as a file is attached;</li>
 *   <li>Settings → Clear cache: all of them.</li>
 * </ul>
 */
public final class UploadCopies {

    private static final String TAG = "UploadCopies";
    /** Folder inside the app's (internal) cache folder. */
    static final String DIR = "uploads";

    /** Copies older than this are deleted whenever a new file is attached (1 hour). */
    static final long KEEP_MS = 60L * 60 * 1000;

    private static final AtomicLong counter = new AtomicLong();
    // Start-up clean-up runs once per app process: the screen is rebuilt in the same process (e.g.
    // light/dark switch) or the assistant drawer opens, and copies made in this run must survive that.
    private static final java.util.concurrent.atomic.AtomicBoolean startCleanUpDone =
            new java.util.concurrent.atomic.AtomicBoolean();
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
            AppCache.deleteOldFolders(new File(app.getCacheDir(), DIR), System.currentTimeMillis() - KEEP_MS);
            Uri[] result = new Uri[files.length];
            for (int i = 0; i < files.length; i++) {
                result[i] = copy(app, files[i]);
            }
            main.post(() -> done.onDone(result));
        });
    }

    /**
     * App start: deletes the copies made before {@code startedAtMillis}, once per app process.
     * Run it on a background thread; take {@code startedAtMillis} when the app starts (not when the
     * job runs), so a file attached while the job waits in the queue isn't deleted.
     *
     * @return how many copies were deleted (0 if it already ran in this process)
     */
    public static int cleanUpAtStart(Context context, long startedAtMillis) {
        return cleanUpAtStart(new File(context.getCacheDir(), DIR), startedAtMillis);
    }

    static int cleanUpAtStart(File uploadsDir, long startedAtMillis) {
        if (!startCleanUpDone.compareAndSet(false, true)) {
            return 0;
        }
        return AppCache.deleteOldFolders(uploadsDir, startedAtMillis);
    }

    /** For tests: lets {@link #cleanUpAtStart} run again. */
    static void resetForTest() {
        startCleanUpDone.set(false);
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
