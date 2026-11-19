package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Mods;
import lastai.sagalib.model.FactionsDef;

/** factions.json at runtime: starting relations on a new game, scripted changes on every load. */
public final class Factions {
    private Factions() {}

    /** Nexerelin's mod id. */
    public static final String NEXERELIN = "nexerelin";

    /**
     * New game only: the content mods' starting relations. SagaLib 1.3 (task #63): with Nexerelin, a
     * faction whose mod ships a Nexerelin config with these relations is left to Nexerelin (it applies
     * them in every sector and randomizes them only when the player asks), so the two never overwrite
     * each other. Mods' ModPlugins run in mod order, so SagaLib would otherwise run after Nexerelin.
     */
    public static void applyRelations() {
        int n = 0;
        java.util.Map<String, Integer> left = new java.util.LinkedHashMap<String, Integer>();
        GameMemory mem = GameMemory.global();
        boolean nex = Global.getSettings().getModManager().isModEnabled(NEXERELIN);
        FactionsDef.ShipsFile files = new FactionsDef.ShipsFile() {
            private final java.util.Map<String, Boolean> seen = new java.util.HashMap<String, Boolean>();
            @Override
            public boolean has(String mod, String path) {
                String k = mod + "|" + path;
                Boolean b = seen.get(k);
                if (b == null) {
                    try { b = mod != null && Global.getSettings().loadText(path, mod) != null; } catch (Throwable t) { b = false; }
                    if (!b) SagaLog.warn(mod + ": factions.json leaves relations to Nexerelin, but " + path + " isn't in the mod; SagaLib sets them");
                    seen.put(k, b);
                }
                return b;
            }
        };
        for (FactionsDef d : Saga.content().factions) {
            for (FactionsDef.Relation r : d.relations) {
                // SagaLib 1.3: a relation only with / only without mods is skipped quietly (logged once)
                if (!Mods.allowed(mem, r.gate, "relation", r.faction + "/" + r.other)) continue;
                if (d.leftToNexerelin(r, nex, files)) {
                    Integer c = left.get(r.faction);
                    left.put(r.faction, c == null ? 1 : c + 1);
                    continue;
                }
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
        for (java.util.Map.Entry<String, Integer> e : left.entrySet()) {
            SagaLog.info("left " + e.getValue() + " starting relation" + (e.getValue() == 1 ? "" : "s") + " of " + e.getKey() + " to Nexerelin (its faction config)");
        }
    }

    /**
     * Every load: removes values from factions' known lists (a .faction patch can't remove entries);
     * SagaLib 1.3: adds to them, sets names, logo, crest, secondary colour and doctrine, hides or
     * shows factions in the intel tab. Each change gives the same result when repeated.
     */
    public static void applyScripted() {
        GameMemory mem = GameMemory.global();
        for (FactionsDef d : Saga.content().factions) {
            for (FactionsDef.Change c : d.scripted) {
                if (!Mods.allowed(mem, c.gate, "faction change", c.faction + " " + c.op + (c.value != null ? " " + c.value : ""))) continue;
                FactionAPI f = Global.getSector().getFaction(c.faction);
                if (f == null) {
                    SagaLog.error(d.source + " at " + c.where + ": unknown faction \"" + c.faction + "\"");
                    continue;
                }
                try {
                    if ("hideFromIntelTab".equals(c.op) || "showInIntelTab".equals(c.op)) {
                        // vanilla's own flag, in both game versions; the .faction key can't depend on mods
                        f.setShowInIntelTab("showInIntelTab".equals(c.op));
                        continue;
                    }
                    if ("set".equals(c.op)) {
                        if (!set(f, c.path, c.value)) SagaLog.error(d.source + " at " + c.where + ": SagaLib can't set \"" + c.path + "\" to \"" + c.value + "\"");
                        continue;
                    }
                    if ("add".equals(c.op)) {
                        if (!add(f, c.path, c.value)) SagaLog.error(d.source + " at " + c.where + ": SagaLib can't add to \"" + c.path + "\"");
                        continue;
                    }
                    if (!remove(f, c.path, c.value)) {
                        SagaLog.error(d.source + " at " + c.where + ": SagaLib can't remove from \"" + c.path + "\"");
                    }
                } catch (Throwable t) {
                    SagaLog.error(d.source + " at " + c.where + ": " + c.op + " " + c.value + " on " + c.path + " failed", t);
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
        else if ("illegalCommodities".equals(path)) f.makeCommodityLegal(value);
        else return false;
        return true;
    }

    /** SagaLib 1.3: adds to a known list (only if not known yet, so every load is the same). */
    static boolean add(FactionAPI f, String path, String value) {
        if ("knownShips.hulls".equals(path)) { if (!f.knowsShip(value)) f.addKnownShip(value, false); }
        else if ("knownWeapons.weapons".equals(path)) { if (!f.knowsWeapon(value)) f.addKnownWeapon(value, false); }
        else if ("knownFighters.fighters".equals(path)) { if (!f.knowsFighter(value)) f.addKnownFighter(value, false); }
        else if ("illegalCommodities".equals(path)) { if (!f.isIllegal(value)) f.makeCommodityIllegal(value); }
        else return false;
        return true;
    }

    /** SagaLib 1.3: name, logo, crest, secondary colour overrides and doctrine fields (FactionDoctrineAPI setters). */
    static boolean set(FactionAPI f, String path, String value) {
        if ("displayName".equals(path)) f.setDisplayNameOverride(value);
        else if ("displayNameWithArticle".equals(path)) f.setDisplayNameWithArticleOverride(value);
        else if ("logo".equals(path)) { if (!Textures.ensure(value)) return false; f.setFactionLogoOverride(value); }
        else if ("crest".equals(path)) { if (!Textures.ensure(value)) return false; f.setFactionCrestOverride(value); }
        else if ("secondaryColor".equals(path)) {
            int[] c = FactionsDef.color(value);
            if (c == null) return false;
            f.setSecondaryColorOverride(new java.awt.Color(c[0], c[1], c[2], c[3]));
        } else if (path.startsWith("doctrine.")) {
            String field = path.substring("doctrine.".length());
            Float v = FactionsDef.doctrineValue(field, value);
            if (v == null) return false;
            com.fs.starfarer.api.campaign.FactionDoctrineAPI doc = f.getDoctrine();
            int n = Math.round(v);
            if ("warships".equals(field)) doc.setWarships(n);
            else if ("carriers".equals(field)) doc.setCarriers(n);
            else if ("phaseShips".equals(field)) doc.setPhaseShips(n);
            else if ("officerQuality".equals(field)) doc.setOfficerQuality(n);
            else if ("shipQuality".equals(field)) doc.setShipQuality(n);
            else if ("numShips".equals(field)) doc.setNumShips(n);
            else if ("shipSize".equals(field)) doc.setShipSize(n);
            else if ("aggression".equals(field)) doc.setAggression(n);
            else if ("combatFreighterProbability".equals(field)) doc.setCombatFreighterProbability(v);
            else return false;
        } else return false;
        return true;
    }
}
