package lastai.sagalib.core;

import lastai.sagalib.SagaLog;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SagaLib 1.4 (Saga Forge #67 U12; docs/FORMAT.md, "Quiet ops"): a world event's start / end
 * actions and a progress stage's or outcome's actions as an op list SagaLib runs itself, without a
 * dialog. Saga Forge writes the list when every action can run that way, and then writes no rule
 * for it. This class reads the list (at load), and runs it: memory ops here, everything else
 * through a {@link Host} (the game's: game.QuietRunner; the tests' own).
 * <p>
 * Keys are rules.csv keys ($global.x, $player.x, $faction.x); a key without a memory prefix ($x) is a
 * local key, which the game side maps to the player fleet's memory (the target of the dialog these
 * actions used to run in).
 */
public final class QuietOps {
    private QuietOps() {}

    public static final List<String> OPS = Collections.unmodifiableList(Arrays.asList(
            "set", "unset", "credits", "item", "rep", "spawn", "despawn", "placeEntity", "removeEntity",
            "startQuest", "storyPoints", "xp", "progress", "plugin"));

    /** Memory prefixes that mean something outside a dialog (anything else is a local key). */
    public static final List<String> SCOPES = Collections.unmodifiableList(Arrays.asList("global", "player", "faction"));

    /** One op, read. Unused fields stay at their defaults. */
    public static final class Op {
        public String op;
        public String path;
        /** set / unset */
        public String key;
        /** set: as written (a rules.csv literal) */
        public String value;
        /** set: expire after this many days (null = never) */
        public Float days;
        /** credits, rep (points: 5 = +0.05), storyPoints, xp */
        public double amount;
        /** item */
        public String id;
        /** item: commodity or special */
        public String kind;
        public int qty;
        /** rep: one of the two */
        public String faction;
        public String person;
        public String encounter;
        public String entity;
        public boolean decivilize;
        /** startQuest; stage optional */
        public String quest;
        public String stage;
        /** progress */
        public String event;
        public int points;
        public String text;
        /** plugin */
        public String className;
        public final Map<String, String> args = new LinkedHashMap<String, String>();

        @Override
        public String toString() {
            if ("set".equals(op)) return "set " + key + " = " + value + (days != null ? " (" + Values.str(days) + " days)" : "");
            if ("unset".equals(op)) return "unset " + key;
            if ("credits".equals(op) || "storyPoints".equals(op) || "xp".equals(op)) return op + " " + Values.str(amount);
            if ("item".equals(op)) return "item " + id + " x" + qty;
            if ("rep".equals(op)) return "rep " + (person != null ? "person " + person : faction) + " " + Values.str(amount);
            if ("spawn".equals(op) || "despawn".equals(op)) return op + " " + encounter;
            if ("placeEntity".equals(op)) return "placeEntity " + entity;
            if ("removeEntity".equals(op)) return "removeEntity " + entity + (decivilize ? " decivilize" : "");
            if ("startQuest".equals(op)) return "startQuest " + quest + (stage != null ? " at " + stage : "");
            if ("progress".equals(op)) return "progress " + event + " " + points;
            if ("plugin".equals(op)) return "plugin " + className + " " + args;
            return op;
        }
    }

    /** What the ops do outside memory. Each call may throw: that op is logged, the others still run. */
    public interface Host {
        void credits(long amount);

        /** {@code kind} as written (commodity / special), may be null. */
        void item(String id, String kind, int qty);

        void factionRep(String faction, double points);

        void personRep(String person, double points);

        void storyPoints(int points);

        void xp(long xp);

        void spawn(String encounter);

        void despawn(String encounter);

        void placeEntity(String entity);

        void removeEntity(String entity, boolean decivilize);

        /** SF_Start, then SF_Step at the stage (null = none). */
        void startQuest(String quest, String stage);

        void progress(String event, int points, String text);

        /** The plugin's arguments, $-values already read from memory. */
        void plugin(String className, Map<String, String> args);
    }

    /** The memory a key is in: global, player or faction; "local" for anything else (the player fleet's memory). */
    public static String scope(String key) {
        if (key == null || !key.startsWith("$")) return "local";
        int dot = key.indexOf('.');
        if (dot > 1 && dot < key.length() - 1) {
            String p = key.substring(1, dot);
            if (SCOPES.contains(p)) return p;
        }
        return "local";
    }

    /** A usable key: starts with $, a name after any prefix, no spaces. */
    static boolean usableKey(String key) {
        if (key == null || key.length() < 2 || !key.startsWith("$") || key.contains(" ")) return false;
        return !key.endsWith(".");
    }

