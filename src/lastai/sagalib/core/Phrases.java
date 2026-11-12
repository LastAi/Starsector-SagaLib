package lastai.sagalib.core;

import lastai.sagalib.model.PhraseDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Picks a phrase's text (PLAN.md §4.3), the same way as play.ts phraseText. */
public final class Phrases {
    private Phrases() {}

    /** The text and which case gave it ("case 2", "variant 1", "otherwise"), for the log. */
    public static final class Choice {
        public final String text;
        public final String chose;

        Choice(String text, String chose) {
            this.text = text;
            this.chose = chose;
        }
    }

    /**
     * first-match: the first case whose conditions all hold, else {@code otherwise}, else "".
     * random: one of the cases (and {@code otherwise} if it isn't empty), picked at random.
     */
    public static Choice choose(PhraseDef p, Conditions conditions, Random random) {
        if ("random".equals(p.mode)) {
            List<String> all = new ArrayList<String>();
            for (PhraseDef.Case c : p.cases) all.add(c.text);
            if (p.otherwise != null && !p.otherwise.isEmpty()) all.add(p.otherwise);
            if (all.isEmpty()) return new Choice("", "variant 1");
            int i = (int) Math.floor(random.nextDouble() * all.size());
            return new Choice(all.get(i), "variant " + (i + 1));
        }
        for (int i = 0; i < p.cases.size(); i++) {
            PhraseDef.Case c = p.cases.get(i);
            if (conditions.all(c.when)) return new Choice(c.text, "case " + (i + 1));
        }
        return new Choice(p.otherwise == null ? "" : p.otherwise, "otherwise");
    }

    public static String text(PhraseDef p, Conditions conditions, Random random) {
        return choose(p, conditions, random).text;
    }
}
