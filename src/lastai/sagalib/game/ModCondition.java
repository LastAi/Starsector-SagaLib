package lastai.sagalib.game;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.core.Conditions;
import lastai.sagalib.core.Mods;

import java.util.List;
import java.util.Map;

/**
 * SF_HasMod / SF_HasAnyMod as rules.csv conditions (SagaLib 1.3). The start record is the one of
 * the content mod whose prefix the rule id has (core.Mods.ownerOfRule).
 */
public abstract class ModCondition extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        Boolean r = Conditions.modCondition(GameMemory.global(), Mods.ownerOfRule(ruleId), name(), args);
        return r != null && r;
    }
}
