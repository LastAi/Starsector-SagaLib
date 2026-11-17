package lastai.sagalib.test;

import lastai.sagalib.core.EncounterLogic;
import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.Problems;
import lastai.sagalib.core.QuestLogic;
import lastai.sagalib.core.TimerLogic;
import lastai.sagalib.core.Triggers;
import lastai.sagalib.core.Values;
import lastai.sagalib.model.EncounterDef;
import lastai.sagalib.model.QuestDef;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Encounters, quest timers and the fire-trigger dialog rules (the pure parts). */
public final class EncounterTests {
    static EncounterDef enc(String singleQuoted) throws Exception {
        Problems p = new Problems();
        EncounterDef e = EncounterDef.parse(Fx.json(singleQuoted), new JsonReader("enc.json", p));
        T.yes(p.isEmpty(), "fixture: " + p.all());
        return e;
    }

    static String spawn(String extra) {
        return "{'format':1,'id':'amb','fleet':{'faction':'pirates','size':'fp','points':80},'spawn':{'at':'belt'" + extra + "},'behavior':'ambush'}";
    }

    static void at(MapMemory m, String q, String step) {
        m.set(QuestLogic.stateKey(q), "active");
        m.set(QuestLogic.stepKey(q), step);
    }

    public static void questStageSpawns() throws Exception {
        EncounterDef e = enc(spawn(",'when':'quest-stage','quest':'vgc_q','stage':'find','despawnOnStageEnd':true"));
        MapMemory m = new MapMemory();
        T.eq(EncounterLogic.Action.NONE, EncounterLogic.decide(e, m, 0), "quest not started");
        at(m, "vgc_q", "find");
        T.eq(EncounterLogic.Action.SPAWN, EncounterLogic.decide(e, m, 0), "quest at the stage");
        EncounterLogic.spawned(m, e, "vgc_q", "find");
        T.eq(EncounterLogic.Action.NONE, EncounterLogic.decide(e, m, 1), "spawned, still at the stage");
        m.set(QuestLogic.stepKey("vgc_q"), "return");
        T.eq(EncounterLogic.Action.DESPAWN, EncounterLogic.decide(e, m, 2), "stage ended: leaves");
        EncounterLogic.resolve(m, e, Fx.map(), false, 2);
        T.eq("avoided", EncounterLogic.state(m, "amb"), "avoided");
        T.eq(EncounterLogic.Action.NONE, EncounterLogic.decide(e, m, 3), "not at the stage");
        T.eq(null, EncounterLogic.state(m, "amb"), "leaving the stage resets it");
        m.set(QuestLogic.stepKey("vgc_q"), "find");
        T.eq(EncounterLogic.Action.SPAWN, EncounterLogic.decide(e, m, 4), "back at the stage: spawns again");
        EncounterLogic.spawned(m, e, "vgc_q", "find");
        EncounterLogic.resolve(m, e, Fx.map(), true, 5);
        T.eq(EncounterLogic.Action.NONE, EncounterLogic.decide(e, m, 6), "beaten: no respawn while at the stage");
    }

    /** SagaLib 1.1: a named fleet's ship names, flagship, template and captain rule are read; defeats are counted. */
    public static void namedFleet() throws Exception {
        EncounterDef e = enc("{'format':1,'id':'amb','fleet':{'faction':'pirates','size':'explicit','variants':['wolf_Assault','hound_Standard'],"
                + "'shipNames':['Thirty Days',''],'flagship':0,'template':'sfw_butters','captainDiesOnDefeat':true,'flagshipCommander':'sfw_butters'},"
                + "'spawn':{'at':'belt'},'behavior':'ambush'}");
        T.eq(Arrays.asList("Thirty Days", ""), e.shipNames, "ship names");
        T.eq(0, e.flagship, "flagship index");
        T.eq("sfw_butters", e.template, "template");
        T.yes(e.captainDiesOnDefeat, "captain dies on defeat");
        MapMemory m = new MapMemory();
        EncounterLogic.countDefeat(m, "sfw_butters");
        EncounterLogic.countDefeat(m, "sfw_butters");
        T.eq(2.0, Values.number(m.get(EncounterLogic.defeatsKey("sfw_butters"))), "two defeats");
    }

