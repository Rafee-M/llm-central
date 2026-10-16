package org.llmcentral.app.storage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Which shared files the app accepts from other apps (share sheet, "Ask AI", direct intents). */
public class SharedFilesTest {

    private static final String OWN = "org.llmcentral.app.fileprovider";

    @Test
    public void otherAppsContentIsAccepted() {
        assertTrue(SharedFiles.isAcceptable("content", "com.android.providers.media.documents", OWN));
        assertTrue(SharedFiles.isAcceptable("CONTENT", "com.google.android.apps.photos.contentprovider", OWN));
    }

    @Test
    public void plainFilesAreRefused() {
        // A file:// address would be read by WebView with this app's rights: e.g. its own settings or cookies.
        assertFalse(SharedFiles.isAcceptable("file", null, OWN));
        assertFalse(SharedFiles.isAcceptable("file", "", OWN));
        assertFalse("file://localhost/... has an authority", SharedFiles.isAcceptable("file", "localhost", OWN));
    }

    @Test
    public void ownFileProviderIsRefused() {
        assertFalse(SharedFiles.isAcceptable("content", OWN, OWN));
        assertFalse(SharedFiles.isAcceptable("content", "ORG.LLMCENTRAL.APP.FILEPROVIDER", OWN));
    }

    @Test
    public void ownFileProviderWithAUserPrefixIsRefused() {
        // content://0@<authority>/… : Android drops "<user>@" before it looks the provider up
        // (ContentProvider.getAuthorityWithoutUserId), so this still opens our own FileProvider.
        assertFalse(SharedFiles.isAcceptable("content", "0@" + OWN, OWN));
        assertFalse(SharedFiles.isAcceptable("content", "10@" + OWN, OWN));
        assertFalse(SharedFiles.isAcceptable("content", "x@y@" + OWN, OWN));
        assertFalse("nothing left after the prefix", SharedFiles.isAcceptable("content", "0@", OWN));
        // Another app's file shared from a work profile carries the same prefix and stays accepted.
        assertTrue(SharedFiles.isAcceptable("content", "10@com.android.providers.media.documents", OWN));
    }

    @Test
    public void anythingElseIsRefused() {
        assertFalse(SharedFiles.isAcceptable(null, "x", OWN));
        assertFalse(SharedFiles.isAcceptable("http", "example.com", OWN));
        assertFalse(SharedFiles.isAcceptable("content", null, OWN));
        assertFalse(SharedFiles.isAcceptable("content", "", OWN));
    }
}
