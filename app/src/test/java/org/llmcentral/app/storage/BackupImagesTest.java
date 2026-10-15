package org.llmcentral.app.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** The offline backup keeps all new pictures and only a budget of older ones (BackupImages). */
public class BackupImagesTest {

    private static JSONObject img(String src, int dataChars) throws Exception {
        StringBuilder data = new StringBuilder();
        for (int i = 0; i < dataChars; i++) data.append('x');
        return new JSONObject().put("src", src).put("dataUrl", data.toString());
    }

    @Test
    public void newerIsAlwaysKeptWholeEvenOverBudget() throws Exception {
        JSONObject newer = new JSONObject().put("blob:a", "0123456789").put("blob:b", "0123456789");
        JSONObject older = new JSONObject().put("blob:old", "01234");
        JSONObject merged = new BackupImages(0).mergeMap(newer, older);
        assertTrue(merged.has("blob:a"));
        assertTrue(merged.has("blob:b"));
        assertFalse("no budget left for older pictures", merged.has("blob:old"));
    }

    @Test
    public void olderOnlyEntriesAreAddedWhileTheyFit() throws Exception {
        JSONObject newer = new JSONObject().put("blob:new", "abc");
        JSONObject older = new JSONObject().put("blob:old", "0123456789"); // 8 + 10 = 18 characters
        assertTrue(new BackupImages(18).mergeMap(newer, older).has("blob:old"));
        assertFalse(new BackupImages(17).mergeMap(new JSONObject().put("blob:new", "abc"), older).has("blob:old"));
    }

    @Test
    public void newerValueWinsAndIsNotCountedTwice() throws Exception {
        JSONObject newer = new JSONObject().put("blob:same", "NEW");
        JSONObject older = new JSONObject().put("blob:same", "OLD");
        JSONObject merged = new BackupImages(1000).mergeMap(newer, older);
        assertEquals("NEW", merged.getString("blob:same"));
        assertEquals(1, merged.length());
    }

    @Test
    public void nullsAreHandled() throws Exception {
        BackupImages merge = new BackupImages(1000);
        assertNull(merge.mergeMap(null, null));
        assertEquals(1, merge.mergeMap(null, new JSONObject().put("blob:o", "x")).length());
        JSONObject newer = new JSONObject().put("blob:n", "x");
        assertEquals(newer, merge.mergeMap(newer, null));
        assertNull(merge.mergeList(null, null));
    }

    @Test
    public void listKeepsNewerThenOlderNewestFirstUntilBudget() throws Exception {
        JSONArray newer = new JSONArray().put(img("blob:n1", 50));
        // Older copy: newest first (that is how merges write it). Each item costs src (7) + data (50) = 57.
        JSONArray older = new JSONArray().put(img("blob:o1", 50)).put(img("blob:o2", 50)).put(img("blob:o3", 50));
        JSONArray merged = new BackupImages(2 * 57).mergeList(newer, older);
        assertEquals(3, merged.length());
        assertEquals("blob:n1", merged.getJSONObject(0).getString("src"));
        assertEquals("blob:o1", merged.getJSONObject(1).getString("src"));
        assertEquals("blob:o2", merged.getJSONObject(2).getString("src")); // o3 didn't fit
    }

    @Test
    public void listSkipsPicturesTheNewerCopyHas() throws Exception {
        JSONArray newer = new JSONArray().put(img("https://duck.ai/x.png", 10));
        JSONArray older = new JSONArray().put(img("https://duck.ai/x.png", 10)).put(img("blob:o", 10));
        JSONArray merged = new BackupImages(1000).mergeList(newer, older);
        assertEquals(2, merged.length());
        assertEquals("blob:o", merged.getJSONObject(1).getString("src"));
    }

    @Test
    public void budgetIsSharedByBothParts() throws Exception {
        BackupImages merge = new BackupImages(20);
        // The map uses 6 + 10 = 16 of 20 characters …
        assertTrue(merge.mergeMap(new JSONObject(), new JSONObject().put("blob:m", "0123456789")).has("blob:m"));
        // … so a 7 + 10 = 17 character list item no longer fits.
        assertEquals(0, merge.mergeList(new JSONArray(), new JSONArray().put(img("blob:l1", 10))).length());
    }

    @Test
    public void repeatedVisitsNoLongerGrowWithoutLimit() throws Exception {
        // The bug: every visit gives the same picture a new blob: address. Merge 100 visits; the saved
        // copy must stay within newest visit + budget instead of holding all 100 copies.
        long budget = 5 * 1000;
        JSONObject saved = null;
        for (int visit = 0; visit < 100; visit++) {
            StringBuilder picture = new StringBuilder();
            for (int i = 0; i < 1000; i++) picture.append('p');
            JSONObject dump = new JSONObject().put("blob:https://duck.ai/visit-" + visit, picture.toString());
            saved = new BackupImages(budget).mergeMap(dump, saved);
        }
        assertTrue("kept " + saved.length() + " copies", saved.length() <= 1 + 5);
    }
}
