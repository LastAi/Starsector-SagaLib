package lastai.sagalib.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SagaLib 1.3 (Saga Forge PLAN.md §10.2): "Only with / Only without" these mods on a content item
 * (docs/FORMAT.md, "Mod conditions"):
 * <pre>
 *   "modsWith":    ["a", "b"]        every one of them counts
 *   "modsWithout": ["c"]             none of them counts
 *   "modsAny":     [["d", "e"]]      in each group, at least one counts
 * </pre>
 * Whether a mod counts is {@link Mods#counts}: enabled when this game started, and still enabled.
 * The owner is the mod that ships the item (its start record decides).
 */
public final class ModGate {
    public static final ModGate NONE = new ModGate(null, Collections.<String>emptyList(), Collections.<String>emptyList(), Collections.<List<String>>emptyList());

    public final String owner;
    public final List<String> with;
    public final List<String> without;
    public final List<List<String>> any;

    public ModGate(String owner, List<String> with, List<String> without, List<List<String>> any) {
        this.owner = owner;
        this.with = with;
        this.without = without;
        this.any = any;
    }

    public boolean isEmpty() { return with.isEmpty() && without.isEmpty() && any.isEmpty(); }

    /** Reads modsWith / modsWithout / modsAny of {@code o} (missing = no condition); the owner is the reader's mod. */
    public static ModGate parse(JSONObject o, String path, JsonReader r) {
        if (o == null || (!o.has("modsWith") && !o.has("modsWithout") && !o.has("modsAny"))) return NONE.ownedBy(r.mod);
        List<String> with = ids(o, "modsWith", path, r);
        List<String> without = ids(o, "modsWithout", path, r);
        List<List<String>> any = new ArrayList<List<String>>();
        JSONArray a = o.optJSONArray("modsAny");
        if (o.has("modsAny") && a == null) r.error(JsonReader.at(path, "modsAny"), "should be a list of lists of mod ids");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONArray g = a.optJSONArray(i);
            if (g == null) {
                r.error(JsonReader.at(JsonReader.at(path, "modsAny"), i), "should be a list of mod ids");
                continue;
            }
            List<String> group = new ArrayList<String>();
            for (int j = 0; j < g.length(); j++) {
                String s = g.optString(j, "").trim();
                if (!s.isEmpty()) group.add(s);
            }
            if (!group.isEmpty()) any.add(Collections.unmodifiableList(group));
        }
        return new ModGate(r.mod, with, without, Collections.unmodifiableList(any));
    }

    private static List<String> ids(JSONObject o, String key, String path, JsonReader r) {
        if (!o.has(key)) return Collections.emptyList();
        JSONArray a = o.optJSONArray(key);
        if (a == null) {
            r.error(JsonReader.at(path, key), "should be a list of mod ids");
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (!s.isEmpty()) out.add(s);
        }
        return Collections.unmodifiableList(out);
    }

    /** The same conditions owned by {@code mod}. */
    public ModGate ownedBy(String mod) {
        if (mod == null ? owner == null : mod.equals(owner)) return this;
        return new ModGate(mod, with, without, any);
    }

    /** "only with a, b; only without c; with any of d, e". */
    public String describe() {
        StringBuilder b = new StringBuilder();
        if (!with.isEmpty()) b.append("only with ").append(join(with));
        if (!without.isEmpty()) b.append(b.length() > 0 ? "; " : "").append("only without ").append(join(without));
        for (List<String> g : any) b.append(b.length() > 0 ? "; " : "").append("with any of ").append(join(g));
        return b.length() == 0 ? "no mod conditions" : b.toString();
    }

    static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String s : l) b.append(b.length() > 0 ? ", " : "").append(s);
        return b.toString();
    }
}
