package org.llmcentral.app.storage;

import android.content.Context;

import java.io.File;
import java.util.Collections;
import java.util.Set;

/**
 * The app's cache folders (round 6): how big they are, and cleaning up the app's own temporary files.
 *
 * <p><b>What's in the cache folders</b> (Android's {@code getCacheDir()} and {@code getExternalCacheDir()}):</p>
 * <ul>
 *   <li>WebView's HTTP cache for every account: the websites' own program files, fonts and images
 *       ({@code cache/WebView/…/HTTP Cache}; verified in Chromium's {@code aw_browser_context.cc}).
 *       It is emptied through WebView ({@code clearCache}), never by deleting its files while WebView
 *       may be using them.</li>
 *   <li>The app's own temporary files, directly in the folder: camera photos, and files
 *       older app versions left (shared-file copies {@code shared_file_…}, converted documents and PDF
 *       parts, a half-downloaded converter). Until round 6 nothing ever deleted them.</li>
 * </ul>
 * <p>So the folder size is the same "Cache" figure Android shows in App info → Storage.</p>
 *
 * <p>Only plain {@link File} code apart from the two Context helpers, so AppCacheTest checks it on the
 * computer with real temporary folders.</p>
 */
public final class AppCache {

    /** Temporary files older than this are deleted when the app starts (1 day). */
    public static final long TEMP_MAX_AGE_MS = 24L * 60 * 60 * 1000;

    private AppCache() {
        // Only static methods.
    }

    /** @return the size in bytes of both cache folders (run on a background thread: it walks every file). */
    public static long sizeBytes(Context context) {
        return folderSize(context.getCacheDir()) + folderSize(context.getExternalCacheDir());
    }

    /**
     * Deletes the app's own temporary files (only files lying directly in the cache folders, never
     * folders, so WebView's cache is untouched). Run on a background thread.
     *
     * @param olderThanMs only files last changed more than this long ago (0 = all)
     * @param keep        absolute paths of files still needed (e.g. a shared file waiting for 📎)
     * @return how many files were deleted
     */
    public static int deleteTempFiles(Context context, long olderThanMs, Set<String> keep) {
        long cutoff = System.currentTimeMillis() - olderThanMs;
        return deleteTopLevelFiles(context.getCacheDir(), cutoff, keep)
                + deleteTopLevelFiles(context.getExternalCacheDir(), cutoff, keep);
    }

    /** Total size of all files in a folder and its sub-folders (0 for a missing folder). */
    static long folderSize(File dir) {
        if (dir == null) {
            return 0;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return dir.isFile() ? dir.length() : 0;
        }
        long total = 0;
        for (File child : children) {
            // Don't follow links out of the folder (a link's real path differs from its own path).
            total += child.isDirectory() ? (isLink(child) ? 0 : folderSize(child)) : child.length();
        }
        return total;
    }

    /**
     * Deletes the files directly inside {@code dir} last modified before {@code cutoffMillis}, except
     * those in {@code keep}. Sub-folders are left alone.
     */
    static int deleteTopLevelFiles(File dir, long cutoffMillis, Set<String> keep) {
        File[] children = dir == null ? null : dir.listFiles();
        if (children == null) {
            return 0;
        }
        Set<String> spared = keep != null ? keep : Collections.emptySet();
        int deleted = 0;
        for (File child : children) {
            if (child.isFile() && child.lastModified() < cutoffMillis
                    && !spared.contains(child.getAbsolutePath()) && child.delete()) {
                deleted++;
            }
        }
        return deleted;
    }

    /**
     * @return true if {@code file} is a symbolic link. Compared against its <i>canonical</i> parent,
     *         because the cache folder's own path may itself go through a link on Android
     *         (/data/user/0/… → /data/data/…), which must not make every file look like a link.
     */
    static boolean isLink(File file) {
        try {
            File parent = file.getParentFile();
            File expected = parent == null ? file.getAbsoluteFile()
                    : new File(parent.getCanonicalFile(), file.getName());
            return !file.getCanonicalFile().equals(expected);
        } catch (Exception e) {
            return true;
        }
    }
}
