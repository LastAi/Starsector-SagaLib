package lastai.sagalib.core;

import lastai.sagalib.SagaLog;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * SagaLib 1.4, task #77: AoTD Vaults of Knowledge research from quests (docs/FORMAT.md, "Vaults of
 * Knowledge research"). VoK keeps research in a Java object (its research manager in the sector's
 * persistent data), not in memory, so granting a tech and asking whether one is researched go through
 * a {@link Source}: the guarded bridge in game/VokResearch, a fake in the tests. Without VoK (or with
 * a SagaLib built without its bridge) granting does nothing and logs why once, and "has researched"
 * is false. Play mode does the same (tools/saga-forge/src/shared/play.ts).
 */
public final class Research {
    private Research() {}

    public static final String VOK = "aotd_vok";

    /** What only Vaults of Knowledge knows. */
    public interface Source {
        /** VoK has the tech (a row of an enabled mod's aotd_tech_options.csv). */
        boolean known(String techId);

        /**
         * The faction has researched it; null = the player, as VoK counts it (the player's faction or the
         * faction that commissions the player).
         */
        boolean researched(String techId, String factionId);

        /**
         * Marks it researched for the player's own faction (VoK's own "research_tech"); returns its name,
         * or null when the player's faction had it already (nothing changes).
         */
        String grant(String techId);
    }

    /** The result of {@link #grant}. */
    public static final class Grant {
        /** Researched now (false: already researched, or not possible). */
        public final boolean done;
        /** The tech's name when done, else why not (null when it was already researched). */
        public final String nameOrWhy;

        Grant(boolean done, String nameOrWhy) { this.done = done; this.nameOrWhy = nameOrWhy; }
    }

    private static volatile Source source;
    private static volatile String whyNot = "Vaults of Knowledge isn't enabled";
    private static final Set<String> LOGGED = Collections.synchronizedSet(new HashSet<String>());

    /** The game (or a test) says what VoK knows; null = no VoK, with why (for the log and the dev menu). */
    public static void setup(Source s, String why) {
        source = s;
        whyNot = s != null ? "" : why == null || why.isEmpty() ? "Vaults of Knowledge isn't enabled" : why;
    }

    /** "" when VoK's bridge is ready, else why not. */
    public static String status() { return whyNot; }

    /** SF_GrantResearch: the player's faction gets the tech as researched. Failures are logged once per tech and reason. */
    public static Grant grant(String techId) {
        Source s = source;
        if (techId == null || techId.trim().isEmpty()) return fail(techId, "no tech id");
        if (s == null) return fail(techId, whyNot + ": nothing is granted");
        try {
            if (!s.known(techId)) return fail(techId, "Vaults of Knowledge has no tech \"" + techId + "\" (not in an enabled mod's data/campaign/aotd_tech_options.csv)");
            String name = s.grant(techId);
            return name == null ? new Grant(false, null) : new Grant(true, name.isEmpty() ? techId : name);
        } catch (Throwable t) {
            SagaLog.errorOnce("vok-grant-" + techId, "Vaults of Knowledge bridge: granting " + techId + " failed", t);
            return new Grant(false, "Vaults of Knowledge failed (see starsector.log)");
        }
    }

    /** SF_HasResearch: false without VoK, for a tech VoK doesn't have, or on any error. */
    public static boolean has(String techId, String factionId) {
        Source s = source;
        if (s == null || techId == null || techId.isEmpty()) return false;
        try {
            return s.known(techId) && s.researched(techId, factionId == null || factionId.isEmpty() || "player".equals(factionId) ? null : factionId);
        } catch (Throwable t) {
            SagaLog.errorOnce("vok-has-" + techId, "Vaults of Knowledge bridge: asking about " + techId + " failed", t);
            return false;
        }
    }

    private static Grant fail(String techId, String why) {
        if (LOGGED.add(techId + "|" + why)) SagaLog.info("SF_GrantResearch " + techId + ": " + why);
        return new Grant(false, why);
    }

    /** Tests: how many distinct failures were logged. */
    public static int loggedCount() { return LOGGED.size(); }

    /** Tests: forget what was logged. */
    public static void resetLog() { LOGGED.clear(); }
}
