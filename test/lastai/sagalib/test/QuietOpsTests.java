package lastai.sagalib.test;

import lastai.sagalib.core.Content;
import lastai.sagalib.core.EventLogic;
import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.Problems;
import lastai.sagalib.core.QuietOps;
import lastai.sagalib.model.EventsDef;
import lastai.sagalib.model.ProgressDef;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** SagaLib 1.4: world event and progress event actions run without a dialog (quiet ops). */
public final class QuietOpsTests {
    /** Records every host call, in order; "boom" in an id throws. */
    static final class Host implements QuietOps.Host {
        final List<String> log = new ArrayList<String>();

        void add(String s) {
            if (s.contains("boom")) throw new IllegalStateException("boom");
            log.add(s);
        }

        public void credits(long amount) { add("credits " + amount); }
        public void item(String id, String kind, int qty) { add("item " + id + " " + kind + " " + qty); }
        public void factionRep(String faction, double points) { add("rep " + faction + " " + points); }
        public void personRep(String person, double points) { add("personRep " + person + " " + points); }
        public void storyPoints(int points) { add("sp " + points); }
        public void xp(long xp) { add("xp " + xp); }
        public void spawn(String encounter) { add("spawn " + encounter); }
        public void despawn(String encounter) { add("despawn " + encounter); }
        public void placeEntity(String entity) { add("place " + entity); }
        public void removeEntity(String entity, boolean decivilize) { add("remove " + entity + " " + decivilize); }
        public void startQuest(String quest, String stage) { add("start " + quest + " " + stage); }
        public void progress(String event, int points, String text) { add("progress " + event + " " + points + " '" + text + "'"); }
        public void plugin(String className, Map<String, String> args) { add("plugin " + className + " " + new java.util.TreeMap<String, String>(args)); }
    }

    static List<QuietOps.Op> ops(String singleQuotedArray, Problems p) throws Exception {
        return QuietOps.parse(Fx.json("{'start':" + singleQuotedArray + "}"), "start", "$.entries[0]", new JsonReader("ev.json", p));
    }

    static final String ALL = "[{'op':'set','key':'$global.sed_flag','value':'true'},"
            + "{'op':'set','key':'$global.sed_n','value':'5','days':3},"
            + "{'op':'set','key':'$player.sed_name','value':'Hello there'},"
            + "{'op':'set','key':'$sed_local','value':'false'},"
            + "{'op':'set','key':'$global.sed_copy','value':'$global.sed_n'},"
            + "{'op':'unset','key':'$global.sed_old'},"
            + "{'op':'credits','amount':-100},"
            + "{'op':'item','id':'alpha_core','kind':'special','qty':2},"
            + "{'op':'item','id':'food','qty':-3},"
            + "{'op':'rep','faction':'hegemony','amount':5},"
            + "{'op':'rep','person':'hel_tyger','amount':-2.5},"
            + "{'op':'spawn','encounter':'sed_raid'},{'op':'despawn','encounter':'sed_raid'},"
            + "{'op':'placeEntity','entity':'sed_wreck'},{'op':'removeEntity','entity':'sed_base','decivilize':true},{'op':'removeEntity','entity':'sed_buoy'},"
            + "{'op':'startQuest','quest':'sed_q'},{'op':'startQuest','quest':'sed_q2','stage':'st1'},"
            + "{'op':'storyPoints','amount':1},{'op':'xp','amount':1000},"
            + "{'op':'progress','event':'sed_unrest','points':50,'text':'Riots'},{'op':'progress','event':'sed_unrest','points':-10},"
            + "{'op':'plugin','className':'data.scripts.X','args':{'k':'v','who':'$global.sed_name2','none':'$global.nothing','quest':'q'}}]";

