package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.EventLogic;
import lastai.sagalib.core.Memory;
import lastai.sagalib.core.Pools;
import lastai.sagalib.core.Triggers;
import lastai.sagalib.model.EventsDef;

import java.util.Random;

/**
 * Runs the world event schedulers (SagaLib 1.2) from SagaLib's campaign script: the pure
 * {@link EventLogic} decides, this posts the news items and queues the start and end triggers
 * (the mod's rules for them run like quest timers, outside any dialog).
 */
public final class WorldEvents {
    private WorldEvents() {}

    /** Dev mode: a test profile can start a given event at the next tick. */
    public static String forceKey(String scheduler) { return "$global.sagaforge_ev_" + scheduler + "_force"; }

    public static void tick(EventsDef d, Memory mem, double now) {
        String force = null;
        if (Global.getSettings().isDevMode()) {
            Object f = mem.get(forceKey(d.id));
            if (f != null) {
                force = String.valueOf(f);
                mem.unset(forceKey(d.id));
                mem.set(EventLogic.nextKey(d.id), 0f); // check now
            }
        }
        Long s = Pools.storedSeed(mem);
        if (s == null) {
            s = new Random().nextLong();
            mem.set(Pools.SEED_KEY, Long.toString(s));
        }
        for (EventLogic.Change c : EventLogic.tick(d, mem, RuleCommands.conditions(null, null, GameMemory.defaultMap()), s, now, force)) {
            SagaLog.info("events " + d.id + ": " + c + (force != null && c.start ? " (forced)" : ""));
            if (c.start) {
                EventsDef.Event e = d.events.get(c.event);
                if (e != null && e.news != null) {
                    EventsDef.News n = e.news;
                    // one entry per event (asked in game, 8d): a new run replaces the older entry still listed,
                    // unless the event may run several at once
                    if (!e.stack) removeNews(c.event);
                    Global.getSector().getIntelManager().addIntel(new SagaNewsIntel(c.event, n.title, n.text, n.icon, n.tag, n.at, n.days));
                }
                Triggers.add(mem, EventLogic.startTrigger(c.event));
            } else {
                Triggers.add(mem, EventLogic.endTrigger(c.event));
            }
        }
    }

    private static void removeNews(String event) {
        for (com.fs.starfarer.api.campaign.comm.IntelInfoPlugin i : new java.util.ArrayList<com.fs.starfarer.api.campaign.comm.IntelInfoPlugin>(Global.getSector().getIntelManager().getIntel(SagaNewsIntel.class))) {
            if (event.equals(((SagaNewsIntel) i).getEvent())) Global.getSector().getIntelManager().removeIntel(i);
        }
    }
}
