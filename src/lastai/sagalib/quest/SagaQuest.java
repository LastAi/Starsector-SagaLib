package lastai.sagalib.quest;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Memory;
import lastai.sagalib.core.QuestLogic;
import lastai.sagalib.game.GameMemory;
import lastai.sagalib.game.RuleCommands;
import lastai.sagalib.game.Saga;
import lastai.sagalib.model.PersonDef;
import lastai.sagalib.model.QuestDef;

import java.util.Map;

/**
 * The one mission class for every Saga Forge quest (PLAN.md §3.2). person_missions.csv /
 * bar_events.csv rows name it as plugin; the game sets the mission id (= quest id) before
 * create(), and everything else is looked up in the loaded quest JSON.
 * <p>
 * SAVED IN SAVES: keep the class name and its fields forever. It stores ids only: the quest id
 * (vanilla's missionId) and {@link #markedId}. Progress lives in memory keys (QuestLogic).
 */
public class SagaQuest extends HubMissionWithBarEvent {
    /** The entity, market or person this quest marked on the map for the current step ("" or null = none). */
    protected String markedId;
    /** Set by the library for quests it starts itself (dev tools, profiles): skip requires and state checks. */
    protected transient boolean skipChecks;

    public void setSkipChecks(boolean skip) { skipChecks = skip; }

    private QuestDef def() { return Saga.content().quest(getMissionId()); }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        String id = getMissionId();
        QuestDef q = def();
        if (q == null) {
            SagaLog.errorOnce("noquest:" + id, "mission \"" + id + "\" names SagaQuest as its plugin, but no quest \"" + id
                    + "\" is loaded (check data/config/sagaforge/content.csv)", null);
            return false;
        }
        if (!q.isMission()) {
            SagaLog.errorOnce("dialogquest:" + id, "quest \"" + id + "\" has kind \"" + q.kind + "\", so it can't be a mission", null);
            return false;
        }
        Memory mem = GameMemory.global();
        String state = QuestLogic.state(mem, id);
        if (!skipChecks) {
            if (QuestLogic.ACTIVE.equals(state)) return false;
            if (!q.repeatable && !QuestLogic.NOT_STARTED.equals(state)) return false;
            Map<String, MemoryAPI> map = GameMemory.defaultMap();
            if (createdAt != null) map.put(MemKeys.MARKET, createdAt.getMemoryWithoutUpdate());
            for (String line : q.requires) {
                if (!RuleCommands.conditions(null, null, map).eval(line)) return false;
            }
        }

        // the giver: the hub's person (contacts), the quest's own person, or a bar patron
        if (getPerson() == null && q.start.person != null && !q.start.person.isEmpty()) {
            PersonAPI p = Global.getSector().getImportantPeople().getPerson(q.start.person);
            if (p != null) setPersonOverride(p);
            else SagaLog.warn("quest " + id + ": start person \"" + q.start.person + "\" doesn't exist (yet)");
        }
        if (getPerson() == null && barEvent && createdAt != null) {
            try {
                findOrCreateGiver(createdAt, false, true);
            } catch (Throwable t) {
                SagaLog.errorOnce("giver:" + id, "quest " + id + ": no bar patron could be created", t);
                return false;
            }
        }

