package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.game.QuestRuntime;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/** SF_Step &lt;quest&gt; &lt;step&gt;: start if needed, move to the step, update the journal; a success/fail stage ends the quest. */
public class SF_Step extends SagaCommand {
    @Override
    protected int minArgs() { return 2; }

    @Override
    protected String usage() { return "<quest> <step>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        QuestRuntime.step(args.get(0), args.get(1), ruleId, dialog, memoryMap, false);
        return true;
    }
}
