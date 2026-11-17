package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.game.SagaCommand;
import lastai.sagalib.systems.SystemBuilder;

import java.util.List;
import java.util.Map;

/**
 * SF_RemoveEntity &lt;entity&gt; [decivilize] (SagaLib 1.1): the entity fades out, once. One with a
 * market is only removed with "decivilize" (vanilla's DecivTracker); otherwise it stays and an
 * error is logged.
 */
public class SF_RemoveEntity extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<entity> [decivilize]"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        boolean deciv = args.size() > 1 && "decivilize".equalsIgnoreCase(args.get(1));
        SystemBuilder.remove(args.get(0), deciv, "SF_RemoveEntity in rule " + ruleId);
        return true;
    }
}
