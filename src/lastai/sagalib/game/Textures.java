package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import lastai.sagalib.SagaLog;

import java.util.HashSet;
import java.util.Set;

/**
 * The game only draws textures it has loaded. Faction portraits, icons in the item CSVs and
 * illustrations in settings.json are loaded by the game; a person's own portrait set with
 * setPortraitSprite is not, and shows blank (found in game, 2026-11-11). Load it first.
 * <p>
 * SagaLib 1.3: a path that can't be loaded is remembered too, so callers that run every frame (a
 * quest's intel icon) log it once and don't try again.
 */
public final class Textures {
    private static final Set<String> loaded = new HashSet<String>();
    private static final Set<String> failed = new HashSet<String>();

    private Textures() {}

    /** Load a texture once (no-op for paths already loaded). Returns false if it can't be read. */
    public static synchronized boolean ensure(String path) {
        if (path == null || path.isEmpty() || loaded.contains(path)) return true;
        if (failed.contains(path)) return false;
        try {
            Global.getSettings().loadTexture(path);
            loaded.add(path);
            return true;
        } catch (Throwable t) {
            failed.add(path);
            SagaLog.error("can't load image \"" + path + "\": " + t.getMessage());
            return false;
        }
    }
}
