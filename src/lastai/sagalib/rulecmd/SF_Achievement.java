package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;
import lastai.sagalib.game.Achievements;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/**
 * SF_Achievement &lt;achievement&gt; (SagaLib 1.3, task #65): unlocks a MagicLib achievement (a row of
 * the mod's data/config/magic_achievements.csv) through the guarded MagicLib bridge, with MagicLib's
 * notice in the dialog. Achievements are kept in the game's common data, so one unlocked stays
 * unlocked in every save. Without MagicLib it does nothing (logged once) and the dialog goes on.
 */
public class SF_Achievement extends SagaCommand {
    private static final java.util.Set<String> LOGGED = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<achievement>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        String id = args.get(0);
        String why = Achievements.complete(id, dialog == null ? null : dialog.getTextPanel());
        if (why != null) {
            if (LOGGED.add(id + "|" + why)) SagaLog.info(name() + " in rule " + ruleId + ": achievement " + id + " not unlocked: " + why);
            return false;
        }
        SagaLog.info(name() + " in rule " + ruleId + ": achievement " + id + " unlocked (or already was)");
        return true;
    }
}
