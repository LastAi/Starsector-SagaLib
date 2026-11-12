package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Reconciler;
import lastai.sagalib.quest.QuestStage;
import lastai.sagalib.quest.SagaQuest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Save reconciliation on every load (docs/FORMAT.md): Reconciler on the memory keys, then the
 * journal entries: a quest gone from every mod ends quietly, the others get their marker back.
 */
public final class SaveReconcile {
    private SaveReconcile() {}

    public static void run() {
        try {
            Set<String> ids = new LinkedHashSet<String>(Saga.content().quests.keySet());
            List<SagaQuest> missions = new ArrayList<SagaQuest>();
            for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(SagaQuest.class)) {
                SagaQuest q = (SagaQuest) i;
                if (q.getMissionId() == null || q.isEnded() || q.isEnding()) continue;
                ids.add(q.getMissionId());
                missions.add(q);
            }
            // quests whose mod is gone still have their keys: $<quest>_state = active and a $<quest>_step
            MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();
            for (String k : new ArrayList<String>(global.getKeys())) {
                if (!k.endsWith("_state") || !"active".equals(global.get(k))) continue;
                String q = k.substring(1, k.length() - "_state".length());
                if (global.contains("$" + q + "_step")) ids.add(q);
            }
            for (String line : Reconciler.reconcile(GameMemory.global(), ids, Saga.content().quests)) SagaLog.info("save: " + line);
            for (SagaQuest q : missions) {
                if (Saga.content().quest(q.getMissionId()) == null) {
                    q.endQuietly();
                    SagaLog.info("save: " + q.getMissionId() + ": its journal entry was ended quietly (the quest is in no enabled mod)");
                } else if (q.getCurrentStage() == QuestStage.ACTIVE) {
                    q.updateMarker();
                }
            }
        } catch (Throwable t) {
            SagaLog.error("save reconciliation failed; quests are left as they were", t);
        }
    }
}
