package lastai.sagalib.dev;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import lastai.sagalib.SagaLog;
import lastai.sagalib.game.Saga;

/**
 * New game in dev mode: once the campaign is on screen, asks which test profile to apply (or
 * none). A transient script, so it never ends up in a save.
 */
public final class ProfilePicker implements EveryFrameScript {
    private boolean done;
    private float waited;

    public static void scheduleIfWanted() {
        if (!DevTools.enabled() || Saga.content().profiles.isEmpty()) return;
        Global.getSector().addTransientScript(new ProfilePicker());
    }

    @Override
    public boolean isDone() { return done; }

    @Override
    public boolean runWhilePaused() { return true; }

    @Override
    public void advance(float amount) {
        if (done) return;
        // not while the game is still loading: no campaign UI yet (see RuntimeScript)
        if (Global.getCurrentState() != GameState.CAMPAIGN) return;
        waited += amount;
        if (waited < 0.5f || Global.getSector().getCampaignUI().isShowingDialog() || Global.getSector().getCampaignUI().isShowingMenu()) return;
        done = true;
        try {
            if (!Global.getSector().getCampaignUI().showInteractionDialog(new DevMenu(true), Global.getSector().getPlayerFleet())) {
                SagaLog.warn("dev: the test profile picker couldn't open; use Ctrl+F9 to apply a profile");
            }
        } catch (Throwable t) {
            SagaLog.error("dev: the test profile picker failed", t);
        }
    }
}
