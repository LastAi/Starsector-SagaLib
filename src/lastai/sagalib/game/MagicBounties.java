package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Bounties;

/**
 * SagaLib 1.3, task #59: the guarded MagicLib bridge (PLAN.md §10: SagaLib runs normally without
 * MagicLib). The bridge class is loaded by name only when MagicLib is enabled, so no SagaLib class
 * that the game always loads refers to MagicLib. Without MagicLib, or with a SagaLib built without
 * the bridge (build.sh skips it when MagicLib's jar isn't installed), every bounty is
 * "unavailable" and SF_StartBounty only logs why.
 */
public final class MagicBounties {
    private MagicBounties() {}

    static final String BRIDGE = "lastai.sagalib.bridge.magiclib.MagicLibBridge";

    private static volatile BountyBridge bridge;
    private static volatile boolean tried;
    /** Why there is no bridge ("" while there is one), for the dev menu. */
    private static volatile String status = "not loaded yet";

    /** Loads the bridge (once per game start) and tells core.Bounties what MagicLib knows. */
    public static synchronized void setup() {
        if (!tried) {
            tried = true;
            bridge = load();
        }
        final BountyBridge b = bridge;
        Bounties.setup(b == null ? null : new Bounties.Source() {
            public boolean ready() { return true; }

            public boolean known(String id) {
                try {
                    return b.known(id);
                } catch (Throwable t) {
                    SagaLog.errorOnce("bounty-known", "MagicLib bridge: asking MagicLib about bounty " + id + " failed", t);
                    return false;
                }
            }

            public String memKey(String id) {
                try {
                    return b.memKey(id);
                } catch (Throwable t) {
                    SagaLog.errorOnce("bounty-key", "MagicLib bridge: reading the memory key of bounty " + id + " failed", t);
                    return null;
                }
            }
        });
    }

    private static BountyBridge load() {
        if (!Global.getSettings().getModManager().isModEnabled(Bounties.MAGICLIB)) {
            status = "MagicLib isn't enabled";
            SagaLog.info("MagicLib isn't enabled: quests see its bounties as unavailable");
            return null;
        }
        try {
            Class<?> c = Global.getSettings().getScriptClassLoader().loadClass(BRIDGE);
            BountyBridge b = (BountyBridge) c.newInstance();
            status = "";
            SagaLog.info("MagicLib bridge loaded: quests can start and check MagicLib bounties");
            return b;
        } catch (ClassNotFoundException e) {
            status = "this SagaLib was built without its MagicLib bridge";
            SagaLog.warn("MagicLib is enabled, but this SagaLib was built without its MagicLib bridge: quests see its bounties as unavailable");
        } catch (Throwable t) {
            status = "the bridge failed to load (see starsector.log)";
            SagaLog.error("MagicLib bridge failed to load (a MagicLib version SagaLib doesn't know?): quests see its bounties as unavailable", t);
        }
        return null;
    }

    /** "" when the bridge is ready, else why not. */
    public static String status() { return status; }

    /** SF_StartBounty: accept the bounty for the player. null = done, else why not (already logged by the caller). */
    public static String accept(String owner, String id, InteractionDialogAPI dialog) {
        String why = Bounties.cantStart(GameMemory.global(), owner, id);
        if (why != null) return why;
        BountyBridge b = bridge;
        if (b == null) return "no MagicLib bridge";
        try {
            return b.accept(id, dialog);
        } catch (Throwable t) {
            SagaLog.error("MagicLib bridge: accepting bounty " + id + " failed", t);
            return "MagicLib failed (see starsector.log)";
        }
    }
}
