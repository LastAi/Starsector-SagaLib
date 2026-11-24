package lastai.sagalib.game;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Memory;
import lastai.sagalib.core.TimerLogic;
import lastai.sagalib.core.Triggers;
import lastai.sagalib.encounters.EncounterManager;
import lastai.sagalib.model.QuestDef;

/**
 * SagaLib's one campaign script (transient, re-added on every load): counts days, runs the
 * encounter manager and quest timers a few times a day, and fires queued triggers when no
 * dialog is open. All its state is in memory keys.
 */
public final class RuntimeScript implements EveryFrameScript {
    private static final float TICK_DAYS = 0.25f;
    private float sinceTick = TICK_DAYS; // tick soon after a load
    /** SagaLib 1.3 (task #61): the day of the last owner check (once a day). */
    private double lastOwnerCheck = -1;

    public static void register() {
        if (!Global.getSector().hasTransientScript(RuntimeScript.class)) Global.getSector().addTransientScript(new RuntimeScript());
    }

    @Override
    public boolean isDone() { return false; }

    @Override
    public boolean runWhilePaused() { return true; }

    /**
     * Real seconds the campaign screen has been up. The game runs campaign scripts during the last
     * loading stages too, before the campaign UI exists; opening a dialog then is fatal (found in
     * game, 2026-11-11). Nothing runs until the campaign is showing and has settled.
     */
    private float settled = 0f;
    private static final float SETTLE_SECONDS = 0.5f;

    @Override
    public void advance(float amount) {
        if (Global.getCurrentState() != GameState.CAMPAIGN) return;
        if (settled < SETTLE_SECONDS) {
            settled += amount;
            return;
        }
        try {
            Memory mem = GameMemory.global();
            if (!Global.getSector().isPaused()) {
                float days = Global.getSector().getClock().convertToDays(amount);
                mem.set(EncounterManager.DAYS_KEY, Float.valueOf((float) (EncounterManager.now() + days)));
                sinceTick += days;
            }
            if (sinceTick >= TICK_DAYS) {
                sinceTick = 0;
                EncounterManager.tick();
                // SagaLib 1.1: objects that appear or disappear with quest stages (also set by profiles or jumps)
                try { lastai.sagalib.systems.SystemBuilder.applyWorldChanges(); } catch (Throwable t) { SagaLog.errorOnce("world-tick", "world changes failed", t); }
                double now = EncounterManager.now();
                for (QuestDef q : Saga.content().quests.values()) {
                    for (String t : TimerLogic.tick(q, mem, now)) {
                        SagaLog.info("quest " + q.id + ": timer fired " + t);
                        Triggers.add(mem, t);
                    }
                    // SagaLib 1.3: Wait nodes on a MagicLib bounty's end (MagicLib's memory keys; no MagicLib class)
                    if (q.bountyWaits.isEmpty()) continue;
                    try {
                        for (String t : lastai.sagalib.core.BountyWaits.tick(q, mem, lastai.sagalib.core.Mods.ownerOfRule(q.id))) {
                            SagaLog.info("quest " + q.id + ": MagicLib bounty ended, fired " + t);
                            Triggers.add(mem, t);
                        }
                    } catch (Throwable t) { SagaLog.errorOnce("bountywait:" + q.id, "quest " + q.id + ": the bounty wait failed", t); }
                }
                // SagaLib 1.3 (task #61): markets of active quests changing owner, once a day
                if (lastOwnerCheck < 0 || now - lastOwnerCheck >= 1.0) {
                    lastOwnerCheck = now;
                    try { OwnerChanges.tick(mem); } catch (Throwable t) { SagaLog.errorOnce("owners", "the market owner check failed", t); }
                    // SagaLib 1.3 (task #70): people present only while their market's owner gets on with a faction
                    try { PresenceRuntime.tick(mem); } catch (Throwable t) { SagaLog.errorOnce("presence", "the presence check failed", t); }
                }
                // SagaLib 1.2: roaming fleets
                try { lastai.sagalib.encounters.RoamingFleets.tick(mem, now); } catch (Throwable t) { SagaLog.errorOnce("roaming", "roaming fleets failed", t); }
                // SagaLib 1.2: progress events start when their conditions hold
                try { ProgressEvents.tick(); } catch (Throwable t) { SagaLog.errorOnce("progress", "progress events failed", t); }
                // SagaLib 1.2: world event schedulers
                for (lastai.sagalib.model.EventsDef d : Saga.content().events.values()) {
                    if (!lastai.sagalib.core.Mods.allowed(mem, d.gate, "event scheduler", d.id)) continue; // SagaLib 1.3
                    try { WorldEvents.tick(d, mem, now); } catch (Throwable t) { SagaLog.errorOnce("events:" + d.id, "event scheduler " + d.id + " failed", t); }
                }
            }
            if (!Global.getSector().getCampaignUI().isShowingDialog() && !Global.getSector().getCampaignUI().isShowingMenu()) {
                String t = Triggers.poll(mem);
                // SagaLib 1.4: a world event's or progress stage's actions written as ops run without a dialog
                java.util.List<lastai.sagalib.core.QuietOps.Op> ops = t == null ? null : Saga.content().quietOps(t);
                if (ops != null) {
                    if (!QuietRunner.run(t, ops)) Triggers.pushBack(mem, t);
                } else if (t != null && !TriggerDialog.fire(t)) Triggers.pushBack(mem, t);
            }
        } catch (Throwable t) {
            SagaLog.errorOnce("runtime-script", "SagaLib's campaign script failed (logged once)", t);
        }
    }
}
