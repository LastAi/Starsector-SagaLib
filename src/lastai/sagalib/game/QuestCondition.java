package lastai.sagalib.game;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.QuestLogic;

import java.util.List;
import java.util.Map;

/** SF_AtStep / SF_QuestDone / SF_QuestState as rules.csv conditions (QuestLogic.condition on the dialog's memory). */
public abstract class QuestCondition extends SagaCommand {
    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (Saga.content().quest(args.get(0)) == null) {
            SagaLog.warnOnce("cond-unknown:" + ruleId + ":" + args.get(0), name() + " in rule " + ruleId + ": unknown quest \""
                    + args.get(0) + "\" (its mod may be disabled); reading its keys anyway");
        }
        Boolean r = QuestLogic.condition(new GameMemory(memoryMap), name(), args);
        return r != null && r;
    }
}
