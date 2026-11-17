package lastai.sagalib.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SagaLib 1.2: random targets (docs/FORMAT.md "Random targets"). Pure part: the known picks and
 * criteria (the same ids as Saga Forge's shared/targets.ts and test/golden/target-criteria.json),
 * the memory keys, and references to a picked target. The search itself is vanilla's
 * HubMissionWithSearch, run by quest/TargetSearch in the game.
 * <p>
 * Memory (all permanent, written when the quest is offered):
 * <pre>
 *   $global.&lt;quest&gt;_&lt;var&gt;          the picked id (system, planet/entity, or market id)
 *   $global.&lt;quest&gt;_&lt;var&gt;_name     its name          (also usable as a text token)
 *   $global.&lt;quest&gt;_&lt;var&gt;_system   its star system's name ("" in hyperspace)
 *   $global.&lt;quest&gt;_&lt;var&gt;_dist     light-years from the quest giver, one decimal
 *   $global.&lt;quest&gt;_&lt;var&gt;_faction  its faction's name ("" for none); _factionId its id
 * </pre>
 */
public final class Targets {
    private Targets() {}

    public static final List<String> PICKS = Collections.unmodifiableList(Arrays.asList("system", "planet", "entity", "market"));

    public static final Set<String> CRITERIA = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
            "sysTags", "sysInteresting", "sysUnexplored", "sysExplored", "sysNotEnteredFor", "sysInner", "sysFringe",
            "sysWithin", "sysOutside", "sysBlackHole", "sysPulsar", "sysNebula", "sysNotBlackHole", "sysNotPulsar",
            "sysNotNebula", "sysSafeStars", "sysNotUsedForStory", "sysHasColony", "sysHasBase", "sysJumpPoints", "sysPlanets",
            "plGasGiant", "plNotGasGiant", "plPopulated", "plUnpopulated", "plTags", "plConditions", "plUnsurveyed",
            "plNotFullySurveyed", "plFullySurveyed", "plRuins", "plNoRuins", "plUnexploredRuins", "plNotNearJump",
            "enType", "enTags", "enFlags", "enUndiscovered",
            "mkFaction", "mkFactionNot", "mkNotPlayer", "mkHostileTo", "mkNotHostileTo", "mkFactionCustom", "mkSizeAtLeast",
            "mkSizeAtMost", "mkStabilityAtLeast", "mkStabilityAtMost", "mkMilitary", "mkNotMilitary", "mkSpaceport",
            "mkNotSpaceport", "mkHidden", "mkNotHidden", "mkConditions", "mkIndustries")));

    /** Criteria vanilla only has as "require" (no prefer variant): used as prefer, they are skipped with a warning. */
    public static final Set<String> REQUIRE_ONLY = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
            "sysSafeStars", "sysNotUsedForStory", "mkNotPlayer")));

    public static final String[] FACTS = { "name", "system", "dist", "faction", "factionId" };

    /**
     * SagaLib 1.2 (RC-R3): what the pick is like, for Branch conditions and phrases. Booleans are
     * true/false (Boolean); ruins is "" or scattered/widespread/extensive/vast; theme is the system's
     * theme_* tags, comma-separated (for texts); ruinsTheme … miscTheme test the theme families.
     */
    public static final String[] DETAILS = { "deciv", "hasRuins", "ruins", "unexploredRuins", "populated", "gasGiant", "theme",
            "ruinsTheme", "derelictTheme", "remnantTheme", "miscTheme", "blackHole", "pulsar", "nebula", "hostile", "unexplored" };

    /** A system theme family ("ruins" for theme_ruins, theme_ruins_main, …): true when any tag has it. */
    public static boolean hasTheme(java.util.Collection<String> tags, String family) {
        for (String t : tags) if (t.equals("theme_" + family) || t.startsWith("theme_" + family + "_")) return true;
        return false;
    }

    /** RC-R4: the person at the target ("_person" = their id, "_personName"); RC-R2: what SF_SpawnAtTarget placed ("_spawned"). */
    public static final String PERSON = "person", PERSON_NAME = "personName", SPAWNED = "spawned";

    /** Ruins size from a ruins_* condition id ("" for none). */
    public static String ruinsSize(String condition) {
        return condition == null || !condition.startsWith("ruins_") ? "" : condition.substring("ruins_".length());
    }

    /** The system theme tags (theme_*), comma-separated. */
    public static String themes(java.util.Collection<String> tags) {
        StringBuilder b = new StringBuilder();
        for (String t : tags) if (t.startsWith("theme_")) { if (b.length() > 0) b.append(","); b.append(t); }
        return b.toString();
    }

    public static String key(String quest, String var) { return "$global." + quest + "_" + var; }

    public static String factKey(String quest, String var, String fact) { return key(quest, var) + "_" + fact; }

    /** A marker, arrival or flag entity that names a target ("$global.&lt;quest&gt;_&lt;var&gt;") resolves to the picked id. */
    public static String resolve(Memory mem, String ref) {
        if (ref == null || !ref.startsWith("$global.")) return ref;
        Object v = mem.get(ref);
        return v == null || String.valueOf(v).isEmpty() ? null : String.valueOf(v);
    }

    /** SF_IsTarget: the dialog's place is the target when the picked id is its entity, its market or its system. */
    public static boolean matches(String picked, String entityId, String marketId, String systemId) {
        if (picked == null || picked.isEmpty()) return false;
        return picked.equals(entityId) || picked.equals(marketId) || picked.equals(systemId);
    }

    /** Light-years with one decimal, as the "_dist" fact. */
    public static String formatDist(float ly) {
        return String.valueOf(Math.round(ly * 10f) / 10f);
    }
}
