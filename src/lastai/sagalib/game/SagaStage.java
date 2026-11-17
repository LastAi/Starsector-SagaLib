package lastai.sagalib.game;

/**
 * Stage slots of SagaLib's progress events (SagaLib 1.2). Vanilla's BaseEventIntel needs enum stage
 * ids (it compares their ordinal); S0 is the start (progress 0), S1..S20 the definition's stages in
 * order. Saved in the player's save: never rename or reorder these.
 */
public enum SagaStage {
    S0, S1, S2, S3, S4, S5, S6, S7, S8, S9, S10, S11, S12, S13, S14, S15, S16, S17, S18, S19, S20
}
