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
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.util.Misc;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Memory;
import lastai.sagalib.core.SystemPlan;
import lastai.sagalib.core.WorldChanges;
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
            if (!lastai.sagalib.core.Mods.allowed(GameMemory.global(), s.gate, "system", s.id)) continue; // SagaLib 1.3
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
    /** Nexerelin 0.12.2d (ExerelinConstants.MEMORY_KEY_UNINVADABLE): NexUtilsMarket.canBeInvaded is false while it is true. */
    public static final String UNINVADABLE_KEY = "$nex_uninvadable";

    /**
     * SagaLib 1.3 (task #61): on every load, the mod's markets marked "protectFromInvasion" get
     * $nex_uninvadable (also in saves made before the mark was added; never unset by SagaLib, so
     * another mod's protection stays).
     */
    public static void protectMarkets() {
        for (SystemDef s : Saga.content().systems.values()) {
            for (SystemDef.Entity e : s.entities) {
                if (e.market == null || !e.market.protectFromInvasion) continue;
                com.fs.starfarer.api.campaign.SectorEntityToken t = Global.getSector().getEntityById(e.id);
                MarketAPI m = t != null ? t.getMarket() : Global.getSector().getEconomy().getMarket(e.market.id);
                if (m == null || m.getMemoryWithoutUpdate().getBoolean(UNINVADABLE_KEY)) continue;
                m.getMemoryWithoutUpdate().set(UNINVADABLE_KEY, true);
                SagaLog.info("market " + m.getId() + ": protected from invasion ($nex_uninvadable)");
            }
        }
    }

    public static void onGameLoad() {
        for (SystemDef s : Saga.content().systems.values()) {
            if (!lastai.sagalib.core.Mods.allowed(GameMemory.global(), s.gate, "system", s.id)) continue; // SagaLib 1.3
            try {
                if (s.isNew()) {
                    if (s.addToExistingSaves && find(s) == null) {
                        SagaLog.info("system " + s.id + " is missing from this save; building it (addToExistingSaves)");
                        buildNew(s);
                    }
                    // built by an older SagaLib, before hyperspace was cleared around new systems: once now
                    StarSystemAPI built = find(s);
                    if (built != null && GameMemory.global().get(clearedKey(built)) == null) clearHyperspace(built);
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
        SectorEntityToken star;
        if (s.starType == null) {
            // SagaLib 1.1: no star, an empty centre and a light colour (vanilla's black site)
            star = sys.initNonStarCenter();
            sys.setLightColor(new Color(s.light[0], s.light[1], s.light[2]));
        } else {
            star = sys.initStar(s.id + "_star", s.starType, s.starRadius, s.corona);
        }
        if (s.music != null && !s.music.isEmpty()) sys.getMemoryWithoutUpdate().set(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, s.music);
        // deferred entities (appears) are placed by their quest later (WorldChanges)
        SystemPlan.Order order = SystemPlan.buildOrder(WorldChanges.initial(s.entities), GAME);
        int n = build(sys, star, order, s);
        // listed jump points also need this to get their hyperspace side; the fringe one only when none is listed
        sys.autogenerateHyperspaceJumpPoints(true, !s.hasJumpPoint());
        clearHyperspace(sys);
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
            if (!lastai.sagalib.core.Mods.allowed(GameMemory.global(), e.gate, "object", e.id)) continue; // SagaLib 1.3
            try {
                SectorEntityToken focus = "star".equals(e.orbit.focus) ? star : Global.getSector().getEntityById(e.orbit.focus);
                if (focus == null) {
                    SagaLog.error(e.where + ": orbit focus \"" + e.orbit.focus + "\" not found; " + e.id + " not built");
                    continue;
                }
                SectorEntityToken made = entity(sys, focus, e);
                if (made == null) continue;
                made.setCustomDescriptionId(e.id);
                salvage(made, e.loot, e.defenders);
                if (e.market != null) market(made, e.market, e);
                else if (!e.conditions.isEmpty() || e.surveyed != null) conditions(made, e);
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

    /** SagaLib 1.1: an uncolonised planet's conditions and survey level (a condition-only market, as vanilla's). */
    static void conditions(SectorEntityToken made, SystemDef.Entity e) {
        if (!(made instanceof PlanetAPI)) {
            SagaLog.warn(e.where + ": conditions only apply to planets and moons; ignored");
            return;
        }
        PlanetAPI planet = (PlanetAPI) made;
        Misc.initConditionMarket(planet);
        MarketAPI m = planet.getMarket();
        for (String c : e.conditions) {
            try { m.addCondition(c); } catch (Throwable t) { SagaLog.error(e.where + ": condition \"" + c + "\" failed", t); }
        }
        if ("full".equals(e.surveyed)) m.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        else if ("preliminary".equals(e.surveyed)) m.setSurveyLevel(MarketAPI.SurveyLevel.PRELIMINARY);
        else if ("none".equals(e.surveyed)) m.setSurveyLevel(MarketAPI.SurveyLevel.NONE);
    }

    // ------------------------------------------------------------------ SagaLib 1.1: world changes

    /** Place what is due and remove what is due (after a step change, on load, in the manager tick). */
    public static void applyWorldChanges() {
        Memory mem = GameMemory.global();
        for (WorldChanges.Placement p : WorldChanges.toPlace(Saga.content().systems.values(), mem)) place(p, "its quest reached stage " + p.entity.appears.stage);
        for (SystemDef.Removal r : WorldChanges.toRemove(Saga.content().systems.values(), mem)) remove(r.target, "decivilize".equals(r.market), r.where);
        applyEntityFlags(mem);
    }

    /**
     * SagaLib 1.1: quests' entity flags, recomputed from the steps (so a load or a jump is right too).
     * Important flags also make the entity stand out on the map (Misc.makeImportant), as vanilla's missions do.
     */
    static void applyEntityFlags(Memory mem) {
        for (WorldChanges.FlagState s : WorldChanges.entityFlags(Saga.content().quests.values(), mem)) {
            // the entity may be a random target ($global.<quest>_<var>, SagaLib 1.2), or its market
            String eid = lastai.sagalib.core.Targets.resolve(mem, s.flag.entity);
            if (eid == null) continue;
            SectorEntityToken t = Global.getSector().getEntityById(eid);
            if (t == null && Global.getSector().getEconomy().getMarket(eid) != null) t = Global.getSector().getEconomy().getMarket(eid).getPrimaryEntity();
            if (t == null) continue;
            com.fs.starfarer.api.campaign.rules.MemoryAPI em = t.getMemoryWithoutUpdate();
            boolean has = em.getBoolean(s.flag.flag);
            if (s.on && !has) {
                em.set(s.flag.flag, true);
                if (s.flag.important) Misc.makeImportant(t, s.flag.flag);
            } else if (!s.on && has) {
                em.unset(s.flag.flag);
                if (s.flag.important) Misc.makeUnimportant(t, s.flag.flag);
            }
        }
    }

    /** SF_PlaceEntity: place a deferred entity now (once). */
    public static boolean placeById(String id, String why) {
        WorldChanges.Placement p = WorldChanges.find(Saga.content().systems.values(), id);
        if (p == null) { SagaLog.error(why + ": no entity \"" + id + "\" in any system file"); return false; }
        if (!p.entity.deferred()) { SagaLog.warn(why + ": " + id + " is built with its system; nothing to place"); return false; }
        if (WorldChanges.placed(GameMemory.global(), id)) return true;
        return place(p, why);
    }

    static boolean place(WorldChanges.Placement p, String why) {
        Memory mem = GameMemory.global();
        SystemDef.Entity e = p.entity;
        try {
            if (Global.getSector().getEntityById(e.id) != null) { mem.set(WorldChanges.placedKey(e.id), Boolean.TRUE); return true; }
            StarSystemAPI sys = p.system.isNew() ? find(p.system) : existing(p.system.id);
            if (sys == null) {
                SagaLog.warnOnce("place:" + e.id, e.where + ": its system is not in this game; " + e.id + " not placed");
                mem.set(WorldChanges.placedKey(e.id), Boolean.TRUE);
                return false;
            }
            SectorEntityToken center = sys.getStar() != null ? sys.getStar() : sys.getCenter();
            SectorEntityToken old = e.replaces != null ? Global.getSector().getEntityById(e.replaces) : null;
            SectorEntityToken focus;
            SystemDef.Entity build = e;
            if (old != null && old.getOrbitFocus() != null) {
                // take the replaced entity's place: same focus, where it is now on its orbit
                focus = old.getOrbitFocus();
                build = copyWithOrbit(e, old.getCircularOrbitAngle(), old.getCircularOrbitRadius(), old.getCircularOrbitPeriod());
            } else {
                focus = "star".equals(e.orbit.focus) ? center : Global.getSector().getEntityById(e.orbit.focus);
            }
            if (focus == null) { SagaLog.error(e.where + ": orbit focus \"" + e.orbit.focus + "\" not found; " + e.id + " not placed"); return false; }
            SectorEntityToken made = entity(sys, focus, build);
            if (made == null) return false;
            made.setCustomDescriptionId(e.id);
            salvage(made, e.loot, e.defenders);
            if (e.market != null) market(made, e.market, e);
            else if (!e.conditions.isEmpty() || e.surveyed != null) conditions(made, e);
            mem.set(WorldChanges.placedKey(e.id), Boolean.TRUE);
            mem.set(SystemPlan.addedKey(e.id), Boolean.TRUE);
            SagaLog.info("placed " + e.id + " in " + p.system.id + " (" + why + ")");
            if (e.replaces != null) {
                SystemDef.Removal r = WorldChanges.removal(Saga.content().systems.values(), e.replaces);
                remove(e.replaces, r != null && "decivilize".equals(r.market), e.where + " (replaces)");
            }
            return true;
        } catch (Throwable t) {
            SagaLog.error(e.where + ": " + e.id + " could not be placed", t);
            mem.set(WorldChanges.placedKey(e.id), Boolean.TRUE); // don't retry every tick
            return false;
        }
    }

    static SystemDef.Entity copyWithOrbit(SystemDef.Entity e, float angle, float radius, float days) {
        SystemDef.Entity c = new SystemDef.Entity();
        c.id = e.id; c.kind = e.kind; c.type = e.type; c.name = e.name; c.radius = e.radius; c.width = e.width; c.count = e.count;
        c.faction = e.faction; c.market = e.market; c.where = e.where;
        c.orbit.focus = e.orbit.focus; c.orbit.angle = angle; c.orbit.radius = radius; c.orbit.days = days > 0 ? days : e.orbit.days;
        return c;
    }

    /**
     * Remove an entity (it fades out). Guardrails: it must exist; one with a market is only removed
     * when decivilize is set (vanilla's DecivTracker), else it stays and an error is logged.
     */
    public static boolean remove(String id, boolean decivilize, String why) {
        Memory mem = GameMemory.global();
        if (WorldChanges.removed(mem, id)) return true;
        SectorEntityToken t = Global.getSector().getEntityById(id);
        if (t == null) {
            SagaLog.warn(why + ": no entity \"" + id + "\" in this game; nothing to remove");
            mem.set(WorldChanges.removedKey(id), Boolean.TRUE);
            return false;
        }
        MarketAPI m = t.getMarket();
        if (m != null && !m.isPlanetConditionMarketOnly()) {
            if (!decivilize) {
                SagaLog.error(why + ": " + id + " has the market " + m.getId() + "; it is only removed with \"market\": \"decivilize\". Left in place.");
                mem.set(WorldChanges.removedKey(id), Boolean.TRUE);
                return false;
            }
            DecivTracker.decivilize(m, true, false);
        }
        mem.set(WorldChanges.removedKey(id), Boolean.TRUE);
        if (t.getContainingLocation() != null) Misc.fadeAndExpire(t);
        SagaLog.info("removed " + id + " (" + why + ")");
        return true;
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
        if (md.protectFromDeciv) m.getMemoryWithoutUpdate().set(com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker.NO_DECIV_KEY, true);
        if (md.protectFromInvasion) m.getMemoryWithoutUpdate().set(UNINVADABLE_KEY, true);
        m.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        entity.setMarket(m);
        entity.setFaction(md.faction);
        Global.getSector().getEconomy().addMarket(m, true);
        for (String person : md.people) People.placeAt(person, m);
        SagaLog.info("market " + md.id + " (" + md.name + ", size " + md.size + ", " + md.faction + ") added to the economy");
    }

    /** SagaLib 1.2 (RC-F7): an entity's salvage loot (vanilla drop groups) and defenders. */
    public static void salvage(SectorEntityToken t, java.util.List<SystemDef.Loot> loot, SystemDef.Defenders d) {
        for (SystemDef.Loot l : loot) {
            if (l.chances > 0) t.addDropRandom(l.group, l.chances);
            if (l.value > 0) t.addDropValue(l.group, l.value);
        }
        if (d != null) Misc.setDefenderOverride(t, new com.fs.starfarer.api.impl.campaign.procgen.DefenderDataOverride(d.faction, d.prob, d.minStr, d.maxStr, d.maxSize));
    }

    /**
     * Clears hyperspace's storm clouds around a new system, exactly as vanilla's SectorGen does for its
     * own systems (it runs before mods build theirs, so ours were left inside the clouds; reported
     * 2026-11-16). Vanilla: NebulaEditor on the HyperspaceTerrainPlugin, two clearArc calls.
     */
    static String clearedKey(StarSystemAPI sys) { return "$global.sagaforge_hyperclear_" + sys.getId(); }

    static void clearHyperspace(StarSystemAPI sys) {
        try {
            com.fs.starfarer.api.campaign.CampaignTerrainAPI terrain = Misc.getHyperspaceTerrain();
            if (terrain == null || !(terrain.getPlugin() instanceof com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin)) return;
            com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin plugin = (com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin) terrain.getPlugin();
            com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor editor = new com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor(plugin);
            float minRadius = plugin.getTileSize() * 2f;
            float radius = sys.getMaxRadiusInHyperspace() * 0.5f;
            float x = sys.getLocation().x, y = sys.getLocation().y;
            editor.clearArc(x, y, 0, radius + minRadius * 0.5f, 0, 360f);
            editor.clearArc(x, y, 0, radius + minRadius, 0, 360f, 0.25f);
            GameMemory.global().set(clearedKey(sys), Boolean.TRUE);
        } catch (Throwable t) {
            SagaLog.error("system " + sys.getId() + ": clearing hyperspace around it failed", t);
        }
    }

    /**
     * SagaLib 1.2, new game only (after vanilla's onNewGameAfterEconomyLoad): vanilla's
     * updateKnownPlanets marks every planet in a system with a colony fully surveyed and its ruins
     * explored. Planets that say ruinsUnexplored get their ruins back, and their own survey level.
     */
    public static void restoreRuins() {
        for (SystemDef s : Saga.content().systems.values()) {
            if (!lastai.sagalib.core.Mods.allowed(GameMemory.global(), s.gate, "system", s.id)) continue; // SagaLib 1.3
            for (SystemDef.Entity e : s.entities) {
                if (!e.ruinsUnexplored) continue;
                SectorEntityToken t = Global.getSector().getEntityById(e.id);
                MarketAPI m = t == null ? null : t.getMarket();
                if (m == null || !m.isPlanetConditionMarketOnly()) continue;
                m.getMemoryWithoutUpdate().unset("$ruinsExplored");
                if ("none".equals(e.surveyed)) m.setSurveyLevel(MarketAPI.SurveyLevel.NONE);
                else if ("preliminary".equals(e.surveyed)) m.setSurveyLevel(MarketAPI.SurveyLevel.PRELIMINARY);
                SagaLog.info("ruins of " + e.id + " stay unexplored");
            }
        }
    }

    /**
     * New game, after vanilla's random systems exist: each of the mods' new systems and its closest
     * other system. Below 3,000 units (vanilla keeps its own at least 3,065 apart) it is a warning.
     */
    public static void reportSpacing() {
        for (SystemDef s : Saga.content().systems.values()) {
            if (!lastai.sagalib.core.Mods.allowed(GameMemory.global(), s.gate, "system", s.id)) continue; // SagaLib 1.3
            if (!s.isNew()) continue;
            StarSystemAPI sys = find(s);
            if (sys == null) continue;
            StarSystemAPI near = null;
            float best = Float.MAX_VALUE;
            for (StarSystemAPI o : Global.getSector().getStarSystems()) {
                if (o == sys) continue;
                float d = Misc.getDistance(o.getLocation(), sys.getLocation());
                if (d < best) { best = d; near = o; }
            }
            if (near == null) continue;
            String msg = "system " + s.id + ": closest other system after random generation is " + near.getBaseName() + " at " + Math.round(best) + " units";
            if (best < 3000f) SagaLog.warn(msg + " (closer than vanilla keeps its own; move it in Saga Forge)");
            else SagaLog.info(msg);
        }
    }
}
