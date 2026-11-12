package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;
import lastai.sagalib.encounters.EncounterManager;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/**
 * SF_ShowFleets &lt;encounter&gt;: the visual panel shows the player's fleet and the encounter's
 * (VisualPanelAPI.showFleetInfo). If the encounter isn't spawned, only the player's fleet is shown.
 */
public class SF_ShowFleets extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<encounter>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        CampaignFleetAPI other = EncounterManager.fleet(args.get(0));
        if (other == null) {
            SagaLog.warn("SF_ShowFleets in rule " + ruleId + ": encounter \"" + args.get(0) + "\" isn't spawned; showing only the player's fleet");
            try {
                dialog.getVisualPanel().showFleetInfo("Your fleet", player, null, null);
            } catch (Throwable t) {
                // a null second fleet may not be supported: fall back to the flagship
                SagaLog.warnOnce("showfleets-null", "showFleetInfo without a second fleet failed (" + t + "); showing the flagship instead");
                if (player.getFlagship() != null) dialog.getVisualPanel().showFleetMemberInfo(player.getFlagship());
            }
            return true;
        }
        dialog.getVisualPanel().showFleetInfo("Your fleet", player, other.getNameWithFactionKeepCase(), other);
        return true;
    }
}