    /**
     * The list under {@code key} of {@code o}, or null when the key is missing (then the action is a rule,
     * as before). A bad op is logged and left out; the others are kept.
     */
    public static List<Op> parse(JSONObject o, String key, String path, JsonReader r) {
        if (o == null || !o.has(key) || o.isNull(key)) return null;
        String at = JsonReader.at(path, key);
        Object v = o.opt(key);
        if (!(v instanceof JSONArray)) {
            r.error(at, "should be a list of ops; the action is left out");
            return new ArrayList<Op>();
        }
        JSONArray a = (JSONArray) v;
        List<Op> out = new ArrayList<Op>();
        for (int i = 0; i < a.length(); i++) {
            String p = JsonReader.at(at, i);
            JSONObject x = a.optJSONObject(i);
            if (x == null) {
                r.error(p, "an op should be an object; skipped");
                continue;
            }
            Op op = parseOp(x, p, r);
            if (op != null) out.add(op);
        }
        return out;
    }

    static Op parseOp(JSONObject x, String p, JsonReader r) {
        Op op = new Op();
        op.path = p;
        op.op = r.str(x, "op", p, true, null);
        if (op.op == null) return null;
        if (!OPS.contains(op.op)) {
            r.error(JsonReader.at(p, "op"), "unknown op \"" + op.op + "\" " + OPS + "; skipped");
            return null;
        }
        String o = op.op;
        if ("set".equals(o) || "unset".equals(o)) {
            op.key = r.str(x, "key", p, true, null);
            if (op.key == null) return null;
            op.key = op.key.trim();
            if (!usableKey(op.key)) {
                r.error(JsonReader.at(p, "key"), "\"" + op.key + "\" isn't a memory key ($global.x, $player.x, $faction.x or $x); skipped");
                return null;
            }
            String pre = op.key.indexOf('.') > 1 ? op.key.substring(1, op.key.indexOf('.')) : null;
            if (pre != null && "local".equals(scope(op.key)) && Arrays.asList("entity", "market", "person", "source", "sector").contains(pre)) {
                r.warn(JsonReader.at(p, "key"), "\"" + op.key + "\": $" + pre + ". means nothing outside a dialog; it is a key of the player fleet's memory here");
            }
            if ("set".equals(o)) {
                op.value = r.str(x, "value", p, false, "true");
                Double d = r.num(x, "days", p, null);
                op.days = d == null ? null : Float.valueOf(d.floatValue());
            }
        } else if ("credits".equals(o) || "storyPoints".equals(o) || "xp".equals(o)) {
            Double d = r.num(x, "amount", p, null);
            if (d == null) { r.error(JsonReader.at(p, "amount"), "missing (a number is required); skipped"); return null; }
            op.amount = d;
        } else if ("item".equals(o)) {
            op.id = r.str(x, "id", p, true, null);
            if (op.id == null) return null;
            op.kind = r.str(x, "kind", p, false, "commodity");
            if (!"commodity".equals(op.kind) && !"special".equals(op.kind)) {
                r.warn(JsonReader.at(p, "kind"), "unknown kind \"" + op.kind + "\" (commodity or special); the item's own kind is used");
                op.kind = null;
            }
            Double q = r.num(x, "qty", p, 1d);
            op.qty = q == null ? 1 : (int) Math.round(q);
        } else if ("rep".equals(o)) {
            op.faction = emptyToNull(r.str(x, "faction", p, false, ""));
            op.person = emptyToNull(r.str(x, "person", p, false, ""));
            if ((op.faction == null) == (op.person == null)) {
                r.error(p, "a rep op needs \"faction\" or \"person\" (one of them); skipped");
                return null;
            }
            if ("$person".equals(op.person)) {
                r.error(JsonReader.at(p, "person"), "\"$person\" (the person talked to) needs a dialog; skipped");
                return null;
            }
            Double d = r.num(x, "amount", p, null);
            if (d == null) { r.error(JsonReader.at(p, "amount"), "missing (a number is required); skipped"); return null; }
            op.amount = d;
        } else if ("spawn".equals(o) || "despawn".equals(o)) {
            op.encounter = r.str(x, "encounter", p, true, null);
            if (op.encounter == null) return null;
        } else if ("placeEntity".equals(o) || "removeEntity".equals(o)) {
            op.entity = r.str(x, "entity", p, true, null);
            if (op.entity == null) return null;
            Boolean b = r.bool(x, "decivilize", p, false);
            op.decivilize = b != null && b;
        } else if ("startQuest".equals(o)) {
            op.quest = r.str(x, "quest", p, true, null);
            if (op.quest == null) return null;
            op.stage = emptyToNull(r.str(x, "stage", p, false, ""));
        } else if ("progress".equals(o)) {
            op.event = r.str(x, "event", p, true, null);
            if (op.event == null) return null;
            Double d = r.num(x, "points", p, null);
            if (d == null) { r.error(JsonReader.at(p, "points"), "missing (a number is required); skipped"); return null; }
            op.points = (int) Math.round(d);
            op.text = r.str(x, "text", p, false, "");
        } else if ("plugin".equals(o)) {
            op.className = r.str(x, "className", p, true, null);
            if (op.className == null || op.className.trim().isEmpty()) {
                if (op.className != null) r.error(JsonReader.at(p, "className"), "empty; skipped");
                return null;
            }
            op.className = op.className.trim();
            JSONObject a = r.obj(x, "args", p, false);
            if (a != null) {
                for (Iterator<?> it = a.keys(); it.hasNext(); ) {
                    String k = String.valueOf(it.next());
                    if (k.trim().isEmpty()) continue;
                    Object val = a.opt(k);
                    op.args.put(k.trim(), val == null || val == JSONObject.NULL ? "" : String.valueOf(val));
                }
            }
        }
        return op;
    }

