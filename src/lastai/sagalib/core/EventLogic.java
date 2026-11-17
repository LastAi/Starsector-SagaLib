package lastai.sagalib.core;

import lastai.sagalib.model.EventsDef;
import lastai.sagalib.model.PoolDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The event scheduler (SagaLib 1.2, docs/FORMAT.md "World events"), pure so it can be tested.
 * All state is in global memory (nothing saved by class name):
 * <ul>
 *   <li>{@code $global.sagaforge_ev_<scheduler>_next}: the day of the next check;</li>
 *   <li>{@code $global.<event>_active}: true while the event runs (for conditions and dialog);</li>
 *   <li>{@code $global.sagaforge_ev_<event>_until}: the day it ends;</li>
 *   <li>the pool keys of {@link Pools} for once / max / cooldown (the scheduler is the pool).</li>
 * </ul>
 * Each tick ends the events whose time is up, and when a check is due starts one (weighted pick
 * among the events whose conditions hold and that aren't running or used up) if fewer than max
 * run. The next check is one interval later (×0.75–1.25), or a day later while fewer than min run.
 */
public final class EventLogic {
    private EventLogic() {}

    public static String nextKey(String scheduler) { return "$global.sagaforge_ev_" + scheduler + "_next"; }

    public static String activeKey(String event) { return "$global." + event + "_active"; }

    public static String untilKey(String event) { return "$global.sagaforge_ev_" + event + "_until"; }

    public static String startTrigger(String event) { return "SF_event_" + event + "_start"; }

    public static String endTrigger(String event) { return "SF_event_" + event + "_end"; }

    /** What a tick did: an event started or ended. */
    public static final class Change {
        public final String event;
        public final boolean start;

        Change(String event, boolean start) {
            this.event = event;
            this.start = start;
        }

        @Override
        public String toString() { return (start ? "start " : "end ") + event; }
    }

    public static boolean running(Memory m, String event) { return Values.truthy(m.get(activeKey(event))); }

    /** The end days of an event's runs (one, or several for an event that may run several at once). */
    static List<Float> ends(Memory m, String event) {
        List<Float> out = new ArrayList<Float>();
        Object v = m.get(untilKey(event));
        if (v instanceof Number) { out.add(((Number) v).floatValue()); return out; }
        if (v == null) return out;
        for (String part : String.valueOf(v).split(";")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            try { out.add(Float.parseFloat(t)); } catch (NumberFormatException ignored) { /* skip */ }
        }
        return out;
    }

    private static void setEnds(Memory m, String event, List<Float> ends) {
        if (ends.isEmpty()) {
            m.unset(untilKey(event));
            m.unset(activeKey(event));
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Float f : ends) { if (b.length() > 0) b.append(';'); b.append(f); }
        m.set(untilKey(event), b.toString());
        m.set(activeKey(event), true);
    }

    /** Runs going on now (an event that runs several at once counts each run). */
    public static int runningCount(EventsDef d, Memory m) {
        int n = 0;
        for (String id : d.events.keySet()) if (running(m, id)) n += Math.max(1, ends(m, id).size());
        return n;
    }

    /**
     * One tick at day {@code now}. {@code seed}: the world seed (picks are stable for a save);
     * {@code force}: an event id to start at the next check (dev mode), or null.
     */
    public static List<Change> tick(EventsDef d, Memory m, Conditions cond, long seed, double now, String force) {
        List<Change> out = new ArrayList<Change>();
        // ends first, so a slot is free for this check
        for (EventsDef.Event e : d.events.values()) {
            if (!running(m, e.id)) continue;
            List<Float> ends = ends(m, e.id), left = new ArrayList<Float>();
            for (Float end : ends) {
                if (now >= end) out.add(new Change(e.id, false));
                else left.add(end);
            }
            if (ends.isEmpty()) out.add(new Change(e.id, false)); // a flag without end days: end it
            if (left.size() != ends.size() || ends.isEmpty()) setEnds(m, e.id, left);
        }
        Object next = m.get(nextKey(d.id));
        Random r = new Random(Pools.scramble(Pools.mix(seed, d.id) + (long) Math.floor(now * 4)));
        if (next == null) {
            m.set(nextKey(d.id), (float) (now + (d.firstAfterDays > 0 ? d.firstAfterDays : interval(d, r))));
            return out;
        }
        if (now < num(next)) return out;
        int running = runningCount(d, m);
        if (running < d.max) {
            // the picker sees the events that aren't running, and those that may run several at once
            PoolDef view = new PoolDef();
            view.id = d.pool.id;
            view.nothing = 0;
            for (PoolDef.Entry en : d.pool.entries) if (canStart(d, m, en.id)) view.entries.add(en);
            String pick = Pools.roll(view, m, cond, seed, false, force != null && d.events.containsKey(force) && canStart(d, m, force) ? force : null);
            if (!Pools.NOTHING.equals(pick)) {
                EventsDef.Event e = d.events.get(pick);
                List<Float> ends = ends(m, pick);
                ends.add((float) (now + (e == null ? 0 : e.durationDays)));
                setEnds(m, pick, ends);
                out.add(new Change(pick, true));
                running++;
            }
        }
        m.set(nextKey(d.id), (float) (now + (running < d.min ? 1.0 : interval(d, r))));
        return out;
    }

    /** Not running, or allowed to run several at once ({@code "stack": true}). */
    static boolean canStart(EventsDef d, Memory m, String event) {
        EventsDef.Event e = d.events.get(event);
        return !running(m, event) || (e != null && e.stack);
    }

    private static double interval(EventsDef d, Random r) { return d.intervalDays * (0.75 + 0.5 * r.nextDouble()); }

    private static float num(Object o) {
        if (o instanceof Number) return ((Number) o).floatValue();
        try { return o == null ? 0f : Float.parseFloat(String.valueOf(o)); } catch (NumberFormatException e) { return 0f; }
    }
}
