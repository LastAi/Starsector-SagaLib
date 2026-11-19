package lastai.sagalib.game;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Mods;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SagaLib 1.3 (Saga Forge PLAN.md §10.2): the start record of each content mod's optional and
 * incompatible mods (core.Mods.record), at a new game and on every load. Every difference is
 * logged on every load; a new one is shown once per save in a small dialog once the campaign is on
 * screen (a transient script, never in the save).
 */
public final class ModNotices {
    private ModNotices() {}

    /** Takes the record (first time a mod is seen in this save); with {@code tell}, logs what differs and shows what is new. */
    public static void run(boolean tell) {
        if (Mods.lists().isEmpty()) return;
        List<Mods.Notice> all = Mods.record(GameMemory.global(), tell);
        List<String> fresh = new ArrayList<String>();
        for (Mods.Notice n : all) {
            if (tell) SagaLog.warn("mods: " + n.text);
            if (n.fresh) fresh.add(n.text);
        }
        if (tell && !fresh.isEmpty()) Global.getSector().addTransientScript(new Show(fresh));
    }

    /** Waits for the campaign screen, then shows the notices in one dialog. */
    static final class Show implements EveryFrameScript {
        private final List<String> texts;
        private boolean done;
        private float waited;

        Show(List<String> texts) { this.texts = texts; }

        @Override
        public boolean isDone() { return done; }

        @Override
        public boolean runWhilePaused() { return true; }

        @Override
        public void advance(float amount) {
            if (done || Global.getCurrentState() != GameState.CAMPAIGN) return;
            waited += amount;
            if (waited < 1f || Global.getSector().getCampaignUI().isShowingDialog() || Global.getSector().getCampaignUI().isShowingMenu()) return;
            done = true;
            try {
                if (!Global.getSector().getCampaignUI().showInteractionDialog(new Dialog(texts), Global.getSector().getPlayerFleet())) {
                    for (String t : texts) Global.getSector().getCampaignUI().addMessage(t, Misc.getNegativeHighlightColor());
                }
            } catch (Throwable t) {
                SagaLog.error("mods: the notice couldn't be shown", t);
            }
        }
    }

    static final class Dialog implements InteractionDialogPlugin {
        private final List<String> texts;
        private InteractionDialogAPI dialog;

        Dialog(List<String> texts) { this.texts = texts; }

        @Override
        public void init(InteractionDialogAPI dialog) {
            this.dialog = dialog;
            dialog.getVisualPanel().fadeVisualOut();
            dialog.getTextPanel().addPara("The mods in this game changed since it started:", Misc.getHighlightColor());
            for (String t : texts) dialog.getTextPanel().addPara(t);
            dialog.getTextPanel().addPara("This is shown once per game. The starsector.log repeats it on every load ([SagaLib]).", Misc.getGrayColor());
            dialog.getOptionPanel().addOption("OK", "ok");
            dialog.getOptionPanel().setShortcut("ok", Keyboard.KEY_ESCAPE, false, false, false, true);
        }

        @Override
        public void optionSelected(String optionText, Object optionData) { dialog.dismiss(); }

        @Override
        public void optionMousedOver(String optionText, Object optionData) {}

        @Override
        public void advance(float amount) {}

        @Override
        public void backFromEngagement(EngagementResultAPI battleResult) {}

        @Override
        public Object getContext() { return null; }

        @Override
        public Map<String, MemoryAPI> getMemoryMap() { return null; }
    }
}
