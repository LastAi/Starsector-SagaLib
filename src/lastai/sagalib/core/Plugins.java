package lastai.sagalib.core;

import lastai.sagalib.SagaLog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SagaLib 1.4, task #79: a content mod's own plugin classes, run from a quest by
 * {@code SF_RunPlugin <className> ["key=value" ...]} (docs/FORMAT.md, "Plugins"). This is the part
 * without the game: reading the line, where the result goes, storing it, and turning every failure
 * into "no result". Creating the class and calling it is the {@link Host}'s job (game/PluginRunner
 * in game, a stand-in in the tests and the golden cases). Play mode does the same
 * (tools/saga-forge/src/shared/plugins.ts and play.ts).
 */
public final class Plugins {
    private Plugins() {}

    /** Where the result goes when the line names no quest and node (a hand-written line). */
    public static final String LOCAL_RESULT_KEY = "$sagaforge_plugin_result";
    /** SagaLib's own keys in the line (where the call comes from); never passed to the plugin. */
    public static final String QUEST = "quest";
    public static final String NODE = "node";

    /** {@code $global.<quest>_<node>_result}: what the plugin of that node returned last. */
    public static String resultKey(String quest, String node) { return "$global." + quest + "_" + node + "_result"; }

    /** One SF_RunPlugin line, read. */
    public static final class Call {
        public final String className;
        /** null when the line has no (or an empty) quest= / node=. */
        public final String questId;
        public final String nodeId;
        /** The plugin's arguments in line order (without quest and node); unmodifiable. */
        public final Map<String, String> args;

        public Call(String className, String questId, String nodeId, Map<String, String> args) {
            this.className = className;
            this.questId = questId;
            this.nodeId = nodeId;
            this.args = Collections.unmodifiableMap(new LinkedHashMap<String, String>(args));
        }

        /** {@code $global.<quest>_<node>_result} with both, else {@link #LOCAL_RESULT_KEY}. */
        public String resultKey() { return questId != null && nodeId != null ? Plugins.resultKey(questId, nodeId) : LOCAL_RESULT_KEY; }

        @Override
        public String toString() {
            return className + (questId != null ? " quest=" + questId : "") + (nodeId != null ? " node=" + nodeId : "") + (args.isEmpty() ? "" : " " + args);
        }
    }

    /** A failure SagaLib describes itself (no class, not a SagaPlugin): logged as one line, without a stack trace. */
    public static final class Failure extends Exception {
        public Failure(String message) { super(message); }
    }

    /** Creates the plugin and runs it (game: game/PluginRunner). Throws on any failure. */
    public interface Host {
        String run(Call call) throws Exception;
    }

    /**
     * Reads a line's arguments (class name first): {@code key=value} (split at the first "="), a word
     * without "=" is {@code key=true}, a value starting with "$" is read from memory (missing = "").
     * {@code quest=} and {@code node=} are SagaLib's. null when there is no class name.
     * <p>
     * In rules.csv every {@code key=value} is written in double quotes ({@code "chance=0.5"}): the
     * game's Misc.tokenize makes an unquoted {@code =} an operator token, and its rule parser refuses
     * an operator in a command line while loading the rules (RuleException "Operators (except for
     * '!') not allowed in command invocations"; javap, 0.98a-RC8). A quoted token is one literal, so
     * its "$" isn't read by the game either: that's why SagaLib reads "$" values itself.
     */
    public static Call parse(List<String> tokens, Memory mem) {
        if (tokens == null || tokens.isEmpty() || tokens.get(0) == null || tokens.get(0).trim().isEmpty()) return null;
        String cls = tokens.get(0).trim();
        String quest = null, node = null;
        Map<String, String> args = new LinkedHashMap<String, String>();
        for (int i = 1; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t == null || t.isEmpty()) continue;
            int eq = t.indexOf('=');
            String key = (eq < 0 ? t : t.substring(0, eq)).trim();
            String value = eq < 0 ? "true" : t.substring(eq + 1);
            if (key.isEmpty()) continue;
            value = arg(value, mem);
            if (QUEST.equals(key)) quest = value.trim().isEmpty() ? null : value.trim();
            else if (NODE.equals(key)) node = value.trim().isEmpty() ? null : value.trim();
            else args.put(key, value);
        }
        return new Call(cls, quest, node, args);
    }

    /** One argument's value: one starting with "$" is read from memory (missing = ""), else as written. */
    public static String arg(String value, Memory mem) {
        if (value == null) return "";
        if (value.startsWith("$") && mem != null) {
            Object v = mem.get(value.trim());
            return v == null ? "" : Values.str(v);
        }
        return value;
    }

    /** What a plugin returned, as stored: trimmed text; null or blank = no result. */
    public static String normalize(Object result) {
        if (result == null) return null;
        String s = String.valueOf(result).trim();
        return s.isEmpty() ? null : s;
    }

    /** Sets the call's result key to the result, or unsets it (no result), so an old value never decides. */
    public static void store(Memory mem, Call call, String result) {
        if (mem == null || call == null) return;
        if (result == null) mem.unset(call.resultKey());
        else mem.set(call.resultKey(), result);
    }

    /**
     * Runs a call through the host and stores the result. Every failure (the host's own, or anything
     * the plugin throws) is logged as one error line naming {@code where} and the class (the plugin's
     * own exceptions with their stack trace) and counts as no result. Returns the stored result.
     */
    public static String run(Memory mem, Call call, Host host, String where) {
        String result = null;
        try {
            result = normalize(host.run(call));
        } catch (Failure f) {
            SagaLog.error(where + ": plugin " + call.className + " " + f.getMessage() + "; the quest takes its \"no result\" path");
        } catch (Throwable t) {
            SagaLog.error(where + ": plugin " + call.className + " failed (" + t + "); the quest takes its \"no result\" path", t);
        }
        store(mem, call, result);
        SagaLog.info(where + ": plugin " + call + " -> " + (result == null ? "no result" : result) + " (" + call.resultKey() + ")");
        return result;
    }
}
