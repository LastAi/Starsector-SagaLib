package lastai.sagalib.core;

import lastai.sagalib.SagaLog;
import lastai.sagalib.model.PersonDef;
import lastai.sagalib.model.QuestDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * SagaLib 1.3 (task #70, docs/FORMAT.md "Presence"): a person is present at their market only while
 * the market's owner is at least some level with a faction they're loyal to. Memory only; the
 * sector is {@link Places.World} (market owners and faction relations). The same as Saga Forge's
 * Play mode (tools/saga-forge/src/shared/play.ts: checkPresence, factionRep, awayContact); golden
 * case test/golden/presence.json.
 * <ul>
 * <li>{@code $global.sagaforge_away_<person>} is true while they're away, false while present
 *     (set by the daily check and on load; unset until the first check).</li>
 * <li>A quest whose contact is away is not offered ({@link #awayPerson}).</li>
 * <li>{@code SF_FactionRep <factionA> <factionB> <level>}: A is at least that level with B.</li>
 * </ul>
 */
public final class Presence {
    private Presence() {}

    /** The game's RepLevel names, worst first (RepLevel's order). */
    public static final List<String> LEVELS = Arrays.asList("VENGEFUL", "HOSTILE", "INHOSPITABLE", "SUSPICIOUS", "NEUTRAL",
            "FAVORABLE", "WELCOMING", "FRIENDLY", "COOPERATIVE");
    /** "Not hostile": RepLevel.isAtBest(HOSTILE) is FactionAPI.isHostileTo, so the next one up. */
    public static final String NOT_HOSTILE = "notHostile";

    public static String awayKey(String person) { return "$global.sagaforge_away_" + person; }

    /** The level an "atLeast" stands for (notHostile = INHOSPITABLE); null if it isn't one. */
    public static String level(String atLeast) {
        if (atLeast == null) return null;
        if (NOT_HOSTILE.equalsIgnoreCase(atLeast)) return "INHOSPITABLE";
        String u = atLeast.toUpperCase(java.util.Locale.ROOT);
        return LEVELS.contains(u) ? u : null;
    }

    /** True if the level {@code is} is {@code atLeast} or better; null when either is unknown. */
    public static Boolean atLeast(String is, String atLeast) {
        String min = level(atLeast);
        if (is == null || min == null || !LEVELS.contains(is)) return null;
        return LEVELS.indexOf(is) >= LEVELS.indexOf(min);
    }

    /** How faction A stands with B, as a level: a faction with itself is COOPERATIVE; null when unknown. */
    public static String relation(Places.World w, String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return null;
        if (a.equals(b)) return "COOPERATIVE";
        return w == null ? null : w.relation(a, b);
    }

    /** A is at least {@code atLeast} with B; null when a faction or the relation is unknown. */
    public static Boolean factionRep(Places.World w, String a, String b, String atLeast) {
        return atLeast(relation(w, a, b), atLeast);
    }

    /** SF_FactionRep as a condition ($ arguments read from memory); null for other names. */
    public static Boolean condition(Memory m, String name, List<String> args) {
        if (!"SF_FactionRep".equals(name)) return null;
        if (args.size() < 3) {
            SagaLog.warnOnce("factionrep-args:" + args, "SF_FactionRep needs <factionA> <factionB> <level>, got " + args + "; counted as false");
            return false;
        }
        String a = arg(m, args.get(0)), b = arg(m, args.get(1));
        if (level(args.get(2)) == null) {
            SagaLog.warnOnce("factionrep-level:" + args.get(2), "SF_FactionRep: unknown level \"" + args.get(2) + "\" (" + LEVELS + " or notHostile); counted as false");
            return false;
        }
        Boolean r = factionRep(Places.world(), a, b, args.get(2));
        if (r == null) {
            SagaLog.warnOnce("factionrep-unknown:" + a + ":" + b, "SF_FactionRep " + a + " " + b + ": a faction doesn't exist in this game; counted as false");
            return false;
        }
        return r;
    }

    private static String arg(Memory m, String s) {
        if (s == null || !s.startsWith("$") || m == null) return s;
        Object v = m.get(s);
        return v == null ? null : Values.str(v);
    }

    // ------------------------------------------------------------------ presence

    /** Whether the person should be away now; null when it can't be told (no presence, market gone, relation unknown). */
    public static Boolean shouldBeAway(PersonDef d, Places.World w) {
        if (d == null || d.presence == null || w == null || !"market".equals(d.placementAt) || d.market == null) return null;
        String owner = w.owner(d.market);
        if (owner == null) return null;
        Boolean ok = factionRep(w, owner, d.presence.faction, d.presence.atLeast);
        return ok == null ? null : !ok;
    }

    /** A person leaving or coming back. */
    public static final class Move {
        public final String person, owner;
        public final boolean away;
        /** False when the flag was unset before (the first check of this save). */
        public final boolean changed;

        Move(String person, String owner, boolean away, boolean changed) {
            this.person = person;
            this.owner = owner;
            this.away = away;
            this.changed = changed;
        }

        @Override
        public String toString() { return person + ":" + (away ? "leaves" : "back") + ":" + owner; }
    }

    /**
     * The daily check (and on load): sets every presence person's away flag (true/false) and
     * returns those whose flag changed, or that are away at the first check. A person whose market
     * is gone, or whose relation can't be told, keeps their flag.
     */
    public static List<Move> check(Memory m, Collection<PersonDef> people, Places.World w) {
        List<Move> out = new ArrayList<Move>();
        for (PersonDef d : people) {
            Boolean away = shouldBeAway(d, w);
            if (away == null) continue;
            Object was = m.get(awayKey(d.id));
            boolean before = Values.truthy(was);
            m.set(awayKey(d.id), Boolean.valueOf(away));
            if (was == null ? away : before != away) out.add(new Move(d.id, w.owner(d.market), away, was != null));
        }
        return out;
    }

    public static boolean isAway(Memory m, String person) { return Values.truthy(m.get(awayKey(person))); }

    /** The first person a quest needs (its contact) who is away now, or null. */
    public static String awayPerson(Memory m, QuestDef q) {
        if (q == null) return null;
        for (String id : Places.people(q)) if (isAway(m, id)) return id;
        return null;
    }
}
