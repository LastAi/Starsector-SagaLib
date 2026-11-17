package lastai.sagalib.encounters;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Memory;
import lastai.sagalib.core.RoamLogic;
import lastai.sagalib.game.Saga;
import lastai.sagalib.model.EncounterDef;

import java.util.Random;

/**
 * Roaming fleets (SagaLib 1.2, docs/FORMAT.md "Roaming fleets"): an encounter with
 * {@code spawn.when: "roaming"} is sent from its place to another on vanilla's routes
 * (RouteManager): the route moves on its own, and a real fleet exists only while the player is
 * near it (vanilla's rule: within about 1.6 light-years), as for traders and patrols. One route
 * source per encounter ({@code sagalib_roam_<id>}), at most {@code max} at a time.
 */
public final class RoamingFleets {
    private RoamingFleets() {}

    private static final SagaRouteSpawner SPAWNER = new SagaRouteSpawner();
    private static final Random RANDOM = new Random();

    public static String source(String encounter) { return "sagalib_roam_" + encounter; }

    public static void tick(Memory mem, double now) {
        RouteManager rm = RouteManager.getInstance();
        if (rm == null) return;
        for (EncounterDef e : Saga.content().encounters.values()) {
            if (!"roaming".equals(e.when)) continue;
            int onTheWay = rm.getNumRoutesFor(source(e.id));
            if (!RoamLogic.due(mem, e.id, now, e.days, onTheWay, e.max, RANDOM)) continue;
            try { send(e, rm); } catch (Throwable t) { SagaLog.errorOnce("roam:" + e.id, "roaming fleet " + e.id + " couldn't be sent", t); }
        }
    }

    static void send(EncounterDef e, RouteManager rm) {
        SectorEntityToken from = EncounterManager.place(e.at), to = EncounterManager.place(e.to);
        if (from == null || to == null) {
            SagaLog.warnOnce("roam-place:" + e.id, "roaming fleet " + e.id + " (" + e.source + "): " + (from == null ? "\"" + e.at + "\"" : "\"" + e.to + "\"") + " isn't an entity or market in this game");
            return;
        }
        MarketAPI market = from.getMarket();
        OptionalFleetData extra = new OptionalFleetData(market, e.faction);
        extra.fleetType = e.fleetType;
        RouteData r = rm.addRoute(source(e.id), market, Long.valueOf(RANDOM.nextLong()), extra, SPAWNER, e.id);
        r.addSegment(new RouteSegment(1, 1f, from)); // getting ready
        r.addSegment(new RouteSegment(2, from, to)); // travel (days from the hyperspace distance)
        r.addSegment(new RouteSegment(3, Math.max(0.1f, e.stayDays), to));
        if (e.roundTrip) {
            r.addSegment(new RouteSegment(4, to, from));
            r.addSegment(new RouteSegment(5, 1f, from));
        }
        SagaLog.info("roaming fleet " + e.id + " leaves " + from.getName() + " for " + to.getName() + (e.roundTrip ? " and back" : ""));
    }

}
