package lastai.sagalib.test;

import lastai.sagalib.core.EventLogic;
import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.Pools;
import lastai.sagalib.core.Problems;
import lastai.sagalib.core.Values;
import lastai.sagalib.model.EventsDef;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** World event schedulers (SagaLib 1.2): checks, max/min, durations, once, conditions, force, news. */
public final class EventTests {
    static EventsDef def(String json) throws Exception {
        Problems p = new Problems();
        EventsDef d = EventsDef.parse(new JSONObject(json), new JsonReader("test", p));
        T.yes(p.isEmpty(), "events parse: " + p.all());
        return d;
    }

    static final String TWO = "{format:1,id:'sed_news',name:'News',min:0,max:1,intervalDays:10,firstAfterDays:2,entries:["
            + "{id:'sed_a',weight:1,durationDays:5,news:{title:'A happened',text:'Text A',tag:'Major events',at:'sed_station',days:20}},"
            + "{id:'sed_b',weight:1,durationDays:5,once:'playthrough'}]}";

    /** Runs day by day; returns "day:change" lines. */
    static List<String> run(EventsDef d, MapMemory m, int days, String force) {
        List<String> out = new ArrayList<String>();
        for (int day = 0; day <= days; day++) {
            m.set(Pools.DAYS_KEY, (float) day);
            for (EventLogic.Change c : EventLogic.tick(d, m, null, 99L, day, day == 0 ? force : null)) out.add(day + ":" + c);
        }
        return out;
    }

    public static void parsesNewsAndLimits() throws Exception {
        EventsDef d = def(TWO);
        T.eq(2, d.events.size(), "two events");
        T.eq("A happened", d.events.get("sed_a").news.title, "news title");
        T.eq("sed_station", d.events.get("sed_a").news.at, "news place");
        T.eq(20f, d.events.get("sed_a").news.days, "news days");
        T.yes(d.events.get("sed_b").news == null, "no news");
        T.eq("playthrough", d.pool.entry("sed_b").once, "once is a pool limit");
    }

    public static void firstCheckAfterFirstAfterDaysAndOneAtATime() throws Exception {
        EventsDef d = def(TWO);
        MapMemory m = new MapMemory();
        List<String> log = run(d, m, 60, null);
        T.yes(log.size() >= 4, "several starts and ends in 60 days: " + log);
        T.yes(log.get(0).startsWith("2:start "), "the first starts on day 2 (firstAfterDays): " + log);
        int running = 0, most = 0;
        for (String l : log) { running += l.contains(":start ") ? 1 : -1; most = Math.max(most, running); }
        T.eq(1, most, "never more than max (1) at a time: " + log);
        // a started event ends after its duration
        String first = log.get(0).substring(log.get(0).indexOf(' ') + 1);
        T.yes(log.contains("7:end " + first), "ends 5 days later: " + log);
        int b = 0;
        for (String l : log) if (l.endsWith("start sed_b")) b++;
        T.yes(b <= 1, "sed_b (once) starts at most once: " + log);
    }

    public static void runningEventsSetTheirFlag() throws Exception {
        EventsDef d = def(TWO);
        MapMemory m = new MapMemory();
        run(d, m, 2, null);
        int active = 0;
        for (String id : d.events.keySet()) if (Values.truthy(m.get(EventLogic.activeKey(id)))) active++;
        T.eq(1, active, "one $global.<event>_active after the first start");
    }

    public static void minFillsUpADayApart() throws Exception {
        EventsDef d = def("{format:1,id:'sed_min',min:2,max:3,intervalDays:30,firstAfterDays:1,entries:[{id:'x',durationDays:100},{id:'y',durationDays:100},{id:'z',durationDays:100}]}");
        List<String> log = run(d, new MapMemory(), 4, null);
        T.eq("[1:start x, 2:start y]", sortedEventsByDay(log, d), "below min, the next check is a day later; then an interval");
    }

    private static String sortedEventsByDay(List<String> log, EventsDef d) {
        // which event starts first is a weighted pick; replace the ids by the order they appear
        List<String> out = new ArrayList<String>();
        List<String> seen = new ArrayList<String>();
        for (String l : log) {
            String id = l.substring(l.lastIndexOf(' ') + 1);
            if (!seen.contains(id)) seen.add(id);
            out.add(l.substring(0, l.lastIndexOf(' ') + 1) + "xyz".charAt(seen.indexOf(id)));
        }
        return out.toString();
    }

