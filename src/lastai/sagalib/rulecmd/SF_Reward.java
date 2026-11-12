package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.QuestLogic;
import lastai.sagalib.game.GameEffects;
import lastai.sagalib.game.SagaCommand;
import lastai.sagalib.game.Saga;
import lastai.sagalib.model.QuestDef;

import java.util.List;
import java.util.Map;

/** SF_Reward &lt;quest&gt; &lt;rewardId&gt;: give that reward from the quest JSON, with vanilla's "Gained ..." lines. */
public class SF_Reward extends SagaCommand {
    @Override
    protected int minArgs() { return 2; }

    @Override
    protected String usage() { return "<quest> <rewardId>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        QuestDef q = Saga.content().quest(args.get(0));
        if (q == null) {
            SagaLog.error("SF_Reward in rule " + ruleId + ": unknown quest \"" + args.get(0) + "\"");
            return false;
        }
        return QuestLogic.reward(q, q.id, args.get(1), new GameEffects(dialog == null ? null : dialog.getTextPanel()));
    }
}
