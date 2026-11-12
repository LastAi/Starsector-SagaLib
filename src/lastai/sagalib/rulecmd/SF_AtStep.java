package lastai.sagalib.rulecmd;

import lastai.sagalib.game.QuestCondition;

/** Condition SF_AtStep &lt;quest&gt; &lt;step&gt;: the quest is active and at that step. */
public class SF_AtStep extends QuestCondition {
    @Override
    protected int minArgs() { return 2; }

    @Override
    protected String usage() { return "<quest> <step>"; }
}
