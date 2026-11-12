package lastai.sagalib.game;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.CallEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.missions.BarCMD;
import com.fs.starfarer.api.util.Misc;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Memory;
import lastai.sagalib.core.QuestLogic;
import lastai.sagalib.model.QuestDef;
import lastai.sagalib.quest.SagaQuest;

import java.util.Map;
import java.util.Random;

/**
 * What SF_Start / SF_Step / SF_End do in game: QuestLogic's memory changes plus the journal
 * entry (SagaQuest). The rule commands and the dev tools both go through here.
 */
public final class QuestRuntime {
    private QuestRuntime() {}

    static QuestDef quest(String id, String ruleId, String command) {
        QuestDef q = Saga.content().quest(id);
        if (q == null) SagaLog.error(command + " in rule " + ruleId + ": unknown quest \"" + id + "\"");
        return q;
    }

    /**
     * SF_Start: accept the quest. The mission being offered ($missionId == quest) is accepted through
     * vanilla's hub (contact: "Call $mHub accept", bar: "BarCMD accept"); any other quest is
     * created and accepted directly. Then the state is active and the variables are set.
     * False if the quest couldn't start (unknown, or its requires don't hold).
     */
    public static boolean start(String id, String ruleId, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap, boolean skipChecks) {
        QuestDef q = quest(id, ruleId, "SF_Start");
        if (q == null) return false;
        Memory mem = new GameMemory(memoryMap);
        if (QuestLogic.isActive(mem, id)) return true;
        if (q.isMission() && SagaQuest.running(id) == null) {
            if (dialog != null && memoryMap != null && id.equals(offeredMissionId(memoryMap))) acceptOffered(id, ruleId, dialog, memoryMap);
            if (SagaQuest.running(id) == null && startDirectly(q, dialog, memoryMap, skipChecks) == null) {
                SagaLog.info("SF_Start " + id + " (rule " + ruleId + "): not started (its requires don't hold, or it already ran and isn't repeatable)");
                return false;
            }
        }
        QuestLogic.start(mem, q, id);
        return true;
    }

    private static String offeredMissionId(Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
        return local == null ? null : local.getString("$missionId");
    }

    private static void acceptOffered(String id, String ruleId, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        try {
            MemoryAPI entity = memoryMap.get(MemKeys.ENTITY);
            if (entity != null && entity.contains("$BarCMD")) {
                new BarCMD().execute(ruleId, dialog, Misc.tokenize("accept " + id), memoryMap);
            } else if (memoryMap.get(MemKeys.LOCAL) != null && memoryMap.get(MemKeys.LOCAL).contains("$mHub")) {
                new CallEvent().execute(ruleId, dialog, Misc.tokenize("$mHub accept " + id), memoryMap);
            }
        } catch (Throwable t) {
            SagaLog.error("SF_Start " + id + " (rule " + ruleId + "): accepting through the mission hub failed; starting it directly", t);
        }
    }

    /** Creates and accepts a quest's mission without a hub (a quest started by another quest, dev tools, profiles). */
    public static SagaQuest startDirectly(QuestDef q, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap, boolean skipChecks) {
        SagaQuest m = new SagaQuest();
        m.setMissionId(q.id);
        m.setGenRandom(new Random());
        m.setSkipChecks(skipChecks);
        MarketAPI market = dialog != null && dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMarket() : null;
        m.createAndAbortIfFailed(market, false);
        if (m.isMissionCreationAborted()) return null;
        m.accept(dialog, memoryMap != null ? memoryMap : GameMemory.defaultMap());
        return m;
    }

    /** SF_Step: start if needed, set the step, update the journal; a success/fail stage ends the quest. */
    public static void step(String id, String step, String ruleId, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap, boolean skipChecks) {
        QuestDef q = quest(id, ruleId, "SF_Step");
        if (q == null) return;
        if (q.stage(step) == null) SagaLog.warn("SF_Step in rule " + ruleId + ": quest " + id + " has no stage \"" + step + "\"");
        Memory mem = new GameMemory(memoryMap);
        if (!QuestLogic.isActive(mem, id) && !start(id, ruleId, dialog, memoryMap, skipChecks)) return;
        String kind = QuestLogic.step(mem, q, id, step);
        SagaQuest m = SagaQuest.running(id);
        if (m == null) return;
        if (QuestLogic.SUCCESS.equals(kind) || QuestLogic.FAIL.equals(kind)) m.finish(QuestLogic.SUCCESS.equals(kind), dialog, memoryMap);
        else m.stepChanged(dialog);
    }

    /** SF_End: state success/fail and the mission ends (vanilla stage COMPLETED/FAILED, completed key on success). */
    public static void end(String id, boolean success, String ruleId, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        QuestDef q = quest(id, ruleId, "SF_End");
        if (q == null) return;
        QuestLogic.end(new GameMemory(memoryMap), id, success);
        SagaQuest m = SagaQuest.running(id);
        if (m != null) m.finish(success, dialog, memoryMap);
    }

    /**
     * Makes the journal match the memory keys after a profile or a jump: active quests without a
     * running mission get one (no checks); missions of quests that aren't active end quietly.
     */
    public static void syncJournal() {
        Memory mem = GameMemory.global();
        for (QuestDef q : Saga.content().quests.values()) {
            if (!q.isMission()) continue;
            SagaQuest m = SagaQuest.running(q.id);
            boolean active = QuestLogic.isActive(mem, q.id);
            if (active && m == null) {
                if (startDirectly(q, null, null, true) == null) SagaLog.warn("quest " + q.id + " is active but its journal entry couldn't be created");
            } else if (!active && m != null) {
                m.endQuietly();
            } else if (m != null) {
                m.updateMarker();
            }
        }
    }
}
