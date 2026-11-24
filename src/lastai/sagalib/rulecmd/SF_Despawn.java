package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.encounters.EncounterManager;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/** SF_Despawn [encounter]: the encounter's fleet leaves peacefully; outcome avoided. SagaLib 1.4: without one, the fleet this dialog is with. */
public class SF_Despawn extends SagaCommand {
    @Override
    protected int minArgs() { return 0; }

    @Override
    protected String usage() { return "[encounter]"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        EncounterManager.despawn(args.isEmpty() ? "" : args.get(0), ruleId, dialog);
        return true;
    }
}
