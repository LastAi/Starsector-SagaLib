package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/** SF_ShowPlanetInfo &lt;entity&gt;: the visual panel shows the planet with its info (VisualPanelAPI.showPlanetInfo). */
public class SF_ShowPlanetInfo extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<entity>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        SectorEntityToken e = Global.getSector().getEntityById(args.get(0));
        if (e == null && Global.getSector().getEconomy().getMarket(args.get(0)) != null) {
            e = Global.getSector().getEconomy().getMarket(args.get(0)).getPrimaryEntity();
        }
        if (e == null) {
            SagaLog.error("SF_ShowPlanetInfo in rule " + ruleId + ": unknown entity \"" + args.get(0) + "\"");
            return false;
        }
        dialog.getVisualPanel().showPlanetInfo(e);
        return true;
    }
}
