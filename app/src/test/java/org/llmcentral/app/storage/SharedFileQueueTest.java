package org.llmcentral.app.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Which shared files the page's 📎 receives now and which wait ({@link SharedFileQueue}, VERIFICATION.md R4-12). */
public class SharedFileQueueTest {

    private static final List<String> THREE = Arrays.asList("a.jpg", "b.jpg", "c.pdf");

    @Test
    public void aPickerForSeveralFilesGetsThemAll() {
        assertEquals(THREE, SharedFileQueue.handOver(THREE, true));
        assertTrue(SharedFileQueue.remaining(THREE, true).isEmpty());
    }

    @Test
    public void aPickerForOneFileGetsTheFirstAndTheRestWait() {
        // The page's file field takes one file: giving it three would leave the choice to the page (Chromium passes
        // them all); instead each 📎 tap attaches the next shared file.
        assertEquals(Collections.singletonList("a.jpg"), SharedFileQueue.handOver(THREE, false));
        assertEquals(Arrays.asList("b.jpg", "c.pdf"), SharedFileQueue.remaining(THREE, false));
    }

    @Test
    public void oneSharedFileGoesToAnyPicker() {
        List<String> one = Collections.singletonList("a.jpg");
        assertEquals(one, SharedFileQueue.handOver(one, false));
        assertTrue(SharedFileQueue.remaining(one, false).isEmpty());
    }

    @Test
    public void nothingSharedHandsOverNothing() {
        List<String> none = Collections.emptyList();
        assertTrue(SharedFileQueue.handOver(none, true).isEmpty());
        assertTrue(SharedFileQueue.remaining(none, false).isEmpty());
    }
}
