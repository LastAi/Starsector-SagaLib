package lastai.sagalib.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads typed fields of one file and records a problem (file + JSON path) for each one that is
 * missing or of the wrong type. Readers return null when a value is unusable, so the caller can
 * skip that object and go on with the rest of the file.
 */
public final class JsonReader {
    public final String file;
    public final Problems problems;

    public JsonReader(String file, Problems problems) {
        this.file = file;
        this.problems = problems;
    }

    public void error(String path, String message) { problems.error(file, path, message); }

    public void warn(String path, String message) { problems.warn(file, path, message); }

    public static String at(String path, String key) { return path + "." + key; }

    public static String at(String path, int index) { return path + "[" + index + "]"; }

    private static boolean missing(JSONObject o, String key) { return !o.has(key) || o.isNull(key); }

    /** A string; numbers and booleans are accepted as their text. Missing: {@code def}, or an error if required. */
    public String str(JSONObject o, String key, String path, boolean required, String def) {
        if (missing(o, key)) {
            if (required) error(at(path, key), "missing (a string is required)");
            return required ? null : def;
        }
        Object v = o.opt(key);
        if (v instanceof String) return (String) v;
        if (v instanceof Number || v instanceof Boolean) return String.valueOf(v);
        error(at(path, key), "should be a string, not " + kind(v));
        return required ? null : def;
    }

    /** A number. Missing: {@code def}. Wrong type: an error and {@code null}. */
    public Double num(JSONObject o, String key, String path, Double def) {
        if (missing(o, key)) return def;
        Object v = o.opt(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            try {
                return Double.parseDouble(((String) v).trim());
            } catch (NumberFormatException ignored) {
                // falls through to the error
            }
        }
        error(at(path, key), "should be a number, not " + kind(v));
        return null;
    }

    public Boolean bool(JSONObject o, String key, String path, boolean def) {
        if (missing(o, key)) return def;
        Object v = o.opt(key);
        if (v instanceof Boolean) return (Boolean) v;
        if ("true".equals(v)) return true;
        if ("false".equals(v)) return false;
        error(at(path, key), "should be true or false, not " + kind(v));
        return null;
    }

    /** An object, or null (missing ones are an error only if required). */
    public JSONObject obj(JSONObject o, String key, String path, boolean required) {
        if (missing(o, key)) {
            if (required) error(at(path, key), "missing (an object is required)");
            return null;
        }
        Object v = o.opt(key);
        if (v instanceof JSONObject) return (JSONObject) v;
        error(at(path, key), "should be an object, not " + kind(v));
        return null;
    }

    /** An array; missing gives an empty one. Wrong type: an error and an empty array. */
    public JSONArray arr(JSONObject o, String key, String path) {
        if (missing(o, key)) return new JSONArray();
        Object v = o.opt(key);
        if (v instanceof JSONArray) return (JSONArray) v;
        error(at(path, key), "should be a list, not " + kind(v));
        return new JSONArray();
    }

    /** A list of strings; bad entries are reported and skipped. */
    public List<String> strings(JSONObject o, String key, String path) {
        List<String> out = new ArrayList<String>();
        JSONArray a = arr(o, key, path);
        for (int i = 0; i < a.length(); i++) {
            Object v = a.opt(i);
            if (v instanceof String) out.add((String) v);
            else if (v instanceof Number || v instanceof Boolean) out.add(String.valueOf(v));
            else error(at(at(path, key), i), "should be a string, not " + kind(v));
        }
        return out;
    }

    /** The objects of a list; entries that aren't objects are reported and skipped (their index is kept in paths). */
    public List<Indexed> objects(JSONObject o, String key, String path) {
        List<Indexed> out = new ArrayList<Indexed>();
        JSONArray a = arr(o, key, path);
        for (int i = 0; i < a.length(); i++) {
            Object v = a.opt(i);
            String p = at(at(path, key), i);
            if (v instanceof JSONObject) out.add(new Indexed((JSONObject) v, p));
            else error(p, "should be an object, not " + kind(v));
        }
        return out;
    }

    public static final class Indexed {
        public final JSONObject json;
        public final String path;

        Indexed(JSONObject json, String path) {
            this.json = json;
            this.path = path;
        }
    }

    static String kind(Object v) {
        if (v == null || v == JSONObject.NULL) return "null";
        if (v instanceof String) return "a string";
        if (v instanceof Number) return "a number";
        if (v instanceof Boolean) return "true/false";
        if (v instanceof JSONArray) return "a list";
        if (v instanceof JSONObject) return "an object";
        return v.getClass().getSimpleName();
    }

    /** Checks "format": every format SagaLib ever supported (only 1 so far). False = skip the file. */
    public boolean checkFormat(JSONObject root) {
        if (!root.has("format")) {
            error("$.format", "missing; SagaLib needs the format number Saga Forge writes (1)");
            return false;
        }
        Object f = root.opt("format");
        if (!(f instanceof Number) || ((Number) f).doubleValue() != 1) {
            error("$.format", "format " + f + " is not supported by this SagaLib (it reads format 1); update SagaLib");
            return false;
        }
        return true;
    }
}
