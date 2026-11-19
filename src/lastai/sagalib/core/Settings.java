package lastai.sagalib.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SagaLib 1.3 (task #62): a content mod's settings (docs/FORMAT.md, "Mod settings (LunaLib)"),
 * read from the mod's own data/config/LunaSettings.csv. Every value field becomes
 * {@code $global.<fieldID>}: its default, or the player's value from LunaLib (game/ModSettings
 * through the optional bridge). Settings can also scale credit rewards and encounter fleet size
 * ({@link Scale}). Pure: no game classes, so the tests and Saga Forge's Play mode agree.
 */
public final class Settings {
    private Settings() {}

    /** LunaLib field types that hold a value (and so get a memory key). */
    public static final List<String> VALUE_TYPES = Collections.unmodifiableList(Arrays.asList("Boolean", "Int", "Double", "Radio", "String"));
    /** Layout only: no value, skipped silently. */
    public static final List<String> LAYOUT_TYPES = Collections.unmodifiableList(Arrays.asList("Header", "Text"));
    /** LunaLib types Saga Forge doesn't make: skipped with a warning. */
    public static final List<String> UNSUPPORTED_TYPES = Collections.unmodifiableList(Arrays.asList("Keycode", "Color"));

    private static final Pattern INT = Pattern.compile("[-+]?\\d+");

    /** One value field of a mod's settings page. */
    public static final class Field {
        public final String mod, id, name, type;
        /** The default, read as the type (Boolean, Float or String). */
        public final Object def;
        /** Radio: the choices (secondaryValue); empty otherwise. */
        public final List<String> options;
        /** Int and Double: minValue / maxValue as written (null = none); LunaLib's menu keeps the player inside them. */
        public final Double min, max;
        public final String tab;

        public Field(String mod, String id, String name, String type, Object def, List<String> options, Double min, Double max, String tab) {
            this.mod = mod;
            this.id = id;
            this.name = name;
            this.type = type;
            this.def = def;
            this.options = options == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<String>(options));
            this.min = min;
            this.max = max;
            this.tab = tab;
        }

        /** The memory key: $global.&lt;fieldID&gt;. */
        public String key() { return "$global." + id; }

