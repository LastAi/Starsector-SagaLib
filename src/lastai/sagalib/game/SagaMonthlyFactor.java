package lastai.sagalib.game;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import lastai.sagalib.model.ProgressDef;

/**
 * A monthly factor of a progress event (SagaLib 1.2): its points each month while its conditions
 * hold. Saved inside the event: this class name must never change; it keeps only ids.
 */
public class SagaMonthlyFactor extends BaseEventFactor {
    private final String eventId;
    private final String factorId;

    public SagaMonthlyFactor(String eventId, String factorId) {
        this.eventId = eventId;
        this.factorId = factorId;
    }

    public String getFactorId() { return factorId; }

    private ProgressDef.Factor def() {
        ProgressDef d = Saga.content().progress.get(eventId);
        return d == null ? null : d.factor(factorId);
    }

    @Override
    public int getProgress(BaseEventIntel intel) {
        ProgressDef.Factor f = def();
        if (f == null) return 0;
        try {
            return RuleCommands.conditions(null, null, GameMemory.defaultMap()).all(f.conditions) ? f.points : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        ProgressDef.Factor f = def();
        return f == null ? factorId : f.desc;
    }

    @Override
    public boolean shouldShow(BaseEventIntel intel) { return def() != null && getProgress(intel) != 0; }

    @Override
    public TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        final String desc = getDesc(intel);
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara(desc, 0f);
            }
        };
    }
}
