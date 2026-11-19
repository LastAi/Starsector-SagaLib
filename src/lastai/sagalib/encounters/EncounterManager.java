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
        if (!lastai.sagalib.core.Mods.allowed(mem(), e.gate, "encounter", e.id)) return; // SagaLib 1.3
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
        withdraw(e, f);
    }

    /**
     * The fleet stops being the quest's enemy and goes away: not hostile, not chasing the player, not
     * marked, off to the nearest jump point to despawn. Used when it leaves (avoided) and after a
     * defeat it survived (found in game, 2026-11-16: a fleet beaten but not destroyed kept chasing
     * the player after the quest was done).
     */
    public static void withdraw(EncounterDef e, CampaignFleetAPI f) {
        if (f == null || !f.isAlive()) return;
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
        // SagaLib 1.1 (a named fleet): count its defeats, and its captain may die with it (only once per spawn)
        if (defeated && EncounterLogic.isSpawned(mem(), e.id)) {
            if (e.template != null) EncounterLogic.countDefeat(mem(), e.template);
            if (e.captainDiesOnDefeat && e.flagshipCommander != null && !e.flagshipCommander.isEmpty()) lastai.sagalib.game.People.retire(e.flagshipCommander, "the fleet of encounter " + e.id + " was defeated");
        }
        String trigger = EncounterLogic.resolve(mem(), e, Saga.content().quests, defeated, now());
        SagaLog.info("encounter " + e.id + ": " + (defeated ? "defeated" : "avoided") + (trigger != null ? "; firing " + trigger : ""));
        if (trigger != null) Triggers.add(mem(), trigger);
    }

    /** The manager tick (runtime script): spawns and stage-end despawns, and spawned encounters whose fleet is gone. */
    public static void tick() {
        double now = now();
        for (EncounterDef e : Saga.content().encounters.values()) {
            try {
                if (!lastai.sagalib.core.Mods.allowed(mem(), e.gate, "encounter", e.id)) continue; // SagaLib 1.3
                if ("ambush".equals(e.behavior)) spring(e);
                EncounterLogic.Action a = EncounterLogic.decide(e, mem(), now, Saga.content().quests, "player-near".equals(e.when) && playerNear(e));
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

    /** SagaLib 1.1: the player's fleet is in the spawn place's system, and within range (0 = anywhere there). */
    static boolean playerNear(EncounterDef e) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        SectorEntityToken at = place(e.at);
        if (player == null || at == null || player.getContainingLocation() != at.getContainingLocation()) return false;
        return e.range <= 0 || Misc.getDistance(player.getLocation(), at.getLocation()) <= e.range;
    }

    // ------------------------------------------------------------------ fleets

    static SectorEntityToken place(String id) {
        if (id == null || id.isEmpty()) return null;
        // SagaLib 1.2 (RC-R2): spawn at a quest's random target ($global.<quest>_<var>): the picked place
        if (id.startsWith("$global.")) return lastai.sagalib.quest.SagaQuest.targetEntity(lastai.sagalib.core.Targets.resolve(lastai.sagalib.game.GameMemory.global(), id));
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
        CampaignFleetAPI f = build(e, at.getLocationInHyperspace());
        if (f == null) return null;
        at.getContainingLocation().addEntity(f);
        Vector2f p = spawnPoint(at, near);
        f.setLocation(p.x, p.y);
        flags(e, f);
        // orbits and patrols go around the spawn point (a belt's own location is the star)
        SectorEntityToken around = p.equals(at.getLocation()) ? at : at.getContainingLocation().createToken(p.x, p.y);
        if ("ambush".equals(e.behavior) && near != null && near.getContainingLocation() == at.getContainingLocation()) around = near;
        assign(e, f, around);
        return f;
    }

    /**
     * SagaLib 1.2 (Phase 7e): a random encounter from an encounter pool at one of vanilla's
     * encounter points, the way vanilla's own EPEncounterCreators spawn theirs: lying in wait near
     * the point, despawned by vanilla's MissionFleetAutoDespawn once the player is far away. Its
     * comm dialog (scoped by FLEET_KEY) works as for any encounter; it has no quest outcome.
     */
    public static CampaignFleetAPI spawnAtPoint(EncounterDef e, com.fs.starfarer.api.campaign.LocationAPI where, Vector2f loc, Vector2f locInHyper) {
        CampaignFleetAPI f = build(e, locInHyper);
        if (f == null) return null;
        where.addEntity(f);
        f.setLocation(loc.x, loc.y);
        flags(e, f);
        SectorEntityToken around = where.createToken(Misc.getPointWithinRadius(loc, 1000f));
        f.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, around, 30f, "laying in wait");
        f.addScript(new com.fs.starfarer.api.impl.campaign.missions.hub.MissionFleetAutoDespawn(null, f));
        SagaLog.info("random encounter " + e.id + " spawned at an encounter point in " + where.getName());
        return f;
    }

    /** The fleet of an encounter (its ships, captain and name), not yet in the sector. */
    static CampaignFleetAPI build(EncounterDef e, Vector2f locInHyper) {
        if (Global.getSector().getFaction(e.faction) == null) {
            SagaLog.error("encounter " + e.id + " (" + e.source + "): unknown faction \"" + e.faction + "\"");
            return null;
        }
        CampaignFleetAPI f;
        if (e.explicit()) {
            f = FleetFactoryV3.createEmptyFleet(e.faction, e.fleetType, null);
            for (int i = 0; i < e.variants.size(); i++) {
                String v = e.variants.get(i);
                try {
                    if (Global.getSettings().getVariant(v) == null) throw new IllegalArgumentException("unknown variant");
                    FleetMemberAPI m = f.getFleetData().addFleetMember(v);
                    m.getRepairTracker().setCR(m.getRepairTracker().getMaxCR());
                    // SagaLib 1.1 (a named fleet): its ship names and its flagship
                    String name = i < e.shipNames.size() ? e.shipNames.get(i) : "";
                    if (name != null && !name.isEmpty()) m.setShipName(name);
                    if (i == e.flagship) {
                        for (FleetMemberAPI o : f.getFleetData().getMembersListCopy()) o.setFlagship(false);
                        m.setFlagship(true);
                    }
                } catch (Throwable t) {
                    SagaLog.error("encounter " + e.id + ": variant \"" + v + "\" skipped (" + t.getMessage() + ")");
                }
            }
        } else {
            // SagaLib 1.3 (task #62): the points may be scaled by a mod setting ($global.<setting>)
            float points = e.scaledPoints(lastai.sagalib.game.GameMemory.global());
            if (points != e.points) SagaLog.info("encounter " + e.id + ": " + e.points + " fleet points scaled to " + points + " by setting " + e.sizeScale.setting);
            FleetParamsV3 p = new FleetParamsV3(locInHyper, e.faction, e.quality, e.fleetType, points, 0f, 0f, 0f, 0f, 0f, 0f);
            p.ignoreMarketFleetSizeMult = true;
            f = FleetFactoryV3.createFleet(p);
        }
        if (f == null || f.getFleetData().getNumMembers() == 0) {
            SagaLog.error("encounter " + e.id + ": the fleet came out empty; not spawned");
            return null;
        }
        f.getFleetData().ensureHasFlagship();
        if (e.flagshipCommander != null && !e.flagshipCommander.isEmpty() && lastai.sagalib.game.People.isGone(e.flagshipCommander)) {
            SagaLog.info("encounter " + e.id + ": its captain " + e.flagshipCommander + " is gone; the fleet's own officer commands");
        } else if (e.flagshipCommander != null && !e.flagshipCommander.isEmpty()) {
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
        return f;
    }

    /** The encounter's memory flags on its fleet (who it fights, hostility, disengage, importance). */
    static void flags(EncounterDef e, CampaignFleetAPI f) {
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
