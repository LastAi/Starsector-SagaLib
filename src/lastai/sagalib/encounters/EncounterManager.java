package lastai.sagalib.encounters;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.EncounterLogic;
import lastai.sagalib.core.Memory;
import lastai.sagalib.core.QuestLogic;
import lastai.sagalib.core.Triggers;
import lastai.sagalib.game.GameMemory;
import lastai.sagalib.game.Saga;
import lastai.sagalib.model.EncounterDef;
import lastai.sagalib.model.QuestDef;

/**
 * Encounters in game (docs/FORMAT.md, "Encounters"): spawning fleets, the manager tick
 * (quest-stage, new-game, periodic spawns; despawnOnStageEnd) and outcomes. State is in memory
 * keys (EncounterLogic) and the fleet's own memory ($sagaforge_encounter); nothing else is saved.
 */
public final class EncounterManager {
    private EncounterManager() {}

    /** Fleet memory key; comm dialog rules test $entity.sagaforge_encounter == &lt;id&gt;. */
    public static final String FLEET_KEY = "$sagaforge_encounter";
    private static final float FOREVER = 1000000f;

    static EncounterDef def(String id) { return Saga.content().encounters.get(id); }

    static Memory mem() { return GameMemory.global(); }

    /** SagaLib's day counter (advanced by the runtime script); timers and periodic spawns use it. */
    public static final String DAYS_KEY = "$global.sagaforge_days";

    public static double now() {
        Object d = mem().get(DAYS_KEY);
        return d instanceof Number ? ((Number) d).doubleValue() : 0;
    }

    /** The spawned fleet of an encounter, or null. */
    public static CampaignFleetAPI fleet(String encounterId) {
        for (LocationAPI loc : Global.getSector().getAllLocations()) {
            for (CampaignFleetAPI f : loc.getFleets()) {
                MemoryAPI m = f.getMemoryWithoutUpdate();
                if (m.contains(FLEET_KEY) && encounterId.equals(m.getString(FLEET_KEY)) && f.isAlive() && !f.isDespawning()) return f;
            }
        }
        return null;
    }

    /** The encounter id of a fleet, or null. */
    public static String idOf(CampaignFleetAPI f) {
        if (f == null) return null;
        MemoryAPI m = f.getMemoryWithoutUpdate();
        return m.contains(FLEET_KEY) ? m.getString(FLEET_KEY) : null;
    }

    /** SF_Spawn: spawns it now (unless it is already out), for the quest that lists it, at that quest's step. */
    public static void spawn(String encounterId, String ruleId, InteractionDialogAPI dialog) {
        EncounterDef e = def(encounterId);
        if (e == null) {
            SagaLog.error("SF_Spawn in rule " + ruleId + ": unknown encounter \"" + encounterId + "\"");
            return;
        }
        if (fleet(e.id) != null) {
            SagaLog.info("SF_Spawn " + e.id + " (rule " + ruleId + "): already spawned");
            return;
        }
        spawnNow(e);
    }

    static CampaignFleetAPI spawnNow(EncounterDef e) {
        QuestDef q = EncounterLogic.questFor(e, Saga.content().quests);
        String quest = q == null ? null : q.id;
        String stage = quest == null ? null : (e.quest != null ? e.stage : QuestLogic.step(mem(), quest));
        CampaignFleetAPI f = create(e, marker(q, stage));
        if (f == null) return null;
        EncounterLogic.spawned(mem(), e, quest, stage);
        SagaLog.info("encounter " + e.id + " spawned at " + e.at + (quest != null ? " for " + quest + " at step " + stage : ""));
        return f;
    }

    /** SF_Despawn: the fleet leaves peacefully; outcome avoided (fired once the dialog is closed). */
    public static void despawn(String encounterId, String ruleId, InteractionDialogAPI dialog) {
        EncounterDef e = def(encounterId);
        if (e == null) {
            SagaLog.error("SF_Despawn in rule " + ruleId + ": unknown encounter \"" + encounterId + "\"");
            return;
        }
        CampaignFleetAPI f = fleet(e.id);
        if (f == null && !EncounterLogic.isSpawned(mem(), e.id)) {
            SagaLog.warn("SF_Despawn " + e.id + " (rule " + ruleId + "): it isn't spawned");
            return;
        }
        leave(e, f);
    }

