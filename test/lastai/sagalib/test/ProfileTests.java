package lastai.sagalib.test;

import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.Problems;
import lastai.sagalib.core.Profiles;
import lastai.sagalib.core.QuestLogic;
import lastai.sagalib.model.ProfileDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Applying a test profile. */
public final class ProfileTests {
    static final class RecordingPlayer implements Profiles.Player {
        final List<String> log = new ArrayList<String>();

        @Override
        public void setCredits(double credits) { log.add("credits " + (long) credits); }

        @Override
        public void setStoryPoints(int points) { log.add("storyPoints " + points); }

        @Override
        public void setCommission(String faction) { log.add("commission " + faction); }

        @Override
        public void setRep(String faction, float value) { log.add("rep " + faction + " " + value); }

        @Override
        public void addCargo(String item, int qty) { log.add("cargo " + item + " " + qty); }

        @Override
        public void setGender(String gender) { log.add("gender " + gender); }

        @Override
        public void startAt(String entity) { log.add("startAt " + entity); }
    }

    static ProfileDef profile(String singleQuoted) throws Exception {
        Problems p = new Problems();
        ProfileDef d = ProfileDef.parse(Fx.json(singleQuoted), "$", new JsonReader("t", p));
        T.yes(p.isEmpty(), "fixture: " + p.all());
        return d;
    }

    public static void secondStep() throws Exception {
        ProfileDef p = profile("{'id':'p','quests':{'vgc_q':{'state':'active','step':'return'},'gone_q':{'state':'not-started'}},"
                + "'flags':{'$global.a':'true','global.b':'3','$global.c':'text','$global.d':'false','$global.e':''},"
                + "'player':{'credits':5000,'storyPoints':2,'commission':'hegemony','rep':{'hegemony':0.3},'cargo':[{'item':'food','qty':10}],"
                + "'gender':'FEMALE','startAt':'jangala'}}");
        MapMemory m = new MapMemory();
        m.set(QuestLogic.stateKey("gone_q"), "active");
        m.set(QuestLogic.stepKey("gone_q"), "x");
        RecordingPlayer pl = new RecordingPlayer();
        Profiles.apply(m, p, Fx.map(Fx.basic()), pl);
        T.eq("active", m.get(QuestLogic.stateKey("vgc_q")), "state");
        T.eq("return", m.get(QuestLogic.stepKey("vgc_q")), "second step");
        T.eq(1f, m.get("$global.vgc_q_pay"), "variables initialized");
        T.eq(null, m.get(QuestLogic.stateKey("gone_q")), "not-started clears the state");
        T.eq(null, m.get(QuestLogic.stepKey("gone_q")), "and the step");
        T.eq(true, m.get("$global.a"), "flag true");
        T.eq(3f, m.get("$global.b"), "flag number, $ added");
        T.eq("text", m.get("$global.c"), "flag text");
        T.eq(false, m.get("$global.d"), "flag false");
        T.eq("", m.get("$global.e"), "empty flag stays text");
        T.eq("hegemony", m.get("$player.commissionFactionId"), "commission key (Play mode's)");
        T.eq("hegemony", m.get("$global.commissionFaction"), "commission global key");
        T.eq(Arrays.asList("credits 5000", "storyPoints 2", "commission hegemony", "rep hegemony 0.3", "cargo food 10", "gender FEMALE",
                "startAt jangala"), pl.log, "player changes in order");
    }

    public static void stepIsOptional() throws Exception {
        ProfileDef p = profile("{'id':'p','quests':{'vgc_q':{'state':'success'}}}");
        MapMemory m = new MapMemory();
        m.set(QuestLogic.stepKey("vgc_q"), "won");
        RecordingPlayer pl = new RecordingPlayer();
        Profiles.apply(m, p, Fx.map(Fx.basic()), pl);
        T.eq("success", m.get(QuestLogic.stateKey("vgc_q")), "state");
        T.eq("won", m.get(QuestLogic.stepKey("vgc_q")), "step kept");
        T.eq(0, pl.log.size(), "no player part, no player changes");
    }
}
