package lastai.sagalib.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.MusicPlayerPluginImpl;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictShipData;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.SystemPlan;
import lastai.sagalib.game.GameMemory;
import lastai.sagalib.game.People;
import lastai.sagalib.game.Saga;
import lastai.sagalib.model.SystemDef;

import java.awt.Color;
import java.util.Random;

/**
 * Builds the content mods' star systems (docs/FORMAT.md, "Star systems"):
 * <ul>
 * <li>new systems on a new game, and on load in a save without them when addToExistingSaves is
 * set (a built system carries the tag sagaforge:&lt;id&gt;, which is how it is found again);</li>
 * <li>additions to existing systems on new game and every load, each entity only if
 * getEntityById(id) is null;</li>
 * <li>markets go into the economy; people are placed after building (People.applyAll retries
 * everyone whose market didn't exist before).</li>
 * </ul>
 * Built things are vanilla objects in the save; SagaLib keeps no state of its own here.
 */
public final class SystemBuilder {
    private SystemBuilder() {}

    private static final SystemPlan.Exists GAME = new SystemPlan.Exists() {
        @Override
        public boolean exists(String id) { return Global.getSector().getEntityById(id) != null; }
    };

    /** New game: every new system, and the additions to existing ones. */
    public static void onNewGame() {
        for (SystemDef s : Saga.content().systems.values()) {
            try {
                if (s.isNew()) {
                    if (find(s) == null) buildNew(s);
                } else {
                    addToExisting(s);
                }
            } catch (Throwable t) {
                SagaLog.error("system " + s.id + " (" + s.source + ") could not be built", t);
            }
        }
    }

    /** Every load: missing opt-in systems, additions to existing systems, then people (who may now find their market). */
    public static void onGameLoad() {
        for (SystemDef s : Saga.content().systems.values()) {
            try {
                if (s.isNew()) {
                    if (s.addToExistingSaves && find(s) == null) {
                        SagaLog.info("system " + s.id + " is missing from this save; building it (addToExistingSaves)");
                        buildNew(s);
                    }
                } else {
                    addToExisting(s);
                }
            } catch (Throwable t) {
                SagaLog.error("system " + s.id + " (" + s.source + ") could not be built", t);
            }
            if (Global.getSettings().isDevMode()) report(s);
        }
    }

    /** Dev mode: which of the JSON's entities this save has (a salvaged wreck, say, is gone). */
    private static void report(SystemDef s) {
        StringBuilder missing = new StringBuilder();
        int found = 0;
        for (SystemDef.Entity e : s.entities) {
            if (Global.getSector().getEntityById(e.id) != null) found++;
            else missing.append(missing.length() == 0 ? "" : ", ").append(e.id).append(" (").append(e.kind).append(')');
        }
        SagaLog.info("system " + s.id + ": " + found + "/" + s.entities.size() + " entities in this save" + (missing.length() == 0 ? "" : "; missing: " + missing));
    }

    /** The built system with this definition's tag, or null. */
    public static StarSystemAPI find(SystemDef s) {
        for (StarSystemAPI sys : Global.getSector().getStarSystems()) if (sys.hasTag(s.tag())) return sys;
        return null;
    }

    static StarSystemAPI existing(String id) {
        for (StarSystemAPI sys : Global.getSector().getStarSystems()) {
            if (id.equalsIgnoreCase(sys.getId()) || id.equalsIgnoreCase(sys.getBaseName())) return sys;
        }
        return null;
    }

    static void buildNew(SystemDef s) {
        StarSystemAPI sys = Global.getSector().createStarSystem(s.name);
        sys.addTag(s.tag());
        for (String t : s.tags) sys.addTag(t);
        sys.getLocation().set(s.hyperX, s.hyperY);
        sys.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
        PlanetAPI star = sys.initStar(s.id + "_star", s.starType, s.starRadius, s.corona);
        if (s.music != null && !s.music.isEmpty()) sys.getMemoryWithoutUpdate().set(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, s.music);
        SystemPlan.Order order = SystemPlan.buildOrder(s.entities, GAME);
        int n = build(sys, star, order, s);
        // listed jump points also need this to get their hyperspace side; the fringe one only when none is listed
        sys.autogenerateHyperspaceJumpPoints(true, !s.hasJumpPoint());
        SagaLog.info("built system " + s.id + " (" + s.name + ") with " + n + " entities at " + s.hyperX + ", " + s.hyperY);
    }