    /** SagaLib 1.1: spawn when the player is near, once; with a quest stage, per visit to it. */
    public static void playerNearSpawns() throws Exception {
        EncounterDef e = enc(spawn(",'when':'player-near','range':1500"));
        T.eq(1500f, e.range, "range read");
        MapMemory m = new MapMemory();
        T.eq(EncounterLogic.Action.NONE, EncounterLogic.decide(e, m, 0, Fx.map(), false), "player far: nothing");
        T.eq(EncounterLogic.Action.SPAWN, EncounterLogic.decide(e, m, 0, Fx.map(), true), "player near: spawn");
        EncounterLogic.spawned(m, e, null, null);
        EncounterLogic.resolve(m, e, Fx.map(), true, 1);
        T.eq(EncounterLogic.Action.NONE, EncounterLogic.decide(e, m, 2, Fx.map(), true), "only once");

        EncounterDef q = enc(spawn(",'when':'player-near','quest':'vgc_q','stage':'find'"));
        MapMemory m2 = new MapMemory();
        T.eq(EncounterLogic.Action.NONE, EncounterLogic.decide(q, m2, 0, Fx.map(), true), "near, but the quest isn't at the stage");
        at(m2, "vgc_q", "find");
        T.eq(EncounterLogic.Action.SPAWN, EncounterLogic.decide(q, m2, 0, Fx.map(), true), "near and at the stage");
        EncounterLogic.spawned(m2, q, "vgc_q", "find");
        EncounterLogic.resolve(m2, q, Fx.map(), false, 1);
        m2.set(QuestLogic.stepKey("vgc_q"), "other");
        EncounterLogic.decide(q, m2, 2, Fx.map(), false);
        m2.set(QuestLogic.stepKey("vgc_q"), "find");
        T.eq(EncounterLogic.Action.SPAWN, EncounterLogic.decide(q, m2, 3, Fx.map(), true), "a later visit to the stage: again");
    }

    public static void spawnedBySFSpawnOnly() throws Exception {
        EncounterDef e = enc(spawn(",'when':'quest-stage','despawnOnStageEnd':true"));
        MapMemory m = new MapMemory();
        at(m, "vgc_q", "search");
        T.eq(EncounterLogic.Action.NONE, EncounterLogic.decide(e, m, 0), "no spawn.quest: only SF_Spawn");
        EncounterLogic.spawned(m, e, "vgc_q", "search"); // SF_Spawn records the quest's step
        T.eq(EncounterLogic.Action.NONE, EncounterLogic.decide(e, m, 0), "still at search");
        m.set(QuestLogic.stateKey("vgc_q"), "success");
        T.eq(EncounterLogic.Action.DESPAWN, EncounterLogic.decide(e, m, 0), "the quest ended: leaves");
        EncounterDef keep = enc(spawn(",'when':'quest-stage'"));
        EncounterLogic.spawned(m, keep, "vgc_q", "search");
        T.eq(EncounterLogic.Action.NONE, EncounterLogic.decide(keep, m, 0), "without despawnOnStageEnd it stays");
    }

    public static void renamedStageKeepsTheFleet() throws Exception {
        // found in game, 2026-11-11: a save at "search" loaded after the rename search → find; the
        // ambush (spawned at "search") left on load and its outcome fired while the game was loading
        EncounterDef e = enc(spawn(",'when':'quest-stage','despawnOnStageEnd':true"));
        QuestDef q = Fx.quest("{'format':1,'id':'vgc_q','stages':[{'id':'find','renamedFrom':['search']},{'id':'return'}]}");
        Map<String, QuestDef> quests = Collections.singletonMap("vgc_q", q);
        MapMemory m = new MapMemory();
        at(m, "vgc_q", "search");
        EncounterLogic.spawned(m, e, "vgc_q", "search");
        m.set(QuestLogic.stepKey("vgc_q"), "find"); // the reconciler moved the save
        T.eq(EncounterLogic.Action.NONE, EncounterLogic.decide(e, m, 1, quests), "renamed stage: the fleet stays");
        T.eq("find", m.get(EncounterLogic.stageKey("amb")), "its record follows the rename");
        m.set(QuestLogic.stepKey("vgc_q"), "return");
        T.eq(EncounterLogic.Action.DESPAWN, EncounterLogic.decide(e, m, 2, quests), "a real stage change still makes it leave");
        // without the quest definitions it can't know about renames
        EncounterLogic.spawned(m, e, "vgc_q", "search");
        m.set(QuestLogic.stepKey("vgc_q"), "find");
        T.eq(EncounterLogic.Action.DESPAWN, EncounterLogic.decide(e, m, 3), "no quests given: different id = left");
    }

