package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.core.Presence;
import lastai.sagalib.game.GameMemory;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/**
 * SagaLib 1.3 (task #70). Condition SF_FactionRep &lt;factionA&gt; &lt;factionB&gt; &lt;level&gt;: faction A is at
 * least that level (VENGEFUL … COOPERATIVE, or notHostile) with faction B. Either may be a memory
 * key such as $faction.id (the owner of the market the dialog is at). Vanilla's RepIsAtWorst is
 * the player's standing; this one is between two factions.
 */
public class SF_FactionRep extends SagaCommand {
    @Override
    protected int minArgs() { return 3; }

    @Override
    protected String usage() { return "<factionA> <factionB|$faction.id> <VENGEFUL…COOPERATIVE|notHostile>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        Boolean r = Presence.condition(new GameMemory(memoryMap), name(), args);
        return r != null && r;
    }
}
