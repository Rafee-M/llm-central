package org.llmcentral.app.storage;

import java.util.Locale;

/**
 * Which files another app may hand to this app to attach to a chat (share sheet, "Ask AI", or an app that starts
 * our screens directly; they are "exported", so any app can).
 *
 * <p><b>Why a check:</b> a shared file is later given to the chat page's 📎 picker, and WebView reads it with
 * <i>this</i> app's rights. A {@code file://} address could therefore point at the app's private files (its
 * settings, cookies, saved chats) and get them attached to a chat without the user meaning to. Real apps share
 * {@code content://} addresses (Android has blocked {@code file://} between apps since Android 7), so only
 * those are accepted, and never one of our own {@code FileProvider} (it serves our own files).</p>
 *
 * <p>Plain Java (strings in, boolean out), so SharedFilesTest checks it on the computer.</p>
 */
public final class SharedFiles {

    private SharedFiles() {
    }

    /**
     * @param scheme                   the address's scheme, e.g. "content"
     * @param authority                the provider part of a content:// address
     * @param ownFileProviderAuthority this app's own FileProvider (package name + ".fileprovider")
     * @return true if the file may be offered to the page
     */
    public static boolean isAcceptable(String scheme, String authority, String ownFileProviderAuthority) {
        if (scheme == null || !"content".equals(scheme.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (authority == null || authority.isEmpty()) {
            return false;
        }
        return !authority.equalsIgnoreCase(ownFileProviderAuthority);
    }
}