    /** A set op's value as stored: a rules.csv literal (true / false / number / text), or another key's value ("$x"). */
    public static Object value(Memory mem, String written) {
        if (written == null) return Boolean.TRUE;
        String t = written.trim();
        if (t.startsWith("$") && !t.contains(" ")) return mem.get(t);
        return Values.literal(t);
    }

    /** A plugin's arguments as SF_RunPlugin passes them: a value starting with $ read from memory (missing = ""). */
    public static Map<String, String> pluginArgs(Memory mem, Map<String, String> args) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : args.entrySet()) {
            if (Plugins.QUEST.equals(e.getKey()) || Plugins.NODE.equals(e.getKey())) continue;
            out.put(e.getKey(), Plugins.arg(e.getValue(), mem));
        }
        return out;
    }

    /**
     * Runs the ops in order. A failing op logs an error naming {@code where} and the op; the others
     * still run. Returns how many ran without an exception.
     */
    public static int run(List<Op> ops, Memory mem, Host host, String where) {
        int ok = 0;
        if (ops == null) return 0;
        for (Op op : ops) {
            try {
                apply(op, mem, host);
                ok++;
            } catch (Throwable t) {
                SagaLog.error(where + ": op " + op + (op.path != null ? " (" + op.path + ")" : "") + " failed", t);
            }
        }
        SagaLog.info(where + ": ran " + ok + " of " + ops.size() + " op(s) without a dialog");
        return ok;
    }

    static void apply(Op op, Memory mem, Host host) {
        String o = op.op;
        if ("set".equals(o)) {
            Object v = value(mem, op.value);
            if (v == null) mem.unset(op.key);
            else if (op.days != null) mem.set(op.key, v, op.days);
            else mem.set(op.key, v);
        } else if ("unset".equals(o)) {
            mem.unset(op.key);
        } else if ("credits".equals(o)) {
            host.credits(Math.round(op.amount));
        } else if ("item".equals(o)) {
            host.item(op.id, op.kind, op.qty);
        } else if ("rep".equals(o)) {
            if (op.person != null) host.personRep(op.person, op.amount);
            else host.factionRep(op.faction, op.amount);
        } else if ("storyPoints".equals(o)) {
            host.storyPoints((int) Math.round(op.amount));
        } else if ("xp".equals(o)) {
            host.xp(Math.round(op.amount));
        } else if ("spawn".equals(o)) {
            host.spawn(op.encounter);
        } else if ("despawn".equals(o)) {
            host.despawn(op.encounter);
        } else if ("placeEntity".equals(o)) {
            host.placeEntity(op.entity);
        } else if ("removeEntity".equals(o)) {
            host.removeEntity(op.entity, op.decivilize);
        } else if ("startQuest".equals(o)) {
            host.startQuest(op.quest, op.stage);
        } else if ("progress".equals(o)) {
            host.progress(op.event, op.points, op.text == null ? "" : op.text);
        } else if ("plugin".equals(o)) {
            host.plugin(op.className, pluginArgs(mem, op.args));
        } else {
            throw new IllegalArgumentException("unknown op " + o);
        }
    }

    static String emptyToNull(String s) { return s == null || s.trim().isEmpty() ? null : s.trim(); }
}
