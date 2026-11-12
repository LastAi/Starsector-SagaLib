package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.RemoveShip;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/**
 * SF_RemoveShip &lt;variant&gt;: removes one ship of that variant from the player's fleet ("Lost ...").
 * A ship whose hull id matches is used if no variant matches; the flagship is taken last.
 */
public class SF_RemoveShip extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<variant>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        String id = args.get(0);
        FleetMemberAPI pick = null;
        for (int pass = 0; pass < 2 && pick == null; pass++) {
            for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                boolean match = pass == 0 ? id.equals(m.getVariant().getHullVariantId()) : id.equals(m.getHullId());
                if (!match) continue;
                if (pick == null || pick.isFlagship()) pick = m;
            }
        }
        if (pick == null) {
            SagaLog.warn("SF_RemoveShip in rule " + ruleId + ": the player has no ship of variant \"" + id + "\"");
            return false;
        }
        Global.getSector().getPlayerFleet().getFleetData().removeFleetMember(pick);
        if (dialog != null) RemoveShip.addShipLossText(pick, dialog.getTextPanel());
        return true;
    }
}
