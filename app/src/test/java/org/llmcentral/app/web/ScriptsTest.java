package org.llmcentral.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/**
 * Checks that the injected-script files and the names in {@link Scripts} stay in sync.
 * If someone renames or deletes a .js file (or adds one but forgets to list it), this test fails on
 * the computer, instead of the app silently injecting an empty script on the phone.
 */
public class ScriptsTest {

    // Gradle runs unit tests with the module folder ("app/") as the working directory.
    private static final File JS_DIR = new File("src/main/assets/" + Scripts.ASSET_DIR);

    @Test
    public void everyListedScriptExistsAndIsNotEmpty() {
        for (String name : Scripts.ALL) {
            File f = new File(JS_DIR, name);
            assertTrue("missing asset: " + f, f.isFile());
            assertTrue("empty asset: " + f, f.length() > 0);
        }
    }

    @Test
    public void everyScriptFileIsListed() {
        Set<String> listed = new HashSet<>(Arrays.asList(Scripts.ALL));
        String[] files = JS_DIR.list((dir, n) -> n.endsWith(".js"));
        assertTrue("assets/js folder not found at " + JS_DIR.getAbsolutePath(), files != null);
        for (String f : files) {
            assertTrue("assets/js/" + f + " is not listed in Scripts.ALL", listed.contains(f));
        }
        assertEquals("duplicate names in Scripts.ALL", listed.size(), Scripts.ALL.length);
    }

    @Test
    public void readUtf8KeepsNonAsciiCharacters() throws Exception {
        // rtl_resolver.js contains Persian-script ranges; they must survive reading byte-for-byte.
        String text = "abc ؀ۿ é 😀";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        assertEquals(text, Scripts.readUtf8(new ByteArrayInputStream(bytes)));
    }
}
