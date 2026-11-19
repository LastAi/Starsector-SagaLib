package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Content;
import lastai.sagalib.core.Versions;

import java.util.List;

/**
 * SagaLib 1.3 (docs/FORMAT.md "Versions by installed mods"): right after the start record (new
 * game and every load, and after a dev-menu reload) every versioned item not chosen yet in this
 * save is chosen ({@code $global.sagaforge_ver_<kind>_<id>}), then every item's chosen version is
 * put into the content maps, so all other code uses it unchanged. Nothing new is saved but the
 * memory keys.
 */
public final class ItemVersions {
    private ItemVersions() {}

    public static void run() {
        Content c = Saga.content();
        if (c.versioned.isEmpty()) return;
        GameMemory mem = GameMemory.global();
        int fresh = 0;
        for (Versions.Item it : c.versioned.values()) {
            try {
                boolean was = mem.get(Versions.memKey(it.kind, it.id)) != null;
                List<String> on = Versions.choose(mem, it);
                String key = Versions.key(on);
                c.activate(it, key);
                if (!was) {
                    fresh++;
                    SagaLog.info("versions: " + it.kind + " " + it.id + " of " + it.owner + " uses version " + key + " in this game (mentions " + it.mentioned + ")");
                }
            } catch (Throwable t) {
                SagaLog.error("versions: " + it.kind + " " + it.id + " of " + it.owner + " failed; it keeps the version in use", t);
            }
        }
        SagaLog.info("versions: " + c.versioned.size() + " items with versions, " + fresh + " chosen now");
        // progress events read their definition while the save loads (before this): give them the chosen one
        try {
            if (Global.getSector() != null && Global.getSector().getIntelManager() != null) {
                for (Object o : Global.getSector().getIntelManager().getIntel(SagaProgressEvent.class)) ((SagaProgressEvent) o).refreshDef();
            }
        } catch (Throwable t) {
            SagaLog.error("versions: progress events couldn't take their version", t);
        }
    }
}
