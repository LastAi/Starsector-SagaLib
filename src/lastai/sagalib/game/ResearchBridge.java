package lastai.sagalib.game;

/**
 * SagaLib 1.4, task #77: what SagaLib asks AoTD Vaults of Knowledge about research. Implemented only by
 * the bridge in src-optional/vok (compiled against VoK's jar, loaded by class name when VoK is enabled:
 * {@link VokResearch}), so nothing here names a VoK class.
 */
public interface ResearchBridge {
    /** VoK has the tech (any enabled mod's aotd_tech_options.csv row). */
    boolean known(String techId);

    /** The faction has researched the tech; null = the player, as VoK counts it (isResearchedForPlayer: the player's faction or its commissioning faction). */
    boolean researched(String techId, String factionId);

    /**
     * Marks the tech researched for the player's faction, as VoK's console command research_tech does
     * (its rewards unlock, VoK's listeners run). Returns the tech's name, or null when the player's
     * faction had it already (nothing changes).
     */
    String grant(String techId);
}
