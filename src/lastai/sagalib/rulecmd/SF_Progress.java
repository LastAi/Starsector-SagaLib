package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;
import lastai.sagalib.game.SagaCommand;
import lastai.sagalib.game.SagaProgressEvent;

import java.util.List;
import java.util.Map;

/**
 * SF_Progress &lt;event&gt; &lt;points&gt; ["text"] (SagaLib 1.2): one-time points for a progress event,
 * listed with the text (like vanilla's one-time factors); starts the event if it isn't running yet
 * (unless it already ended). Negative points lower it.
 */
public class SF_Progress extends SagaCommand {
    @Override
    protected int minArgs() { return 2; }

    @Override
    protected String usage() { return "<event> <points> [\"text\"]"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        int points;
        try {
            points = Math.round(Float.parseFloat(args.get(1)));
        } catch (NumberFormatException e) {
            SagaLog.error(name() + " in rule " + ruleId + ": \"" + args.get(1) + "\" isn't a number of points");
            return false;
        }
        return SagaProgressEvent.oneTime(args.get(0), points, args.size() > 2 ? args.get(2) : "", dialog, name() + " in rule " + ruleId);
    }
}