    /** Resolves the encounter as avoided and sends the fleet away without a fight. */
    static void leave(EncounterDef e, CampaignFleetAPI f) {
        resolve(e, false);
        if (f == null) return;
        MemoryAPI m = f.getMemoryWithoutUpdate();
        m.unset(MemFlags.MEMORY_KEY_MAKE_HOSTILE);
        m.unset(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE);
        m.unset(MemFlags.MEMORY_KEY_PURSUE_PLAYER);
        m.set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        Misc.makeUnimportant(f, "sagaforge_" + e.id);
        f.clearAssignments();
        SectorEntityToken exit = Misc.findNearestJumpPointTo(f);
        if (exit != null) f.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, exit, 30f);
        else Misc.fadeAndExpire(f);
    }

    /** Records the outcome once and queues its trigger. */
    public static void resolve(EncounterDef e, boolean defeated) {
        String trigger = EncounterLogic.resolve(mem(), e, Saga.content().quests, defeated, now());
        SagaLog.info("encounter " + e.id + ": " + (defeated ? "defeated" : "avoided") + (trigger != null ? "; firing " + trigger : ""));
        if (trigger != null) Triggers.add(mem(), trigger);
    }

    /** The manager tick (runtime script): spawns and stage-end despawns, and spawned encounters whose fleet is gone. */
    public static void tick() {
        double now = now();
        for (EncounterDef e : Saga.content().encounters.values()) {
            try {
                if ("ambush".equals(e.behavior)) spring(e);
                EncounterLogic.Action a = EncounterLogic.decide(e, mem(), now, Saga.content().quests);
                if (a == EncounterLogic.Action.SPAWN) {
                    if (fleet(e.id) == null && spawnNow(e) == null) {
                        // can't spawn (bad location or faction): don't retry every tick, fire nothing
                        EncounterLogic.failed(mem(), e, now);
                    }
                } else if (a == EncounterLogic.Action.DESPAWN) {
                    SagaLog.info("encounter " + e.id + ": its quest left the stage; the fleet leaves");
                    leave(e, fleet(e.id));
                } else if (EncounterLogic.isSpawned(mem(), e.id) && fleet(e.id) == null) {
                    // gone without the listener seeing it (removed by another mod, an old save)
                    resolve(e, false);
                }
            } catch (Throwable t) {
                SagaLog.errorOnce("enc-tick:" + e.id, "encounter " + e.id + ": the manager failed", t);
            }
        }
    }

    // ------------------------------------------------------------------ fleets

    static SectorEntityToken place(String id) {
        if (id == null || id.isEmpty()) return null;
        SectorEntityToken t = Global.getSector().getEntityById(id);
        if (t != null) return t;
        MarketAPI m = Global.getSector().getEconomy().getMarket(id);
        return m == null ? null : m.getPrimaryEntity();
    }

    /** The map marker of the quest's stage, or null. */
    static SectorEntityToken marker(QuestDef q, String stage) {
        if (q == null || stage == null || q.stage(stage) == null) return null;
        return place(q.stage(stage).marker);
    }

    /**
     * Where the fleet appears. A ring or asteroid belt's own location is its centre (the star),
     * so a fleet "at the belt" goes onto the band: the point nearest the quest stage's marker if
     * that is in the same system, else a random point (found in game, 2026-11-11: the ambush sat
     * at the star and Haven's patrols destroyed it).
     */
    static Vector2f spawnPoint(SectorEntityToken at, SectorEntityToken near) {
        Vector2f c = at.getLocation();
        if (at instanceof CampaignTerrainAPI && ((CampaignTerrainAPI) at).getPlugin() instanceof BaseRingTerrain) {
            BaseRingTerrain ring = (BaseRingTerrain) ((CampaignTerrainAPI) at).getPlugin();
            if (ring.params != null && ring.params.middleRadius > 0) {
                SectorEntityToken focus = ring.params.relatedEntity != null ? ring.params.relatedEntity : at.getOrbitFocus();
                Vector2f centre = focus != null ? focus.getLocation() : c;
                float angle = near != null && near.getContainingLocation() == at.getContainingLocation()
                        ? Misc.getAngleInDegrees(centre, near.getLocation()) + 8f // a little ahead of the marker, not on it
                        : (float) Math.random() * 360f;
                Vector2f u = Misc.getUnitVectorAtDegreeAngle(angle);
                return new Vector2f(centre.x + u.x * ring.params.middleRadius, centre.y + u.y * ring.params.middleRadius);
            }
        }
        return new Vector2f(c);
    }

    /** Ambush fleets lie in wait; when the player comes close they attack. */
    static void spring(EncounterDef e) {
        CampaignFleetAPI f = fleet(e.id);
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (f == null || player == null || player.getContainingLocation() != f.getContainingLocation()) return;
        MemoryAPI m = f.getMemoryWithoutUpdate();
        if (m.getBoolean("$sagaforge_sprung") || m.getBoolean(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE)) return;
        if (Misc.getDistance(f, player) > AMBUSH_RANGE) return;
        m.set("$sagaforge_sprung", true);
        m.set(MemFlags.MEMORY_KEY_PURSUE_PLAYER, true);
        f.clearAssignments();
        f.addAssignment(FleetAssignment.INTERCEPT, player, 20f);
        f.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, player, FOREVER);
        SagaLog.info("encounter " + e.id + ": the ambush springs");
    }

    static final float AMBUSH_RANGE = 1600f;

    static CampaignFleetAPI create(EncounterDef e, SectorEntityToken near) {
        SectorEntityToken at = place(e.at);
        if (at == null || at.getContainingLocation() == null) {
            SagaLog.error("encounter " + e.id + " (" + e.source + "): spawn.at \"" + e.at + "\" is not an entity or market in this game");
            return null;
        }
        if (Global.getSector().getFaction(e.faction) == null) {
            SagaLog.error("encounter " + e.id + " (" + e.source + "): unknown faction \"" + e.faction + "\"");
            return null;
        }
        CampaignFleetAPI f;
        if (e.explicit()) {
            f = FleetFactoryV3.createEmptyFleet(e.faction, e.fleetType, null);
            for (String v : e.variants) {
                try {
                    if (Global.getSettings().getVariant(v) == null) throw new IllegalArgumentException("unknown variant");
                    FleetMemberAPI m = f.getFleetData().addFleetMember(v);
                    m.getRepairTracker().setCR(m.getRepairTracker().getMaxCR());
                } catch (Throwable t) {
                    SagaLog.error("encounter " + e.id + ": variant \"" + v + "\" skipped (" + t.getMessage() + ")");
                }
            }
        } else {
            FleetParamsV3 p = new FleetParamsV3(at.getLocationInHyperspace(), e.faction, e.quality, e.fleetType, e.points, 0f, 0f, 0f, 0f, 0f, 0f);
            p.ignoreMarketFleetSizeMult = true;
            f = FleetFactoryV3.createFleet(p);
        }
        if (f == null || f.getFleetData().getNumMembers() == 0) {
            SagaLog.error("encounter " + e.id + ": the fleet came out empty; not spawned");
            return null;
        }
        f.getFleetData().ensureHasFlagship();
        if (e.flagshipCommander != null && !e.flagshipCommander.isEmpty()) {
            PersonAPI c = Global.getSector().getImportantPeople().getPerson(e.flagshipCommander);
            if (c == null) {
                SagaLog.warn("encounter " + e.id + ": flagship commander \"" + e.flagshipCommander + "\" doesn't exist; the fleet's own officer commands");
            } else {
                f.setCommander(c);
                if (f.getFlagship() != null) f.getFlagship().setCaptain(c);
            }
        }
        f.getFleetData().setSyncNeeded();
        if (e.fleetName != null && !e.fleetName.isEmpty()) {
            f.setName(e.fleetName);
            f.setNoFactionInName(true);
        }
        at.getContainingLocation().addEntity(f);
        Vector2f p = spawnPoint(at, near);
        f.setLocation(p.x, p.y);

        MemoryAPI m = f.getMemoryWithoutUpdate();
        m.set(FLEET_KEY, e.id);
        // a quest's fleet isn't fought or chased by patrols and traders, and doesn't chase them (as vanilla's mission fleets)
        m.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        m.set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        m.set(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER, true); // as MagicLib's bounty fleets: without it they ignore the player too
        if (e.hostile) {
            m.set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
            m.set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        }
        if (e.noRepImpact) m.set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
        if (e.allowDisengage) m.set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
        else m.set(MemFlags.MEMORY_KEY_MAKE_PREVENT_DISENGAGE, true);
        if (e.important) Misc.makeImportant(f, "sagaforge_" + e.id);
        if ("ambush".equals(e.behavior)) f.setTransponderOn(false);
        // orbits and patrols go around the spawn point (a belt's own location is the star)
        SectorEntityToken around = p.equals(at.getLocation()) ? at : at.getContainingLocation().createToken(p.x, p.y);
        if ("ambush".equals(e.behavior) && near != null && near.getContainingLocation() == at.getContainingLocation()) around = near;
        assign(e, f, around);
        return f;
    }

    static void assign(EncounterDef e, CampaignFleetAPI f, SectorEntityToken at) {
        String b = e.behavior;
        if ("guard".equals(b)) {
            f.addAssignment(FleetAssignment.DEFEND_LOCATION, at, FOREVER);
        } else if ("intercept-player".equals(b)) {
            f.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PURSUE_PLAYER, true);
            f.addAssignment(FleetAssignment.INTERCEPT, Global.getSector().getPlayerFleet(), 30f);
            f.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, at, FOREVER);
        } else if ("travel".equals(b)) {
            SectorEntityToken to = place(e.travelTo);
            if (to == null) {
                SagaLog.warn("encounter " + e.id + ": travelTo \"" + e.travelTo + "\" not found; it patrols instead");
                f.addAssignment(FleetAssignment.PATROL_SYSTEM, at, FOREVER);
            } else {
                f.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, to, FOREVER);
            }
        } else if ("ambush".equals(b)) {
            f.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, at, FOREVER);
        } else {
            f.addAssignment(FleetAssignment.PATROL_SYSTEM, at, FOREVER);
        }
    }
}
