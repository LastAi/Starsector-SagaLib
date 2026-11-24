package lastai.sagalib.test;

import lastai.sagalib.core.Content;
import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.Problems;
import lastai.sagalib.core.QuestLogic;
import lastai.sagalib.core.Settings;
import lastai.sagalib.model.EncounterDef;
import lastai.sagalib.model.QuestDef;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** SagaLib 1.3 (task #62): mod settings from LunaSettings.csv, applied into $global keys, and scales by a setting. */
public final class SettingsTests {
    /** A CSV row as loadCSV gives it: only the columns named (others missing), every cell a String. */
    static JSONObject row(String... kv) throws Exception {
        JSONObject o = new JSONObject();
        for (int i = 0; i + 1 < kv.length; i += 2) o.put(kv[i], kv[i + 1]);
        return o;
    }

    static List<Settings.Field> read(Problems p, JSONObject... rows) {
        JSONArray a = new JSONArray();
        for (JSONObject r : rows) a.put(r);
        List<Settings.Field> out = new ArrayList<Settings.Field>();
        Settings.read("m", a, out, p, "LunaSettings.csv");
        return out;
    }

    public static void readsValueFieldsWithMissingColumns() throws Exception {
        Problems p = new Problems();
        List<Settings.Field> f = read(p,
                row("fieldID", "a_on", "fieldType", "Boolean", "defaultValue", "TRUE"),
                row("fieldID", "a_n", "fieldType", "Int", "defaultValue", " 7 ", "minValue", "1", "maxValue", "10"),
                row("fieldID", "a_d", "fieldType", "Double", "defaultValue", "0.25"),
                row("fieldID", "a_r", "fieldType", "Radio", "defaultValue", "B", "secondaryValue", "A, B ,C"),
                row("fieldID", "a_s", "fieldType", "String"),
                row("fieldID", "a_h", "fieldType", "Header", "fieldName", "Title"),
                row("fieldID", "a_t", "fieldType", "Text"),
                row("fieldType", "Boolean", "defaultValue", "true"),
                row("fieldID", "", "fieldType", "Int", "defaultValue", "x"));
        T.yes(p.isEmpty(), "no problems: " + p.all());
        T.eq(5, f.size(), "five value fields");
        T.eq(Boolean.TRUE, f.get(0).def, "Boolean, any case");
        T.eq(7f, f.get(1).def, "Int as Float, trimmed");
        T.eq(1.0, f.get(1).min, "min");
        T.eq(10.0, f.get(1).max, "max");
        T.eq(0.25f, f.get(2).def, "Double as Float");
        T.eq("[A, B, C]", f.get(3).options.toString(), "Radio choices trimmed");
        T.eq("B", f.get(3).def, "Radio default");
        T.eq("", f.get(4).def, "String without a defaultValue column is empty");
        T.eq("$global.a_on", f.get(0).key(), "memory key");
        T.eq("m", f.get(0).mod, "mod");
    }

    public static void unsupportedUnknownAndBadDefaults() throws Exception {
        Problems p = new Problems();
        List<Settings.Field> f = read(p,
                row("fieldID", "k", "fieldType", "Keycode", "defaultValue", "33"),
                row("fieldID", "c", "fieldType", "Color", "defaultValue", "#ff0000"),
                row("fieldID", "u", "fieldType", "Slider", "defaultValue", "1"),
                row("fieldID", "bi", "fieldType", "Int", "defaultValue", "1.5"),
                row("fieldID", "bd", "fieldType", "Double", "defaultValue", "lots"),
                row("fieldID", "bb", "fieldType", "Boolean", "defaultValue", "yes"),
                row("fieldID", "ok", "fieldType", "Int", "defaultValue", "-3"),
                row("fieldID", "r", "fieldType", "Radio", "defaultValue", "D", "secondaryValue", "A,B"));
        T.eq(2, f.size(), "only ok and r: " + f);
        T.eq(-3f, f.get(0).def, "negative Int");
        T.yes(p.has("warning in LunaSettings.csv at row 1: setting k: type Keycode isn't supported"), "Keycode warned: " + p.all());
        T.yes(p.has("setting c: type Color isn't supported"), "Color warned");
        T.yes(p.has("warning in LunaSettings.csv at row 3: setting u: unknown type \"Slider\""), "unknown type warned");
        T.yes(p.has("error in LunaSettings.csv at row 4: setting bi: the default \"1.5\" isn't a whole number"), "bad Int default");
        T.yes(p.has("setting bd: the default \"lots\" isn't a number"), "bad Double default");
        T.yes(p.has("setting bb: the default \"yes\" isn't true or false"), "bad Boolean default");
        T.yes(p.has("warning in LunaSettings.csv at row 8: setting r: the default \"D\" isn't one of its choices"), "Radio default outside the choices");
        T.eq(3, p.errors(), "three errors");
    }

    public static void duplicateIdsFirstWins() throws Exception {
        Problems p = new Problems();
        List<Settings.Field> f = read(p,
                row("fieldID", "x", "fieldType", "Int", "defaultValue", "1"),
                row("fieldID", "x", "fieldType", "Int", "defaultValue", "2"));
        T.eq(1, f.size(), "one");
        T.eq(1f, f.get(0).def, "the first wins");
        T.yes(p.has("setting x is already defined by m; the first one is used"), "warned: " + p.all());
        // across mods
        JSONArray other = new JSONArray().put(row("fieldID", "x", "fieldType", "Boolean", "defaultValue", "true"));
        Settings.read("n", other, f, p, "other.csv");
        T.eq(1, f.size(), "still one");
        T.yes(p.has("warning in other.csv at row 1: setting x is already defined by m"), "warned across mods");
    }

    public static void applySetsDefaultsThenPlayerValues() throws Exception {
        Problems p = new Problems();
        List<Settings.Field> f = read(p,
                row("fieldID", "on", "fieldType", "Boolean", "defaultValue", "false"),
                row("fieldID", "n", "fieldType", "Int", "defaultValue", "5"),
                row("fieldID", "d", "fieldType", "Double", "defaultValue", "1.5"),
                row("fieldID", "r", "fieldType", "Radio", "defaultValue", "A", "secondaryValue", "A,B"),
                row("fieldID", "s", "fieldType", "String", "defaultValue", "hi"));
        MapMemory m = new MapMemory();
        List<Settings.Applied> a = Settings.apply(m, f, null);
        T.eq(Boolean.FALSE, m.get("$global.on"), "default Boolean");
        T.eq(5f, m.get("$global.n"), "default Int");
        T.eq("hi", m.get("$global.s"), "default String");
        T.no(a.get(0).fromPlayer, "from the default");
        T.yes(a.get(0).changed(), "was unset");
        // LunaLib's objects: Boolean, Integer, Double, String
        final JSONObject player = new JSONObject().put("on", Boolean.TRUE).put("n", Integer.valueOf(9)).put("d", Double.valueOf(2.0)).put("r", "B").put("s", "");
        a = Settings.apply(m, f, new Settings.Player() {
            public Object value(Settings.Field fd) { return player.opt(fd.id); }
        });
        T.eq(Boolean.TRUE, m.get("$global.on"), "player Boolean");
        T.eq(9f, m.get("$global.n"), "player Int as Float");
        T.eq(2f, m.get("$global.d"), "player Double as Float");
        T.eq("B", m.get("$global.r"), "player Radio");
        T.eq("", m.get("$global.s"), "an empty String is a value");
        T.yes(a.get(0).fromPlayer, "from the player");
        // a value that doesn't read, a Radio value outside the choices, a whole Double for an Int; missing ones go back to the default
        final JSONObject bad = new JSONObject().put("on", "nope").put("n", Double.valueOf(4.0)).put("r", "C");
        a = Settings.apply(m, f, new Settings.Player() {
            public Object value(Settings.Field fd) { return bad.opt(fd.id); }
        });
        T.eq(Boolean.FALSE, m.get("$global.on"), "unreadable: default");
        T.eq(4f, m.get("$global.n"), "4.0 reads as an Int");
        T.eq(1.5f, m.get("$global.d"), "missing: back to the default");
        T.eq("A", m.get("$global.r"), "Radio outside the choices: default");
        T.eq("hi", m.get("$global.s"), "String back to the default");
        T.eq(Boolean.FALSE, Settings.read(f.get(0), "FALSE"), "Boolean text, any case");
        T.eq(null, Settings.read(f.get(1), Double.valueOf(4.5)), "4.5 isn't an Int");
    }

    /** SagaLib 1.4 (Saga Forge #67 U18): the values at the start of the game, recorded once. */
    public static void recordKeepsTheValueTheGameStartedWith() throws Exception {
        Problems p = new Problems();
        List<Settings.Field> f = read(p,
                row("fieldID", "on", "fieldType", "Boolean", "defaultValue", "false"),
                row("fieldID", "r", "fieldType", "Radio", "defaultValue", "A", "secondaryValue", "A,B"),
                row("fieldID", "n", "fieldType", "Int", "defaultValue", "5"));
        MapMemory m = new MapMemory();
        final JSONObject player = new JSONObject().put("on", Boolean.TRUE).put("r", "C");
        List<Settings.Field> done = Settings.record(m, f, new Settings.Player() {
            public Object value(Settings.Field fd) { return player.opt(fd.id); }
        });
        T.eq(3, done.size(), "every field recorded");
        T.eq("true", Settings.recorded(m, "on"), "the player's Boolean, as text");
        T.eq("A", Settings.recorded(m, "r"), "a Radio value outside the choices: the default");
        T.eq("5", Settings.recorded(m, "n"), "an Int without .0");
        T.eq("true", m.get("$global.sagaforge_setting_on"), "the memory key");
        T.eq(null, m.get("$global.on"), "recording doesn't set the live value");
        final JSONObject later = new JSONObject().put("on", Boolean.FALSE).put("r", "B");
        done = Settings.record(m, f, new Settings.Player() {
            public Object value(Settings.Field fd) { return later.opt(fd.id); }
        });
        T.eq(0, done.size(), "nothing recorded again");
        T.eq("true", Settings.recorded(m, "on"), "a later change doesn't change the record");
        Settings.apply(m, f, new Settings.Player() {
            public Object value(Settings.Field fd) { return later.opt(fd.id); }
        });
        T.eq(Boolean.FALSE, m.get("$global.on"), "the live value follows LunaLib");
        T.eq("true", Settings.recorded(m, "on"), "the record stays");
        T.eq(null, Settings.recorded(m, "unknown"), "not a setting: nothing recorded");
        MapMemory old = new MapMemory();
        Settings.record(old, f, null);
        T.eq("false", Settings.recorded(old, "on"), "without LunaLib: the default");
    }

    static Settings.Scale scale(String singleQuoted) throws Exception {
        Problems p = new Problems();
        Settings.Scale s = Settings.Scale.parse(Fx.json("{'s':" + singleQuoted + "}"), "s", "$", new JsonReader("t.json", p));
        T.yes(p.isEmpty(), "scale parses: " + p.all());
        return s;
    }

    public static void factorPerFactorsAndClamping() throws Exception {
        MapMemory m = new MapMemory();
        Settings.Scale per = scale("{'setting':'x','per':100}");
        T.eq(1.0, Settings.factor(per, m), "unset: 1");
        m.set("$global.x", 150f);
        T.eq(1.5, Settings.factor(per, m), "per");
        m.set("$global.x", "250");
        T.eq(2.5, Settings.factor(per, m), "a String number");
        m.set("$global.x", "lots");
        T.eq(1.0, Settings.factor(per, m), "not a number: 1");
        m.set("$global.x", Boolean.TRUE);
        T.eq(1.0, Settings.factor(per, m), "a Boolean without factors: 1");
        m.set("$global.x", 1e9f);
        T.eq(100.0, Settings.factor(per, m), "clamped to 100");
        m.set("$global.x", -50f);
        T.eq(0.0, Settings.factor(per, m), "clamped to 0");
        Settings.Scale plain = scale("{'setting':'x'}");
        m.set("$global.x", 0.5f);
        T.eq(0.5, Settings.factor(plain, m), "per defaults to 1");
        Settings.Scale fs = scale("{'setting':'x','factors':{'Easy':0.5,'true':3,'2':4,'Huge':1000}}");
        m.set("$global.x", "Easy");
        T.eq(0.5, Settings.factor(fs, m), "factor by text");
        m.set("$global.x", "Other");
        T.eq(1.0, Settings.factor(fs, m), "not in the factors: 1");
        m.set("$global.x", Boolean.TRUE);
        T.eq(3.0, Settings.factor(fs, m), "Boolean as true");
        m.set("$global.x", 2f);
        T.eq(4.0, Settings.factor(fs, m), "a Float as text without .0");
        m.set("$global.x", "Huge");
        T.eq(100.0, Settings.factor(fs, m), "factors are clamped too");
        // per 0 is replaced by 1 with a warning
        Problems p = new Problems();
        Settings.Scale zero = Settings.Scale.parse(Fx.json("{'s':{'setting':'x','per':0}}"), "s", "$", new JsonReader("t.json", p));
        T.eq(1.0, zero.per, "per 0: 1");
        T.yes(p.has("should be a number other than 0"), "warned");
        T.eq(null, Settings.Scale.parse(Fx.json("{'s':{'per':2}}"), "s", "$", new JsonReader("t.json", p)), "no setting: no scale");
        T.yes(p.has("at $.s.setting: missing"), "reported: " + p.all());
    }

    public static void creditsRoundAndPointsKeepOne() throws Exception {
        MapMemory m = new MapMemory();
        Settings.Scale s = scale("{'setting':'x'}");
        m.set("$global.x", 1.5f);
        T.eq(1499L, Settings.credits(999, s, m), "half up");
        m.set("$global.x", 0.333f);
        T.eq(333L, Settings.credits(1000, s, m), "rounded");
        T.eq(777L, Settings.credits(777, null, m), "no scale");
        m.set("$global.x", 0.01f);
        T.eq(1f, Settings.points(30, s, m), "at least 1 point");
        T.eq(0f, Settings.points(0, s, m), "0 stays 0");
        m.set("$global.x", 0f);
        T.eq(1f, Settings.points(30, s, m), "factor 0 still 1 point");
        m.set("$global.x", 2f);
        T.eq(60f, Settings.points(30, s, m), "doubled");
    }

    public static void rewardsAndEncountersParseTheirScale() throws Exception {
        QuestDef q = Fx.quest("{'format':1,'id':'q','title':'Q','stages':[{'id':'a'}],"
                + "'rewards':[{'id':'r','credits':1000,'creditsScale':{'setting':'pay','per':100}},{'id':'p','credits':5}]}");
        MapMemory m = new MapMemory();
        m.set("$global.pay", 50f);
        T.eq(500L, QuestLogic.credits(q.reward("r"), m), "scaled");
        T.eq(1000L, QuestLogic.credits(q.reward("r"), null), "no memory: unscaled");
        T.eq(5L, QuestLogic.credits(q.reward("p"), m), "no scale");
        Fx.RecordingEffects fx = new Fx.RecordingEffects();
        QuestLogic.reward(q, q.id, "r", fx, m);
        T.eq(500L, fx.credits, "given scaled");
        Problems p = new Problems();
        EncounterDef fp = EncounterDef.parse(Fx.json("{'format':1,'id':'e','fleet':{'faction':'pirates','points':40,'sizeScale':{'setting':'pay','per':100}},'spawn':{'at':'x'}}"), new JsonReader("e.json", p));
        EncounterDef ex = EncounterDef.parse(Fx.json("{'format':1,'id':'f','fleet':{'faction':'pirates','size':'explicit','variants':['a'],'points':40,'sizeScale':{'setting':'pay','per':100}},'spawn':{'at':'x'}}"), new JsonReader("e.json", p));
        T.yes(p.isEmpty(), "encounters parse: " + p.all());
        T.eq(20f, fp.scaledPoints(m), "fp scaled");
        T.eq(40f, ex.scaledPoints(m), "explicit fleets unchanged");
    }

    public static void contentReadsSettingsRows() throws Exception {
        ContentTests.FakeSource src = new ContentTests.FakeSource()
                .row("m_settings", "settings", "data/config/LunaSettings.csv", "m")
                .row("n_settings", "settings", "data/config/LunaSettings.csv", "n");
        src.csvs.put("m/data/config/LunaSettings.csv", new JSONArray()
                .put(row("fieldID", "m_on", "fieldType", "Boolean", "defaultValue", "true"))
                .put(row("fieldID", "m_head", "fieldType", "Header")));
        Content c = Content.load(src);
        T.eq(1, c.settings.size(), "one value field");
        T.eq("m", c.settings.get(0).mod, "its mod");
        T.yes(c.problems.has("data/config/LunaSettings.csv (mod n): missing"), "missing file is an error: " + c.problems.all());
        T.yes(c.summary().contains("1 settings"), "summary: " + c.summary());
    }
}
