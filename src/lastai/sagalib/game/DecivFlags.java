package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyDecivListener;
import lastai.sagalib.SagaLog;

/**
 * SagaLib 1.2 (Phase 7e, RC-R11): remembers every colony that decivilises, as
 * $global.sagaforge_deciv_&lt;market id&gt; = true, so quests and phrases can react ("the colony you
 * saved collapsed"). A transient listener, re-added on every load (vanilla ColonyDecivListener).
 */
public final class DecivFlags implements ColonyDecivListener {
    public static final String PREFIX = "$global.sagaforge_deciv_";

    public static void register() {
        if (Global.getSector().getListenerManager().hasListenerOfClass(DecivFlags.class)) return;
        Global.getSector().getListenerManager().addListener(new DecivFlags(), true);
    }

    @Override
    public void reportColonyAboutToBeDecivilized(MarketAPI market, boolean fullyDestroyed) {}

    @Override
    public void reportColonyDecivilized(MarketAPI market, boolean fullyDestroyed) {
        if (market == null) return;
        GameMemory.global().set(PREFIX + market.getId(), Boolean.TRUE);
        SagaLog.info("colony " + market.getId() + " decivilised" + (fullyDestroyed ? " (destroyed)" : ""));
    }
}
