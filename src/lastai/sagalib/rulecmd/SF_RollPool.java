package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Pools;
import lastai.sagalib.game.GameMemory;
import lastai.sagalib.game.RuleCommands;
import lastai.sagalib.game.Saga;
import lastai.sagalib.game.SagaCommand;
import lastai.sagalib.model.PoolDef;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * SF_RollPool &lt;pool&gt; [object|global] (SagaLib 1.2): picks one entry of a random pool by weight
 * (docs/FORMAT.md, "Pools") and puts it in $sagaforge_pick for the rules that follow. Rolled for
 * the dialog's object by default (its salvage seed; once-per-object limits), else for the world (a
 * seed stored once). The same object and pool give the same pick after a reload. As a condition it
 * is true when an entry (not "nothing") was picked. In dev mode,
 * $global.sagaforge_pool_&lt;pool&gt;_force = &lt;entry&gt; forces the next pick (test profiles).
 */
public class SF_RollPool extends SagaCommand {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<pool> [object|global]"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        String id = args.get(0);
        PoolDef pool = Saga.content().pools.get(id);
        if (pool == null) {
            SagaLog.error(name() + " in rule " + ruleId + ": no pool \"" + id + "\" (is the mod's data/sagaforge/pools/" + id + ".json listed in content.csv?)");
            return false;
        }
        GameMemory mem = new GameMemory(memoryMap);
        // SagaLib 1.3: a pool only with / only without mods picks nothing
        if (!lastai.sagalib.core.Mods.allowed(GameMemory.global(), pool.gate, "pool", id)) { mem.set(lastai.sagalib.core.Pools.PICK_KEY, lastai.sagalib.core.Pools.NOTHING); return true; }
        SectorEntityToken target = dialog != null ? dialog.getInteractionTarget() : null;
        boolean object = !"global".equals(args.size() > 1 ? args.get(1) : "") && target != null
                && memoryMap != null && memoryMap.containsKey(MemKeys.ENTITY);
        long base;
        if (object) base = Misc.getSalvageSeed(target);
        else {
            Long s = Pools.storedSeed(mem);
            if (s == null) {
                s = new Random().nextLong();
                mem.set(Pools.SEED_KEY, Long.toString(s));
            }
            base = s;
        }
        String force = null;
        if (Global.getSettings().isDevMode()) {
            String fk = "$global.sagaforge_pool_" + id + "_force";
            Object f = mem.get(fk);
            if (f != null) {
                force = String.valueOf(f);
                mem.unset(fk);
            }
        }
        String pick = Pools.roll(pool, mem, RuleCommands.conditions(ruleId, dialog, memoryMap), base, object, force);
        SagaLog.info("pool " + id + " (" + (object ? "object " + target.getId() : "world") + ") picked " + pick + (force != null ? " (forced)" : ""));
        return !Pools.NOTHING.equals(pick);
    }
}