    public static void newGameAndPeriodic() throws Exception {
        EncounterDef once = enc(spawn(",'when':'new-game'"));
        MapMemory m = new MapMemory();
        T.eq(EncounterLogic.Action.SPAWN, EncounterLogic.decide(once, m, 0), "new-game spawns");
        EncounterLogic.spawned(m, once, null, null);
        EncounterLogic.resolve(m, once, Fx.map(), true, 1);
        T.eq(EncounterLogic.Action.NONE, EncounterLogic.decide(once, m, 100), "only once");

        EncounterDef per = enc(spawn(",'when':'periodic','days':30"));
        m = new MapMemory();
        T.eq(EncounterLogic.Action.SPAWN, EncounterLogic.decide(per, m, 0), "periodic spawns at first");
        EncounterLogic.spawned(m, per, null, null);
        T.eq(EncounterLogic.Action.NONE, EncounterLogic.decide(per, m, 5), "one alive");
        EncounterLogic.resolve(m, per, Fx.map(), true, 10);
        T.eq(EncounterLogic.Action.NONE, EncounterLogic.decide(per, m, 39), "29 days after");
        T.eq(EncounterLogic.Action.SPAWN, EncounterLogic.decide(per, m, 40), "30 days after it was gone");
        EncounterLogic.failed(m, per, 50);
        T.eq(EncounterLogic.Action.NONE, EncounterLogic.decide(per, m, 51), "a failed spawn waits too");
    }

    public static void outcomes() throws Exception {
        QuestDef q = Fx.quest("{'format':1,'id':'vgc_q','encounters':[{'node':'n','encounter':'amb','defeated':'q_def','avoided':'q_av'}]}");
        Map<String, QuestDef> quests = Fx.map(q);
        EncounterDef e = enc(spawn(",'when':'quest-stage'"));
        MapMemory m = new MapMemory();
        T.eq(null, EncounterLogic.resolve(m, e, quests, true, 0), "not spawned: no outcome");
        EncounterLogic.spawned(m, e, null, null);
        T.eq("q_def", EncounterLogic.resolve(m, e, quests, true, 1), "the quest's defeated trigger");
        T.eq(null, EncounterLogic.resolve(m, e, quests, false, 2), "an outcome fires once");
        EncounterLogic.spawned(m, e, "vgc_q", "x");
        T.eq("q_av", EncounterLogic.resolve(m, e, quests, false, 3), "avoided");
        EncounterDef own = enc("{'format':1,'id':'amb','fleet':{'faction':'pirates'},'spawn':{'at':'belt'},'outcomes':{'defeated':'own_def'}}");
        EncounterLogic.spawned(m, own, null, null);
        T.eq("own_def", EncounterLogic.resolve(m, own, quests, true, 4), "the encounter's own outcome wins");
        EncounterLogic.spawned(m, own, null, null);
        T.eq("q_av", EncounterLogic.resolve(m, own, quests, false, 5), "falls back to the quest's for the other one");
        T.eq(q, EncounterLogic.questFor(e, quests), "questFor");
        T.eq("5", lastai.sagalib.core.Values.str(m.get(EncounterLogic.goneKey("amb"))), "gone day");
    }

    public static void readingErrors() throws Exception {
        Problems p = new Problems();
        JsonReader r = new JsonReader("e", p);
        T.eq(null, EncounterDef.parse(Fx.json("{'format':1,'id':'x','fleet':{'faction':'p','size':'explicit'},'spawn':{'at':'a'}}"), r), "explicit needs variants");
        T.yes(p.has("$.fleet.variants"), "path: " + p.all());
        T.eq(null, EncounterDef.parse(Fx.json("{'format':1,'id':'x','fleet':{'faction':'p'},'spawn':{'at':'a','when':'sometimes'}}"), r), "bad when");
        T.yes(p.has("$.spawn.when"), "when path");
        EncounterDef e = EncounterDef.parse(Fx.json("{'format':1,'id':'x','fleet':{'faction':'p'},'spawn':{'at':'a','quest':'q'},'behavior':'dance'}"), r);
        T.eq(null, e.quest, "quest without stage is dropped");
        T.eq("patrol", e.behavior, "unknown behavior falls back");
        T.yes(e.allowDisengage, "allowDisengage defaults to true");
    }

    // ------------------------------------------------------------------ timers

