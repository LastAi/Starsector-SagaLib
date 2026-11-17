package lastai.sagalib.core;

/** What a reward does to the player. The game adapter also prints vanilla's "Gained ..." lines. */
public interface Effects {
    void credits(long amount);

    /** Reputation in points (5 = +0.05), as vanilla's AdjustRep. */
    void factionRep(String faction, double points);

    void personRep(String person, double points);

    void item(String item, int qty);

    void ship(String variant);

    void xp(long xp);

    void storyPoints(int points);
    /** SagaLib 1.2 (Phase 8a): items rolled from a drop group, as salvage does: chances = random rolls, value = credits' worth. */
    default void loot(String group, int chances, int value) {}
}
