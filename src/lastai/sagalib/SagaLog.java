package lastai.sagalib;

import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

/** One logger for the library ("[SagaLib]" prefix). Repeated problems can be logged once, so a per-frame failure can't flood the log. */
public final class SagaLog {
    private static final Logger LOG = Logger.getLogger("lastai.sagalib");
    private static final Set<String> ONCE = new HashSet<String>();

    private SagaLog() {}

    public static void info(String msg) { LOG.info("[SagaLib] " + msg); }

    public static void warn(String msg) { LOG.warn("[SagaLib] " + msg); }

    public static void error(String msg) { LOG.error("[SagaLib] " + msg); }

    public static void error(String msg, Throwable t) { LOG.error("[SagaLib] " + msg, t); }

    /** Logs a warning only the first time {@code key} is seen. */
    public static synchronized void warnOnce(String key, String msg) {
        if (ONCE.add(key)) warn(msg);
    }

    /** Logs an error only the first time {@code key} is seen. */
    public static synchronized void errorOnce(String key, String msg, Throwable t) {
        if (ONCE.add(key)) {
            if (t == null) error(msg);
            else error(msg, t);
        }
    }
}