        QuestLogic.initVariables(mem, q);
        setStartingStage(QuestStage.ACTIVE);
        setSuccessStage(QuestStage.COMPLETED);
        setFailureStage(QuestStage.FAILED);
        setAbandonStage(QuestStage.ABANDONED);
        // rewards come from SF_Reward / SF_End only, never from vanilla's mission completion
        setNoRepChanges();
        if (q.timeLimitDays > 0) setTimeLimit(QuestStage.FAILED, (float) q.timeLimitDays, null);
        setCompletedKey();
        if (getPerson() != null) {
            PersonDef pd = Saga.content().people.get(getPerson().getId());
            if (pd != null && pd.isContact()) setGiverIsPotentialContactOnSuccess();
        }
        return true;
    }

    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        QuestLogic.start(GameMemory.global(), def(), getMissionId());
        updateMarker();
    }

    @Override
    protected void endSuccessImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        QuestLogic.end(GameMemory.global(), getMissionId(), true);
        markedId = null;
    }

    @Override
    protected void endFailureImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        // also reached when the time limit runs out
        QuestLogic.end(GameMemory.global(), getMissionId(), false);
        markedId = null;
    }

    @Override
    protected void endAbandonImpl() {
        QuestLogic.abandon(GameMemory.global(), def(), getMissionId());
        markedId = null;
        SagaLog.info("quest " + getMissionId() + " abandoned");
    }

    /** Ends the mission (vanilla stage COMPLETED or FAILED), as SF_End or a success/fail stage does. */
    public void finish(boolean success, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (isEnding() || isEnded()) return;
        setCurrentStage(success ? QuestStage.COMPLETED : QuestStage.FAILED, dialog, memoryMap);
        runTriggers();
    }

    /** The step changed: move the map marker and post the journal update (in the dialog if there is one). */
    public void stepChanged(InteractionDialogAPI dialog) {
        if (isEnding() || isEnded()) return;
        updateMarker();
        sendUpdateForNextStep(NEXT_STEP_UPDATE, dialog == null ? null : dialog.getTextPanel());
    }

    /** Ends the journal entry without any message (its quest is gone from every mod, or a profile reset it). */
    public void endQuietly() {
        unmark();
        endImmediately();
    }

    // ------------------------------------------------------------------ journal

    private String currentStep() { return QuestLogic.step(GameMemory.global(), getMissionId()); }

    @Override
    public String getBaseName() {
        QuestDef q = def();
        return q != null && q.title != null && !q.title.isEmpty() ? q.title : String.valueOf(getMissionId());
    }

    @Override
    public String getIcon() {
        QuestDef q = def();
        if (q != null && q.icon != null && !q.icon.isEmpty()) return q.icon;
        PersonAPI p = getPerson();
        if (p != null) return p.getPortraitSprite();
        return Global.getSettings().getSpriteName("intel", "important_accepted_mission");
    }

    @Override
    public String getNextStepText() {
        String step = currentStep();
        if (step == null) return null;
        if (QuestLogic.CHANGED_STEP.equals(step)) return "Changed by an update; it can be abandoned";
        QuestDef q = def();
        QuestDef.Stage st = q == null ? null : q.stage(step);
        if (st == null) return null;
        if (st.nextStep != null && !st.nextStep.isEmpty()) return st.nextStep;
        return st.title;
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        String step = currentStep();
        if (QuestLogic.CHANGED_STEP.equals(step)) {
            info.addPara("This quest was changed by an update, and the step you were at no longer exists. "
                    + "You can abandon it below; it can then be offered again.", opad, Misc.getHighlightColor(), "changed by an update");
            return;
        }
        QuestDef q = def();
        if (q == null) return;
        QuestDef.Stage st = q.stage(step);
        if (st != null) {
            String text = st.journal != null && !st.journal.isEmpty() ? st.journal : st.title;
            if (text != null && !text.isEmpty()) info.addPara(text, opad);
        } else if (q.start.offerText != null && !q.start.offerText.isEmpty()) {
            info.addPara(q.start.offerText, opad);
        }
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (!isEnding() && !isEnded()) {
            SectorEntityToken e = markerEntity(stepMarker());
            if (e != null) return getMapLocationFor(e);
        }
        return super.getMapLocation(map);
    }

    // ------------------------------------------------------------------ markers

    private String stepMarker() {
        QuestDef q = def();
        QuestDef.Stage st = q == null ? null : q.stage(currentStep());
        return st == null || st.marker == null || st.marker.isEmpty() ? null : st.marker;
    }

    /** The entity on the map for a marker id: an entity, a market's primary entity, or a person's market. */
    private static SectorEntityToken markerEntity(String id) {
        if (id == null) return null;
        SectorEntityToken e = Global.getSector().getEntityById(id);
        if (e != null) return e;
        MarketAPI m = Global.getSector().getEconomy().getMarket(id);
        if (m != null) return m.getPrimaryEntity();
        PersonAPI p = Global.getSector().getImportantPeople().getPerson(id);
        if (p != null && p.getMarket() != null) return p.getMarket().getPrimaryEntity();
        return null;
    }

    private void unmark() {
        if (markedId == null || markedId.isEmpty()) return;
        String id = markedId;
        markedId = null;
        SectorEntityToken e = Global.getSector().getEntityById(id);
        if (e != null) {
            makeUnimportant(e, QuestStage.ACTIVE);
            return;
        }
        MarketAPI m = Global.getSector().getEconomy().getMarket(id);
        if (m != null) {
            makeUnimportant(m, QuestStage.ACTIVE);
            return;
        }
        PersonAPI p = Global.getSector().getImportantPeople().getPerson(id);
        if (p != null) makeUnimportant(p, QuestStage.ACTIVE);
    }

    /** Marks the current step's marker as important (and unmarks the previous one). Also used after a load or reload. */
    public void updateMarker() {
        if (getCurrentStage() != QuestStage.ACTIVE) return;
        String want = stepMarker();
        if (want != null && want.equals(markedId)) return;
        unmark();
        if (want == null) return;
        SectorEntityToken e = Global.getSector().getEntityById(want);
        MarketAPI m = e == null ? Global.getSector().getEconomy().getMarket(want) : null;
        PersonAPI p = e == null && m == null ? Global.getSector().getImportantPeople().getPerson(want) : null;
        if (e != null) makeImportant(e, (String) null, QuestStage.ACTIVE);
        else if (m != null) makeImportant(m, (String) null, QuestStage.ACTIVE);
        else if (p != null) makeImportant(p, (String) null, QuestStage.ACTIVE);
        else {
            SagaLog.warnOnce("marker:" + getMissionId() + ":" + want, "quest " + getMissionId() + ": marker \"" + want
                    + "\" is not an entity, market or person in this game");
            return;
        }
        markedId = want;
    }

    // ------------------------------------------------------------------ lookup

    /** The running (accepted, not ended) mission of a quest, or null. */
    public static SagaQuest running(String questId) {
        for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(SagaQuest.class)) {
            SagaQuest q = (SagaQuest) i;
            if (questId.equals(q.getMissionId()) && q.getCurrentStage() == QuestStage.ACTIVE && !q.isEnding() && !q.isEnded()) return q;
        }
        return null;
    }
}