    /** Seen in game (8d): an event's "Gained 100 credits" opened a Continue box; event actions run quietly. */
    public static void eventActionsRunQuietly() {
        T.eq(lastai.sagalib.core.Triggers.After.DISMISS, lastai.sagalib.core.Triggers.after("SF_event_sed_boom_start", false, true, false), "text without options: closed");
        T.eq(lastai.sagalib.core.Triggers.After.KEEP, lastai.sagalib.core.Triggers.after("SF_event_sed_boom_start", true, true, false), "with options: the dialog stays");
        T.eq(lastai.sagalib.core.Triggers.After.CONTINUE, lastai.sagalib.core.Triggers.after("vgc_q_timer", false, true, false), "quest timers still show Continue");
        T.eq(lastai.sagalib.core.Triggers.After.DISMISS, lastai.sagalib.core.Triggers.after("SF_progress_sed_unrest_rumours", false, true, false), "progress stages too");
    }

    /** Asked in game (8d): by default an event never runs twice at once; "stack" lets it. */
    public static void oneAtATimeUnlessStack() throws Exception {
        String base = "{format:1,id:'sed_s',max:3,intervalDays:1,firstAfterDays:1,entries:[{id:'only',durationDays:10%s}]}";
        List<String> one = run(def(String.format(base, "")), new MapMemory(), 6, null);
        T.eq("[1:start only]", one.toString(), "one run at a time");
        MapMemory m = new MapMemory();
        List<String> many = run(def(String.format(base, ",stack:true")), m, 6, null);
        T.eq(3, many.size(), "stacked up to max (3): " + many);
        T.yes(Values.truthy(m.get(EventLogic.activeKey("only"))), "its flag is set while any run");
        List<String> later = run(def(String.format(base, ",stack:true")), m, 20, null);
        int ends = 0;
        for (String l : later) if (l.contains("end only")) ends++;
        T.yes(ends >= 3, "each run ends on its own: " + later);
    }

    /** Progress events (SagaLib 1.2): stages in order, outcomes, factors, restartAt checks. */
    public static void progressDefinitions() throws Exception {
        Problems p = new Problems();
        lastai.sagalib.model.ProgressDef d = lastai.sagalib.model.ProgressDef.parse(new JSONObject("{format:1,id:'sed_unrest',name:'Unrest',max:1000,negative:true,restartAt:0,stages:[{id:'rumours',progress:300,title:'Rumours'},{id:'riots',progress:600,outcomes:[{id:'calm',weight:2},{id:'burn'}]},{id:'bad',progress:500},{id:'settled',progress:1000}],factors:[{id:'boom',desc:'A boom',points:100,conditions:['.sed_boom_active']}]}"), new JsonReader("t", p));
        T.eq(3, d.stages.size(), "a stage below the one before is skipped: " + p.all());
        T.yes(p.has("must be above the stage before"), "and reported");
        T.eq(2, d.stage("riots").outcomes.size(), "outcomes");
        T.eq(100, d.factor("boom").points, "factor points");
        T.eq(0, d.restartAt, "restartAt");
        T.yes(d.negative, "negative");
    }

    public static void conditionsAndForce() throws Exception {
        EventsDef d = def("{format:1,id:'sed_c',max:1,intervalDays:5,firstAfterDays:1,entries:[{id:'p',durationDays:1,conditions:['$global.ok']},{id:'q',durationDays:1}]}");
        MapMemory m = new MapMemory();
        lastai.sagalib.core.Conditions cond = new lastai.sagalib.core.Conditions(m, null);
        List<String> starts = new ArrayList<String>();
        for (int day = 0; day <= 40; day++) {
            m.set(Pools.DAYS_KEY, (float) day);
            for (EventLogic.Change c : EventLogic.tick(d, m, cond, 7L, day, null)) if (c.start) starts.add(c.event);
        }
        T.no(starts.contains("p"), "p needs $global.ok: " + starts);
        // dev mode force: the next check starts that event
        MapMemory m2 = new MapMemory();
        m2.set(EventLogic.nextKey("sed_c"), 0f);
        List<EventLogic.Change> ch = EventLogic.tick(d, m2, null, 7L, 3, "p");
        T.eq("[start p]", ch.toString(), "forced");
    }
}
