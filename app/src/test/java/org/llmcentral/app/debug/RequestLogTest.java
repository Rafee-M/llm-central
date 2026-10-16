package org.llmcentral.app.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The debug build's request log (what the phone measurements for tracker blocking contain). */
public class RequestLogTest {

    private static final long T0 = 1_000_000L;

    private static RequestLog log() {
        return new RequestLog(T0);
    }

    private static void page(RequestLog log, String url, long at) {
        log.record("chatgpt", "chatgpt", RequestLog.PAGE, url, "GET", false, "chatgpt.com", false, at);
    }

    /** The data rows of an export (without the "#" lines and the column header). */
    private static List<String[]> rows(RequestLog log) {
        List<String[]> rows = new ArrayList<>();
        for (String line : log.export("test", T0 + 60_000).split("\n")) {
            if (!line.startsWith("#") && !line.startsWith("account\t") && !line.isEmpty()) {
                rows.add(line.split("\t", -1));
            }
        }
        return rows;
    }

    @Test
    public void keepsOnlyHostAndFirstPathSegment() {
        RequestLog log = log();
        page(log, "https://ab.chatgpt.com/v1/rgstr/secret-chat?k=client-key&q=hello#frag", T0);
        String export = log.export("test", T0);
        assertTrue(export.contains("ab.chatgpt.com"));
        assertTrue(export.contains("/v1"));
        // Nothing after the first segment, no query, no fragment: the log is pasted into a chat.
        assertFalse(export.contains("rgstr"));
        assertFalse(export.contains("secret-chat"));
        assertFalse(export.contains("client-key"));
        assertFalse(export.contains("hello"));
        assertFalse(export.contains("frag"));
    }

    @Test
    public void idLikeFirstSegmentsAreHidden() {
        RequestLog log = log();
        page(log, "https://files.oaiusercontent.com/file-AbC123dEf456?x=1", T0);
        page(log, "https://files.oaiusercontent.com/", T0);
        String[] row = rows(log).get(0);
        assertEquals("/{id},/", row[12]);
    }

    @Test
    public void countsPerAccountSourceAndHost() {
        RequestLog log = log();
        page(log, "https://chatgpt.com/backend-api/me", T0 + 2_000);
        log.record("chatgpt", "chatgpt", RequestLog.PAGE, "https://chatgpt.com/", "GET", true, "chatgpt.com", false,
                T0 + 5_000);
        log.record("chatgpt", "chatgpt", RequestLog.PAGE, "https://chatgpt.com/backend-api/x", "post", false,
                "chatgpt.com", true, T0 + 9_000);
        log.record("chatgpt", "chatgpt", RequestLog.WORKER, "https://chatgpt.com/sw.js", "GET", false, null, false,
                T0 + 10_000);
        List<String[]> rows = rows(log);
        assertEquals(2, rows.size());
        String[] page = rows.get(0);
        // account provider source host party requests page_loads blocked methods first_s last_s page_hosts paths
        assertEquals("chatgpt", page[0]);
        assertEquals("chatgpt", page[1]);
        assertEquals("page", page[2]);
        assertEquals("chatgpt.com", page[3]);
        assertEquals("first", page[4]);
        assertEquals("3", page[5]);
        assertEquals("1", page[6]);
        assertEquals("1", page[7]);
        assertEquals("GET,POST", page[8]);
        assertEquals("2", page[9]);
        assertEquals("9", page[10]);
        assertEquals("chatgpt.com", page[11]);
        assertEquals("/backend-api,/", page[12]);
        String[] worker = rows.get(1);
        assertEquals("worker", worker[2]);
        assertEquals("-", worker[11]); // service workers have no page
        assertEquals("/sw.js", worker[12]);
    }

    @Test
    public void partyComesFromTheProvidersOwnHosts() {
        RequestLog log = log();
        // "first" = the provider's own domains (ProviderSpec.ownsHost: chatgpt.com, openai.com, its sign-in hosts).
        // Its CDN on another domain (oaistatic.com) is third-party, as a browser sees it.
        page(log, "https://ab.chatgpt.com/v1", T0);
        page(log, "https://cdn.oaistatic.com/a.js", T0);
        log.record("x", "no-such-provider", RequestLog.PAGE, "https://a.example/", "GET", false, "", false, T0);
        for (String[] row : rows(log)) {
            String expected = row[3].equals("ab.chatgpt.com") ? "first"
                    : row[3].equals("cdn.oaistatic.com") ? "third" : "?";
            assertEquals(row[3], expected, row[4]);
        }
    }

