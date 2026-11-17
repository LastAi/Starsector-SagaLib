package lastai.sagalib.quest;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.util.Misc;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Memory;
import lastai.sagalib.core.Targets;
import lastai.sagalib.model.QuestDef;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

/**
 * SagaLib 1.2: runs a quest's random-target searches with vanilla's own HubMissionWithSearch
 * (requires are AND; prefers in order, each dropped when it would leave nothing), and stores the
 * picks (core/Targets keys). Called from SagaQuest.create(): no match means the quest isn't offered.
 */
final class TargetSearch {
    private TargetSearch() {}

    /** All the quest's targets, in order; false (nothing stored for the failed one) when one finds nothing. */
    static boolean run(HubMissionWithSearch m, QuestDef q, Vector2f giverLoc, Memory mem) {
        for (QuestDef.Target t : q.targets) {
            // a new offer: forget the last pick's person and spawn
            mem.unset(Targets.factKey(q.id, t.var, Targets.PERSON));
            mem.unset(Targets.factKey(q.id, t.var, Targets.PERSON_NAME));
            mem.unset(Targets.factKey(q.id, t.var, Targets.SPAWNED));
            m.resetSearch();
            try {
                for (QuestDef.Criterion c : t.require) apply(m, c, true, giverLoc, "quest " + q.id);
                for (QuestDef.Criterion c : t.prefer) apply(m, c, false, giverLoc, "quest " + q.id);
            } catch (Throwable e) {
                SagaLog.errorOnce("target:" + q.id + ":" + t.var, "quest " + q.id + ": target \"" + t.var + "\": a criterion failed", e);
                return false;
            }
            if (t.systemFirst) m.searchMakeSystemPreferencesMoreImportant(true);
            Object picked;
            if ("system".equals(t.pick)) picked = m.pickSystem();
            else if ("planet".equals(t.pick)) picked = m.pickPlanet();
            else if ("entity".equals(t.pick)) picked = m.pickEntity();
            else picked = m.pickMarket();
            m.resetSearch();
            if (picked == null) {
                SagaLog.info("quest " + q.id + ": no " + t.pick + " matches target \"" + t.var + "\", so it isn't offered now");
                return false;
            }
            store(q.id, t.var, picked, giverLoc, mem);
            if (t.person != null && !person(m, q, t, picked, mem)) return false;
        }
        return true;
    }

    /** RC-R4: someone with one of the posts at the picked colony, else a new person (vanilla findOrCreatePerson; a new one leaves when the mission ends). */
    private static boolean person(HubMissionWithSearch m, QuestDef q, QuestDef.Target t, Object picked, Memory mem) {
        MarketAPI market = picked instanceof MarketAPI ? (MarketAPI) picked
                : picked instanceof SectorEntityToken ? ((SectorEntityToken) picked).getMarket() : null;
        if (market == null || market.isPlanetConditionMarketOnly() || !(m instanceof SagaQuest)) {
            SagaLog.info("quest " + q.id + ": target " + t.var + " has no colony for its person, so it isn't offered now");
            return false;
        }
        com.fs.starfarer.api.characters.PersonAPI p = ((SagaQuest) m).personAt(market, t.person.faction, t.person.rank, t.person.posts.toArray(new String[0]));
        if (p == null) return false;
        mem.set(Targets.factKey(q.id, t.var, Targets.PERSON), p.getId());
        mem.set(Targets.factKey(q.id, t.var, Targets.PERSON_NAME), p.getNameString());
        SagaLog.info("quest " + q.id + ": target " + t.var + " person = " + p.getId() + " (" + p.getNameString() + ", " + p.getPostId() + ")");
        return true;
    }

