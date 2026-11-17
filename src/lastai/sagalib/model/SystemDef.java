package lastai.sagalib.model;

import lastai.sagalib.core.JsonReader;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A star system file (docs/FORMAT.md, "Star systems"). Definitions only: never saved. */
public final class SystemDef {
    public static final List<String> ENTITY_KINDS = Arrays.asList("planet", "moon", "station", "asteroidBelt", "ring", "derelict",
            "commRelay", "navBuoy", "sensorArray", "custom", "jumpPoint");

    public static final class Orbit {
        /** "star" or another entity's id. */
        public String focus = "star";
        public float radius;
        public float angle;
        public float days;
    }

    public static final class MarketDef {
        public String id;
        public String name;
        public String faction;
        public int size;
        public final List<String> conditions = new ArrayList<String>();
        public final List<String> industries = new ArrayList<String>();
        public final List<String> submarkets = new ArrayList<String>();
        public boolean freePort;
        public final List<String> people = new ArrayList<String>();
        /** SagaLib 1.2 (Phase 7e, RC-R11): $core_noDeciv, as vanilla's pirate and Pather bases. */
        public boolean protectFromDeciv;
    }

    /** SagaLib 1.2 (RC-F7): a drop group the entity's salvage rolls (vanilla addDropRandom / addDropValue). */
    public static final class Loot {
        public String group;
        public int chances;
        public int value;
    }

    /** SagaLib 1.2 (RC-F7): defenders of a salvage entity (vanilla DefenderDataOverride). */
    public static final class Defenders {
        public String faction;
        public float prob = 1f, minStr, maxStr;
        public int maxSize = 4;
    }

    public static final class Entity {
        public String id;
        public String kind;
        /** planets.json type, custom_entities.json type, ring texture key, or a derelict's variant ("wreck" = random). */
        public String type;
        public String name;
        public float radius;
        public float width;
        public int count;
        /** Owner of a custom entity (default: the market's faction, else neutral). */
        public String faction;
        public Orbit orbit = new Orbit();
        public MarketDef market;
        public String where;
        /** SagaLib 1.1: built only when this quest reaches this stage (or by SF_PlaceEntity); null = with the system. */
        public StageRef appears;
        /** SagaLib 1.1: with appears, takes this entity's place (orbit) and removes it. */
        public String replaces;
        /** SagaLib 1.1: a planet without a market: its conditions (a condition-only market), and how surveyed it is. */
        public final List<String> conditions = new ArrayList<String>();
        public String surveyed;
        /** SagaLib 1.2 (RC-F7): salvage loot and defenders. */
        public final List<Loot> loot = new ArrayList<Loot>();
        public Defenders defenders;
        /** SagaLib 1.2: its ruins stay unexplored at new game (vanilla marks ruins in a system with a colony explored). */
        public boolean ruinsUnexplored;

        public boolean deferred() { return appears != null; }
    }

    /** A quest and one of its stages (SagaLib 1.1). */
    public static final class StageRef {
        public String quest;
        public String stage;
    }

    /** SagaLib 1.1: an entity removed when a quest reaches a stage (or by SF_RemoveEntity). */
    public static final class Removal {
        public String target;
        public StageRef at;
        /** "keep" (default: an entity with a market isn't removed) or "decivilize". */
        public String market = "keep";
        public String where;
    }

    public String id;
    public String name;
    /** new or existing (id = the game's system id). */
    public String kind;
    public float hyperX;
    public float hyperY;
    /** Null for a system without a star (SagaLib 1.1): an empty centre lit by {@link #light}. */
    public String starType;
    /** Light colour of a starless system, [r, g, b] 0-255. */
    public int[] light;
    public final List<Removal> removals = new ArrayList<Removal>();
    public float starRadius;
    public float corona;
    public boolean addToExistingSaves;
    public final List<String> tags = new ArrayList<String>();
    public String music;
    public final List<Entity> entities = new ArrayList<Entity>();
    public String source;

    public boolean isNew() { return "new".equals(kind); }

    /** The tag a built system carries so it is found again in a save. */
    public String tag() { return "sagaforge:" + id; }

    public Entity entity(String entityId) {
        for (Entity e : entities) if (e.id.equals(entityId)) return e;
        return null;
    }

    public boolean hasJumpPoint() {
        for (Entity e : entities) if ("jumpPoint".equals(e.kind)) return true;
        return false;
    }

