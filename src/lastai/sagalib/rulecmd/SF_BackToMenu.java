package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/**
 * SF_BackToMenu: the end of a conversation branch. Clears the options, then runs FireAll
 * PopulateOptions (the market's, contact's or fleet's menu). If nothing adds an option (a
 * custom entity such as a wreck has no menu), the dialog closes instead of being left with
 * the old options or none (found in game, 2026-11-11).
 */
public class SF_BackToMenu extends SagaCommand {
    @Override
    protected int minArgs() { return 0; }

    @Override
    protected String usage() { return ""; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        dialog.getOptionPanel().clearOptions();
        FireAll.fire(ruleId, dialog, memoryMap, "PopulateOptions");
        List<?> options = dialog.getOptionPanel().getSavedOptionList();
        if (options == null || options.isEmpty()) dialog.dismiss();
        return true;
    }
}
