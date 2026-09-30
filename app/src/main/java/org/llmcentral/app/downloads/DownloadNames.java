package org.llmcentral.app.downloads;

import org.llmcentral.app.provider.ProviderSpec;

import java.util.Locale;

/**
 * File names and folders for downloads (the rules only, no Android code, so DownloadNamesTest checks
 * them on the computer).
 *
 * <p><b>duck.ai keeps its old rules exactly</b> (from duckAssist): files go to
 * {@code Downloads/duck.ai/}, chat exports named .txt/.text/.bin or without an extension become
 * Markdown ({@code .md}), and generic names ("download", "chat", "export"…) become
 * {@code Duck_AI_Chat_<date>_<time>}. Those rules make sense for duck.ai's chat exports only.</p>
 *
 * <p><b>Other providers</b> (round 6) keep the name the website gives the file (a ChatGPT CSV stays a
 * CSV) and get their own folder, e.g. {@code Downloads/ChatGPT/}.</p>
 *
 * <p>Every name is cleaned: characters that aren't allowed in file names ({@code / \ : * ? " < > |}
 * and control characters) become "_", so a name from a website can never point into another folder.</p>
 */
public final class DownloadNames {

    /** Longest file name kept (Android's limit is 255 bytes; this leaves room for " (1)" etc.). */
    static final int MAX_NAME_CHARS = 120;

    private DownloadNames() {
        // Only static methods.
    }

    /** @return the folder inside Downloads, e.g. "duck.ai" or "ChatGPT". */
    public static String folder(ProviderSpec provider) {
        return provider.isDuck() ? "duck.ai" : clean(provider.displayName, "Downloads");
    }

    /**
     * The name suggested in the "Save file" box.
     *
     * @param name      the name from the website (may be null or empty)
     * @param timestamp e.g. "20260930_184500" (passed in so tests are repeatable)
     */
    public static String suggested(ProviderSpec provider, String name, String timestamp) {
        if (provider.isDuck()) {
            return clean(duckName(name, timestamp), "Duck_AI_Chat_" + timestamp + ".md");
        }
        return clean(name, provider.displayName + "_download_" + timestamp);
    }

    /**
     * The name to save under, after the user edited the box.
     *
     * @param suggested what the box showed
     * @param typed     what the user left in the box
     */
    public static String chosen(ProviderSpec provider, String suggested, String typed) {
        String name = clean(typed, suggested);
        // duck.ai's chat exports stay Markdown even if the user removed ".md" (old behaviour).
        if (provider.isDuck() && suggested.endsWith(".md") && !name.toLowerCase(Locale.ROOT).endsWith(".md")) {
            name += ".md";
        }
        return name;
    }

    // ---------------------------------------------------------------- names and types of page-made files
    // (round 6 fix) WebView tells the app a download's address, header and type, but NOT the name the
    // website chose with <a download="report.csv"> (verified in Chromium's AwDownloadManagerDelegate).
    // For a file the page made itself there is no header, so the old code guessed a name from the page's
    // address (on Claude: the chat id, random-looking letters) and had no real type ("octet-stream"),
    // so the file got no extension and opened as "binary". These rules fix both.

    /**
     * The name for a file the page made (ChatGPT, Claude, DeepSeek, Z.ai): the page's own name for it
     * (its {@code download} attribute), else the name in the "Content-Disposition" header.
     *
     * @return that name, or null if neither exists (the caller then uses "Provider_download_time")
     */
    public static String pageFileName(String pageName, String headerName) {
        if (pageName != null && !pageName.trim().isEmpty()) {
            return pageName.trim();
        }
        return headerName != null && !headerName.trim().isEmpty() ? headerName.trim() : null;
    }

    /**
     * @return true for a type that says nothing about the content ("some bytes"), so the file's
     *         extension or the data: text is a better guide
     */
    public static boolean isGenericType(String mimetype) {
        if (mimetype == null) {
            return true;
        }
        String t = mimetype.trim().toLowerCase(Locale.ROOT);
        return t.isEmpty() || t.equals("application/octet-stream") || t.equals("binary/octet-stream")
                || t.equals("application/binary") || t.equals("application/x-binary")
                || t.equals("application/unknown") || t.equals("*/*");
    }

