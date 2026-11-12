package lastai.sagalib.test;

import lastai.sagalib.core.Conditions;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.QuestLogic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Condition lines: memory, SF_ conditions, other commands through the resolver. */
public final class ConditionTests {
    public static void memory() {
        MapMemory m = new MapMemory();
        Conditions c = Fx.conditions(m);
        T.no(c.eval("$global.flag"), "unset flag");
        T.yes(c.eval("!$global.flag"), "negated unset flag");
        m.set("$global.flag", true);
        T.yes(c.eval("$global.flag"), "set flag");
        m.set("$global.n", 3f);
        T.yes(c.eval("$global.n >= 3"), ">=");
        T.no(c.eval("$global.n > 3"), ">");
        T.yes(c.eval("$global.n != 4"), "!=");
        m.set("$global.other", 3f);
        T.yes(c.eval("$global.n == $global.other"), "compare with another key");
        T.yes(c.eval("score:100"), "score lines always hold");
        T.yes(c.eval("$global.n == 3 score:10"), "trailing score");
        T.yes(c.all(Arrays.asList("$global.flag", "$global.n == 3")), "all");
        T.no(c.all(Arrays.asList("$global.flag", "$global.n == 4")), "not all");
    }

    public static void sagaConditions() {
        MapMemory m = new MapMemory();
        Conditions c = Fx.conditions(m);
        T.yes(c.eval("SF_QuestState vgc_q notStarted"), "not started");
        T.yes(c.eval("SF_QuestState vgc_q not-started"), "the editor's spelling");
        T.no(c.eval("SF_AtStep vgc_q find"), "not at a step");
        m.set(QuestLogic.stateKey("vgc_q"), "active");
        m.set(QuestLogic.stepKey("vgc_q"), "find");
        T.yes(c.eval("SF_AtStep vgc_q find"), "at step");
        T.yes(c.eval("!SF_AtStep vgc_q return"), "negated");
        T.no(c.eval("SF_QuestDone vgc_q"), "not done");
        m.set(QuestLogic.stateKey("vgc_q"), "fail");
        T.no(c.eval("SF_AtStep vgc_q find"), "an ended quest is at no step");
        T.yes(c.eval("SF_QuestDone vgc_q"), "done (any)");
        T.yes(c.eval("SF_QuestDone vgc_q any"), "done any");
        T.yes(c.eval("SF_QuestDone vgc_q fail"), "done fail");
        T.no(c.eval("SF_QuestDone vgc_q success"), "not success");
        T.yes(c.eval("SF_QuestState vgc_q fail"), "state");
    }

    public static void otherCommandsGoToTheResolver() {
        MapMemory m = new MapMemory();
        final List<String> seen = new ArrayList<String>();
        Conditions c = new Conditions(m, new Conditions.Commands() {
            @Override
            public Boolean eval(String name, List<String> args, String body) {
                seen.add(body);
                if ("RepIsAtWorst".equals(name)) return "FAVORABLE".equals(args.get(1));
                return null;
            }
        });
        T.yes(c.eval("RepIsAtWorst hegemony FAVORABLE"), "resolver true");
        T.yes(c.eval("!RepIsAtWorst hegemony HOSTILE"), "negated resolver");
        T.no(c.eval("NoSuchCommand x"), "unknown command is false");
        T.yes(c.eval("!NoSuchCommand x"), "negated unknown is true");
        T.eq(Arrays.asList("RepIsAtWorst hegemony FAVORABLE", "RepIsAtWorst hegemony HOSTILE", "NoSuchCommand x", "NoSuchCommand x"), seen, "bodies without !");
        seen.clear();
        c.eval("SF_QuestDone vgc_q");
        T.eq(0, seen.size(), "SF_ conditions never reach the resolver");
    }
}
