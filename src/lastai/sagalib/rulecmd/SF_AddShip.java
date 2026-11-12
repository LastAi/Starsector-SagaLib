package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.game.GameEffects;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/** SF_AddShip &lt;variant&gt;: a new ship of that variant joins the player's fleet ("Gained ..."). */
public class SF_AddShip extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<variant>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        new GameEffects(dialog == null ? null : dialog.getTextPanel()).ship(args.get(0));
        return true;
    }
}
