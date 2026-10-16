package org.llmcentral.app.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides whether a page's request goes to a tracker: analytics, telemetry or fingerprinting (Settings → Data &amp;
 * tools → "Block trackers"; plan {@code docs/superpowers/plans/2026-10-16-tracker-blocking.md} §6–§7).
 *
 * <p>The rules come from {@code assets/trackers.txt}, one per line:</p>
 * <ul>
 *   <li>{@code sentry.io}: that host <b>and every subdomain</b> ({@code o1.ingest.sentry.io}), never a different
 *       domain that only ends the same way ({@code notsentry.io});</li>
 *   <li>{@code chatgpt.com/ces/v1/}: only addresses on that host (and its subdomains) whose path starts so; the rest
 *       of the site keeps working;</li>
 *   <li>{@code ?ddsource}: any address with that query parameter, whatever the host (Datadog reports sent through a
 *       site's own address);</li>
 *   <li>{@code #} starts a comment; a trailing {@code *} is ignored; anything else (spaces, wildcards) is skipped.</li>
 * </ul>
 *
 * <p>A <b>page load</b> (the "main frame") is never blocked: trackers are never pages, and a blocked page would only
 * show an error. Plain Java and read-only after {@link #parse}, so WebView's background threads can call
 * {@link #shouldBlock} at the same time; TrackerBlockerTest checks it, also against the owner's phone log.</p>
 */
public final class TrackerBlocker {

    private final Set<String> hosts = new HashSet<>();
    private final Map<String, List<String>> paths = new HashMap<>();
    private final Set<String> queryNames = new HashSet<>();

    private TrackerBlocker() {
    }

    /** Reads the rules (the text of {@code trackers.txt}); null or empty text gives a blocker that blocks nothing. */
    public static TrackerBlocker parse(String text) {
        TrackerBlocker blocker = new TrackerBlocker();
        if (text == null) {
            return blocker;
        }
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            while (line.endsWith("*")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("?")) {
                String name = line.substring(1);
                if (name.matches("[A-Za-z0-9_.-]+")) {
                    blocker.queryNames.add(name);
                }
                continue;
            }
            int slash = line.indexOf('/');
            String host = (slash < 0 ? line : line.substring(0, slash)).toLowerCase(Locale.ROOT);
            if (!host.matches("[a-z0-9-]+(\\.[a-z0-9-]+)+")) {
                continue; // not a host name (spaces, wildcards, …)
            }
            if (slash < 0) {
                blocker.hosts.add(host);
            } else {
                String path = line.substring(slash);
                if (path.indexOf(' ') < 0) {
                    blocker.paths.computeIfAbsent(host, h -> new ArrayList<>()).add(path);
                }
            }
        }
        return blocker;
    }

    /** How many rules were read (hosts, paths and query names). */
    public int size() {
        int n = hosts.size() + queryNames.size();
        for (List<String> list : paths.values()) {
            n += list.size();
        }
        return n;
    }

    /**
     * @param mainFrame the request loads the page itself (never blocked)
     * @param host      the address's host, e.g. {@code o1.ingest.sentry.io}
     * @param path      the address's path, e.g. {@code /ces/v1/t} (may be null)
     * @param query     the address's query without "?", e.g. {@code ddsource=browser} (may be null)
     * @return true if the request should not reach the network
     */
    public boolean shouldBlock(boolean mainFrame, String host, String path, String query) {
        if (mainFrame || host == null || host.isEmpty()) {
            return false;
        }
        if (query != null && !queryNames.isEmpty()) {
            for (String part : query.split("&")) {
                int equals = part.indexOf('=');
                if (queryNames.contains(equals < 0 ? part : part.substring(0, equals))) {
                    return true;
                }
            }
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        // The host itself, then each parent domain: o1.ingest.sentry.io → ingest.sentry.io → sentry.io → io.
        while (true) {
            if (hosts.contains(h)) {
                return true;
            }
            List<String> prefixes = paths.get(h);
            if (prefixes != null && path != null) {
                for (String prefix : prefixes) {
                    if (path.startsWith(prefix)) {
                        return true;
                    }
                }
            }
            int dot = h.indexOf('.');
            if (dot < 0) {
                return false;
            }
            h = h.substring(dot + 1);
        }
    }
}
