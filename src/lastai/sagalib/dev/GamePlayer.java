package lastai.sagalib.dev;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.FactionCommissionIntel;
import com.fs.starfarer.api.util.Misc;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Profiles;
import lastai.sagalib.game.Items;

/** The player's side of a test profile in game. */
final class GamePlayer implements Profiles.Player {
    private final boolean newGame;

    GamePlayer(boolean newGame) { this.newGame = newGame; }

    @Override
    public void setCredits(double credits) {
        Global.getSector().getPlayerFleet().getCargo().getCredits().set((float) credits);
    }

    @Override
    public void setStoryPoints(int points) { Global.getSector().getPlayerStats().setStoryPoints(points); }

    @Override
    public void setCommission(String faction) {
        FactionAPI f = Global.getSector().getFaction(faction);
        if (f == null) {
            SagaLog.error("profile: unknown commission faction \"" + faction + "\"");
            return;
        }
        if (faction.equals(Misc.getCommissionFactionId())) return;
        FactionCommissionIntel old = Misc.getCommissionIntel();
        if (old != null) old.endMission();
        // vanilla's way (rulecmd.missions.Commission accept)
        new FactionCommissionIntel(f).missionAccepted();
    }

    @Override
    public void setRep(String faction, float value) {
        if (Global.getSector().getFaction(faction) == null) {
            SagaLog.error("profile: unknown faction \"" + faction + "\"");
            return;
        }
        Global.getSector().getFaction(Factions.PLAYER).setRelationship(faction, value);
    }

    @Override
    public void addCargo(String item, int qty) { Items.change(Global.getSector().getPlayerFleet().getCargo(), item, qty, null); }

    @Override
    public void setGender(String gender) {
        if ("MALE".equals(gender)) Global.getSector().getPlayerPerson().setGender(Gender.MALE);
        else if ("FEMALE".equals(gender)) Global.getSector().getPlayerPerson().setGender(Gender.FEMALE);
    }

    @Override
    public void startAt(String entityId) {
        if (!newGame) {
            SagaLog.info("profile: startAt " + entityId + " is only used at new game");
            return;
        }
        SectorEntityToken target = Global.getSector().getEntityById(entityId);
        if (target == null && Global.getSector().getEconomy().getMarket(entityId) != null) {
            target = Global.getSector().getEconomy().getMarket(entityId).getPrimaryEntity();
        }
        if (target == null) {
            SagaLog.error("profile: startAt \"" + entityId + "\" is not an entity or market");
            return;
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        LocationAPI from = fleet.getContainingLocation();
        LocationAPI to = target.getContainingLocation();
        if (from != to) {
            if (from != null) from.removeEntity(fleet);
            to.addEntity(fleet);
            Global.getSector().setCurrentLocation(to);
        }
        fleet.setLocation(target.getLocation().x, target.getLocation().y + target.getRadius() + 100f);
        SagaLog.info("profile: player fleet moved to " + target.getName());
    }
}
