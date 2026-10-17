package org.llmcentral.app.web;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** The page's {@code <input accept="…">} as Android file types for the 📎 picker ({@link FileChooserTypes}, R4-13). */
public class FileChooserTypesTest {

    private static final String[] NONE = new String[0];

    // Stand-in for Android's MimeTypeMap (extension → type).
    private static final Function<String, String> TYPES;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("pdf", "application/pdf");
        m.put("png", "image/png");
        m.put("txt", "text/plain");
        TYPES = m::get;
    }

    @Test
    public void noAcceptListMeansEveryFile() {
        assertArrayEquals(NONE, FileChooserTypes.mimeTypes(null, TYPES));
        assertArrayEquals(NONE, FileChooserTypes.mimeTypes(new String[0], TYPES));
        assertArrayEquals(NONE, FileChooserTypes.mimeTypes(new String[]{""}, TYPES));
        assertArrayEquals(NONE, FileChooserTypes.mimeTypes(new String[]{" ", ""}, TYPES));
    }

    @Test
    public void typesAreKeptAndExtensionsLookedUp() {
        assertArrayEquals(new String[]{"image/*", "application/pdf"},
                FileChooserTypes.mimeTypes(new String[]{"image/*", ".PDF"}, TYPES));
    }

    @Test
    public void commaListsAndSpacesAreSplitAndDuplicatesDropped() {
        assertArrayEquals(new String[]{"image/png", "text/plain"},
                FileChooserTypes.mimeTypes(new String[]{" image/png , .txt", ".png"}, TYPES));
    }

    @Test
    public void anUnknownEntryShowsEveryFileRatherThanHidingAllowedOnes() {
        // ".heic" has no known type here: filtering would hide files the page accepts, so no filter at all.
        assertArrayEquals(NONE, FileChooserTypes.mimeTypes(new String[]{"image/*", ".heic"}, TYPES));
        assertArrayEquals(NONE, FileChooserTypes.mimeTypes(new String[]{"audio"}, TYPES));
    }

    @Test
    public void anyFileMeansNoFilter() {
        assertArrayEquals(NONE, FileChooserTypes.mimeTypes(new String[]{"*/*", "image/*"}, TYPES));
    }
}
