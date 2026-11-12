package lastai.sagalib.model;

import lastai.sagalib.core.JsonReader;
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

        public Relation(String faction, String other, float value) {
            this.faction = faction;
            this.other = other;
            this.value = value;
        }
    }

    public static final class Change {
        public String faction;
        /** Only "remove" in format 1. */
        public String op;
        /** A list of the .faction file, e.g. "knownShips.hulls". */
        public String path;
        public String value;
        public String where;
    }

    public final List<Relation> relations = new ArrayList<Relation>();
    public final List<Change> scripted = new ArrayList<Change>();
    public String source;

    public static FactionsDef parse(JSONObject o, JsonReader r) {
        if (!r.checkFormat(o)) return null;
        FactionsDef d = new FactionsDef();
        d.source = r.file;
        JSONObject rel = r.obj(o, "relations", "$", false);
        if (rel != null) {
            for (Iterator<?> it = rel.keys(); it.hasNext(); ) {
                String a = String.valueOf(it.next());
                JSONObject others = r.obj(rel, a, "$.relations", false);
                if (others == null) continue;
                for (Iterator<?> it2 = others.keys(); it2.hasNext(); ) {
                    String b = String.valueOf(it2.next());
                    String p = "$.relations." + a;
                    Double v = r.num(others, b, p, null);
                    if (v == null) continue;
                    if (v < -1 || v > 1) {
                        r.error(JsonReader.at(p, b), "relation " + v + " is outside -1..1");
                        continue;
                    }
                    d.relations.add(new Relation(a, b, v.floatValue()));
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
                    c.path = r.str(e.json, "path", e.path, true, null);
                    c.value = r.str(e.json, "value", e.path, true, null);
                    if (c.op == null || c.path == null || c.value == null) continue;
                    if (!"remove".equals(c.op)) {
                        r.error(JsonReader.at(e.path, "op"), "unknown op \"" + c.op + "\" (format 1 has only remove)");
                        continue;
                    }
                    d.scripted.add(c);
                }
            }
        }
        return d;
    }
}
