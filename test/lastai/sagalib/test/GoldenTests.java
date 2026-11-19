package lastai.sagalib.test;

import lastai.sagalib.core.Conditions;
import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.Phrases;
import lastai.sagalib.core.Pools;
import lastai.sagalib.core.Problems;
import lastai.sagalib.core.QuestLogic;
import lastai.sagalib.core.Reconciler;
import lastai.sagalib.core.RuleLine;
import lastai.sagalib.core.Values;
import lastai.sagalib.model.PhraseDef;
import lastai.sagalib.model.PoolDef;
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
 * <li>expect: memory values as text ("@credits" = the player's credits);</li>
 * <li>SagaLib 1.3 (task #62): settings (apply the top-level "settings" with these player values; {} = no LunaLib),
 * credits ([quest, reward] scaled now), fleetPoints (an encounter's scaled points), unset (keys that must be null);</li>
 * <li>SagaLib 1.3: watch (the campaign script's check of Wait nodes on a MagicLib bounty's end), expected {@code fired}.</li>
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
        final Map<String, PoolDef> pools = new LinkedHashMap<String, PoolDef>();
        /** SagaLib 1.3 (task #70): the golden "people" (presence rules). */
        final Map<String, lastai.sagalib.model.PersonDef> people = new LinkedHashMap<String, lastai.sagalib.model.PersonDef>();
        /** SagaLib 1.3 (task #61): the golden "world" (places, people, market owners). */
        final GoldenWorld world = new GoldenWorld();
        /** SagaLib 1.3 (task #62): the golden "settings" (a mod's LunaSettings.csv rows) and "encounters". */
        final List<lastai.sagalib.core.Settings.Field> settings = new ArrayList<lastai.sagalib.core.Settings.Field>();
        final Map<String, lastai.sagalib.model.EncounterDef> encounters = new LinkedHashMap<String, lastai.sagalib.model.EncounterDef>();

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
            else if ("SF_Reward".equals(name)) QuestLogic.reward(def, q, args.get(1), fx, memory);
            else if ("SF_RollPool".equals(name)) {
                // as the game command: an object's seed ("$entity.sagaforge_salvage_seed" stands in
                // for Misc.getSalvageSeed) unless "global", then the stored world seed; a dev force key
                PoolDef pool = pools.get(q);
                if (pool == null) throw new AssertionError(at + ": no pool " + q);
                boolean object = !(args.size() > 1 && "global".equals(args.get(1)));
                String seed = Values.str(memory.get(object ? "$entity.sagaforge_salvage_seed" : Pools.SEED_KEY));
                String fk = "$global.sagaforge_pool_" + q + "_force";
                Object f = memory.get(fk);
                if (f != null) memory.unset(fk);
                Pools.roll(pool, memory, conditions, Long.parseLong(seed), object, f == null ? null : Values.str(f));
            } else if ("SF_StartBounty".equals(name)) {
                // SagaLib 1.3 (task #59): when it may start, the bridge's accept makes MagicLib set its key false
                if (lastai.sagalib.core.Bounties.cantStart(memory, null, q) == null) memory.set(lastai.sagalib.core.Bounties.globalKey(q), Boolean.FALSE);
            } else if ("SF_Spawn".equals(name)) {
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

    /** SagaLib 1.3 (task #61): a sector stand-in: everything exists until a "world" step says otherwise. */
    static final class GoldenWorld implements lastai.sagalib.core.Places.World {
        java.util.Set<String> places = null, people = null;
        final Map<String, String> owners = new LinkedHashMap<String, String>();
        /** SagaLib 1.3 (task #70): the factions that exist (null = any) and their relations ("a|b" = LEVEL, both ways; unset = NEUTRAL). */
        java.util.Set<String> factions = null;
        final Map<String, String> relations = new LinkedHashMap<String, String>();

        void set(JSONObject w) throws Exception {
            JSONArray fa = w.optJSONArray("factions");
            factions = fa == null ? null : new java.util.HashSet<String>(strings(fa));
            relations.clear();
            JSONObject rel = w.optJSONObject("relations");
            for (Iterator<?> it = rel == null ? Collections.<Object>emptyIterator() : rel.keys(); it.hasNext(); ) {
                String k = String.valueOf(it.next());
                relations.put(k, rel.getString(k));
            }
            places = new java.util.HashSet<String>(strings(w.optJSONArray("places")));
            people = new java.util.HashSet<String>(strings(w.optJSONArray("people")));
            owners.clear();
            JSONObject o = w.optJSONObject("owners");
            for (Iterator<?> it = o == null ? Collections.<Object>emptyIterator() : o.keys(); it.hasNext(); ) {
                String k = String.valueOf(it.next());
                owners.put(k, o.getString(k));
            }
        }

        public boolean place(String id) { return places == null || places.contains(id) || owners.containsKey(id); }
        public boolean person(String id) { return people == null || people.contains(id); }
        public String owner(String id) { return owners.get(id); }
        public String relation(String a, String b) {
            if (factions != null && (!factions.contains(a) || !factions.contains(b))) return null;
            String r = relations.get(a + "|" + b);
            if (r == null) r = relations.get(b + "|" + a);
            return r == null ? "NEUTRAL" : r;
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
        JSONArray pl = g.optJSONArray("pools");
        for (int i = 0; pl != null && i < pl.length(); i++) {
            PoolDef p = PoolDef.parse(pl.getJSONObject(i), new JsonReader(f, pp));
            if (p != null) run.pools.put(p.id, p);
        }
        T.yes(pp.isEmpty(), f + ": pools parse: " + pp.all());
        // SagaLib 1.3 (task #70): people in their file format (presence rules)
        JSONArray pe = g.optJSONArray("people");
        for (int i = 0; pe != null && i < pe.length(); i++) {
            lastai.sagalib.model.PersonDef p = lastai.sagalib.model.PersonDef.parse(pe.getJSONObject(i), "$.people[" + i + "]", new JsonReader(f, pp));
            if (p != null) run.people.put(p.id, p);
        }
        T.yes(pp.isEmpty(), f + ": people parse: " + pp.all());
        // SagaLib 1.3 (task #62): a mod's settings rows (as loadCSV gives them) and encounters in their file format
        JSONObject st = g.optJSONObject("settings");
        if (st != null) lastai.sagalib.core.Settings.read(st.getString("mod"), st.getJSONArray("rows"), run.settings, pp, f + " settings");
        T.yes(pp.isEmpty(), f + ": settings read: " + pp.all());
        JSONArray ec = g.optJSONArray("encounters");
        for (int i = 0; ec != null && i < ec.length(); i++) {
            lastai.sagalib.model.EncounterDef e = lastai.sagalib.model.EncounterDef.parse(ec.getJSONObject(i), new JsonReader(f + " encounter " + i, pp));
            if (e != null) run.encounters.put(e.id, e);
        }
        T.yes(pp.isEmpty(), f + ": encounters parse: " + pp.all());
        // SagaLib 1.3: the content mods' lists (mods.json) and the mods enabled now
        final java.util.Set<String> enabled = new java.util.HashSet<String>();
        JSONArray en = g.optJSONArray("enabled");
        for (int i = 0; en != null && i < en.length(); i++) enabled.add(en.getString(i));
        Map<String, lastai.sagalib.core.Mods.ModList> lists = new LinkedHashMap<String, lastai.sagalib.core.Mods.ModList>();
        JSONArray ml = g.optJSONArray("modLists");
        for (int i = 0; ml != null && i < ml.length(); i++) {
            JsonReader mr = new JsonReader(f, pp);
            mr.mod = ml.getJSONObject(i).optString("mod");
            lastai.sagalib.core.Mods.ModList l = lastai.sagalib.core.Mods.ModList.parse(ml.getJSONObject(i), mr);
            if (l != null) lists.put(l.owner, l);
        }
        T.yes(pp.isEmpty(), f + ": mod lists parse: " + pp.all());
        lastai.sagalib.core.Mods.setup(new lastai.sagalib.core.Mods.Enabled() {
            public boolean isEnabled(String id) { return enabled.contains(id); }
        }, lists);
        // SagaLib 1.3 (task #59): what MagicLib knows (its bounties and their memory keys); the bridge is there
        JSONObject bo = g.optJSONObject("bounties");
        final List<String> known = strings(bo == null ? null : bo.optJSONArray("known"));
        final JSONObject memKeys = bo == null ? null : bo.optJSONObject("memKeys");
        lastai.sagalib.core.Bounties.setup(new lastai.sagalib.core.Bounties.Source() {
            public boolean ready() { return true; }
            public boolean known(String id) { return known.contains(id); }
            public String memKey(String id) { return memKeys == null ? null : memKeys.optString(id, null); }
        });
        // SagaLib 1.3 (task #61): the sector as quests need it (golden step "world" replaces it)
        lastai.sagalib.core.Places.setup(run.world);
        try {
            runSteps(g, run, f, originalIds, enabled);
        } finally {
            lastai.sagalib.core.Places.setup(null);
            lastai.sagalib.core.Mods.setup(new lastai.sagalib.core.Mods.Enabled() {
                public boolean isEnabled(String id) { return false; }
            }, null);
            lastai.sagalib.core.Bounties.setup(null);
        }
    }

    /** SagaLib 1.3: an item with versions (golden key "versioned"). */
    static final class VersionedItem {
        final String kind, id, owner;
        final JSONObject item;
        final List<String> mentioned;

        VersionedItem(String kind, String owner, JSONObject item) {
            this.kind = kind;
            this.owner = owner;
            this.item = item;
            this.id = "factions".equals(kind) ? owner : item.optString("id");
            this.mentioned = lastai.sagalib.core.Versions.mentioned(item);
        }
    }

    static Map<String, VersionedItem> versioned(JSONObject g) throws Exception {
        Map<String, VersionedItem> out = new LinkedHashMap<String, VersionedItem>();
        JSONArray a = g.optJSONArray("versioned");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject v = a.getJSONObject(i);
            VersionedItem it = new VersionedItem(v.getString("kind"), v.getString("owner"), v.getJSONObject("item"));
            out.put(it.kind + ":" + it.id, it);
        }
        return out;
    }

    static List<String> strings(JSONArray a) throws Exception {
        List<String> out = new ArrayList<String>();
        for (int i = 0; a != null && i < a.length(); i++) out.add(a.getString(i));
        return out;
    }

    static void runSteps(JSONObject g, Run run, String f, List<String> originalIds, java.util.Set<String> enabled) throws Exception {
        JSONArray steps = g.getJSONArray("steps");
        Map<String, VersionedItem> versioned = versioned(g);
        for (int i = 0; i < steps.length(); i++) {
            JSONObject s = steps.getJSONObject(i);
            String at = f + " step " + (i + 1);
            if (s.optBoolean("newGame", false)) {
                // SagaLib 1.3: an empty save
                run.memory.values.clear();
                lastai.sagalib.core.Mods.resetSkipped();
            }
            if (s.has("do")) run.script(s.getString("do"), at);
            // SagaLib 1.3 (task #62): a settings apply (the player's values as text; {} = no LunaLib), scaled credits and fleet points
            if (s.has("settings")) {
                final JSONObject pv = s.getJSONObject("settings");
                lastai.sagalib.core.Settings.apply(run.memory, run.settings, pv.length() == 0 ? null : new lastai.sagalib.core.Settings.Player() {
                    public Object value(lastai.sagalib.core.Settings.Field fd) { return pv.has(fd.id) ? pv.opt(fd.id) : null; }
                });
            }
            if (s.has("credits")) {
                JSONArray cr = s.getJSONArray("credits");
                QuestDef q = run.quests.get(cr.getString(0));
                T.yes(q != null, at + ": no quest " + cr.getString(0));
                QuestDef.Reward rw = q.reward(cr.getString(1));
                T.yes(rw != null, at + ": no reward " + cr.getString(1) + " in " + q.id);
                T.eq(s.getLong("is"), QuestLogic.credits(rw, run.memory), at + ": credits of " + q.id + "/" + rw.id);
            }
            if (s.has("fleetPoints")) {
                lastai.sagalib.model.EncounterDef e = run.encounters.get(s.getString("fleetPoints"));
                T.yes(e != null, at + ": no encounter " + s.getString("fleetPoints"));
                T.eq(s.getDouble("is"), (double) e.scaledPoints(run.memory), at + ": fleet points of " + e.id);
            }
            if (s.has("unset")) {
                for (String k : strings(s.getJSONArray("unset"))) T.eq(null, run.memory.get(k), at + ": " + k + " is unset");
            }
            if (s.has("enabled")) {
                enabled.clear();
                JSONArray e = s.getJSONArray("enabled");
                for (int k = 0; k < e.length(); k++) enabled.add(e.getString(k));
            }
            if (s.has("record")) {
                List<String> fresh = new ArrayList<String>();
                for (lastai.sagalib.core.Mods.Notice n : lastai.sagalib.core.Mods.record(run.memory, true)) if (n.fresh) fresh.add(n.toString());
                List<String> want = new ArrayList<String>();
                JSONArray nw = s.optJSONArray("notices");
                for (int k = 0; nw != null && k < nw.length(); k++) want.add(nw.getString(k));
                if (nw != null) T.eq(want, fresh, at + ": notices");
            }
            if (s.has("gate")) {
                JsonReader gr = new JsonReader(f, new Problems());
                gr.mod = s.optString("owner", null);
                lastai.sagalib.core.ModGate gate = lastai.sagalib.core.ModGate.parse(s.getJSONObject("gate"), "$.gate", gr);
                T.eq(s.get("is"), lastai.sagalib.core.Mods.blocked(run.memory, gate) == null, at + ": gate " + gate.describe());
            }
            // SagaLib 1.3 (task #61): places a quest needs, offers, the daily owner check
            if (s.has("world")) run.world.set(s.getJSONObject("world"));
            if (s.has("needs")) {
                QuestDef q = run.quests.get(s.getString("needs"));
                T.yes(q != null, at + ": no quest " + s.getString("needs"));
                JSONObject want = s.getJSONObject("is");
                T.eq(strings(want.optJSONArray("places")), lastai.sagalib.core.Places.places(q), at + ": places " + q.id + " needs");
                T.eq(strings(want.optJSONArray("people")), lastai.sagalib.core.Places.people(q), at + ": people " + q.id + " needs");
            }
            if (s.has("offer")) {
                QuestDef q = run.quests.get(s.getString("offer"));
                T.yes(q != null, at + ": no quest " + s.getString("offer"));
                // as SagaQuest.create: the mod gate (with the sector), not active, nothing missing
                String miss = lastai.sagalib.core.Places.missing(q, run.world, null);
                // SagaLib 1.3 (task #70): and its contact is not away
                String away = lastai.sagalib.core.Presence.awayPerson(run.memory, q);
                boolean ok = lastai.sagalib.core.Mods.blocked(run.memory, q.gate) == null && !QuestLogic.isActive(run.memory, q.id) && miss == null && away == null;
                T.eq(s.get("is"), ok, at + ": offer " + q.id);
                if (s.has("missing")) T.eq(s.getString("missing"), miss, at + ": missing for " + q.id);
                if (s.has("away")) T.eq(s.getString("away"), away, at + ": away for " + q.id);
            }
            if (s.optBoolean("day", false)) {
                List<String> got = new ArrayList<String>();
                for (QuestDef q : run.quests.values()) {
                    lastai.sagalib.core.Places.Change c = lastai.sagalib.core.Places.check(run.memory, q);
                    if (c != null) got.add(q.id + ":" + c.market + "->" + c.owner + ":" + c.action + (c.stage != null ? ":" + c.stage : "") + (c.trigger != null ? ":" + c.trigger : ""));
                }
                if (s.has("changes")) T.eq(strings(s.getJSONArray("changes")), got, at + ": owner changes");
            }
            // SagaLib 1.3: the campaign script's check of Wait nodes on a MagicLib bounty's end
            if (s.optBoolean("watch", false)) {
                List<String> got = new ArrayList<String>();
                for (QuestDef q : run.quests.values()) got.addAll(lastai.sagalib.core.BountyWaits.tick(q, run.memory, s.optString("owner", null)));
                if (s.has("fired")) T.eq(strings(s.getJSONArray("fired")), got, at + ": fired");
            }
            // SagaLib 1.3 (task #70): the presence check (daily and on load)
            if (s.optBoolean("presence", false)) {
                List<String> got = new ArrayList<String>();
                for (lastai.sagalib.core.Presence.Move mv : lastai.sagalib.core.Presence.check(run.memory, run.people.values(), run.world)) got.add(mv.toString());
                if (s.has("moves")) T.eq(strings(s.getJSONArray("moves")), got, at + ": presence moves");
            }
            if (s.has("check")) T.eq(s.get("is"), run.conditions.owner(s.optString("owner", null)).eval(s.getString("check")), at + ": " + s.getString("check"));
            if (s.has("token")) T.eq(s.get("is"), run.subst(s.getString("token")), at + ": " + s.getString("token"));
            if (s.has("reload")) {
                run.quests = quests(s.getJSONArray("reload"), at);
                Reconciler.reconcile(run.memory, originalIds, run.quests);
            }
            // SagaLib 1.3: versions by installed mods
            if (s.optBoolean("choose", false)) {
                for (VersionedItem it : versioned.values()) lastai.sagalib.core.Versions.choose(run.memory, it.kind, it.id, it.owner, it.mentioned);
            }
            if (s.has("resolve")) {
                VersionedItem it = versioned.get(s.getString("resolve"));
                T.yes(it != null, at + ": no versioned item " + s.getString("resolve"));
                lastai.sagalib.core.Versions.Result res = lastai.sagalib.core.Versions.resolve(it.item, strings(s.optJSONArray("on")));
                String what = at + ": " + s.getString("resolve") + " with " + strings(s.optJSONArray("on"));
                T.eq(new ArrayList<String>(), res.errors, what + ": errors");
                if (s.has("is")) T.eq(lastai.sagalib.core.Versions.canonical(s.get("is")), lastai.sagalib.core.Versions.canonical(res.value), what);
                if (s.has("conflicts")) T.eq(strings(s.getJSONArray("conflicts")), res.conflicts, what + ": conflicts");
            }
            if (s.has("chosen")) {
                VersionedItem it = versioned.get(s.getString("chosen"));
                T.yes(it != null, at + ": no versioned item " + s.getString("chosen"));
                List<String> on = lastai.sagalib.core.Versions.chosen(run.memory, it.kind, it.id, it.mentioned);
                T.yes(on != null, at + ": " + s.getString("chosen") + " is chosen");
                T.eq(lastai.sagalib.core.Versions.canonical(s.get("is")), lastai.sagalib.core.Versions.canonical(lastai.sagalib.core.Versions.resolve(it.item, on).value),
                        at + ": " + s.getString("chosen") + " as the save uses it (" + on + ")");
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
