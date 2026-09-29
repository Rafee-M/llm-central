package org.llmcentral.app.scripts;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides whether a user script should run on a given page URL.
 *
 * <p>Each user script has a "match" setting: one or more comma-separated patterns in the same style
 * as browser extensions (Tampermonkey / Chrome "match patterns"), for example
 * {@code *://*.duck.ai/*, https://chatgpt.com/*}.</p>
 *
 * <p>A pattern has three parts: {@code scheme://host/path}</p>
 * <ul>
 *   <li><b>scheme</b>: {@code https}, {@code http}, or {@code *} (= http or https).</li>
 *   <li><b>host</b>: an exact host ({@code chatgpt.com}), {@code *} (any host), or
 *       {@code *.example.com}, which matches {@code example.com} itself <i>and</i> every subdomain
 *       ({@code www.example.com}, {@code a.b.example.com}).</li>
 *   <li><b>path</b>: the rest of the URL (path + query). {@code *} means "any characters";
 *       every other character must match literally.</li>
 * </ul>
 * <p>The special values {@code *} and {@code <all_urls>} match every http(s) page. An empty match
 * setting also means "all pages" (behaviour kept from duckAssist).</p>
 *
 * <p><b>Why this class exists:</b> the original duckAssist code turned patterns into a regex and then
 * also returned {@code true} for <i>any</i> URL containing "duck.ai" or "duckduckgo.com". With only
 * duck.ai that was harmless, but with several providers a script written for one site would also run
 * on duck.ai. That shortcut also hid a second bug: {@code *://*.duck.ai/*} did not match
 * {@code https://duck.ai/} because the pattern required a dot before "duck". This class implements the
 * standard rules instead. It uses only plain Java ({@link java.net.URI}, no Android classes), so it
 * can be tested with fast JVM unit tests (see {@code UserScriptMatcherTest}).</p>
 */
public final class UserScriptMatcher {

    private UserScriptMatcher() {
        // Utility class: only static methods, never instantiated.
    }

    /**
     * @param url           the page URL, e.g. {@code https://duck.ai/chat?q=hi}
     * @param matchPatterns the script's match setting (comma-separated patterns)
     * @return true if at least one pattern matches the URL
     */
    public static boolean matchesAny(String url, String matchPatterns) {
        if (url == null) {
            return false;
        }
        if (matchPatterns == null || matchPatterns.trim().isEmpty()) {
            return isWebUrl(url);
        }
        for (String pattern : matchPatterns.split(",")) {
            if (matches(url, pattern.trim())) {
                return true;
            }
        }
        return false;
    }

    /** Checks one single pattern (no commas) against the URL. */
    static boolean matches(String url, String pattern) {
        if (pattern.isEmpty()) {
            return false; // an empty entry between two commas matches nothing
        }
        if (pattern.equals("*") || pattern.equals("<all_urls>")) {
            return isWebUrl(url);
        }

        int schemeEnd = pattern.indexOf("://");
        if (schemeEnd <= 0) {
            // Not a scheme://host/path pattern: treat the whole thing as a simple wildcard text.
            return wildcardToRegex(pattern).matcher(url).matches();
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            return false; // malformed URL: never inject scripts into something we can't parse
        }
        String urlScheme = lower(uri.getScheme());
        String urlHost = lower(uri.getHost());
        if (urlScheme == null || urlHost == null) {
            return false;
        }

        // 1) scheme
        String patternScheme = lower(pattern.substring(0, schemeEnd));
        if (patternScheme.equals("*")) {
            if (!urlScheme.equals("http") && !urlScheme.equals("https")) {
                return false;
            }
        } else if (!patternScheme.equals(urlScheme)) {
            return false;
        }

        // 2) host (the part between "://" and the first "/")
        String rest = pattern.substring(schemeEnd + 3);
        int slash = rest.indexOf('/');
        String patternHost = lower(slash < 0 ? rest : rest.substring(0, slash));
        int colon = patternHost.indexOf(':');
        if (colon >= 0) {
            patternHost = patternHost.substring(0, colon); // ports are ignored
        }
        if (!hostMatches(urlHost, patternHost)) {
            return false;
        }

        // 3) path + query. A pattern without a path ("https://duck.ai") means "any path".
        String patternPath = slash < 0 ? "/*" : rest.substring(slash);
        String urlPath = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            urlPath = urlPath + "?" + uri.getRawQuery();
        }
        return wildcardToRegex(patternPath).matcher(urlPath).matches();
    }

    private static boolean hostMatches(String urlHost, String patternHost) {
        if (patternHost.equals("*")) {
            return true;
        }
        if (patternHost.startsWith("*.")) {
            String base = patternHost.substring(2);
            return urlHost.equals(base) || urlHost.endsWith("." + base);
        }
        return urlHost.equals(patternHost);
    }

    /** Turns "a*b" into the regex "\Qa\E.*\Qb\E": '*' = any characters, everything else literal. */
    private static Pattern wildcardToRegex(String wildcard) {
        StringBuilder regex = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        for (char c : wildcard.toCharArray()) {
            if (c == '*') {
                if (literal.length() > 0) {
                    regex.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                regex.append(".*");
            } else {
                literal.append(c);
            }
        }
        if (literal.length() > 0) {
            regex.append(Pattern.quote(literal.toString()));
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
    }

    private static boolean isWebUrl(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        return u.startsWith("https://") || u.startsWith("http://");
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
