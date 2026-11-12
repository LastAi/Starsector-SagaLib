package lastai.sagalib.core;

import lastai.sagalib.SagaLog;

import java.util.List;

/**
 * Evaluates rules.csv condition lines (docs/FORMAT.md, "Conditions"): memory comparisons here,
 * SagaLib's own SF_ conditions through {@link QuestLogic}, every other command through a
 * {@link Commands} resolver (the game adapter runs the real rule command).
 */
public final class Conditions {
    /** Evaluates a condition command that isn't SagaLib's. null = unknown. */
    public interface Commands {
        Boolean eval(String name, List<String> args, String body);
    }

    private final Memory memory;
    private final Commands commands;

    public Conditions(Memory memory, Commands commands) {
        this.memory = memory;
        this.commands = commands;
    }

    public boolean eval(String line) { return eval(RuleLine.parseCondition(line)); }

    /** True if every line holds. */
    public boolean all(List<String> lines) {
        for (String l : lines) if (!eval(l)) return false;
        return true;
    }

    public boolean eval(RuleLine.Condition c) {
        if (c.kind == RuleLine.Kind.SCORE) return true;
        boolean r;
        if (c.kind == RuleLine.Kind.MEMORY) {
            Object v = memory.get(c.key);
            if (c.op == null) {
                r = Values.truthy(v);
            } else {
                Object rhs = c.value == null ? null : c.value.startsWith("$") ? memory.get(c.value) : Values.literal(c.value);
                r = Values.compare(v, c.op, rhs);
            }
        } else {
            Boolean known = QuestLogic.condition(memory, c.name, c.args);
            if (known == null && commands != null) known = commands.eval(c.name, c.args, c.body);
            if (known == null) {
                SagaLog.warnOnce("cond:" + c.name, "condition \"" + c.raw + "\": no rule command " + c.name + " found; counted as false");
                known = false;
            }
            r = known;
        }
        return c.negate != r;
    }
}
