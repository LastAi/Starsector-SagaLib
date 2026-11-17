package lastai.sagalib.test;

import lastai.sagalib.core.Conditions;
import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.Pools;
import lastai.sagalib.core.Problems;
import lastai.sagalib.model.PoolDef;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Random pools (SagaLib 1.2): the pick, its seed, limits, conditions, nothing and force. */
public final class PoolTests {
    static PoolDef pool(String json) throws Exception {
        Problems p = new Problems();
        PoolDef d = PoolDef.parse(new JSONObject(json), new JsonReader("test", p));
        T.yes(p.isEmpty(), "pool parses: " + p.all());
        return d;
    }

    static final String ABC = "{format:1,id:'vgc_p',entries:[{id:'a',weight:5},{id:'b',weight:3},{id:'c',weight:2}]}";

    /** The java.util.Random numbers the editor's TypeScript port is checked against. */
    public static void javaRandom() {
        T.eq(0.7275637f, new Random(42).nextFloat(), "new Random(42).nextFloat()");
        T.eq(-1155869325, new Random(1).nextInt(), "new Random(1).nextInt()");
        T.eq(7699432170279971967L, Pools.mix(0, "vgc_p"), "the mix of seed 0 and \"vgc_p\"");
    }

    public static void sameSeedSamePick() throws Exception {
        PoolDef p = pool(ABC);
        MapMemory m1 = new MapMemory(), m2 = new MapMemory();
        for (int i = 0; i < 5; i++) {
            String a = Pools.roll(p, m1, null, 1234L, false, null);
            String b = Pools.roll(p, m2, null, 1234L, false, null);
            T.eq(a, b, "draw " + i + " is the same from the same seed");
        }
        T.eq("5", lastai.sagalib.core.Values.str(m1.get(Pools.drawsKey("vgc_p", false))), "five draws counted");
    }

    public static void followsTheWeights() throws Exception {
        PoolDef p = pool(ABC);
        Map<String, Integer> n = new HashMap<String, Integer>();
        for (long s = 0; s < 4000; s++) {
            String k = Pools.roll(p, new MapMemory(), null, s * 7919L, false, null);
            n.put(k, (n.containsKey(k) ? n.get(k) : 0) + 1);
        }
        T.yes(n.get("a") > 1800 && n.get("a") < 2200, "a about 50%: " + n);
        T.yes(n.get("b") > 1000 && n.get("b") < 1400, "b about 30%: " + n);
        T.yes(n.get("c") > 600 && n.get("c") < 1000, "c about 20%: " + n);
        T.no(n.containsKey("nothing"), "no nothing share");
    }

    public static void nothingShare() throws Exception {
        PoolDef p = pool("{format:1,id:'vgc_n',nothing:1,entries:[{id:'a',weight:1}]}");
        int nothing = 0;
        for (long s = 0; s < 2000; s++) {
            MapMemory m = new MapMemory();
            if (Pools.NOTHING.equals(Pools.roll(p, m, null, s * 104729L, false, null))) {
                nothing++;
                T.eq("nothing", m.get(Pools.PICK_KEY), "the pick key says nothing");
            }
        }
        T.yes(nothing > 850 && nothing < 1150, "nothing about half: " + nothing);
    }

    public static void limits() throws Exception {
        PoolDef p = pool("{format:1,id:'vgc_l',entries:[{id:'once',weight:100,once:'playthrough'},{id:'rest',weight:1}]}");
        MapMemory m = new MapMemory();
        T.eq("once", Pools.roll(p, m, null, 5L, false, null), "the heavy entry first");
        for (int i = 0; i < 20; i++) T.eq("rest", Pools.roll(p, m, null, 5L, false, null), "then never again (" + i + ")");
        T.eq("1", lastai.sagalib.core.Values.str(m.get(Pools.countKey("vgc_l", "once"))), "counted once");

        PoolDef max = pool("{format:1,id:'vgc_m',entries:[{id:'two',weight:100,max:2},{id:'rest',weight:1}]}");
        MapMemory m2 = new MapMemory();
        int twos = 0;
        for (int i = 0; i < 20; i++) if ("two".equals(Pools.roll(max, m2, null, 9L, false, null))) twos++;
        T.eq(2, twos, "at most two");

        PoolDef obj = pool("{format:1,id:'vgc_o',entries:[{id:'x',weight:100,once:'object'},{id:'rest',weight:1}]}");
        MapMemory m3 = new MapMemory();
        T.eq("x", Pools.roll(obj, m3, null, 3L, true, null), "first visit");
        T.eq("rest", Pools.roll(obj, m3, null, 3L, true, null), "the same object: not again");
        m3.unset(Pools.doneKey("vgc_o", "x"));
        m3.unset(Pools.drawsKey("vgc_o", true));
        T.eq("x", Pools.roll(obj, m3, null, 3L, true, null), "another object: again");

        PoolDef cd = pool("{format:1,id:'vgc_c',entries:[{id:'x',weight:100,cooldownDays:30},{id:'rest',weight:1}]}");
        MapMemory m4 = new MapMemory();
        m4.set(Pools.DAYS_KEY, 10f);
        T.eq("x", Pools.roll(cd, m4, null, 1L, false, null), "day 10");
        m4.set(Pools.DAYS_KEY, 30f);
        T.eq("rest", Pools.roll(cd, m4, null, 1L, false, null), "day 30: still cooling down");
        m4.set(Pools.DAYS_KEY, 40f);
        T.eq("x", Pools.roll(cd, m4, null, 1L, false, null), "day 40: again");
    }

    public static void conditionsAndForce() throws Exception {
        PoolDef p = pool("{format:1,id:'vgc_k',entries:[{id:'rich',weight:100,conditions:['$player.credits >= 1000']},{id:'poor',weight:1}]}");
        MapMemory m = new MapMemory();
        Conditions c = new Conditions(m, null);
        m.set("$player.credits", 10f);
        T.eq("poor", Pools.roll(p, m, c, 1L, false, null), "the condition fails");
        m.set("$player.credits", 5000f);
        T.eq("rich", Pools.roll(p, m, c, 1L, false, null), "the condition holds");
        T.eq("poor", Pools.roll(p, m, c, 1L, false, "poor"), "forced");
        T.eq("nothing", Pools.roll(p, m, c, 1L, false, "nothing"), "forced to nothing");
        T.eq("rich", Pools.roll(p, m, c, 1L, false, "no_such_entry"), "an unknown force is ignored");
    }

    public static void emptyAndZero() throws Exception {
        PoolDef p = pool("{format:1,id:'vgc_z',entries:[{id:'a',weight:0}]}");
        T.eq("nothing", Pools.roll(p, new MapMemory(), null, 1L, false, null), "total 0: nothing");
    }
}
