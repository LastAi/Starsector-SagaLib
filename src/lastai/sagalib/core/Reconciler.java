package lastai.sagalib.core;

import lastai.sagalib.model.QuestDef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Save reconciliation on load (docs/FORMAT.md; PLAN.md §6), the same rules as play.ts reconcile.
 * For every active quest:
 * <ul>
 * <li>step found in the definition: nothing to do;</li>
 * <li>step listed in a stage's renamedFrom: moved to that stage;</li>
 * <li>step gone: set to {@link QuestLogic#CHANGED_STEP} (the player can abandon the quest);</li>
 * <li>quest gone from every mod: left as it is (the game glue ends its journal entry quietly).</li>
 * </ul>
 * Returns one line per change, for the log.
 */
public final class Reconciler {
    private Reconciler() {}

    public static List<String> reconcile(Memory mem, Collection<String> questIds, Map<String, QuestDef> defs) {
        List<String> log = new ArrayList<String>();
        for (String q : questIds) {
            if (!QuestLogic.ACTIVE.equals(mem.get(QuestLogic.stateKey(q)))) continue;
            QuestDef def = defs.get(q);
            if (def == null) {
                log.add(q + ": no longer in any mod; left as it is");
                continue;
            }
            Object step = mem.get(QuestLogic.stepKey(q));
            if (step == null) continue;
            String s = Values.str(step);
            if (QuestLogic.CHANGED_STEP.equals(s) || def.stage(s) != null) continue;
            QuestDef.Stage moved = null;
            for (QuestDef.Stage st : def.stages) {
                if (st.renamedFrom.contains(s)) {
                    moved = st;
                    break;
                }
            }
            if (moved != null) {
                mem.set(QuestLogic.stepKey(q), moved.id);
                log.add(q + ": step " + s + " is now " + moved.id);
            } else {
                mem.set(QuestLogic.stepKey(q), QuestLogic.CHANGED_STEP);
                log.add(q + ": step " + s + " no longer exists; the quest can be abandoned");
            }
        }
        return log;
    }
}
