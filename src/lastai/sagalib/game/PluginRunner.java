package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import lastai.sagalib.api.SagaPlugin;
import lastai.sagalib.api.SagaPluginContext;
import lastai.sagalib.core.Plugins;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SagaLib 1.4, task #79: creates a mod's plugin class and runs it for core.Plugins. In game the class
 * comes from vanilla's {@code SettingsAPI.getInstanceOfScript}: the script class loader (every enabled
 * mod's jars), the Class cached, a new instance each call; any exception while loading or creating it
 * comes back as RuntimeException "Problem loading class [x]" (javap, 0.98a-RC8: ScriptStore). The
 * tests pass their own {@link Factory}.
 */
public final class PluginRunner {
    private PluginRunner() {}

    /** Makes a new instance of a class by name. */
    public interface Factory {
        Object create(String className) throws Exception;
    }

    /** The game's: vanilla's getInstanceOfScript. */
    public static final Factory GAME = new Factory() {
        public Object create(String className) {
            return Global.getSettings().getInstanceOfScript(className);
        }
    };

    /** The host for one rule: a fresh instance, the type check, a fresh context, the call. */
    public static Plugins.Host host(final Factory factory, final String ruleId, final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap) {
        return new Plugins.Host() {
            public String run(Plugins.Call c) throws Exception {
                Object o;
                try {
                    o = factory.create(c.className);
                } catch (Throwable t) {
                    throw new Plugins.Failure(whyNotCreated(t));
                }
                if (o == null) throw new Plugins.Failure("can't be created (no such class in the enabled mods' jars?)");
                if (!(o instanceof SagaPlugin)) throw new Plugins.Failure("isn't a plugin: " + o.getClass().getName() + " doesn't implement " + SagaPlugin.class.getName());
                SagaPluginContext ctx = new SagaPluginContext(c.questId, c.nodeId, ruleId, dialog, memoryMap);
                return ((SagaPlugin) o).run(ctx, new LinkedHashMap<String, String>(c.args));
            }
        };
    }

    /** One line about why the class couldn't be created: the first known cause in the chain, else the innermost. */
    static String whyNotCreated(Throwable t) {
        Throwable root = t;
        for (Throwable x = t; x != null; x = x.getCause() == x ? null : x.getCause()) {
            root = x;
            String n = x.getClass().getName();
            if (n.equals("java.lang.ClassNotFoundException")) return "isn't in any enabled mod's jars (no class " + x.getMessage() + "; is the jar listed under \"jars\" in its mod_info.json?)";
            if (n.equals("java.lang.NoClassDefFoundError")) return "can't be loaded: it needs " + x.getMessage() + ", which isn't there (another mod or jar missing?)";
            if (n.equals("java.lang.InstantiationException") || n.equals("java.lang.IllegalAccessException") || n.equals("java.lang.NoSuchMethodException"))
                return "can't be created: it needs to be a public class with a public no-argument constructor (" + x + ")";
            if (n.equals("java.lang.UnsupportedClassVersionError")) return "was compiled for a newer Java than the game runs (" + x.getMessage() + "); compile with --release 8 (or 17)";
        }
        return "can't be created (" + root + ")";
    }
}
