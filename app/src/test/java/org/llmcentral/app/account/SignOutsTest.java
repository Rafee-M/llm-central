package org.llmcentral.app.account;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** The "signed out at" record both screens (main and assistant drawer) read to close a signed-out tab. */
public class SignOutsTest {

    private static final long DAY = 24L * 60 * 60 * 1000;

    @Test
    public void onlySignOutsAfterTheLastLookCount() {
        String json = SignOuts.record("{}", "work", 2 * DAY + 100);
        assertEquals(Collections.singletonList("work"), SignOuts.since(json, 2 * DAY + 50));
        assertEquals(Collections.emptyList(), SignOuts.since(json, 2 * DAY + 100));
    }

    @Test
    public void severalAccountsAndUpdates() {
        String json = SignOuts.record("{}", "a", 2 * DAY + 10);
        json = SignOuts.record(json, "b", 2 * DAY + 20);
        json = SignOuts.record(json, "a", 2 * DAY + 30);   // signed out again later: newest time wins
        java.util.List<String> ids = SignOuts.since(json, 2 * DAY + 15);
        Collections.sort(ids);
        assertEquals(Arrays.asList("a", "b"), ids);
        assertEquals(Collections.singletonList("a"), SignOuts.since(json, 2 * DAY + 25));
        assertEquals(2 * DAY + 30, SignOuts.latest(json, 0));
        assertEquals(5 * DAY, SignOuts.latest(json, 5 * DAY));
    }

    @Test
    public void oldEntriesAreDroppedWhenRecording() {
        String json = SignOuts.record("{}", "old", 10);
        json = SignOuts.record(json, "new", 3 * DAY);
        assertEquals(Collections.singletonList("new"), SignOuts.since(json, 0));
    }

    @Test
    public void brokenOrMissingDataIsEmpty() {
        assertEquals(Collections.emptyList(), SignOuts.since(null, 0));
        assertEquals(Collections.emptyList(), SignOuts.since("nope", 0));
        assertEquals(Collections.singletonList("a"), SignOuts.since(SignOuts.record("nope", "a", DAY), 0));
        assertEquals(7, SignOuts.latest("nope", 7));
    }
}
