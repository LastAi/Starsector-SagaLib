package lastai.sagalib.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * SagaLib 1.3 (Saga Forge PLAN.md §10.6, docs/FORMAT.md "Versions by installed mods"): one item
 * with versions for combinations of optional mods. Pure (no game classes), shared semantics with
 * the editor's Play mode (test/golden/versions.json).
 * <ul>
 * <li>{@link #resolve}: the item for a set of mods that count (versions that hold, least specific
 * first, each one's {@code set} applied to a copy without {@code versions}).</li>
 * <li>{@link #choose}: picks an item's version once per save, from the start record
 * ({@link Mods#counts}), and stores it in {@code $global.sagaforge_ver_<kind>_<id>}.</li>
 * </ul>
 */
public final class Versions {
    private Versions() {}

    /** At most this many mods per item (2^6 = 64 versions are parsed at load). */
    public static final int MAX_MODS = 6;
    public static final String BASE = "base";
    public static final List<String> KINDS = Collections.unmodifiableList(Arrays.asList(
            "quest", "person", "system", "encounter", "pool", "events", "progress", "scatter", "factions"));

    /** One versioned item of a content mod, with every version parsed (Content fills it). Never saved. */
    public static final class Item {
        public final String kind;
        public final String id;
        /** The mod that ships it (its start record decides). */
        public final String owner;
        /** File and JSON path, for the log and the dev menu. */
        public final String source;
        /** The item as in its file, with {@code versions}. */
        public final JSONObject raw;
        /** The mods its versions name, sorted; empty when it names too many (base only). */
        public final List<String> mentioned;
        /** Version key ("base", "a+b") → the parsed definition. */
        public final Map<String, Object> defs = new LinkedHashMap<String, Object>();
        /** The definition now in Content's maps. */
        public Object active;

        public Item(String kind, String id, String owner, String source, JSONObject raw, List<String> mentioned) {
            this.kind = kind;
            this.id = id;
            this.owner = owner;
            this.source = source;
            this.raw = raw;
            this.mentioned = Collections.unmodifiableList(new ArrayList<String>(mentioned));
        }

        public String ref() { return kind + ":" + id; }
    }

    /** One entry of a {@code versions} list. */
    static final class Version {
        String label;
        int index;
        final List<String> with = new ArrayList<String>();
        final List<String> without = new ArrayList<String>();
        final List<List<String>> any = new ArrayList<List<String>>();
        JSONObject set = new JSONObject();

        int specificity() { return with.size() + without.size() + any.size(); }

        boolean holds(Set<String> on) {
            for (String m : with) if (!on.contains(m)) return false;
            for (String m : without) if (on.contains(m)) return false;
            for (List<String> g : any) {
                boolean one = false;
                for (String m : g) if (on.contains(m)) { one = true; break; }
                if (!one) return false;
            }
            return true;
        }
    }

    /** The item for one set of mods. */
    public static final class Result {
        /** A copy of the item without {@code versions}, with the versions that hold applied. */
        public final JSONObject value;
        /** Paths two equally specific versions set to different values (sorted). */
        public final List<String> conflicts;
        /** "versions[1].set.a.b: message" (relative to the item). */
        public final List<String> errors;
        /** For each conflict path, the labels of the versions involved. */
        public final Map<String, List<String>> conflictVersions;

        Result(JSONObject value, List<String> conflicts, List<String> errors, Map<String, List<String>> conflictVersions) {
            this.value = value;
            this.conflicts = conflicts;
            this.errors = errors;
            this.conflictVersions = conflictVersions;
        }
    }

    // ---------------------------------------------------------------- reading the versions list

    static List<Version> parse(JSONObject item, List<String> errors) {
        List<Version> out = new ArrayList<Version>();
        if (item == null || !item.has("versions")) return out;
        JSONArray a = item.optJSONArray("versions");
        if (a == null) {
            errors.add("versions: should be a list of versions");
            return out;
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            String at = "versions[" + i + "]";
            if (o == null) {
                errors.add(at + ": should be an object");
                continue;
            }
            Version v = new Version();
            v.index = i;
            String id = o.optString("id", "");
            v.label = id.isEmpty() ? String.valueOf(i) : id;
            ids(o, "modsWith", at, v.with, errors);
            ids(o, "modsWithout", at, v.without, errors);
            if (o.has("modsAny")) {
                JSONArray g = o.optJSONArray("modsAny");
                if (g == null) errors.add(at + ".modsAny: should be a list of lists of mod ids");
                for (int k = 0; g != null && k < g.length(); k++) {
                    JSONArray one = g.optJSONArray(k);
                    if (one == null) {
                        errors.add(at + ".modsAny[" + k + "]: should be a list of mod ids");
                        continue;
                    }
                    List<String> group = new ArrayList<String>();
                    for (int j = 0; j < one.length(); j++) {
                        String s = one.optString(j, "").trim();
                        if (!s.isEmpty()) group.add(s);
                    }
                    if (!group.isEmpty()) v.any.add(group);
                }
            }
            if (o.has("set")) {
                JSONObject s = o.optJSONObject("set");
                if (s == null) errors.add(at + ".set: should be an object of path → value");
                else v.set = s;
            }
            out.add(v);
        }
        return out;
    }

    private static void ids(JSONObject o, String key, String at, List<String> out, List<String> errors) {
        if (!o.has(key)) return;
        JSONArray a = o.optJSONArray(key);
        if (a == null) {
            errors.add(at + "." + key + ": should be a list of mod ids");
            return;
        }
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (!s.isEmpty()) out.add(s);
        }
    }

    /** Whether the item (or, for a people file, any person) has a versions list. */
    public static boolean has(JSONObject item) { return item != null && item.has("versions"); }

    /** Every mod the item's versions name, sorted (Java String order). */
    public static List<String> mentioned(JSONObject item) {
        TreeSet<String> s = new TreeSet<String>();
        for (Version v : parse(item, new ArrayList<String>())) {
            s.addAll(v.with);
            s.addAll(v.without);
            for (List<String> g : v.any) s.addAll(g);
        }
        return new ArrayList<String>(s);
    }

    // ---------------------------------------------------------------- resolution

    /** The item for the mods in {@code on}: versions that hold, least specific first (stable), applied to a copy. */
    public static Result resolve(JSONObject item, Collection<String> on) {
        List<String> errors = new ArrayList<String>();
        JSONObject value = (JSONObject) copy(item);
        value.remove("versions");
        Set<String> mods = new java.util.HashSet<String>(on == null ? Collections.<String>emptySet() : on);
        List<Version> holding = new ArrayList<Version>();
        for (Version v : parse(item, errors)) if (v.holds(mods)) holding.add(v);
        Collections.sort(holding, new Comparator<Version>() {
            public int compare(Version a, Version b) { return a.specificity() - b.specificity(); }
        });
        // path → specificity → canonical value → the versions that set it
        Map<String, TreeMap<Integer, Map<String, List<String>>>> setBy = new LinkedHashMap<String, TreeMap<Integer, Map<String, List<String>>>>();
        for (Version v : holding) {
            for (String path : sortedKeys(v.set)) {
                Object val = v.set.opt(path);
                String err = apply(value, path, val);
                if (err != null) {
                    errors.add("versions[" + v.index + "].set." + path + ": " + err);
                    continue;
                }
                TreeMap<Integer, Map<String, List<String>>> bySpec = setBy.get(path);
                if (bySpec == null) setBy.put(path, bySpec = new TreeMap<Integer, Map<String, List<String>>>());
                Map<String, List<String>> byValue = bySpec.get(v.specificity());
                if (byValue == null) bySpec.put(v.specificity(), byValue = new LinkedHashMap<String, List<String>>());
                String c = canonical(val);
                List<String> who = byValue.get(c);
                if (who == null) byValue.put(c, who = new ArrayList<String>());
                who.add(v.label);
            }
        }
        List<String> conflicts = new ArrayList<String>();
        Map<String, List<String>> conflictVersions = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, TreeMap<Integer, Map<String, List<String>>>> e : setBy.entrySet()) {
            Map<String, List<String>> top = e.getValue().lastEntry().getValue();
            if (top.size() < 2) continue;
            conflicts.add(e.getKey());
            List<String> who = new ArrayList<String>();
            for (List<String> l : top.values()) who.addAll(l);
            conflictVersions.put(e.getKey(), who);
        }
        Collections.sort(conflicts);
        return new Result(value, conflicts, errors, conflictVersions);
    }

    /** The keys of an object, sorted (the game's org.json keeps no order; sorted puts a parent before its children). */
    static List<String> sortedKeys(JSONObject o) {
        List<String> keys = new ArrayList<String>();
        for (Iterator<?> it = o.keys(); it.hasNext(); ) keys.add(String.valueOf(it.next()));
        Collections.sort(keys);
        return keys;
    }

    /** One path segment: an object key, or {@code key[sel]} (the element of the list whose "id", else "token", is sel). */
    static final class Segment {
        final String key;
        final String sel;

        Segment(String key, String sel) {
            this.key = key;
            this.sel = sel;
        }
    }

    /** The segments of a path, or null if it isn't one. */
    static List<Segment> segments(String path) {
        List<Segment> out = new ArrayList<Segment>();
        if (path == null || path.isEmpty()) return null;
        int i = 0, n = path.length();
        while (i < n) {
            int start = i;
            while (i < n && path.charAt(i) != '.' && path.charAt(i) != '[') i++;
            String key = path.substring(start, i);
            if (key.isEmpty()) return null;
            String sel = null;
            if (i < n && path.charAt(i) == '[') {
                int close = path.indexOf(']', i);
                if (close < 0) return null;
                sel = path.substring(i + 1, close);
                if (sel.isEmpty()) return null;
                i = close + 1;
            }
            out.add(new Segment(key, sel));
            if (i < n) {
                if (path.charAt(i) != '.') return null;
                i++;
                if (i == n) return null;
            }
        }
        return out;
    }

    /** Sets (or with null / JSON null, removes) the path in {@code root}. Returns an error, or null. */
    public static String apply(JSONObject root, String path, Object value) {
        List<Segment> segs = segments(path);
        if (segs == null) return "\"" + path + "\" isn't a path (keys joined by \".\", a list element as key[id])";
        if (segs.size() == 1 && segs.get(0).sel == null && "id".equals(segs.get(0).key)) return "a version can't change the id";
        boolean remove = value == null || value == JSONObject.NULL;
        JSONObject cur = root;
        try {
            for (int i = 0; i < segs.size() - 1; i++) {
                Segment s = segs.get(i);
                if (s.sel == null) {
                    Object c = cur.opt(s.key);
                    if (c == null || c == JSONObject.NULL) {
                        if (remove) return null; // nothing to remove
                        c = new JSONObject();
                        cur.put(s.key, c);
                    }
                    if (!(c instanceof JSONObject)) return "\"" + s.key + "\" isn't an object";
                    cur = (JSONObject) c;
                } else {
                    JSONArray a = cur.optJSONArray(s.key);
                    if (a == null) return "\"" + s.key + "\" isn't a list";
                    int idx = find(a, s.sel);
                    if (idx < 0) return "no element \"" + s.sel + "\" in " + s.key;
                    Object el = a.opt(idx);
                    if (!(el instanceof JSONObject)) return s.key + "[" + s.sel + "] isn't an object";
                    cur = (JSONObject) el;
                }
            }
            Segment last = segs.get(segs.size() - 1);
            if (last.sel == null) {
                if (remove) cur.remove(last.key);
                else cur.put(last.key, copy(value));
                return null;
            }
            JSONArray a = cur.optJSONArray(last.key);
            if (a == null) return "\"" + last.key + "\" isn't a list";
            int idx = find(a, last.sel);
            if (idx < 0) return "no element \"" + last.sel + "\" in " + last.key;
            if (remove) a.remove(idx);
            else a.put(idx, copy(value));
            return null;
        } catch (Exception e) {
            return "can't be set (" + e.getMessage() + ")";
        }
    }

    /** The index of the list's object whose "id" (else, when it has no id, "token") is {@code sel}; -1 if none. */
    static int find(JSONArray a, String sel) {
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Object id = o.opt("id");
            if (id instanceof String && !((String) id).isEmpty()) {
                if (sel.equals(id)) return i;
                continue;
            }
            Object tok = o.opt("token");
            if (tok instanceof String && sel.equals(tok)) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- JSON helpers

    /** A deep copy (objects and lists); other values as they are. */
    public static Object copy(Object v) {
        try {
            if (v instanceof JSONObject) return new JSONObject(v.toString());
            if (v instanceof JSONArray) return new JSONArray(v.toString());
        } catch (Exception e) {
            throw new IllegalStateException("can't copy JSON: " + e.getMessage(), e);
        }
        return v;
    }

    /** A canonical text of a JSON value (sorted keys, numbers as numbers): equal values give equal texts. */
    public static String canonical(Object v) {
        StringBuilder b = new StringBuilder();
        canonical(v, b);
        return b.toString();
    }

    private static void canonical(Object v, StringBuilder b) {
        if (v == null || v == JSONObject.NULL) b.append("null");
        else if (v instanceof JSONObject) {
            JSONObject o = (JSONObject) v;
            b.append('{');
            boolean first = true;
            for (String k : sortedKeys(o)) {
                if (!first) b.append(',');
                first = false;
                b.append(JSONObject.quote(k)).append(':');
                canonical(o.opt(k), b);
            }
            b.append('}');
        } else if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            b.append('[');
            for (int i = 0; i < a.length(); i++) {
                if (i > 0) b.append(',');
                canonical(a.opt(i), b);
            }
            b.append(']');
        } else if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(Double.toString(d));
        } else if (v instanceof String) b.append(JSONObject.quote((String) v));
        else b.append(String.valueOf(v));
    }

    // ---------------------------------------------------------------- keys and choosing

    /** "base", or the mods sorted (Java String order) and joined with "+". */
    public static String key(Collection<String> mods) {
        if (mods == null || mods.isEmpty()) return BASE;
        List<String> l = new ArrayList<String>(new TreeSet<String>(mods));
        StringBuilder b = new StringBuilder();
        for (String m : l) b.append(b.length() > 0 ? "+" : "").append(m);
        return b.toString();
    }

    /** The mods of a key ("base" = none). */
    public static List<String> mods(String key) {
        List<String> out = new ArrayList<String>();
        if (key == null || key.isEmpty() || BASE.equals(key)) return out;
        for (String s : key.split("\\+")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    /** Every subset of the mods (sorted lists; the empty one first). */
    public static List<List<String>> subsets(List<String> mods) {
        List<String> sorted = new ArrayList<String>(new TreeSet<String>(mods));
        List<List<String>> out = new ArrayList<List<String>>();
        int n = sorted.size();
        for (int bits = 0; bits < (1 << n); bits++) {
            List<String> s = new ArrayList<String>();
            for (int i = 0; i < n; i++) if ((bits & (1 << i)) != 0) s.add(sorted.get(i));
            out.add(s);
        }
        return out;
    }

    /** {@code $global.sagaforge_ver_<kind>_<id>}: the version key chosen in this save. */
    public static String memKey(String kind, String id) { return "$global.sagaforge_ver_" + kind + "_" + id; }

    /** {@code $global.sfv_<questId>_<mod>}: the quest's chosen version has this mod (for exported dialog). */
    public static String questFlag(String questId, String mod) { return "$global.sfv_" + questId + "_" + sanitize(mod); }

    /** Characters other than letters, digits and _ become _. */
    public static String sanitize(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' ? c : '_');
        }
        return b.toString();
    }

    /** The version this save uses: the stored key, without mods the item no longer mentions; null if not chosen yet. */
    public static List<String> chosen(Memory m, String kind, String id, List<String> mentioned) {
        Object stored = m.get(memKey(kind, id));
        if (stored == null) return null;
        List<String> out = new ArrayList<String>();
        for (String mod : mods(String.valueOf(stored))) if (mentioned.contains(mod) && !out.contains(mod)) out.add(mod);
        Collections.sort(out);
        return out;
    }

    /**
     * Chooses the item's version if this save hasn't yet: the mods it mentions that count for its
     * owner (the start record). Stores the key (and for a quest its {@code sfv_} flags). Returns
     * the version the save uses. A stored choice never changes.
     */
    public static List<String> choose(Memory m, String kind, String id, String owner, List<String> mentioned) {
        List<String> got = chosen(m, kind, id, mentioned);
        if (got != null) return got;
        List<String> on = new ArrayList<String>();
        for (String mod : mentioned) if (Mods.counts(m, owner, mod)) on.add(mod);
        Collections.sort(on);
        m.set(memKey(kind, id), key(on));
        if ("quest".equals(kind)) for (String mod : on) m.set(questFlag(id, mod), Boolean.TRUE);
        return on;
    }

    public static List<String> choose(Memory m, Item it) { return choose(m, it.kind, it.id, it.owner, it.mentioned); }

    // ---------------------------------------------------------------- text.json versions

    /** text.json version paths SagaLib applies: kind → fields. */
    public static final Map<String, List<String>> SPEC_FIELDS;
    static {
        Map<String, List<String>> m = new LinkedHashMap<String, List<String>>();
        m.put("items", Arrays.asList("name", "desc", "icon", "basePrice"));
        m.put("descriptions", Arrays.asList("text1", "text2", "text3"));
        SPEC_FIELDS = Collections.unmodifiableMap(m);
    }

    /** Problems with a text.json's versions: bad lists and paths that aren't items.<id>.<field> / descriptions.<id>.<field>. */
    public static List<String> checkSpecVersions(JSONObject strings) {
        List<String> errors = new ArrayList<String>();
        for (Version v : parse(strings, errors)) {
            for (String path : sortedKeys(v.set)) {
                List<Segment> segs = segments(path);
                String at = "versions[" + v.index + "].set." + path;
                if (segs == null || segs.size() != 3 || segs.get(0).sel != null || segs.get(1).sel != null || segs.get(2).sel != null
                        || !SPEC_FIELDS.containsKey(segs.get(0).key) || !SPEC_FIELDS.get(segs.get(0).key).contains(segs.get(2).key)) {
                    errors.add(at + ": not a path SagaLib sets (items.<id>.name|desc|icon|basePrice, descriptions.<id>|<TYPE>.text1|text2|text3)");
                    continue;
                }
                Object val = v.set.opt(path);
                if ("basePrice".equals(segs.get(2).key) && !(val instanceof Number) && val != JSONObject.NULL) errors.add(at + ": should be a number");
            }
        }
        return errors;
    }

    /**
     * The names, descriptions, icons and prices a text.json's versions change for the mods in
     * {@code on} (the mods enabled now): kind → id → field → value (String, or Number for
     * basePrice). Only values that differ from the file's base are returned.
     */
    public static Map<String, Map<String, Map<String, Object>>> specChanges(JSONObject strings, Collection<String> on) {
        Map<String, Map<String, Map<String, Object>>> out = new LinkedHashMap<String, Map<String, Map<String, Object>>>();
        if (!has(strings)) return out;
        JSONObject base = (JSONObject) copy(strings);
        base.remove("versions");
        JSONObject res = resolve(strings, on).value;
        for (Map.Entry<String, List<String>> k : SPEC_FIELDS.entrySet()) {
            JSONObject all = res.optJSONObject(k.getKey());
            if (all == null) continue;
            JSONObject baseAll = base.optJSONObject(k.getKey());
            for (String id : sortedKeys(all)) {
                JSONObject fields = all.optJSONObject(id);
                if (fields == null) continue;
                JSONObject baseFields = baseAll == null ? null : baseAll.optJSONObject(id);
                for (String f : k.getValue()) {
                    if (!fields.has(f) || fields.isNull(f)) continue;
                    Object v = fields.opt(f);
                    if ("basePrice".equals(f) ? !(v instanceof Number) : !(v instanceof String)) continue;
                    Object b = baseFields == null ? null : baseFields.opt(f);
                    if (b != null && canonical(b).equals(canonical(v))) continue;
                    Map<String, Map<String, Object>> byId = out.get(k.getKey());
                    if (byId == null) out.put(k.getKey(), byId = new LinkedHashMap<String, Map<String, Object>>());
                    Map<String, Object> fm = byId.get(id);
                    if (fm == null) byId.put(id, fm = new LinkedHashMap<String, Object>());
                    fm.put(f, v);
                }
            }
        }
        return out;
    }
}
