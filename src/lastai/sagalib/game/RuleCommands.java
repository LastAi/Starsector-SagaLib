package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.CommandPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Conditions;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs rules.csv condition commands from Java, for phrase cases and quest {@code requires}: the
 * command class is found in the ruleCommandPackages (core first, as the game does), created and
 * its execute() called with Misc.tokenize(args), like the rules engine.
 */
public final class RuleCommands {
    private RuleCommands() {}

    private static final String[] FALLBACK_PACKAGES = {
            "com.fs.starfarer.api.impl.campaign.rulecmd",
            "com.fs.starfarer.api.impl.campaign.rulecmd.salvage",
            "com.fs.starfarer.api.impl.campaign.rulecmd.newgame",
            "com.fs.starfarer.api.impl.campaign.rulecmd.missions",
            "com.fs.starfarer.api.impl.campaign.rulecmd.academy",
            "lastai.sagalib.rulecmd",
    };
    /** Command name to class; a null value = looked for and not found. */
    private static final Map<String, Class<?>> CLASSES = new HashMap<String, Class<?>>();
    private static List<String> packages;

    private static synchronized List<String> packages() {
        if (packages != null) return packages;
        List<String> out = new ArrayList<String>();
        try {
            JSONArray a = Global.getSettings().getJSONArray("ruleCommandPackages");
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Throwable t) {
            SagaLog.warn("can't read ruleCommandPackages from settings.json (" + t + "); using the vanilla packages");
        }
        for (String p : FALLBACK_PACKAGES) if (!out.contains(p)) out.add(p);
        packages = out;
        return out;
    }

    private static synchronized Class<?> find(String name) {
        if (CLASSES.containsKey(name)) return CLASSES.get(name);
        Class<?> found = null;
        ClassLoader cl = Global.getSettings().getScriptClassLoader();
        for (String p : packages()) {
            try {
                Class<?> c = Class.forName(p + "." + name, true, cl);
                if (CommandPlugin.class.isAssignableFrom(c)) {
                    found = c;
                    break;
                }
            } catch (Throwable ignored) {
                // not in this package
            }
        }
        CLASSES.put(name, found);
        return found;
    }

    /**
     * Runs a condition command: {@code body} is the whole line without "!" ("RepIsAtWorst hegemony FAVORABLE").
     * null if there is no such command; false (logged once) if it throws, e.g. because it needs a dialog.
     */
    public static Boolean run(String name, String body, String ruleId, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        Class<?> c = find(name);
        if (c == null) return null;
        try {
            CommandPlugin cmd = (CommandPlugin) c.newInstance();
            List<Token> all = Misc.tokenize(body);
            List<Token> params = new ArrayList<Token>(all.size() > 1 ? all.subList(1, all.size()) : new ArrayList<Token>());
            return cmd.execute(ruleId, dialog, params, memoryMap);
        } catch (Throwable t) {
            SagaLog.errorOnce("cmd-fail:" + body, "condition \"" + body + "\" failed (" + t + "); counted as false"
                    + (dialog == null ? " (no dialog here: phrases and requires can only use commands that work without one)" : ""), null);
            return false;
        }
    }

    /** A condition evaluator for the game: memory keys, SF_ conditions and every other rule command. */
    public static Conditions conditions(final String ruleId, final InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        final GameMemory mem = new GameMemory(memoryMap);
        return new Conditions(mem, new Conditions.Commands() {
            @Override
            public Boolean eval(String name, List<String> args, String body) {
                return run(name, body, ruleId, dialog, mem.map());
            }
        });
    }
}
