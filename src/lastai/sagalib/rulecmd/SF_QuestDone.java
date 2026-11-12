package lastai.sagalib.rulecmd;

import lastai.sagalib.game.QuestCondition;

/** Condition SF_QuestDone &lt;quest&gt; [success|fail|any]: the quest ended that way (default any). */
public class SF_QuestDone extends QuestCondition {
    @Override
    protected int minArgs() { return 1; }

    @Override
    protected String usage() { return "<quest> [success|fail|any]"; }
}
