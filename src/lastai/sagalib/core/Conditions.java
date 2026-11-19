package lastai.sagalib.core;

import lastai.sagalib.SagaLog;

import java.util.List;

/**
 * Evaluates rules.csv condition lines (docs/FORMAT.md, "Conditions"): memory comparisons here,
 * SagaLib's own SF_ conditions through {@link QuestLogic}, {@link Mods} and {@link Bounties}, every other command through a
 * {@link Commands} resolver (the game adapter runs the real rule command).
 */
public final class Conditions {
    /** Evaluates a condition command that isn't SagaLib's. null = unknown. */
    public interface Commands {
        Boolean eval(String name, List<String> args, String body);
    }

    private final Memory memory;
    private final Commands commands;
    /** SagaLib 1.3: the content mod these conditions belong to (whose start record SF_HasMod reads); null = any. */
    private String owner;

    public Conditions(Memory memory, Commands commands) {
        this.memory = memory;
        this.commands = commands;
    }

    /** The content mod these conditions belong to (SF_HasMod / SF_HasAnyMod read its start record). */
    public Conditions owner(String mod) {
        this.owner = mod;
        return this;
    }

    /** SagaLib 1.3: SF_HasMod (every listed mod counts) and SF_HasAnyMod (one does); null for other names. */
    public static Boolean modCondition(Memory m, String owner, String name, List<String> args) {
        if ("SF_HasMod".equals(name)) return !args.isEmpty() && Mods.hasAll(m, owner, args);
        if ("SF_HasAnyMod".equals(name)) return !args.isEmpty() && Mods.hasAny(m, owner, args);
        return null;
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
            Boolean known = modCondition(memory, owner, c.name, c.args);
            if (known == null) known = Bounties.condition(memory, owner, c.name, c.args);
            if (known == null) known = Presence.condition(memory, c.name, c.args); // SagaLib 1.3 (task #70)
            if (known == null) known = QuestLogic.condition(memory, c.name, c.args);
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
