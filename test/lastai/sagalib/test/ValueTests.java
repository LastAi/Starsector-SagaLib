package lastai.sagalib.test;

import lastai.sagalib.core.Values;

/** Values: the literal/truthy/compare/print rules shared with Saga Forge's engine. */
public final class ValueTests {
    public static void literals() {
        T.eq(Boolean.TRUE, Values.literal("true"), "true");
        T.eq(Boolean.FALSE, Values.literal(" false "), "false");
        T.eq(2f, Values.literal("2"), "number");
        T.eq(-1.5f, Values.literal("-1.5"), "negative");
        T.eq("abc", Values.literal("\"abc\""), "quotes removed");
        T.eq("1e3", Values.literal("1e3"), "only plain numbers are numbers");
        T.eq("", Values.literal(""), "empty");
    }

    public static void truthy() {
        T.no(Values.truthy(null), "unset");
        T.no(Values.truthy(false), "false");
        T.no(Values.truthy(0f), "0");
        T.no(Values.truthy(""), "empty");
        T.no(Values.truthy("false"), "\"false\"");
        T.yes(Values.truthy("x"), "text");
        T.yes(Values.truthy(2f), "2");
        T.yes(Values.truthy(true), "true");
    }

    public static void printing() {
        T.eq("1", Values.str(1f), "1.0 prints as 1");
        T.eq("1.5", Values.str(1.5f), "1.5");
        T.eq("true", Values.str(true), "bool");
        T.eq("undefined", Values.str(null), "unset prints as JS does");
        T.eq("abc", Values.str("abc"), "text");
    }

    public static void comparing() {
        T.yes(Values.compare(2f, "==", "2"), "number vs numeric text");
        T.yes(Values.compare("2.0", "==", 2f), "2.0 == 2");
        T.yes(Values.compare("abc", "==", "abc"), "text");
        T.no(Values.compare("abc", "==", "abd"), "text differs");
        T.yes(Values.compare(null, "==", false), "unset == false");
        T.yes(Values.compare(null, "==", "false"), "unset == \"false\"");
        T.yes(Values.compare(null, "!=", "x"), "unset != x");
        T.yes(Values.compare(true, "==", "true"), "bool == \"true\"");
        T.yes(Values.compare(5f, ">", 3f), ">");
        T.no(Values.compare("a", ">", 3f), "> needs numbers");
        T.yes(Values.compare(3f, ">=", "3"), ">=");
        T.yes(Values.compare(-1f, "<", 0f), "<");
        T.yes(Values.compare(0f, "<=", 0f), "<=");
        T.no(Values.compare(1f, "~", 1f), "unknown operator");
    }
}
