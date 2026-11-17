package lastai.sagalib.model;

import lastai.sagalib.core.JsonReader;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * SagaLib 1.2 (Phase 7e, RC-R10): scatter: copies of one custom entity placed in N places picked
 * with vanilla's mission search (a random target without a quest), once per game:
 * <pre>
 * {"format": 1, "id": "svf_caches", "count": 3, "addToExistingSaves": true,
 *  "target": {"pick": "planet", "require": [...], "prefer": [...], "systemFirst": false},
 *  "entity": {"type": "supply_cache", "name": "Hidden cache", "faction": "neutral"},
 *  "loot": [{"group": "svf_cache_loot", "chances": 2}], "defenders": {...}}
 * </pre>
 * Placed with ids &lt;id&gt;_1 … &lt;id&gt;_N, orbiting the picked planet or entity (a system pick: its
 * centre), each in a different system.
 */
public final class ScatterDef {
    public String id;
    public int count = 1;
    public boolean addToExistingSaves;
    public QuestDef.Target target;
    public String entityType;
    public String entityName;
    public String entityFaction = "neutral";
    public final List<SystemDef.Loot> loot = new ArrayList<SystemDef.Loot>();
    public SystemDef.Defenders defenders;
    /** Where "within N light-years" criteria measure from: an entity or market id; null = the player. */
    public String from;
    public String source;

    public static ScatterDef parse(JSONObject o, JsonReader r) {
        if (!r.checkFormat(o)) return null;
        ScatterDef s = new ScatterDef();
        s.source = r.file;
        s.id = r.str(o, "id", "$", true, null);
        if (s.id == null) return null;
        Double c = r.num(o, "count", "$", 1.0);
        s.count = c == null ? 1 : Math.max(0, (int) Math.round(c));
        Boolean ex = r.bool(o, "addToExistingSaves", "$", false);
        s.addToExistingSaves = ex != null && ex;
        JSONObject t = r.obj(o, "target", "$", true);
        if (t == null) return null;
        s.target = QuestDef.parseTarget(r, new JsonReader.Indexed(t, "$.target"), false);
        if (s.target == null) return null;
        JSONObject e = r.obj(o, "entity", "$", true);
        if (e == null) return null;
        s.entityType = r.str(e, "type", "$.entity", true, null);
        if (s.entityType == null) return null;
        s.entityName = r.str(e, "name", "$.entity", false, s.entityType);
        s.entityFaction = r.str(e, "faction", "$.entity", false, "neutral");
        SystemDef.parseLoot(r, o, "$", s.loot);
        s.defenders = SystemDef.parseDefenders(r, o, "$");
        s.from = r.str(o, "from", "$", false, null);
        if (s.from != null && s.from.isEmpty()) s.from = null;
        return s;
    }
}
