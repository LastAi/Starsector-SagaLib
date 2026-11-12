package lastai.sagalib.quest;

/**
 * The vanilla stage of every SagaQuest (PLAN.md §3.2, option A): only the lifecycle. Designer
 * steps are strings in $global.&lt;quest&gt;_step. SAVED IN SAVES: never rename or remove a value.
 */
public enum QuestStage {
    ACTIVE,
    COMPLETED,
    FAILED,
    ABANDONED
}
