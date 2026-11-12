package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import lastai.sagalib.core.Memory;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link Memory} over the game's memory map, resolving keys as the rules engine does
 * (Misc.Token.getVarNameAndMemory): "$global.x" is "$x" in the "global" memory; a key whose
 * prefix isn't in the map is a local key.
 */
public final class GameMemory implements Memory {
    private final Map<String, MemoryAPI> map;

    public GameMemory(Map<String, MemoryAPI> memoryMap) {
        this.map = memoryMap != null ? memoryMap : defaultMap();
    }

    /** Sector and player memory, for code outside a dialog (load, intel, dev tools). */
    public static GameMemory global() { return new GameMemory(null); }

    /** global + player, plus a throwaway local memory (writes to local keys outside a dialog go nowhere). */
    public static Map<String, MemoryAPI> defaultMap() {
        Map<String, MemoryAPI> m = new HashMap<String, MemoryAPI>();
        m.put(MemKeys.GLOBAL, Global.getSector().getMemoryWithoutUpdate());
        m.put(MemKeys.PLAYER, Global.getSector().getCharacterData().getMemoryWithoutUpdate());
        m.put(MemKeys.LOCAL, Global.getFactory().createMemory());
        return m;
    }

    public Map<String, MemoryAPI> map() { return map; }

    private static final class Ref {
        final MemoryAPI memory;
        final String name;

        Ref(MemoryAPI memory, String name) {
            this.memory = memory;
            this.name = name;
        }
    }

    private Ref ref(String key) {
        int dot = key.indexOf('.');
        if (key.startsWith("$") && dot > 1 && dot < key.length() - 1) {
            MemoryAPI m = map.get(key.substring(1, dot));
            if (m != null) return new Ref(m, "$" + key.substring(dot + 1));
        }
        return new Ref(map.get(MemKeys.LOCAL), key);
    }

    @Override
    public Object get(String key) {
        Ref r = ref(key);
        if (r.memory == null || !r.memory.contains(r.name)) return null;
        return r.memory.get(r.name);
    }

    @Override
    public void set(String key, Object value) {
        Ref r = ref(key);
        if (r.memory == null) return;
        if (value == null) r.memory.unset(r.name);
        else r.memory.set(r.name, value);
    }

    @Override
    public void unset(String key) {
        Ref r = ref(key);
        if (r.memory != null) r.memory.unset(r.name);
    }
}
