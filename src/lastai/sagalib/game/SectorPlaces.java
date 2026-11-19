package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import lastai.sagalib.core.Places;

/**
 * SagaLib 1.3 (task #61): {@link Places.World} in game. A place is an entity, a market or a star
 * system; a person is one of the important people; a market's owner is its faction.
 */
public final class SectorPlaces implements Places.World {
    private static SectorAPI sector() {
        try { return Global.getSector(); } catch (Throwable t) { return null; }
    }

    /** The market an id stands for: an entity's market, or a market by id. */
    public static MarketAPI market(String id) {
        SectorAPI s = sector();
        if (s == null || id == null) return null;
        SectorEntityToken e = s.getEntityById(id);
        if (e != null && e.getMarket() != null && !e.getMarket().isPlanetConditionMarketOnly()) return e.getMarket();
        MarketAPI m = s.getEconomy().getMarket(id);
        return m != null && !m.isPlanetConditionMarketOnly() ? m : null;
    }

    @Override
    public boolean place(String id) {
        SectorAPI s = sector();
        if (s == null) return true;
        if (s.getEntityById(id) != null || s.getEconomy().getMarket(id) != null) return true;
        for (StarSystemAPI sys : s.getStarSystems()) if (id.equals(sys.getId())) return true;
        return false;
    }

    @Override
    public boolean person(String id) {
        SectorAPI s = sector();
        return s == null || s.getImportantPeople().getPerson(id) != null;
    }

    @Override
    public String owner(String id) {
        MarketAPI m = market(id);
        return m == null ? null : m.getFactionId();
    }

    /** SagaLib 1.3 (task #70): FactionAPI.getRelationshipLevel (a war set by Nexerelin or an event counts). */
    @Override
    public String relation(String a, String b) {
        SectorAPI s = sector();
        if (s == null || s.getFaction(a) == null || s.getFaction(b) == null) return null;
        return s.getFaction(a).getRelationshipLevel(b).name();
    }
}
