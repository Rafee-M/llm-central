package org.llmcentral.app.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Replaces a saved file so that, whatever happens, either the old or the new copy is on disk (VERIFICATION.md
 * R4-6). Plain Java, unit-tested with real files (AtomicFilesTest).
 *
 * <p><b>How:</b> the new text goes into "&lt;name&gt;.tmp" first; then one {@code rename} puts it in place.
 * On Android (Linux) a rename over an existing file swaps it in one step ("atomic"), so there is never a moment
 * without a complete file. The old code deleted the old copy first and then renamed, without checking the
 * result: if the app was stopped between the two, or the rename failed, the only full copy of the saved chats
 * was gone.</p>
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    /**
     * Writes {@code text} (UTF-8) to {@code target}, replacing it in one step.
     *
     * @throws IOException if writing or the final rename failed; the old file is then unchanged and the
     *                     temporary copy deleted
     */
    public static void writeText(File target, String text) throws IOException {
        File temp = new File(target.getPath() + ".tmp");
        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) {
                writer.write(text);
            }
            if (!temp.renameTo(target)) {
                throw new IOException("Could not replace " + target.getName());
            }
        } catch (IOException | RuntimeException e) {
            temp.delete();
            throw e;
        }
    }
}
