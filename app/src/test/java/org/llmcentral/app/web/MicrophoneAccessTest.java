package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.llmcentral.app.web.MicrophoneAccess.Decision;

/** Checks when a page gets the microphone ({@link MicrophoneAccess#decide}). Runs on the computer. */
public class MicrophoneAccessTest {

    @Test
    public void providersPageGetsItOrAndroidIsAskedFirst() {
        assertEquals(Decision.GRANT, MicrophoneAccess.decide(true, true, true));
        // The app doesn't have Android's permission yet: ask now (not at app start), then answer.
        assertEquals(Decision.ASK_ANDROID, MicrophoneAccess.decide(true, true, false));
    }

    @Test
    public void otherSitesAndOtherRequestsAreDeniedWithoutAsking() {
        // Embedded content from another site never gets the microphone, and never triggers the dialog.
        assertEquals(Decision.DENY, MicrophoneAccess.decide(false, true, true));
        assertEquals(Decision.DENY, MicrophoneAccess.decide(false, true, false));
        // A request without audio (e.g. camera only) is denied.
        assertEquals(Decision.DENY, MicrophoneAccess.decide(true, false, true));
        assertEquals(Decision.DENY, MicrophoneAccess.decide(true, false, false));
    }
}
