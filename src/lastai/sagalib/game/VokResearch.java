package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Research;

/**
 * SagaLib 1.4, task #77: the guarded bridge to AoTD Vaults of Knowledge (PLAN.md §10: SagaLib runs
 * normally without it), loaded the way {@link Achievements} loads MagicLib's. The bridge class is
 * loaded by name only when VoK is enabled, so no SagaLib class the game always loads refers to VoK.
 * Without VoK, or with a SagaLib built without its VoK bridge (build.sh skips it when Vok.jar isn't
 * installed), SF_GrantResearch does nothing (logged once) and SF_HasResearch is false.
 */
public final class VokResearch {
    private VokResearch() {}

    static final String BRIDGE = "lastai.sagalib.bridge.vok.VokBridge";

    private static volatile boolean tried;

    /** Loads the bridge once and tells core.Research what VoK knows. */
    public static synchronized void ensure() {
        if (tried) return;
        tried = true;
        if (!Global.getSettings().getModManager().isModEnabled(Research.VOK)) {
            Research.setup(null, "Vaults of Knowledge isn't enabled");
            SagaLog.info("Vaults of Knowledge isn't enabled: quests grant no research and see none");
            return;
        }
        try {
            Class<?> c = Global.getSettings().getScriptClassLoader().loadClass(BRIDGE);
            final ResearchBridge b = (ResearchBridge) c.newInstance();
            Research.setup(new Research.Source() {
                public boolean known(String id) { return b.known(id); }
                public boolean researched(String id, String faction) { return b.researched(id, faction); }
                public String grant(String id) { return b.grant(id); }
            }, null);
            SagaLog.info("Vaults of Knowledge bridge loaded: quests can grant and check research");
        } catch (ClassNotFoundException e) {
            Research.setup(null, "this SagaLib was built without its Vaults of Knowledge bridge");
            SagaLog.warn("Vaults of Knowledge is enabled, but this SagaLib was built without its VoK bridge: quests grant no research and see none");
        } catch (Throwable t) {
            Research.setup(null, "the Vaults of Knowledge bridge failed to load (see starsector.log)");
            SagaLog.error("Vaults of Knowledge bridge failed to load (a VoK version SagaLib doesn't know?)", t);
        }
    }

    /** "" when the bridge is ready, else why not (loads it if needed). */
    public static String status() {
        ensure();
        return Research.status();
    }
}
