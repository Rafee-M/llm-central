package org.llmcentral.app.theme;

/**
 * How far the main screen's content must stay from each screen edge (2026-10-15). Used by
 * {@link ThemeManager#fitContent}.
 *
 * <p><b>Background.</b> Android tells the app how much of each edge is covered: by the status and
 * navigation bars, by the keyboard, and by the camera cut-out (a notch or punch hole). The layout
 * attribute {@code android:fitsSystemWindows="true"} pads the content by all of them together
 * (verified in the Android source, {@code InsetsState.calculateInsets}: system bars + display cut-out,
 * + keyboard for "adjustResize" screens). So when "Hide status bar" removed the status bar, the
 * cut-out still padded the top: an empty strip stayed where the status bar had been.</p>
 *
 * <p><b>The rule now.</b> The same padding, except at the top while the status bar is hidden: then
 * the cut-out is ignored there and the app's top bar moves up into the freed strip. Sides and bottom
 * always keep clear of the cut-out (it is on a side when the phone is turned), the navigation bar
 * and the keyboard.</p>
 *
 * <p><b>Bar at the bottom</b> (Settings → Appearance, 2026-10-31): then the web page, not the app's bar, would
 * move into the freed strip, and a page's header must not sit under the punch hole; so the cut-out pads the
 * top again (only the cut-out's own height: a phone without one keeps using the whole strip).</p>
 *
 * <p>Plain Java (numbers in pixels, order left, top, right, bottom), so ContentInsetsTest checks it
 * on the computer.</p>
 */
public final class ContentInsets {

    private ContentInsets() {
        // Only static methods.
    }

    /**
     * @param statusBarVisible whether the status bar is on screen right now
     * @param barOnTop         whether the app's bar is the top of the content (false = "Top bar at the bottom")
     * @param bars             space taken by the system bars and the keyboard: left, top, right, bottom
     * @param cutout           space taken by the camera cut-out: left, top, right, bottom
     * @return the padding for the content: left, top, right, bottom
     */
    public static int[] padding(boolean statusBarVisible, boolean barOnTop, int[] bars, int[] cutout) {
        return new int[]{
                Math.max(bars[0], cutout[0]),
                statusBarVisible || !barOnTop ? Math.max(bars[1], cutout[1]) : bars[1],
                Math.max(bars[2], cutout[2]),
                Math.max(bars[3], cutout[3]),
        };
    }
}
