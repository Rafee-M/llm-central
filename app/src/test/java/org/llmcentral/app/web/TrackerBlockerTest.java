package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Tracker blocking: the rules (B-1, B-2) and the real list against the owner's phone log (plan §6). */
public class TrackerBlockerTest {

    private static final String SAMPLE = "# comment\n"
            + "\n"
            + "  Sentry.IO  \n"                       // host: case and spaces don't matter
            + "chatgpt.com/ces/v1/\n"                 // path prefix
            + "s-cdn.example.com/s.js*\n"             // a trailing * is the same as none
            + "?ddsource\n"                           // query parameter, any host
            + "not a rule\n"                          // ignored
            + "*.wild.example\n";                     // wildcards aren't supported: ignored

    private static boolean blocked(TrackerBlocker b, String host, String path, String query) {
        return b.shouldBlock(false, host, path, query);
    }

    @Test
    public void hostRuleCoversTheHostAndItsSubdomainsOnly() {
        TrackerBlocker b = TrackerBlocker.parse(SAMPLE);
        assertTrue(blocked(b, "sentry.io", "/", null));
        assertTrue(blocked(b, "o123.ingest.sentry.io", "/api/1/envelope/", null));
        assertTrue(blocked(b, "SENTRY.IO", "/", null));
        assertFalse("a different domain that only ends the same", blocked(b, "notsentry.io", "/", null));
        assertFalse(blocked(b, "sentry.io.example.com", "/", null));
    }

    @Test
    public void pathRuleBlocksOnlyThatPath() {
        TrackerBlocker b = TrackerBlocker.parse(SAMPLE);
        assertTrue(blocked(b, "chatgpt.com", "/ces/v1/t", null));
        assertTrue(blocked(b, "www.chatgpt.com", "/ces/v1/", null));
        assertFalse(blocked(b, "chatgpt.com", "/backend-api/conversation", null));
        assertFalse(blocked(b, "chatgpt.com", "/ces/v2/t", null));
        assertFalse(blocked(b, "chatgpt.com", "/", null));
        assertTrue(blocked(b, "s-cdn.example.com", "/s.js", null));
        assertFalse(blocked(b, "s-cdn.example.com", "/other.js", null));
    }

    @Test
    public void queryRuleMatchesTheParameterNameOnAnyHost() {
        TrackerBlocker b = TrackerBlocker.parse(SAMPLE);
        assertTrue(blocked(b, "chatgpt.com", "/api/v2/rum", "ddsource=browser&ddtags=x"));
        assertTrue(blocked(b, "x.example", "/", "a=1&ddsource=browser"));
        assertTrue(blocked(b, "x.example", "/", "ddsource"));
        assertFalse("only as a parameter name", blocked(b, "x.example", "/", "q=ddsource%3D"));
        assertFalse(blocked(b, "x.example", "/", "myddsource=1"));
        assertFalse(blocked(b, "x.example", "/", null));
    }

    @Test
    public void pageLoadsAreNeverBlocked() {
        TrackerBlocker b = TrackerBlocker.parse(SAMPLE);
        assertFalse(b.shouldBlock(true, "sentry.io", "/", null));
        assertFalse(b.shouldBlock(true, "chatgpt.com", "/ces/v1/t", "ddsource=browser"));
    }

    @Test
    public void malformedLinesAndMissingPartsAreSafe() {
        TrackerBlocker b = TrackerBlocker.parse(SAMPLE);
        assertEquals(4, b.size());
        assertFalse(blocked(b, "wild.example", "/", null));
        assertFalse(blocked(b, "a.wild.example", "/", null));
        assertFalse(blocked(b, null, null, null));
        assertFalse(blocked(b, "", "/", null));
        assertTrue(blocked(b, "chatgpt.com.", "/ces/v1/", null)); // a host written with its final dot
        assertEquals(0, TrackerBlocker.parse(null).size());
        assertEquals(0, TrackerBlocker.parse("").size());
    }

    // ---------------------------------------------------------------- the real list (assets/trackers.txt)

