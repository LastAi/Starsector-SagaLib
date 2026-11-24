package lastai.sagalib.game;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Settings;
import lastai.sagalib.core.Values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SagaLib 1.3 (task #62): the content mods' settings (docs/FORMAT.md, "Mod settings (LunaLib)") as
 * {@code $global.<fieldID>}, set on every game load: the player's values from LunaLib through the
 * guarded bridge (loaded by class name only when LunaLib is enabled, like {@link MagicBounties}),
 * else the defaults from the mod's own LunaSettings.csv. A change saved in LunaLib's menu while a
 * campaign runs is applied at once. Nothing here is saved: the keys are written again on every load.
 * SagaLib 1.4: except the values recorded when the game started ({@link #record}), which versions use.
 */
public final class ModSettings {
    private ModSettings() {}

    public static final String LUNALIB = "lunalib";
    static final String BRIDGE = "lastai.sagalib.bridge.lunalib.LunaLibBridge";

    private static volatile SettingsBridge bridge;
    private static volatile boolean tried, listening;
    /** Why there is no bridge ("" while there is one), for the log and the dev menu. */
    private static volatile String status = "not loaded yet";
    /** The last value set for each field id, with where it came from (dev menu). */
    private static final Map<String, Settings.Applied> last = Collections.synchronizedMap(new LinkedHashMap<String, Settings.Applied>());

    /** "" when the bridge is ready, else why not. */
    public static String status() { return status; }

    /** The last apply of a field, or null. */
    public static Settings.Applied last(String fieldId) { return last.get(fieldId); }

    /** Each content mod's settings, by the mod's id, in file order. */
    public static Map<String, List<Settings.Field>> byMod() {
        Map<String, List<Settings.Field>> out = new LinkedHashMap<String, List<Settings.Field>>();
        for (Settings.Field f : Saga.content().settings) {
            List<Settings.Field> l = out.get(f.mod);
            if (l == null) out.put(f.mod, l = new ArrayList<Settings.Field>());
            l.add(f);
        }
        return out;
    }

    /** Sets every content mod's settings into the sector's memory (game load; after a dev reload). */
    public static synchronized void apply() {
        last.clear();
        if (byMod().isEmpty()) {
            if (!tried) status = "no content mod has settings";
            return;
        }
        if (!tried) {
            tried = true;
            bridge = load();
        }
        for (Map.Entry<String, List<Settings.Field>> e : byMod().entrySet()) {
            List<Settings.Applied> done = applyMod(e.getValue());
            int fromPlayer = 0;
            for (Settings.Applied a : done) if (a.fromPlayer) fromPlayer++;
            String how = bridge == null ? "defaults; " + status
                    : fromPlayer == done.size() ? "LunaLib"
                    : fromPlayer + " from LunaLib, " + (done.size() - fromPlayer) + " defaults";
            SagaLog.info("settings of " + e.getKey() + ": " + done.size() + " values (" + how + ")");
        }
        if (bridge != null && !listening) {
            listening = true;
            try {
                bridge.listen(new SettingsBridge.Listener() {
                    public void changed(String modId) { onChanged(modId); }
                });
            } catch (Throwable t) {
                SagaLog.errorOnce("settings-listen", "LunaLib bridge: registering the settings listener failed; menu changes apply on the next load", t);
            }
        }
    }

    /**
     * SagaLib 1.4 (Saga Forge #67 U18): records each field's value in this save if it isn't yet
     * ({@link Settings#record}): at a new game before versions are chosen, and on every load (a save
     * from before 1.4, or a field a mod's update added, gets the value it has now). Never changes a
     * recorded value: versions by a setting stay as the game started.
     */
    public static synchronized void record() {
        // SagaLib 1.4 (Saga Forge #67 U37): the player's gender, which versions may depend on, fixed for this save
        try {
            com.fs.starfarer.api.characters.PersonAPI pl = Global.getSector() == null ? null : Global.getSector().getPlayerPerson();
            String g = pl == null || pl.getGender() == null ? null : pl.getGender().name();
            if (Settings.recordPlayerGender(GameMemory.global(), g)) SagaLog.info("the player's gender recorded for this game (versions by it use this): " + g);
            else if (Settings.recorded(GameMemory.global(), Settings.PLAYER_GENDER) == null) SagaLog.warn("the player's gender isn't known yet (" + g + "): versions by it wait for a later load");
        } catch (Throwable t) {
            SagaLog.errorOnce("record-player-gender", "recording the player's gender failed", t);
        }
        if (byMod().isEmpty()) return;
        if (!tried) {
            tried = true;
            bridge = load();
        }
        for (Map.Entry<String, List<Settings.Field>> e : byMod().entrySet()) {
            List<Settings.Field> done = Settings.record(GameMemory.global(), e.getValue(), player());
            if (done.isEmpty()) continue;
            StringBuilder b = new StringBuilder();
            for (Settings.Field f : done) b.append(b.length() == 0 ? "" : ", ").append(f.id).append(' ').append(Settings.recorded(GameMemory.global(), f.id));
            SagaLog.info("settings of " + e.getKey() + " recorded for this game (versions by settings use these): " + b);
        }
    }

    private static Settings.Player player() {
        final SettingsBridge b = bridge;
        return b == null ? null : new Settings.Player() {
            public Object value(Settings.Field f) {
                try {
                    return b.value(f.mod, f.id, f.type);
                } catch (Throwable t) {
                    SagaLog.errorOnce("settings-value:" + f.mod, "LunaLib bridge: reading the settings of " + f.mod + " failed; defaults are used", t);
                    return null;
                }
            }
        };
    }

    private static List<Settings.Applied> applyMod(List<Settings.Field> fields) {
        List<Settings.Applied> done = Settings.apply(GameMemory.global(), fields, player());
        for (Settings.Applied a : done) last.put(a.field.id, a);
        return done;
    }

    /** LunaLib's menu saved a mod's settings: applied at once while a campaign runs (else on the next load). */
    private static void onChanged(String modId) {
        try {
            List<Settings.Field> fields = byMod().get(modId);
            if (fields == null) return;
            if (Global.getSector() == null || Global.getCurrentState() != GameState.CAMPAIGN) return;
            synchronized (ModSettings.class) {
                StringBuilder b = new StringBuilder();
                for (Settings.Applied a : applyMod(fields)) {
                    if (!a.changed()) continue;
                    b.append(b.length() == 0 ? "" : ", ").append(a.field.id).append(' ')
                            .append(a.before == null ? "unset" : Values.str(a.before)).append(" -> ").append(Values.str(a.value));
                }
                SagaLog.info("settings of " + modId + " saved in LunaLib's menu: " + (b.length() == 0 ? "no value changed" : b));
            }
        } catch (Throwable t) {
            SagaLog.errorOnce("settings-changed", "applying the changed settings of " + modId + " failed; they apply on the next load", t);
        }
    }

    private static SettingsBridge load() {
        if (!Global.getSettings().getModManager().isModEnabled(LUNALIB)) {
            status = "LunaLib isn't enabled";
            return null;
        }
        try {
            Class<?> c = Global.getSettings().getScriptClassLoader().loadClass(BRIDGE);
            SettingsBridge b = (SettingsBridge) c.newInstance();
            status = "";
            SagaLog.info("LunaLib bridge loaded: mod settings use the player's values from LunaLib's menu");
            return b;
        } catch (ClassNotFoundException e) {
            status = "this SagaLib was built without its LunaLib bridge";
            SagaLog.warn("LunaLib is enabled, but this SagaLib was built without its LunaLib bridge: mod settings use their defaults");
        } catch (Throwable t) {
            status = "the bridge failed to load (see starsector.log)";
            SagaLog.error("LunaLib bridge failed to load (a LunaLib version SagaLib doesn't know?): mod settings use their defaults", t);
        }
        return null;
    }
}
