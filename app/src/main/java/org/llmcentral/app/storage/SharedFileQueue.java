package org.llmcentral.app.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Several files shared to the app at once (VERIFICATION.md R4-12): which ones the page's 📎 receives when it asks,
 * and which wait for the next tap. Plain Java (generic, so tests use strings instead of Android's Uri).
 *
 * <p>A web page's file field either takes several files ({@code <input multiple>}, WebView's
 * {@code FileChooserParams.MODE_OPEN_MULTIPLE}) or one. Chromium would pass a one-file field every file we hand
 * over and leave it to the page; so a one-file field gets the first shared file, and each further 📎 tap the next.</p>
 */
public final class SharedFileQueue {

    private SharedFileQueue() {
    }

    /** @return the files to hand to the page now. */
    public static <T> List<T> handOver(List<T> pending, boolean pickerTakesSeveral) {
        if (pending.isEmpty() || pickerTakesSeveral) {
            return new ArrayList<>(pending);
        }
        return Collections.singletonList(pending.get(0));
    }

    /** @return the files still waiting after {@link #handOver}. */
    public static <T> List<T> remaining(List<T> pending, boolean pickerTakesSeveral) {
        if (pending.isEmpty() || pickerTakesSeveral) {
            return Collections.emptyList();
        }
        return new ArrayList<>(pending.subList(1, pending.size()));
    }
}