    static QuestDef timed(String timers) throws Exception {
        return Fx.quest("{'format':1,'id':'tq','stages':[{'id':'a'},{'id':'wait'},{'id':'b'}],'timers':" + timers + "}");
    }

    public static void timerAtItsStage() throws Exception {
        QuestDef q = timed("[{'node':'w','trigger':'tq_w','days':7,'stage':'wait'}]");
        MapMemory m = new MapMemory();
        T.eq(Collections.emptyList(), TimerLogic.tick(q, m, 0), "not started");
        at(m, "tq", "a");
        T.eq(Collections.emptyList(), TimerLogic.tick(q, m, 1), "not at its stage");
        m.set(QuestLogic.stepKey("tq"), "wait");
        T.eq(Collections.emptyList(), TimerLogic.tick(q, m, 2), "starts");
        T.eq(Collections.emptyList(), TimerLogic.tick(q, m, 8.9), "6.9 days");
        T.eq(Arrays.asList("tq_w"), TimerLogic.tick(q, m, 9), "7 days: fires");
        T.eq(Collections.emptyList(), TimerLogic.tick(q, m, 30), "fires once per visit");
        m.set(QuestLogic.stepKey("tq"), "b");
        TimerLogic.tick(q, m, 31);
        m.set(QuestLogic.stepKey("tq"), "wait");
        TimerLogic.tick(q, m, 32);
        T.eq(Arrays.asList("tq_w"), TimerLogic.tick(q, m, 39), "a new visit runs again");
    }

    public static void timerCancelledWhenTheStepChanges() throws Exception {
        QuestDef q = timed("[{'node':'w','trigger':'tq_w','days':7,'stage':'wait'}]");
        MapMemory m = new MapMemory();
        at(m, "tq", "wait");
        TimerLogic.tick(q, m, 0);
        m.set(QuestLogic.stepKey("tq"), "b");
        T.eq(Collections.emptyList(), TimerLogic.tick(q, m, 10), "left before the time: cancelled");
        T.eq(null, m.get(TimerLogic.stepKey("tq", "w")), "nothing waiting");
    }

    public static void timerWithoutStage() throws Exception {
        QuestDef q = timed("[{'trigger':'tq_t','days':3}]");
        T.eq("tq_t", q.timers.get(0).node, "node defaults to the trigger");
        MapMemory m = new MapMemory();
        at(m, "tq", "a");
        TimerLogic.tick(q, m, 0);
        T.eq("a", m.get(TimerLogic.stepKey("tq", "tq_t")), "waits at the step it started at");
        T.eq(Arrays.asList("tq_t"), TimerLogic.tick(q, m, 3), "fires");
        m.set(QuestLogic.stepKey("tq"), "b");
        TimerLogic.tick(q, m, 4);
        T.eq(Collections.emptyList(), TimerLogic.tick(q, m, 20), "once per quest run");
        m.set(QuestLogic.stateKey("tq"), "success");
        TimerLogic.tick(q, m, 21);
        T.eq(null, m.get(TimerLogic.doneKey("tq", "tq_t")), "an ended quest forgets its timers");
        at(m, "tq", "a");
        TimerLogic.tick(q, m, 22);
        T.eq(Arrays.asList("tq_t"), TimerLogic.tick(q, m, 25), "a new run waits again");
    }

    // ------------------------------------------------------------------ fire-trigger dialog

    public static void fireTriggerDialogRules() {
        T.eq(Triggers.After.NO_DIALOG, Triggers.before(false), "no matching rule: no dialog");
        T.eq(Triggers.After.KEEP, Triggers.before(true), "a rule matches: open it");
        T.eq(Triggers.After.DISMISS, Triggers.after(false, false, false), "nothing added: dismiss (an Action chain)");
        T.eq(Triggers.After.CONTINUE, Triggers.after(false, true, false), "text, no options: Continue");
        T.eq(Triggers.After.KEEP, Triggers.after(true, true, false), "options: keep");
        T.eq(Triggers.After.KEEP, Triggers.after(true, false, false), "options without text: keep");
        T.eq(Triggers.After.KEEP, Triggers.after(false, false, true), "the rules closed it themselves");
    }

