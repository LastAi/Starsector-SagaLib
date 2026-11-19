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
        // SagaLib 1.3: the reloaded content starts at the base versions: put this save's versions back
        try {
            lastai.sagalib.game.ItemVersions.run();
        } catch (Throwable t) {
            SagaLog.error("dev: versions after reload failed", t);
        }
        // SagaLib 1.3 (task #62): the reloaded settings (new fields, new defaults)
        try {
            lastai.sagalib.game.ModSettings.apply();
        } catch (Throwable t) {
            SagaLog.error("dev: settings after reload failed", t);
        }
        People.applyAll();
        SaveReconcile.run();
        String s = Saga.content().summary();
        SagaLog.info("dev: reloaded " + s);
        return s;
    }
}
