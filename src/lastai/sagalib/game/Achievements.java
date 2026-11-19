package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Bounties;

/**
 * SagaLib 1.3, task #65: the guarded bridge to MagicLib's achievements (PLAN.md §10: SagaLib runs
 * normally without MagicLib), loaded the way {@link MagicBounties} loads the bounty bridge. Without
 * MagicLib, or with a SagaLib built without its MagicLib bridge, SF_Achievement does nothing and logs
 * why once.
 */
public final class Achievements {
    private Achievements() {}

    static final String BRIDGE = "lastai.sagalib.bridge.magiclib.MagicAchievementsBridge";

    private static volatile AchievementBridge bridge;
    private static volatile boolean tried;
    private static volatile String status = "not loaded yet";

    private static synchronized AchievementBridge bridge() {
        if (!tried) {
            tried = true;
            bridge = load();
        }
        return bridge;
    }

    private static AchievementBridge load() {
        if (!Global.getSettings().getModManager().isModEnabled(Bounties.MAGICLIB)) {
            status = "MagicLib isn't enabled";
            return null;
        }
        try {
            Class<?> c = Global.getSettings().getScriptClassLoader().loadClass(BRIDGE);
            AchievementBridge b = (AchievementBridge) c.newInstance();
            status = "";
            SagaLog.info("MagicLib achievements bridge loaded: quests can unlock MagicLib achievements");
            return b;
        } catch (ClassNotFoundException e) {
            status = "this SagaLib was built without its MagicLib bridge";
            SagaLog.warn("MagicLib is enabled, but this SagaLib was built without its MagicLib bridge: quests can't unlock achievements");
        } catch (Throwable t) {
            status = "the bridge failed to load (see starsector.log)";
            SagaLog.error("MagicLib achievements bridge failed to load (a MagicLib version SagaLib doesn't know?)", t);
        }
        return null;
    }

    /** "" when the bridge is ready, else why not (loads it if needed). */
    public static String status() {
        bridge();
        return status;
    }

    /** SF_Achievement: completes it. null = done (or already complete), else why not. */
    public static String complete(String id, TextPanelAPI text) {
        AchievementBridge b = bridge();
        if (b == null) return status;
        try {
            return b.complete(id, text);
        } catch (Throwable t) {
            SagaLog.error("MagicLib bridge: completing achievement " + id + " failed", t);
            return "MagicLib failed (see starsector.log)";
        }
    }
}
