package lastai.sagalib.game;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import lastai.sagalib.core.Plugins;
import lastai.sagalib.core.QuietOps;
import lastai.sagalib.encounters.EncounterManager;
import lastai.sagalib.systems.SystemBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SagaLib 1.4 (docs/FORMAT.md, "Quiet ops"): runs a world event's or progress stage's op list
 * without a dialog, through the same methods as the rule commands (SF_Spawn, SF_PlaceEntity,
 * SF_Start / SF_Step, SF_Progress, SF_RunPlugin, the rewards). The memory map is the one
 * TriggerDialog's dialog had: local = the player fleet's memory, faction = the player's faction.
 */
public final class QuietRunner {
    private QuietRunner() {}

    /** The memory a quiet op sees: what the dialog these actions used to run in had (its target: the player's fleet). */
    public static Map<String, MemoryAPI> memoryMap() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        Map<String, MemoryAPI> map = new HashMap<String, MemoryAPI>();
        map.put(MemKeys.LOCAL, player.getMemoryWithoutUpdate());
        map.put(MemKeys.FACTION, player.getFaction().getMemoryWithoutUpdate());
        map.put(MemKeys.GLOBAL, Global.getSector().getMemoryWithoutUpdate());
        map.put(MemKeys.PLAYER, Global.getSector().getCharacterData().getMemoryWithoutUpdate());
        return map;
    }

    /** False when the campaign isn't up yet (try again later); true when run. */
    public static boolean run(String trigger, List<QuietOps.Op> ops) {
        if (Global.getCurrentState() != GameState.CAMPAIGN || Global.getSector().getPlayerFleet() == null) return false;
        final String where = "quiet actions of " + trigger;
        final Map<String, MemoryAPI> map = memoryMap();
        final GameMemory mem = new GameMemory(map);
        QuietOps.run(ops, mem, new Host(where, map, mem), where);
        return true;
    }

    static final class Host implements QuietOps.Host {
        private final String where;
        private final Map<String, MemoryAPI> map;
        private final GameMemory mem;
        private final GameEffects fx = GameEffects.withMessages();

        Host(String where, Map<String, MemoryAPI> map, GameMemory mem) {
            this.where = where;
            this.map = map;
            this.mem = mem;
        }

        public void credits(long amount) { fx.credits(amount); }

        public void item(String id, String kind, int qty) { fx.item(id, qty); }

        public void factionRep(String faction, double points) { fx.factionRep(faction, points); }

        public void personRep(String person, double points) { fx.personRep(person, points); }

        public void storyPoints(int points) { fx.storyPoints(points); }

        public void xp(long xp) { fx.xp(xp); }

        public void spawn(String encounter) { EncounterManager.spawn(encounter, where, null); }

        public void despawn(String encounter) { EncounterManager.despawn(encounter, where, null); }

        public void placeEntity(String entity) { SystemBuilder.placeById(entity, where); }

        public void removeEntity(String entity, boolean decivilize) { SystemBuilder.remove(entity, decivilize, where); }

        public void startQuest(String quest, String stage) {
            QuestRuntime.start(quest, where, null, map, false);
            if (stage != null) QuestRuntime.step(quest, stage, where, null, map, false);
        }

        public void progress(String event, int points, String text) { SagaProgressEvent.oneTime(event, points, text, null, where); }

        public void plugin(String className, Map<String, String> args) {
            Plugins.Call call = new Plugins.Call(className, null, null, args);
            Plugins.run(mem, call, PluginRunner.host(PluginRunner.GAME, where, null, map), where);
        }
    }
}
