package lastai.sagalib.core;

import lastai.sagalib.model.QuestDef;

import java.util.Map;

/**
 * SagaLib 1.4 (docs/FORMAT.md, "Fights"): the pure part of SF_Fight &lt;quest&gt; &lt;node&gt;, a battle on
 * the spot like vanilla's salvage defenders (rulecmd.salvage.SalvageDefenderInteraction). The game
 * part is game.FightRuntime.
 */
public final class Fights {
    private Fights() {}

    /** Vanilla's text for the first engage option (SalvageDefenderInteraction). */
    public static final String DEFAULT_ENGAGE = "Engage the automated defenses";
    public static final String RE_ENGAGE = "Re-engage";
    /** Days a damaged fleet the player left stays (vanilla's $defenderFleet: memory.expire(key, 10)). */
    public static final float KEEP_DAYS = 10f;

    /** The interaction target's memory key holding a damaged fleet left behind (not $global: the place's own memory). */
    public static String fleetKey(String quest, String node) { return "$sagaforge_fight_" + quest + "_" + node; }

    /** The fight of a quest node, or null when the quest or node isn't known (the caller logs). */
    public static QuestDef.Fight find(Map<String, QuestDef> quests, String quest, String node) {
        QuestDef q = quest == null ? null : quests.get(quest);
        return q == null || node == null ? null : q.fight(node);
    }

    /** Why SF_Fight can't run, or null when it can. */
    public static String problem(Map<String, QuestDef> quests, String quest, String node) {
        QuestDef q = quest == null ? null : quests.get(quest);
        if (q == null) return "unknown quest \"" + quest + "\"";
        if (q.fight(node) == null) return "quest " + quest + " has no fight at node \"" + node + "\" (its JSON's \"fights\")";
        return null;
    }

    public static String engageText(QuestDef.Fight f) { return f.engage == null || f.engage.isEmpty() ? DEFAULT_ENGAGE : f.engage; }

    /** What happens when the fight's dialog is left. */
    public enum After {
        /** The player won outright: forget the fleet, fire {@code won}. */
        WON,
        /** Left or lost after fighting, the fleet damaged: keep it for {@link #KEEP_DAYS}, fire {@code left}. */
        KEEP,
        /** Left without a scratch on it: a fresh one next time, fire {@code left}. */
        LEFT
    }

    /** Vanilla's test (persistDefenders): engaged, and the fleet lost ships or has one needing repairs. */
    public static After after(boolean wonOutright, boolean engaged, boolean lostShips, boolean needsRepairs) {
        if (wonOutright) return After.WON;
        return engaged && (lostShips || needsRepairs) ? After.KEEP : After.LEFT;
    }
}
