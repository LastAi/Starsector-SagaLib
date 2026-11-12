package lastai.sagalib;

import com.fs.starfarer.api.BaseModPlugin;
import lastai.sagalib.dev.DevInput;
import lastai.sagalib.dev.ProfilePicker;
import lastai.sagalib.encounters.EncounterListener;
import lastai.sagalib.game.RuntimeScript;
import lastai.sagalib.game.Factions;
import lastai.sagalib.game.People;
import lastai.sagalib.game.PhraseTokens;
import lastai.sagalib.game.Saga;
import lastai.sagalib.game.SaveReconcile;
import lastai.sagalib.systems.SystemBuilder;

/**
 * Loads the content mods' JSON at startup and wires it into each campaign. Everything added to
 * the sector here is transient (token generator, dev hotkey, picker) or vanilla (people, intel),
 * and re-done on every load.
 */
public final class SagaLibModPlugin extends BaseModPlugin {
    @Override
    public void onApplicationLoad() {
        Saga.checkGameVersion();
        Saga.reload();
    }

    @Override
    public void onNewGame() {
        // like a SectorGeneratorPlugin: new systems (with their markets) and additions to existing ones
        step("systems", new Runnable() { public void run() { SystemBuilder.onNewGame(); } });
    }

    @Override
    public void onNewGameAfterEconomyLoad() {
        // vanilla markets exist now: place people (also at the new markets), set the starting relations
        step("people", new Runnable() { public void run() { People.applyAll(); } });
        step("relations", new Runnable() { public void run() { Factions.applyRelations(); } });
        step("phrases", new Runnable() { public void run() { PhraseTokens.register(); } });
    }

    @Override
    public void onGameLoad(final boolean newGame) {
        step("phrases", new Runnable() { public void run() { PhraseTokens.register(); } });
        step("systems", new Runnable() { public void run() { SystemBuilder.onGameLoad(); } });
        step("people", new Runnable() { public void run() { People.applyAll(); } });
        step("faction changes", new Runnable() { public void run() { Factions.applyScripted(); } });
        step("reconciliation", new Runnable() { public void run() { SaveReconcile.run(); } });
        // encounters (spawns, outcomes) and quest timers: transient, so re-added on every load
        step("encounters", new Runnable() {
            public void run() {
                EncounterListener.register();
                RuntimeScript.register();
            }
        });
        step("dev tools", new Runnable() {
            public void run() {
                DevInput.register();
                if (newGame) ProfilePicker.scheduleIfWanted();
            }
        });
    }

    /** A failing part is logged and skipped; it never stops the game from loading. */
    private static void step(String what, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            SagaLog.error(what + " failed", t);
        }
    }
}
