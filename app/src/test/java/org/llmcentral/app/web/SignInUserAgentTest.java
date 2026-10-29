package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.llmcentral.app.web.SignInUserAgent.Change;

/**
 * Google's sign-in page gets a browser user agent while it is shown (owner, 2026-10-29, geminiAssist's method):
 * {@link SignInUserAgent#disguise} and {@link SignInUserAgent#change}.
 */
public class SignInUserAgentTest {

    private static final String PHONE = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.240205.004; wv) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/140.0.7339.207 Mobile Safari/537.36";

    @Test
    public void webViewMarkIsReplacedLikeGeminiAssist() {
        // geminiAssist's modUserAgent: the first "(...)" becomes "(X11; Linux <os.arch>)", the rest stays.
        assertEquals("Mozilla/5.0 (X11; Linux aarch64) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 "
                + "Chrome/140.0.7339.207 Mobile Safari/537.36", SignInUserAgent.disguise(PHONE, "aarch64"));
        // Android 16's reduced user agent, and a tab in "Desktop site" mode (web/DesktopMode).
        assertEquals("Mozilla/5.0 (X11; Linux aarch64) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 "
                + "Chrome/140.0.0.0 Mobile Safari/537.36", SignInUserAgent.disguise("Mozilla/5.0 (Linux; Android 10; K; wv) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/140.0.0.0 Mobile Safari/537.36", "aarch64"));
        String desktop = SignInUserAgent.disguise("Mozilla/5.0 (X11; Linux x86_64; wv) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Version/4.0 Chrome/140.0.0.0 Safari/537.36", "armv8l");
        assertEquals("Mozilla/5.0 (X11; Linux armv8l) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 "
                + "Chrome/140.0.0.0 Safari/537.36", desktop);
        assertFalse(SignInUserAgent.disguise(PHONE, "aarch64").contains("; wv"));
    }

    @Test
    public void oddInputsAreSafe() {
        assertEquals("", SignInUserAgent.disguise(null, "aarch64"));
        assertEquals("NoBrackets/1.0", SignInUserAgent.disguise("NoBrackets/1.0", "aarch64"));
        assertEquals("Mozilla/5.0 (X11; Linux aarch64) X", SignInUserAgent.disguise("Mozilla/5.0 (Linux; wv) X", null));
    }

    @Test
    public void googleSignInSwitchesOnAndLeavingSwitchesBack() {
        String disguised = SignInUserAgent.disguise(PHONE, "aarch64");
        // Not disguised yet: Google's sign-in host switches it on; everything else leaves it alone.
        assertEquals(Change.DISGUISE, SignInUserAgent.change(null, PHONE, "accounts.google.com"));
        assertEquals(Change.NONE, SignInUserAgent.change(null, PHONE, "chatgpt.com"));
        assertEquals(Change.NONE, SignInUserAgent.change(null, PHONE, "appleid.apple.com")); // Apple needs no disguise
        assertEquals(Change.NONE, SignInUserAgent.change(null, PHONE, "accounts.google.com.evil.net"));
        assertEquals(Change.NONE, SignInUserAgent.change(null, PHONE, null));
        // Disguised: staying on Google changes nothing (no restart loop); leaving restores the tab's own user agent.
        assertEquals(Change.NONE, SignInUserAgent.change(disguised, disguised, "accounts.google.com"));
        assertEquals(Change.RESTORE, SignInUserAgent.change(disguised, disguised, "chat.z.ai"));
    }

    @Test
    public void someoneElseChangingTheUserAgentWins() {
        // "Desktop site" switched while Google's page was open: the saved disguise is stale. On Google it is applied
        // again to the new user agent; elsewhere nothing is restored over Desktop site's choice.
        String desktop = "Mozilla/5.0 (X11; Linux x86_64; wv) AppleWebKit/537.36 Safari/537.36";
        String staleDisguise = SignInUserAgent.disguise(PHONE, "aarch64");
        assertEquals(Change.DISGUISE, SignInUserAgent.change(staleDisguise, desktop, "accounts.google.com"));
        assertEquals(Change.NONE, SignInUserAgent.change(staleDisguise, desktop, "claude.ai"));
    }
}