    public static void parsesAndRunsEveryOp() throws Exception {
        Problems p = new Problems();
        List<QuietOps.Op> list = ops(ALL, p);
        T.yes(p.isEmpty(), "no problems: " + p.all());
        T.eq(23, list.size(), "every op");
        MapMemory m = new MapMemory();
        m.set("$global.sed_old", "x");
        m.set("$global.sed_name2", "Tyger");
        Host h = new Host();
        T.eq(23, QuietOps.run(list, m, h, "test"), "all ran");
        T.eq(Boolean.TRUE, m.get("$global.sed_flag"), "true is a boolean, as rules.csv");
        T.eq(5f, m.get("$global.sed_n"), "a number is a float");
        T.eq(3f, m.expires.get("$global.sed_n"), "days: expires");
        T.eq(null, m.expires.get("$global.sed_flag"), "no days: never expires");
        T.eq("Hello there", m.get("$player.sed_name"), "text");
        T.eq(Boolean.FALSE, m.get("$sed_local"), "a local key (the game: the player fleet's memory)");
        T.eq(5f, m.get("$global.sed_copy"), "$key copies another key's value");
        T.eq(null, m.get("$global.sed_old"), "unset");
        String[] expected = {
                "credits -100", "item alpha_core special 2", "item food commodity -3", "rep hegemony 5.0", "personRep hel_tyger -2.5",
                "spawn sed_raid", "despawn sed_raid", "place sed_wreck", "remove sed_base true", "remove sed_buoy false",
                "start sed_q null", "start sed_q2 st1", "sp 1", "xp 1000",
                "progress sed_unrest 50 'Riots'", "progress sed_unrest -10 ''",
                "plugin data.scripts.X {k=v, none=, who=Tyger}"};
        T.eq(java.util.Arrays.asList(expected), h.log, "host calls in order");
    }

    public static void aFailingOpDoesntStopTheOthers() throws Exception {
        Problems p = new Problems();
        List<QuietOps.Op> list = ops("[{'op':'credits','amount':10},{'op':'spawn','encounter':'boom'},{'op':'set','key':'$global.after','value':'1'}]", p);
        MapMemory m = new MapMemory();
        Host h = new Host();
        T.eq(2, QuietOps.run(list, m, h, "test"), "two of three ran");
        T.eq(1f, m.get("$global.after"), "the op after the failure ran");
        T.eq("[credits 10]", h.log.toString(), "credits ran");
    }

    public static void badOps() throws Exception {
        Problems p = new Problems();
        List<QuietOps.Op> list = ops("[{'op':'fly'},{'key':'$global.x'},{'op':'set'},{'op':'set','key':'global.x'},{'op':'credits'},"
                + "{'op':'rep','amount':5},{'op':'rep','faction':'a','person':'b','amount':1},{'op':'rep','person':'$person','amount':1},"
                + "{'op':'spawn'},{'op':'progress','event':'e'},{'op':'plugin','className':' '},'text',"
                + "{'op':'unset','key':'$global.ok'}]", p);
        T.eq(1, list.size(), "only the good one: " + p.all());
        T.eq(12, p.errors(), "an error each: " + p.all());
        T.yes(p.has("unknown op"), "unknown op");
        T.yes(p.has("needs a dialog"), "$person");

        p = new Problems();
        T.eq(null, QuietOps.parse(Fx.json("{'id':'x'}"), "start", "$", new JsonReader("t", p)), "missing: null (a rule, as before)");
        T.eq(0, QuietOps.parse(Fx.json("{'start':[]}"), "start", "$", new JsonReader("t", p)).size(), "empty list: nothing to run, no dialog");
        T.yes(p.isEmpty(), "no problems");
        QuietOps.parse(Fx.json("{'start':'x'}"), "start", "$", new JsonReader("t", p));
        T.yes(p.has("list of ops"), "not a list");

        p = new Problems();
        ops("[{'op':'set','key':'$entity.x','value':'1'},{'op':'item','id':'food','kind':'weapon'}]", p);
        T.eq(0, p.errors(), "warnings only: " + p.all());
        T.eq(2, p.all().size(), "$entity. outside a dialog, unknown kind");
    }

