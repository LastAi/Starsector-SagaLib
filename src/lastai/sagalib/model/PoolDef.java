package lastai.sagalib.model;

import lastai.sagalib.core.JsonReader;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A random pool (SagaLib 1.2; docs/FORMAT.md, "Pools"): data/sagaforge/pools/&lt;id&gt;.json. Entries
 * have a weight, condition lines and a limit; {@code SF_RollPool} picks one of them by weight, or
 * "nothing" (the {@code nothing} weight).
 */
public final class PoolDef {
    public static final class Entry {
        public String id;
        public float weight;
        public final List<String> conditions = new ArrayList<String>();
        /** null, "playthrough" or "object" (per object rolled for; per playthrough when rolled globally). */
        public String once;
        /** At least this many days between two picks; 0 = none. */
        public float cooldownDays;
        /** At most this many picks per playthrough; 0 = no limit. */
        public int max;
    }

    public String id;
    /** "pickOne" (exactly one by weight, or nothing) or "rollEach" (reserved: the editor exports those as plain rules). */
    public String draw = "pickOne";
    /** The weight of "nothing happens". */
    public float nothing;
    public final List<Entry> entries = new ArrayList<Entry>();
    public String source;
    /**
     * SagaLib 1.2 (Phase 7e): "branch" (rolled by SF_RollPool) or "encounters": random encounters
     * whose entries are encounter ids, offered at vanilla's encounter points of {@link #points}
     * (slipstream, outside_system) with {@link #frequency} (vanilla's creators use about 10).
     */
    public String use = "branch";
    public final List<String> points = new ArrayList<String>();
    public float frequency = 10f;

    public boolean isEncounters() { return "encounters".equals(use); }

    /** The vanilla encounter point type for a short name (EncounterManager.EP_TYPE_*). */
    public static String pointType(String shortName) { return "ep_type_" + shortName; }

    public Entry entry(String id) {
        for (Entry e : entries) if (e.id.equals(id)) return e;
        return null;
    }

    public static PoolDef parse(JSONObject o, JsonReader r) {
        if (!r.checkFormat(o)) return null;
        PoolDef p = new PoolDef();
        p.source = r.file;
        p.id = r.str(o, "id", "$", true, null);
        if (p.id == null) return null;
        p.draw = r.str(o, "draw", "$", false, "pickOne");
        if (!"pickOne".equals(p.draw) && !"rollEach".equals(p.draw)) {
            r.error("$.draw", "should be pickOne or rollEach, not \"" + p.draw + "\"; pickOne is used");
            p.draw = "pickOne";
        }
        p.use = r.str(o, "use", "$", false, "branch");
        if (!"branch".equals(p.use) && !"encounters".equals(p.use)) { r.error("$.use", "should be branch or encounters, not \"" + p.use + "\""); p.use = "branch"; }
        p.points.addAll(r.strings(o, "points", "$"));
        Double fq = r.num(o, "frequency", "$", 10.0);
        p.frequency = fq == null || fq < 0 ? 0 : fq.floatValue();
        if (p.isEncounters() && p.points.isEmpty()) r.warn("$.points", "encounter pool \"" + p.id + "\" has no encounter points: it never fires");
        Double n = r.num(o, "nothing", "$", 0.0);
        p.nothing = n == null || n < 0 ? 0 : n.floatValue();
        for (JsonReader.Indexed e : r.objects(o, "entries", "$")) {
            Entry en = new Entry();
            en.id = r.str(e.json, "id", e.path, true, null);
            if (en.id == null) continue;
            if ("nothing".equals(en.id)) {
                r.error(JsonReader.at(e.path, "id"), "\"nothing\" is reserved for the nothing share; this entry is skipped");
                continue;
            }
            if (p.entry(en.id) != null) {
                r.error(JsonReader.at(e.path, "id"), "entry \"" + en.id + "\" is listed twice; this one is skipped");
                continue;
            }
            Double w = r.num(e.json, "weight", e.path, 1.0);
            en.weight = w == null || w < 0 ? 0 : w.floatValue();
            en.conditions.addAll(r.strings(e.json, "conditions", e.path));
            en.once = r.str(e.json, "once", e.path, false, null);
            if (en.once != null && !"playthrough".equals(en.once) && !"object".equals(en.once)) {
                r.error(JsonReader.at(e.path, "once"), "should be playthrough or object, not \"" + en.once + "\"; no once-limit is used");
                en.once = null;
            }
            Double cd = r.num(e.json, "cooldownDays", e.path, 0.0);
            en.cooldownDays = cd == null || cd < 0 ? 0 : cd.floatValue();
            Double mx = r.num(e.json, "max", e.path, 0.0);
            en.max = mx == null || mx < 0 ? 0 : mx.intValue();
            p.entries.add(en);
        }
        if (p.entries.isEmpty()) r.warn("$.entries", "pool \"" + p.id + "\" has no entries: it always gives nothing");
        return p;
    }
}
