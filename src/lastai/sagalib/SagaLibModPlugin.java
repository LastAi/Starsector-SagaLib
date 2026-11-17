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
        // SagaLib 1.2: an enabled translation renames the mod's items and changes its descriptions
        step("translation", new Runnable() { public void run() { lastai.sagalib.game.TranslatedTexts.applySpecs(); } });
        // SagaLib 1.2: random encounters from encounter pools, as one of vanilla's encounter creators
        step("encounter pools", new Runnable() { public void run() { lastai.sagalib.encounters.PoolEncounterCreator.register(); } });
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
        step("phrases", new Runnable() { public void run() { PhraseTokens.register(); lastai.sagalib.game.TextTokens.register(); } });
        // SagaLib 1.2: scatter, once the economy exists (searches can ask for colonies)
        step("scatter", new Runnable() { public void run() { lastai.sagalib.quest.ScatterBuilder.run(true); } });
    }

    @Override
    public void onNewGameAfterProcGen() {
        // vanilla's random systems are made after onNewGame (where ours are built) and should avoid ours: log the closest
        step("spacing", new Runnable() { public void run() { SystemBuilder.reportSpacing(); } });
    }

    @Override
    public void onNewGameAfterTimePass() {
        // after vanilla marked ruins in colonised systems explored (CoreLifecyclePluginImpl.updateKnownPlanets)
        step("ruins", new Runnable() { public void run() { SystemBuilder.restoreRuins(); } });
    }

    @Override
    public void onGameLoad(final boolean newGame) {
        step("phrases", new Runnable() { public void run() { PhraseTokens.register(); lastai.sagalib.game.TextTokens.register(); } });
        step("systems", new Runnable() { public void run() { SystemBuilder.onGameLoad(); SystemBuilder.applyWorldChanges(); } });
        // SagaLib 1.2: names the save keeps (systems, places, markets, factions) follow the enabled translation
        step("translation", new Runnable() { public void run() { lastai.sagalib.game.TranslatedTexts.applySave(); } });
        step("scatter", new Runnable() { public void run() { if (!newGame) lastai.sagalib.quest.ScatterBuilder.run(false); } });
        step("people", new Runnable() { public void run() { People.applyAll(); } });
        step("faction changes", new Runnable() { public void run() { Factions.applyScripted(); } });
        step("reconciliation", new Runnable() { public void run() { SaveReconcile.run(); } });
        // encounters (spawns, outcomes) and quest timers: transient, so re-added on every load
        step("encounters", new Runnable() {
            public void run() {
                EncounterListener.register();
                lastai.sagalib.game.DecivFlags.register();
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
