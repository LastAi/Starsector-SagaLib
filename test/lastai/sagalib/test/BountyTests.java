package lastai.sagalib.test;

import lastai.sagalib.core.Bounties;
import lastai.sagalib.core.Conditions;
import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.Mods;
import lastai.sagalib.core.Problems;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * SagaLib 1.3, task #59: MagicLib bounties from quests (the golden case bounties.json covers the
 * states and SF_StartBounty with the bridge there; these cover what it can't).
 */
public final class BountyTests {
    private static Bounties.Source fake(final String... known) {
        return new Bounties.Source() {
            public boolean ready() { return true; }
            public boolean known(String id) { return Arrays.asList(known).contains(id); }
            public String memKey(String id) { return null; }
        };
    }

    private static void enabled(final Set<String> on, Map<String, Mods.ModList> lists) {
        Mods.setup(new Mods.Enabled() { public boolean isEnabled(String id) { return on.contains(id); } }, lists);
    }

    private static void reset() {
        Mods.setup(new Mods.Enabled() { public boolean isEnabled(String id) { return false; } }, null);
        Bounties.setup(null);
    }

    public static void keysAreRulesNotation() {
        try {
            Bounties.setup(new Bounties.Source() {
                public boolean ready() { return true; }
                public boolean known(String id) { return true; }
                public String memKey(String id) { return "a".equals(id) ? "$HVB_a" : "b".equals(id) ? "$global.b_key" : "c".equals(id) ? "c_key" : null; }
            });
            T.eq("$global.HVB_a", Bounties.globalKey("a"), "MagicLib's $x is $global.x");
            T.eq("$global.b_key", Bounties.globalKey("b"), "already in rules notation");
            T.eq("$global.c_key", Bounties.globalKey("c"), "without a $");
            T.eq("$global.d", Bounties.globalKey("d"), "default: $ + id");
        } finally {
            reset();
        }
    }

    public static void withoutTheBridgeEverythingIsUnavailable() {
        try {
            enabled(new HashSet<String>(Collections.singletonList(Bounties.MAGICLIB)), null);
            Bounties.setup(null);
            MapMemory m = new MapMemory();
            T.eq(Bounties.UNAVAILABLE, Bounties.state(m, null, "x"), "no bridge");
            T.eq("this SagaLib was built without its MagicLib bridge", Bounties.cantStart(m, null, "x"), "and why");
            m.set("$global.x", Boolean.FALSE);
            T.eq(Bounties.UNAVAILABLE, Bounties.state(m, null, "x"), "even with MagicLib's key in the save");
            enabled(new HashSet<String>(), null);
            Bounties.setup(fake("x"));
            T.eq("MagicLib isn't enabled", Bounties.cantStart(m, null, "x"), "MagicLib off");
            T.eq(Bounties.UNAVAILABLE, Bounties.state(m, null, "x"), "MagicLib off: unavailable");
        } finally {
            reset();
        }
    }

    public static void magicLibAddedToARunningGameDoesNotCount() throws Exception {
        try {
            JsonReader r = new JsonReader("test", new Problems());
            r.mod = "tsa";
            Mods.ModList l = Mods.ModList.parse(new JSONObject("{'format':1,'mod':'tsa','prefix':'tsa_','mods':[{'id':'MagicLib','use':'optional'}]}"), r);
            Map<String, Mods.ModList> lists = new LinkedHashMap<String, Mods.ModList>();
            lists.put("tsa", l);
            Set<String> on = new HashSet<String>();
            enabled(on, lists);
            Bounties.setup(fake("tsa_b_x"));
            MapMemory m = new MapMemory();
            Mods.record(m, true); // new game without MagicLib
            on.add(Bounties.MAGICLIB); // added later
            T.eq(Bounties.UNAVAILABLE, Bounties.state(m, "tsa", "tsa_b_x"), "the start record says off");
            T.eq("MagicLib wasn't enabled when this game started", Bounties.cantStart(m, "tsa", "tsa_b_x"), "and why");
            MapMemory fresh = new MapMemory();
            Mods.record(fresh, true); // a new game with it
            T.eq(Bounties.OFFERED, Bounties.state(fresh, "tsa", "tsa_b_x"), "a new game with MagicLib");
            T.eq(null, Bounties.cantStart(fresh, "tsa", "tsa_b_x"), "may start");
            fresh.set("$global.tsa_b_x", Boolean.FALSE);
            T.eq("it is active already", Bounties.cantStart(fresh, "tsa", "tsa_b_x"), "once");
        } finally {
            reset();
        }
    }

