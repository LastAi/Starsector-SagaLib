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
    /** SagaLib 1.3 (task #62): scales {@link #points} (size fp only) by a mod setting (null = never scaled). */
    public lastai.sagalib.core.Settings.Scale sizeScale;
    public final List<String> variants = new ArrayList<String>();
    /**
     * SagaLib 1.4 (Saga Forge #67 U40): an explicit fleet plus this many fleet points drawn like vanilla
     * (FleetFactoryV3: the faction's doctrine and known ships); 0 = none.
     */
    public float randomPoints;
    /** SagaLib 1.4 (#67 U40): extra groups that may join the fleet when it spawns (any size). */
    public final List<lastai.sagalib.core.FleetExtras.Group> extras = new ArrayList<lastai.sagalib.core.FleetExtras.Group>();
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
    /**
     * SagaLib 1.4 (Saga Forge #67 U46): the encounter's own outcome actions, run quietly by its
     * {@link #defeated} / {@link #avoided} trigger (null = a rule on that trigger, if any).
     */
    public java.util.List<lastai.sagalib.core.QuietOps.Op> defeatedActions;
    public java.util.List<lastai.sagalib.core.QuietOps.Op> avoidedActions;
    public String source;
    /** SagaLib 1.3: only with / only without these mods (modsWith, modsWithout, modsAny). */
    public lastai.sagalib.core.ModGate gate = lastai.sagalib.core.ModGate.NONE;

    /** SagaLib 1.1 (a fleet template, R27): ship names parallel to {@link #variants} ("" = the game's), the flagship's index (-1 = the game picks). */
    public final List<String> shipNames = new ArrayList<String>();
    public int flagship = -1;
    /** SagaLib 1.1: the template it was made from: $global.sagaforge_fleet_&lt;template&gt;_defeats counts its defeats. */
    public String template;
    /** SagaLib 1.1: when the fleet is defeated, its captain (flagshipCommander) is gone for good. */
    public boolean captainDiesOnDefeat;

    /** SagaLib 1.4: the quiet actions of one of this encounter's outcome triggers, or null. */
    public java.util.List<lastai.sagalib.core.QuietOps.Op> quietOps(String trigger) {
        if (trigger == null) return null;
        if (trigger.equals(defeated)) return defeatedActions;
        if (trigger.equals(avoided)) return avoidedActions;
        return null;
    }

    public boolean explicit() { return "explicit".equals(size); }

    /** SagaLib 1.3 (task #62): the fleet points now (size fp scaled by {@link #sizeScale}; explicit fleets unchanged). */
    public float scaledPoints(lastai.sagalib.core.Memory mem) {
        if (explicit()) return points;
        return lastai.sagalib.core.Settings.points(points, sizeScale, mem);
    }

    public static EncounterDef parse(JSONObject o, JsonReader r) {
        if (!r.checkFormat(o)) return null;
        EncounterDef e = new EncounterDef();
        e.source = r.file;
        e.gate = lastai.sagalib.core.ModGate.parse(o, "$", r);
        e.id = r.str(o, "id", "$", true, null);
        if (e.id == null) return null;
        e.name = r.str(o, "name", "$", false, e.id);
        JSONObject f = r.obj(o, "fleet", "$", true);
        if (f == null) return null;
        if (!parseFleet(e, f, "$.fleet", r)) return null;

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
            e.defeatedActions = lastai.sagalib.core.QuietOps.parse(out, "defeatedActions", "$.outcomes", r);
            e.avoidedActions = lastai.sagalib.core.QuietOps.parse(out, "avoidedActions", "$.outcomes", r);
            if (e.defeatedActions != null && e.defeated == null) r.warn("$.outcomes.defeatedActions", "actions without a \"defeated\" trigger never run");
            if (e.avoidedActions != null && e.avoided == null) r.warn("$.outcomes.avoidedActions", "actions without an \"avoided\" trigger never run");
        }
        return e;
    }

    /**
     * An encounter's "fleet" object into {@code e} (also a quest fight's fleet, SagaLib 1.4). False
     * (logged) when it can't make a fleet. {@code size} "template" with the template's ships listed
     * (variants) is read as explicit: Saga Forge writes a named fleet resolved, as explicit with its
     * {@code template} id.
     */
    public static boolean parseFleet(EncounterDef e, JSONObject f, String path, JsonReader r) {
        e.faction = r.str(f, "faction", path, true, null);
        if (e.faction == null) return false;
        e.fleetType = r.str(f, "type", path, false, "patrolMedium");
        e.size = r.str(f, "size", path, false, "fp");
        if ("template".equals(e.size)) {
            if (r.strings(f, "variants", path).isEmpty()) {
                r.error(JsonReader.at(path, "size"), "size \"template\" needs the template's ships in \"variants\" (written resolved, as explicit); no fleet");
                return false;
            }
            e.size = "explicit";
        }
        if (!"fp".equals(e.size) && !"explicit".equals(e.size)) {
            r.error(JsonReader.at(path, "size"), "unknown size \"" + e.size + "\" (fp or explicit)");
            return false;
        }
        Double pts = r.num(f, "points", path, 50d);
        e.points = pts == null ? 50 : pts.floatValue();
        e.sizeScale = lastai.sagalib.core.Settings.Scale.parse(f, "sizeScale", path, r);
        e.variants.addAll(r.strings(f, "variants", path));
        if (e.explicit() && e.variants.isEmpty()) {
            r.error(JsonReader.at(path, "variants"), "an explicit fleet needs at least one variant");
            return false;
        }
        Double q = r.num(f, "quality", path, null);
        e.quality = q == null ? null : q.floatValue();
        e.flagshipCommander = r.str(f, "flagshipCommander", path, false, "");
        e.fleetName = r.str(f, "name", path, false, "");
        e.shipNames.addAll(r.strings(f, "shipNames", path));
        Double fs = r.num(f, "flagship", path, -1d);
        e.flagship = fs == null ? -1 : (int) Math.round(fs);
        e.template = emptyToNull(r.str(f, "template", path, false, ""));
        Boolean cd = r.bool(f, "captainDiesOnDefeat", path, false);
        e.captainDiesOnDefeat = cd != null && cd;
        // SagaLib 1.4 (#67 U40): a random part for an explicit fleet, and extra groups
        Double rp = r.num(f, "randomPoints", path, 0d);
        e.randomPoints = rp == null ? 0 : Math.max(0, rp.floatValue());
        org.json.JSONArray ex = f.optJSONArray("extras");
        for (int i = 0; ex != null && i < ex.length(); i++) {
            JSONObject g = ex.optJSONObject(i);
            String gp = JsonReader.at(JsonReader.at(path, "extras"), i);
            if (g == null) { r.error(gp, "an extra group is {\"chance\": 0.5, \"pick\": \"all\" or a number, \"ships\": [...]}; skipped"); continue; }
            lastai.sagalib.core.FleetExtras.Group grp = new lastai.sagalib.core.FleetExtras.Group();
            Double ch = r.num(g, "chance", gp, 1d);
            grp.chance = ch == null ? 1f : (float) Math.max(0, Math.min(1, ch));
            Object pk = g.opt("pick");
            grp.pick = pk instanceof Number ? Math.max(0, ((Number) pk).intValue()) : 0;
            org.json.JSONArray ships = g.optJSONArray("ships");
            for (int k = 0; ships != null && k < ships.length(); k++) {
                Object s = ships.opt(k);
                if (s instanceof String) { grp.variants.add((String) s); grp.weights.add(1f); }
                else if (s instanceof JSONObject && !((JSONObject) s).optString("variant", "").isEmpty()) {
                    grp.variants.add(((JSONObject) s).optString("variant"));
                    grp.weights.add((float) ((JSONObject) s).optDouble("weight", 1));
                }
            }
            if (grp.variants.isEmpty()) r.warn(gp, "an extra group without ships; it adds nothing");
            e.extras.add(grp);
        }
        return true;
    }

    private static boolean bool(JsonReader r, JSONObject o, String key, boolean def) {
        Boolean b = r.bool(o, key, "$.flags", def);
        return b == null ? def : b;
    }

    static String emptyToNull(String s) { return s == null || s.isEmpty() ? null : s; }
}
