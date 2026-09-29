package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

/** JVM unit tests for {@link TabLru} (PROJECT.md R-PERF-1). */
public class TabLruTest {

    @Test
    public void nothingClosedWhileUnderTheLimit() {
        TabLru lru = new TabLru();
        lru.touch("duck");
        lru.touch("chatgpt");
        lru.touch("claude");
        assertEquals(Collections.emptyList(), lru.idsToClose(3, "duck"));
    }

    @Test
    public void leastRecentlyUsedIsClosedFirst() {
        TabLru lru = new TabLru();
        lru.touch("duck");
        lru.touch("chatgpt");
        lru.touch("claude");
        lru.touch("deepseek");      // 4 loaded, limit 3
        assertEquals(Arrays.asList("chatgpt"), lru.idsToClose(3, "duck"));
    }

    @Test
    public void usingATabAgainMakesItRecent() {
        TabLru lru = new TabLru();
        lru.touch("duck");
        lru.touch("chatgpt");
        lru.touch("claude");
        lru.touch("chatgpt");       // chatgpt used again → claude is now the oldest non-pinned tab
        lru.touch("deepseek");
        assertEquals(Arrays.asList("claude"), lru.idsToClose(3, "duck"));
    }

    @Test
    public void pinnedAndCurrentTabsAreNeverClosed() {
        TabLru lru = new TabLru();
        lru.touch("duck");          // pinned, and the oldest
        lru.touch("chatgpt");       // current
        // Limit 1: only one tab "should" stay, but neither the pinned nor the current may close.
        assertEquals(Collections.emptyList(), lru.idsToClose(1, "duck"));
    }

    @Test
    public void limitIsClampedToOneToFive() {
        TabLru lru = new TabLru();
        for (String id : new String[]{"duck", "a", "b", "c", "d", "e", "f"}) {
            lru.touch(id);
        }
        // 7 loaded; 99 is treated as 5 → close the 2 oldest non-pinned ("a", "b").
        assertEquals(Arrays.asList("a", "b"), lru.idsToClose(99, "duck"));
        // 0 is treated as 1 → close everything except pinned "duck" and current "f".
        assertEquals(Arrays.asList("a", "b", "c", "d", "e"), lru.idsToClose(0, "duck"));
    }

    @Test
    public void removeForgetsATab() {
        TabLru lru = new TabLru();
        lru.touch("duck");
        lru.touch("claude");
        assertTrue(lru.contains("claude"));
        lru.remove("claude");
        assertFalse(lru.contains("claude"));
    }
}
