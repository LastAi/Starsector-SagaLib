package lastai.sagalib.core;

import java.util.Random;

/**
 * When a roaming fleet leaves (SagaLib 1.2, docs/FORMAT.md "Roaming fleets"), pure so it can be
 * tested. One key per encounter: {@code $global.sagaforge_roam_<encounter>_next}, the day of the
 * next departure. The first departure is spread over one interval; later ones come an interval
 * (×0.75–1.25) apart; while the most are on the way, it tries again a day later.
 */
public final class RoamLogic {
    private RoamLogic() {}

    public static String nextKey(String encounter) { return "$global.sagaforge_roam_" + encounter + "_next"; }

    /** True if a fleet should leave now ({@code onTheWay}: routes of this encounter now). */
    public static boolean due(Memory m, String encounter, double now, float everyDays, int onTheWay, int max, Random r) {
        Object next = m.get(nextKey(encounter));
        if (next == null) {
            m.set(nextKey(encounter), (float) (now + everyDays * r.nextDouble()));
            return false;
        }
        Double at = Values.number(next);
        if (at != null && now < at) return false;
        if (onTheWay >= max) {
            m.set(nextKey(encounter), (float) (now + 1));
            return false;
        }
        m.set(nextKey(encounter), (float) (now + everyDays * (0.75 + 0.5 * r.nextDouble())));
        return true;
    }
}
