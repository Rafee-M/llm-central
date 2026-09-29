package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM unit tests for {@link NavigationPolicy} (PROJECT.md P14). Run with ./gradlew test. */
public class NavigationPolicyTest {

    @Test
    public void realDomainsAndSubdomainsAreInApp() {
        assertTrue(NavigationPolicy.isInAppHost("duck.ai"));
        assertTrue(NavigationPolicy.isInAppHost("www.duck.ai"));
        assertTrue(NavigationPolicy.isInAppHost("duckduckgo.com"));
        assertTrue(NavigationPolicy.isInAppHost("links.duckduckgo.com"));
        assertTrue(NavigationPolicy.isInAppHost("Duck.AI"));   // hosts are case-insensitive
        assertTrue(NavigationPolicy.isInAppHost("duck.ai."));  // trailing-dot form of the same host
    }

    @Test
    public void lookalikeDomainsAreNotInApp() {
        // The old endsWith("duck.ai") check accepted the first two.
        assertFalse(NavigationPolicy.isInAppHost("evilduck.ai"));
        assertFalse(NavigationPolicy.isInAppHost("notduckduckgo.com"));
        assertFalse(NavigationPolicy.isInAppHost("duck.ai.evil.com"));
        assertFalse(NavigationPolicy.isInAppHost("chatgpt.com"));
        assertFalse(NavigationPolicy.isInAppHost(""));
        assertFalse(NavigationPolicy.isInAppHost(null));
    }

    @Test
    public void externalRequestsForChatSitesAreAllowed() {
        assertEquals("https://duck.ai/chat?q=hi", NavigationPolicy.toInAppUrl("https://duck.ai/chat?q=hi"));
        assertEquals("https://duckduckgo.com/?ia=chat", NavigationPolicy.toInAppUrl("https://duckduckgo.com/?ia=chat"));
        // http is upgraded to https
        assertEquals("https://duck.ai/", NavigationPolicy.toInAppUrl("http://duck.ai/"));
        assertEquals("https://duck.ai/", NavigationPolicy.toInAppUrl("HTTP://duck.ai/"));
    }

    @Test
    public void dangerousExternalRequestsAreRefused() {
        assertNull(NavigationPolicy.toInAppUrl("javascript:alert(1)"));
        assertNull(NavigationPolicy.toInAppUrl("file:///data/data/org.llmcentral.app/shared_prefs/x.xml"));
        assertNull(NavigationPolicy.toInAppUrl("content://some.provider/file"));
        assertNull(NavigationPolicy.toInAppUrl("intent://duck.ai#Intent;end"));
        assertNull(NavigationPolicy.toInAppUrl("https://evil.com/phish"));
        assertNull(NavigationPolicy.toInAppUrl("https://evilduck.ai/"));
        assertNull(NavigationPolicy.toInAppUrl("https://duck.ai@evil.com/"));      // user-info trick
        assertNull(NavigationPolicy.toInAppUrl("https://user:pw@duck.ai/"));       // any user-info refused
        assertNull(NavigationPolicy.toInAppUrl("chrome://crash"));                 // only via the debug hook
        assertNull(NavigationPolicy.toInAppUrl("not a url"));
        assertNull(NavigationPolicy.toInAppUrl(null));
    }

    @Test
    public void rendererTestUrlsAreRecognisedExactly() {
        assertTrue(NavigationPolicy.isRendererTestUrl("chrome://crash"));
        assertTrue(NavigationPolicy.isRendererTestUrl("chrome://kill"));
        assertFalse(NavigationPolicy.isRendererTestUrl("chrome://settings"));
        assertFalse(NavigationPolicy.isRendererTestUrl("https://duck.ai/"));
    }
}
