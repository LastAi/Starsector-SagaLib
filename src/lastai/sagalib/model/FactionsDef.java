package lastai.sagalib.model;

import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.ModGate;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** One mod's data/sagaforge/factions.json: starting relations and scripted changes. */
public final class FactionsDef {
    public static final class Relation {
        public final String faction;
        public final String other;
        public final float value;
        /** SagaLib 1.3: only with / only without these mods ({"value": v, "modsWith": [...]}). */
        public ModGate gate = ModGate.NONE;

        public Relation(String faction, String other, float value) {
            this.faction = faction;
            this.other = other;
            this.value = value;
        }
    }

    public static final class Change {
        public String faction;
        /** "remove" (format 1); SagaLib 1.3: "add", "set", "hideFromIntelTab" and "showInIntelTab" (no path or value). */
        public String op;
        /** A list of the .faction file, e.g. "knownShips.hulls". */
        public String path;
        public String value;
        public String where;
        /** SagaLib 1.3: only with / only without these mods. */
        public ModGate gate = ModGate.NONE;
    }

    public final List<Relation> relations = new ArrayList<Relation>();
    public final List<Change> scripted = new ArrayList<Change>();
    /**
     * SagaLib 1.3 (Saga Forge task #63): factions whose starting relations the mod also ships in a
     * Nexerelin faction config (data/config/exerelinFactionConfig/&lt;id&gt;.json, with
     * useConfigRelationshipsInNonRandomSector). With Nexerelin, their relations without mod
     * conditions are left to it.
     */
    public final List<String> nexerelin = new ArrayList<String>();
    public String source;
    /** The mod that ships the file. */
    public String mod;

    /** Where Nexerelin reads a faction's config (FORMATS.md, "Nexerelin 0.12.2d"). */
    public static String nexConfigPath(String faction) { return "data/config/exerelinFactionConfig/" + faction + ".json"; }

    /** Whether the mod ships a file; SagaLib asks the game (SettingsAPI.loadJSON(path, mod)). */
    public interface ShipsFile { boolean has(String mod, String path); }

    /**
     * Task #63: true when this relation is left to Nexerelin: Nexerelin is enabled, the faction is
     * listed in "nexerelin", the relation has no mod conditions (those aren't in the config), and the
     * mod really ships the faction's config (else SagaLib sets it as before).
     */
    public boolean leftToNexerelin(Relation r, boolean nexerelinEnabled, ShipsFile files) {
        if (!nexerelinEnabled || !nexerelin.contains(r.faction) || !r.gate.isEmpty()) return false;
        return files.has(mod, nexConfigPath(r.faction));
    }

    public static FactionsDef parse(JSONObject o, JsonReader r) {
        if (!r.checkFormat(o)) return null;
        FactionsDef d = new FactionsDef();
        d.source = r.file;
        d.mod = r.mod;
        d.nexerelin.addAll(r.strings(o, "nexerelin", "$"));
        JSONObject rel = r.obj(o, "relations", "$", false);
        if (rel != null) {
            for (Iterator<?> it = rel.keys(); it.hasNext(); ) {
                String a = String.valueOf(it.next());
                JSONObject others = r.obj(rel, a, "$.relations", false);
                if (others == null) continue;
                for (Iterator<?> it2 = others.keys(); it2.hasNext(); ) {
                    String b = String.valueOf(it2.next());
                    String p = "$.relations." + a;
                    // SagaLib 1.3: a relation with mod conditions is an object {"value": v, "modsWith": [...], ...}
                    JSONObject withGate = others.optJSONObject(b);
                    Double v = withGate != null ? r.num(withGate, "value", JsonReader.at(p, b), null) : r.num(others, b, p, null);
                    if (v == null) {
                        if (withGate != null) r.error(JsonReader.at(JsonReader.at(p, b), "value"), "the relation's value is missing");
                        continue;
                    }
                    if (v < -1 || v > 1) {
                        r.error(JsonReader.at(p, b), "relation " + v + " is outside -1..1");
                        continue;
                    }
                    Relation rl = new Relation(a, b, v.floatValue());
                    if (withGate != null) rl.gate = ModGate.parse(withGate, JsonReader.at(p, b), r);
                    else rl.gate = ModGate.NONE.ownedBy(r.mod);
                    d.relations.add(rl);
                }
            }
        }
        JSONObject sc = r.obj(o, "scripted", "$", false);
        if (sc != null) {
            for (Iterator<?> it = sc.keys(); it.hasNext(); ) {
                String f = String.valueOf(it.next());
                String fp = "$.scripted." + f;
                Object v = sc.opt(f);
                if (!(v instanceof JSONArray)) {
                    r.error(fp, "should be a list of changes");
                    continue;
                }
                for (JsonReader.Indexed e : r.objects(sc, f, "$.scripted")) {
                    Change c = new Change();
                    c.faction = f;
                    c.where = e.path;
                    c.op = r.str(e.json, "op", e.path, true, null);
                    if (c.op == null) continue;
                    c.gate = ModGate.parse(e.json, e.path, r);
                    if ("hideFromIntelTab".equals(c.op) || "showInIntelTab".equals(c.op)) {
                        d.scripted.add(c);
                        continue;
                    }
                    if (!"remove".equals(c.op) && !"add".equals(c.op) && !"set".equals(c.op)) {
                        r.error(JsonReader.at(e.path, "op"), "unknown op \"" + c.op + "\" (remove, add, set, hideFromIntelTab or showInIntelTab)");
                        continue;
                    }
                    c.path = r.str(e.json, "path", e.path, true, null);
                    c.value = r.str(e.json, "value", e.path, true, null);
                    if (c.path == null || c.value == null) continue;
                    String bad = checkPath(c.op, c.path);
                    if (bad != null) {
                        r.error(JsonReader.at(e.path, "path"), bad);
                        continue;
                    }
                    bad = checkValue(c.path, c.value);
                    if (bad != null) {
                        r.error(JsonReader.at(e.path, "value"), bad);
                        continue;
                    }
                    d.scripted.add(c);
                }
            }
        }
        return d;
    }

