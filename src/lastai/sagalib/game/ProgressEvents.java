package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import lastai.sagalib.SagaLog;
import lastai.sagalib.model.ProgressDef;

/**
 * Starts progress events (SagaLib 1.2) once their start conditions hold (checked a few times a day
 * by SagaLib's campaign script). An event that ended isn't started again.
 */
public final class ProgressEvents {
    private ProgressEvents() {}

    public static void tick() {
        for (ProgressDef d : Saga.content().progress.values()) {
            if (SagaProgressEvent.get(d.id) != null) continue;
            if (Global.getSector().getMemoryWithoutUpdate().getBoolean(SagaProgressEvent.doneKey(d.id))) continue;
            boolean start;
            try {
                start = d.startConditions.isEmpty() || RuleCommands.conditions(null, null, GameMemory.defaultMap()).all(d.startConditions);
            } catch (Throwable t) {
                SagaLog.errorOnce("progress-start:" + d.id, "progress event " + d.id + ": its start conditions failed", t);
                start = false;
            }
            if (start) SagaProgressEvent.getOrStart(d.id);
        }
    }
}
