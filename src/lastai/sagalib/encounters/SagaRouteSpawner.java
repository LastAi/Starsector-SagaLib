package lastai.sagalib.encounters;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteFleetSpawner;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import lastai.sagalib.SagaLog;
import lastai.sagalib.model.EncounterDef;
import org.lwjgl.util.vector.Vector2f;

/**
 * Makes the real fleet of a roaming encounter's route when the player comes near (vanilla's
 * RouteManager calls it; SagaLib 1.2). Saved inside every route: this class name must never
 * change, and it has no fields. The route's custom data is the encounter id.
 */
public class SagaRouteSpawner implements RouteFleetSpawner {
    @Override
    public CampaignFleetAPI spawnFleet(RouteData route) {
        Object id = route.getCustom();
        EncounterDef e = id == null ? null : EncounterManager.def(String.valueOf(id));
        if (e == null) {
            SagaLog.warn("roaming route " + route.getSource() + ": its encounter \"" + id + "\" is gone (mod removed or renamed?); the route ends");
            return null; // ends the route
        }
        Vector2f hyper = route.getInterpolatedHyperLocation();
        CampaignFleetAPI f = EncounterManager.build(e, hyper);
        if (f == null) return null;
        EncounterManager.flags(e, f);
        f.addScript(new RouteFleetAssignmentAI(f, route)); // places the fleet on its route and moves it along
        SagaLog.info("roaming fleet " + e.id + " appears near the player");
        return f;
    }

    @Override
    public boolean shouldCancelRouteAfterDelayCheck(RouteData route) { return false; }

    @Override
    public boolean shouldRepeat(RouteData route) { return false; }

    @Override
    public void reportAboutToBeDespawnedByRouteManager(RouteData route) {}
}