    public static void conditionLines() {
        try {
            enabled(new HashSet<String>(Collections.singletonList(Bounties.MAGICLIB)), null);
            Bounties.setup(fake("x"));
            MapMemory m = new MapMemory();
            Conditions c = new Conditions(m, null);
            T.yes(c.eval("SF_BountyState x offered"), "offered");
            T.yes(c.eval("!SF_BountyState x active ended"), "negated, several states");
            T.no(c.eval("SF_BountyState x"), "a state is needed");
            T.no(c.eval("SF_BountyState x maybe"), "an unknown state word matches nothing");
            T.yes(Bounties.isStateWord("ended") && Bounties.isStateWord("dismissed") && !Bounties.isStateWord("completed"), "the words");
        } finally {
            reset();
        }
    }

    // ------------------------------------------------------------------ Wait nodes on a bounty's end

    private static lastai.sagalib.model.QuestDef waiting(String waits) throws Exception {
        return Fx.quest("{'format':1,'id':'bq','stages':[{'id':'take'},{'id':'hunt'},{'id':'after'}],'bountyWaits':" + waits + "}");
    }

    private static void at(MapMemory m, String step) {
        m.set(lastai.sagalib.core.QuestLogic.stateKey("bq"), "active");
        m.set(lastai.sagalib.core.QuestLogic.stepKey("bq"), step);
    }

    public static void bountyWaitsParse() throws Exception {
        lastai.sagalib.model.QuestDef q = waiting("[{'node':'w','bounty':' bq_b ','stage':'hunt','succeeded':'bq_w_succeeded','expired':'bq_w_expired'},{'bounty':''}]");
        T.eq(1, q.bountyWaits.size(), "a wait without a bounty is skipped");
        lastai.sagalib.model.QuestDef.BountyWait w = q.bountyWaits.get(0);
        T.eq("bq_b", w.bounty, "trimmed");
        T.eq("hunt", w.stage, "stage");
        T.eq("bq_w_succeeded", w.trigger(Bounties.SUCCEEDED), "succeeded");
        T.eq("", w.trigger(Bounties.FAILED), "unconnected: no trigger");
        T.eq(null, w.trigger(Bounties.ACTIVE), "not an end");
    }

    public static void aWaitWithoutAStageFiresOncePerRun() throws Exception {
        try {
            enabled(new HashSet<String>(Collections.singletonList(Bounties.MAGICLIB)), null);
            Bounties.setup(fake("bq_b"));
            lastai.sagalib.model.QuestDef q = waiting("[{'node':'w','bounty':'bq_b','failed':'bq_w_failed'}]");
            MapMemory m = new MapMemory();
            m.set("$global.bq_b", Boolean.TRUE);
            m.set("$global.bq_b_failed", Boolean.TRUE);
            T.eq(Collections.emptyList(), lastai.sagalib.core.BountyWaits.tick(q, m, null), "the quest isn't active");
            at(m, "take");
            T.eq(Arrays.asList("bq_w_failed"), lastai.sagalib.core.BountyWaits.tick(q, m, null), "any step: fires");
            at(m, "after");
            T.eq(Collections.emptyList(), lastai.sagalib.core.BountyWaits.tick(q, m, null), "once per run, even at another step");
            m.set(lastai.sagalib.core.QuestLogic.stateKey("bq"), "success");
            lastai.sagalib.core.BountyWaits.tick(q, m, null);
            T.eq(null, m.get(lastai.sagalib.core.BountyWaits.doneKey("bq", "w")), "an ended quest forgets its waits");
        } finally {
            reset();
        }
    }

    public static void withoutMagicLibAWaitNeverFires() throws Exception {
        try {
            enabled(new HashSet<String>(), null);
            Bounties.setup(fake("bq_b"));
            lastai.sagalib.model.QuestDef q = waiting("[{'node':'w','bounty':'bq_b','stage':'hunt','succeeded':'bq_w_succeeded'}]");
            MapMemory m = new MapMemory();
            m.set("$global.bq_b", Boolean.TRUE);
            m.set("$global.bq_b_succeeded", Boolean.TRUE);
            at(m, "hunt");
            T.eq(Collections.emptyList(), lastai.sagalib.core.BountyWaits.tick(q, m, null), "MagicLib off: unavailable, keeps waiting");
            T.eq(null, m.get(lastai.sagalib.core.BountyWaits.doneKey("bq", "w")), "and nothing is recorded");
        } finally {
            reset();
        }
    }
}
