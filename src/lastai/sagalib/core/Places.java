package lastai.sagalib.core;

import lastai.sagalib.model.QuestDef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SagaLib 1.3 (task #61, Saga Forge PLAN.md §10.3): the fixed places and people a quest needs, and
 * the owners of its markets (docs/FORMAT.md, "Places a quest needs" and "Markets changing owner").
 * The same as Saga Forge's Play mode (tools/saga-forge/src/shared/play.ts: runtimeNeeds,
 * missingNeed, rememberOwners, checkOwners); golden case test/golden/places.json.
 * <ul>
 * <li>A quest is not offered or started while a place or person it names doesn't exist: its
 *     contact, "$id ==" in its start conditions, its stage markers and Arrive places (a Nexerelin
 *     random sector has none of vanilla's hand-made markets; a mod may be gone).</li>
 * <li>With "ownerChange", the owners of its markets are remembered when it starts
 *     ({@code $global.sagaforge_owners_<quest>} = "market=faction;…") and compared once a day.</li>
 * </ul>
 * Memory only; {@link World} is the sector (the game, or a test's stand-in).
 */
public final class Places {
    private Places() {}

    /** The sector as far as quests need it. */
    public interface World {
        /** An entity, market or system with this id exists. */
        boolean place(String id);
        /** A person with this id exists (important people). */
        boolean person(String id);
        /** The faction that owns this market (an entity or market id); null when it isn't a market (or is gone). */
        String owner(String id);
        /**
         * SagaLib 1.3 (task #70): how faction A stands with faction B, as a RepLevel name
         * ("NEUTRAL"); null when a faction doesn't exist (core.Presence, SF_FactionRep).
         */
        default String relation(String a, String b) { return null; }
    }

    private static World world;

    /** The game sets this when a game loads (game.Saga); tests set their stand-in. Null = everything exists, nothing is watched. */
    public static void setup(World w) { world = w; }

    public static World world() { return world; }

    private static final Pattern ID_LINE = Pattern.compile("^\\s*\\$id\\s*==\\s*([\\w-]+)\\s*$");

    private static boolean fixed(String id) { return id != null && !id.isEmpty() && !id.startsWith("$"); }

    /** The people a quest needs (its contact). */
    public static List<String> people(QuestDef q) {
        TreeSet<String> out = new TreeSet<String>();
        if (("contact".equals(q.start.kind) || "bar".equals(q.start.kind)) && fixed(q.start.person)) out.add(q.start.person);
        return new ArrayList<String>(out);
    }

    /** The places a quest needs: "$id ==" in its start conditions, stage markers, Arrive places (random targets are left out). */
    public static List<String> places(QuestDef q) {
        TreeSet<String> out = new TreeSet<String>();
        for (String l : q.start.conditions) {
            Matcher m = ID_LINE.matcher(l);
            if (m.matches()) out.add(m.group(1));
        }
        for (QuestDef.Stage s : q.stages) if (fixed(s.marker)) out.add(s.marker);
        for (String a : q.arrivals) if (fixed(a)) out.add(a);
        return new ArrayList<String>(out);
    }

    /**
     * The first person or place the quest needs that doesn't exist ("person baird", "place
     * jangala"), or null. {@code later} = objects that appear during the quest (not there yet).
     */
    public static String missing(QuestDef q, World w, Collection<String> later) {
        if (w == null || q == null) return null;
        for (String id : people(q)) if (!w.person(id)) return "person " + id;
        for (String id : places(q)) if ((later == null || !later.contains(id)) && !w.place(id)) return "place " + id;
        return null;
    }

    // ------------------------------------------------------------------ owners

    public static String ownersKey(String q) { return "$global.sagaforge_owners_" + q; }

    public static String changedKey(String q) { return "$global." + q + "_ownerChanged"; }

    public static String newOwnerKey(String q) { return "$global." + q + "_newOwner"; }

    /** A watched market id: a random target's key ("$global.<quest>_<var>") is read for the picked id. */
    private static String watched(Memory m, String ref) {
        if (ref == null || ref.isEmpty()) return null;
        if (!ref.startsWith("$")) return ref;
        Object v = m.get(ref);
        String s = v == null ? null : Values.str(v);
        return s == null || s.isEmpty() ? null : s;
    }

    private static List<String[]> parse(Object v) {
        List<String[]> out = new ArrayList<String[]>();
        String s = v == null ? "" : Values.str(v);
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(";")) {
            String[] kv = part.split("=", -1);
            if (kv.length == 2 && !kv[0].isEmpty()) out.add(kv);
        }
        return out;
    }

    private static String join(List<String[]> pairs) {
        StringBuilder b = new StringBuilder();
        for (String[] p : pairs) b.append(b.length() > 0 ? ";" : "").append(p[0]).append('=').append(p[1]);
        return b.toString();
    }

    private static boolean has(List<String[]> pairs, String id) {
        for (String[] p : pairs) if (p[0].equals(id)) return true;
        return false;
    }

    /** When the quest starts: the owner of each watched market, and of the giver's market (null = none). */
    public static void remember(Memory m, QuestDef q, String giverMarket) {
        World w = world;
        if (w == null || q == null || q.ownerChange == null) return;
        List<String[]> pairs = new ArrayList<String[]>();
        List<String> refs = new ArrayList<String>(q.ownerChange.markets);
        if (giverMarket != null) refs.add(giverMarket);
        for (String ref : refs) {
            String id = watched(m, ref);
            String o = id == null ? null : w.owner(id);
            if (id != null && o != null && !has(pairs, id)) pairs.add(new String[]{id, o});
        }
        m.set(ownersKey(q.id), join(pairs));
    }

    /** What changed, and what the quest does about it. */
    public static final class Change {
        public final String market, owner, action, stage, trigger;

        Change(String market, String owner, QuestDef.OwnerChange oc) {
            this.market = market;
            this.owner = owner;
            this.action = oc.action;
            this.stage = "stage".equals(oc.action) ? oc.stage : null;
            this.trigger = "stage".equals(oc.action) ? oc.trigger : null;
        }

        @Override
        public String toString() { return market + " -> " + owner + " (" + action + (stage != null ? " " + stage : "") + ")"; }
    }

    /**
     * Once a day, for an active quest: the first watched market whose owner differs from the
     * remembered one. Sets $global.&lt;quest&gt;_ownerChanged (the market) and _newOwner (the faction),
     * remembers the new owner, and changes the memory as the quest says: "fail" ends it failed,
     * "stage" sets the step (a success or fail stage ends it). The game glue updates the journal
     * and fires {@link Change#trigger}. Null = nothing changed. A market that is gone is left out.
     */
    public static Change check(Memory m, QuestDef q) {
        World w = world;
        if (w == null || q == null || q.ownerChange == null || !QuestLogic.isActive(m, q.id)) return null;
        List<String[]> pairs = parse(m.get(ownersKey(q.id)));
        // a market picked after the start (a random target) is remembered when it is first seen
        for (String ref : q.ownerChange.markets) {
            String id = watched(m, ref);
            String o = id == null ? null : w.owner(id);
            if (id != null && o != null && !has(pairs, id)) pairs.add(new String[]{id, o});
        }
        String[] hit = null;
        for (String[] p : pairs) {
            String now = w.owner(p[0]);
            if (now == null || now.equals(p[1])) continue;
            p[1] = now;
            if (hit == null) hit = new String[]{p[0], now};
        }
        m.set(ownersKey(q.id), join(pairs));
        if (hit == null) return null;
        m.set(changedKey(q.id), hit[0]);
        m.set(newOwnerKey(q.id), hit[1]);
        QuestDef.OwnerChange oc = q.ownerChange;
        if ("fail".equals(oc.action)) QuestLogic.end(m, q.id, false);
        else if ("stage".equals(oc.action) && oc.stage != null && !oc.stage.isEmpty()) QuestLogic.step(m, q, q.id, oc.stage);
        return new Change(hit[0], hit[1], oc);
    }

    /** The markets remembered for a quest (dev menu): market id → owner. */
    public static List<String[]> remembered(Memory m, String q) {
        return Collections.unmodifiableList(parse(m.get(ownersKey(q))));
    }
}
