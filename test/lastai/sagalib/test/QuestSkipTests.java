package lastai.sagalib.test;

import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.Problems;
import lastai.sagalib.core.QuestSkip;
import lastai.sagalib.model.QuestDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** SagaLib 1.3 (Saga Forge task #65): a quest skipped on Nexerelin's new-game screen. */
public final class QuestSkipTests {
    static QuestDef skipQuest() throws Exception {
        return Fx.quest("{'format':1,'id':'vgc_q','title':'Q','kind':'quest',"
                + "'stages':[{'id':'won','title':'Won','kind':'success'}],"
                + "'rewards':[{'id':'done','credits':500}],"
                + "'skip':{'rewards':[{'id':'done','credits':1000,'rep':[{'faction':'hegemony','amount':5}]},{'id':'skip_1','xp':200}],'unhide':['vgc_reyes','vgc_gone']}}");
    }

    public static void theSkipSectionIsRead() throws Exception {
        QuestDef q = skipQuest();
        T.yes(q.skip != null, "skip read");
        T.eq(2, q.skip.rewards.size(), "two rewards");
        T.eq(1000L, q.skip.rewards.get(0).credits, "the skip's own copy of the reward");
        T.eq(Arrays.asList("vgc_reyes", "vgc_gone"), q.skip.unhide, "people to show");
        T.yes(Fx.basic().skip == null, "no skip section: null");
        Problems p = new Problems();
        QuestDef.parse(Fx.json("{'format':1,'id':'x','title':'X','kind':'quest','skip':{'rewards':[{'credits':5}]}}"), new JsonReader("x.json", p));
        T.yes(!p.isEmpty(), "a skip reward without an id is a problem");
    }

    public static void rewardsAreGivenOnce() throws Exception {
        QuestDef q = skipQuest();
        MapMemory m = new MapMemory();
        Fx.RecordingEffects fx = new Fx.RecordingEffects();
        final List<String> shown = new ArrayList<String>();
        QuestSkip.People people = new QuestSkip.People() {
            public boolean unhide(String id) { shown.add(id); return !"vgc_gone".equals(id); }
        };
        T.yes(QuestSkip.apply(q, m, fx, people), "first time: done");
        T.eq(Arrays.asList("credits 1000", "rep hegemony 5.0", "xp 200"), fx.log, "the skip's rewards, in Play mode's order");
        T.eq(Arrays.asList("vgc_reyes", "vgc_gone"), shown, "both asked (one missing is only logged)");
        T.eq(Boolean.TRUE, m.get(QuestSkip.skippedKey("vgc_q")), "recorded");
        T.eq("$global.sagaforge_skipped_vgc_q", QuestSkip.skippedKey("vgc_q"), "the key");
        T.yes(!QuestSkip.apply(q, m, fx, people), "second time: nothing");
        T.eq(3, fx.log.size(), "no reward twice");
    }

    public static void aQuestWithoutSkipIsOnlyRecorded() throws Exception {
        MapMemory m = new MapMemory();
        Fx.RecordingEffects fx = new Fx.RecordingEffects();
        T.yes(QuestSkip.apply(Fx.basic(), m, fx, null), "done");
        T.eq(0, fx.log.size(), "no rewards");
        T.eq(Boolean.TRUE, m.get(QuestSkip.skippedKey("vgc_q")), "recorded");
        T.yes(!QuestSkip.apply(null, m, fx, null), "no quest: nothing");
    }
}
