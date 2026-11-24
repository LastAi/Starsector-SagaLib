package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.core.Research;
import lastai.sagalib.game.SagaCommand;
import lastai.sagalib.game.VokResearch;

import java.util.List;
import java.util.Map;

/**
 * Condition SF_HasResearch &lt;techId&gt; [faction] (SagaLib 1.4, task #77): the player's faction (or the
 * named faction; "player" is the player's) has researched an AoTD Vaults of Knowledge tech. Asked
 * through the guarded VoK bridge: false without VoK, or for a tech VoK doesn't have.
 */
public class SF_HasResearch extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<techId> [faction]"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        VokResearch.ensure();
        return Research.has(args.get(0), args.size() > 1 ? args.get(1) : null);
    }
}