    private static void store(String quest, String var, Object picked, Vector2f giverLoc, Memory mem) {
        String id, name, system = "", faction = "", factionId = "";
        Vector2f loc;
        if (picked instanceof StarSystemAPI) {
            StarSystemAPI s = (StarSystemAPI) picked;
            id = s.getId();
            name = s.getBaseName();
            system = s.getBaseName();
            loc = s.getLocation();
        } else if (picked instanceof MarketAPI) {
            MarketAPI mk = (MarketAPI) picked;
            id = mk.getId();
            name = mk.getName();
            if (mk.getStarSystem() != null) system = mk.getStarSystem().getBaseName();
            faction = factionName(mk.getFaction());
            factionId = mk.getFaction() == null ? "" : mk.getFaction().getId();
            loc = mk.getLocationInHyperspace();
        } else {
            SectorEntityToken e = (SectorEntityToken) picked;
            id = e.getId();
            name = e.getName();
            if (e.getStarSystem() != null) system = e.getStarSystem().getBaseName();
            boolean colony = e.getMarket() != null && !e.getMarket().isPlanetConditionMarketOnly();
            faction = colony ? factionName(e.getMarket().getFaction()) : "";
            factionId = colony && e.getMarket().getFaction() != null ? e.getMarket().getFaction().getId() : "";
            loc = e.getLocationInHyperspace();
        }
        mem.set(Targets.key(quest, var), id);
        mem.set(Targets.factKey(quest, var, "name"), name);
        mem.set(Targets.factKey(quest, var, "system"), system);
        mem.set(Targets.factKey(quest, var, "dist"), giverLoc == null || loc == null ? "" : Targets.formatDist(Misc.getDistanceLY(giverLoc, loc)));
        mem.set(Targets.factKey(quest, var, "faction"), faction);
        mem.set(Targets.factKey(quest, var, "factionId"), factionId);
        // RC-R3: what it is like (Targets.DETAILS)
        StarSystemAPI sys = picked instanceof StarSystemAPI ? (StarSystemAPI) picked
                : picked instanceof MarketAPI ? ((MarketAPI) picked).getStarSystem() : ((SectorEntityToken) picked).getStarSystem();
        MarketAPI mk = picked instanceof MarketAPI ? (MarketAPI) picked : picked instanceof SectorEntityToken ? ((SectorEntityToken) picked).getMarket() : null;
        boolean ruins = mk != null && Misc.hasRuins(mk);
        mem.set(Targets.factKey(quest, var, "deciv"), Boolean.valueOf(mk != null && mk.hasCondition("decivilized")));
        mem.set(Targets.factKey(quest, var, "hasRuins"), Boolean.valueOf(ruins));
        mem.set(Targets.factKey(quest, var, "ruins"), ruins ? Targets.ruinsSize(Misc.getRuinsType(mk)) : "");
        mem.set(Targets.factKey(quest, var, "unexploredRuins"), Boolean.valueOf(Misc.hasUnexploredRuins(mk)));
        mem.set(Targets.factKey(quest, var, "populated"), Boolean.valueOf(mk != null && !mk.isPlanetConditionMarketOnly()));
        mem.set(Targets.factKey(quest, var, "gasGiant"), Boolean.valueOf(picked instanceof com.fs.starfarer.api.campaign.PlanetAPI && ((com.fs.starfarer.api.campaign.PlanetAPI) picked).isGasGiant()));
        mem.set(Targets.factKey(quest, var, "theme"), sys == null ? "" : Targets.themes(sys.getTags()));
        for (String fam : new String[] { "ruins", "derelict", "remnant", "misc" })
            mem.set(Targets.factKey(quest, var, fam + "Theme"), Boolean.valueOf(sys != null && Targets.hasTheme(sys.getTags(), fam)));
        mem.set(Targets.factKey(quest, var, "blackHole"), Boolean.valueOf(sys != null && sys.hasBlackHole()));
        mem.set(Targets.factKey(quest, var, "pulsar"), Boolean.valueOf(sys != null && sys.hasPulsar()));
        mem.set(Targets.factKey(quest, var, "nebula"), Boolean.valueOf(sys != null && sys.isNebula()));
        FactionAPI fac = mk != null && !mk.isPlanetConditionMarketOnly() ? mk.getFaction() : null;
        mem.set(Targets.factKey(quest, var, "hostile"), Boolean.valueOf(fac != null && fac.isHostileTo(com.fs.starfarer.api.impl.campaign.ids.Factions.PLAYER)));
        mem.set(Targets.factKey(quest, var, "unexplored"), Boolean.valueOf(sys != null && !sys.isEnteredByPlayer()));
        SagaLog.info("quest " + quest + ": target " + var + " = " + id + " (" + name + (system.isEmpty() ? "" : ", " + system) + ")");
    }

