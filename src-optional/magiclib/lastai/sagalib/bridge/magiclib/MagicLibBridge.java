package lastai.sagalib.bridge.magiclib;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import lastai.sagalib.game.BountyBridge;
import org.magiclib.bounty.ActiveBounty;
import org.magiclib.bounty.MagicBountyCoordinator;
import org.magiclib.bounty.MagicBountyLoader;
import org.magiclib.bounty.MagicBountySpec;
import org.magiclib.util.MagicTxt;

/**
 * SagaLib's MagicLib bridge (task #59), checked against MagicLib 1.5.6 (Saga Forge FORMATS.md,
 * "MagicLib 1.5.6 bounties"). Compiled against MagicLib's jar by build.sh and loaded by name only
 * when MagicLib is enabled (game/MagicBounties). Accepting does what MagicLib's own board and its
 * old bar event do: createActiveBounty (the place is picked and the fleet spawned) if the board
 * hasn't yet, then acceptBounty with the scaled credit reward, the reputation and the paying faction.
 */
public class MagicLibBridge implements BountyBridge {
    private static MagicBountySpec spec(String id) {
        MagicBountySpec s = MagicBountyLoader.getBountyData(id);
        if (s != null) return s;
        ActiveBounty a = MagicBountyCoordinator.getInstance().getActiveBounty(id);
        return a == null ? null : a.getSpec();
    }

    @Override
    public boolean known(String id) { return spec(id) != null; }

    @Override
    public String memKey(String id) {
        MagicBountySpec s = spec(id);
        return s == null || s.job_memKey == null || s.job_memKey.trim().isEmpty() ? null : s.job_memKey;
    }

    @Override
    public String accept(String id, InteractionDialogAPI dialog) {
        MagicBountyCoordinator c = MagicBountyCoordinator.getInstance();
        if (c.getCompletedBounties().contains(id)) return "MagicLib lists it as finished";
        ActiveBounty a = c.getActiveBounty(id);
        if (a != null && a.getStage() != ActiveBounty.Stage.NotAccepted) return "MagicLib has it as " + a.getStage().name();
        MagicBountySpec s = spec(id);
        if (s == null) return "MagicLib has no bounty \"" + id + "\"";
        if (a == null) {
            a = c.createActiveBounty(id, s);
            if (a == null) return "MagicLib couldn't create it (no place for the fleet found, or no fleet; see MagicLib's lines in starsector.log)";
        }
        SectorEntityToken from = dialog != null && dialog.getInteractionTarget() != null ? dialog.getInteractionTarget() : Global.getSector().getPlayerFleet();
        a.acceptBounty(from, a.calculateCreditReward(c.getPreScalingCreditRewardMultiplier(), c.getPostScalingCreditRewardMultiplier()),
                s.job_reputation_reward, s.job_forFaction);
        if (dialog != null && dialog.getTextPanel() != null) {
            // MagicLib's own line ("Accepted job: "), as its bar event shows it
            dialog.getTextPanel().addPara("%s", Misc.getHighlightColor(), MagicTxt.getString("mb_accepted") + s.job_name);
        }
        return null;
    }
}
