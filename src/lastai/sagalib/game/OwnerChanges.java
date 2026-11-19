package lastai.sagalib.game;

import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Memory;
import lastai.sagalib.core.Places;
import lastai.sagalib.core.QuestLogic;
import lastai.sagalib.core.Triggers;
import lastai.sagalib.model.QuestDef;
import lastai.sagalib.quest.SagaQuest;

/**
 * SagaLib 1.3 (task #61): once a day (RuntimeScript), the markets of active quests with
 * "ownerChange" are compared with the owners remembered at the start (core.Places.check). The
 * memory changes come from there; this is the journal: a failed quest's entry ends, a moved one
 * shows its new step, and "stage" fires the quest's trigger (what follows that Stage node).
 */
public final class OwnerChanges {
    private OwnerChanges() {}

    public static void tick(Memory mem) {
        for (QuestDef q : Saga.content().quests.values()) {
            if (q.ownerChange == null || !QuestLogic.isActive(mem, q.id)) continue;
            Places.Change c = Places.check(mem, q);
            if (c != null) apply(mem, q, c);
        }
    }

    /** Also used by the dev menu's "change market owner" (it checks at once instead of waiting a day). */
    public static void apply(Memory mem, QuestDef q, Places.Change c) {
        SagaLog.info("quest " + q.id + ": market " + c.market + " changed owner to " + c.owner + "; " + ("fail".equals(c.action) ? "the quest fails" : "stage".equals(c.action) ? "it goes to stage " + c.stage : "it keeps going"));
        SagaQuest m = SagaQuest.running(q.id);
        if ("fail".equals(c.action)) {
            if (m != null) m.finish(false, null, null);
            return;
        }
        if ("stage".equals(c.action)) {
            String state = QuestLogic.state(mem, q.id);
            if (m != null) {
                if (QuestLogic.SUCCESS.equals(state) || QuestLogic.FAIL.equals(state)) m.finish(QuestLogic.SUCCESS.equals(state), null, null);
                else m.stepChanged(null);
            }
            try { lastai.sagalib.systems.SystemBuilder.applyWorldChanges(); } catch (Throwable t) { SagaLog.error("world changes after an owner change in " + q.id, t); }
            if (c.trigger != null && QuestLogic.isActive(mem, q.id)) Triggers.add(mem, c.trigger);
        }
    }

    /** Dev menu: check every active quest now (after the dev menu changed an owner). Returns how many quests reacted. */
    public static int checkNow(Memory mem) {
        int n = 0;
        for (QuestDef q : Saga.content().quests.values()) {
            if (q.ownerChange == null || !QuestLogic.isActive(mem, q.id)) continue;
            Places.Change c = Places.check(mem, q);
            if (c != null) { apply(mem, q, c); n++; }
        }
        return n;
    }
}
