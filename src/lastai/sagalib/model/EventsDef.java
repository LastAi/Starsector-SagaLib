package lastai.sagalib.model;

import lastai.sagalib.core.JsonReader;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An event scheduler (SagaLib 1.2, docs/FORMAT.md "World events"):
 * {@code data/sagaforge/events/<id>.json}, type {@code events}. Its events are the entries of a
 * pool (weight, conditions, once, max, cooldownDays: the same picker as SF_RollPool), each with a
 * duration and an optional news item.
 */
public final class EventsDef {
    /** One event: how long it runs, and its news item. */
    public static final class Event {
        public String id;
        public float durationDays;
        /** May start while it already runs (each run with its own news item and end). Default: one at a time. */
        public boolean stack;
        public News news;
        /** SagaLib 1.4: the start / end actions SagaLib runs itself (core.QuietOps); null = a rule on the trigger, as before. */
        public java.util.List<lastai.sagalib.core.QuietOps.Op> start;
        public java.util.List<lastai.sagalib.core.QuietOps.Op> end;
    }

    /** A news item in the intel screen (all texts are translatable). */
    public static final class News {
        public String title = "";
        public String text = "";
        public String icon = "";
        /** Intel tab tag, e.g. "Major events" (vanilla Tags.INTEL_MAJOR_EVENT). */
        public String tag = "Major events";
        /** An entity or market id shown on the map, or empty. */
        public String at = "";
        public float days = 30f;
    }

    public String id;
    public String name = "";
    public int min;
    public int max = 1;
    public float intervalDays = 30f;
    /** Days before the first check (0 = one interval). */
    public float firstAfterDays;
    /** The events as pool entries (the picker's input). */
    public PoolDef pool;
    public final Map<String, Event> events = new LinkedHashMap<String, Event>();
    public String source;
    /** SagaLib 1.3: only with / only without these mods (modsWith, modsWithout, modsAny). */
    public lastai.sagalib.core.ModGate gate = lastai.sagalib.core.ModGate.NONE;

    public static EventsDef parse(JSONObject o, JsonReader r) {
        if (!r.checkFormat(o)) return null;
        PoolDef pool = PoolDef.parse(o, r);
        if (pool == null) return null;
        EventsDef d = new EventsDef();
        d.id = pool.id;
        d.pool = pool;
        d.source = r.file;
        d.gate = lastai.sagalib.core.ModGate.parse(o, "$", r);
        d.name = r.str(o, "name", "$", false, d.id);
        Double mn = r.num(o, "min", "$", 0.0), mx = r.num(o, "max", "$", 1.0), iv = r.num(o, "intervalDays", "$", 30.0), fa = r.num(o, "firstAfterDays", "$", 0.0);
        d.min = mn == null ? 0 : Math.max(0, mn.intValue());
        d.max = mx == null ? 1 : Math.max(0, mx.intValue());
        if (d.min > d.max) {
            r.warn("$.min", "min (" + d.min + ") is more than max (" + d.max + "); max is used for both");
            d.min = d.max;
        }
        d.intervalDays = iv == null || iv <= 0 ? 30f : iv.floatValue();
        d.firstAfterDays = fa == null || fa < 0 ? 0f : fa.floatValue();
        for (JsonReader.Indexed e : r.objects(o, "entries", "$")) {
            String id = r.str(e.json, "id", e.path, true, null);
            if (id == null) continue;
            Event ev = new Event();
            ev.id = id;
            Double du = r.num(e.json, "durationDays", e.path, 0.0);
            ev.durationDays = du == null || du < 0 ? 0f : du.floatValue();
            ev.stack = e.json.optBoolean("stack", false);
            ev.start = lastai.sagalib.core.QuietOps.parse(e.json, "start", e.path, r);
            ev.end = lastai.sagalib.core.QuietOps.parse(e.json, "end", e.path, r);
            JSONObject n = r.obj(e.json, "news", e.path, false);
            if (n != null) {
                String np = JsonReader.at(e.path, "news");
                News news = new News();
                news.title = r.str(n, "title", np, false, "");
                news.text = r.str(n, "text", np, false, "");
                news.icon = r.str(n, "icon", np, false, "");
                news.tag = r.str(n, "tag", np, false, "Major events");
                news.at = r.str(n, "at", np, false, "");
                Double nd = r.num(n, "days", np, 30.0);
                news.days = nd == null || nd <= 0 ? 30f : nd.floatValue();
                if (news.title.isEmpty()) r.warn(JsonReader.at(np, "title"), "news item of event \"" + id + "\" has no title");
                ev.news = news;
            }
            d.events.put(id, ev);
        }
        if (d.events.isEmpty()) r.warn("$.entries", "event scheduler \"" + d.id + "\" has no events");
        return d;
    }
}
