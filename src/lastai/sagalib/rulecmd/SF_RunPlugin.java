package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Plugins;
import lastai.sagalib.game.GameMemory;
import lastai.sagalib.game.PluginRunner;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/**
 * SF_RunPlugin &lt;className&gt; ["quest=&lt;id&gt;" "node=&lt;id&gt;"] ["key=value" ...] (SagaLib 1.4, task #79;
 * docs/FORMAT.md, "Plugins"; each key=value in double quotes, or the game refuses the line while
 * loading rules): runs a content mod's own class that implements
 * {@link lastai.sagalib.api.SagaPlugin}, a new instance per call, with the line's key=value
 * arguments. What it returns goes into {@code $global.<quest>_<node>_result} (without quest and
 * node: {@code $sagaforge_plugin_result}); no result, or any failure (no such class, not a plugin,
 * an exception in the plugin), unsets it, logs one error line and the dialog goes on, so the
 * exported rules take the node's "no result" path. True when the plugin returned a result.
 */
public class SF_RunPlugin extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<className> [key=value ...]"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        // each "key=value" is one quoted token (an unquoted "=" never gets here: the game refuses the line at load)
        GameMemory mem = new GameMemory(memoryMap);
        Plugins.Call call = Plugins.parse(args, mem);
        if (call == null) {
            SagaLog.error(name() + " in rule " + ruleId + ": needs a class name");
            return false;
        }
        String result = Plugins.run(mem, call, PluginRunner.host(PluginRunner.GAME, ruleId, dialog, memoryMap), name() + " in rule " + ruleId);
        return result != null;
    }
}
