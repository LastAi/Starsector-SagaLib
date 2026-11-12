package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import lastai.sagalib.SagaLog;
import lastai.sagalib.model.FactionsDef;

/** factions.json at runtime: starting relations on a new game, scripted changes on every load. */
public final class Factions {
    private Factions() {}

    /** New game only: the content mods' starting relations. */
    public static void applyRelations() {
        int n = 0;
        for (FactionsDef d : Saga.content().factions) {
            for (FactionsDef.Relation r : d.relations) {
                FactionAPI a = Global.getSector().getFaction(r.faction);
                if (a == null || Global.getSector().getFaction(r.other) == null) {
                    SagaLog.error(d.source + ": relation " + r.faction + " / " + r.other + ": unknown faction");
                    continue;
                }
                a.setRelationship(r.other, r.value);
                n++;
            }
        }
        if (n > 0) SagaLog.info("set " + n + " starting faction relations");
    }

    /** Every load: removes values from factions' known lists (a .faction patch can't remove entries). */
    public static void applyScripted() {
        for (FactionsDef d : Saga.content().factions) {
            for (FactionsDef.Change c : d.scripted) {
                FactionAPI f = Global.getSector().getFaction(c.faction);
                if (f == null) {
                    SagaLog.error(d.source + " at " + c.where + ": unknown faction \"" + c.faction + "\"");
                    continue;
                }
                try {
                    if (!remove(f, c.path, c.value)) {
                        SagaLog.error(d.source + " at " + c.where + ": SagaLib can't remove from \"" + c.path + "\"");
                    }
                } catch (Throwable t) {
                    SagaLog.error(d.source + " at " + c.where + ": removing " + c.value + " from " + c.path + " failed", t);
                }
            }
        }
    }

    /** False if the path isn't a list SagaLib knows how to change. */
    static boolean remove(FactionAPI f, String path, String value) {
        if ("knownShips.hulls".equals(path)) f.removeKnownShip(value);
        else if ("knownWeapons.weapons".equals(path)) f.removeKnownWeapon(value);
        else if ("knownFighters.fighters".equals(path)) f.removeKnownFighter(value);
        else if ("knownIndustries.industries".equals(path)) f.removeKnownIndustry(value);
        else if ("knownHullMods.hullMods".equals(path) || "hullMods".equals(path)) f.removeKnownHullMod(value);
        else if ("priorityShips.hulls".equals(path)) f.removePriorityShip(value);
        else if ("priorityWeapons.weapons".equals(path)) f.removePriorityWeapon(value);
        else if ("priorityFighters.fighters".equals(path)) f.removePriorityFighter(value);
        else if ("priorityHullMods.hullMods".equals(path)) f.removePriorityHullMod(value);
        else return false;
        return true;
    }
}
