package lastai.sagalib.core;

import lastai.sagalib.SagaLog;
import lastai.sagalib.model.QuestDef;

/**
 * SagaLib 1.3 (task #65): a quest skipped on Nexerelin's new-game "skip quests" screen. Nexerelin sets
 * the memory flags of the chosen ending from the mod's questSkip.json (its own applyMemKeys, which only
 * runs through a plugin: SagaLib's bridge, src-optional/nexerelin). The bridge then calls this once per
 * skipped quest after the time pass: the ending's rewards (the quest's "skip" section) and the people
 * to show in their comm directory. {@link #skippedKey} records it, so it never runs twice.
 */
public final class QuestSkip {
    private QuestSkip() {}

    /** True once SagaLib gave a skipped quest's rewards; also a condition for dialog ("skipped through Nexerelin"). */
    public static String skippedKey(String q) { return "$global.sagaforge_skipped_" + q; }

    /** Shows a person in their market's comm directory; false if there is no such person or entry. */
    public interface People {
        boolean unhide(String personId);
    }

    /**
     * Gives a skipped quest's rewards and shows its people, once. False (nothing done) if it was done
     * before. A quest without a "skip" section is only recorded.
     */
    public static boolean apply(QuestDef def, Memory m, Effects fx, People people) {
        if (def == null) return false;
        String key = skippedKey(def.id);
        if (Boolean.TRUE.equals(m.get(key))) return false;
        m.set(key, Boolean.TRUE);
        if (def.skip == null) return true;
        for (QuestDef.Reward r : def.skip.rewards) QuestLogic.apply(r, fx, m);
        for (String p : def.skip.unhide) {
            if (people != null && !people.unhide(p)) SagaLog.warn("quest skip " + def.id + ": " + p + " isn't in a comm directory, so they can't be shown");
        }
        return true;
    }
}