    static void addToExisting(SystemDef s) {
        StarSystemAPI sys = existing(s.id);
        if (sys == null) {
            SagaLog.warnOnce("nosystem:" + s.id, "system " + s.id + " (" + s.source + "): no star system with that id in this game; nothing added");
            return;
        }
        SystemPlan.Order order = SystemPlan.additions(s, GAME, GameMemory.global());
        if (order.entities.isEmpty() && order.problems.isEmpty()) return;
        int n = build(sys, sys.getStar() != null ? sys.getStar() : sys.getCenter(), order, s);
        if (s.music != null && !s.music.isEmpty() && !sys.getMemoryWithoutUpdate().contains(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY)) {
            sys.getMemoryWithoutUpdate().set(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, s.music);
        }
        if (n > 0) SagaLog.info("added " + n + " entities to system " + s.id);
    }

    private static int build(StarSystemAPI sys, SectorEntityToken star, SystemPlan.Order order, SystemDef s) {
        for (String p : order.problems) SagaLog.error("system " + s.id + " (" + s.source + "): " + p);
        int n = 0;
        for (SystemDef.Entity e : order.entities) {
            try {
                SectorEntityToken focus = "star".equals(e.orbit.focus) ? star : Global.getSector().getEntityById(e.orbit.focus);
                if (focus == null) {
                    SagaLog.error(e.where + ": orbit focus \"" + e.orbit.focus + "\" not found; " + e.id + " not built");
                    continue;
                }
                SectorEntityToken made = entity(sys, focus, e);
                if (made == null) continue;
                made.setCustomDescriptionId(e.id);
                if (e.market != null) market(made, e.market, e);
                GameMemory.global().set(SystemPlan.addedKey(e.id), Boolean.TRUE);
                n++;
            } catch (Throwable t) {
                SagaLog.error(e.where + ": " + e.id + " could not be built", t);
            }
        }
        return n;
    }

    private static String owner(SystemDef.Entity e) {
        if (e.faction != null && !e.faction.isEmpty()) return e.faction;
        if (e.market != null) return e.market.faction;
        return Factions.NEUTRAL;
    }

    private static String typeOr(SystemDef.Entity e, String def) { return e.type == null || e.type.isEmpty() ? def : e.type; }

