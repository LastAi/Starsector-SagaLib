package lastai.sagalib.dev;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Profiles;
import lastai.sagalib.game.GameMemory;
import lastai.sagalib.game.People;
import lastai.sagalib.game.QuestRuntime;
import lastai.sagalib.game.Saga;
import lastai.sagalib.game.SaveReconcile;
import lastai.sagalib.model.ProfileDef;

/** Dev-mode actions shared by the new-game picker and the Ctrl+F9 menu. Never used outside dev mode. */
public final class DevTools {
    private DevTools() {}

    public static boolean enabled() { return Global.getSettings().isDevMode(); }

    /** Writes the profile into the running game, then makes the journal match it. */
    public static void applyProfile(ProfileDef p, boolean newGame, TextPanelAPI text) {
        Profiles.apply(GameMemory.global(), p, Saga.content().quests, new GamePlayer(newGame));
        QuestRuntime.syncJournal();
        SagaLog.info("dev: applied test profile " + p.id + " (" + p.name + ")");
        if (text != null) text.addPara("Applied test profile \"" + p.name + "\".");
    }

    /** Re-reads quests, people, phrases and profiles from disk; built systems and spawned fleets stay as they are. */
    public static String reload() {
        Saga.reload();
        People.applyAll();
        SaveReconcile.run();
        String s = Saga.content().summary();
        SagaLog.info("dev: reloaded " + s);
        return s;
    }
}