    /** Lists "remove" can change (the .faction file's keys). */
    public static final List<String> REMOVABLE = java.util.Collections.unmodifiableList(java.util.Arrays.asList(
            "knownShips.hulls", "knownWeapons.weapons", "knownFighters.fighters", "knownIndustries.industries", "knownHullMods.hullMods", "hullMods",
            "priorityShips.hulls", "priorityWeapons.weapons", "priorityFighters.fighters", "priorityHullMods.hullMods", "illegalCommodities"));
    /** SagaLib 1.3: lists "add" can change. */
    public static final List<String> ADDABLE = java.util.Collections.unmodifiableList(java.util.Arrays.asList(
            "knownShips.hulls", "knownWeapons.weapons", "knownFighters.fighters", "illegalCommodities"));
    /** SagaLib 1.3: fields "set" can change (plus doctrine.<field>). */
    public static final List<String> SETTABLE = java.util.Collections.unmodifiableList(java.util.Arrays.asList(
            "displayName", "displayNameWithArticle", "logo", "crest", "secondaryColor"));
    /** SagaLib 1.3: doctrine fields "set" can change, with their range (min, max; integers unless noted). */
    public static final java.util.Map<String, float[]> DOCTRINE;
    static {
        java.util.Map<String, float[]> m = new java.util.LinkedHashMap<String, float[]>();
        for (String f : new String[] { "warships", "carriers", "phaseShips", "officerQuality", "shipQuality" }) m.put(f, new float[] { 0, 5 });
        for (String f : new String[] { "numShips", "shipSize", "aggression" }) m.put(f, new float[] { 1, 5 });
        m.put("combatFreighterProbability", new float[] { 0, 1 });
        DOCTRINE = java.util.Collections.unmodifiableMap(m);
    }

    /** Null if {@code op} can change {@code path}; else why not. */
    static String checkPath(String op, String path) {
        if ("remove".equals(op)) return REMOVABLE.contains(path) ? null : "SagaLib can't remove from \"" + path + "\" " + REMOVABLE;
        if ("add".equals(op)) return ADDABLE.contains(path) ? null : "SagaLib can't add to \"" + path + "\" " + ADDABLE;
        if ("displayNameLong".equals(path)) return "the game (0.98a) has no setter for displayNameLong; set displayName and displayNameWithArticle";
        if (SETTABLE.contains(path)) return null;
        if (path.startsWith("doctrine.") && DOCTRINE.containsKey(path.substring(9))) return null;
        return "SagaLib can't set \"" + path + "\" (" + SETTABLE + ", or doctrine." + DOCTRINE.keySet() + ")";
    }

    /** Null if the value suits the path; else why not. */
    static String checkValue(String path, String value) {
        if ("secondaryColor".equals(path)) return color(value) == null ? "should be \"r,g,b\" or \"r,g,b,a\" (0-255 each), not \"" + value + "\"" : null;
        if (path.startsWith("doctrine.")) {
            String f = path.substring(9);
            float[] range = DOCTRINE.get(f);
            Float v = doctrineValue(f, value);
            if (v == null) return "should be " + ("combatFreighterProbability".equals(f) ? "a number" : "a whole number") + " from " + fmt(range[0]) + " to " + fmt(range[1]) + ", not \"" + value + "\"";
        }
        if (value.trim().isEmpty()) return "is empty";
        return null;
    }

    private static String fmt(float f) { return f == (int) f ? String.valueOf((int) f) : String.valueOf(f); }

    /** A doctrine field's value, or null if it isn't a number in its range (whole for all but combatFreighterProbability). */
    public static Float doctrineValue(String field, String value) {
        float[] range = DOCTRINE.get(field);
        if (range == null || value == null) return null;
        try {
            float v = Float.parseFloat(value.trim());
            if (v < range[0] || v > range[1]) return null;
            if (!"combatFreighterProbability".equals(field) && v != Math.rint(v)) return null;
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "r,g,b" or "r,g,b,a" (0-255) → {r, g, b, a}; null if malformed. */
    public static int[] color(String s) {
        if (s == null) return null;
        String[] p = s.split(",");
        if (p.length != 3 && p.length != 4) return null;
        int[] out = { 0, 0, 0, 255 };
        try {
            for (int i = 0; i < p.length; i++) {
                int v = Integer.parseInt(p[i].trim());
                if (v < 0 || v > 255) return null;
                out[i] = v;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }
}
