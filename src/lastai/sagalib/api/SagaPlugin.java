package lastai.sagalib.api;

import java.util.Map;

/**
 * A content mod's own Java, run from a quest (SagaLib 1.4, task #79; docs/FORMAT.md, "Plugins").
 * <p>
 * A mod made with Saga Forge ships a small jar (listed under "jars" in its mod_info.json) with one
 * or more classes that implement this interface. A quest's Plugin node, an Action's "Run a plugin",
 * a world event or a progress stage runs one through the rule command
 * {@code SF_RunPlugin <className> ["key=value" ...]} (each pair in double quotes: the game refuses an
 * unquoted "=" in a command line while loading the rules).
 * <ul>
 * <li>SagaLib creates a <b>new instance for every call</b> (vanilla's
 * {@code SettingsAPI.getInstanceOfScript}), so the class needs a public no-argument constructor. Keep
 * nothing in fields between calls: SagaLib saves nothing of the plugin; put what must last into
 * memory keys or the sector's persistent data yourself.</li>
 * <li>{@link #run} gets the call's context and its {@code key=value} arguments (in line order;
 * a value written as {@code $key} is already read from memory; a word without "=" is
 * {@code "true"}). The map is a copy: changing it changes nothing.</li>
 * <li>It returns a <b>result id</b> the quest branches on (one of the Plugin node's declared
 * results, e.g. {@code "ok"}), or {@code null} / {@code ""} for no result. SagaLib stores it, trimmed,
 * in {@code $global.<quest>_<node>_result} (or {@code $sagaforge_plugin_result} when the line names
 * no quest and node).</li>
 * <li>Anything it throws is caught: SagaLib logs one error line (with the stack trace) and the
 * quest takes its "no result" path. The game keeps running.</li>
 * </ul>
 * This interface is SagaLib's stable API: its method won't change within SagaLib 1.x (new
 * methods, if any, come with a default).
 */
public interface SagaPlugin {
    /**
     * Does the plugin's work.
     *
     * @param ctx  where the call comes from: quest and node ids, the dialog (may be null), the rule's
     *             memory map (may be null), and {@link SagaPluginContext#addText} for the dialog.
     * @param args the line's {@code key=value} arguments, without SagaLib's own {@code quest} and
     *             {@code node}; never null.
     * @return a result id, or null / "" for no result.
     */
    String run(SagaPluginContext ctx, Map<String, String> args);
}
