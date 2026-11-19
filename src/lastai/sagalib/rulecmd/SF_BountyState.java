package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.core.Bounties;
import lastai.sagalib.core.Mods;
import lastai.sagalib.game.GameMemory;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/**
 * Condition SF_BountyState &lt;bounty&gt; &lt;state&gt; [more states] (SagaLib 1.3, task #59): the MagicLib
 * bounty is in one of these states: unavailable (no MagicLib, or MagicLib doesn't have it), offered
 * (MagicLib has it, nobody took it), active (accepted), succeeded, failed (the flagship was
 * salvaged), expired (its time ran out, or it ended without the player), dismissed, or ended (any
 * of the last four). Read from MagicLib's own memory keys (core.Bounties).
 */
public class SF_BountyState extends SagaCommand {
    @Override
    protected int minArgs() { return 2; }

    @Override
    protected String usage() { return "<bounty> <state> [more states]"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        Boolean r = Bounties.condition(GameMemory.global(), Mods.ownerOfRule(ruleId), name(), args);
        return r != null && r;
    }
}
