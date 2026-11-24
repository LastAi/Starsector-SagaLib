package lastai.sagalib.test;

import lastai.sagalib.core.Research;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SagaLib 1.4, task #77: AoTD Vaults of Knowledge research through core.Research (the bridge itself
 * needs the game and VoK: Saga Forge INGAME-CHECKS "Task #77").
 */
public final class ResearchTests {
    /** A VoK stand-in: these techs exist, the player has `done`, an AI faction has `ai`. */
    private static final class Fake implements Research.Source {
        final Set<String> techs = new HashSet<String>(Arrays.asList("a_tech", "b_tech", "c_tech"));
        final Set<String> done = new HashSet<String>(Arrays.asList("a_tech"));
        final Set<String> ai = new HashSet<String>(Arrays.asList("b_tech"));
        final List<String> granted = new ArrayList<String>();

        public boolean known(String id) { return techs.contains(id); }

        public boolean researched(String id, String faction) { return faction == null ? done.contains(id) : ai.contains(id); }

        public String grant(String id) {
            if (done.contains(id)) return null;
            done.add(id);
            granted.add(id);
            return "Tech " + id;
        }
    }

    private static void reset() {
        Research.setup(null, null);
        Research.resetLog();
    }

    public static void withoutVokNothingIsGrantedAndLoggedOnce() {
        try {
            reset();
            Research.Grant g = Research.grant("b_tech");
            T.no(g.done, "nothing granted without VoK");
            T.yes(g.nameOrWhy.contains("Vaults of Knowledge isn't enabled"), "why: " + g.nameOrWhy);
            Research.grant("b_tech");
            Research.grant("b_tech");
            T.eq(1, Research.loggedCount(), "logged once for the same tech and reason");
            Research.grant("c_tech");
            T.eq(2, Research.loggedCount(), "another tech is logged once too");
            T.no(Research.has("a_tech", null), "has is false without VoK");
            T.no(Research.has("b_tech", "hegemony"), "a faction's too");
            T.eq("Vaults of Knowledge isn't enabled", Research.status(), "status for the dev menu");
            Research.setup(null, "this SagaLib was built without its Vaults of Knowledge bridge");
            T.yes(Research.grant("b_tech").nameOrWhy.startsWith("this SagaLib was built without"), "the reason without the bridge");
        } finally {
            reset();
        }
    }

    public static void withVokGrantAndAsk() {
        try {
            reset();
            Fake f = new Fake();
            Research.setup(f, null);
            T.eq("", Research.status(), "bridge ready");
            T.yes(Research.has("a_tech", null), "the player has a_tech");
            T.yes(Research.has("a_tech", "player"), "\"player\" is the player");
            T.no(Research.has("b_tech", null), "not b_tech yet");
            T.yes(Research.has("b_tech", "hegemony"), "an AI faction's own answer");
            Research.Grant g = Research.grant("b_tech");
            T.yes(g.done, "granted");
            T.eq("Tech b_tech", g.nameOrWhy, "with its name for the dialog line");
            T.yes(Research.has("b_tech", null), "now the player has it");
            Research.Grant again = Research.grant("b_tech");
            T.no(again.done, "already researched: nothing changes");
            T.eq(null, again.nameOrWhy, "and it isn't a failure");
            T.eq(Arrays.asList("b_tech"), f.granted, "VoK was asked to grant it once");
            Research.Grant unknown = Research.grant("nope");
            T.no(unknown.done, "a tech VoK doesn't have");
            T.yes(unknown.nameOrWhy.contains("has no tech \"nope\""), "why: " + unknown.nameOrWhy);
            T.no(Research.has("nope", null), "has is false for it");
            T.no(Research.grant("").done, "no id");
        } finally {
            reset();
        }
    }

    public static void vokErrorsAreCaught() {
        try {
            reset();
            Research.setup(new Research.Source() {
                public boolean known(String id) { return true; }
                public boolean researched(String id, String faction) { throw new IllegalStateException("boom"); }
                public String grant(String id) { throw new IllegalStateException("boom"); }
            }, null);
            T.no(Research.has("a_tech", null), "an error in VoK is false");
            Research.Grant g = Research.grant("a_tech");
            T.no(g.done, "an error in VoK grants nothing");
            T.yes(g.nameOrWhy.contains("failed"), "and says so");
        } finally {
            reset();
        }
    }
}
