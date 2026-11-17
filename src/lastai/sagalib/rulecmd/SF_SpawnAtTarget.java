package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Memory;
import lastai.sagalib.core.Targets;
import lastai.sagalib.game.GameMemory;
import lastai.sagalib.game.SagaCommand;
import lastai.sagalib.quest.SagaQuest;

import java.util.List;
import java.util.Map;

/**
 * SF_SpawnAtTarget &lt;quest&gt; &lt;var&gt; &lt;entity|derelict&gt; &lt;type&gt; [where] (SagaLib 1.2, RC-R2): places
 * something near the quest's random target with vanilla's mission spawning (removed when the
 * quest ends) and stores its id in $global.&lt;quest&gt;_&lt;var&gt;_spawned. type: a custom entity type
 * (entity) or a faction id (derelict); where: vanilla's EntityLocationType, default ORBITING_PARAM.
 * Once per quest: a second call keeps the first.
 */
public class SF_SpawnAtTarget extends SagaCommand {
    @Override
    protected int minArgs() { return 4; }

    @Override
    protected String usage() { return "<quest> <var> <entity|derelict> <type> [where]"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        String quest = args.get(0), var = args.get(1);
        Memory mem = GameMemory.global();
        String spawnedKey = Targets.factKey(quest, var, Targets.SPAWNED);
        if (mem.get(spawnedKey) != null) return true;
        SagaQuest m = SagaQuest.running(quest);
        if (m == null) { SagaLog.warn("SF_SpawnAtTarget in rule " + ruleId + ": quest " + quest + " isn't running"); return false; }
        Object picked = mem.get(Targets.key(quest, var));
        SectorEntityToken center = picked == null ? null : SagaQuest.targetEntity(String.valueOf(picked));
        if (center == null) { SagaLog.warn("SF_SpawnAtTarget in rule " + ruleId + ": quest " + quest + " has no target " + var + " in this game"); return false; }
        SectorEntityToken e = m.spawnAtTarget(center, args.get(2), args.get(3), args.size() > 4 ? args.get(4) : null);
        if (e == null) { SagaLog.warn("SF_SpawnAtTarget in rule " + ruleId + ": vanilla couldn't place a " + args.get(3) + " near " + center.getId()); return false; }
        mem.set(spawnedKey, e.getId());
        SagaLog.info("quest " + quest + ": spawned " + e.getId() + " (" + args.get(2) + " " + args.get(3) + ") near target " + var);
        return true;
    }
}
