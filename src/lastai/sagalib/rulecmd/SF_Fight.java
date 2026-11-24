package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.game.FightRuntime;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/**
 * SF_Fight &lt;quest&gt; &lt;node&gt; (SagaLib 1.4; docs/FORMAT.md, "Fights"): a battle on the spot in the open
 * dialog against the fleet of the quest's fight at that node, like vanilla's salvage defenders; then
 * the fight's {@code won} trigger (a win) or {@code left} trigger (left or lost) in the same dialog.
 */
public class SF_Fight extends SagaCommand {
    @Override
    protected int minArgs() { return 2; }

    @Override
    protected String usage() { return "<quest> <node>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        return FightRuntime.fight(args.get(0), args.get(1), ruleId, dialog, memoryMap);
    }
}