    public static SystemDef parse(JSONObject o, JsonReader r) {
        if (!r.checkFormat(o)) return null;
        SystemDef s = new SystemDef();
        s.source = r.file;
        s.id = r.str(o, "id", "$", true, null);
        if (s.id == null) return null;
        s.name = r.str(o, "name", "$", false, s.id);
        s.kind = r.str(o, "kind", "$", false, "new");
        if (!"new".equals(s.kind) && !"existing".equals(s.kind)) {
            r.error("$.kind", "unknown kind \"" + s.kind + "\" (new or existing)");
            return null;
        }
        if (s.isNew()) {
            JSONArray h = r.arr(o, "hyperspace", "$");
            if (h.length() != 2 || !(h.opt(0) instanceof Number) || !(h.opt(1) instanceof Number)) {
                r.error("$.hyperspace", "a new system needs its hyperspace location as [x, y]");
                return null;
            }
            s.hyperX = ((Number) h.opt(0)).floatValue();
            s.hyperY = ((Number) h.opt(1)).floatValue();
            if (o.has("star") && o.isNull("star")) {
                // SagaLib 1.1: no star, an empty centre and a light colour
                JSONArray l = o.optJSONArray("light");
                s.light = new int[] { 255, 255, 255 };
                if (l != null && l.length() >= 3) for (int i = 0; i < 3; i++) s.light[i] = Math.max(0, Math.min(255, l.optInt(i, 255)));
            } else {
                JSONObject star = r.obj(o, "star", "$", true);
                if (star == null) return null;
                s.starType = r.str(star, "type", "$.star", true, null);
                if (s.starType == null) return null;
                s.starRadius = num(r, star, "radius", "$.star", 500);
                s.corona = num(r, star, "corona", "$.star", 300);
            }
        }
        Boolean add = r.bool(o, "addToExistingSaves", "$", false);
        s.addToExistingSaves = add != null && add;
        s.tags.addAll(r.strings(o, "tags", "$"));
        s.music = r.str(o, "music", "$", false, "");
        for (JsonReader.Indexed e : r.objects(o, "entities", "$")) {
            Entity en = parseEntity(e.json, e.path, r);
            if (en == null) continue;
            if (s.entity(en.id) != null) {
                r.error(JsonReader.at(e.path, "id"), "entity \"" + en.id + "\" is listed twice; the second one is skipped");
                continue;
            }
            s.entities.add(en);
        }
        for (JsonReader.Indexed x : r.objects(o, "removals", "$")) {
            Removal rm = new Removal();
            rm.where = r.file + " " + x.path;
            rm.target = r.str(x.json, "target", x.path, true, null);
            if (rm.target == null) continue;
            rm.at = stageRef(x.json, x.path, r);
            rm.market = r.str(x.json, "market", x.path, false, "keep");
            if (!"keep".equals(rm.market) && !"decivilize".equals(rm.market)) {
                r.error(JsonReader.at(x.path, "market"), "market is \"keep\" or \"decivilize\"; using keep");
                rm.market = "keep";
            }
            s.removals.add(rm);
        }
        return s;
    }

    /** {"quest": q, "stage": s} at key (default: the object itself, for removals). Null if incomplete. */
    static StageRef stageRef(JSONObject o, String path, JsonReader r) { return stageRef(o, path, r, null); }

    static StageRef stageRef(JSONObject o, String path, JsonReader r, String key) {
        JSONObject x = key == null ? o : o.optJSONObject(key);
        String p = key == null ? path : JsonReader.at(path, key);
        if (x == null) return null;
        String q = r.str(x, "quest", p, false, null), st = r.str(x, "stage", p, false, null);
        if (q == null || q.isEmpty() || st == null || st.isEmpty()) return null;
        StageRef sr = new StageRef();
        sr.quest = q;
        sr.stage = st;
        return sr;
    }

    static float num(JsonReader r, JSONObject o, String key, String path, float def) {
        Double d = r.num(o, key, path, (double) def);
        return d == null ? def : d.floatValue();
    }

