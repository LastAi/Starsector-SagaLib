package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.EventFactor;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Triggers;
import lastai.sagalib.model.ProgressDef;

import java.awt.Color;
import java.util.Random;
import java.util.Set;

/**
 * A progress event of a content mod (SagaLib 1.2, docs/FORMAT.md "Progress events"): vanilla's
 * BaseEventIntel (progress bar, stages, factors) driven by a {@link ProgressDef}, looked up by id.
 * One instance per definition, kept in sector memory. Saved in the player's save: this class name
 * must never change; it keeps only the definition id (the definition is read again after loading).
 */
public class SagaProgressEvent extends BaseEventIntel {
    private final String defId;
    private transient ProgressDef def;

    public static String refKey(String id) { return "$sagaforge_pe_ref_" + id; }

    /** Set once the event ended (it isn't started again). */
    public static String doneKey(String id) { return "$global.sagaforge_pe_" + id + "_done"; }

    /** The id of the last stage reached ($global.<event>_stage). */
    public static String stageKey(String id) { return "$global." + id + "_stage"; }

    public static String trigger(String event, String stage, String outcome) {
        return "SF_progress_" + event + "_" + stage + (outcome == null ? "" : "_" + outcome);
    }

    public static SagaProgressEvent get(String id) {
        Object o = Global.getSector().getMemoryWithoutUpdate().get(refKey(id));
        return o instanceof SagaProgressEvent && !((SagaProgressEvent) o).isEnded() ? (SagaProgressEvent) o : null;
    }

