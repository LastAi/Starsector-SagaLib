package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.core.Targets;
import lastai.sagalib.game.GameMemory;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/**
 * Condition SF_IsTarget &lt;quest&gt; &lt;var&gt; [person|spawned] (SagaLib 1.2): the dialog is at the
 * quest's random target (its entity, its market, or for a system target anything in that system),
 * with the target's person ($id), or at what SF_SpawnAtTarget placed. Used by Arrive nodes.
 */
public class SF_IsTarget extends SagaCommand {
    @Override
    protected int minArgs() { return 2; }

    @Override
    protected String usage() { return "<quest> <var> [person|spawned]"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        // SagaLib 1.2: "person" = talking to the target's person; "spawned" = at what SF_SpawnAtTarget placed
        String part = args.size() > 2 ? args.get(2) : "";
        if ("person".equals(part)) {
            Object pid = GameMemory.global().get(Targets.factKey(args.get(0), args.get(1), Targets.PERSON));
            Object id = new GameMemory(memoryMap).get("$id");
            return pid != null && id != null && String.valueOf(pid).equals(String.valueOf(id));
        }
        Object picked = GameMemory.global().get("spawned".equals(part) ? Targets.factKey(args.get(0), args.get(1), Targets.SPAWNED) : Targets.key(args.get(0), args.get(1)));
        if (picked == null || dialog == null) return false;
        SectorEntityToken t = dialog.getInteractionTarget();
        if (t == null) return false;
        String market = t.getMarket() != null ? t.getMarket().getId() : null;
        String system = t.getStarSystem() != null ? t.getStarSystem().getId() : null;
        return Targets.matches(String.valueOf(picked), t.getId(), market, system);
    }
}
