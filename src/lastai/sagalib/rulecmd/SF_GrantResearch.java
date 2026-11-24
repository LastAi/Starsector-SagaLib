package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Research;
import lastai.sagalib.game.SagaCommand;
import lastai.sagalib.game.VokResearch;

import java.util.List;
import java.util.Map;

/**
 * SF_GrantResearch &lt;techId&gt; (SagaLib 1.4, task #77): the player's faction gets an AoTD Vaults of
 * Knowledge tech as researched (any enabled mod's, VoK's own too), through the guarded VoK bridge.
 * A line in the dialog says so (a campaign message when there is no dialog). Already researched:
 * nothing changes. Without VoK it does nothing (logged once) and the dialog goes on.
 */
public class SF_GrantResearch extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<techId>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        VokResearch.ensure();
        String id = args.get(0);
        Research.Grant g = Research.grant(id);
        if (!g.done) return g.nameOrWhy == null;
        SagaLog.info(name() + " in rule " + ruleId + ": research " + id + " granted to the player's faction");
        if (dialog != null && dialog.getTextPanel() != null) {
            dialog.getTextPanel().setFontSmallInsignia();
            dialog.getTextPanel().addPara("Research completed: %s", Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), g.nameOrWhy);
            dialog.getTextPanel().setFontInsignia();
        } else if (Global.getSector() != null) {
            MessageIntel m = new MessageIntel("Research completed: " + g.nameOrWhy, Misc.getBasePlayerColor());
            m.setIcon(Global.getSector().getPlayerFaction().getCrest());
            m.setSound(BaseIntelPlugin.getSoundMajorPosting());
            Global.getSector().getCampaignUI().addMessage(m, CommMessageAPI.MessageClickAction.COLONY_INFO);
        }
        return true;
    }
}