    /** The running event, started now if it isn't (and never ended); null if it ended or has no definition. */
    public static SagaProgressEvent getOrStart(String id) {
        SagaProgressEvent e = get(id);
        if (e != null) return e;
        if (Saga.content().progress.get(id) == null) return null;
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(doneKey(id))) return null;
        return new SagaProgressEvent(id);
    }

    public SagaProgressEvent(String id) {
        super();
        this.defId = id;
        Global.getSector().getMemoryWithoutUpdate().set(refKey(id), this);
        setup();
        Global.getSector().getIntelManager().addIntel(this, false);
        SagaLog.info("progress event " + id + " started");
    }

    public String getDefId() { return defId; }

    ProgressDef def() {
        if (def == null) def = Saga.content().progress.get(defId);
        return def;
    }

    /** The stages from the definition (S0 = start); also after loading, when the definition changed. */
    void setup() {
        ProgressDef d = def();
        if (d == null) return;
        setMaxProgress(d.max);
        boolean[] reached = new boolean[SagaStage.values().length];
        for (EventStageData s : stages) if (s.id instanceof SagaStage) reached[((SagaStage) s.id).ordinal()] = s.wasEverReached;
        stages.clear();
        addStage(SagaStage.S0, 0);
        for (int i = 0; i < d.stages.size(); i++) {
            ProgressDef.Stage st = d.stages.get(i);
            addStage(SagaStage.values()[i + 1], st.progress, false, StageIconSize.MEDIUM);
            EventStageData esd = getDataFor(SagaStage.values()[i + 1]);
            esd.keepIconBrightWhenLaterStageReached = true;
            esd.isRepeatable = d.restartAt >= 0;
            esd.wasEverReached = reached[i + 1];
        }
        getDataFor(SagaStage.S0).wasEverReached = true;
        // monthly factors: one per definition factor
        for (ProgressDef.Factor f : d.factors) {
            boolean has = false;
            for (EventFactor ef : factors) if (ef instanceof SagaMonthlyFactor && f.id.equals(((SagaMonthlyFactor) ef).getFactorId())) has = true;
            if (!has) factors.add(new SagaMonthlyFactor(defId, f.id));
        }
    }

    /** SagaLib 1.3: the definition changed after the save was read (versions by installed mods, a dev reload): use the current one. */
    void refreshDef() {
        def = null;
        if (def() != null) setup();
    }

    protected Object readResolve() {
        def = null;
        if (def() != null && stages.size() != def().stages.size() + 1) setup();
        else if (def() != null) setup(); // cheap; picks up new factors and changed thresholds
        return this;
    }

    ProgressDef.Stage stageOf(Object stageId) {
        ProgressDef d = def();
        if (d == null || !(stageId instanceof SagaStage)) return null;
        int i = ((SagaStage) stageId).ordinal() - 1;
        return i >= 0 && i < d.stages.size() ? d.stages.get(i) : null;
    }

    @Override
    protected String getName() {
        ProgressDef d = def();
        return d == null ? "Event" : d.name;
    }

    @Override
    public String getIcon() {
        ProgressDef d = def();
        if (d != null && !d.icon.isEmpty() && Textures.ensure(d.icon)) return d.icon;
        return Global.getSettings().getSpriteName("events", "stage_unknown_neutral");
    }

    @Override
    protected String getStageIconImpl(Object stageId) {
        ProgressDef.Stage st = stageOf(stageId);
        if (st != null && !st.icon.isEmpty() && Textures.ensure(st.icon)) return st.icon;
        if (stageId == SagaStage.S0) return getIcon();
        // vanilla has stage_unknown_good/_bad/_neutral only (a plain stage_unknown crashed the game, 8d-3)
        ProgressDef d = def();
        return Global.getSettings().getSpriteName("events", d != null && d.negative ? "stage_unknown_bad" : "stage_unknown_neutral");
    }

    @Override
    public TooltipCreator getStageTooltipImpl(Object stageId) {
        final ProgressDef.Stage st = stageOf(stageId);
        final EventStageData esd = getDataFor(stageId);
        if (st == null || esd == null) return null;
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addTitle(st.title);
                if (!st.text.isEmpty()) tooltip.addPara(st.text, 10f);
                esd.addProgressReq(tooltip, 10f);
            }
        };
    }

    @Override
    public void addStageDescriptionText(TooltipMakerAPI info, float width, Object stageId) {
        ProgressDef.Stage st = stageOf(stageId);
        if (st == null || !isStageActiveAndLast(stageId) || st.text.isEmpty()) return;
        info.addPara(st.text, 0f);
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
        if (addEventFactorBulletPoints(info, mode, isUpdate, tc, initPad)) return;
        if (isUpdate && getListInfoParam() instanceof EventStageData) {
            ProgressDef.Stage st = stageOf(((EventStageData) getListInfoParam()).id);
            if (st != null) info.addPara(st.title, tc, initPad);
        }
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        ProgressDef d = def();
        if (d != null && !d.tag.isEmpty()) tags.add(d.tag);
        return tags;
    }

    @Override
    public boolean isEventProgressANegativeThingForThePlayer() {
        ProgressDef d = def();
        return d != null && d.negative;
    }

    @Override
    public boolean withMonthlyFactors() {
        ProgressDef d = def();
        return d != null && !d.factors.isEmpty();
    }

    /** Runs the stage's actions (the rules on its trigger; with outcomes, one picked at random). */
    @Override
    protected void notifyStageReached(EventStageData stage) {
        ProgressDef d = def();
        ProgressDef.Stage st = stageOf(stage.id);
        if (d == null || st == null) return;
        GameMemory mem = GameMemory.global();
        mem.set(stageKey(defId), st.id);
        String outcome = null;
        if (!st.outcomes.isEmpty()) {
            float total = 0;
            for (ProgressDef.Outcome o : st.outcomes) total += o.weight;
            float x = new Random().nextFloat() * total, at = 0;
            for (ProgressDef.Outcome o : st.outcomes) {
                at += o.weight;
                if (x < at) { outcome = o.id; break; }
            }
            if (outcome == null) outcome = st.outcomes.get(st.outcomes.size() - 1).id;
            mem.set("$global." + defId + "_" + st.id + "_outcome", outcome);
        }
        SagaLog.info("progress event " + defId + ": stage " + st.id + " reached" + (outcome != null ? ", outcome " + outcome : ""));
        Triggers.add(mem, trigger(defId, st.id, outcome));
        boolean last = d.stages.indexOf(st) == d.stages.size() - 1;
        if (last) {
            if (d.restartAt >= 0) {
                setProgress(d.restartAt);
            } else {
                mem.set(doneKey(defId), true);
                endAfterDelay(30f);
            }
        }
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().getMemoryWithoutUpdate().unset(refKey(defId));
    }

    /** One-time points (SF_Progress) with the text shown in the factor list. */
    public void addPoints(int points, String desc, com.fs.starfarer.api.campaign.InteractionDialogAPI dialog) {
        addFactor(new SagaOneTimeFactor(points, desc), dialog);
    }

    @Override
    public Color getBarColor() {
        ProgressDef d = def();
        return d != null && d.negative ? Misc.getNegativeHighlightColor() : super.getBarColor();
    }
}
