package org.llmcentral.app.theme;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The 8 preset colour palettes (PROJECT.md §4.4), read from {@code assets/palettes.json}.
 *
 * <p>That file is computed on a computer by {@code tools/palettes/generate.mjs} with Google's Material
 * Color Utilities, so the app ships only ~6 KB of results instead of the whole colour library
 * (PROJECT.md §4.2, R-SIZE). Each palette has a full light and dark {@link Scheme}.</p>
 */
public final class Palettes {

    private static final String TAG = "Palettes";

    /** Used when nothing (or an unknown id) is saved: duck.ai's orange. */
    public static final String DEFAULT_ID = "duck";

    /** One palette, e.g. "Ocean" (seed #1E6FD9). */
    public static final class Palette {
        public final String id;
        public final String name;
        public final int seed;
        public final Scheme light;
        public final Scheme dark;

        Palette(String id, String name, int seed, Scheme light, Scheme dark) {
            this.id = id;
            this.name = name;
            this.seed = seed;
            this.light = light;
            this.dark = dark;
        }
    }

    private static List<Palette> cached;

    private Palettes() {
    }

    /** @return all palettes, in display order (read from the APK once, then kept in memory). */
    public static synchronized List<Palette> all(Context context) {
        if (cached == null) {
            try (InputStream in = context.getApplicationContext().getAssets().open("palettes.json")) {
                cached = parse(readUtf8(in));
            } catch (Exception e) {
                // Only possible if the file is missing from the APK (a build mistake): PalettesTest checks it.
                Log.e(TAG, "Couldn't read palettes.json", e);
                cached = Collections.emptyList();
            }
        }
        return cached;
    }

    /** @return the palette with this id, or the default one. Never null if the asset is intact. */
    public static Palette byId(Context context, String id) {
        Palette fallback = null;
        for (Palette p : all(context)) {
            if (p.id.equals(id)) {
                return p;
            }
            if (p.id.equals(DEFAULT_ID)) {
                fallback = p;
            }
        }
        return fallback;
    }

    /**
     * Parses the JSON written by the generator:
     * {@code {"roles":[…], "palettes":[{"id","name","seed","light":[hex…],"dark":[hex…]}]}}.
     * The role order comes from the file itself, so the order in {@link Scheme#ROLES} doesn't matter.
     */
    static List<Palette> parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray roles = root.getJSONArray("roles");
        JSONArray list = root.getJSONArray("palettes");
        List<Palette> result = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject p = list.getJSONObject(i);
            result.add(new Palette(p.getString("id"), p.getString("name"),
                    Scheme.parseHex(p.getString("seed")),
                    scheme(roles, p.getJSONArray("light"), false),
                    scheme(roles, p.getJSONArray("dark"), true)));
        }
        return Collections.unmodifiableList(result);
    }

    private static Scheme scheme(JSONArray roles, JSONArray values, boolean dark) throws Exception {
        Map<String, Integer> colors = new HashMap<>();
        for (int i = 0; i < roles.length(); i++) {
            colors.put(roles.getString(i), Scheme.parseHex(values.getString(i)));
        }
        return new Scheme(dark, colors);
    }

    static String readUtf8(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
