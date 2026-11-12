package lastai.sagalib.game;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base of SagaLib's rule commands: arguments are resolved like vanilla's ($vars read from
 * memory), a missing argument logs an error with the rule id, and nothing ever throws into the
 * rules engine.
 */
public abstract class SagaCommand extends BaseCommandPlugin {
    /** The command's name in rules.csv (its class name). */
    protected String name() { return getClass().getSimpleName(); }

    /** At least {@code min} arguments; the argument names are for the error message. */
    protected abstract int minArgs();

    protected abstract String usage();

    protected abstract boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params,
                                   Map<String, MemoryAPI> memoryMap);

    @Override
    public final boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        try {
            List<String> args = new ArrayList<String>();
            if (params != null) {
                for (Token t : params) {
                    String s = t.getString(memoryMap);
                    args.add(s == null ? "" : s);
                }
            }
            if (args.size() < minArgs()) {
                SagaLog.error(name() + " in rule " + ruleId + ": needs " + usage() + ", got " + args);
                return false;
            }
            return run(ruleId, dialog, args, params, memoryMap);
        } catch (Throwable t) {
            SagaLog.error(name() + " in rule " + ruleId + " failed", t);
            return false;
        }
    }
}
