package org.llmcentral.app.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Replacing a saved file without ever losing the old copy ({@link AtomicFiles}, VERIFICATION.md R4-6). */
public class AtomicFilesTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void writesANewFile() throws Exception {
        File target = new File(tmp.getRoot(), "chats.json");
        AtomicFiles.writeText(target, "{\"a\":1}");
        assertEquals("{\"a\":1}", read(target));
    }

    @Test
    public void replacesAnOldCopyAndLeavesNoTemporaryFile() throws Exception {
        File target = tmp.newFile("chats.json");
        Files.write(target.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        AtomicFiles.writeText(target, "new ünïcödé");
        assertEquals("new ünïcödé", read(target));
        assertFalse(new File(tmp.getRoot(), "chats.json.tmp").exists());
    }

    @Test
    public void aFailedReplaceKeepsTheOldCopyAndCleansUp() throws Exception {
        // A target that can't be replaced (here: a folder with a file in it) makes the final rename fail. The
        // old content must still be there, the half-done copy gone, and the caller told (exception).
        File target = tmp.newFolder("chats.json");
        File inside = new File(target, "keep.txt");
        Files.write(inside.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        try {
            AtomicFiles.writeText(target, "new");
            fail("expected an IOException");
        } catch (IOException expected) {
            // fine
        }
        assertTrue(target.isDirectory());
        assertEquals("old", read(inside));
        assertFalse(new File(tmp.getRoot(), "chats.json.tmp").exists());
    }
}
