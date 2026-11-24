package lastai.sagalib.game;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.BaseFIDDelegate;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfig;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.util.Misc;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Fights;
import lastai.sagalib.encounters.EncounterManager;
import lastai.sagalib.model.EncounterDef;
import lastai.sagalib.model.QuestDef;
import lastai.sagalib.rulecmd.SF_BackToMenu;

import java.util.ArrayList;
import java.util.Map;

/**
 * SagaLib 1.4: what SF_Fight &lt;quest&gt; &lt;node&gt; does in game (docs/FORMAT.md, "Fights"): a battle in
 * the open dialog against the fight's fleet, the way vanilla's SalvageDefenderInteraction fights a
 * place's automated defenses, then the fight's {@code won} or {@code left} trigger in the same dialog.
 */
public final class FightRuntime {
    private FightRuntime() {}

    public static boolean fight(String quest, String node, final String ruleId, final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap) {
        final String where = "SF_Fight " + quest + " " + node + " in rule " + ruleId;
        if (dialog == null) {
            SagaLog.error(where + ": needs an open dialog");
            return false;
        }
        String problem = Fights.problem(Saga.content().quests, quest, node);
        if (problem != null) {
            SagaLog.error(where + ": " + problem);
            return false;
        }
        final QuestDef.Fight fight = Fights.find(Saga.content().quests, quest, node);
        final SectorEntityToken entity = dialog.getInteractionTarget();
        if (entity == null) {
            SagaLog.error(where + ": the dialog has no interaction target");
            return false;
        }
        final MemoryAPI memory = entity.getMemoryWithoutUpdate();
        final String key = Fights.fleetKey(quest, node);

        // a damaged fleet the player left here (kept for a while, as vanilla keeps $defenderFleet), else a fresh one
        CampaignFleetAPI kept = memory.contains(key) && memory.get(key) instanceof CampaignFleetAPI ? (CampaignFleetAPI) memory.get(key) : null;
        if (kept != null && (!kept.isAlive() || kept.getFleetData().getNumMembers() == 0)) kept = null;
        final CampaignFleetAPI fleet;
        if (kept != null) {
            fleet = kept;
            SagaLog.info(where + ": the fleet left here before (damaged) fights again");
        } else {
            fleet = EncounterManager.build(fight.fleet, entity.getLocationInHyperspace());
            if (fleet == null) {
                SagaLog.error(where + ": its fleet couldn't be made (see above); no fight");
                return false;
            }
            if (fleet.getInflater() != null) fleet.getInflater().setRemoveAfterInflating(false);
        }
        // at the place for the battle (a kept fleet waits far away; vanilla moves its defenders back the same way)
        fleet.setLocation(entity.getLocation().x, entity.getLocation().y);
        MemoryAPI fm = fleet.getMemoryWithoutUpdate();
        fm.set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
        fm.set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);

        dialog.setInteractionTarget(fleet);

        final FIDConfig config = new FIDConfig();
        config.leaveAlwaysAvailable = true;
        config.showCommLinkOption = false;
        config.showEngageText = false;
        config.showFleetAttitude = false;
        config.showTransponderStatus = false;
        config.showWarningDialogWhenNotHostile = false;
        config.alwaysAttackVsAttack = true;
        config.impactsAllyReputation = true;
        config.impactsEnemyReputation = false;
        config.pullInAllies = false;
        config.pullInEnemies = false;
        config.pullInStations = false;
        config.lootCredits = false;
        config.firstTimeEngageOptionText = Fights.engageText(fight);
        config.afterFirstTimeEngageOptionText = Fights.RE_ENGAGE;
        config.noSalvageLeaveOptionText = "Continue";
        config.dismissOnLeave = false;
        config.printXPToDialog = true;

