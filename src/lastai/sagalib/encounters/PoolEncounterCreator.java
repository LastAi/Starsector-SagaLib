package lastai.sagalib.encounters;

import com.fs.starfarer.api.impl.campaign.enc.BaseEPEncounterCreator;
import com.fs.starfarer.api.impl.campaign.enc.EncounterManager;
import com.fs.starfarer.api.impl.campaign.enc.EncounterPoint;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Memory;
import lastai.sagalib.core.Pools;
import lastai.sagalib.game.GameMemory;
import lastai.sagalib.game.RuleCommands;
import lastai.sagalib.game.Saga;
import lastai.sagalib.model.EncounterDef;
import lastai.sagalib.model.PoolDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SagaLib 1.2 (Phase 7e, RC-R9): random encounters from the mods' encounter pools, through
 * vanilla's own EncounterManager (one creator among vanilla's, picked by frequency at its encounter
 * points: slipstreams, outside systems). Stateless, as vanilla asks: instances are shared across
 * saves; the pools' limits live in memory (core/Pools).
 */
public final class PoolEncounterCreator extends BaseEPEncounterCreator {
    private static final PoolEncounterCreator INSTANCE = new PoolEncounterCreator();

    /** Adds the creator to vanilla's list once (onApplicationLoad). */
    public static void register() {
        if (!EncounterManager.CREATORS.contains(INSTANCE)) EncounterManager.CREATORS.add(INSTANCE);
    }

    @Override
    public String getId() { return "lastai_sagalib_pools"; }

    private static List<PoolDef> pools(EncounterPoint point, Memory mem) {
        List<PoolDef> out = new ArrayList<PoolDef>();
        for (PoolDef p : Saga.content().pools.values()) {
            if (!p.isEncounters() || p.frequency <= 0) continue;
            if (!lastai.sagalib.core.Mods.allowed(mem, p.gate, "pool", p.id)) continue; // SagaLib 1.3
            boolean here = false;
            for (String s : p.points) if (PoolDef.pointType(s).equals(point.type)) here = true;
            if (!here) continue;
            boolean any = false;
            for (PoolDef.Entry e : p.entries) if (Pools.eligible(p, e, mem, RuleCommands.conditions(null, null, GameMemory.defaultMap()), false)) any = true;
            if (any) out.add(p);
        }
        return out;
    }

    @Override
    public float getFrequencyForPoint(EncounterManager manager, EncounterPoint point) {
        try {
            float f = 0;
            for (PoolDef p : pools(point, GameMemory.global())) f += p.frequency;
            return f;
        } catch (Throwable t) {
            SagaLog.errorOnce("poolenc-freq", "random encounter pools: frequency failed", t);
            return 0;
        }
    }

    @Override
    public void createEncounter(EncounterManager manager, EncounterPoint point) {
        try {
            Memory mem = GameMemory.global();
            List<PoolDef> list = pools(point, mem);
            if (list.isEmpty()) return;
            Random random = manager.getRandom();
            float total = 0;
            for (PoolDef p : list) total += p.frequency;
            float roll = random.nextFloat() * total;
            PoolDef pool = list.get(list.size() - 1);
            for (PoolDef p : list) { roll -= p.frequency; if (roll < 0) { pool = p; break; } }
            String pick = Pools.roll(pool, mem, RuleCommands.conditions(null, null, GameMemory.defaultMap()), random.nextLong(), false, null);
            if (Pools.NOTHING.equals(pick)) return;
            EncounterDef e = Saga.content().encounters.get(pick);
            if (e != null && !lastai.sagalib.core.Mods.allowed(mem, e.gate, "encounter", e.id)) return; // SagaLib 1.3
            if (e == null) { SagaLog.warnOnce("poolenc:" + pool.id + ":" + pick, "encounter pool " + pool.id + ": no encounter \"" + pick + "\""); return; }
            lastai.sagalib.encounters.EncounterManager.spawnAtPoint(e, point.where, point.loc, point.getLocInHyper());
        } catch (Throwable t) {
            SagaLog.error("random encounter pools: spawning failed", t);
        }
    }

}
