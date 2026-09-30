package org.llmcentral.app.downloads;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.llmcentral.app.provider.Providers;

/** Checks download file names and folders ({@link DownloadNames}). Runs on the computer. */
public class DownloadNamesTest {

    private static final String TS = "20260930_184500";

    /**
     * duckAssist's original MainActivity.formatMarkdownFilename, copied unchanged (with the timestamp
     * passed in), used as the reference: duck.ai's names must not change in round 6.
     */
    private static String oldDuckName(String filename, String timestamp) {
        if (filename == null || filename.isEmpty()) {
            return "Duck_AI_Chat_" + timestamp + ".md";
        }
        if (filename.toLowerCase().endsWith(".txt")) {
            filename = filename.substring(0, filename.length() - 4) + ".md";
        } else if (filename.toLowerCase().endsWith(".text")) {
            filename = filename.substring(0, filename.length() - 5) + ".md";
        } else if (filename.toLowerCase().endsWith(".bin")) {
            filename = filename.substring(0, filename.length() - 4) + ".md";
        }
        if (!filename.contains(".")) {
            filename += ".md";
        }
        int dotIndex = filename.lastIndexOf('.');
        String baseName = (dotIndex > 0) ? filename.substring(0, dotIndex).trim() : filename;
        String ext = (dotIndex > 0) ? filename.substring(dotIndex) : "";
        if (baseName.equalsIgnoreCase("download") || baseName.equalsIgnoreCase("chat") || baseName.equalsIgnoreCase("export") || baseName.equalsIgnoreCase("duckduckgo_chat") || baseName.equalsIgnoreCase("duck_ai_chat")) {
            filename = "Duck_AI_Chat_" + timestamp + ext;
        } else if (ext.equalsIgnoreCase(".md") && !baseName.matches(".*_\\d{8}_\\d{6}$")) {
            filename = baseName + "_" + timestamp + ext;
        }
        return filename;
    }

    @Test
    public void duckNamesAreExactlyAsBefore() {
        String[] names = {null, "", "chat.txt", "Chat.TXT", "notes.text", "blob.bin", "download", "export.md",
                "duckduckgo_chat.txt", "duck_ai_chat.md", "My Chat_20250101_120000.md", "report.pdf",
                "image.png", "summary", "a.b.txt", "archive.tar.gz", "Übersicht.txt", "x"};
        for (String name : names) {
            assertEquals("for " + name, oldDuckName(name, TS), DownloadNames.duckName(name, TS));
            // suggested() only adds cleaning, which changes nothing for these names
            assertEquals("for " + name, oldDuckName(name, TS), DownloadNames.suggested(Providers.DUCK, name, TS));
        }
    }

    @Test
    public void otherProvidersKeepTheWebsitesNameAndGetTheirOwnFolder() {
        assertEquals("data.csv", DownloadNames.suggested(Providers.CHATGPT, "data.csv", TS));
        assertEquals("export.txt", DownloadNames.suggested(Providers.CLAUDE, "export.txt", TS)); // not .md
        assertEquals("DeepSeek_download_" + TS, DownloadNames.suggested(Providers.DEEPSEEK, "", TS));
        assertEquals("duck.ai", DownloadNames.folder(Providers.DUCK));
        assertEquals("ChatGPT", DownloadNames.folder(Providers.CHATGPT));
        assertEquals("Z.ai", DownloadNames.folder(Providers.ZAI));
    }

    @Test
    public void namesCanNeverLeaveTheFolder() {
        assertEquals("_.._etc_passwd", DownloadNames.clean("/../etc/passwd", "x"));
        assertEquals("a_b_c", DownloadNames.clean("a\\b:c", "x"));
        assertEquals("hidden", DownloadNames.clean("..hidden", "x"));
        assertEquals("x", DownloadNames.clean("...", "x"));
        assertEquals("x", DownloadNames.clean("  ", "x"));
        assertEquals("line_break.csv", DownloadNames.clean("line\nbreak.csv", "x"));
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longName.append('a');
        }
        String cut = DownloadNames.clean(longName + ".csv", "x");
        assertEquals(DownloadNames.MAX_NAME_CHARS, cut.length());
        assertEquals(true, cut.endsWith(".csv")); // the extension survives
    }

    @Test
    public void chosenNameRules() {
        // duck.ai's exports stay Markdown, as before.
        assertEquals("notes.md", DownloadNames.chosen(Providers.DUCK, "Duck_AI_Chat_" + TS + ".md", "notes"));
        assertEquals("Duck_AI_Chat_" + TS + ".md", DownloadNames.chosen(Providers.DUCK, "Duck_AI_Chat_" + TS + ".md", "  "));
        // Others: what the user typed, cleaned; no forced extension.
        assertEquals("my_data.csv", DownloadNames.chosen(Providers.CHATGPT, "data.csv", "my/data.csv"));
        assertEquals("text/markdown", DownloadNames.mimeType("a.md", "text/plain"));
        assertEquals("text/csv", DownloadNames.mimeType("a.csv", "text/csv"));
    }
}
