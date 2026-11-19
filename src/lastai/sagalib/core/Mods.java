package lastai.sagalib.core;

import lastai.sagalib.SagaLog;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SagaLib 1.3 (Saga Forge PLAN.md §10.1, §10.2): each content mod's list of other mods
 * (data/sagaforge/mods.json) and the record, kept in the save, of which of its optional and
 * incompatible mods were enabled when the save first saw the mod.
 * <p>
 * A mod <b>counts</b> for a content mod when it was enabled when the game started (the record)
 * <b>and</b> is still enabled. A mod added to a running game changes nothing; a removed one stops
 * what depends on it. Mods the record doesn't know (not listed as optional or incompatible) count
 * when they are enabled.
 * <p>
 * Memory keys (saved): {@code $global.sagaforge_mod_<owner>_<mod>} = "on" | "off" (the start
 * record) and {@code $global.sagaforge_modtold_<owner>_<mod>} = the change the player was told
 * about ("added", "removed", "incompatible"; unset when there is nothing to tell).
 */
public final class Mods {
    private Mods() {}

    /** Whether a mod is enabled now (the game's ModManager; a set in the tests). */
    public interface Enabled {
        boolean isEnabled(String modId);
    }

    /** One entry of a mod's list. */
    public static final class Entry {
        public final String id;
        public final String name;
        /** required, optional or incompatible. */
        public final String use;
        public final String reason;

        public Entry(String id, String name, String use, String reason) {
            this.id = id;
            this.name = name == null || name.isEmpty() ? id : name;
            this.use = use;
            this.reason = reason == null ? "" : reason;
        }

        public boolean recorded() { return "optional".equals(use) || "incompatible".equals(use); }
    }

    /** One content mod's data/sagaforge/mods.json. */
    public static final class ModList {
        public final String owner;
        public final String name;
        /** The mod's id prefix ("vgc_"): its rule ids start with it ({@link #ownerOfRule}). */
        public final String prefix;
        public final List<Entry> entries = new ArrayList<Entry>();
        public String source;

        public ModList(String owner, String name, String prefix) {
            this.owner = owner;
            this.name = name == null || name.isEmpty() ? owner : name;
            this.prefix = prefix == null ? "" : prefix;
        }

        public Entry entry(String id) {
            for (Entry e : entries) if (e.id.equals(id)) return e;
            return null;
        }

        public static ModList parse(JSONObject o, JsonReader r) {
            if (!r.checkFormat(o)) return null;
            String owner = r.str(o, "mod", "$", false, r.mod);
            if (owner == null || owner.isEmpty()) {
                r.error("$.mod", "the mod's id is missing");
                return null;
            }
            ModList l = new ModList(owner, r.str(o, "name", "$", false, owner), r.str(o, "prefix", "$", false, ""));
            l.source = r.file;
            for (JsonReader.Indexed e : r.objects(o, "mods", "$")) {
                String id = r.str(e.json, "id", e.path, true, null);
                String use = r.str(e.json, "use", e.path, true, null);
                if (id == null || use == null) continue;
                if (!"required".equals(use) && !"optional".equals(use) && !"incompatible".equals(use)) {
                    r.error(JsonReader.at(e.path, "use"), "unknown use \"" + use + "\" (required, optional or incompatible)");
                    continue;
                }
                l.entries.add(new Entry(id, r.str(e.json, "name", e.path, false, id), use, r.str(e.json, "reason", e.path, false, "")));
            }
            return l;
        }
    }

    /** A difference between the enabled mods and a save's record (logged on every load; shown once per save). */
    public static final class Notice {
        public final String owner;
        public final String mod;
        /** added, removed or incompatible. */
        public final String what;
        public final String text;
        /** Not told in this save before (show it in game). */
        public final boolean fresh;

        Notice(String owner, String mod, String what, String text, boolean fresh) {
            this.owner = owner;
            this.mod = mod;
            this.what = what;
            this.text = text;
            this.fresh = fresh;
        }

        @Override
        public String toString() { return owner + ":" + mod + ":" + what; }
    }

    private static volatile Enabled enabled = new Enabled() {
        public boolean isEnabled(String modId) { return false; }
    };
    private static volatile Map<String, ModList> lists = Collections.emptyMap();
    /** What was skipped in this game and why (for the dev menu), in the order it happened. */
    private static final Map<String, String> SKIPPED = new LinkedHashMap<String, String>();

