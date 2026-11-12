package lastai.sagalib.core;

import lastai.sagalib.model.EncounterDef;
import lastai.sagalib.model.QuestDef;

import java.util.Map;

/**
 * Encounter bookkeeping (docs/FORMAT.md, "Encounters"), in memory keys only so saves never hold
 * a SagaLib object for it:
 * <pre>
 *   $global.sagaforge_enc_&lt;id&gt;_state   spawned | defeated | avoided (unset = never spawned, or reset)
 *   $global.sagaforge_enc_&lt;id&gt;_quest   the quest it was spawned for (if any)
 *   $global.sagaforge_enc_&lt;id&gt;_stage   that quest's step when it spawned
 *   $global.sagaforge_enc_&lt;id&gt;_gone    the day it was beaten or left (periodic respawns)
 *   $global.sagaforge_enc_&lt;id&gt;_once    true after a new-game encounter spawned
 * </pre>
 * The fleet itself is found by its memory key $sagaforge_encounter = &lt;id&gt;.
 */
public final class EncounterLogic {
    private EncounterLogic() {}

    public static final String SPAWNED = "spawned";
    public static final String DEFEATED = "defeated";
    public static final String AVOIDED = "avoided";

    public enum Action { NONE, SPAWN, DESPAWN }

    static String base(String id) { return "$global.sagaforge_enc_" + id; }

    public static String stateKey(String id) { return base(id) + "_state"; }

    public static String questKey(String id) { return base(id) + "_quest"; }

    public static String stageKey(String id) { return base(id) + "_stage"; }

    public static String goneKey(String id) { return base(id) + "_gone"; }

    public static String onceKey(String id) { return base(id) + "_once"; }

    public static String state(Memory m, String id) {
        Object s = m.get(stateKey(id));
        return s == null ? null : Values.str(s);
    }

    public static boolean isSpawned(Memory m, String id) { return SPAWNED.equals(state(m, id)); }

    /**
     * What the manager script should do now. quest-stage encounters with spawn.quest/stage spawn when
     * the quest is at that stage (once per visit: leaving the stage resets a beaten/avoided one);
     * new-game ones spawn once; periodic ones respawn {@code days} after they were gone. A spawned
     * encounter with despawnOnStageEnd leaves when its quest is no longer at the stage it spawned at.
     */
    public static Action decide(EncounterDef e, Memory m, double now) {
        return decide(e, m, now, null);
    }

    /**
     * As above, knowing the quests: a fleet spawned at a stage that was renamed since (the save
     * moved from an old id in renamedFrom to the new one) is still at its stage, and its record is
     * moved to the new id (found in game, 2026-11-11: the ambush left on load after search → find).
     */
    public static Action decide(EncounterDef e, Memory m, double now, Map<String, QuestDef> quests) {
        String state = state(m, e.id);
        if (SPAWNED.equals(state)) {
            if (e.despawnOnStageEnd) {
                Object q = m.get(questKey(e.id)), st = m.get(stageKey(e.id));
                if (q != null && st != null) {
                    String qid = Values.str(q);
                    String step = QuestLogic.step(m, qid);
                    String was = Values.str(st);
                    boolean active = QuestLogic.isActive(m, qid);
                    if (active && step != null && !was.equals(step) && renamed(quests, qid, was, step)) {
                        m.set(stageKey(e.id), step);
                        was = step;
                    }
                    boolean still = active && was.equals(step);
                    if (!still) return Action.DESPAWN;
                }
            }
            return Action.NONE;
        }
        if ("quest-stage".equals(e.when)) {
            if (e.quest == null) return Action.NONE; // only SF_Spawn spawns it
            boolean atStage = QuestLogic.isActive(m, e.quest) && e.stage.equals(QuestLogic.step(m, e.quest));
            if (!atStage) {
                if (state != null) m.unset(stateKey(e.id)); // a later visit to the stage spawns it again
                return Action.NONE;
            }
            return state == null ? Action.SPAWN : Action.NONE;
        }
        if ("new-game".equals(e.when)) return m.get(onceKey(e.id)) == null ? Action.SPAWN : Action.NONE;
        if ("periodic".equals(e.when)) {
            Double gone = Values.number(m.get(goneKey(e.id)));
            return gone == null || now - gone >= e.days ? Action.SPAWN : Action.NONE;
        }
        return Action.NONE;
    }

    /** True if stage {@code now} of that quest lists {@code old} in its renamedFrom. */
    static boolean renamed(Map<String, QuestDef> quests, String quest, String old, String now) {
        if (quests == null) return false;
        QuestDef q = quests.get(quest);
        QuestDef.Stage s = q == null ? null : q.stage(now);
        return s != null && s.renamedFrom.contains(old);
    }

    /** The quest an encounter belongs to: spawn.quest, else the first quest listing it in "encounters". */
    public static QuestDef questFor(EncounterDef e, Map<String, QuestDef> quests) {
        if (e.quest != null && quests.containsKey(e.quest)) return quests.get(e.quest);
        for (QuestDef q : quests.values()) {
            for (QuestDef.EncounterUse u : q.encounters) if (e.id.equals(u.encounter)) return q;
        }
        return null;
    }

    /** Records a spawn, with the quest and step it belongs to (both may be null). */
    public static void spawned(Memory m, EncounterDef e, String quest, String stage) {
        m.set(stateKey(e.id), SPAWNED);
        if (quest != null && stage != null) {
            m.set(questKey(e.id), quest);
            m.set(stageKey(e.id), stage);
        } else {
            m.unset(questKey(e.id));
            m.unset(stageKey(e.id));
        }
        m.set(onceKey(e.id), Boolean.TRUE);
    }

    /** A spawn that couldn't happen (logged by the game side): counted as gone, with no outcome. */
    public static void failed(Memory m, EncounterDef e, double now) {
        m.set(stateKey(e.id), AVOIDED);
        m.set(goneKey(e.id), Float.valueOf((float) now));
        m.set(onceKey(e.id), Boolean.TRUE);
    }

    /**
     * The encounter ended: beaten in battle ({@code defeated}) or gone without being beaten.
     * Returns the trigger to fire (outcomes in the encounter file first, then the owning quest's
     * "encounters" entry), or null. Only a spawned encounter resolves, so an outcome fires once.
     */
    public static String resolve(Memory m, EncounterDef e, Map<String, QuestDef> quests, boolean defeated, double now) {
        if (!isSpawned(m, e.id)) return null;
        m.set(stateKey(e.id), defeated ? DEFEATED : AVOIDED);
        m.set(goneKey(e.id), Float.valueOf((float) now));
        String trigger = defeated ? e.defeated : e.avoided;
        if (trigger != null) return trigger;
        Object recorded = m.get(questKey(e.id));
        QuestDef q = recorded != null ? quests.get(Values.str(recorded)) : null;
        if (q == null) q = questFor(e, quests);
        if (q == null) return null;
        for (QuestDef.EncounterUse u : q.encounters) {
            if (!e.id.equals(u.encounter)) continue;
            String t = defeated ? u.defeated : u.avoided;
            return t == null || t.isEmpty() ? null : t;
        }
        return null;
    }
}
