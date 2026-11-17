package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.game.People;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/**
 * SF_RemovePerson &lt;person&gt; (SagaLib 1.1): the person is written out of the story (dead, gone
 * for good): removed from their market, comm directory and the important people, never recreated,
 * and never a fleet's captain again. $global.sagaforge_gone_&lt;person&gt; is set.
 */
public class SF_RemovePerson extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<person>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        People.retire(args.get(0), "SF_RemovePerson in rule " + ruleId);
        return true;
    }
}