    public static void keysAndValues() {
        T.eq("global", QuietOps.scope("$global.x"), "global");
        T.eq("player", QuietOps.scope("$player.x"), "player");
        T.eq("faction", QuietOps.scope("$faction.x"), "faction (the player's)");
        T.eq("local", QuietOps.scope("$x"), "no prefix: local (the player fleet)");
        T.eq("local", QuietOps.scope("$entity.x"), "a dialog's prefix: local");
        MapMemory m = new MapMemory();
        T.eq(Boolean.TRUE, QuietOps.value(m, "true"), "true");
        T.eq(Boolean.FALSE, QuietOps.value(m, "false"), "false");
        T.eq(-2.5f, QuietOps.value(m, "-2.5"), "number");
        T.eq("a b", QuietOps.value(m, "\"a b\""), "quoted text");
        T.eq("word", QuietOps.value(m, "word"), "text");
        T.eq(null, QuietOps.value(m, "$global.none"), "an unset key: unset");
    }

    public static void eventsAndProgressHaveLists() throws Exception {
        Problems p = new Problems();
        EventsDef d = EventsDef.parse(new JSONObject("{format:1,id:'sed_news',entries:["
                + "{id:'sed_a',weight:1,start:[{op:'credits',amount:100}],end:[{op:'unset',key:'$global.x'}]},"
                + "{id:'sed_b',weight:1}]}"), new JsonReader("ev", p));
        T.yes(p.isEmpty(), "events: " + p.all());
        T.eq(1, d.events.get("sed_a").start.size(), "start ops");
        T.eq(1, d.events.get("sed_a").end.size(), "end ops");
        T.eq(null, d.events.get("sed_b").start, "no list: a rule");

        ProgressDef pd = ProgressDef.parse(new JSONObject("{format:1,id:'sed_unrest',max:1000,stages:["
                + "{id:'rumours',progress:300,actions:[{op:'xp',amount:10}]},"
                + "{id:'riots',progress:600,outcomes:[{id:'calm',actions:[{op:'credits',amount:5}]},{id:'burn'}]},"
                + "{id:'end',progress:900}]}"), new JsonReader("pe", p));
        T.yes(p.isEmpty(), "progress: " + p.all());

        Content c = Content.empty();
        c.events.put(d.id, d);
        c.progress.put(pd.id, pd);
        T.eq(1, c.quietOps(EventLogic.startTrigger("sed_a")).size(), "event start");
        T.eq("unset", c.quietOps(EventLogic.endTrigger("sed_a")).get(0).op, "event end");
        T.eq(null, c.quietOps(EventLogic.startTrigger("sed_b")), "event without a list: the trigger in a dialog");
        T.eq("xp", c.quietOps(EventLogic.progressTrigger("sed_unrest", "rumours", null)).get(0).op, "stage");
        T.eq("credits", c.quietOps(EventLogic.progressTrigger("sed_unrest", "riots", "calm")).get(0).op, "outcome");
        T.eq(null, c.quietOps(EventLogic.progressTrigger("sed_unrest", "riots", "burn")), "outcome without a list");
        T.eq(null, c.quietOps(EventLogic.progressTrigger("sed_unrest", "end", null)), "stage without a list");
        T.eq(null, c.quietOps("vgc_q_wait1"), "a quest timer: never quiet");
        T.eq("SF_progress_sed_unrest_riots_calm", EventLogic.progressTrigger("sed_unrest", "riots", "calm"), "trigger name unchanged");

        // a stage with outcomes runs the outcome's actions; its own are ignored (warned)
        p = new Problems();
        ProgressDef both = ProgressDef.parse(new JSONObject("{format:1,id:'x',stages:[{id:'s',progress:100,actions:[{op:'xp',amount:1}],outcomes:[{id:'o'}]}]}"), new JsonReader("pe", p));
        T.eq(0, p.errors(), "a warning");
        T.yes(p.has("outcomes"), "says why: " + p.all());
        T.eq(null, both.stages.get(0).actions, "ignored");
    }
}
