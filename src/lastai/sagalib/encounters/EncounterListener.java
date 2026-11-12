package lastai.sagalib.encounters;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.EncounterLogic;
import lastai.sagalib.game.GameMemory;
import lastai.sagalib.game.Saga;
import lastai.sagalib.model.EncounterDef;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Encounter outcomes: beaten in a battle with the player = defeated; despawned any other way =
 * avoided. Transient (added on every load), so saves never reference it.
 */
public final class EncounterListener extends BaseCampaignEventListener {
    private EncounterListener() { super(false); }

    public static void register() { Global.getSector().addTransientListener(new EncounterListener()); }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        try {
            if (battle == null || !battle.isPlayerInvolved()) return;
            Set<CampaignFleetAPI> enemies = new LinkedHashSet<CampaignFleetAPI>();
            if (battle.getNonPlayerSideSnapshot() != null) enemies.addAll(battle.getNonPlayerSideSnapshot());
            if (battle.getNonPlayerSide() != null) enemies.addAll(battle.getNonPlayerSide());
            boolean playerWon = primaryWinner != null && battle.isOnPlayerSide(primaryWinner);
            for (CampaignFleetAPI f : enemies) {
                String id = EncounterManager.idOf(f);
                if (id == null) continue;
                EncounterDef e = Saga.content().encounters.get(id);
                if (e == null || !EncounterLogic.isSpawned(GameMemory.global(), id)) continue;
                if (playerWon || battle.wasFleetDefeated(f, primaryWinner)) EncounterManager.resolve(e, true);
            }
        } catch (Throwable t) {
            SagaLog.errorOnce("battle-listener", "checking a battle for encounters failed", t);
        }
    }

    @Override
    public void reportFleetDespawned(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        try {
            String id = EncounterManager.idOf(fleet);
            if (id == null) return;
            EncounterDef e = Saga.content().encounters.get(id);
            if (e == null || !EncounterLogic.isSpawned(GameMemory.global(), id)) return;
            EncounterManager.resolve(e, reason == FleetDespawnReason.DESTROYED_BY_BATTLE);
        } catch (Throwable t) {
            SagaLog.errorOnce("despawn-listener", "checking a despawned fleet for encounters failed", t);
        }
    }
}
