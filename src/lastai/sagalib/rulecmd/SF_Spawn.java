package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.encounters.EncounterManager;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/** SF_Spawn &lt;encounter&gt;: spawn an encounter's fleet at its spawn.at, for the quest that lists it. */
public class SF_Spawn extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<encounter>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        EncounterManager.spawn(args.get(0), ruleId, dialog);
        return true;
    }
}
