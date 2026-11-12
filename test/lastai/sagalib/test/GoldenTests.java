package lastai.sagalib.test;

import lastai.sagalib.core.Conditions;
import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.Phrases;
import lastai.sagalib.core.Problems;
import lastai.sagalib.core.QuestLogic;
import lastai.sagalib.core.Reconciler;
import lastai.sagalib.core.RuleLine;
import lastai.sagalib.core.Values;
import lastai.sagalib.model.PhraseDef;
import lastai.sagalib.model.QuestDef;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The golden cases shared with Saga Forge (test/golden/*.json), run with the same semantics as
 * tools/saga-forge/test/golden.test.ts:
 * <ul>
 * <li>do: one script cell (SF_* commands, "$key = value", "$key++", "unset $key");</li>
 * <li>check: a condition line, expected {@code is} (true/false);</li>
 * <li>token: a text whose tokens are filled (phrases, then memory), expected {@code is};</li>
 * <li>reload: the quest definitions are replaced, then the save is reconciled (ids of the original quests);</li>
 * <li>expect: memory values as text ("@credits" = the player's credits).</li>
 * </ul>
 */
public final class GoldenTests {
    private static final Pattern TOKEN = Pattern.compile("\\$[A-Za-z_][\\w.]*");

    static final class Run {
        final String file;
        Map<String, QuestDef> quests;
        final Map<String, PhraseDef> phrases = new LinkedHashMap<String, PhraseDef>();
        final MapMemory memory = new MapMemory();
        final Fx.RecordingEffects fx = new Fx.RecordingEffects();
        final Conditions conditions = new Conditions(memory, null);
        final Random random = new Random();
        final List<String> spawned = new ArrayList<String>();

        Run(String file) { this.file = file; }

        void script(String cell, String at) {
            for (String line : RuleLine.lines(cell)) {
                RuleLine.ScriptLine s = RuleLine.parseScriptLine(line);
                switch (s.kind) {
                    case SET:
                        memory.set(s.key, s.value.startsWith("$") ? memory.get(s.value) : Values.literal(s.value));
                        break;
                    case INCR: {
                        Double n = Values.number(memory.get(s.key));
                        memory.set(s.key, (float) ((n == null ? 0 : n) + s.by));
                        break;
                    }
                    case UNSET:
                        memory.unset(s.key);
                        break;
                    default:
                        command(s.name, s.args, at);
                }
            }
        }

        void command(String name, List<String> args, String at) {
            String q = args.isEmpty() ? "" : args.get(0);
            QuestDef def = quests.get(q);
            if ("SF_Start".equals(name)) QuestLogic.start(memory, def, q);
            else if ("SF_Step".equals(name)) QuestLogic.step(memory, def, q, args.get(1));
            else if ("SF_End".equals(name)) QuestLogic.end(memory, q, !"fail".equals(args.size() > 1 ? args.get(1) : ""));
            else if ("SF_Reward".equals(name)) QuestLogic.reward(def, q, args.get(1), fx);
            else if ("SF_Spawn".equals(name)) {
                if (!spawned.contains(q)) spawned.add(q);
            } else if ("SF_Despawn".equals(name)) spawned.remove(q);
            else throw new AssertionError(at + ": the Java golden runner doesn't simulate \"" + name + "\"");
        }

        /** Fills tokens like the editor's Engine.subst: phrases (recursively), then memory; unknown ones become [$token]. */
        String subst(String text) {
            Matcher m = TOKEN.matcher(text);
            StringBuffer out = new StringBuffer();
            while (m.find()) {
                String tok = m.group();
                String trimmed = tok.replaceAll("\\.+$", "");
                String tail = tok.substring(trimmed.length());
                String rep;
                PhraseDef p = phrases.get(trimmed);
                if (p != null) rep = subst(Phrases.text(p, conditions, random)) + tail;
                else if (memory.values.containsKey(trimmed)) rep = Values.str(memory.get(trimmed)) + tail;
                else rep = "[" + trimmed + "]" + tail;
                m.appendReplacement(out, Matcher.quoteReplacement(rep));
            }
            m.appendTail(out);
            return out.toString();
        }
    }

    static Map<String, QuestDef> quests(JSONArray a, String at) throws Exception {
        Map<String, QuestDef> out = new LinkedHashMap<String, QuestDef>();
        Problems p = new Problems();
        for (int i = 0; i < a.length(); i++) {
            QuestDef q = QuestDef.parse(a.getJSONObject(i), new JsonReader(at + " quest " + i, p));
            if (q != null) out.put(q.id, q);
        }
        T.yes(p.isEmpty(), at + ": the quests parse without problems: " + p.all());
        return out;
    }

    static void runFile(Path path) throws Exception {
        String f = path.getFileName().toString();
        JSONObject g = new JSONObject(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        Run run = new Run(f);
        JSONArray original = g.getJSONArray("quests");
        run.quests = quests(original, f);
        List<String> originalIds = new ArrayList<String>(run.quests.keySet());
        Problems pp = new Problems();
        JSONArray ph = g.optJSONArray("phrases");
        for (int i = 0; ph != null && i < ph.length(); i++) {
            PhraseDef p = PhraseDef.parse(ph.getJSONObject(i), "$.phrases[" + i + "]", new JsonReader(f, pp));
            if (p != null) run.phrases.put(p.token, p);
        }
        T.yes(pp.isEmpty(), f + ": phrases parse: " + pp.all());
        JSONArray steps = g.getJSONArray("steps");
        for (int i = 0; i < steps.length(); i++) {
            JSONObject s = steps.getJSONObject(i);
            String at = f + " step " + (i + 1);
            if (s.has("do")) run.script(s.getString("do"), at);
            if (s.has("check")) T.eq(s.get("is"), run.conditions.eval(s.getString("check")), at + ": " + s.getString("check"));
            if (s.has("token")) T.eq(s.get("is"), run.subst(s.getString("token")), at + ": " + s.getString("token"));
            if (s.has("reload")) {
                run.quests = quests(s.getJSONArray("reload"), at);
                Reconciler.reconcile(run.memory, originalIds, run.quests);
            }
            if (s.has("expect")) {
                JSONObject e = s.getJSONObject("expect");
                List<String> keys = new ArrayList<String>();
                for (Iterator<?> it = e.keys(); it.hasNext(); ) keys.add(String.valueOf(it.next()));
                Collections.sort(keys);
                for (String k : keys) {
                    String got = "@credits".equals(k) ? Long.toString(run.fx.credits) : Values.str(run.memory.get(k));
                    T.eq(e.getString(k), got, at + ": " + k);
                }
            }
        }
    }

    static List<Path> files() throws Exception {
        Path dir = Paths.get(System.getProperty("sagalib.modDir", "."), "test", "golden");
        List<Path> out = new ArrayList<Path>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : ds) out.add(p);
        }
        Collections.sort(out);
        return out;
    }

    public static void everyGoldenCasePasses() throws Exception {
        List<Path> files = files();
        T.yes(files.size() >= 4, "golden cases found: " + files.size());
        List<String> failures = new ArrayList<String>();
        for (Path p : files) {
            try {
                runFile(p);
                System.out.println("       golden " + p.getFileName() + ": ok");
            } catch (AssertionError e) {
                failures.add(e.getMessage());
            }
        }
        T.eq(new ArrayList<String>(), failures, "golden failures");
    }

    /** The runner itself fails on a wrong expectation (so a passing run means something). */
    public static void runnerDetectsMismatches() throws Exception {
        Run run = new Run("self");
        run.quests = Fx.map(Fx.basic());
        run.script("SF_Step vgc_q find\n$global.n = 2\n$global.n++", "self");
        T.eq("3", Values.str(run.memory.get("$global.n")), "++");
        T.eq("find", run.memory.get("$global.vgc_q_step"), "SF_Step ran");
        T.eq("[$nope]", run.subst("$nope"), "unknown token");
        T.eq("3.", run.subst("$global.n."), "trailing dot kept outside the token");
        boolean threw = false;
        try {
            run.script("AddCredits 5", "self");
        } catch (AssertionError e) {
            threw = true;
        }
        T.yes(threw, "unsimulated commands fail loudly");
    }
}
