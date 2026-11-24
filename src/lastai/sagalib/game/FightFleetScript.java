package lastai.sagalib.game;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/**
 * SagaLib 1.4 (SF_Fight): keeps a damaged fight fleet the player left behind going (its CR and
 * repairs advance), parked far away and not in any location's fleet list, like vanilla's salvage
 * FleetAdvanceScript. Unlike vanilla's, it stops once the place's memory no longer holds this fleet
 * under its key (the fight was won, or the key expired after 10 days), so it doesn't run forever and
 * never touches another fleet's script. Saved with the entity: never rename the class or its fields.
 */
public class FightFleetScript implements EveryFrameScript {
    private CampaignFleetAPI fleet;
    private SectorEntityToken entity;
    private String key;
    private boolean done;

    public FightFleetScript(CampaignFleetAPI fleet, SectorEntityToken entity, String key) {
        this.fleet = fleet;
        this.entity = entity;
        this.key = key;
    }

    public boolean isFor(CampaignFleetAPI f) { return f != null && f == fleet; }

    public void advance(float amount) {
        if (done) return;
        if (fleet == null || entity == null || key == null || entity.getMemoryWithoutUpdate().get(key) != fleet) {
            done = true;
            return;
        }
        fleet.advance(amount);
    }

    public boolean isDone() { return done; }

    public boolean runWhilePaused() { return false; }
}
