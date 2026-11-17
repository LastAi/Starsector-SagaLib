package lastai.sagalib.model;

import lastai.sagalib.core.JsonReader;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** An encounter file (docs/FORMAT.md, "Encounters"). Definitions only: never saved. */
public final class EncounterDef {
    public static final List<String> WHEN = Arrays.asList("quest-stage", "new-game", "periodic", "player-near", "roaming");
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
    /** player-near (SagaLib 1.1): how close the player's fleet must come to {@link #at}, in map units; 0 = anywhere in its system. */
    public float range;
    /** roaming (SagaLib 1.2): travels from {@link #at} to this place (an entity or market id). */
    public String to;
    /** roaming: at most this many on the way at once. */
    public int max = 1;
    /** roaming: days it stays at {@link #to} before it leaves (or goes back). */
    public float stayDays = 3f;
    /** roaming: travels back to {@link #at} after its stay. */
    public boolean roundTrip;

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

    /** SagaLib 1.1 (a fleet template, R27): ship names parallel to {@link #variants} ("" = the game's), the flagship's index (-1 = the game picks). */
    public final List<String> shipNames = new ArrayList<String>();
    public int flagship = -1;
    /** SagaLib 1.1: the template it was made from: $global.sagaforge_fleet_&lt;template&gt;_defeats counts its defeats. */
    public String template;
    /** SagaLib 1.1: when the fleet is defeated, its captain (flagshipCommander) is gone for good. */
    public boolean captainDiesOnDefeat;

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
        e.shipNames.addAll(r.strings(f, "shipNames", "$.fleet"));
        Double fs = r.num(f, "flagship", "$.fleet", -1d);
        e.flagship = fs == null ? -1 : (int) Math.round(fs);
        e.template = emptyToNull(r.str(f, "template", "$.fleet", false, ""));
        Boolean cd = r.bool(f, "captainDiesOnDefeat", "$.fleet", false);
        e.captainDiesOnDefeat = cd != null && cd;

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
        Double range = r.num(s, "range", "$.spawn", 0d);
        e.range = range == null ? 0 : Math.max(0, range.floatValue());
        // SagaLib 1.2: roaming fleets (a route from at to "to", every "days" on average, at most "max")
        e.to = emptyToNull(r.str(s, "to", "$.spawn", false, ""));
        Double mx = r.num(s, "max", "$.spawn", 1d);
        e.max = mx == null ? 1 : Math.max(0, mx.intValue());
        Double st = r.num(s, "stayDays", "$.spawn", 3d);
        e.stayDays = st == null ? 3f : Math.max(0, st.floatValue());
        Boolean rt = r.bool(s, "roundTrip", "$.spawn", false);
        e.roundTrip = rt != null && rt;
        if ("roaming".equals(e.when)) {
            if (e.to == null) { r.error("$.spawn.to", "a roaming fleet needs \"to\": where it travels"); return null; }
            if (e.days <= 0) { r.warn("$.spawn.days", "a roaming fleet needs \"days\" (on average between departures); 30 is used"); e.days = 30f; }
        }

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