    public static void triggerQueue() {
        MapMemory m = new MapMemory();
        T.eq(null, Triggers.poll(m), "empty");
        Triggers.add(m, "a");
        Triggers.add(m, " b ");
        Triggers.add(m, "");
        T.eq(Arrays.asList("a", "b"), Triggers.queued(m), "queued in order");
        T.eq("a", Triggers.poll(m), "first");
        Triggers.pushBack(m, "a");
        T.eq("a", Triggers.poll(m), "pushed back first");
        T.eq("b", Triggers.poll(m), "then b");
        T.eq(null, m.get(Triggers.QUEUE_KEY), "the key is removed when empty");
    }

    // ------------------------------------------------------------------ the example mod

    static JSONObject read(Path p) throws Exception { return new JSONObject(new String(Files.readAllBytes(p), StandardCharsets.UTF_8)); }

    public static void vanguardAmbushAndQuestLoad() throws Exception {
        Problems p = new Problems();
        EncounterDef e = EncounterDef.parse(read(SystemTests.VGC.resolve("data/sagaforge/encounters/vgc_belt_ambush.json")), new JsonReader("vgc_belt_ambush.json", p));
        QuestDef q = QuestDef.parse(read(SystemTests.VGC.resolve("data/sagaforge/quests/vgc_lost_convoy.json")), new JsonReader("vgc_lost_convoy.json", p));
        T.yes(p.isEmpty(), "no problems: " + p.all());
        T.eq("vgc_kestrel_belt", e.at, "spawn at the belt");
        T.yes(e.hostile && e.important && e.despawnOnStageEnd, "flags");
        T.eq("ambush", e.behavior, "behavior");
        Map<String, QuestDef> quests = Fx.map(q);
        T.eq(q, EncounterLogic.questFor(e, quests), "the quest that lists the ambush");
        // the quest's flow: SF_Step search, SF_Spawn; the bluff (SF_Despawn) fires avoided
        MapMemory m = new MapMemory();
        at(m, q.id, "search");
        EncounterLogic.spawned(m, e, q.id, "search");
        T.eq("vgc_lost_convoy_ambush_avoided", EncounterLogic.resolve(m, e, quests, false, 1), "avoided trigger from the quest");
        EncounterLogic.spawned(m, e, q.id, "search");
        T.eq("vgc_lost_convoy_ambush_defeated", EncounterLogic.resolve(m, e, quests, true, 2), "defeated trigger from the quest");
        List<String> none = TimerLogic.tick(q, m, 3);
        T.eq(0, none.size(), "no timers in this quest");
    }

    /** SagaLib 1.2: roaming fleets leave an interval apart, at most max on the way. */
    public static void roamingDepartures() throws Exception {
        lastai.sagalib.core.MapMemory m = new lastai.sagalib.core.MapMemory();
        java.util.Random r = new java.util.Random(5);
        T.no(lastai.sagalib.core.RoamLogic.due(m, "x", 0, 10f, 0, 2, r), "the first call only plans");
        int sent = 0, onTheWay = 0;
        for (int day = 1; day <= 100; day++) if (lastai.sagalib.core.RoamLogic.due(m, "x", day, 10f, onTheWay, 2, r)) { sent++; onTheWay = Math.min(2, onTheWay + 1); }
        T.eq(2, sent, "never more than max (2) on the way: " + sent);
        onTheWay = 0;
        int more = 0;
        for (int day = 101; day <= 200; day++) if (lastai.sagalib.core.RoamLogic.due(m, "x", day, 10f, onTheWay, 50, r)) more++;
        T.yes(more >= 7 && more <= 14, "about one every 10 days when there is room: " + more);
    }

    public static void roamingNeedsADestination() throws Exception {
        lastai.sagalib.core.Problems p = new lastai.sagalib.core.Problems();
        lastai.sagalib.model.EncounterDef e = lastai.sagalib.model.EncounterDef.parse(new org.json.JSONObject("{format:1,id:'r',fleet:{faction:'pirates'},spawn:{at:'a',when:'roaming',days:20}}"), new lastai.sagalib.core.JsonReader("t", p));
        T.yes(e == null && p.has("needs \"to\""), "roaming without to: " + p.all());
        p = new lastai.sagalib.core.Problems();
        e = lastai.sagalib.model.EncounterDef.parse(new org.json.JSONObject("{format:1,id:'r',fleet:{faction:'pirates'},spawn:{at:'a',when:'roaming',to:'b',days:20,max:3,stayDays:5,roundTrip:true}}"), new lastai.sagalib.core.JsonReader("t", p));
        T.yes(e != null && "b".equals(e.to) && e.max == 3 && e.stayDays == 5f && e.roundTrip, "fields read");
    }
}
