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
        // SagaLib 1.3 (task #61): the sector as quests need it (which places and people exist, market owners)
        lastai.sagalib.core.Places.setup(new lastai.sagalib.game.SectorPlaces());
        // SagaLib 1.2: an enabled translation renames the mod's items and changes its descriptions
        step("translation", new Runnable() { public void run() { lastai.sagalib.game.TranslatedTexts.applySpecs(); } });
        // SagaLib 1.2: random encounters from encounter pools, as one of vanilla's encounter creators
        step("encounter pools", new Runnable() { public void run() { lastai.sagalib.encounters.PoolEncounterCreator.register(); } });
    }

    @Override
    public void onNewGame() {
        // SagaLib 1.3: record which optional and incompatible mods this game starts with, before anything uses them
        step("mods", new Runnable() { public void run() { lastai.sagalib.core.Mods.resetSkipped(); lastai.sagalib.game.ModNotices.run(false); } });
        // SagaLib 1.4: record the mods' settings as this game starts with them (versions by a setting use these, for good)
        step("settings record", new Runnable() { public void run() { lastai.sagalib.game.ModSettings.record(); } });
        // SagaLib 1.3: each item's version by installed mods, chosen from the record before systems are built
        step("versions", new Runnable() { public void run() { lastai.sagalib.game.ItemVersions.run(); } });
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
        // SagaLib 1.3: the start record (a content mod new to this save is recorded now), the log
        // lines and the one-time notice when the mods differ from it
        step("mods", new Runnable() {
            public void run() {
                if (!newGame) lastai.sagalib.core.Mods.resetSkipped();
                lastai.sagalib.game.ModNotices.run(true);
            }
        });
        // SagaLib 1.3 (task #62): the mods' settings as $global.<id> (LunaLib's values, else the defaults), before anything reads quests
        // SagaLib 1.4: a save from before 1.4 (or a field an update added) records the values it has now; recorded ones never change
        step("settings", new Runnable() { public void run() { lastai.sagalib.game.ModSettings.record(); lastai.sagalib.game.ModSettings.apply(); } });
        // SagaLib 1.3: versions by installed mods: choose new items, put this save's versions in place
        step("versions", new Runnable() { public void run() { lastai.sagalib.game.ItemVersions.run(); } });
        step("phrases", new Runnable() { public void run() { PhraseTokens.register(); lastai.sagalib.game.TextTokens.register(); } });
        // SagaLib 1.4 (task #79): one-name people's first-name tokens; only here, after vanilla's generator (OneNameTokens)
        step("one-name tokens", new Runnable() { public void run() { lastai.sagalib.game.OneNameTokens.register(); } });
        step("systems", new Runnable() { public void run() { SystemBuilder.onGameLoad(); SystemBuilder.applyWorldChanges(); } });
        // SagaLib 1.3 (task #61): Nexerelin never invades the markets marked so
        step("invasion protection", new Runnable() { public void run() { SystemBuilder.protectMarkets(); } });
        // SagaLib 1.2: names the save keeps (systems, places, markets, factions) follow the enabled translation
        step("translation", new Runnable() { public void run() { lastai.sagalib.game.TranslatedTexts.applySave(); } });
        step("scatter", new Runnable() { public void run() { if (!newGame) lastai.sagalib.quest.ScatterBuilder.run(false); } });
        step("people", new Runnable() { public void run() { People.applyAll(); } });
        step("faction changes", new Runnable() { public void run() { Factions.applyScripted(); } });
        // SagaLib 1.3 (task #70): who is away (their market's owner and its relations), applied to the comm directories
        step("presence", new Runnable() { public void run() { lastai.sagalib.game.PresenceRuntime.onLoad(); } });
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
