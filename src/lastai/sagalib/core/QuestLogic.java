package lastai.sagalib.core;

import lastai.sagalib.SagaLog;
import lastai.sagalib.model.QuestDef;

import java.util.List;

/**
 * Quest progress in memory keys (docs/FORMAT.md, "Memory keys"), the same as Saga Forge's Play
 * mode (tools/saga-forge/src/shared/play.ts: startQuest, SF_Step, SF_End, applyReward):
 * <pre>
 *   $global.&lt;quest&gt;_state   active | success | fail (unset = not started)
 *   $global.&lt;quest&gt;_step    the current stage id
 *   $global.&lt;quest&gt;_&lt;var&gt;   quest variables
 * </pre>
 * Memory only: the journal entry (SagaQuest) is updated by the game glue around these calls.
 */
public final class QuestLogic {
    private QuestLogic() {}

    public static final String NOT_STARTED = "notStarted";
    public static final String ACTIVE = "active";
    public static final String SUCCESS = "success";
    public static final String FAIL = "fail";
    /** The step of an active quest whose saved step no longer exists (PLAN.md §6). */
    public static final String CHANGED_STEP = "__changed";

    public static String stepKey(String q) { return "$global." + q + "_step"; }

    public static String stateKey(String q) { return "$global." + q + "_state"; }

    public static String varKey(String q, String v) { return "$global." + q + "_" + v; }

    /**
     * SagaLib 1.3 (task #59): true once the quest has ended that way (it stays true when a
     * repeatable quest runs again). A true/false key, for what can't test the state string:
     * MagicLib's bounty triggers (trigger_memKeys_*) read Booleans in the sector's memory only.
     */
    public static String endedKey(String q, boolean success) { return "$global.sagaforge_" + (success ? "success" : "fail") + "_" + q; }

    /** notStarted, active, success or fail. */
    public static String state(Memory m, String q) {
        Object s = m.get(stateKey(q));
        if (ACTIVE.equals(s) || SUCCESS.equals(s) || FAIL.equals(s)) return (String) s;
        return NOT_STARTED;
    }

    public static boolean isActive(Memory m, String q) { return ACTIVE.equals(state(m, q)); }

    /** The current step, or null. */
    public static String step(Memory m, String q) {
        Object s = m.get(stepKey(q));
        return s == null ? null : Values.str(s);
    }

    /** A variable's initial value: number (Float, 0 if unreadable), bool, else the text. */
    public static Object parseInitial(String type, String s) {
        if ("number".equals(type)) {
            Double d = Values.number(s);
            return Float.valueOf(d == null ? 0f : d.floatValue());
        }
        if ("bool".equals(type)) return Boolean.valueOf("true".equals(s));
        return s == null ? "" : s;
    }

    /** Sets every variable that is unset to its initial value. */
    public static void initVariables(Memory m, QuestDef def) {
        if (def == null) return;
        for (QuestDef.Variable v : def.variables) {
            if ("target".equals(v.type)) continue;
            String k = varKey(def.id, v.name);
            if (m.get(k) == null) m.set(k, parseInitial(v.type, v.initial));
        }
    }

    /** Starts a quest: state active, variables at their initial values. False if it was already active. */
    public static boolean start(Memory m, QuestDef def, String q) {
        if (isActive(m, q)) return false;
        m.set(stateKey(q), ACTIVE);
        initVariables(m, def);
        // SagaLib 1.3 (task #61): the owners of its markets, compared once a day
        Places.remember(m, def, null);
        return true;
    }

    /**
     * Moves a quest to a step, starting it first if needed. A stage of kind success/fail also ends
     * the quest that way. Returns that kind ("normal" if the stage isn't in the definition).
     */
    public static String step(Memory m, QuestDef def, String q, String step) {
        start(m, def, q);
        m.set(stepKey(q), step);
        QuestDef.Stage st = def == null ? null : def.stage(step);
        if (st != null && (SUCCESS.equals(st.kind) || FAIL.equals(st.kind))) {
            end(m, q, SUCCESS.equals(st.kind));
            return st.kind;
        }
        return "normal";
    }