    private static TrackerBlocker realList() throws IOException {
        File file = new File("src/main/assets/trackers.txt");
        if (!file.exists()) {
            file = new File("app/src/main/assets/trackers.txt"); // when run from the project folder
        }
        return TrackerBlocker.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    /** Telemetry the owner's phone log shows (docs/trackers/phone/2026-10-17-all-providers.tsv): host, path, query. */
    private static final String[][] TELEMETRY = {
            {"ab.chatgpt.com", "/v1/rgstr", null},
            {"chatgpt.com", "/ces/v1/t", null},
            {"auth.openai.com", "/awe/api/v2/rum", "ddsource=browser"},
            {"chatgpt.com", "/awe/api/v2/rum", null},
            {"s-cdn.anthropic.com", "/s.js", null},
            {"a.claude.ai", "/isolated-segment.html", null},
            {"api.anthropic.com", "/api/event_logging/batch", null},
            {"hif-dliq.deepseek.com", "/query", null},
            {"gator.volces.com", "/list", null},
            {"gator.volces.com", "/webid", null},
            {"fp-it-acc.portal101.cn", "/deviceprofile/v4", null},
            {"improving.duckduckgo.com", "/t/x", null},
            {"sdata.chatglm.cn", "/frontend/x", null},
            {"zcode.z.ai", "/api/v1/zai/blank.gif", "e=1"},
            {"www.googletagmanager.com", "/gtm.js", "id=GTM-X"},
            {"sdk.rum.aliyuncs.com", "/v2/browser-sdk.js", null},
            {"k8s-log-c1f874a604cb64b29bb69219c8faaf8b7.cn-hongkong.log.aliyuncs.com", "/logstores/x", null},
            {"o1.ingest.sentry.io", "/api/1/envelope/", null},
            {"browser-intake-datadoghq.com", "/api/v2/rum", null},
            {"some-proxy.example", "/rum", "ddsource=browser"},
    };

    /** What the sites need (sign-in, captcha, bot checks, pages, API, CDNs), from the same phone log. */
    private static final String[][] NEEDED = {
            {"chatgpt.com", "/backend-api/conversation", null},
            {"chatgpt.com", "/", null},
            // Shared sign-in pages (provider/SignInHosts, 2026-10-29)
            {"accounts.google.com", "/o/oauth2/v2/auth", null},
            {"appleid.apple.com", "/auth/authorize", null},
            {"idmsa.apple.com", "/appleauth/auth/signin", null},
            {"auth.openai.com", "/api/accounts/authorize", null},
            {"auth-cdn.oaistatic.com", "/assets/app.js", null},
            {"sentinel.openai.com", "/backend-api/sentinel/req", null},
            {"cdn.auth0.com", "/avatars/a.png", null},
            {"cdn.openai.com", "/common/x", null},
            {"cdn.platform.openai.com", "/deployments/x", null},
            {"www.google.com", "/s2/favicons", "domain=x"},
            {"claude.ai", "/api/organizations", null},
            {"assets-proxy.anthropic.com", "/claude-ai/x.js", null},
            {"api.anthropic.com", "/api/hello", null},
            {"chat.deepseek.com", "/api/v0/chat/completion", null},
            {"fe-static.deepseek.com", "/chat/x.js", null},
            {"cdn.deepseek.com", "/site-icons/x.png", null},
            {"challenges.cloudflare.com", "/turnstile/v0/api.js", null},
            {"a0fea896111e.c54f3839.ap-south-1.token.awswaf.com", "/x/challenge.js", null},
            {"appleid.cdn-apple.com", "/appleauth/static/x.js", null},
            {"duck.ai", "/duckchat/v1/chat", null},
            {"chat.z.ai", "/api/chat/completions", null},
            {"zcode.z.ai", "/api/v1/other", null},
            {"z-cdn.chatglm.cn", "/z-ai/x.js", null},
            {"g.alicdn.com", "/captcha-frontend/x.js", null},
            {"o.alicdn.com", "/frontend-lib/x.js", null},
            {"no8xfe.captcha-open-southeast.aliyuncs.com", "/", null},
            {"upload.captcha-open-southeast.aliyuncs.com", "/", null},
            {"static-captcha-sgp.aliyuncs.com", "/qst/x", null},
            {"cloudauth-device-dualstack.ap-southeast-1.aliyuncs.com", "/", null},
            {"artifacts-cdn.chatglm.site", "/x", null},
            {"fonts.gstatic.com", "/s/x.woff2", null},
            {"preview-chat-1.space-z.ai", "/", null},
    };

    @Test
    public void realListBlocksTheTelemetryInThePhoneLog() throws IOException {
        TrackerBlocker list = realList();
        for (String[] r : TELEMETRY) {
            assertTrue(r[0] + r[1], blocked(list, r[0], r[1], r[2]));
        }
    }

    @Test
    public void realListNeverBlocksWhatTheSitesNeed() throws IOException {
        TrackerBlocker list = realList();
        for (String[] r : NEEDED) {
            assertFalse(r[0] + r[1], blocked(list, r[0], r[1], r[2]));
        }
    }

    @Test
    public void realListIsSmall() throws IOException {
        int size = realList().size();
        assertTrue("rules: " + size, size >= 40 && size <= 200);
    }
}
