package lastai.sagalib.test;

import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.QuestLogic;
import lastai.sagalib.core.Reconciler;
import lastai.sagalib.model.QuestDef;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Save reconciliation after the JSON changed. */
public final class ReconcileTests {
    public static void stepFoundOrRenamedOrGone() throws Exception {
        QuestDef q = Fx.basic(); // "return" has renamedFrom ["back"]
        MapMemory m = new MapMemory();
        m.set(QuestLogic.stateKey("vgc_q"), "active");
        m.set(QuestLogic.stepKey("vgc_q"), "find");
        T.eq(Collections.emptyList(), Reconciler.reconcile(m, Arrays.asList("vgc_q"), Fx.map(q)), "step found: nothing");

        m.set(QuestLogic.stepKey("vgc_q"), "back");
        List<String> log = Reconciler.reconcile(m, Arrays.asList("vgc_q"), Fx.map(q));
        T.eq("return", m.get(QuestLogic.stepKey("vgc_q")), "renamed");
        T.eq(Arrays.asList("vgc_q: step back is now return"), log, "log line");

        m.set(QuestLogic.stepKey("vgc_q"), "gone");
        log = Reconciler.reconcile(m, Arrays.asList("vgc_q"), Fx.map(q));
        T.eq(QuestLogic.CHANGED_STEP, m.get(QuestLogic.stepKey("vgc_q")), "gone");
        T.eq(Arrays.asList("vgc_q: step gone no longer exists; the quest can be abandoned"), log, "log line");
        T.eq(Collections.emptyList(), Reconciler.reconcile(m, Arrays.asList("vgc_q"), Fx.map(q)), "__changed stays");
        T.eq("active", m.get(QuestLogic.stateKey("vgc_q")), "still active");
    }

    public static void onlyActiveQuests() throws Exception {
        QuestDef q = Fx.basic();
        MapMemory m = new MapMemory();
        m.set(QuestLogic.stateKey("vgc_q"), "success");
        m.set(QuestLogic.stepKey("vgc_q"), "gone");
        T.eq(Collections.emptyList(), Reconciler.reconcile(m, Arrays.asList("vgc_q"), Fx.map(q)), "ended quests are left alone");
        T.eq("gone", m.get(QuestLogic.stepKey("vgc_q")), "untouched");
        m.set(QuestLogic.stateKey("vgc_q"), "active");
        m.unset(QuestLogic.stepKey("vgc_q"));
        T.eq(Collections.emptyList(), Reconciler.reconcile(m, Arrays.asList("vgc_q"), Fx.map(q)), "active without a step");
    }

    public static void questGone() throws Exception {
        MapMemory m = new MapMemory();
        m.set(QuestLogic.stateKey("old_q"), "active");
        m.set(QuestLogic.stepKey("old_q"), "x");
        m.set("$global.old_q_pay", 3f);
        List<String> log = Reconciler.reconcile(m, Arrays.asList("old_q"), Fx.map());
        T.eq(Arrays.asList("old_q: no longer in any mod; left as it is"), log, "log");
        T.eq("x", m.get(QuestLogic.stepKey("old_q")), "keys kept");
        T.eq(3f, m.get("$global.old_q_pay"), "variables kept");
    }
}
