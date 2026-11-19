package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.QuestLogic;
import lastai.sagalib.core.QuestSkip;
import lastai.sagalib.model.QuestDef;

/**
 * SagaLib 1.3 (task #65): the game side of {@link QuestSkip}, called by the Nexerelin bridge
 * (src-optional/nexerelin, a QuestSkipPlugin named in the mod's questSkip.json) after the new game's
 * time pass, once per quest the player skipped. No class here names a Nexerelin class.
 */
public final class QuestSkips {
    private QuestSkips() {}

    /** The bridge's call for one skipped quest. Never throws. */
    public static void skipped(String questId) {
        try {
            QuestDef def = Saga.content().quest(questId);
            if (def == null) {
                SagaLog.warn("quest skip: Nexerelin skipped \"" + questId + "\", but no SagaLib quest has that id (check content.csv)");
                return;
            }
            boolean done = QuestSkip.apply(def, GameMemory.global(), new GameEffects(null), new QuestSkip.People() {
                public boolean unhide(String id) {
                    PersonAPI p = Global.getSector().getImportantPeople().getPerson(id);
                    if (p == null || p.getMarket() == null) return false;
                    CommDirectoryEntryAPI e = p.getMarket().getCommDirectory().getEntryForPerson(p);
                    if (e == null) return false;
                    e.setHidden(false);
                    return true;
                }
            });
            if (done) SagaLog.info("quest skip: " + questId + " skipped through Nexerelin (state " + QuestLogic.state(GameMemory.global(), questId)
                    + (def.skip == null ? "" : ", " + def.skip.rewards.size() + " reward(s) given") + ")");
        } catch (Throwable t) {
            SagaLog.error("quest skip: " + questId + " failed", t);
        }
    }
}
