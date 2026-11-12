package lastai.sagalib.model;

import lastai.sagalib.core.JsonReader;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** An encounter file (docs/FORMAT.md, "Encounters"). Definitions only: never saved. */
public final class EncounterDef {
    public static final List<String> WHEN = Arrays.asList("quest-stage", "new-game", "periodic");
    public static final List<String> BEHAVIORS = Arrays.asList("patrol", "guard", "intercept-player", "travel", "ambush");

    public String id;
    public String name;
    public String faction;
    public String fleetType;
    /** fp (FleetFactoryV3 with combat points) or explicit (the listed variants). */
    public String size;
    public float points;
    public final List<String> variants = new ArrayList<String>();
    /** null = the faction's default quality. */
    public Float quality;
    public String flagshipCommander;
    public String fleetName;

    public String at;
    public String when;
    /** Optional: spawn when this quest reaches {@link #stage}. */
    public String quest;
    public String stage;
    public boolean despawnOnStageEnd;
    /** periodic: days before respawning. */
    public float days;

    public String behavior;
    public String travelTo;
    public boolean hostile;
    public boolean noRepImpact;
    public boolean important;
    public boolean allowDisengage = true;
    public String commDialog;
    public String defeated;
    public String avoided;
    public String source;

    public boolean explicit() { return "explicit".equals(size); }

    public static EncounterDef parse(JSONObject o, JsonReader r) {
        if (!r.checkFormat(o)) return null;
        EncounterDef e = new EncounterDef();
        e.source = r.file;
        e.id = r.str(o, "id", "$", true, null);
        if (e.id == null) return null;
        e.name = r.str(o, "name", "$", false, e.id);
        JSONObject f = r.obj(o, "fleet", "$", true);
        if (f == null) return null;
        e.faction = r.str(f, "faction", "$.fleet", true, null);
        if (e.faction == null) return null;
        e.fleetType = r.str(f, "type", "$.fleet", false, "patrolMedium");
        e.size = r.str(f, "size", "$.fleet", false, "fp");
        if (!"fp".equals(e.size) && !"explicit".equals(e.size)) {
            r.error("$.fleet.size", "unknown size \"" + e.size + "\" (fp or explicit)");
            return null;
        }
        Double pts = r.num(f, "points", "$.fleet", 50d);
        e.points = pts == null ? 50 : pts.floatValue();
        e.variants.addAll(r.strings(f, "variants", "$.fleet"));
        if (e.explicit() && e.variants.isEmpty()) {
            r.error("$.fleet.variants", "an explicit fleet needs at least one variant");
            return null;
        }
        Double q = r.num(f, "quality", "$.fleet", null);
        e.quality = q == null ? null : q.floatValue();
        e.flagshipCommander = r.str(f, "flagshipCommander", "$.fleet", false, "");
        e.fleetName = r.str(f, "name", "$.fleet", false, "");

        JSONObject s = r.obj(o, "spawn", "$", true);
        if (s == null) return null;
        e.at = r.str(s, "at", "$.spawn", true, null);
        if (e.at == null) return null;
        e.when = r.str(s, "when", "$.spawn", false, "quest-stage");
        if (!WHEN.contains(e.when)) {
            r.error("$.spawn.when", "unknown value \"" + e.when + "\" " + WHEN);
            return null;
        }
        e.quest = emptyToNull(r.str(s, "quest", "$.spawn", false, ""));
        e.stage = emptyToNull(r.str(s, "stage", "$.spawn", false, ""));
        if ((e.quest == null) != (e.stage == null)) {
            r.error("$.spawn", "quest and stage go together; this encounter only spawns through SF_Spawn");
            e.quest = null;
            e.stage = null;
        }
        Boolean d = r.bool(s, "despawnOnStageEnd", "$.spawn", false);
        e.despawnOnStageEnd = d != null && d;
        Double days = r.num(s, "days", "$.spawn", 0d);
        e.days = days == null ? 0 : days.floatValue();

        e.behavior = r.str(o, "behavior", "$", false, "patrol");
        if (!BEHAVIORS.contains(e.behavior)) {
            r.error("$.behavior", "unknown behavior \"" + e.behavior + "\" " + BEHAVIORS + "; using patrol");
            e.behavior = "patrol";
        }
        e.travelTo = r.str(o, "travelTo", "$", false, "");
        JSONObject fl = r.obj(o, "flags", "$", false);
        if (fl != null) {
            e.hostile = bool(r, fl, "hostile", false);
            e.noRepImpact = bool(r, fl, "noRepImpact", false);
            e.important = bool(r, fl, "important", false);
            e.allowDisengage = bool(r, fl, "allowDisengage", true);
        }
        e.commDialog = r.str(o, "commDialog", "$", false, "");
        JSONObject out = r.obj(o, "outcomes", "$", false);
        if (out != null) {
            e.defeated = emptyToNull(r.str(out, "defeated", "$.outcomes", false, ""));
            e.avoided = emptyToNull(r.str(out, "avoided", "$.outcomes", false, ""));
        }
        return e;
    }

    private static boolean bool(JsonReader r, JSONObject o, String key, boolean def) {
        Boolean b = r.bool(o, key, "$.flags", def);
        return b == null ? def : b;
    }

    static String emptyToNull(String s) { return s == null || s.isEmpty() ? null : s; }
}
