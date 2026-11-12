package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import lastai.sagalib.SagaLog;

/** Item ids as quests use them: a commodity, special item, weapon or fighter wing, found in that order. */
public final class Items {
    private Items() {}

    public enum Kind { COMMODITY, SPECIAL, WEAPON, FIGHTER }

    public static Kind kindOf(String id) {
        try {
            if (Global.getSettings().getCommoditySpec(id) != null) return Kind.COMMODITY;
        } catch (Throwable ignored) {
            // not a commodity
        }
        try {
            if (Global.getSettings().getSpecialItemSpec(id) != null) return Kind.SPECIAL;
        } catch (Throwable ignored) {
            // not a special item
        }
        try {
            if (Global.getSettings().getWeaponSpec(id) != null) return Kind.WEAPON;
        } catch (Throwable ignored) {
            // not a weapon
        }
        try {
            if (Global.getSettings().getFighterWingSpec(id) != null) return Kind.FIGHTER;
        } catch (Throwable ignored) {
            // not a wing
        }
        return null;
    }

    /** Adds (qty &gt; 0) or removes (qty &lt; 0) items, with vanilla's "Gained"/"Lost" line if {@code text} isn't null. False if the id is unknown. */
    public static boolean change(CargoAPI cargo, String id, int qty, TextPanelAPI text) {
        Kind k = kindOf(id);
        if (k == null) {
            SagaLog.error("unknown item \"" + id + "\" (not a commodity, special item, weapon or fighter wing)");
            return false;
        }
        if (qty == 0) return true;
        int n = Math.abs(qty);
        boolean add = qty > 0;
        switch (k) {
            case COMMODITY:
                if (add) cargo.addCommodity(id, n);
                else cargo.removeCommodity(id, n);
                if (text != null) {
                    if (add) AddRemoveCommodity.addCommodityGainText(id, n, text);
                    else AddRemoveCommodity.addCommodityLossText(id, n, text);
                }
                break;
            case SPECIAL:
                SpecialItemData data = new SpecialItemData(id, null);
                if (add) cargo.addSpecial(data, n);
                else cargo.removeItems(CargoItemType.SPECIAL, data, n);
                if (text != null) {
                    if (add) AddRemoveCommodity.addItemGainText(data, n, text);
                    else AddRemoveCommodity.addItemLossText(data, n, text);
                }
                break;
            case WEAPON:
                if (add) cargo.addWeapons(id, n);
                else cargo.removeWeapons(id, n);
                if (text != null) {
                    if (add) AddRemoveCommodity.addWeaponGainText(id, n, text);
                    else AddRemoveCommodity.addWeaponLossText(id, n, text);
                }
                break;
            case FIGHTER:
                if (add) cargo.addFighters(id, n);
                else cargo.removeFighters(id, n);
                if (text != null) {
                    if (add) AddRemoveCommodity.addFighterGainText(id, n, text);
                    else AddRemoveCommodity.addFighterLossText(id, n, text);
                }
                break;
            default:
                break;
        }
        return true;
    }

    /** A ship of that variant, named and repaired the way a new ship joins the player's fleet; null if the variant is unknown. */
    public static FleetMemberAPI ship(String variant) {
        try {
            if (Global.getSettings().getVariant(variant) == null) {
                SagaLog.error("unknown ship variant \"" + variant + "\"");
                return null;
            }
            FleetMemberAPI m = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
            m.setShipName(Global.getSector().getPlayerFaction().pickRandomShipName());
            m.getRepairTracker().setCR(m.getRepairTracker().getMaxCR());
            return m;
        } catch (Throwable t) {
            SagaLog.error("can't create a ship of variant \"" + variant + "\"", t);
            return null;
        }
    }
}
