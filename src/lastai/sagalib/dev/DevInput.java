package lastai.sagalib.dev;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;

import java.util.List;

/**
 * Ctrl+F9 on the campaign map opens the dev menu. Registered as a transient listener on every
 * load, and only in dev mode (docs/FORMATS.md, "Campaign hotkeys and dev tools").
 */
public final class DevInput implements CampaignInputListener {
    public static void register() {
        if (!DevTools.enabled()) return;
        if (Global.getSector().getListenerManager().hasListenerOfClass(DevInput.class)) return;
        Global.getSector().getListenerManager().addListener(new DevInput(), true);
    }

    @Override
    public int getListenerInputPriority() { return 0; }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        if (!DevTools.enabled()) return;
        for (InputEventAPI e : events) {
            if (e.isConsumed() || !e.isKeyDownEvent() || e.getEventValue() != Keyboard.KEY_F9 || !e.isCtrlDown()) continue;
            if (Global.getSector().getCampaignUI().isShowingDialog()) continue;
            if (Global.getSector().getCampaignUI().showInteractionDialog(new DevMenu(false), Global.getSector().getPlayerFleet())) {
                e.consume();
            }
        }
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {}

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {}
}