    private static String factionName(FactionAPI f) { return f == null ? "" : f.getDisplayName(); }

    // ------------------------------------------------------------------ criteria → vanilla methods

    private static String str(QuestDef.Criterion c, int i) { return i < c.args.size() && c.args.get(i) != null ? String.valueOf(c.args.get(i)) : ""; }

    private static float num(QuestDef.Criterion c, int i) {
        Object v = i < c.args.size() ? c.args.get(i) : null;
        if (v instanceof Number) return ((Number) v).floatValue();
        try { return Float.parseFloat(String.valueOf(v)); } catch (NumberFormatException e) { return 0f; }
    }

    @SuppressWarnings("unchecked")
    private static String[] list(QuestDef.Criterion c, int i) {
        Object v = i < c.args.size() ? c.args.get(i) : null;
        if (v instanceof List) return ((List<String>) v).toArray(new String[0]);
        String s = v == null ? "" : String.valueOf(v).trim();
        return s.isEmpty() ? new String[0] : s.split("\\s*,\\s*");
    }

    private static ReqMode mode(QuestDef.Criterion c, int i) {
        try { return ReqMode.valueOf(str(c, i)); } catch (IllegalArgumentException e) { return ReqMode.ANY; }
    }

    private static Vector2f from(QuestDef.Criterion c, int i, Vector2f giverLoc) {
        if ("player".equals(str(c, i)) || giverLoc == null) return Global.getSector().getPlayerFleet().getLocationInHyperspace();
        return giverLoc;
    }

