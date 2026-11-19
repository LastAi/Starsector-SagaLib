package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Mods;
import lastai.sagalib.game.MagicBounties;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/**
 * SF_StartBounty &lt;bounty&gt; (SagaLib 1.3, task #59): the player takes a MagicLib bounty from the
 * quest, as if accepted on MagicLib's board: MagicLib picks its place, spawns the fleet, adds its
 * intel and pays the reward when it ends. Once: a bounty already taken or ended stays as it is.
 * Without MagicLib (or when MagicLib doesn't have the bounty) it does nothing and logs why; a quest
 * tests SF_BountyState &lt;bounty&gt; unavailable first and says so to the player.
 */
public class SF_StartBounty extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<bounty>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        String id = args.get(0);
        String why = MagicBounties.accept(Mods.ownerOfRule(ruleId), id, dialog);
        if (why != null) {
            SagaLog.info(name() + " in rule " + ruleId + ": bounty " + id + " not started: " + why);
            return false;
        }
        SagaLog.info(name() + " in rule " + ruleId + ": bounty " + id + " accepted for the player");
        return true;
    }
}
