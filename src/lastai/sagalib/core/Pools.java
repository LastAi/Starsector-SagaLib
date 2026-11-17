package lastai.sagalib.core;

import lastai.sagalib.model.PoolDef;

import java.util.Random;

/**
 * Picks one entry of a random pool (SagaLib 1.2; docs/FORMAT.md, "Pools"). Pure: memory in,
 * memory out, the seed given by the caller. The same as Saga Forge's Play mode
 * (tools/saga-forge/src/shared/pools.ts), which ports java.util.Random exactly, so the golden
 * cases prove both pick the same entry from the same seed.
 * <pre>
 *   seed   = scramble(base ^ (poolId.hashCode() * 0x9E3779B97F4A7C15L) + draws * 0x2545F4914F6CDD1DL)  (SplitMix64)
 *   random = new Random(seed); x = nextFloat() * (eligible weights + nothing)
 * </pre>
 * Memory keys (S = "$entity." when rolled for an object, else "$global."):
 * <pre>
 *   S.sagaforge_pool_&lt;pool&gt;_draws               draws so far (a reload gives the same sequence)
 *   $global.sagaforge_pool_&lt;pool&gt;_&lt;entry&gt;_count  picks per playthrough (once, max)
 *   $entity.sagaforge_pool_&lt;pool&gt;_&lt;entry&gt;_done   once per object
 *   $global.sagaforge_pool_&lt;pool&gt;_&lt;entry&gt;_day    the day it was last picked (cooldown)
 *   $global.sagaforge_pool_&lt;pool&gt;_last           the last pick
 *   $sagaforge_pick                                 the pick, for the rules that follow (local)
 * </pre>
 */
public final class Pools {
    private Pools() {}

    public static final String NOTHING = "nothing";
    public static final String PICK_KEY = "$sagaforge_pick";
    public static final String SEED_KEY = "$global.sagaforge_seed";
    public static final String DAYS_KEY = "$global.sagaforge_days";

    private static String key(String scope, String pool, String rest) { return scope + "sagaforge_pool_" + pool + "_" + rest; }

    public static String countKey(String pool, String entry) { return key("$global.", pool, entry + "_count"); }

    public static String doneKey(String pool, String entry) { return key("$entity.", pool, entry + "_done"); }

    public static String dayKey(String pool, String entry) { return key("$global.", pool, entry + "_day"); }

    public static String lastKey(String pool) { return key("$global.", pool, "last"); }

    public static String drawsKey(String pool, boolean object) { return key(object ? "$entity." : "$global.", pool, "draws"); }

    /** The seed mixed with the pool's id (Java long arithmetic; the editor uses BigInt). */
    public static long mix(long base, String poolId) { return base ^ ((long) poolId.hashCode() * 0x9E3779B97F4A7C15L); }

    /**
     * SplitMix64: java.util.Random gives nearly the same first number for seeds that differ a
     * little (the next draw, a neighbouring object), so the seed is scrambled first.
     */
    public static long scramble(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** The java.util.Random seed of one draw. */
    public static long drawSeed(long base, String poolId, long draws) { return scramble(mix(base, poolId) + draws * 0x2545F4914F6CDD1DL); }

    /** Is this entry allowed now (its limit not used up, its conditions true)? */
    public static boolean eligible(PoolDef pool, PoolDef.Entry e, Memory mem, Conditions cond, boolean object) {
        if (e.weight <= 0) return false;
        float count = num(mem.get(countKey(pool.id, e.id)));
        if ("playthrough".equals(e.once) || ("object".equals(e.once) && !object)) {
            if (count >= 1) return false;
        }
        if ("object".equals(e.once) && object && Values.truthy(mem.get(doneKey(pool.id, e.id)))) return false;
        if (e.max > 0 && count >= e.max) return false;
        if (e.cooldownDays > 0) {
            Object last = mem.get(dayKey(pool.id, e.id));
            if (last != null && num(mem.get(DAYS_KEY)) - num(last) < e.cooldownDays) return false;
        }
        return cond == null || cond.all(e.conditions);
    }

    /**
     * Rolls the pool and writes the result and the limits to memory. {@code object}: rolled for
     * the dialog's object (once-per-object limits and the draw count live on it).
     * {@code force}: an entry id to pick instead (dev mode / Play mode), or null.
     * Returns the picked entry id, or {@link #NOTHING}.
     */
    public static String roll(PoolDef pool, Memory mem, Conditions cond, long base, boolean object, String force) {
        String drawsKey = drawsKey(pool.id, object);
        long draws = (long) num(mem.get(drawsKey));
        String pick = NOTHING;
        if (force != null && (NOTHING.equals(force) || pool.entry(force) != null)) {
            pick = force;
        } else {
            float total = pool.nothing;
            boolean[] ok = new boolean[pool.entries.size()];
            for (int i = 0; i < ok.length; i++) {
                ok[i] = eligible(pool, pool.entries.get(i), mem, cond, object);
                if (ok[i]) total += pool.entries.get(i).weight;
            }
            if (total > 0) {
                Random r = new Random(drawSeed(base, pool.id, draws));
                float x = r.nextFloat() * total;
                float at = 0;
                for (int i = 0; i < ok.length; i++) {
                    if (!ok[i]) continue;
                    at += pool.entries.get(i).weight;
                    if (x < at) { pick = pool.entries.get(i).id; break; }
                }
            }
        }
        mem.set(drawsKey, (float) (draws + 1));
        mem.set(PICK_KEY, pick);
        mem.set(lastKey(pool.id), pick);
        if (!NOTHING.equals(pick)) {
            String c = countKey(pool.id, pick);
            mem.set(c, num(mem.get(c)) + 1f);
            if (object) mem.set(doneKey(pool.id, pick), true);
            mem.set(dayKey(pool.id, pick), num(mem.get(DAYS_KEY)));
        }
        return pick;
    }

    /** The stored world seed, or null if none yet (the game glue stores a random one once). */
    public static Long storedSeed(Memory mem) {
        Object v = mem.get(SEED_KEY);
        if (v == null) return null;
        try { return Long.parseLong(Values.str(v)); } catch (NumberFormatException e) { return null; }
    }

    private static float num(Object v) {
        Double d = Values.number(v);
        return d == null ? 0f : d.floatValue();
    }
}
