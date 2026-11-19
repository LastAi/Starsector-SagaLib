package lastai.sagalib.core;

import lastai.sagalib.model.QuestDef;

import java.util.ArrayList;
import java.util.List;

/**
 * SagaLib 1.3: Wait nodes on a MagicLib bounty's end (the quest JSON's "bountyWaits"). The
 * bounty's state comes from MagicLib's own memory keys ({@link Bounties#state}), so this works for
 * any mod's bounty and needs no MagicLib class. State lives in one memory key, so it survives saves:
 * <pre>
 *   $global.sagaforge_bwait_&lt;quest&gt;_&lt;node&gt;_done    the step it fired at ("*" = once per run)
 * </pre>
 * Rules (docs/FORMAT.md, "MagicLib bounties"):
 * <ul>
 * <li>A wait with {@code stage} watches while the active quest is at that step. When the bounty has
 * ended (now, or before the quest got there: the keys stay) it fires the outcome's trigger once.
 * Leaving the step and coming back watches again.</li>
 * <li>A wait without {@code stage} watches while the quest is active and fires once per run.</li>
 * <li>An outcome without a trigger (unconnected) fires nothing; the quest stays where it is.</li>
 * <li>A quest that isn't active forgets its waits (a new run starts fresh).</li>
 * </ul>
 * Without MagicLib every bounty is "unavailable", so nothing fires. Play mode does the same
 * (tools/saga-forge/src/shared/play.ts, checkBountyWaits); golden case test/golden/bountywait.json.
 */
public final class BountyWaits {
    private BountyWaits() {}

    public static String doneKey(String quest, String node) { return "$global.sagaforge_bwait_" + quest + "_" + node + "_done"; }

    /** One quest's waits now; returns the triggers to fire. {@code owner}: the content mod (whether MagicLib counts for it). */
    public static List<String> tick(QuestDef q, Memory m, String owner) {
        List<String> fire = new ArrayList<String>();
        if (q.bountyWaits.isEmpty()) return fire;
        boolean active = QuestLogic.isActive(m, q.id);
        String step = QuestLogic.step(m, q.id);
        for (QuestDef.BountyWait w : q.bountyWaits) {
            String dk = doneKey(q.id, w.node);
            if (!active) {
                m.unset(dk);
                continue;
            }
            Object done = m.get(dk);
            if (done != null) {
                if (w.stage == null || Values.str(done).equals(step)) continue;
                m.unset(dk); // left the step since it fired: it may watch again
            }
            if (w.stage != null && !w.stage.equals(step)) continue;
            String trigger = w.trigger(Bounties.state(m, owner, w.bounty));
            if (trigger == null) continue; // not ended (or unavailable)
            m.set(dk, w.stage == null ? "*" : step);
            if (!trigger.isEmpty()) fire.add(trigger);
        }
        return fire;
    }
}
