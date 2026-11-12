package lastai.sagalib.test;

import java.util.Objects;

/** Tiny assertions. */
final class T {
    static int checks;

    private T() {}

    static void eq(Object expected, Object actual, String what) {
        checks++;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    static void yes(boolean cond, String what) {
        checks++;
        if (!cond) throw new AssertionError(what);
    }

    static void no(boolean cond, String what) {
        yes(!cond, what);
    }
}
