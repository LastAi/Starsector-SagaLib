package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Content;
import lastai.sagalib.core.Problems;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

/** The loaded content definitions (static, never saved) and the library's constants. */
public final class Saga {
    private Saga() {}

    public static final String MOD_ID = "lastai_sagalib";
    public static final String VERSION = "1.0.0";
    /** Game versions this SagaLib was checked against; another one logs a warning. */
    public static final List<String> KNOWN_GAME_VERSIONS = Arrays.asList("0.98a-RC8");

    private static volatile Content content = Content.empty();

    public static Content content() { return content; }

    /** (Re)reads every content mod's JSON through the game's loaders and logs what was found. */
    public static Content reload() {
        Content c = Content.load(new Content.Source() {
            @Override
            public JSONArray index() throws Exception {
                return Global.getSettings().getMergedSpreadsheetDataForMod("id", Content.INDEX, MOD_ID);
            }

            @Override
            public JSONObject load(String path, String mod) throws Exception {
                if (!Global.getSettings().getModManager().isModEnabled(mod)) {
                    throw new IllegalStateException("mod " + mod + " is not enabled");
                }
                return Global.getSettings().loadJSON(path, mod);
            }

            @Override
            public boolean devMode() { return Global.getSettings().isDevMode(); }
        });
        for (Problems.Problem p : c.problems.all()) {
            if (p.error) SagaLog.error(p.toString());
            else SagaLog.warn(p.toString());
        }
        SagaLog.info("content: " + c.summary());
        content = c;
        return c;
    }

    public static void checkGameVersion() {
        // the game reports "Starsector 0.98a-RC8" (found in game, 2026-11-11)
        String v = Global.getSettings().getVersionString().replaceFirst("^Starsector\\s+", "").trim();
        if (!KNOWN_GAME_VERSIONS.contains(v)) {
            SagaLog.warn("game version " + v + " is not one SagaLib " + VERSION + " was checked against " + KNOWN_GAME_VERSIONS
                    + "; quests may misbehave. Look for a SagaLib update.");
        }
    }
}
