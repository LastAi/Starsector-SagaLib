package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;
import lastai.sagalib.game.QuestRuntime;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/** SF_End &lt;quest&gt; success|fail: end the mission that way. */
public class SF_End extends SagaCommand {
    @Override
    protected int minArgs() { return 2; }

    @Override
    protected String usage() { return "<quest> success|fail"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        String how = args.get(1);
        if (!"success".equals(how) && !"fail".equals(how)) {
            SagaLog.error("SF_End in rule " + ruleId + ": the outcome is success or fail, not \"" + how + "\"");
            return false;
        }
        QuestRuntime.end(args.get(0), "success".equals(how), ruleId, dialog, memoryMap);
        return true;
    }
}