    /** One criterion, as require (r) or prefer. Every id of Targets.CRITERIA has a case. */
    static void apply(HubMissionWithSearch m, QuestDef.Criterion c, boolean r, Vector2f giverLoc, String owner) {
        if (!r && Targets.REQUIRE_ONLY.contains(c.id)) {
            SagaLog.warnOnce("prefonly:" + owner + ":" + c.id, owner + ": " + c.id + " can only be required (vanilla has no prefer variant); skipped");
            return;
        }
        String id = c.id;
        if ("sysTags".equals(id)) { if (r) m.requireSystemTags(mode(c, 0), list(c, 1)); else m.preferSystemTags(mode(c, 0), list(c, 1)); }
        else if ("sysInteresting".equals(id)) { if (r) m.requireSystemInterestingAndNotUnsafeOrCore(); else m.preferSystemInteresting(); }
        else if ("sysUnexplored".equals(id)) { if (r) m.requireSystemUnexplored(); else m.preferSystemUnexplored(); }
        else if ("sysExplored".equals(id)) { if (r) m.requireSystemExplored(); else m.preferSystemExplored(); }
        else if ("sysNotEnteredFor".equals(id)) { if (r) m.requireSystemNotEnteredByPlayerFor(num(c, 0)); else m.preferSystemNotEnteredByPlayerFor(num(c, 0)); }
        else if ("sysInner".equals(id)) { if (r) m.requireSystemInInnerSector(); else m.preferSystemInInnerSector(); }
        else if ("sysFringe".equals(id)) { if (r) m.requireSystemOnFringeOfSector(); else m.preferSystemOnFringeOfSector(); }
        else if ("sysWithin".equals(id)) { if (r) m.requireSystemWithinRangeOf(from(c, 0, giverLoc), num(c, 1)); else m.preferSystemWithinRangeOf(from(c, 0, giverLoc), num(c, 1)); }
        else if ("sysOutside".equals(id)) { if (r) m.requireSystemOutsideRangeOf(from(c, 0, giverLoc), num(c, 1)); else m.preferSystemOutsideRangeOf(from(c, 0, giverLoc), num(c, 1)); }
        else if ("sysBlackHole".equals(id)) { if (r) m.requireSystemBlackHole(); else m.preferSystemBlackHole(); }
        else if ("sysPulsar".equals(id)) { if (r) m.requireSystemHasPulsar(); else m.preferSystemHasPulsar(); }
        else if ("sysNebula".equals(id)) { if (r) m.requireSystemNebula(); else m.preferSystemNebula(); }
        else if ("sysNotBlackHole".equals(id)) { if (r) m.requireSystemNotBlackHole(); else m.preferSystemNotBlackHole(); }
        else if ("sysNotPulsar".equals(id)) { if (r) m.requireSystemNotHasPulsar(); else m.preferSystemNotPulsar(); }
        else if ("sysNotNebula".equals(id)) { if (r) m.requireSystemNotNebula(); else m.preferSystemNotNebula(); }
        else if ("sysSafeStars".equals(id)) m.requireSystemHasSafeStars();
        else if ("sysNotUsedForStory".equals(id)) m.requireSystemNotAlreadyUsedForStory();
        else if ("sysHasColony".equals(id)) { if (r) m.requireSystemHasColony(str(c, 0), (int) num(c, 1)); else m.preferSystemHasColony(str(c, 0), (int) num(c, 1)); }
        else if ("sysHasBase".equals(id)) { if (r) m.requireSystemHasBase(str(c, 0)); else m.preferSystemHasBase(str(c, 0)); }
        else if ("sysJumpPoints".equals(id)) { if (r) m.requireSystemHasAtLeastNumJumpPoints((int) num(c, 0)); else m.preferSystemHasAtLeastNumJumpPoints((int) num(c, 0)); }
        else if ("sysPlanets".equals(id)) { if (r) m.requireSystemHasNumPlanets((int) num(c, 0)); else m.preferSystemHasNumPlanets((int) num(c, 0)); }
        else if ("plGasGiant".equals(id)) { if (r) m.requirePlanetIsGasGiant(); else m.preferPlanetIsGasGiant(); }
        else if ("plNotGasGiant".equals(id)) { if (r) m.requirePlanetNotGasGiant(); else m.preferPlanetNonGasGiant(); }
        else if ("plPopulated".equals(id)) { if (r) m.requirePlanetPopulated(); else m.preferPlanetPopulated(); }
        else if ("plUnpopulated".equals(id)) { if (r) m.requirePlanetUnpopulated(); else m.preferPlanetUnpopulated(); }
        else if ("plTags".equals(id)) { if (r) m.requirePlanetTags(mode(c, 0), list(c, 1)); else m.preferPlanetTags(mode(c, 0), list(c, 1)); }
        else if ("plConditions".equals(id)) { if (r) m.requirePlanetConditions(mode(c, 0), list(c, 1)); else m.preferPlanetConditions(mode(c, 0), list(c, 1)); }
        else if ("plUnsurveyed".equals(id)) { if (r) m.requirePlanetUnsurveyed(); else m.preferPlanetUnsurveyed(); }
        else if ("plNotFullySurveyed".equals(id)) { if (r) m.requirePlanetNotFullySurveyed(); else m.preferPlanetNotFullySurveyed(); }
        else if ("plFullySurveyed".equals(id)) { if (r) m.requirePlanetFullySurveyed(); else m.preferPlanetFullySurveyed(); }
        else if ("plRuins".equals(id)) { if (r) m.requirePlanetWithRuins(); else m.preferPlanetWithRuins(); }
        else if ("plNoRuins".equals(id)) { if (r) m.requirePlanetWithoutRuins(); else m.preferPlanetWithoutRuins(); }
        else if ("plUnexploredRuins".equals(id)) { if (r) m.requirePlanetUnexploredRuins(); else m.preferPlanetUnexploredRuins(); }
        else if ("plNotNearJump".equals(id)) { if (r) m.requirePlanetNotNearJumpPoint(num(c, 0)); else m.preferPlanetNotNearJumpPoint(num(c, 0)); }
        else if ("enType".equals(id)) { if (r) m.requireEntityType(list(c, 0)); else m.preferEntityType(list(c, 0)); }
        else if ("enTags".equals(id)) { if (r) m.requireEntityTags(mode(c, 0), list(c, 1)); else m.preferEntityTags(mode(c, 0), list(c, 1)); }
        else if ("enFlags".equals(id)) { if (r) m.requireEntityMemoryFlags(list(c, 0)); else m.preferEntityMemoryFlags(list(c, 0)); }
        else if ("enUndiscovered".equals(id)) { if (r) m.requireEntityUndiscovered(); else m.preferEntityUndiscovered(); }
        else if ("mkFaction".equals(id)) { if (r) m.requireMarketFaction(list(c, 0)); else m.preferMarketFaction(list(c, 0)); }
        else if ("mkFactionNot".equals(id)) { if (r) m.requireMarketFactionNot(list(c, 0)); else m.preferMarketFactionNot(list(c, 0)); }
        else if ("mkNotPlayer".equals(id)) m.requireMarketFactionNotPlayer();
        else if ("mkHostileTo".equals(id)) { if (r) m.requireMarketFactionHostileTo(str(c, 0)); else m.preferMarketFactionHostileTo(str(c, 0)); }
        else if ("mkNotHostileTo".equals(id)) { if (r) m.requireMarketFactionNotHostileTo(str(c, 0)); else m.preferMarketFactionNotHostileTo(str(c, 0)); }
        else if ("mkFactionCustom".equals(id)) { if (r) m.requireMarketFactionCustom(mode(c, 0), list(c, 1)); else m.preferMarketFactionCustom(mode(c, 0), list(c, 1)); }
        else if ("mkSizeAtLeast".equals(id)) { if (r) m.requireMarketSizeAtLeast((int) num(c, 0)); else m.preferMarketSizeAtLeast((int) num(c, 0)); }
        else if ("mkSizeAtMost".equals(id)) { if (r) m.requireMarketSizeAtMost((int) num(c, 0)); else m.preferMarketSizeAtMost((int) num(c, 0)); }
        else if ("mkStabilityAtLeast".equals(id)) { if (r) m.requireMarketStabilityAtLeast((int) num(c, 0)); else m.preferMarketStabilityAtLeast((int) num(c, 0)); }
        else if ("mkStabilityAtMost".equals(id)) { if (r) m.requireMarketStabilityAtMost((int) num(c, 0)); else m.preferMarketStabilityAtMost((int) num(c, 0)); }
        else if ("mkMilitary".equals(id)) { if (r) m.requireMarketIsMilitary(); else m.preferMarketIsMilitary(); }
        else if ("mkNotMilitary".equals(id)) { if (r) m.requireMarketNotMilitary(); else m.preferMarketNotMilitary(); }
        else if ("mkSpaceport".equals(id)) { if (r) m.requireMarketHasSpaceport(); else m.preferMarketHasSpaceport(); }
        else if ("mkNotSpaceport".equals(id)) { if (r) m.requireMarketNotHasSpaceport(); else m.preferMarketNotHasSpaceport(); }
        else if ("mkHidden".equals(id)) { if (r) m.requireMarketHidden(); else m.preferMarketHidden(); }
        else if ("mkNotHidden".equals(id)) { if (r) m.requireMarketNotHidden(); else m.preferMarketNotHidden(); }
        else if ("mkConditions".equals(id)) { if (r) m.requireMarketConditions(mode(c, 0), list(c, 1)); else m.preferMarketConditions(mode(c, 0), list(c, 1)); }
        else if ("mkIndustries".equals(id)) { if (r) m.requireMarketIndustries(mode(c, 0), list(c, 1)); else m.preferMarketIndustries(mode(c, 0), list(c, 1)); }
        else SagaLog.warnOnce("crit:" + id, owner + ": unknown target criterion " + id);
    }
}
