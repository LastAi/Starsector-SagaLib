package lastai.sagalib.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SagaLib 1.4 (Saga Forge #67 U40): a fleet's extra groups. Each group may join the fleet when it
 * spawns ({@code chance}, 0 to 1) and adds all its ships or {@code pick} of them, drawn by weight
 * without repeats. Pure logic (no game classes), so it is tested; EncounterManager adds the ships.
 */
public final class FleetExtras {
    private FleetExtras() {}

    /** One extra group. */
    public static final class Group {
        public float chance = 1f;
        /** 0 = all its ships; else how many to draw. */
        public int pick;
        public final List<String> variants = new ArrayList<String>();
        public final List<Float> weights = new ArrayList<Float>();
    }

    /** The variants the groups add this time (in group order). */
    public static List<String> roll(List<Group> groups, Random r) {
        List<String> out = new ArrayList<String>();
        for (Group g : groups) {
            if (g.variants.isEmpty() || r.nextFloat() >= g.chance) continue;
            if (g.pick <= 0 || g.pick >= g.variants.size()) { out.addAll(g.variants); continue; }
            List<String> vs = new ArrayList<String>(g.variants);
            List<Float> ws = new ArrayList<Float>();
            for (int i = 0; i < vs.size(); i++) ws.add(i < g.weights.size() && g.weights.get(i) != null ? Math.max(0f, g.weights.get(i)) : 1f);
            for (int n = 0; n < g.pick && !vs.isEmpty(); n++) {
                float total = 0;
                for (float w : ws) total += w;
                int at = vs.size() - 1;
                if (total > 0) {
                    float x = r.nextFloat() * total;
                    for (int i = 0; i < ws.size(); i++) { x -= ws.get(i); if (x < 0) { at = i; break; } }
                } else at = r.nextInt(vs.size());
                out.add(vs.remove(at));
                ws.remove(at);
            }
        }
        return out;
    }
}
