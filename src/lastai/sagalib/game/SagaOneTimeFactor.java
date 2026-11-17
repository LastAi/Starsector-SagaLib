package lastai.sagalib.game;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor;

/**
 * One-time points of a progress event (SagaLib 1.2, SF_Progress), listed for 30 days like
 * vanilla's. Saved inside the event: this class name must never change; it keeps a string only.
 */
public class SagaOneTimeFactor extends BaseOneTimeFactor {
    private final String desc;

    public SagaOneTimeFactor(int points, String desc) {
        super(points);
        this.desc = desc == null ? "" : desc;
    }

    @Override
    public String getDesc(BaseEventIntel intel) { return desc; }
}
