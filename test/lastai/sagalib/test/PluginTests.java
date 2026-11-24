package lastai.sagalib.test;

import lastai.sagalib.api.SagaPlugin;
import lastai.sagalib.api.SagaPluginContext;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.Plugins;
import lastai.sagalib.game.PluginRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SagaLib 1.4, task #79: SF_RunPlugin's line, result key and failures (core.Plugins), and the runner
 * that creates the plugin (game.PluginRunner) with the tests' own class factory instead of the game's
 * getInstanceOfScript. The game command itself: Saga Forge INGAME-CHECKS "Task #79".
 */
public final class PluginTests {
    /** A plugin that returns its "result" argument and records its context. */
    public static final class Echo implements SagaPlugin {
        static SagaPluginContext lastCtx;
        static Map<String, String> lastArgs;
        static int created;

        public Echo() { created++; }

        public String run(SagaPluginContext ctx, Map<String, String> args) {
            lastCtx = ctx;
            lastArgs = args;
            args.put("changed", "by the plugin"); // a copy: changes nothing
            ctx.addText("never shown: no dialog");
            return args.get("result");
        }
    }

    /** A plugin that fails on purpose. */
    public static final class Thrower implements SagaPlugin {
        public String run(SagaPluginContext ctx, Map<String, String> args) { throw new IllegalStateException("on purpose"); }
    }

    /** Not a plugin. */
    public static final class NotAPlugin {}

    /** A plugin without a public no-argument constructor. */
    public static final class NoDefault implements SagaPlugin {
        public NoDefault(String x) {}
        public String run(SagaPluginContext ctx, Map<String, String> args) { return "x"; }
    }

    /** The tests' factory: the class by its binary name, a new instance (what getInstanceOfScript does in game). */
    static final PluginRunner.Factory FACTORY = new PluginRunner.Factory() {
        public Object create(String className) throws Exception {
            return Class.forName(className).newInstance();
        }
    };

    static final String ECHO = PluginTests.class.getName() + "$Echo";

    static List<String> toks(String... t) { return new ArrayList<String>(Arrays.asList(t)); }

    public static void readsTheLine() {
        MapMemory mem = new MapMemory();
        mem.set("$global.pga_name", "Ana");
        mem.set("$global.pga_n", Float.valueOf(3f));
        Plugins.Call c = Plugins.parse(toks("my.mod.Plugin", "quest=pga_test", "node=flip", "chance=0.5", "text=Heads or tails?", "loud", "who=$global.pga_name",
                "n=$global.pga_n", "none=$global.nothing", "eq=a=b", "=skipped", ""), mem);
        T.eq("my.mod.Plugin", c.className, "class");
        T.eq("pga_test", c.questId, "quest");
        T.eq("flip", c.nodeId, "node");
        Map<String, String> want = new LinkedHashMap<String, String>();
        want.put("chance", "0.5");
        want.put("text", "Heads or tails?");
        want.put("loud", "true");
        want.put("who", "Ana");
        want.put("n", "3");
        want.put("none", "");
        want.put("eq", "a=b");
        T.eq(want, c.args, "args in order, $-values read, a bare word is true, split at the first =");
        T.eq("$global.pga_test_flip_result", c.resultKey(), "result key");
        T.eq(null, Plugins.parse(toks(), mem), "no class");
        T.eq(null, Plugins.parse(toks("  "), mem), "blank class");
        Plugins.Call bare = Plugins.parse(toks("a.B", "x=1", "quest="), mem);
        T.eq(null, bare.questId, "an empty quest is none");
        T.eq(Plugins.LOCAL_RESULT_KEY, bare.resultKey(), "without quest and node: the local key");
        T.eq(Plugins.LOCAL_RESULT_KEY, Plugins.parse(toks("a.B", "quest=q"), mem).resultKey(), "a quest alone isn't enough");
    }

