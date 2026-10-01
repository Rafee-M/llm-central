package org.llmcentral.app.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

/** Checks when copies of attached files are deleted at app start ({@link UploadCopies}). */
public class UploadCopiesTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static File copy(File uploads, String name, long modified) throws Exception {
        File folder = new File(uploads, name);
        assertTrue(folder.mkdirs());
        assertTrue(new File(folder, "file.zip").createNewFile());
        assertTrue(folder.setLastModified(modified));
        return folder;
    }

    @Test
    public void atStartCopiesFromEarlierRunsGoButNewOnesStayAndItRunsOncePerProcess() throws Exception {
        UploadCopies.resetForTest();
        File uploads = tmp.newFolder("uploads");
        long start = System.currentTimeMillis();
        File earlierRun = copy(uploads, "1_1", start - 5_000);   // made before this app start
        File thisRun = copy(uploads, "2_2", start + 5_000);      // attached while the job waited

        assertEquals(1, UploadCopies.cleanUpAtStart(uploads, start));
        assertFalse(earlierRun.exists());
        assertTrue(thisRun.exists());

        // Same process again (screen rebuilt, drawer opened): copies of this run must survive.
        assertEquals(0, UploadCopies.cleanUpAtStart(uploads, start + 60_000));
        assertTrue(thisRun.exists());
    }
}