    /** Ends a quest: state success or fail, and its true/false ended key ({@link #endedKey}). */
    public static void end(Memory m, String q, boolean success) {
        m.set(stateKey(q), success ? SUCCESS : FAIL);
        m.set(endedKey(q, success), Boolean.TRUE);
    }

    /**
     * The player abandoned the quest's journal entry: back to not started (it can be offered
     * again, like a vanilla mission), and its variables are cleared so a new start begins fresh.
     */
    public static void abandon(Memory m, QuestDef def, String q) {
        m.unset(stateKey(q));
        m.unset(stepKey(q));
        if (def != null) for (QuestDef.Variable v : def.variables) m.unset(varKey(q, v.name));
    }

    /** SagaLib's condition commands; null if {@code name} isn't one of them. */
    public static Boolean condition(Memory m, String name, List<String> args) {
        String q = args.isEmpty() ? "" : args.get(0);
        if ("SF_AtStep".equals(name)) {
            String want = args.size() > 1 ? args.get(1) : null;
            String now = step(m, q);
            return isActive(m, q) && (want == null ? now == null : want.equals(now));
        }
        if ("SF_QuestDone".equals(name)) {
            String s = state(m, q);
            String how = args.size() > 1 ? args.get(1) : null;
            if (how != null && !"any".equals(how)) return s.equals(how);
            return SUCCESS.equals(s) || FAIL.equals(s);
        }
        if ("SF_QuestState".equals(name)) {
            String want = args.size() > 1 ? args.get(1) : "";
            if ("not-started".equals(want)) want = NOT_STARTED; // the editor's spelling
            return state(m, q).equals(want);
        }
        return null;
    }

    /** Gives one reward of a quest. False (and a warning) if the quest has no such reward. */
    public static boolean reward(QuestDef def, String questId, String rewardId, Effects fx) {
        return reward(def, questId, rewardId, fx, null);
    }

    /** SagaLib 1.3 (task #62): gives one reward; with a memory, its credits follow their creditsScale setting. */
    public static boolean reward(QuestDef def, String questId, String rewardId, Effects fx, Memory mem) {
        QuestDef.Reward r = def == null ? null : def.reward(rewardId);
        if (r == null) {
            SagaLog.warn("SF_Reward: no reward \"" + rewardId + "\" in " + questId);
            return false;
        }
        apply(r, fx, mem);
        return true;
    }

    /** SagaLib 1.3 (task #62): a reward's credits, scaled by its creditsScale setting when a memory is given. */
    public static long credits(QuestDef.Reward r, Memory mem) {
        if (mem == null || r.creditsScale == null) return r.credits;
        return Settings.credits(r.credits, r.creditsScale, mem);
    }

    /** Applies a reward in Play mode's order: credits, faction rep, person rep, items, loot (1.2), ships, XP, story points. */
    public static void apply(QuestDef.Reward r, Effects fx) {
        apply(r, fx, null);
    }

    /** SagaLib 1.3 (task #62): {@link #apply(QuestDef.Reward, Effects)} with the credits scaled by a setting. */
    public static void apply(QuestDef.Reward r, Effects fx, Memory mem) {
        long credits = credits(r, mem);
        if (credits != r.credits) {
            SagaLog.info("reward " + r.id + ": " + r.credits + " credits scaled to " + credits + " by setting " + r.creditsScale.setting);
        }
        if (credits != 0) fx.credits(credits);
        for (QuestDef.Amount a : r.rep) fx.factionRep(a.id, a.amount);
        for (QuestDef.Amount a : r.personRep) fx.personRep(a.id, a.amount);
        for (QuestDef.Amount a : r.items) fx.item(a.id, (int) Math.round(a.amount));
        for (lastai.sagalib.model.SystemDef.Loot l : r.loot) fx.loot(l.group, l.chances, l.value);
        for (String s : r.ships) fx.ship(s);
        if (r.xp != 0) fx.xp(r.xp);
        if (r.storyPoints != 0) fx.storyPoints(r.storyPoints);
    }
}
