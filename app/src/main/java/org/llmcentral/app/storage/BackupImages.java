package org.llmcentral.app.storage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * How much of the <i>previous</i> offline backup's extra pictures a new duck.ai backup keeps
 * (2026-10-15). Used by {@code MainActivity.onChatsFetched}.
 *
 * <p><b>The problem it solves.</b> Each backup (taken when the app goes to the background after
 * duck.ai was used) is merged with the saved one, so that nothing disappears when a backup is
 * incomplete. Two parts of it are keyed by a picture's page address: {@code blobMap} (files the page
 * made) and {@code domImages} (pictures shown on the page). For duck.ai's own pictures that address
 * is a {@code blob:} address, and a browser gives such an address a new random ID every time the
 * page loads (File API: "blob:" + origin + "/" + UUID). So every visit added the same pictures again
 * under new keys, and the merge kept all old ones: the saved file could only grow, and with it the
 * work at every backup and the memory it needs.</p>
 *
 * <p><b>The rule now.</b> Everything from the newest backup is kept, as before. Pictures that only
 * the older saved copy has are kept too, newest first, until they add up to {@link #OLD_BUDGET_CHARS}
 * characters (about 15 MB of pictures; base64 text is a third bigger than the picture). The chats
 * themselves and duck.ai's own stored pictures (its IndexedDB) are merged as before and never cut.</p>
 *
 * <p>Plain Java ({@code org.json} only), so BackupImagesTest checks it on the computer.</p>
 */
public final class BackupImages {

    /** Characters of older pictures kept in total (both parts together): 20 MB of text. */
    public static final long OLD_BUDGET_CHARS = 20L * 1024 * 1024;

    private long left;

    /** @param budgetChars how many characters of older pictures this merge may keep */
    public BackupImages(long budgetChars) {
        this.left = budgetChars;
    }

    /**
     * {@code blobMap}: page address → picture as a data URL. All of {@code newer} is kept; entries only
     * {@code older} has are added (in the older copy's order, which is newest first) while they fit.
     *
     * @return the merged map ({@code newer} itself, filled up), or null if both are null
     */
    public JSONObject mergeMap(JSONObject newer, JSONObject older) throws JSONException {
        if (older == null) {
            return newer;
        }
        JSONObject merged = newer != null ? newer : new JSONObject();
        java.util.Iterator<String> keys = older.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (merged.has(key)) {
                continue; // the newer copy has it already
            }
            Object value = older.get(key);
            long size = key.length() + (value instanceof String ? ((String) value).length() : 0);
            if (size > left) {
                break; // budget used up: older pictures are left out
            }
            left -= size;
            merged.put(key, value);
        }
        return merged;
    }

    /**
     * {@code domImages}: a list of {@code {src, alt, dataUrl}}. All of {@code newer} is kept; items whose
     * {@code src} only {@code older} has are added (newest first) while they fit.
     *
     * @return the merged list ({@code newer} itself, filled up), or null if both are null
     */
    public JSONArray mergeList(JSONArray newer, JSONArray older) {
        if (older == null) {
            return newer;
        }
        JSONArray merged = newer != null ? newer : new JSONArray();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < merged.length(); i++) {
            JSONObject item = merged.optJSONObject(i);
            if (item != null && item.has("src")) {
                seen.add(item.optString("src"));
            }
        }
        for (int i = 0; i < older.length(); i++) {
            JSONObject item = older.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String src = item.optString("src", null);
            if (src != null && seen.contains(src)) {
                continue; // the newer copy has it already
            }
            long size = item.optString("dataUrl", "").length() + (src != null ? src.length() : 0);
            if (size > left) {
                break; // budget used up: older pictures are left out
            }
            left -= size;
            merged.put(item);
            if (src != null) {
                seen.add(src);
            }
        }
        return merged;
    }
}
