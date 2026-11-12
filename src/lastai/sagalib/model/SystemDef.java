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
    }

    public String id;
    public String name;
    /** new or existing (id = the game's system id). */
    public String kind;
    public float hyperX;
    public float hyperY;
    public String starType;
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
            JSONObject star = r.obj(o, "star", "$", true);
            if (star == null) return null;
            s.starType = r.str(star, "type", "$.star", true, null);
            if (s.starType == null) return null;
            s.starRadius = num(r, star, "radius", "$.star", 500);
            s.corona = num(r, star, "corona", "$.star", 300);
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
        return s;
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
            if (md.faction != null) e.market = md;
        }
        return e;
    }
}
