package org.llmcentral.app.debug;

import org.llmcentral.app.provider.ProviderSpec;
import org.llmcentral.app.provider.Providers;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

/**
 * <b>Debug builds only:</b> which servers each account's pages contact, so the tracker list can be built from
 * what the sites really load inside WebView on the phone (plan {@code 2026-10-16-tracker-blocking.md} §4).
 *
 * <p>Only {@code RequestLogHooks} uses this class, and only behind {@code BuildConfig.DEBUG}. In a release build
 * that flag is the constant {@code false}, so the calls are left out by the compiler and R8 removes this class:
 * release builds don't contain it (checked by comparing the release APK before and after, CHANGES.md).</p>
 *
 * <p><b>Privacy:</b> the export is pasted into a chat, so it holds the host name and only the <i>first</i> path
 * segment of each address ({@code /backend-api}, never the rest, never the query); a segment that looks like an id
 * becomes {@code /{id}}. No headers, bodies or cookies are ever seen here.</p>
 *
 * <p>Plain Java (no Android classes), so RequestLogTest checks it on the computer. Thread-safe: WebView calls
 * the hooks on several background threads at once.</p>
 */
public final class RequestLog {

    /** A tab's own page requests ({@code WebViewClient.shouldInterceptRequest}). */
    public static final String PAGE = "page";
    /** Requests of an account's service worker (a page's background script). */
    public static final String WORKER = "worker";

    public static final int MAX_ROWS = 3_000;
    public static final int MAX_PAGE_HOSTS = 6;
    public static final int MAX_PATHS = 8;

    private static final String COLUMNS = "account\tprovider\tsource\thost\tparty\trequests\tpage_loads\tblocked\t"
            + "methods\tfirst_s\tlast_s\tpage_hosts\tpaths";

    private static RequestLog instance;

    /** One line of the export: one host, seen by one account's pages or service worker. */
    private static final class Row {
        final String account;
        final String provider;
        final String source;
        final String host;
        final String party;
        long requests;
        long pageLoads;
        long blocked;
        final Set<String> methods = new TreeSet<>();
        final long firstMillis;
        long lastMillis;
        final Set<String> pageHosts = new LinkedHashSet<>();
        final Set<String> paths = new LinkedHashSet<>();

        Row(String account, String provider, String source, String host, String party, long now) {
            this.account = account;
            this.provider = provider;
            this.source = source;
            this.host = host;
            this.party = party;
            this.firstMillis = now;
            this.lastMillis = now;
        }
    }

    private final Map<String, Row> rows = new HashMap<>();
    private long startMillis;
    private int dropped;

    public RequestLog(long startMillis) {
        this.startMillis = startMillis;
    }

    /** The one log of this app process (debug builds), shared by the main screen, the drawer and the log screen. */
    public static synchronized RequestLog get() {
        if (instance == null) {
            instance = new RequestLog(System.currentTimeMillis());
        }
        return instance;
    }

    /**
     * Counts one request. Anything that isn't an http(s) address with a host (data:, blob:, about:…) is ignored.
     *
     * @param source   {@link #PAGE} or {@link #WORKER}
     * @param pageHost host of the page the tab showed (null for service workers)
     * @param blocked  the request was answered by the app instead of the network (tracker blocking, later)
     */
    public synchronized void record(String accountId, String providerId, String source, String url, String method,
                                    boolean mainFrame, String pageHost, boolean blocked, long nowMillis) {
        String host = hostOf(url);
        if (host == null) {
            return;
        }
        String account = clean(accountId);
        String key = account + '\t' + source + '\t' + host;
        Row row = rows.get(key);
        if (row == null) {
            if (rows.size() >= MAX_ROWS) {
                dropped++;
                return;
            }
            row = new Row(account, clean(providerId), clean(source), host, partyOf(providerId, host), nowMillis);
            rows.put(key, row);
        }
        row.requests++;
        if (mainFrame) {
            row.pageLoads++;
        }
        if (blocked) {
            row.blocked++;
        }
        String m = clean(method).toUpperCase(Locale.ROOT);
        if (!m.isEmpty() && row.methods.size() < 8) {
            row.methods.add(m.length() > 10 ? m.substring(0, 10) : m);
        }
        row.lastMillis = Math.max(row.lastMillis, nowMillis);
        String page = clean(pageHost);
        if (!page.isEmpty() && row.pageHosts.size() < MAX_PAGE_HOSTS) {
            row.pageHosts.add(page);
        }
        if (row.paths.size() < MAX_PATHS) {
            row.paths.add(firstSegment(url));
        }
    }

    /** Starts over (the log screen's Clear). */
    public synchronized void clear(long nowMillis) {
        rows.clear();
        dropped = 0;
        startMillis = nowMillis;
    }

    /** How many new hosts didn't fit any more ({@link #MAX_ROWS}). */
    public synchronized int dropped() {
        return dropped;
    }

    /** Numbers for the log screen. */
    public static final class Summary {
        public final int accounts;
        public final int rows;
        public final long requests;
        public final long seconds;
        public final int dropped;

