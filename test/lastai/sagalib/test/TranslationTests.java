package lastai.sagalib.test;

import lastai.sagalib.core.Content;
import lastai.sagalib.core.TextKeys;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;

/** Translations (docs/FORMAT.md "Translations"): keys, fingerprints, and what a translation replaces. */
public final class TranslationTests {
    private static JSONObject shared() throws Exception {
        Path f = Paths.get(System.getProperty("sagalib.modDir", "."), "test", "textkeys.json");
        return new JSONObject(new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
    }

    /** The cases Saga Forge checks too: both compute the same keys and fingerprints. */
    public static void keysAndFingerprintsMatchTheEditor() throws Exception {
        JSONObject s = shared();
        JSONObject fps = s.getJSONObject("fingerprints");
        for (Iterator<?> it = fps.keys(); it.hasNext(); ) {
            String text = String.valueOf(it.next());
            T.eq(fps.getString(text), TextKeys.fingerprint(text), "fingerprint of \"" + text + "\"");
        }
        JSONArray cases = s.getJSONArray("cases");
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            Map<String, String> got = TextKeys.leaves(c.getString("id"), c.get("json"));
            JSONObject want = c.getJSONObject("keys");
            T.eq(want.length(), got.size(), c.getString("id") + ": number of keys " + got.keySet());
            for (Iterator<?> it = want.keys(); it.hasNext(); ) {
                String k = String.valueOf(it.next());
                T.eq(want.getString(k), got.get(k), c.getString("id") + ": " + k);
            }
        }
    }

    private static JSONArray csv(String[]... rows) throws Exception {
        JSONArray a = new JSONArray();
        for (String[] r : rows) {
            JSONObject o = new JSONObject();
            o.put("key", r[0]);
            o.put("translation", r[1]);
            o.put("source", r[2]);
            a.put(o);
        }
        return a;
    }

    private static ContentTests.FakeSource source() throws Exception {
        ContentTests.FakeSource src = new ContentTests.FakeSource()
                .row("vgc_q", "quest", "data/sagaforge/quests/vgc_q.json", "vgc")
                .row("vgc_text", "strings", "data/sagaforge/text.json", "vgc")
                .row("vgc/de", "translation", "data/sagaforge/translations/vgc.de.csv", "vgc_de")
                .file("vgc", "data/sagaforge/quests/vgc_q.json",
                        "{'format':1,'id':'vgc_q','title':'The Convoy','stages':[{'id':'find','title':'Find it','journal':'Look in the belt.'},{'id':'back','title':'Go back'}]}")
                .file("vgc", "data/sagaforge/text.json",
                        "{'format':1,'rules':{'vgc_q_intro':{'t':'Hello $playerName.','o_yes':'Yes'}},'items':{'vgc_box':{'name':'Black box'}},'factions':{'vgc_v':{'displayName':'Vanguard'}}}");
        src.csvs.put("vgc_de/data/sagaforge/translations/vgc.de.csv", csv(
                new String[] { "vgc_q/title", "Der Konvoi", TextKeys.fingerprint("The Convoy") },
                new String[] { "vgc_q/stages[find].journal", "Sieh im Gürtel nach.", TextKeys.fingerprint("Look in the belt.") },
                // made for an older original: stays English
                new String[] { "vgc_q/stages[find].title", "Finde ihn", TextKeys.fingerprint("Find the convoy") },
                // empty: not translated yet
                new String[] { "vgc_q/stages[back].title", "", "" },
                new String[] { "vgc_text/rules.vgc_q_intro.t", "Hallo $playerName.", TextKeys.fingerprint("Hello $playerName.") },
                new String[] { "vgc_text/items.vgc_box.name", "Blackbox", TextKeys.fingerprint("Black box") }));
        return src;
    }

    public static void aTranslationReplacesCurrentLinesOnly() throws Exception {
        Content c = Content.load(source());
        T.eq(0, c.problems.errors(), "no errors " + c.problems.all());
        T.eq("Der Konvoi", c.quest("vgc_q").title, "title translated");
        T.eq("Sieh im Gürtel nach.", c.quest("vgc_q").stage("find").journal, "journal translated");
        T.eq("Find it", c.quest("vgc_q").stage("find").title, "a stale line falls back to the original");
        T.eq("Go back", c.quest("vgc_q").stage("back").title, "an empty line falls back to the original");
        T.eq("Hallo $playerName.", c.ruleTexts.get("vgc_q_intro").get("t"), "rule text translated");
        T.eq("Yes", c.ruleTexts.get("vgc_q_intro").get("o_yes"), "untranslated option stays");
        T.eq("Blackbox", c.translatedSpecs.get("items").get("vgc_box").get("name"), "item name to apply");
        T.no(c.translatedSpecs.containsKey("factions"), "an untranslated faction name isn't applied");
        T.eq("Vanguard", c.specTexts("factions").get("vgc_v").get("displayName"), "but it is known (to take back an old override)");
        T.eq("The Convoy", c.originals.get("vgc_q/title"), "the original is kept");
        T.yes(c.summary().contains("vgc (de, 6 lines from vgc_de)"), "summary names the translation: " + c.summary());
    }

    public static void withoutTheTranslationModNothingChanges() throws Exception {
        ContentTests.FakeSource src = source();
        src.csvs.clear();
        Content c = Content.load(src);
        T.yes(c.problems.has("missing"), "the listed CSV is reported missing");
        T.eq("The Convoy", c.quest("vgc_q").title, "original title");
        T.eq("Hello $playerName.", c.ruleTexts.get("vgc_q_intro").get("t"), "original rule text");
        T.yes(c.translatedSpecs.isEmpty(), "nothing to apply");
    }

    public static void twoTranslationsOfOneModUseTheFirst() throws Exception {
        ContentTests.FakeSource src = source().row("vgc/fr", "translation", "data/sagaforge/translations/vgc.fr.csv", "vgc_fr");
        src.csvs.put("vgc_fr/data/sagaforge/translations/vgc.fr.csv", csv(new String[] { "vgc_q/title", "Le convoi", "" }));
        Content c = Content.load(src);
        T.eq("Der Konvoi", c.quest("vgc_q").title, "the first listed wins");
        T.yes(c.problems.has("two translations of vgc are enabled"), "and the other is reported");
    }

    public static void badTranslationRowsAreReported() throws Exception {
        Content c = Content.load(new ContentTests.FakeSource().row("nolang", "translation", "x.csv", "m"));
        T.yes(c.problems.has("needs the id <mod id>/<language>"), "id without a language");
    }
}
