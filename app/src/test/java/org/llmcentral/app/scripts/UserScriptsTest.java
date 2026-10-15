package org.llmcentral.app.scripts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * JVM unit tests for {@link UserScripts}: when a saved script runs, how its call is built, and which
 * saved lists are accepted. Run with: ./gradlew test
 */
public class UserScriptsTest {

    private static JSONObject script(String match, boolean enabled, String code) throws Exception {
        JSONObject o = new JSONObject().put("id", "s1").put("enabled", enabled).put("code", code);
        if (match != null) {
            o.put("match", match);
        }
        return o;
    }

    @Test
    public void emptyMatchRunsEverywhere() throws Exception {
        JSONObject s = script("", true, "x()");
        assertTrue(UserScripts.shouldRun(s, "https://chatgpt.com/c/1"));
        assertTrue(UserScripts.shouldRun(s, "https://accounts.google.com/signin"));
        assertFalse(UserScripts.shouldRun(s, "file:///android_asset/settings.html"));
    }

    @Test
    public void separatorOnlyMatchRunsEverywhere() throws Exception {
        assertTrue(UserScripts.shouldRun(script(" , ,", true, "x()"), "https://claude.ai/new"));
    }

    @Test
    public void missingMatchKeyMeansDuckOnly() throws Exception {
        JSONObject s = script(null, true, "x()");
        assertTrue(UserScripts.shouldRun(s, "https://duck.ai/chat"));
        assertFalse(UserScripts.shouldRun(s, "https://chatgpt.com/"));
    }

    @Test
    public void disabledOrBlankNeverRuns() throws Exception {
        assertFalse(UserScripts.shouldRun(script("", false, "x()"), "https://duck.ai/"));
        assertFalse(UserScripts.shouldRun(script("", true, "  \n "), "https://duck.ai/"));
        assertFalse(UserScripts.shouldRun(new JSONObject().put("match", ""), "https://duck.ai/"));
        assertFalse(UserScripts.shouldRun(script("", true, "x()"), null));
    }

    @Test
    public void headerExcludeWins() throws Exception {
        String code = "// ==UserScript==\n// @match   *://*.duck.ai/*\n// @exclude https://duck.ai/settings*\n"
                + "// @exclude-match *://*.duck.ai/admin/*\n// ==/UserScript==\nx();";
        JSONObject s = script("*://*.duck.ai/*", true, code);
        assertTrue(UserScripts.shouldRun(s, "https://duck.ai/chat"));
        assertFalse(UserScripts.shouldRun(s, "https://duck.ai/settings/x"));
        assertFalse(UserScripts.shouldRun(s, "https://duck.ai/admin/users"));
    }

    @Test
    public void excludeOutsideHeaderIgnored() {
        String code = "// ==UserScript==\n// @exclude  a*\n// ==/UserScript==\n// @exclude b*\nvar s = '@exclude c*';";
        assertEquals(Collections.singletonList("a*"), UserScripts.excludes(code));
        assertEquals(Collections.emptyList(), UserScripts.excludes("// @exclude x*\nrun();"));
        assertEquals(Collections.emptyList(), UserScripts.excludes(null));
        assertEquals(Arrays.asList("p1", "p2"),
                UserScripts.excludes("// ==UserScript==\r\n// @exclude p1\r\n//@exclude-match   p2  \r\n// ==/UserScript=="));
    }

    @Test
    public void invocationQuotesIdAndWrapsCodeInAFunction() {
        String call = UserScripts.invocation("RUN", "a\"b", "go();");
        assertEquals("RUN(\"a\\\"b\",function(GM_addStyle,unsafeWindow){\ngo();\n})", call);
    }

    @Test
    public void sanitizeRejectsAnythingButAListOfObjects() {
        assertNull(UserScripts.sanitize(null));
        assertNull(UserScripts.sanitize("not json"));
        assertNull(UserScripts.sanitize("{\"a\":1}"));
        assertNull(UserScripts.sanitize("[1, 2]"));
        assertEquals("[ ]", UserScripts.sanitize(" [ ] "));
        assertEquals("[ {\"id\": \"x\"} ]", UserScripts.sanitize("[ {\"id\": \"x\"} ]")); // kept as written
    }

    @Test
    public void defaultListHasOneDisabledExample() throws Exception {
        JSONArray list = new JSONArray(UserScripts.defaultList("CODE"));
        assertEquals(1, list.length());
        JSONObject s = list.getJSONObject(0);
        assertEquals("example_larger_text", s.getString("id"));
        assertFalse(s.getBoolean("enabled"));
        assertEquals("CODE", s.getString("code"));
        assertEquals(UserScripts.LEGACY_MATCH, s.getString("match"));
    }

    @Test
    public void idFallsBackToTheListPosition() throws Exception {
        assertEquals("s1", UserScripts.idOf(script("", true, "x()"), 3));
        assertEquals("script_3", UserScripts.idOf(new JSONObject(), 3));
        assertEquals("script_3", UserScripts.idOf(new JSONObject().put("id", " "), 3));
    }
}
