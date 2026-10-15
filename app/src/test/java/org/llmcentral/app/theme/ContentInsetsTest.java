package org.llmcentral.app.theme;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/** The main screen's padding with and without the status bar ({@link ContentInsets}). */
public class ContentInsetsTest {

    // A phone held upright: 32 px cut-out at the top (punch hole), status bar 32 px, navigation bar 48 px.
    private static final int[] CUTOUT_TOP = {0, 32, 0, 0};

    @Test
    public void statusBarShownKeepsTheOldPadding() {
        // Same as android:fitsSystemWindows: the bigger of bars and cut-out on every side.
        assertArrayEquals(new int[]{0, 32, 0, 48},
                ContentInsets.padding(true, new int[]{0, 32, 0, 48}, CUTOUT_TOP));
    }

    @Test
    public void statusBarHiddenUsesTheStripAtTheTop() {
        // Hidden: the status bar reports 0 at the top, and the cut-out no longer pads it.
        assertArrayEquals(new int[]{0, 0, 0, 48},
                ContentInsets.padding(false, new int[]{0, 0, 0, 48}, CUTOUT_TOP));
    }

    @Test
    public void keyboardStillPushesTheContentUp() {
        // The keyboard (900 px) counts with the bars at the bottom, shown or hidden status bar alike.
        assertArrayEquals(new int[]{0, 0, 0, 900},
                ContentInsets.padding(false, new int[]{0, 0, 0, 900}, CUTOUT_TOP));
    }

    @Test
    public void sideCutoutIsAlwaysAvoidedWhenTurned() {
        // Phone turned: the cut-out is on the left; content keeps clear of it even with the bar hidden.
        assertArrayEquals(new int[]{40, 0, 0, 0},
                ContentInsets.padding(false, new int[]{0, 0, 0, 0}, new int[]{40, 0, 0, 0}));
    }
}
