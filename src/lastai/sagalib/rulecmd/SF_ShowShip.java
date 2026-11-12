package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.game.Items;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/** SF_ShowShip &lt;variant&gt;: the visual panel shows one ship (VisualPanelAPI.showFleetMemberInfo). */
public class SF_ShowShip extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<variant>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        FleetMemberAPI m = Items.ship(args.get(0));
        if (m == null) return false;
        dialog.getVisualPanel().showFleetMemberInfo(m);
        return true;
    }
}
