package org.llmcentral.app.scripts;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The rules for the user's own scripts (Settings → Data &amp; tools → User scripts), in plain Java so
 * {@code UserScriptsTest} can check them on the computer.
 *
 * <p><b>Where they are kept:</b> one JSON list in the preference {@value #PREF}, written by the scripts
 * screen ({@code assets/scripts_manager.html} through {@code ui/ScriptsWindow}). Each item:
 * {@code id, name, description, author, match, enabled, code}.</p>
 *
 * <p><b>When a script runs</b> ({@link #shouldRun}): it is switched on, has code, the page address
 * matches its "match" setting ({@link UserScriptMatcher}; empty = every web page opened in the app),
 * and no {@code @exclude} line of its {@code ==UserScript==} header matches.</p>
 *
 * <p><b>How it runs</b> ({@link #invocation}): {@code assets/js/user_script_run.js} is a JavaScript
 * function; the app calls it with the script's id and a function holding the script's code. The runner
 * makes sure each script runs once per page, even when WebView reports the same page as finished
 * again (a jump to a {@code #section} does that).</p>
 */
public final class UserScripts {

    /** Preference (in {@code duck_assist_prefs}) holding the JSON list. Never rename: users would lose their scripts. */
    public static final String PREF = "user_scripts";

    /**
     * Sites for a script saved without any "match" value at all (only possible for hand-made or very old
     * data). An empty value is different: it means every website (owner's decision, 2026-10-15).
     */
    public static final String LEGACY_MATCH = "*://*.duck.ai/*";

    private static final String HEADER_START = "==UserScript==";
    private static final String HEADER_END = "==/UserScript==";

    private UserScripts() {
    }

    /**
     * @param script one saved script
     * @param url    the address of the page that just finished loading
     * @return true if the script should run on that page
     */
    public static boolean shouldRun(JSONObject script, String url) {
        // Only the value true counts as "on" (the editor's switch shows exactly that), not "true" as text or 1.
        if (url == null || !Boolean.TRUE.equals(script.opt("enabled"))) {
            return false;
        }
        String code = script.optString("code", "");
        if (code.trim().isEmpty()) {
            return false;
        }
        // Only text counts as a sites setting; anything else (missing, null, false…) is the old default, which
        // is also what the editor shows for it.
        Object raw = script.opt("match");
        String match = raw instanceof String ? (String) raw : LEGACY_MATCH;
        if (!UserScriptMatcher.matchesAny(url, match)) {
            return false;
        }
        for (String exclude : excludes(code)) {
            // One pattern per @exclude line (Tampermonkey's rule): a comma is part of it, never a separator.
            if (UserScriptMatcher.matches(url, exclude)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The {@code @exclude} and {@code @exclude-match} values of the script's {@code ==UserScript==}
     * header (Tampermonkey's way to say "not on these pages"). Lines outside the header are ignored,
     * so code that merely mentions "@exclude" changes nothing, and a header without its
     * {@code ==/UserScript==} end line counts as no header (the editor's notes use the same rule).
     */
    public static List<String> excludes(String code) {
        if (code == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        boolean inHeader = false;
        for (String raw : code.split("\r?\n")) {
            String line = raw.trim();
            if (line.contains(HEADER_END)) {
                return result;
            }
            if (line.contains(HEADER_START)) {
                inHeader = true;
                continue;
            }
            if (!inHeader || !line.startsWith("//")) {
                continue;
            }
            String body = line.substring(2).trim();
            String lower = body.toLowerCase(Locale.ROOT);
            String key = lower.startsWith("@exclude-match") ? "@exclude-match"
                    : lower.startsWith("@exclude") ? "@exclude" : null;
            if (key == null) {
                continue;
            }
            String rest = body.substring(key.length());
            if (!rest.isEmpty() && !Character.isWhitespace(rest.charAt(0))) {
                continue; // e.g. "@excluded": a different key
            }
            String value = rest.trim();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return Collections.emptyList(); // no end line: not a complete header
    }

    /**
     * The JavaScript the app runs for one script: the runner from {@code user_script_run.js}, called
     * with the id (as a safe JavaScript string, {@link JSONObject#quote}) and a function holding the code.
     * The code is the user's own, so it isn't escaped: it runs with the page's rights either way.
     */
    public static String invocation(String runner, String id, String code) {
        // The inner { } lets a script declare its own "const GM_addStyle" without a duplicate-name error.
        return runner + "(" + JSONObject.quote(id) + ",function(GM_addStyle,unsafeWindow){{\n" + code + "\n}})";
    }

    /**
     * The key the runner uses to remember "already ran in this page": the script's place in the list plus
     * its id, so two entries can never block each other (even hand-made ones with the same id or none).
     */
    public static String runKey(JSONObject script, int position) {
        return position + ":" + script.optString("id", "").trim();
    }

    /**
     * Checks a list the scripts screen wants to save.
     *
     * @return the text unchanged (trimmed) if it is a JSON list of objects, else null (don't save)
     */
    public static String sanitize(String json) {
        if (json == null) {
            return null;
        }
        try {
            JSONArray list = new JSONArray(json.trim());
            for (int i = 0; i < list.length(); i++) {
                if (!(list.get(i) instanceof JSONObject)) {
                    return null;
                }
            }
            return json.trim();
        } catch (Exception e) {
            return null;
        }
    }

    /** The list shown before the user ever saved one: a switched-off example (assets/js/user_script_sample.js). */
    public static String defaultList(String sampleCode) {
        try {
            JSONObject script = new JSONObject();
            // Written for LLM Central, GPL-3.0-only like the app (F-Droid requires every file to be free).
            script.put("id", "example_larger_text");
            script.put("name", "Example: larger text on duck.ai");
            script.put("author", "LLM Central");
            script.put("description",
                    "Shows how user scripts work: makes duck.ai's text 10 % larger. Edit or delete it as you like.");
            script.put("match", LEGACY_MATCH);
            script.put("enabled", false);
            script.put("code", sampleCode);
            return new JSONArray().put(script).toString();
        } catch (Exception e) {
            return "[]";
        }
    }
}