        @Override
        public String toString() { return mod + "/" + id + " (" + type + ", default " + Values.str(def) + ")"; }
    }

    /** The player's values (LunaLib's, through the bridge). */
    public interface Player {
        /** The player's raw value for this field (Boolean, Number or String), or null when there is none. */
        Object value(Field f);
    }

    /** What one apply set for a field. */
    public static final class Applied {
        public final Field field;
        public final Object value;
        /** True when the value is the player's, false when it is the default. */
        public final boolean fromPlayer;
        /** The memory value before (null = unset). */
        public final Object before;

        Applied(Field field, Object value, boolean fromPlayer, Object before) {
            this.field = field;
            this.value = value;
            this.fromPlayer = fromPlayer;
            this.before = before;
        }

        public boolean changed() { return before == null || !Values.str(before).equals(Values.str(value)); }
    }

    private static String cell(JSONObject row, String col) {
        Object v = row.opt(col);
        return v == null || JSONObject.NULL.equals(v) ? "" : String.valueOf(v).trim();
    }

    private static Double optNumber(String s) {
        return s.isEmpty() ? null : Values.number(s);
    }

    /**
     * Reads a mod's LunaSettings.csv rows (as the game's loadCSV returns them: every cell a String;
     * a column may be missing) and adds its value fields to {@code into}. Rows without a fieldID are
     * skipped (as LunaLib does); Header and Text silently; Keycode, Color and unknown types with a
     * warning; a default that doesn't read as its type is an error and the field is skipped. A field
     * id already in {@code into} (this mod or another) is a warning: the first one wins.
     */
    public static void read(String mod, JSONArray rows, List<Field> into, Problems problems, String file) {
        for (int i = 0; rows != null && i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String where = "row " + (i + 1);
            String id = cell(row, "fieldID");
            if (id.isEmpty()) continue;
            String type = cell(row, "fieldType");
            if (LAYOUT_TYPES.contains(type)) continue;
            if (UNSUPPORTED_TYPES.contains(type)) {
                problems.warn(file, where, "setting " + id + ": type " + type + " isn't supported by SagaLib (no $global." + id + "); LunaLib still shows it");
                continue;
            }
            if (!VALUE_TYPES.contains(type)) {
                problems.warn(file, where, "setting " + id + ": unknown type \"" + type + "\" (" + VALUE_TYPES + ", Header, Text); skipped");
                continue;
            }
            List<String> options = new ArrayList<String>();
            if ("Radio".equals(type)) {
                for (String o : cell(row, "secondaryValue").split(",")) if (!o.trim().isEmpty()) options.add(o.trim());
            }
            String defText = cell(row, "defaultValue");
            Field probe = new Field(mod, id, cell(row, "fieldName"), type, null, options, null, null, cell(row, "tab"));
            Object def = read(probe, defText, false);
            if (def == null) {
                problems.error(file, where, "setting " + id + ": the default \"" + defText + "\" isn't " + ("Int".equals(type) ? "a whole number" : "Double".equals(type) ? "a number" : "Boolean".equals(type) ? "true or false" : "a value") + "; skipped");
                continue;
            }
            if ("Radio".equals(type) && !options.isEmpty() && !options.contains(def)) {
                problems.warn(file, where, "setting " + id + ": the default \"" + defText + "\" isn't one of its choices " + options);
            }
            Field old = find(into, id);
            if (old != null) {
                problems.warn(file, where, "setting " + id + " is already defined by " + old.mod + "; the first one is used");
                continue;
            }
            into.add(new Field(mod, id, probe.name, type, def, options, optNumber(cell(row, "minValue")), optNumber(cell(row, "maxValue")), probe.tab));
        }
    }

    public static Field find(List<Field> fields, String id) {
        for (Field f : fields) if (f.id.equals(id)) return f;
        return null;
    }

    /**
     * A raw value read as the field's type, or null when it doesn't read: Boolean (true/false, any
     * case), Int (a whole number, as Float), Double (a number, as Float), Radio and String (text; a
     * player's Radio value must be one of the choices).
     */
    public static Object read(Field f, Object raw) { return read(f, raw, true); }

    private static Object read(Field f, Object raw, boolean player) {
        if (raw == null) return null;
        if ("Boolean".equals(f.type)) {
            if (raw instanceof Boolean) return raw;
            String s = String.valueOf(raw).trim();
            if (s.equalsIgnoreCase("true")) return Boolean.TRUE;
            if (s.equalsIgnoreCase("false")) return Boolean.FALSE;
            return null;
        }
        if ("Int".equals(f.type)) {
            Double d;
            if (raw instanceof Number) d = ((Number) raw).doubleValue();
            else {
                String s = String.valueOf(raw).trim();
                d = INT.matcher(s).matches() ? Double.valueOf(Double.parseDouble(s)) : null;
            }
            if (d == null || d.isNaN() || d.isInfinite() || d != Math.rint(d)) return null;
            return Float.valueOf(d.floatValue());
        }
        if ("Double".equals(f.type)) {
            Double d = raw instanceof Number ? Double.valueOf(((Number) raw).doubleValue()) : Values.number(String.valueOf(raw));
            if (d == null || d.isNaN() || d.isInfinite()) return null;
            return Float.valueOf(d.floatValue());
        }
        if ("Radio".equals(f.type)) {
            String s = String.valueOf(raw).trim();
            if (player && !f.options.isEmpty() && !f.options.contains(s)) return null;
            return s;
        }
        if ("String".equals(f.type)) return String.valueOf(raw);
        return null;
    }

    /**
     * Sets every field's $global key: the player's value when {@code player} gives one that reads as
     * the field's type, else the default. Every field is set again each time, so a value the player
     * source no longer has goes back to the default.
     */
    public static List<Applied> apply(Memory mem, List<Field> fields, Player player) {
        List<Applied> out = new ArrayList<Applied>();
        for (Field f : fields) {
            Object v = player == null ? null : read(f, player.value(f));
            boolean fromPlayer = v != null;
            if (v == null) v = f.def;
            Object before = mem.get(f.key());
            mem.set(f.key(), v);
            out.add(new Applied(f, v, fromPlayer, before));
        }
        return out;
    }

    // ------------------------------------------------------------------ scaling by a setting

    /** A multiplier read from a setting: {@code {"setting": id, "per": n, "factors": {"value": n}}}. */
    public static final class Scale {
        public final String setting;
        public final double per;
        /** Value text → factor; null when the scale divides by {@link #per} instead. */
        public final Map<String, Double> factors;

        public Scale(String setting, double per, Map<String, Double> factors) {
            this.setting = setting;
            this.per = per;
            this.factors = factors == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, Double>(factors));
        }

        /** Reads {@code o[key]}; null when it is missing or unusable (reported). */
        public static Scale parse(JSONObject o, String key, String path, JsonReader r) {
            JSONObject s = r.obj(o, key, path, false);
            if (s == null) return null;
            String p = JsonReader.at(path, key);
            String setting = r.str(s, "setting", p, true, null);
            if (setting == null || setting.trim().isEmpty()) {
                if (setting != null) r.error(JsonReader.at(p, "setting"), "empty (a setting id is required)");
                return null;
            }
            Double per = r.num(s, "per", p, 1d);
            if (per == null || per == 0 || per.isNaN() || per.isInfinite()) {
                if (per != null) r.warn(JsonReader.at(p, "per"), "should be a number other than 0; 1 is used");
                per = 1d;
            }
            Map<String, Double> factors = null;
            JSONObject fo = r.obj(s, "factors", p, false);
            if (fo != null) {
                factors = new LinkedHashMap<String, Double>();
                for (Iterator<?> it = fo.keys(); it.hasNext(); ) {
                    String k = String.valueOf(it.next());
                    Double n = r.num(fo, k, JsonReader.at(p, "factors"), null);
                    if (n != null) factors.put(k, n);
                }
            }
            return new Scale(setting.trim(), per, factors);
        }
    }

    /**
     * The factor of a scale now: the setting's value in memory ($global.&lt;setting&gt;; unset = 1).
     * With factors: the value's text (true/false, a number without ".0", the text) → its factor,
     * missing = 1. Without: a number divided by per, anything else 1. NaN or infinite = 1; then
     * clamped to 0..100.
     */
    public static double factor(Scale s, Memory mem) {
        if (s == null || mem == null) return 1;
        Object v = mem.get("$global." + s.setting);
        if (v == null) return 1;
        double f;
        if (s.factors != null) {
            Double d = s.factors.get(Values.str(v));
            f = d == null ? 1 : d;
        } else {
            Double n = v instanceof Boolean ? null : Values.number(v);
            f = n == null ? 1 : n / s.per;
        }
        if (Double.isNaN(f) || Double.isInfinite(f)) return 1;
        return Math.max(0, Math.min(100, f));
    }

    /** Credits scaled: rounded half up. */
    public static long credits(long credits, Scale s, Memory mem) {
        if (s == null) return credits;
        return Math.round(credits * factor(s, mem));
    }

    /** Fleet points scaled; at least 1 when the base points are above 0. */
    public static float points(float points, Scale s, Memory mem) {
        if (s == null) return points;
        float p = (float) (points * factor(s, mem));
        return points > 0 && p < 1 ? 1f : p;
    }
}
