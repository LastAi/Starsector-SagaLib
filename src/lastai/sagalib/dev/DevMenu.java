package lastai.sagalib.dev;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.QuestLogic;
import lastai.sagalib.game.GameMemory;
import lastai.sagalib.game.QuestRuntime;
import lastai.sagalib.game.Saga;
import lastai.sagalib.model.ProfileDef;
import lastai.sagalib.model.QuestDef;
import org.lwjgl.input.Keyboard;

import java.util.Map;

/**
 * SagaLib's dev menu (dev mode only): apply a test profile, jump a quest to a step, reload the
 * JSON. With {@code newGame} it is the profile picker shown after a new sector is generated.
 */
public final class DevMenu implements InteractionDialogPlugin {
    private static final class Opt {
        final String kind;
        final String a;
        final String b;

        Opt(String kind, String a, String b) {
            this.kind = kind;
            this.a = a;
            this.b = b;
        }
    }

    private final boolean newGame;
    private InteractionDialogAPI dialog;

    public DevMenu(boolean newGame) { this.newGame = newGame; }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.getVisualPanel().fadeVisualOut();
        TextPanelAPI text = dialog.getTextPanel();
        if (newGame) {
            text.addPara("SagaLib (dev mode): apply a test profile to this new game?");
            profiles();
        } else {
            text.addPara("SagaLib dev menu. Profiles and jumps change the running save: use a throwaway save.",
                    Misc.getHighlightColor(), "change the running save");
            main();
        }
    }

    private OptionPanelAPI options() { return dialog.getOptionPanel(); }

    private void leaveOption(String label) {
        Opt leave = new Opt("leave", null, null);
        options().addOption(label, leave);
        options().setShortcut(leave, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    private void main() {
        options().clearOptions();
        options().addOption("Apply a test profile", new Opt("profiles", null, null));
        options().addOption("Jump a quest to a step", new Opt("quests", null, null));
        options().addOption("Reload the JSON from disk", new Opt("reload", null, null));
        leaveOption("Leave");
    }

    private void profiles() {
        options().clearOptions();
        for (ProfileDef p : Saga.content().profileList()) options().addOption(p.name + " (" + p.id + ")", new Opt("profile", p.id, null));
        if (newGame) {
            leaveOption("None");
        } else {
            if (Saga.content().profiles.isEmpty()) dialog.getTextPanel().addPara("No test profiles are loaded (data/sagaforge/dev/test_profiles.json).");
            options().addOption("Back", new Opt("main", null, null));
        }
    }

    private void quests() {
        options().clearOptions();
        GameMemory mem = GameMemory.global();
        for (QuestDef q : Saga.content().quests.values()) {
            if (!q.isMission()) continue;
            String st = QuestLogic.state(mem, q.id);
            String step = QuestLogic.step(mem, q.id);
            options().addOption(q.title + " (" + st + (step != null ? ", at " + step : "") + ")", new Opt("quest", q.id, null));
        }
        options().addOption("Back", new Opt("main", null, null));
    }

    private void steps(String questId) {
        QuestDef q = Saga.content().quest(questId);
        options().clearOptions();
        if (q != null) for (QuestDef.Stage s : q.stages) options().addOption(s.id + ": " + s.title, new Opt("step", q.id, s.id));
        options().addOption("Back", new Opt("quests", null, null));
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (!(optionData instanceof Opt)) return;
        Opt o = (Opt) optionData;
        TextPanelAPI text = dialog.getTextPanel();
        try {
            if ("leave".equals(o.kind)) {
                dialog.dismiss();
            } else if ("main".equals(o.kind)) {
                main();
            } else if ("profiles".equals(o.kind)) {
                profiles();
            } else if ("quests".equals(o.kind)) {
                quests();
            } else if ("quest".equals(o.kind)) {
                text.addPara("Steps of " + o.a + ":");
                steps(o.a);
            } else if ("reload".equals(o.kind)) {
                String s = DevTools.reload();
                text.addPara("Reloaded: " + s + ". Systems and fleets already in the game are not rebuilt.");
                if (Saga.content().problems.errors() > 0) text.addPara("There are errors: see starsector.log ([SagaLib]).", Misc.getNegativeHighlightColor());
                main();
            } else if ("profile".equals(o.kind)) {
                ProfileDef p = Saga.content().profiles.get(o.a);
                if (p != null) DevTools.applyProfile(p, newGame, text);
                if (newGame) dialog.dismiss();
                else main();
            } else if ("step".equals(o.kind)) {
                QuestRuntime.step(o.a, o.b, "SagaLib dev menu", dialog, null, true);
                text.addPara("Quest " + o.a + " is now at step " + o.b + ".");
                QuestRuntime.syncJournal();
                quests();
            }
        } catch (Throwable t) {
            SagaLog.error("dev menu action " + o.kind + " failed", t);
            text.addPara("That failed: see starsector.log.", Misc.getNegativeHighlightColor());
        }
    }

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
