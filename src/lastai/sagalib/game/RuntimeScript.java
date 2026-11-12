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
                double now = EncounterManager.now();
                for (QuestDef q : Saga.content().quests.values()) {
                    if (q.timers.isEmpty()) continue;
                    for (String t : TimerLogic.tick(q, mem, now)) {
                        SagaLog.info("quest " + q.id + ": timer fired " + t);
                        Triggers.add(mem, t);
                    }
                }
            }
            if (!Global.getSector().getCampaignUI().isShowingDialog() && !Global.getSector().getCampaignUI().isShowingMenu()) {
                String t = Triggers.poll(mem);
                if (t != null && !TriggerDialog.fire(t)) Triggers.pushBack(mem, t);
            }
        } catch (Throwable t) {
            SagaLog.errorOnce("runtime-script", "SagaLib's campaign script failed (logged once)", t);
        }
    }
}