    @Test
    public void pageHostsAndPathsAreCapped() {
        RequestLog log = log();
        for (int i = 0; i < 20; i++) {
            log.record("chatgpt", "chatgpt", RequestLog.PAGE, "https://t.example/p" + (char) ('a' + i), "GET", false,
                    "h" + i + ".example", false, T0);
        }
        String[] row = rows(log).get(0);
        assertEquals(RequestLog.MAX_PAGE_HOSTS, row[11].split(",").length);
        assertEquals(RequestLog.MAX_PATHS, row[12].split(",").length);
    }

    @Test
    public void rowsAreCappedAndTheRestCountedAsDropped() {
        RequestLog log = log();
        for (int i = 0; i < RequestLog.MAX_ROWS + 5; i++) {
            page(log, "https://h" + i + ".example/", T0);
        }
        assertEquals(RequestLog.MAX_ROWS, rows(log).size());
        assertEquals(5, log.dropped());
        assertTrue(log.export("test", T0).contains("dropped 5"));
        page(log, "https://h1.example/", T0); // an existing row still counts
        assertEquals(5, log.dropped());
    }

    @Test
    public void ignoresWhatIsNotAWebAddress() {
        RequestLog log = log();
        page(log, "data:text/plain,hi", T0);
        page(log, "blob:https://chatgpt.com/123", T0);
        page(log, "about:blank", T0);
        page(log, "https:///nohost", T0);
        page(log, "not a url at all", T0);
        page(log, null, T0);
        assertEquals(0, rows(log).size());
        page(log, "HTTP://User:Pw@Example.COM:8443/A", T0);
        String[] row = rows(log).get(0);
        assertEquals("example.com", row[3]); // lower case, no user info, no port
        assertEquals("/A", row[12]);
    }

    @Test
    public void tabsAndLineBreaksCannotBreakTheTable() {
        RequestLog log = log();
        log.record("acc\tx\ny", "chatgpt", RequestLog.PAGE, "https://chatgpt.com/", "G\tET", false, "p\nage", false, T0);
        List<String[]> rows = rows(log);
        assertEquals(1, rows.size());
        assertEquals(13, rows.get(0).length);
    }

    @Test
    public void exportHasHeaderColumnsAndIsSorted() {
        RequestLog log = log();
        page(log, "https://b.example/", T0);
        page(log, "https://a.example/", T0);
        page(log, "https://a.example/", T0);
        log.record("claude", "claude", RequestLog.PAGE, "https://claude.ai/", "GET", false, "claude.ai", false, T0);
        String export = log.export("app 1.0", T0 + 61_000);
        String[] lines = export.split("\n");
        assertTrue(lines[0].startsWith("# LLM Central request log v1"));
        assertTrue(lines[1].contains("app 1.0"));
        assertTrue(lines[1].contains("61 s"));
        assertEquals("account\tprovider\tsource\thost\tparty\trequests\tpage_loads\tblocked\tmethods\tfirst_s\t"
                + "last_s\tpage_hosts\tpaths", lines[2]);
        List<String[]> rows = rows(log);
        assertEquals("a.example", rows.get(0)[3]); // most requests first within an account
        assertEquals("b.example", rows.get(1)[3]);
        assertEquals("claude", rows.get(2)[0]);
    }

    @Test
    public void clearStartsOver() {
        RequestLog log = log();
        page(log, "https://a.example/", T0);
        log.clear(T0 + 100_000);
        assertEquals(0, rows(log).size());
        assertEquals(0, log.dropped());
        page(log, "https://a.example/", T0 + 103_000);
        assertEquals("3", rows(log).get(0)[9]); // seconds count from the clear
    }

    @Test
    public void summaryCountsAccountsHostsAndRequests() {
        RequestLog log = log();
        page(log, "https://a.example/", T0);
        page(log, "https://a.example/", T0);
        log.record("claude", "claude", RequestLog.PAGE, "https://claude.ai/", "GET", false, "claude.ai", false, T0);
        RequestLog.Summary s = log.summary(T0 + 5_000);
        assertEquals(2, s.accounts);
        assertEquals(2, s.rows);
        assertEquals(3, s.requests);
        assertEquals(5, s.seconds);
    }

    @Test
    public void recordsFromManyThreadsAreAllCounted() throws InterruptedException {
        RequestLog log = log();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < 1_000; i++) {
                    page(log, "https://a.example/x", T0);
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertEquals("8000", rows(log).get(0)[5]);
    }
}
