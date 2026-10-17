package org.llmcentral.app.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Which links a local dialog page (Settings, saved chats…) may open inside itself ({@link DialogWebViewClient}). */
public class DialogWebViewClientTest {

    @Test
    public void ownPagesStayInside() {
        assertTrue(DialogWebViewClient.staysInside("file"));
        assertTrue(DialogWebViewClient.staysInside("about"));
        assertTrue(DialogWebViewClient.staysInside("data"));
        assertTrue(DialogWebViewClient.staysInside("blob"));
    }

    @Test
    public void javascriptLinksNeverRunInside() {
        // R4-7: a saved chat's text could carry a javascript: link; inside the saved-chats window it would run with
        // that window's bridge (all saved chats). Such links are never loaded there.
        assertFalse(DialogWebViewClient.staysInside("javascript"));
    }

    @Test
    public void webLinksGoToTheBrowser() {
        assertFalse(DialogWebViewClient.staysInside("https"));
        assertFalse(DialogWebViewClient.staysInside("http"));
        assertFalse(DialogWebViewClient.staysInside(""));
    }
}
