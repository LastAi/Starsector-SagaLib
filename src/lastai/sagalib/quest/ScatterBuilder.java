package lastai.sagalib.quest;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Memory;
import lastai.sagalib.game.GameMemory;
import lastai.sagalib.game.Saga;
import lastai.sagalib.model.QuestDef;
import lastai.sagalib.model.ScatterDef;
import lastai.sagalib.systems.SystemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SagaLib 1.2 (Phase 7e, RC-R10): places the mods' scatters, once per game, with vanilla's mission
 * search (a throwaway SagaQuest is only the search; it is never created or accepted). New game:
 * after the economy loads (searches can ask for colonies). Existing saves: on load, if the
 * scatter says addToExistingSaves. $global.sagaforge_scatter_&lt;id&gt;_done = how many were placed.
 */
public final class ScatterBuilder {
    private ScatterBuilder() {}

    public static String doneKey(String id) { return "$global.sagaforge_scatter_" + id + "_done"; }

    public static void run(boolean newGame) {
        Memory mem = GameMemory.global();
        for (ScatterDef s : Saga.content().scatters.values()) {
            if (mem.get(doneKey(s.id)) != null) continue;
            if (!lastai.sagalib.core.Mods.allowed(mem, s.gate, "scatter", s.id)) continue; // SagaLib 1.3
            if (!newGame && !s.addToExistingSaves) continue;
            try {
                int n = place(s);
                mem.set(doneKey(s.id), Integer.valueOf(n));
                SagaLog.info("scatter " + s.id + ": placed " + n + " of " + s.count);
            } catch (Throwable t) {
                SagaLog.error("scatter " + s.id + " (" + s.source + ") failed", t);
            }
        }
    }

    static int place(ScatterDef s) {
        SagaQuest search = new SagaQuest();
        search.setMissionId("sagaforge_scatter_" + s.id);
        long seed = Global.getSector().getSeedString() == null ? s.id.hashCode() : (Global.getSector().getSeedString() + s.id).hashCode();
        Random random = new Random(seed);
        search.setGenRandom(random);
        // "within N light-years" measures from the scatter's place, else from the player
        org.lwjgl.util.vector.Vector2f fromLoc = null;
        if (s.from != null) {
            SectorEntityToken f = SagaQuest.targetEntity(s.from);
            if (f != null) fromLoc = f.getLocationInHyperspace();
            else SagaLog.warn("scatter " + s.id + ": from \"" + s.from + "\" is not an entity or market in this game; distances are from the player");
        }
        List<StarSystemAPI> used = new ArrayList<StarSystemAPI>();
        int placed = 0;
        for (int i = 1; i <= s.count; i++) {
            search.resetSearch();
            QuestDef.Target t = s.target;
            for (QuestDef.Criterion c : t.require) TargetSearch.apply(search, c, true, fromLoc, "scatter " + s.id);
            for (QuestDef.Criterion c : t.prefer) TargetSearch.apply(search, c, false, fromLoc, "scatter " + s.id);
            for (StarSystemAPI u : used) search.requireSystemNot(u);
            if (t.systemFirst) search.searchMakeSystemPreferencesMoreImportant(true);
            Object picked = "system".equals(t.pick) ? search.pickSystem() : "planet".equals(t.pick) ? search.pickPlanet()
                    : "entity".equals(t.pick) ? search.pickEntity() : search.pickMarket();
            if (picked == null) { SagaLog.info("scatter " + s.id + ": no place for copy " + i + " (nothing matches)"); break; }
            SectorEntityToken focus = picked instanceof StarSystemAPI ? ((StarSystemAPI) picked).getCenter()
                    : picked instanceof MarketAPI ? ((MarketAPI) picked).getPrimaryEntity() : (SectorEntityToken) picked;
            LocationAPI loc = focus.getContainingLocation();
            if (!(loc instanceof StarSystemAPI)) continue;
            StarSystemAPI sys = (StarSystemAPI) loc;
            used.add(sys);
            String id = s.id + "_" + i;
            SectorEntityToken e = sys.addCustomEntity(id, s.entityName, s.entityType, s.entityFaction);
            float r = picked instanceof StarSystemAPI ? 2500f + random.nextFloat() * 2500f : focus.getRadius() + 150f + random.nextFloat() * 150f;
            float days = r / (5f + random.nextFloat() * 10f);
            e.setCircularOrbit(focus, random.nextFloat() * 360f, r, days);
            e.addTag(Tags.NOT_RANDOM_MISSION_TARGET);
            e.getMemoryWithoutUpdate().set("$sagaforge_scatter", s.id);
            SystemBuilder.salvage(e, s.loot, s.defenders);
            SagaLog.info("scatter " + s.id + ": " + id + " near " + (focus instanceof PlanetAPI ? focus.getName() : focus.getId()) + " in " + sys.getBaseName());
            placed++;
        }
        return placed;
    }
}