    /**
     * Why every key=value is quoted in rules.csv: the game's tokenizer makes an unquoted "=" an
     * operator token (and its rule parser refuses operators in a command line at load), while a
     * quoted "key=value" is one literal token, "$" and all. Misc's static setup reads the settings,
     * so a stand-in SettingsAPI is set for the call.
     */
    public static void theGamesTokenizerKeepsAQuotedKeyValueWhole() {
        Object before = com.fs.starfarer.api.Global.getSettings();
        com.fs.starfarer.api.Global.setSettings((com.fs.starfarer.api.SettingsAPI) java.lang.reflect.Proxy.newProxyInstance(PluginTests.class.getClassLoader(),
                new Class<?>[]{com.fs.starfarer.api.SettingsAPI.class}, new java.lang.reflect.InvocationHandler() {
                    public Object invoke(Object p, java.lang.reflect.Method m, Object[] a) {
                        Class<?> r = m.getReturnType();
                        if (r == float.class) return 1f;
                        if (r == int.class) return 1;
                        if (r == boolean.class) return false;
                        if (r == long.class) return 1L;
                        if (r == double.class) return 1d;
                        return null;
                    }
                }));
        try {
            List<com.fs.starfarer.api.util.Misc.Token> t = com.fs.starfarer.api.util.Misc.tokenize("SF_RunPlugin a.B \"quest=q\" \"text=Heads or tails?\" \"who=$global.x\" loud k=v");
            List<String> s = new ArrayList<String>();
            for (com.fs.starfarer.api.util.Misc.Token x : t) s.add(x.string + (x.isOperator() ? "(op)" : x.isVariable() ? "(var)" : ""));
            T.eq(Arrays.asList("SF_RunPlugin", "a.B", "quest=q", "text=Heads or tails?", "who=$global.x", "loud", "k", "=(op)", "v"), s, "tokens");
        } finally {
            com.fs.starfarer.api.Global.setSettings((com.fs.starfarer.api.SettingsAPI) before);
        }
    }

    public static void storesAndClearsTheResult() {
        MapMemory mem = new MapMemory();
        Plugins.Call c = Plugins.parse(toks("a.B", "quest=q", "node=n"), mem);
        final String[] ret = {"ok"};
        Plugins.Host h = new Plugins.Host() { public String run(Plugins.Call call) { return ret[0]; } };
        T.eq("ok", Plugins.run(mem, c, h, "test"), "ok");
        T.eq("ok", mem.get("$global.q_n_result"), "stored");
        ret[0] = "  odd ";
        Plugins.run(mem, c, h, "test");
        T.eq("odd", mem.get("$global.q_n_result"), "trimmed");
        ret[0] = "  ";
        T.eq(null, Plugins.run(mem, c, h, "test"), "blank");
        T.eq(null, mem.get("$global.q_n_result"), "blank unsets the old result");
        mem.set("$global.q_n_result", "old");
        ret[0] = null;
        Plugins.run(mem, c, h, "test");
        T.eq(null, mem.get("$global.q_n_result"), "null unsets it");
        mem.set("$global.q_n_result", "old");
        Plugins.run(mem, c, new Plugins.Host() { public String run(Plugins.Call call) { throw new RuntimeException("boom"); } }, "test");
        T.eq(null, mem.get("$global.q_n_result"), "a failure unsets it");
        mem.set("$global.q_n_result", "old");
        Plugins.run(mem, c, new Plugins.Host() { public String run(Plugins.Call call) throws Exception { throw new Plugins.Failure("isn't a plugin"); } }, "test");
        T.eq(null, mem.get("$global.q_n_result"), "SagaLib's own failure too");
        Plugins.run(mem, c, new Plugins.Host() { public String run(Plugins.Call call) { throw new StackOverflowError(); } }, "test");
        T.eq(null, mem.get("$global.q_n_result"), "an Error too");
        Plugins.Call local = Plugins.parse(toks("a.B"), mem);
        ret[0] = "done";
        Plugins.run(mem, local, h, "test");
        T.eq("done", mem.get(Plugins.LOCAL_RESULT_KEY), "the local key");
    }

