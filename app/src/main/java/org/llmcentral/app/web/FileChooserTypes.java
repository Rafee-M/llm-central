package org.llmcentral.app.web;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Turns a page's file-field filter ({@code <input type="file" accept="image/*,.pdf">}, handed to the app as
 * {@code WebChromeClient.FileChooserParams.getAcceptTypes()}) into the Android file types the 📎 "File Manager"
 * picker understands ({@code Intent.EXTRA_MIME_TYPES}). VERIFICATION.md R4-13. Plain Java, unit-tested; the
 * extension → type lookup (Android's {@code MimeTypeMap}) is passed in.
 *
 * <p>Rule of thumb: when in doubt, show every file. A filter that hides a file the page would accept is worse
 * than no filter, so any entry we can't translate (an unknown extension, a malformed type) turns filtering off.</p>
 */
public final class FileChooserTypes {

    private FileChooserTypes() {
    }

    /**
     * @param accept        the page's accept entries (each may itself be a comma list); null or empty = anything
     * @param extensionType extension without the dot, lowercase (e.g. "pdf") → type, or null if unknown
     * @return the types to filter by, in order without duplicates; empty = no filter (every file)
     */
    public static String[] mimeTypes(String[] accept, Function<String, String> extensionType) {
        Set<String> types = new LinkedHashSet<>();
        if (accept != null) {
            for (String entry : accept) {
                if (entry == null) {
                    continue;
                }
                for (String raw : entry.split(",")) {
                    String item = raw.trim().toLowerCase(Locale.ROOT);
                    if (item.isEmpty()) {
                        continue;
                    }
                    String type;
                    if (item.startsWith(".")) {
                        type = extensionType.apply(item.substring(1));
                    } else if (item.indexOf('/') > 0) {
                        type = item;
                    } else {
                        type = null;
                    }
                    if (type == null || type.equals("*/*")) {
                        return new String[0];
                    }
                    types.add(type);
                }
            }
        }
        return types.toArray(new String[0]);
    }
}