    /** The game (or a test) says which mods are enabled and what the content mods list. */
    public static void setup(Enabled e, Map<String, ModList> l) {
        enabled = e;
        lists = l == null ? Collections.<String, ModList>emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<String, ModList>(l));
        synchronized (SKIPPED) { SKIPPED.clear(); }
    }

    public static Map<String, ModList> lists() { return lists; }

    public static boolean enabledNow(String mod) { return enabled.isEnabled(mod); }

    public static String recordKey(String owner, String mod) { return "$global.sagaforge_mod_" + owner + "_" + mod; }

    public static String toldKey(String owner, String mod) { return "$global.sagaforge_modtold_" + owner + "_" + mod; }

    /**
     * Records, for every content mod, the state of each listed optional or incompatible mod it
     * hasn't recorded yet (at a new game, when the mod is added to a running game, or when an update
     * of it lists a new mod). Then compares the record with the enabled mods: the differences are
     * returned (fresh = not told in this save before; with {@code tell} marked as told).
     */
    public static List<Notice> record(Memory m, boolean tell) {
        List<Notice> out = new ArrayList<Notice>();
        for (ModList l : lists.values()) {
            for (Entry e : l.entries) {
                if (!e.recorded()) continue;
                String k = recordKey(l.owner, e.id);
                boolean now = enabled.isEnabled(e.id);
                if (m.get(k) == null) m.set(k, now ? "on" : "off");
                String what;
                if ("incompatible".equals(e.use)) what = now ? "incompatible" : null;
                else {
                    boolean atStart = "on".equals(m.get(k));
                    what = atStart && !now ? "removed" : !atStart && now ? "added" : null;
                }
                String tk = toldKey(l.owner, e.id);
                if (what == null) {
                    if (tell) m.unset(tk);
                    continue;
                }
                boolean fresh = !what.equals(m.get(tk));
                if (tell) m.set(tk, what);
                out.add(new Notice(l.owner, e.id, what, text(l, e, what), fresh));
            }
        }
        return out;
    }

    static String text(ModList l, Entry e, String what) {
        if ("incompatible".equals(what)) {
            return l.name + ": the author marks " + e.name + " as possibly conflicting" + (e.reason.isEmpty() ? "." : ": " + e.reason);
        }
        if ("added".equals(what)) {
            return l.name + " was set up for the mods this game started with. " + e.name + " was added since, so " + l.name
                    + " plays on without its " + e.name + " content. Start a new game to get it.";
        }
        return e.name + " was removed after this game started. The author of " + l.name + " didn't plan for that, so things may break.";
    }

    /** The content mod whose rule this is (the longest matching prefix), or null. */
    public static String ownerOfRule(String ruleId) {
        if (ruleId == null) return null;
        String best = null;
        int len = 0;
        for (ModList l : lists.values()) {
            if (!l.prefix.isEmpty() && ruleId.startsWith(l.prefix) && l.prefix.length() > len) {
                best = l.owner;
                len = l.prefix.length();
            }
        }
        return best;
    }

    /**
     * Whether {@code mod} counts for {@code owner}'s content: enabled now, and not recorded as
     * off when the game started. Without an owner, every content mod that records it must say on.
     */
    public static boolean counts(Memory m, String owner, String mod) {
        if (!enabled.isEnabled(mod)) return false;
        if (owner != null && lists.containsKey(owner)) return !"off".equals(m.get(recordKey(owner, mod)));
        for (ModList l : lists.values()) {
            Entry e = l.entry(mod);
            if (e != null && e.recorded() && "off".equals(m.get(recordKey(l.owner, mod)))) return false;
        }
        return true;
    }

    /** SF_HasMod: every one counts. */
    public static boolean hasAll(Memory m, String owner, List<String> ids) {
        for (String id : ids) if (!counts(m, owner, id)) return false;
        return true;
    }

    /** SF_HasAnyMod: at least one counts. */
    public static boolean hasAny(Memory m, String owner, List<String> ids) {
        for (String id : ids) if (counts(m, owner, id)) return true;
        return false;
    }

    /** null if the item's conditions hold; else why not ("needs X", "not with Y"). */
    public static String blocked(Memory m, ModGate g) {
        if (g == null || g.isEmpty()) return null;
        for (String id : g.with) if (!counts(m, g.owner, id)) return "needs " + id + why(m, g.owner, id);
        for (String id : g.without) if (counts(m, g.owner, id)) return "only without " + id;
        for (List<String> group : g.any) if (!hasAny(m, g.owner, group)) return "needs one of " + ModGate.join(group);
        return null;
    }

    private static String why(Memory m, String owner, String id) {
        if (!enabled.isEnabled(id)) return " (not enabled)";
        return " (not enabled when this game started)";
    }

    /**
     * True if the item may be used. If not, logs one info line the first time (per game) and
     * remembers it for the dev menu.
     */
    public static boolean allowed(Memory m, ModGate g, String kind, String id) {
        String why = blocked(m, g);
        if (why == null) return true;
        skip(kind, id, why, g.owner);
        return false;
    }

    /** SagaLib 1.3: records that an item is skipped and why (dev menu), with one info line the first time per game. */
    public static void skip(String kind, String id, String why, String owner) {
        String key = kind + " " + id;
        boolean first;
        synchronized (SKIPPED) {
            first = !SKIPPED.containsKey(key);
            if (first) SKIPPED.put(key, why);
        }
        if (first) SagaLog.info("skipped " + key + (owner != null ? " of " + owner : "") + ": " + why);
    }

    /** What was skipped and why ("quest vgc_x" → "needs HMI (not enabled)"). */
    public static Map<String, String> skipped() {
        synchronized (SKIPPED) { return new LinkedHashMap<String, String>(SKIPPED); }
    }

    /** Forget what was skipped (a new game or another save: the record may differ). */
    public static void resetSkipped() {
        synchronized (SKIPPED) { SKIPPED.clear(); }
    }
}