        final FleetInteractionDialogPluginImpl plugin = new FleetInteractionDialogPluginImpl(config);
        final InteractionDialogPlugin original = dialog.getPlugin();
        config.delegate = new BaseFIDDelegate() {
            @Override
            public void notifyLeave(InteractionDialogAPI dialog) {
                try {
                    left(where, fight, fleet, plugin, original, entity, memory, key, dialog, memoryMap);
                } catch (Throwable t) {
                    SagaLog.error(where + ": after the fight", t);
                    dialog.setPlugin(original);
                    dialog.setInteractionTarget(entity);
                    dialog.dismiss();
                }
            }

            @Override
            public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
                bcc.aiRetreatAllowed = false;
                bcc.objectivesAllowed = false;
                bcc.enemyDeployAll = true;
            }
            // no postPlayerSalvageGeneration: vanilla's normal battle salvage, without the Omega / derelict AI-core extras
        };

        dialog.setPlugin(plugin);
        plugin.init(dialog);
        SagaLog.info(where + ": fight against " + fleet.getName() + " (" + fleet.getFleetData().getNumMembers() + " ships)");
        return true;
    }

    static void left(String where, QuestDef.Fight fight, CampaignFleetAPI fleet, FleetInteractionDialogPluginImpl plugin, InteractionDialogPlugin original,
                     SectorEntityToken entity, MemoryAPI memory, String key, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        // the "standing down" assignment after a battle doesn't matter here
        fleet.clearAssignments();
        fleet.deflate();
        dialog.setPlugin(original);
        dialog.setInteractionTarget(entity);

        boolean won = false, engaged = false, lost = false, repairs = false;
        if (plugin.getContext() instanceof FleetEncounterContext) {
            FleetEncounterContext c = (FleetEncounterContext) plugin.getContext();
            won = c.didPlayerWinEncounterOutright();
            engaged = c.isEngagedInHostilities();
            if (engaged) {
                lost = !Misc.getSnapshotMembersLost(fleet).isEmpty();
                for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
                    if (m.getStatus().needsRepairs()) { repairs = true; break; }
                }
            }
        }
        Fights.After after = Fights.after(won, engaged, lost, repairs);
        SagaLog.info(where + ": " + after.name().toLowerCase() + (after == Fights.After.WON ? "; firing " + fight.won : "; firing " + fight.left));

        if (after == Fights.After.WON) {
            memory.unset(key);
            removeScripts(entity, fleet);
            fleet.getMemoryWithoutUpdate().clear();
            defeated(fight.fleet);
            dialog.setPromptText("You decide to...");
            if (!FireBest.fire(null, dialog, memoryMap, fight.won)) {
                SagaLog.info(where + ": no rule matches " + fight.won + "; back to the menu");
                SF_BackToMenu.back(null, dialog, memoryMap);
            }
            return;
        }
        if (after == Fights.After.KEEP) {
            boolean running = false;
            for (EveryFrameScript s : new ArrayList<EveryFrameScript>(entity.getScripts())) {
                if (s instanceof FightFleetScript && ((FightFleetScript) s).isFor(fleet)) running = true;
            }
            if (!running) {
                fleet.setDoNotAdvanceAI(true);
                fleet.setContainingLocation(entity.getContainingLocation());
                // somewhere far off where it's not going to be in terrain or whatever (vanilla's words)
                fleet.setLocation(1000000, 1000000);
                entity.addScript(new FightFleetScript(fleet, entity, key));
            }
            memory.set(key, fleet);
            memory.expire(key, Fights.KEEP_DAYS);
        }
        // LEFT: nothing changes; a kept fleet stays until its key expires, a fresh one is forgotten
        if (!FireBest.fire(null, dialog, memoryMap, fight.left)) dialog.dismiss();
    }

    static void removeScripts(SectorEntityToken entity, CampaignFleetAPI fleet) {
        for (EveryFrameScript s : new ArrayList<EveryFrameScript>(entity.getScripts())) {
            if (s instanceof FightFleetScript && ((FightFleetScript) s).isFor(fleet)) entity.removeScript(s);
        }
    }

    /** A named fleet's defeats count, and its captain may die with it (as for an encounter's fleet). */
    static void defeated(EncounterDef e) {
        if (e.template != null) lastai.sagalib.core.EncounterLogic.countDefeat(GameMemory.global(), e.template);
        if (e.captainDiesOnDefeat && e.flagshipCommander != null && !e.flagshipCommander.isEmpty()) People.retire(e.flagshipCommander, "the fleet of fight " + e.id + " was defeated");
    }
}