    static Entity parseEntity(JSONObject o, String path, JsonReader r) {
        Entity e = new Entity();
        e.where = r.file + " " + path;
        e.id = r.str(o, "id", path, true, null);
        e.kind = r.str(o, "kind", path, true, null);
        if (e.id == null || e.kind == null) return null;
        if (!ENTITY_KINDS.contains(e.kind)) {
            r.error(JsonReader.at(path, "kind"), "unknown entity kind \"" + e.kind + "\" " + ENTITY_KINDS);
            return null;
        }
        e.type = r.str(o, "type", path, false, "");
        e.name = r.str(o, "name", path, false, e.id);
        e.radius = num(r, o, "radius", path, 100);
        e.width = num(r, o, "width", path, 200);
        e.count = (int) num(r, o, "count", path, 100);
        e.faction = r.str(o, "faction", path, false, "");
        JSONObject orbit = r.obj(o, "orbit", path, true);
        if (orbit == null) return null;
        String op = JsonReader.at(path, "orbit");
        e.orbit.focus = r.str(orbit, "focus", op, false, "star");
        e.orbit.radius = num(r, orbit, "radius", op, 0);
        e.orbit.angle = num(r, orbit, "angle", op, 0);
        e.orbit.days = num(r, orbit, "days", op, 100);
        if (o.has("appears")) {
            JSONObject ap = o.optJSONObject("appears");
            if (ap == null) r.error(JsonReader.at(path, "appears"), "appears is {\"quest\": …, \"stage\": …}; ignored");
            else {
                // {} or no stage: placed only by SF_PlaceEntity
                StageRef sr = stageRef(o, path, r, "appears");
                e.appears = sr != null ? sr : new StageRef();
            }
        }
        e.replaces = r.str(o, "replaces", path, false, null);
        if (e.replaces != null && e.replaces.isEmpty()) e.replaces = null;
        if (e.replaces != null && e.appears == null) r.error(JsonReader.at(path, "replaces"), "replaces only works with appears; ignored");
        e.conditions.addAll(r.strings(o, "conditions", path));
        e.surveyed = r.str(o, "surveyed", path, false, null);
        parseLoot(r, o, path, e.loot);
        Boolean ru = r.bool(o, "ruinsUnexplored", path, false);
        e.ruinsUnexplored = ru != null && ru;
        e.defenders = parseDefenders(r, o, path);
        JSONObject m = r.obj(o, "market", path, false);
        if (m != null) {
            String mp = JsonReader.at(path, "market");
            MarketDef md = new MarketDef();
            md.id = r.str(m, "id", mp, false, e.id);
            md.name = r.str(m, "name", mp, false, e.name);
            md.faction = r.str(m, "faction", mp, true, null);
            Double size = r.num(m, "size", mp, 3d);
            md.size = size == null ? 3 : (int) Math.round(size);
            md.conditions.addAll(r.strings(m, "conditions", mp));
            md.industries.addAll(r.strings(m, "industries", mp));
            md.submarkets.addAll(r.strings(m, "submarkets", mp));
            Boolean fp = r.bool(m, "freePort", mp, false);
            md.freePort = fp != null && fp;
            md.people.addAll(r.strings(m, "people", mp));
            Boolean nd = r.bool(m, "protectFromDeciv", mp, false);
            md.protectFromDeciv = nd != null && nd;
            if (md.faction != null) e.market = md;
        }
        return e;
    }

    /** "loot": [{"group": "svf_cache", "chances": 3}] or [{"group": "g", "value": 5000}] (format 1 wrote []). */
    public static void parseLoot(JsonReader r, JSONObject o, String path, List<Loot> out) {
        for (JsonReader.Indexed x : r.objects(o, "loot", path)) {
            Loot l = new Loot();
            l.group = r.str(x.json, "group", x.path, true, null);
            if (l.group == null) continue;
            Double c = r.num(x.json, "chances", x.path, 0.0);
            Double v = r.num(x.json, "value", x.path, 0.0);
            l.chances = c == null ? 0 : (int) Math.round(c);
            l.value = v == null ? 0 : (int) Math.round(v);
            if (l.chances <= 0 && l.value <= 0) { r.error(x.path, "loot needs chances or value"); continue; }
            out.add(l);
        }
    }

    public static Defenders parseDefenders(JsonReader r, JSONObject o, String path) {
        JSONObject d = r.obj(o, "defenders", path, false);
        if (d == null) return null;
        String dp = JsonReader.at(path, "defenders");
        Defenders df = new Defenders();
        df.faction = r.str(d, "faction", dp, true, null);
        if (df.faction == null) return null;
        Double pr = r.num(d, "prob", dp, 1.0), mn = r.num(d, "minStr", dp, 10.0), mx = r.num(d, "maxStr", dp, 30.0), ms = r.num(d, "maxSize", dp, 4.0);
        df.prob = pr == null ? 1f : pr.floatValue();
        df.minStr = mn == null ? 10f : mn.floatValue();
        df.maxStr = mx == null ? 30f : mx.floatValue();
        df.maxSize = ms == null ? 4 : ms.intValue();
        return df;
    }
}
