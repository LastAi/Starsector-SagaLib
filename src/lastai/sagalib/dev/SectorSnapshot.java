package lastai.sagalib.dev;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import lastai.sagalib.SagaLog;
import lastai.sagalib.game.Saga;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SagaLib 1.3 (dev mode only, docs/FORMAT.md "Sector snapshot"): the running sector's systems,
 * places, markets and people, written to saves/common/sagaforge_snapshot.json.data for Saga
 * Forge's "seen in game" source.
 */
public final class SectorSnapshot {
    private SectorSnapshot() {}

    public static final String FILE = "sagaforge_snapshot.json";

    /** Writes the snapshot; returns a line for the menu. */
    public static String write() throws Exception {
        if (!Global.getSettings().isDevMode()) throw new IllegalStateException("only in dev mode");
        JSONObject o = build();
        Global.getSettings().writeTextFileToCommon(FILE, o.toString(2));
        String line = "wrote saves/common/" + FILE + ".data (" + o.getJSONArray("systems").length() + " systems, " + o.getJSONArray("places").length()
                + " places, " + o.getJSONArray("markets").length() + " markets, " + o.getJSONArray("people").length() + " people)";
        SagaLog.info("dev: sector snapshot " + line);
        return line;
    }

    static JSONObject build() throws Exception {
        JSONObject o = new JSONObject();
        o.put("format", 1);
        o.put("sagalib", Saga.VERSION);
        o.put("gameVersion", Global.getSettings().getVersionString().replaceFirst("^Starsector\\s+", "").trim());
        CampaignClockAPI clock = Global.getSector().getClock();
        o.put("day", "c." + clock.getCycle() + "." + clock.getMonth() + "." + clock.getDay());
        JSONArray mods = new JSONArray();
        for (ModSpecAPI m : Global.getSettings().getModManager().getEnabledModsCopy()) {
            JSONObject j = new JSONObject();
            j.put("id", m.getId());
            j.put("name", m.getName());
            j.put("version", m.getVersion());
            mods.put(j);
        }
        o.put("mods", mods);

        JSONArray systems = new JSONArray();
        JSONArray places = new JSONArray();
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            try {
                JSONObject j = new JSONObject();
                j.put("id", s.getId());
                j.put("name", s.getBaseName());
                j.put("x", Math.round(s.getLocation().x));
                j.put("y", Math.round(s.getLocation().y));
                systems.put(j);
                for (Object e : s.getAllEntities()) {
                    JSONObject p = place(s, (SectorEntityToken) e);
                    if (p != null) places.put(p);
                }
            } catch (Throwable t) {
                SagaLog.error("dev: snapshot of system " + s.getId() + " failed", t);
            }
        }
        o.put("systems", systems);
        o.put("places", places);

        JSONArray markets = new JSONArray();
        Map<String, JSONObject> people = new LinkedHashMap<String, JSONObject>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            try {
                JSONObject j = new JSONObject();
                j.put("id", m.getId());
                if (m.getPrimaryEntity() != null) j.put("entity", m.getPrimaryEntity().getId());
                j.put("name", m.getName());
                j.put("faction", m.getFactionId());
                j.put("size", m.getSize());
                if (m.getStarSystem() != null) j.put("system", m.getStarSystem().getId());
                markets.put(j);
                for (PersonAPI p : m.getPeopleCopy()) person(people, p, m);
            } catch (Throwable t) {
                SagaLog.error("dev: snapshot of market " + m.getId() + " failed", t);
            }
        }
        o.put("markets", markets);
        ImportantPeopleAPI ip = Global.getSector().getImportantPeople();
        if (ip != null) for (ImportantPeopleAPI.PersonDataAPI d : ip.getPeopleCopy()) {
            if (d != null && d.getPerson() != null) person(people, d.getPerson(), d.getPerson().getMarket());
        }
        o.put("people", new JSONArray(people.values()));
        return o;
    }

    /** A system entity with an id that isn't a fleet, or null. */
    static JSONObject place(StarSystemAPI s, SectorEntityToken e) throws Exception {
        if (e == null || e instanceof CampaignFleetAPI) return null;
        String id = e.getId();
        if (id == null || id.isEmpty()) return null;
        String kind, type = null;
        if (e instanceof PlanetAPI) {
            PlanetAPI pl = (PlanetAPI) e;
            kind = pl.isStar() ? "star" : "planet";
            type = pl.getTypeId();
        } else if (e instanceof JumpPointAPI) {
            kind = "jumpPoint";
        } else if (e instanceof CustomCampaignEntityAPI) {
            kind = e.hasTag(Tags.STATION) ? "station" : "custom";
            type = ((CustomCampaignEntityAPI) e).getCustomEntityType();
        } else return null; // asteroids, terrain, rings, nebulae
        JSONObject j = new JSONObject();
        j.put("id", id);
        j.put("name", e.getName());
        j.put("system", s.getId());
        j.put("kind", kind);
        if (type != null) j.put("type", type);
        if (e.getFaction() != null) j.put("faction", e.getFaction().getId());
        MarketAPI m = e.getMarket();
        if (m != null && !m.isPlanetConditionMarketOnly()) j.put("market", m.getId());
        return j;
    }

    static void person(Map<String, JSONObject> out, PersonAPI p, MarketAPI m) throws Exception {
        if (p == null || p.getId() == null || p.getId().isEmpty()) return;
        JSONObject j = out.get(p.getId());
        if (j == null) {
            j = new JSONObject();
            j.put("id", p.getId());
            if (p.getName() != null) {
                j.put("first", p.getName().getFirst());
                j.put("last", p.getName().getLast());
            }
            if (p.getFaction() != null) j.put("faction", p.getFaction().getId());
            j.put("post", p.getPostId());
            j.put("rank", p.getRankId());
            j.put("portrait", p.getPortraitSprite());
            out.put(p.getId(), j);
        }
        if (!j.has("market") && m != null) j.put("market", m.getId());
    }
}
