package lastai.sagalib.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translation keys and fingerprints (SagaLib 1.2, docs/FORMAT.md "Translations"). Every text in
 * a content file has a key made of the content id and its path, with ids instead of positions
 * where an array's objects have one: {@code vgc_lost_convoy/stages[search].journal}. Saga Forge
 * computes the same keys (test/textkeys.json is shared). A translation line only applies while
 * its fingerprint matches the original text, so a changed original falls back to itself instead of
 * showing a stale translation.
 */
public final class TextKeys {
    private TextKeys() {}

    /** FNV-1a (32 bit) over the UTF-16 code units, as 8 lowercase hex digits. */
    public static String fingerprint(String s) {
        int h = 0x811c9dc5;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193;
        }
        String hex = Integer.toHexString(h);
        while (hex.length() < 8) hex = "0" + hex;
        return hex;
    }

    /** Every text of a content file: key → text (keys starting with "_" are skipped). */
    public static Map<String, String> leaves(String contentId, Object json) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        walk(json, contentId + "/", out, null, null);
        return out;
    }

    /**
     * Replaces the texts the translation has a current line for. Returns how many; the originals of
     * replaced texts go into {@code originals} (key → original) when it isn't null.
     */
    public static int translate(String contentId, Object json, Translation tr, Map<String, String> originals) {
        if (tr == null) return 0;
        Map<String, String> got = new LinkedHashMap<String, String>();
        walk(json, contentId + "/", null, tr, got);
        if (originals != null) originals.putAll(got);
        return got.size();
    }

    private static void walk(Object o, String path, Map<String, String> out, Translation tr, Map<String, String> originals) {
        if (o instanceof JSONObject) {
            JSONObject j = (JSONObject) o;
            boolean root = path.endsWith("/");
            for (Iterator<?> it = j.keys(); it.hasNext(); ) {
                String k = String.valueOf(it.next());
                if (k.startsWith("_")) continue;
                String p = root ? path + k : path + "." + k;
                Object v = j.opt(k);
                if (v instanceof String) {
                    String text = (String) v;
                    if (out != null) out.put(p, text);
                    if (tr != null) {
                        String t = tr.text(p, text);
                        if (t != null) {
                            try { j.put(k, t); } catch (Exception ignored) { /* a string key always takes a string */ }
                            originals.put(p, text);
                        }
                    }
                } else {
                    walk(v, p, out, tr, originals);
                }
            }
        } else if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            for (int i = 0; i < a.length(); i++) {
                Object v = a.opt(i);
                String p = path + "[" + segment(v, i) + "]";
                if (v instanceof String) {
                    String text = (String) v;
                    if (out != null) out.put(p, text);
                    if (tr != null) {
                        String t = tr.text(p, text);
                        if (t != null) {
                            try { a.put(i, t); } catch (Exception ignored) { /* in range */ }
                            originals.put(p, text);
                        }
                    }
                } else {
                    walk(v, p, out, tr, originals);
                }
            }
        }
    }

    /** An array element's segment: its "id", else its "token", else its position. */
    private static String segment(Object v, int i) {
        if (v instanceof JSONObject) {
            JSONObject j = (JSONObject) v;
            Object id = j.opt("id");
            if (id instanceof String && !((String) id).isEmpty()) return (String) id;
            Object tok = j.opt("token");
            if (tok instanceof String && !((String) tok).isEmpty()) return (String) tok;
        }
        return String.valueOf(i);
    }
}
