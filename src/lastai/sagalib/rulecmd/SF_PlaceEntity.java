package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.game.SagaCommand;
import lastai.sagalib.systems.SystemBuilder;

import java.util.List;
import java.util.Map;

/** SF_PlaceEntity &lt;entity&gt; (SagaLib 1.1): place a deferred entity ("appears") now, once. */
public class SF_PlaceEntity extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<entity>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        SystemBuilder.placeById(args.get(0), "SF_PlaceEntity in rule " + ruleId);
        return true;
    }
}