    public static void runnerCreatesAFreshPluginEachCall() {
        MapMemory mem = new MapMemory();
        Plugins.Call c = Plugins.parse(toks(ECHO, "quest=pga_test", "node=flip", "result=ok", "x=1"), mem);
        int before = Echo.created;
        T.eq("ok", Plugins.run(mem, c, PluginRunner.host(FACTORY, "pga_test_flip", null, null), "test"), "ran");
        Plugins.run(mem, c, PluginRunner.host(FACTORY, "pga_test_flip", null, null), "test");
        T.eq(2, Echo.created - before, "a new instance per call");
        T.eq("pga_test", Echo.lastCtx.getQuestId(), "context: quest");
        T.eq("flip", Echo.lastCtx.getNodeId(), "context: node");
        T.eq("pga_test_flip", Echo.lastCtx.getRuleId(), "context: rule");
        T.eq(null, Echo.lastCtx.getDialog(), "context: no dialog");
        T.eq(null, Echo.lastCtx.getMemoryMap(), "context: no memory map");
        T.no(Echo.lastCtx.hasDialog(), "hasDialog");
        T.no(Echo.lastCtx.addText("x"), "addText without a dialog");
        T.no(Echo.lastCtx.addTextHighlighted("x", "x"), "addTextHighlighted without a dialog");
        T.eq("1", Echo.lastArgs.get("x"), "args");
        T.no(Echo.lastArgs.containsKey("quest"), "quest isn't an argument");
        T.eq(null, c.args.get("changed"), "the plugin's changes to its map don't reach the call");
        T.eq("ok", mem.get("$global.pga_test_flip_result"), "stored");
    }

    public static void runnerFailuresAreNoResult() {
        MapMemory mem = new MapMemory();
        String[][] cases = {
                {PluginTests.class.getName() + "$Thrower", "failed (java.lang.IllegalStateException"},
                {PluginTests.class.getName() + "$NotAPlugin", "isn't a plugin"},
                {"no.such.Plugin", "isn't in any enabled mod's jars"},
                {PluginTests.class.getName() + "$NoDefault", "public no-argument constructor"},
        };
        for (String[] cs : cases) {
            mem.set("$global.q_n_result", "old");
            Plugins.Call c = Plugins.parse(toks(cs[0], "quest=q", "node=n", "result=ok"), mem);
            T.eq(null, Plugins.run(mem, c, PluginRunner.host(FACTORY, "r", null, null), "test"), cs[0]);
            T.eq(null, mem.get("$global.q_n_result"), cs[0] + ": unset");
        }
        // the messages, as logged
        final String[] why = {null};
        for (String[] cs : cases) {
            if (cs[0].endsWith("$Thrower")) continue;
            try {
                PluginRunner.host(FACTORY, "r", null, null).run(Plugins.parse(toks(cs[0]), mem));
                why[0] = null;
            } catch (Exception e) {
                why[0] = e.getMessage();
            }
            T.yes(why[0] != null && why[0].contains(cs[1]), cs[0] + ": " + why[0]);
        }
        // getInstanceOfScript's wrapping (RuntimeException "Problem loading class [x]" around the cause)
        PluginRunner.Factory game = new PluginRunner.Factory() {
            public Object create(String className) {
                throw new RuntimeException("Problem loading class [" + className + "]", new ClassNotFoundException(className));
            }
        };
        try {
            PluginRunner.host(game, "r", null, null).run(Plugins.parse(toks("x.Y"), mem));
            T.yes(false, "should fail");
        } catch (Exception e) {
            T.yes(e.getMessage().contains("isn't in any enabled mod's jars (no class x.Y"), e.getMessage());
        }
    }
}