    /** @return the type written in a data: URL ("data:text/csv;base64,…" → "text/csv"), or "". */
    public static String typeOfDataUrl(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:")) {
            return "";
        }
        int end = dataUrl.length();
        for (char c : new char[]{';', ','}) {
            int i = dataUrl.indexOf(c);
            if (i >= 0 && i < end) {
                end = i;
            }
        }
        return dataUrl.substring(5, end).trim().toLowerCase(Locale.ROOT);
    }

    /** @return the extension in lower case without the dot ("Report.CSV" → "csv"), or "" if none. */
    public static String extension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1 || name.length() - dot - 1 > 10) {
            return "";
        }
        String ext = name.substring(dot + 1);
        for (int i = 0; i < ext.length(); i++) {
            if (!Character.isLetterOrDigit(ext.charAt(i))) {
                return "";
            }
        }
        return ext.toLowerCase(Locale.ROOT);
    }

    /** @return the name with ".ext" added if it has no extension yet and {@code ext} is known. */
    public static String withExtension(String name, String ext) {
        if (name == null || ext == null || ext.isEmpty() || !extension(name).isEmpty()) {
            return name;
        }
        return name + "." + ext;
    }

    /**
     * duck.ai's naming rules, unchanged from duckAssist's {@code formatMarkdownFilename} (only the
     * lower-casing now uses {@code Locale.ROOT}: with a Turkish phone language ".BIN" used to become
     * ".bın" and was not recognised).
     */
    static String duckName(String filename, String timestamp) {
        if (filename == null || filename.isEmpty()) {
            return "Duck_AI_Chat_" + timestamp + ".md";
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        // Change .txt, .text, or .bin extensions to .md
        if (lower.endsWith(".txt")) {
            filename = filename.substring(0, filename.length() - 4) + ".md";
        } else if (lower.endsWith(".text")) {
            filename = filename.substring(0, filename.length() - 5) + ".md";
        } else if (lower.endsWith(".bin")) {
            filename = filename.substring(0, filename.length() - 4) + ".md";
        }
        // If no extension exists, append .md
        if (!filename.contains(".")) {
            filename += ".md";
        }
        int dotIndex = filename.lastIndexOf('.');
        String baseName = (dotIndex > 0) ? filename.substring(0, dotIndex).trim() : filename;
        String ext = (dotIndex > 0) ? filename.substring(dotIndex) : "";
        // Ensure timestamp is present on markdown exports and generic names
        if (baseName.equalsIgnoreCase("download") || baseName.equalsIgnoreCase("chat")
                || baseName.equalsIgnoreCase("export") || baseName.equalsIgnoreCase("duckduckgo_chat")
                || baseName.equalsIgnoreCase("duck_ai_chat")) {
            filename = "Duck_AI_Chat_" + timestamp + ext;
        } else if (ext.equalsIgnoreCase(".md") && !baseName.matches(".*_\\d{8}_\\d{6}$")) {
            filename = baseName + "_" + timestamp + ext;
        }
        return filename;
    }

    /**
     * Makes a name safe for a file: forbidden characters → "_", no leading/trailing spaces or dots
     * (".." must never mean "the folder above"), at most {@value #MAX_NAME_CHARS} characters.
     *
     * @return the cleaned name, or {@code fallback} if nothing usable is left
     */
    static String clean(String name, String fallback) {
        if (name == null) {
            return fallback;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); ) {
            int cp = name.codePointAt(i);
            i += Character.charCount(cp);
            boolean bad = cp < 0x20 || cp == 0x7F || "/\\:*?\"<>|".indexOf(cp) >= 0;
            sb.appendCodePoint(bad ? '_' : cp);
        }
        String s = sb.toString().trim();
        while (s.startsWith(".")) {
            s = s.substring(1);
        }
        while (s.endsWith(".") || s.endsWith(" ")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.codePointCount(0, s.length()) > MAX_NAME_CHARS) {
            // Keep the extension: cut the part before it.
            int dot = s.lastIndexOf('.');
            String ext = dot > 0 && s.length() - dot <= 10 ? s.substring(dot) : "";
            String base = s.substring(0, s.length() - ext.length());
            base = base.substring(0, base.offsetByCodePoints(0, MAX_NAME_CHARS - ext.length()));
            s = base + ext;
        }
        return s.isEmpty() || s.replace("_", "").isEmpty() ? fallback : s;
    }
}
