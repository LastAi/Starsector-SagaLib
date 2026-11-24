package lastai.sagalib.core;

/**
 * Memory keys as written in rules.csv: "$global.x", "$player.x", "$faction.x", ..., or "$x" (local).
 * The game adapter maps them onto the game's memories; tests use {@link MapMemory}.
 * Values are Strings, Floats and Booleans (anything else the game stored is passed through).
 */
public interface Memory {
    /** The value, or null if unset. */
    Object get(String key);

    void set(String key, Object value);

    /** SagaLib 1.4: set, expiring after {@code days} (as rules.csv's "$x = value days"). */
    default void set(String key, Object value, float days) { set(key, value); }

    void unset(String key);
}
