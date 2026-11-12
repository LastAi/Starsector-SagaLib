package lastai.sagalib.core;

import java.util.regex.Pattern;

/**
 * How memory values read, compare and print. The same rules as Saga Forge's rules engine
 * (tools/saga-forge/src/shared/rules/engine.ts: literal, truthy, compare), so phrases and
 * conditions give the same answers in the editor and in game.
 */
public final class Values {
    private Values() {}

    private static final Pattern LITERAL_NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    /** Roughly what JavaScript's Number() accepts for the strings memory holds. */
    private static final Pattern ANY_NUMBER = Pattern.compile("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");

    /** A literal from a rule line: "true"/"false", a number (as Float), else the string without quotes. */
    public static Object literal(String s) {
        String t = s.trim();
        if (t.length() >= 1 && t.startsWith("\"")) t = t.substring(1);
        if (t.length() >= 1 && t.endsWith("\"")) t = t.substring(0, t.length() - 1);
        if (t.equals("true")) return Boolean.TRUE;
        if (t.equals("false")) return Boolean.FALSE;
        if (!t.isEmpty() && LITERAL_NUMBER.matcher(t).matches()) return Float.valueOf(Float.parseFloat(t));
        return t;
    }

    /** Set and not false, 0, "" or "false". */
    public static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).doubleValue() != 0;
        if (v instanceof String) return !((String) v).isEmpty() && !"false".equals(v);
        return true;
    }

    /** The number a value stands for, or null. */
    public static Double number(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (!s.isEmpty() && ANY_NUMBER.matcher(s).matches()) return Double.parseDouble(s);
        }
        return null;
    }

    /** Text of a value as JavaScript's String() writes it: 2.0 is "2", null is "undefined". */
    public static String str(Object v) {
        if (v == null) return "undefined";
        if (v instanceof Float || v instanceof Double) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
            // Floats print their shortest form ("1.5", not 1.5000000xx)
            return v instanceof Float ? Float.toString((Float) v) : Double.toString(d);
        }
        return String.valueOf(v);
    }

    private static String strOrEmpty(Object v) { return v == null ? "" : str(v); }

    private static boolean looseEquals(Object a, Object b) {
        return strOrEmpty(a).equals(strOrEmpty(b)) || (a == null && (Boolean.FALSE.equals(b) || "false".equals(b)));
    }

    /** Compares two values with ==, !=, &gt;, &gt;=, &lt;, &lt;=: as numbers when both are numbers, else as text. */
    public static boolean compare(Object a, String op, Object b) {
        Double na = number(a), nb = number(b);
        boolean numeric = na != null && nb != null;
        if ("==".equals(op)) return numeric ? na.doubleValue() == nb.doubleValue() : looseEquals(a, b);
        if ("!=".equals(op)) return numeric ? na.doubleValue() != nb.doubleValue() : !looseEquals(a, b);
        if (">".equals(op)) return numeric && na > nb;
        if (">=".equals(op)) return numeric && na >= nb;
        if ("<".equals(op)) return numeric && na < nb;
        if ("<=".equals(op)) return numeric && na <= nb;
        return false;
    }
}
