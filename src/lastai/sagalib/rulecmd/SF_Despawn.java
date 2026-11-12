package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.encounters.EncounterManager;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/** SF_Despawn &lt;encounter&gt;: the encounter's fleet leaves peacefully; outcome avoided. */
public class SF_Despawn extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<encounter>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        EncounterManager.despawn(args.get(0), ruleId, dialog);
        return true;
    }
}
