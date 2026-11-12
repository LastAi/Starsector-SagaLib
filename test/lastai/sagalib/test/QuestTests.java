package lastai.sagalib.test;

import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.QuestLogic;
import lastai.sagalib.model.QuestDef;

import java.util.Arrays;

/** Quest bookkeeping in memory keys. */
public final class QuestTests {
    public static void startSetsStateAndVariables() throws Exception {
        QuestDef q = Fx.basic();
        MapMemory m = new MapMemory();
        T.eq(QuestLogic.NOT_STARTED, QuestLogic.state(m, "vgc_q"), "not started");
        T.yes(QuestLogic.start(m, q, "vgc_q"), "started");
        T.eq("active", m.get("$global.vgc_q_state"), "state key");
        T.eq(1f, m.get("$global.vgc_q_pay"), "number variable");
        T.eq(true, m.get("$global.vgc_q_ok"), "bool variable");
        T.eq("Reyes", m.get("$global.vgc_q_who"), "string variable");
        T.eq(null, m.get("$global.vgc_q_step"), "no step yet");
        m.set("$global.vgc_q_pay", 5f);
        T.no(QuestLogic.start(m, q, "vgc_q"), "already active");
        T.eq(5f, m.get("$global.vgc_q_pay"), "variables keep their value");
    }

    public static void variablesAreOnlySetWhenUnset() throws Exception {
        QuestDef q = Fx.basic();
        MapMemory m = new MapMemory();
        m.set("$global.vgc_q_pay", 9f);
        QuestLogic.initVariables(m, q);
        T.eq(9f, m.get("$global.vgc_q_pay"), "kept");
        T.eq(0f, QuestLogic.parseInitial("number", "abc"), "unreadable number is 0");
        T.eq(false, QuestLogic.parseInitial("bool", "yes"), "bool is only \"true\"");
    }

    public static void steps() throws Exception {
        QuestDef q = Fx.basic();
        MapMemory m = new MapMemory();
        T.eq("normal", QuestLogic.step(m, q, "vgc_q", "find"), "normal stage");
        T.eq("active", m.get("$global.vgc_q_state"), "step starts the quest");
        T.eq("find", QuestLogic.step(m, "vgc_q"), "step key");
        T.eq("success", QuestLogic.step(m, q, "vgc_q", "won"), "success stage");
        T.eq("success", m.get("$global.vgc_q_state"), "ended");
        T.eq("won", m.get("$global.vgc_q_step"), "step kept");

        m = new MapMemory();
        T.eq("fail", QuestLogic.step(m, q, "vgc_q", "lost"), "fail stage");
        T.eq("fail", QuestLogic.state(m, "vgc_q"), "failed");

        m = new MapMemory();
        T.eq("normal", QuestLogic.step(m, q, "vgc_q", "nowhere"), "unknown stage still sets the step (as Play mode)");
        T.eq("nowhere", QuestLogic.step(m, "vgc_q"), "unknown step stored");
        T.eq("normal", QuestLogic.step(m, null, "other_q", "x"), "unknown quest");
        T.eq("active", QuestLogic.state(m, "other_q"), "unknown quest still started");
    }

    public static void endAndAbandon() throws Exception {
        QuestDef q = Fx.basic();
        MapMemory m = new MapMemory();
        QuestLogic.step(m, q, "vgc_q", "find");
        QuestLogic.end(m, "vgc_q", false);
        T.eq("fail", QuestLogic.state(m, "vgc_q"), "end fail");
        QuestLogic.end(m, "vgc_q", true);
        T.eq("success", QuestLogic.state(m, "vgc_q"), "end success");

        m = new MapMemory();
        QuestLogic.step(m, q, "vgc_q", "find");
        m.set("$global.other_flag", true);
        QuestLogic.abandon(m, q, "vgc_q");
        T.eq(QuestLogic.NOT_STARTED, QuestLogic.state(m, "vgc_q"), "abandoned = not started");
        T.eq(null, m.get("$global.vgc_q_step"), "step cleared");
        T.eq(null, m.get("$global.vgc_q_pay"), "variables cleared");
        T.eq(true, m.get("$global.other_flag"), "other keys untouched");
    }

    public static void rewards() throws Exception {
        QuestDef q = Fx.basic();
        Fx.RecordingEffects fx = new Fx.RecordingEffects();
        T.yes(QuestLogic.reward(q, q.id, "done", fx), "given");
        T.eq(Arrays.asList("credits 1000", "rep hegemony 5.0", "personRep vgc_reyes -3.0", "item food 2", "ship buffalo_Standard", "xp 500",
                "storyPoints 1"), fx.log, "every part, in Play mode's order");
        fx = new Fx.RecordingEffects();
        T.no(QuestLogic.reward(q, q.id, "nope", fx), "unknown reward");
        T.no(QuestLogic.reward(null, "gone", "done", fx), "unknown quest");
        T.eq(0, fx.log.size(), "nothing given");
        QuestDef empty = Fx.quest("{'format':1,'id':'e','rewards':[{'id':'r'}]}");
        QuestLogic.reward(empty, "e", "r", fx);
        T.eq(0, fx.log.size(), "zero parts are skipped");
    }
}
