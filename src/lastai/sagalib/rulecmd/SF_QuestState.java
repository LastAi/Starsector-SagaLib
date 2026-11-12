package lastai.sagalib.rulecmd;

import lastai.sagalib.game.QuestCondition;

/** Condition SF_QuestState &lt;quest&gt; notStarted|active|success|fail. */
public class SF_QuestState extends QuestCondition {
    @Override
    protected int minArgs() { return 2; }

    @Override
    protected String usage() { return "<quest> notStarted|active|success|fail"; }
}
