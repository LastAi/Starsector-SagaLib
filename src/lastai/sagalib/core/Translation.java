package lastai.sagalib.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * One translation of one content mod (SagaLib 1.2): a CSV a translation mod ships and lists in its
 * content.csv as type "translation", id "&lt;mod id&gt;/&lt;language&gt;". Columns: key, translation,
 * source (the original's fingerprint, see {@link TextKeys}); others (context, original) are for the
 * translator and ignored here.
 */
public final class Translation {
    public final String mod;
    public final String language;
    /** The translation mod that ships it. */
    public final String from;
    private final Map<String, String[]> lines = new HashMap<String, String[]>();

    public Translation(String mod, String language, String from) {
        this.mod = mod;
        this.language = language;
        this.from = from;
    }

    public void put(String key, String text, String source) {
        lines.put(key, new String[] { text, source == null ? "" : source.trim() });
    }

    public int size() { return lines.size(); }

    /**
     * The translated text for a key, or null: no line, an empty translation, or a line made for
     * another original (its fingerprint doesn't match: the original changed since).
     */
    public String text(String key, String original) {
        String[] l = lines.get(key);
        if (l == null || l[0] == null || l[0].isEmpty()) return null;
        if (!l[1].isEmpty() && original != null && !l[1].equals(TextKeys.fingerprint(original))) return null;
        return l[0];
    }

    /** Reads the CSV rows (as the game's loadCSV returns them). Rows without a key are skipped. */
    public static Translation fromRows(String mod, String language, String from, JSONArray rows, Problems problems, String file) {
        Translation t = new Translation(mod, language, from);
        if (rows == null) return t;
        boolean warned = false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.optJSONObject(i);
            if (r == null) continue;
            String key = r.optString("key", "").trim();
            if (key.isEmpty() || key.startsWith("#")) continue;
            if (!r.has("translation") && !warned) {
                problems.error(file, "", "has no \"translation\" column (columns: key, context, original, translation, source)");
                warned = true;
            }
            t.put(key, r.optString("translation", ""), r.optString("source", ""));
        }
        return t;
    }
}