    static SectorEntityToken entity(StarSystemAPI sys, SectorEntityToken focus, SystemDef.Entity e) {
        SystemDef.Orbit o = e.orbit;
        String k = e.kind;
        if ("planet".equals(k) || "moon".equals(k)) {
            return sys.addPlanet(e.id, focus, e.name, typeOr(e, "barren"), o.angle, e.radius, o.radius, o.days);
        }
        if ("asteroidBelt".equals(k)) {
            // vanilla belts are asteroid sprites plus band textures under them (Corvus, Askonia);
            // without the bands a belt is just a few scattered rocks in flight (found in game, 2026-11-11)
            float w = Math.max(e.width, 128f);
            sys.addRingBand(focus, "misc", "rings_asteroids0", 256f, 0, Color.white, w, o.radius - w * 0.25f, o.days * 1.05f);
            sys.addRingBand(focus, "misc", "rings_asteroids0", 256f, 3, Color.white, w, o.radius + w * 0.25f, o.days * 0.95f);
            SectorEntityToken t = sys.addAsteroidBelt(focus, e.count, o.radius, e.width, o.days * 0.9f, o.days * 1.1f, Terrain.ASTEROID_BELT, e.name);
            t.setId(e.id); // so markers, spawns and getEntityById find it by the JSON id
            return t;
        }
        if ("ring".equals(k)) {
            SectorEntityToken t = sys.addRingBand(focus, "misc", typeOr(e, "rings_dust0"), 256f, 0, Color.white, e.width, o.radius, o.days,
                    Terrain.RING, e.name);
            t.setId(e.id);
            return t;
        }
        if ("jumpPoint".equals(k)) {
            JumpPointAPI jp = Global.getFactory().createJumpPoint(e.id, e.name);
            jp.setCircularOrbit(focus, o.angle, o.radius, o.days);
            if (focus instanceof PlanetAPI && !((PlanetAPI) focus).isStar()) jp.setRelatedPlanet(focus);
            jp.setStandardWormholeToHyperspaceVisual();
            sys.addEntity(jp);
            return jp;
        }
        if ("derelict".equals(k)) {
            Random random = new Random(e.id.hashCode());
            DerelictShipData data = null;
            if (!e.type.isEmpty() && !Entities.WRECK.equals(e.type) && Global.getSettings().getVariant(e.type) != null) {
                data = DerelictShipEntityPlugin.createVariant(e.type, random, 0f);
            }
            if (data == null) data = DerelictShipEntityPlugin.createRandom(Factions.INDEPENDENT, null, random);
            SectorEntityToken t = sys.addCustomEntity(e.id, e.name, Entities.WRECK, Factions.NEUTRAL, data);
            t.setCircularOrbitWithSpin(focus, o.angle, o.radius, o.days, -10f, 10f);
            // always visible, like a salvage entity of type ALWAYS_VISIBLE: a quest points at it
            t.setDiscoverable(null);
            t.setSensorProfile(null);
            t.getMemoryWithoutUpdate().set(MemFlags.SALVAGE_SEED, random.nextLong());
            return t;
        }
        String type;
        if ("commRelay".equals(k)) type = typeOr(e, "comm_relay");
        else if ("navBuoy".equals(k)) type = typeOr(e, "nav_buoy");
        else if ("sensorArray".equals(k)) type = typeOr(e, "sensor_array");
        else type = e.type;
        if (type == null || type.isEmpty()) {
            SagaLog.error(e.where + ": " + k + " " + e.id + " needs a type (custom_entities.json id)");
            return null;
        }
        SectorEntityToken t = sys.addCustomEntity(e.id, e.name, type, owner(e));
        if ("station".equals(k)) t.setCircularOrbitPointingDown(focus, o.angle, o.radius, o.days);
        else t.setCircularOrbit(focus, o.angle, o.radius, o.days);
        return t;
    }

    static void market(SectorEntityToken entity, SystemDef.MarketDef md, SystemDef.Entity e) {
        if (Global.getSector().getEconomy().getMarket(md.id) != null) {
            SagaLog.warn(e.where + ": market " + md.id + " already exists; not created again");
            return;
        }
        if (Global.getSector().getFaction(md.faction) == null) {
            SagaLog.error(e.where + ": market " + md.id + ": unknown faction \"" + md.faction + "\"; no market");
            return;
        }
        MarketAPI m = Global.getFactory().createMarket(md.id, md.name, md.size);
        m.setFactionId(md.faction);
        m.setPrimaryEntity(entity);
        m.getTariff().modifyFlat("default_tariff", 0.3f);
        for (String c : md.conditions) {
            try {
                m.addCondition(c);
            } catch (Throwable t) {
                SagaLog.error(e.where + ": market " + md.id + ": condition \"" + c + "\" failed", t);
            }
        }
        for (String i : md.industries) {
            try {
                m.addIndustry(i);
            } catch (Throwable t) {
                SagaLog.error(e.where + ": market " + md.id + ": industry \"" + i + "\" failed", t);
            }
        }
        for (String sm : md.submarkets) {
            try {
                m.addSubmarket(sm);
            } catch (Throwable t) {
                SagaLog.error(e.where + ": market " + md.id + ": submarket \"" + sm + "\" failed", t);
            }
        }
        m.setFreePort(md.freePort);
        m.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        entity.setMarket(m);
        entity.setFaction(md.faction);
        Global.getSector().getEconomy().addMarket(m, true);
        for (String person : md.people) People.placeAt(person, m);
        SagaLog.info("market " + md.id + " (" + md.name + ", size " + md.size + ", " + md.faction + ") added to the economy");
    }
}
