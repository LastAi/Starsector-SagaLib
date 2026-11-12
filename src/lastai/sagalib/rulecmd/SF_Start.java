package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.game.QuestRuntime;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/** SF_Start &lt;quest&gt;: accept the quest (through vanilla's hub if it is the mission being offered). */
public class SF_Start extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<quest>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        return QuestRuntime.start(args.get(0), ruleId, dialog, memoryMap, false);
    }
}
