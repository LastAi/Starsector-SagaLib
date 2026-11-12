package lastai.sagalib.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Triggers SagaLib fires outside a dialog (encounter outcomes, quest timers): a queue kept in one
 * memory key ($global.sagaforge_pending, one trigger per line, so it survives a save), and the
 * rules for the dialog that runs them (docs/FORMAT.md, end of "Encounters").
 */
public final class Triggers {
    private Triggers() {}

    public static final String QUEUE_KEY = "$global.sagaforge_pending";

    /** What to do with the dialog. */
    public enum After {
        /** No rule matches the trigger: open no dialog. */
        NO_DIALOG,
        /** Nothing was added (no text, no options): close it at once. */
        DISMISS,
        /** Text but no options: one "Continue" option that closes it. */
        CONTINUE,
        /** The rules added options (or closed the dialog themselves): leave it as it is. */
        KEEP
    }

    public static After before(boolean ruleMatches) { return ruleMatches ? After.KEEP : After.NO_DIALOG; }

    public static After after(boolean hasOptions, boolean addedText, boolean alreadyClosed) {
        if (alreadyClosed || hasOptions) return After.KEEP;
        return addedText ? After.CONTINUE : After.DISMISS;
    }

    public static List<String> queued(Memory m) {
        List<String> out = new ArrayList<String>();
        Object v = m.get(QUEUE_KEY);
        if (v == null) return out;
        for (String s : Values.str(v).split("\n")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    public static void add(Memory m, String trigger) {
        if (trigger == null || trigger.trim().isEmpty()) return;
        List<String> q = queued(m);
        q.add(trigger.trim());
        store(m, q);
    }

    /** Takes the first queued trigger, or null. */
    public static String poll(Memory m) {
        List<String> q = queued(m);
        if (q.isEmpty()) return null;
        String first = q.remove(0);
        store(m, q);
        return first;
    }

    /** Puts a trigger back at the front (its dialog couldn't open yet). */
    public static void pushBack(Memory m, String trigger) {
        List<String> q = queued(m);
        q.add(0, trigger);
        store(m, q);
    }

    private static void store(Memory m, List<String> q) {
        if (q.isEmpty()) {
            m.unset(QUEUE_KEY);
            return;
        }
        StringBuilder b = new StringBuilder();
        for (String s : q) {
            if (b.length() > 0) b.append('\n');
            b.append(s);
        }
        m.set(QUEUE_KEY, b.toString());
    }
}
