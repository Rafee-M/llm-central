package org.llmcentral.app.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/** JVM unit tests for the provider list (PROJECT.md R-PROV). Run with ./gradlew test. */
public class ProvidersTest {

    @Test
    public void idsAreUniqueAndDuckIsFirst() {
        Set<String> ids = new HashSet<>();
        for (ProviderSpec p : Providers.ALL) {
            assertTrue("duplicate id " + p.id, ids.add(p.id));
        }
        assertSame(Providers.DUCK, Providers.ALL.get(0));
        assertTrue(Providers.DUCK.isDuck());
        assertFalse(Providers.CHATGPT.isDuck());
    }

    @Test
    public void everyHomePageIsHttpsAndOwnedByItsProvider() throws Exception {
        for (ProviderSpec p : Providers.ALL) {
            URI home = new URI(p.homeUrl);
            assertEquals(p.id + " home must be https", "https", home.getScheme());
            assertTrue(p.id + " must own its home page host", p.ownsHost(home.getHost()));
        }
    }

    @Test
    public void noDomainIsClaimedByTwoProviders() {
        // Otherwise a link could be "in-app" for two tabs at once, and account isolation gets muddy.
        Set<String> seen = new HashSet<>();
        for (ProviderSpec p : Providers.ALL) {
            for (String d : p.inAppDomains()) {
                assertTrue(d + " is listed twice", seen.add(d));
            }
        }
        for (ProviderSpec a : Providers.ALL) {
            for (ProviderSpec b : Providers.ALL) {
                if (a == b) continue;
                for (String d : b.inAppDomains()) {
                    assertFalse(a.id + " also owns " + b.id + "'s domain " + d, a.ownsHost(d));
                }
            }
        }
    }

    @Test
    public void loginPagesStayInTheirProvidersTab() {
        assertTrue(Providers.CHATGPT.ownsHost("auth.openai.com"));
        assertTrue(Providers.CHATGPT.ownsHost("login.live.com"));
        assertTrue(Providers.ZAI.ownsHost("github.com"));
        assertTrue(Providers.DEEPSEEK.ownsHost("chat.deepseek.com"));
        // Shared sign-in hosts (SignInHosts) are only navigable, never owned (owning would also grant the microphone,
        // page scripts and prompt filling there).
        for (ProviderSpec p : Providers.ALL) {
            assertFalse(p.ownsHost("accounts.google.com"));
        }
        // Look-alikes are rejected (same exact-match rules as NavigationPolicy).
        assertFalse(Providers.CLAUDE.ownsHost("evilclaude.ai"));
        assertFalse(Providers.ZAI.ownsHost("notz.ai"));
    }

    /** Owner, 2026-10-29: "Continue with Apple/Google" opens inside the account's own container, for every chat provider. */
    @Test
    public void everyChatProviderOpensAppleAndGoogleSignInInTheApp() throws Exception {
        for (ProviderSpec p : Providers.ALL) {
            for (String host : new String[]{"appleid.apple.com", "idmsa.apple.com", "accounts.google.com"}) {
                assertEquals(p.id + " " + host, !p.isDuck(), p.loadsInApp(host));
            }
            assertTrue(p.id, p.loadsInApp(new java.net.URI(p.homeUrl).getHost())); // own pages still load
        }
        // Provider-specific sign-in options stay with their provider.
        assertTrue(Providers.CHATGPT.loadsInApp("login.live.com"));
        assertFalse(Providers.CLAUDE.loadsInApp("login.live.com"));
        assertTrue(Providers.ZAI.loadsInApp("github.com"));
        assertFalse(Providers.DEEPSEEK.loadsInApp("github.com"));
    }

    @Test
    public void noProviderOwnsTheSharedSignInHosts() {
        for (ProviderSpec p : Providers.ALL) {
            for (String host : SignInHosts.HOSTS) {
                assertFalse(p.id + " owns " + host, p.ownsHost(host));
            }
        }
    }

    @Test
    public void googleSearchAppleSupportAndLookAlikesStayOutside() {
        for (ProviderSpec p : Providers.ALL) {
            for (String host : new String[]{"www.google.com", "docs.google.com", "google.com", "support.apple.com",
                    "apple.com", "accounts.google.com.evil.net", "evilaccounts.google.com", "evilappleid.apple.com", null, ""}) {
                assertFalse(p.id + " " + host, p.loadsInApp(host));
            }
        }
        assertTrue(SignInHosts.needsBrowserUserAgent("accounts.google.com"));
        assertFalse(SignInHosts.needsBrowserUserAgent("appleid.apple.com"));
        assertFalse(SignInHosts.needsBrowserUserAgent("www.google.com"));
        assertFalse(SignInHosts.needsBrowserUserAgent(null));
    }

    /** Gemini (2026-10-29): only its chat site is its own; Google's other sites open in the browser. */
    @Test
    public void geminiOwnsOnlyItsChatSite() throws Exception {
        assertSame(Providers.GEMINI, Providers.byId("gemini"));
        assertTrue(Providers.ALL.contains(Providers.GEMINI));
        assertEquals("gemini.google.com", new URI(Providers.GEMINI.homeUrl).getHost());
        assertTrue(Providers.GEMINI.ownsHost("gemini.google.com"));
        for (String host : new String[]{"google.com", "www.google.com", "docs.google.com", "drive.google.com",
                "accounts.google.com", "myaccount.google.com", "gemini.app.google", "evilgemini.google.com.example"}) {
            assertFalse(host, Providers.GEMINI.ownsHost(host));
        }
        // Google sign-in stays in the app (shared SignInHosts), with Google's page in the account's own container.
        assertTrue(Providers.GEMINI.loadsInApp("accounts.google.com"));
        assertFalse(Providers.GEMINI.loadsInApp("www.google.com"));
        assertFalse(Providers.GEMINI.isDuck());
    }

    @Test
    public void unknownIdFallsBackToDuck() {
        assertSame(Providers.CLAUDE, Providers.byId("claude"));
        assertSame(Providers.DUCK, Providers.byId("does-not-exist"));
        assertSame(Providers.DUCK, Providers.byId(null));
    }
}
