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

    void unset(String key);
}
