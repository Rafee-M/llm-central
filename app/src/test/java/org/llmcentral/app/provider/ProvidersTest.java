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
        // Google blocks sign-in inside web views, so it's not in-app for anyone.
        for (ProviderSpec p : Providers.ALL) {
            assertFalse(p.ownsHost("accounts.google.com"));
        }
        // Look-alikes are rejected (same exact-match rules as NavigationPolicy).
        assertFalse(Providers.CLAUDE.ownsHost("evilclaude.ai"));
        assertFalse(Providers.ZAI.ownsHost("notz.ai"));
    }

    @Test
    public void unknownIdFallsBackToDuck() {
        assertSame(Providers.CLAUDE, Providers.byId("claude"));
        assertSame(Providers.DUCK, Providers.byId("does-not-exist"));
        assertSame(Providers.DUCK, Providers.byId(null));
    }
}