        Summary(int accounts, int rows, long requests, long seconds, int dropped) {
            this.accounts = accounts;
            this.rows = rows;
            this.requests = requests;
            this.seconds = seconds;
            this.dropped = dropped;
        }
    }

    public synchronized Summary summary(long nowMillis) {
        Set<String> accounts = new HashSet<>();
        long requests = 0;
        for (Row row : rows.values()) {
            accounts.add(row.account);
            requests += row.requests;
        }
        return new Summary(accounts.size(), rows.size(), requests, seconds(nowMillis), dropped);
    }

    /**
     * The log as tab-separated text (format v1, plan §4): two "#" lines (what was measured), the column names,
     * then one row per account, source and host, sorted by account, source, most requests first.
     *
     * @param about app, Android and WebView versions (written by the caller, which knows them)
     */
    public synchronized String export(String about, long nowMillis) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        StringBuilder out = new StringBuilder();
        out.append("# LLM Central request log v1 (debug build)\n");
        out.append("# ").append(clean(about)).append(" · started ").append(iso.format(new Date(startMillis)))
                .append(" · ").append(seconds(nowMillis)).append(" s · dropped ").append(dropped).append('\n');
        out.append(COLUMNS).append('\n');
        List<Row> sorted = new ArrayList<>(rows.values());
        Collections.sort(sorted, (a, b) -> {
            int c = a.account.compareTo(b.account);
            if (c == 0) {
                c = a.source.compareTo(b.source);
            }
            if (c == 0) {
                c = Long.compare(b.requests, a.requests);
            }
            return c != 0 ? c : a.host.compareTo(b.host);
        });
        for (Row row : sorted) {
            out.append(row.account).append('\t').append(row.provider).append('\t').append(row.source).append('\t')
                    .append(row.host).append('\t').append(row.party).append('\t').append(row.requests).append('\t')
                    .append(row.pageLoads).append('\t').append(row.blocked).append('\t')
                    .append(String.join(",", row.methods)).append('\t')
                    .append((row.firstMillis - startMillis) / 1000).append('\t')
                    .append((row.lastMillis - startMillis) / 1000).append('\t')
                    .append(row.pageHosts.isEmpty() ? "-" : String.join(",", row.pageHosts)).append('\t')
                    .append(String.join(",", row.paths)).append('\n');
        }
        return out.toString();
    }

    private long seconds(long nowMillis) {
        return Math.max(0, (nowMillis - startMillis) / 1000);
    }

    /** "first" if the host is one of the provider's own domains (ProviderSpec.ownsHost), "third" if not. */
    private static String partyOf(String providerId, String host) {
        ProviderSpec provider = providerId == null ? null : Providers.find(providerId);
        if (provider == null) {
            return "?";
        }
        return provider.ownsHost(host) ? "first" : "third";
    }

    /** @return the lower-case host of an http(s) address (no user info, no port), or null. */
    static String hostOf(String url) {
        if (url == null) {
            return null;
        }
        int schemeEnd = url.indexOf("://");
        if (schemeEnd <= 0) {
            return null;
        }
        String scheme = url.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return null;
        }
        int start = schemeEnd + 3;
        int end = start;
        while (end < url.length() && "/?#".indexOf(url.charAt(end)) < 0) {
            end++;
        }
        String authority = url.substring(start, end);
        authority = authority.substring(authority.lastIndexOf('@') + 1); // user info
        String host;
        if (authority.startsWith("[")) { // IPv6 literal
            int close = authority.indexOf(']');
            host = close > 0 ? authority.substring(0, close + 1) : "";
        } else {
            int colon = authority.indexOf(':');
            host = colon >= 0 ? authority.substring(0, colon) : authority;
        }
        host = clean(host).toLowerCase(Locale.ROOT);
        return host.isEmpty() || host.indexOf(' ') >= 0 ? null : host;
    }

    /**
     * @return "/" + the first path segment ({@code /backend-api}), "/" for none, "/{id}" for a segment that looks
     *         like an id (more than 24 characters or at least 4 digits), so no file or chat id is ever kept.
     */
    static String firstSegment(String url) {
        int start = url.indexOf("://") + 3;
        int slash = start;
        while (slash < url.length() && "/?#".indexOf(url.charAt(slash)) < 0) {
            slash++;
        }
        if (slash >= url.length() || url.charAt(slash) != '/') {
            return "/";
        }
        int end = slash + 1;
        while (end < url.length() && "/?#".indexOf(url.charAt(end)) < 0) {
            end++;
        }
        String segment = clean(url.substring(slash + 1, end));
        int digits = 0;
        for (int i = 0; i < segment.length(); i++) {
            if (Character.isDigit(segment.charAt(i))) {
                digits++;
            }
        }
        if (segment.length() > 24 || digits >= 4) {
            return "/{id}";
        }
        return "/" + segment.replace(",", "%2C");
    }

    /** Tabs and line breaks would break the table: replaced by spaces; null becomes "". */
    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }
}
