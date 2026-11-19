package lastai.sagalib.core;

import lastai.sagalib.SagaLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * SagaLib 1.3, task #59: MagicLib bounties from quests (docs/FORMAT.md, "MagicLib bounties"). The
 * state of a bounty comes from the memory keys MagicLib itself sets (Saga Forge FORMATS.md,
 * "MagicLib 1.5.6 bounties"): its key ("$" + id unless the bounty's job_memKey says otherwise) is
 * false once accepted and true once it ended, with "_succeeded", "_failed" or "_expired" also true.
 * What only MagicLib knows (whether it has the bounty, and accepting it) comes through a
 * {@link Source}: the guarded bridge in game/MagicBounties, a fake in the tests. Without MagicLib
 * every bounty is "unavailable" and starting one does nothing. Play mode does the same
 * (tools/saga-forge/src/shared/play.ts); golden case test/golden/bounties.json.
 */
public final class Bounties {
    private Bounties() {}

    public static final String MAGICLIB = "MagicLib";

    public static final String UNAVAILABLE = "unavailable";
    public static final String OFFERED = "offered";
    public static final String ACTIVE = "active";
    public static final String SUCCEEDED = "succeeded";
    public static final String FAILED = "failed";
    public static final String EXPIRED = "expired";
    public static final String DISMISSED = "dismissed";
    /** Not a state: any of the four ends. */
    public static final String ENDED = "ended";

    public static final List<String> STATES = Collections.unmodifiableList(Arrays.asList(UNAVAILABLE, OFFERED, ACTIVE, SUCCEEDED, FAILED, EXPIRED, DISMISSED));
    private static final List<String> ENDS = Arrays.asList(SUCCEEDED, FAILED, EXPIRED, DISMISSED);

    /** What only MagicLib knows. */
    public interface Source {
        /** MagicLib is enabled and SagaLib was built with its bridge. */
        boolean ready();

        /** MagicLib has this bounty (read from a mod's file and valid, or already created), taken or not. */
        boolean known(String id);

        /** The bounty's memory key as MagicLib sets it, e.g. "$tsa_b_raiders" (null = "$" + id). */
        String memKey(String id);
    }

    private static final Source NONE = new Source() {
        public boolean ready() { return false; }
        public boolean known(String id) { return false; }
        public String memKey(String id) { return null; }
    };

    private static volatile Source source = NONE;

    /** The game (or a test) says what MagicLib knows; null = no MagicLib. */
    public static void setup(Source s) { source = s == null ? NONE : s; }

    public static Source source() { return source; }

    /** The bounty's key in rules.csv notation: MagicLib's sector-memory "$x" is "$global.x". */
    public static String globalKey(String id) {
        String k = source.memKey(id);
        if (k == null || k.trim().isEmpty()) k = id;
        k = k.trim();
        if (k.startsWith("$global.")) return k;
        return "$global." + (k.startsWith("$") ? k.substring(1) : k);
    }

    /**
     * Why the bounty can't be had in this game, or null: MagicLib doesn't count for the content mod
     * (not enabled, or not enabled when the game started: core.Mods), SagaLib was built without the
     * bridge, or MagicLib doesn't have the bounty.
     */
    public static String unavailableBecause(Memory m, String owner, String id) {
        if (!Mods.enabledNow(MAGICLIB)) return "MagicLib isn't enabled";
        if (!Mods.counts(m, owner, MAGICLIB)) return "MagicLib wasn't enabled when this game started";
        if (!source.ready()) return "this SagaLib was built without its MagicLib bridge";
        if (m.get(globalKey(id)) == null && !source.known(id)) return "MagicLib has no bounty \"" + id + "\" (not in any enabled mod's magicBounty_data.json, invalid, or its required mods are missing)";
        return null;
    }

    /** unavailable, offered, active, succeeded, failed, expired or dismissed. */
    public static String state(Memory m, String owner, String id) {
        if (!Mods.counts(m, owner, MAGICLIB) || !source.ready()) return UNAVAILABLE;
        String key = globalKey(id);
        Object v = m.get(key);
        if (v == null) return source.known(id) ? OFFERED : UNAVAILABLE;
        if (!Values.truthy(v)) return ACTIVE;
        if (Values.truthy(m.get(key + "_succeeded"))) return SUCCEEDED;
        if (Values.truthy(m.get(key + "_failed"))) return FAILED;
        if (Values.truthy(m.get(key + "_expired"))) return EXPIRED;
        return DISMISSED;
    }

    /** Whether {@code state} is one of {@code wanted} ("ended" = succeeded, failed, expired or dismissed). */
    public static boolean matches(String state, List<String> wanted) {
        for (String w : wanted) {
            if (w.equals(state)) return true;
            if (ENDED.equals(w) && ENDS.contains(state)) return true;
        }
        return false;
    }

    /** The state words a condition may use. */
    public static boolean isStateWord(String w) { return STATES.contains(w) || ENDED.equals(w); }

    /** SF_BountyState &lt;bounty&gt; &lt;state&gt; [more states]; null for other names. */
    public static Boolean condition(Memory m, String owner, String name, List<String> args) {
        if (!"SF_BountyState".equals(name)) return null;
        if (args.size() < 2) {
            SagaLog.warnOnce("bounty-args:" + args, "SF_BountyState needs <bounty> <state> [more states], got " + args);
            return false;
        }
        List<String> wanted = new ArrayList<String>(args.subList(1, args.size()));
        for (String w : wanted) {
            if (!isStateWord(w)) SagaLog.warnOnce("bounty-state:" + w, "SF_BountyState " + args.get(0) + ": \"" + w + "\" isn't a state (" + STATES + " or " + ENDED + ")");
        }
        return matches(state(m, owner, args.get(0)), wanted);
    }

    /**
     * Whether SF_StartBounty may accept the bounty now: null = yes (it is offered), else why not
     * (unavailable, or already taken or ended: starting is once, like accepting on the board).
     */
    public static String cantStart(Memory m, String owner, String id) {
        String why = unavailableBecause(m, owner, id);
        if (why != null) return why;
        String s = state(m, owner, id);
        return OFFERED.equals(s) ? null : "it is " + s + " already";
    }
}
