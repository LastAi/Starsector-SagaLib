package lastai.sagalib.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One rules.csv condition or script line, parsed the way Saga Forge's engine does
 * (tools/saga-forge/src/shared/rules/parse.ts: tokenize, parseCondition, parseScriptLine).
 */
public final class RuleLine {
    private RuleLine() {}

    public static final List<String> OPERATORS = Collections.unmodifiableList(Arrays.asList("==", "!=", ">=", "<=", ">", "<"));

    public enum Kind { MEMORY, COMMAND, SCORE }

    /** A condition line. MEMORY: key [op value]; COMMAND: name args; SCORE: "score:N". */
    public static final class Condition {
        public Kind kind;
        public String raw;
        public boolean negate;
        public String key;
        /** null = "is set and truthy". */
        public String op;
        public String value;
        public String name;
        public List<String> args = new ArrayList<String>();
        /** The line without a leading "!" and a trailing "score:N" (what a command gets). */
        public String body;
    }

    public enum ScriptKind { SET, INCR, UNSET, COMMAND }

    public static final class ScriptLine {
        public ScriptKind kind;
        public String raw;
        public String key;
        public String value;
        public int by;
        public Float expire;
        public String name;
        public List<String> args = new ArrayList<String>();
    }

    /** Splits a line into tokens; "double quotes" group words. */
    public static List<String> tokenize(String line) {
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        boolean had = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                had = true;
                continue;
            }
            if (!quoted && Character.isWhitespace(c)) {
                if (cur.length() > 0 || had) out.add(cur.toString());
                cur.setLength(0);
                had = false;
                continue;
            }
            cur.append(c);
        }
        if (cur.length() > 0 || had) out.add(cur.toString());
        return out;
    }

    private static final Pattern SCORE_ONLY = Pattern.compile("(?i)^score\\s*:\\s*(-?[\\d.]+)$");
    private static final Pattern SCORE_TRAIL = Pattern.compile("(?i)\\s+score\\s*:\\s*(-?[\\d.]+)$");
    private static final Pattern COMPACT = Pattern.compile("^(\\$[^\\s=!<>]+)\\s*(==|!=|>=|<=|>|<)\\s*(.*)$");

    public static Condition parseCondition(String rawLine) {
        String line = rawLine.trim();
        Condition c = new Condition();
        c.raw = line;
        if (SCORE_ONLY.matcher(line).matches()) {
            c.kind = Kind.SCORE;
            c.body = line;
            return c;
        }
        String body = line;
        Matcher trail = SCORE_TRAIL.matcher(body);
        if (trail.find()) body = body.substring(0, trail.start()).trim();
        if (body.startsWith("!")) {
            c.negate = true;
            body = body.substring(1).trim();
        }
        c.body = body;
        if (body.startsWith("$")) {
            c.kind = Kind.MEMORY;
            List<String> toks = tokenize(body);
            c.key = toks.isEmpty() ? body : toks.get(0);
            if (toks.size() >= 3 && OPERATORS.contains(toks.get(1))) {
                c.op = toks.get(1);
                c.value = join(toks.subList(2, toks.size()));
                return c;
            }
            Matcher m = COMPACT.matcher(body);
            if (m.matches()) {
                c.key = m.group(1);
                c.op = m.group(2);
                c.value = m.group(3).replaceAll("^\"|\"$", "");
            }
            return c;
        }
        c.kind = Kind.COMMAND;
        List<String> toks = tokenize(body);
        c.name = toks.isEmpty() ? "" : toks.get(0);
        if (toks.size() > 1) c.args.addAll(toks.subList(1, toks.size()));
        return c;
    }

    private static final Pattern NUMBER = Pattern.compile("^-?[\\d.]+$");
    private static final Pattern POSTFIX_INCR = Pattern.compile("^(\\$\\S+?)(\\+\\+|--)$");

    public static ScriptLine parseScriptLine(String rawLine) {
        String line = rawLine.trim();
        List<String> toks = tokenize(line);
        ScriptLine s = new ScriptLine();
        s.raw = line;
        if (!toks.isEmpty() && toks.get(0).startsWith("$")) {
            String second = toks.size() > 1 ? toks.get(1) : null;
            if ("=".equals(second)) {
                s.kind = ScriptKind.SET;
                s.key = toks.get(0);
                String last = toks.get(toks.size() - 1);
                if (toks.size() >= 4 && NUMBER.matcher(last).matches()) {
                    s.expire = Float.parseFloat(last);
                    s.value = join(toks.subList(2, toks.size() - 1));
                } else {
                    s.value = join(toks.subList(2, toks.size()));
                }
                return s;
            }
            if ("++".equals(second) || "--".equals(second)) {
                s.kind = ScriptKind.INCR;
                s.key = toks.get(0);
                s.by = "++".equals(second) ? 1 : -1;
                if (toks.size() > 2) s.expire = Float.parseFloat(toks.get(2));
                return s;
            }
            Matcher m = POSTFIX_INCR.matcher(toks.get(0));
            if (m.matches()) {
                s.kind = ScriptKind.INCR;
                s.key = m.group(1);
                s.by = "++".equals(m.group(2)) ? 1 : -1;
                return s;
            }
        }
        if (toks.size() > 1 && "unset".equals(toks.get(0)) && toks.get(1).startsWith("$")) {
            s.kind = ScriptKind.UNSET;
            s.key = toks.get(1);
            return s;
        }
        s.kind = ScriptKind.COMMAND;
        s.name = toks.isEmpty() ? "" : toks.get(0);
        if (toks.size() > 1) s.args.addAll(toks.subList(1, toks.size()));
        return s;
    }

    /** Lines of a cell: trimmed, without blanks and "#" comments. */
    public static List<String> lines(String cell) {
        List<String> out = new ArrayList<String>();
        for (String l : cell.split("\\r?\\n")) {
            String t = l.trim();
            if (!t.isEmpty() && !t.startsWith("#")) out.add(t);
        }
        return out;
    }

    static String join(List<String> parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (b.length() > 0) b.append(' ');
            b.append(p);
        }
        return b.toString();
    }
}
