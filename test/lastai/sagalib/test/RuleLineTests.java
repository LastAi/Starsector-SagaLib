package lastai.sagalib.test;

import lastai.sagalib.core.RuleLine;

import java.util.Arrays;

/** Parsing condition and script lines like Saga Forge's parse.ts. */
public final class RuleLineTests {
    public static void tokenizing() {
        T.eq(Arrays.asList("SF_Show", "heading", "Two words"), RuleLine.tokenize("SF_Show heading \"Two words\""), "quotes group");
        T.eq(Arrays.asList("a", ""), RuleLine.tokenize("a \"\""), "empty quoted token kept");
        T.eq(Arrays.asList("a", "b"), RuleLine.tokenize("  a   b "), "spaces");
    }

    public static void memoryConditions() {
        RuleLine.Condition c = RuleLine.parseCondition("$player.commissionFactionId == vgc_vanguard");
        T.eq(RuleLine.Kind.MEMORY, c.kind, "kind");
        T.eq("$player.commissionFactionId", c.key, "key");
        T.eq("==", c.op, "op");
        T.eq("vgc_vanguard", c.value, "value");

        c = RuleLine.parseCondition("!$global.vgc_rude");
        T.yes(c.negate, "negated");
        T.eq(null, c.op, "plain flag");

        c = RuleLine.parseCondition("$a>=3");
        T.eq("$a", c.key, "compact key");
        T.eq(">=", c.op, "compact op");
        T.eq("3", c.value, "compact value");

        c = RuleLine.parseCondition("$id == rao score:1000");
        T.eq("rao", c.value, "score is not part of the value");
    }

    public static void otherConditions() {
        RuleLine.Condition c = RuleLine.parseCondition("score:50");
        T.eq(RuleLine.Kind.SCORE, c.kind, "score only");
        c = RuleLine.parseCondition("!SF_AtStep vgc_q find");
        T.eq(RuleLine.Kind.COMMAND, c.kind, "command");
        T.yes(c.negate, "negated command");
        T.eq("SF_AtStep", c.name, "name");
        T.eq(Arrays.asList("vgc_q", "find"), c.args, "args");
        T.eq("SF_AtStep vgc_q find", c.body, "body without !");
    }

    public static void scriptLines() {
        RuleLine.ScriptLine s = RuleLine.parseScriptLine("$global.vgc_q_pay = 2");
        T.eq(RuleLine.ScriptKind.SET, s.kind, "set");
        T.eq("2", s.value, "value");
        T.eq(null, s.expire, "no expiry");
        s = RuleLine.parseScriptLine("$asked = true 0");
        T.eq(0f, s.expire, "expiry");
        T.eq("true", s.value, "value before expiry");
        s = RuleLine.parseScriptLine("$n++");
        T.eq(RuleLine.ScriptKind.INCR, s.kind, "postfix ++");
        T.eq(1, s.by, "by");
        s = RuleLine.parseScriptLine("unset $x");
        T.eq(RuleLine.ScriptKind.UNSET, s.kind, "unset");
        s = RuleLine.parseScriptLine("SF_Step vgc_q find");
        T.eq(RuleLine.ScriptKind.COMMAND, s.kind, "command");
        T.eq(Arrays.asList("vgc_q", "find"), s.args, "args");
        T.eq(Arrays.asList("a", "b"), RuleLine.lines("a\n# comment\n\n  b  "), "lines");
    }
}
