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
    /** SagaLib 1.4 (quiet ops): no dialog; credits, items, XP and story points show as campaign messages. */
    private final boolean messages;

    public GameEffects(TextPanelAPI text) { this(text, false); }

    private GameEffects(TextPanelAPI text, boolean messages) {
        this.text = text;
        this.messages = messages;
    }

    /**
     * SagaLib 1.4: outside any dialog, with what the player sees as vanilla's campaign messages
     * (lower left). Reputation already does that by itself without a text panel (CoreReputationPlugin).
     */
    public static GameEffects withMessages() { return new GameEffects(null, true); }

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
        if (messages && amount != 0) CampaignMessages.credits(amount);
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
    public void item(String item, int qty) {
        if (Items.change(cargo(), item, qty, text) && messages) CampaignMessages.item(item, qty);
    }

    @Override
    public void ship(String variant) {
        FleetMemberAPI m = Items.ship(variant);
        if (m == null) return;
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(m);
        if (text != null) AddShip.addShipGainText(m, text);
    }

    @Override
    public void xp(long xp) {
        // without a dialog: the game's own XP message (withMessage), as for XP gained in the campaign
        if (messages) Global.getSector().getPlayerStats().addXP(xp, null, true);
        else Global.getSector().getPlayerStats().addXP(xp, text);
    }

    @Override
    public void storyPoints(int points) {
        Global.getSector().getPlayerStats().addStoryPoints(points, text, false);
        if (messages) CampaignMessages.storyPoints(points);
    }

    /** Vanilla's salvage roller on one drop group; the items go to the player with "Gained …" lines. */
    @Override
    public void loot(String group, int chances, int value) {
        try {
            com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData d = new com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData();
            d.group = group;
            java.util.List<com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData> byValue = new java.util.ArrayList<com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData>();
            java.util.List<com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData> byChance = new java.util.ArrayList<com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData>();
            if (value > 0) { d.value = value; byValue.add(d); } else { d.chances = Math.max(1, chances); byChance.add(d); }
            CargoAPI got = com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity.generateSalvage(new java.util.Random(), 1f, 1f, 1f, 1f, byValue, byChance);
            for (com.fs.starfarer.api.campaign.CargoStackAPI st : got.getStacksCopy()) {
                cargo().addFromStack(st);
                if (text != null) AddRemoveCommodity.addStackGainText(st, text);
            }
        } catch (Throwable t) {
            SagaLog.error("reward loot from drop group \"" + group + "\" failed", t);
        }
    }
}
