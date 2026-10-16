package org.llmcentral.app.account;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * When each account was last signed out, so that <b>both</b> screens close its tab.
 *
 * <p>The app has two screens with their own tabs: the main screen and the assistant drawer (ChatActivity).
 * Signing out in Settings closes the tab only in the screen that shows Settings; the other screen's tab would
 * still show the signed-in page. So a sign-out is also written here (pref {@value #PREF}: {"accountId": time}),
 * and each screen, when it comes back to the front, closes the tabs of accounts signed out since it last looked
 * (MainActivity.refreshAccounts).</p>
 *
 * <p>Plain Java on JSON text, so SignOutsTest checks it on the computer.</p>
 */
public final class SignOuts {

    /** Preference (in duck_assist_prefs) with the JSON object. */
    public static final String PREF = "signed_out_at";

    /** Entries older than this are dropped when a new one is recorded: no screen still needs them. */
    static final long KEEP_MS = 24L * 60 * 60 * 1000;

    private SignOuts() {
    }

    /** @return the JSON with {@code accountId} signed out at {@code now} (and entries older than a day dropped). */
    public static String record(String json, String accountId, long now) {
        JSONObject o = parse(json);
        JSONObject out = new JSONObject();
        try {
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String id = it.next();
                long at = o.optLong(id, 0);
                if (now - at <= KEEP_MS) {
                    out.put(id, at);
                }
            }
            out.put(accountId, now);
        } catch (Exception e) {
            return "{}";
        }
        return out.toString();
    }

    /** @return the accounts signed out after {@code sinceMillis} */
    public static List<String> since(String json, long sinceMillis) {
        List<String> ids = new ArrayList<>();
        JSONObject o = parse(json);
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String id = it.next();
            if (o.optLong(id, 0) > sinceMillis) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** @return the newest sign-out time, or {@code atLeast} if that is later */
    public static long latest(String json, long atLeast) {
        long latest = atLeast;
        JSONObject o = parse(json);
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            latest = Math.max(latest, o.optLong(it.next(), 0));
        }
        return latest;
    }

    private static JSONObject parse(String json) {
        try {
            return json == null ? new JSONObject() : new JSONObject(json);
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
