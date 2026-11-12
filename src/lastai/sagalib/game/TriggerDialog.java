package lastai.sagalib.game;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Triggers;

import java.util.HashMap;
import java.util.Map;

/**
 * Fires a trigger outside a dialog (encounter outcomes, quest timers) in a rule-based dialog on
 * the player's fleet, with the rules of docs/FORMAT.md (end of "Encounters"): no matching rule,
 * no dialog; nothing added, closed at once; text but no options, one "Continue".
 */
public final class TriggerDialog extends RuleBasedInteractionDialogPluginImpl {
    private final String trigger;

    private TriggerDialog(String trigger) {
        super(trigger);
        this.trigger = trigger;
    }

    /** False if a dialog is already open (try again later). True if handled (fired, or nothing to fire). */
    public static boolean fire(String trigger) {
        // still loading (no campaign UI yet) or another dialog is open: try again later
        if (Global.getCurrentState() != GameState.CAMPAIGN) return false;
        if (Global.getSector().getCampaignUI().isShowingDialog()) return false;
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (Triggers.before(matches(trigger, player)) == Triggers.After.NO_DIALOG) {
            SagaLog.info("trigger " + trigger + ": no rule matches; no dialog");
            return true;
        }
        return Global.getSector().getCampaignUI().showInteractionDialog(new TriggerDialog(trigger), player);
    }

    /** The same memory map the dialog will have (target = the player's fleet). Unsure (a command throws) = assume it matches. */
    static boolean matches(String trigger, CampaignFleetAPI player) {
        try {
            Map<String, MemoryAPI> map = new HashMap<String, MemoryAPI>();
            map.put(MemKeys.LOCAL, player.getMemory());
            map.put(MemKeys.FACTION, player.getFaction().getMemory());
            map.put(MemKeys.GLOBAL, Global.getSector().getMemory());
            map.put(MemKeys.PLAYER, Global.getSector().getCharacterData().getMemory());
            return Global.getSector().getRules().getBestMatching(null, trigger, null, map) != null;
        } catch (Throwable t) {
            return true;
        }
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        int before = UiLabels.count(dialog.getTextPanel());
        super.init(dialog); // fires the trigger; adds vanilla's failsafe "Leave" if the rules added no option
        try {
            OptionPanelAPI options = dialog.getOptionPanel();
            boolean ruleOptions = !options.hasOption(FAILSAFE_LEAVE);
            int after = UiLabels.count(dialog.getTextPanel());
            boolean added = before < 0 || after < 0 || after > before; // can't tell: assume text, show Continue
            Triggers.After what = Triggers.after(ruleOptions, added, false);
            if (what == Triggers.After.DISMISS) {
                dialog.dismiss();
            } else if (what == Triggers.After.CONTINUE) {
                options.clearOptions();
                options.addOption("Continue", FAILSAFE_LEAVE);
            }
        } catch (Throwable t) {
            SagaLog.error("trigger " + trigger + ": finishing the dialog failed", t);
        }
    }
}
