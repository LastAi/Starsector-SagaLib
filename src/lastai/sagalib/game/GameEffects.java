package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddShip;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Effects;

/** Rewards in game, each with vanilla's text line in the dialog ({@code text} may be null: no lines). */
public final class GameEffects implements Effects {
    private final TextPanelAPI text;

    public GameEffects(TextPanelAPI text) { this.text = text; }

    private static CargoAPI cargo() { return Global.getSector().getPlayerFleet().getCargo(); }

    @Override
    public void credits(long amount) {
        if (amount >= 0) {
            cargo().getCredits().add(amount);
            if (text != null) AddRemoveCommodity.addCreditsGainText((int) amount, text);
        } else {
            cargo().getCredits().subtract(-amount);
            if (text != null) AddRemoveCommodity.addCreditsLossText((int) -amount, text);
        }
    }

    private static CustomRepImpact impact(double points) {
        CustomRepImpact impact = new CustomRepImpact();
        impact.delta = (float) (points * 0.01);
        return impact;
    }

    @Override
    public void factionRep(String faction, double points) {
        if (Global.getSector().getFaction(faction) == null) {
            SagaLog.error("reward: unknown faction \"" + faction + "\"");
            return;
        }
        // the same call as vanilla's "AdjustRep <faction> <points>"
        Global.getSector().adjustPlayerReputation(new RepActionEnvelope(RepActions.CUSTOM, impact(points), null, text, true), faction);
    }

    @Override
    public void personRep(String person, double points) {
        PersonAPI p = Global.getSector().getImportantPeople().getPerson(person);
        if (p == null) {
            SagaLog.error("reward: unknown person \"" + person + "\"");
            return;
        }
        Global.getSector().adjustPlayerReputation(new RepActionEnvelope(RepActions.CUSTOM, impact(points), null, text, true), p);
    }

    @Override
    public void item(String item, int qty) { Items.change(cargo(), item, qty, text); }

    @Override
    public void ship(String variant) {
        FleetMemberAPI m = Items.ship(variant);
        if (m == null) return;
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(m);
        if (text != null) AddShip.addShipGainText(m, text);
    }

    @Override
    public void xp(long xp) { Global.getSector().getPlayerStats().addXP(xp, text); }

    @Override
    public void storyPoints(int points) { Global.getSector().getPlayerStats().addStoryPoints(points, text, false); }
}
