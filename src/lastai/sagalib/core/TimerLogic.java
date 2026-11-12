package lastai.sagalib.core;

import lastai.sagalib.model.QuestDef;

import java.util.ArrayList;
import java.util.List;

/**
 * Quest timers (the quest JSON's "timers": a Wait node of N days). State lives in memory keys,
 * so it survives saves and JSON updates:
 * <pre>
 *   $global.sagaforge_timer_&lt;quest&gt;_&lt;node&gt;_step    the step the timer is waiting at
 *   $global.sagaforge_timer_&lt;quest&gt;_&lt;node&gt;_start   the day it started (SagaLib's day counter)
 *   $global.sagaforge_timer_&lt;quest&gt;_&lt;node&gt;_done    the step it fired or was cancelled at ("*" = never again)
 * </pre>
 * Rules (docs/FORMAT.md, "Quest timers"):
 * <ul>
 * <li>A timer with {@code stage} starts when the active quest is at that step, and fires after
 * {@code days} if the quest is still there. Leaving the step cancels it; coming back starts it again.</li>
 * <li>A timer without {@code stage} starts at the step the quest is at when it is first seen active
 * (in practice the step set just before the Wait node), runs once per quest run, and is cancelled
 * if the step changes first.</li>
 * <li>A quest that is no longer active forgets its timers (a new run starts fresh).</li>
 * </ul>
 */
public final class TimerLogic {
    private TimerLogic() {}

    static String base(String quest, String node) { return "$global.sagaforge_timer_" + quest + "_" + node; }

    public static String stepKey(String quest, String node) { return base(quest, node) + "_step"; }

    public static String startKey(String quest, String node) { return base(quest, node) + "_start"; }

    public static String doneKey(String quest, String node) { return base(quest, node) + "_done"; }

    /** Advances one quest's timers to day {@code now}; returns the triggers to fire. */
    public static List<String> tick(QuestDef q, Memory m, double now) {
        List<String> fire = new ArrayList<String>();
        boolean active = QuestLogic.isActive(m, q.id);
        String step = QuestLogic.step(m, q.id);
        for (QuestDef.Timer t : q.timers) {
            String sk = stepKey(q.id, t.node), stk = startKey(q.id, t.node), dk = doneKey(q.id, t.node);
            if (!active) {
                m.unset(sk);
                m.unset(stk);
                m.unset(dk);
                continue;
            }
            Object waitingAt = m.get(sk);
            if (waitingAt != null) {
                if (!Values.str(waitingAt).equals(step)) {
                    // the quest moved on before the time was up
                    m.unset(sk);
                    m.unset(stk);
                    m.set(dk, t.stage == null ? "*" : Values.str(waitingAt));
                    continue;
                }
                Double start = Values.number(m.get(stk));
                if (start == null || now - start >= t.days) {
                    fire.add(t.trigger);
                    m.unset(sk);
                    m.unset(stk);
                    m.set(dk, t.stage == null ? "*" : step);
                }
                continue;
            }
            if (step == null) continue;
            Object done = m.get(dk);
            if (t.stage != null) {
                if (done != null && !Values.str(done).equals(step)) {
                    m.unset(dk); // left the stage since it last ran: it may run again
                    done = null;
                }
                if (step.equals(t.stage) && done == null) start(m, sk, stk, step, now);
            } else if (done == null) {
                start(m, sk, stk, step, now);
            }
        }
        return fire;
    }

    private static void start(Memory m, String sk, String stk, String step, double now) {
        m.set(sk, step);
        m.set(stk, Float.valueOf((float) now));
    }
}
