package lastai.sagalib.test;

import lastai.sagalib.core.Content;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** The content index: rows from every mod, files loaded per mod, bad files skipped. */
public final class ContentTests {
    static final class FakeSource implements Content.Source {
        final JSONArray rows = new JSONArray();
        final Map<String, JSONObject> files = new HashMap<String, JSONObject>();
        boolean dev;

        FakeSource row(String id, String type, String path, String mod) throws Exception {
            JSONObject r = new JSONObject();
            r.put("id", id);
            r.put("type", type);
            r.put("path", path);
            r.put("mod", mod);
            rows.put(r);
            return this;
        }

        FakeSource file(String mod, String path, String singleQuoted) throws Exception {
            files.put(mod + "/" + path, Fx.json(singleQuoted));
            return this;
        }

        @Override
        public JSONArray index() { return rows; }

        @Override
        public JSONObject load(String path, String mod) throws Exception {
            JSONObject o = files.get(mod + "/" + path);
            if (o == null) throw new RuntimeException("no such file");
            return o;
        }

        @Override
        public boolean devMode() { return dev; }
    }

    static FakeSource sample() throws Exception {
        return new FakeSource()
                .row("vgc_q", "quest", "data/sagaforge/quests/vgc_q.json", "vgc")
                .row("vgc_people", "people", "data/sagaforge/people/vgc_people.json", "vgc")
                .row("vgc_factions", "factions", "data/sagaforge/factions.json", "vgc")
                .row("other_factions", "factions", "data/sagaforge/factions.json", "other")
                .row("vgc_profiles", "profiles", "data/sagaforge/dev/test_profiles.json", "vgc")
                .row("vgc_sys", "system", "data/sagaforge/systems/vgc_sys.json", "vgc")
                .row("dup", "quest", "data/sagaforge/quests/dup.json", "other")
                .row("missing", "quest", "data/sagaforge/quests/missing.json", "vgc")
                .row("weird", "spaceship", "x.json", "vgc")
                .row("half", "", "", "vgc")
                .file("vgc", "data/sagaforge/quests/vgc_q.json",
                        "{'format':1,'id':'vgc_q','title':'Q','stages':[{'id':'find'}],'phrases':[{'token':'$vgc_inline','cases':[],'otherwise':'i'}]}")
                .file("other", "data/sagaforge/quests/dup.json", "{'format':1,'id':'vgc_q','title':'Again'}")
                .file("vgc", "data/sagaforge/people/vgc_people.json", "{'format':1,'people':[{'id':'vgc_reyes','faction':'f',"
                        + "'phrases':[{'token':'$vgc_reyes_title','cases':[],'otherwise':'Captain'}]}],"
                        + "'phrases':[{'token':'$vgc_modwide','cases':[],'otherwise':'m'},{'token':'$vgc_inline','cases':[],'otherwise':'again'}]}")
                .file("vgc", "data/sagaforge/factions.json", "{'format':1,'relations':{'vgc':{'hegemony':0.2}}}")
                .file("other", "data/sagaforge/factions.json", "{'format':1,'relations':{'o':{'hegemony':-0.2}}}")
                .file("vgc", "data/sagaforge/dev/test_profiles.json", "{'format':1,'profiles':[{'id':'p1','name':'P'}]}")
                .file("vgc", "data/sagaforge/systems/vgc_sys.json", "{'format':1,'id':'vgc_sys','kind':'new','hyperspace':[1,2],'star':{'type':'star_yellow'},'entities':[]}");
    }

    public static void loadsEveryKind() throws Exception {
        FakeSource src = sample();
        Content c = Content.load(src);
        T.eq(1, c.quests.size(), "one quest (the duplicate is skipped)");
        T.eq("Q", c.quest("vgc_q").title, "first definition wins");
        T.yes(c.problems.has("quest \"vgc_q\" is already defined by data/sagaforge/quests/vgc_q.json (mod vgc)"), "duplicate reported: " + c.problems.all());
        T.eq(1, c.people.size(), "people");
        T.eq(3, c.phrases.size(), "inline, person's and mod-wide phrases");
        T.eq("i", c.phrases.get("$vgc_inline").otherwise, "first phrase wins");
        T.yes(c.problems.has("phrase $vgc_inline is already defined"), "duplicate phrase warned");
        T.eq(2, c.factions.size(), "two mods' factions.json at the same path are different files");
        T.eq(0, c.profiles.size(), "profiles are read only in dev mode");
        T.eq(1, c.systems.size(), "system read");
        T.eq("star_yellow", c.systems.get("vgc_sys").starType, "system star");
        T.yes(c.problems.has("data/sagaforge/quests/missing.json (mod vgc): can't read it"), "missing file");
        T.yes(c.problems.has("unknown type \"spaceship\""), "unknown type");
        T.yes(c.problems.has("row \"half\" needs type, path and mod"), "incomplete row");
        T.yes(c.summary().contains("1 quests"), "summary");
    }

    public static void devModeReadsProfiles() throws Exception {
        FakeSource src = sample();
        src.dev = true;
        Content c = Content.load(src);
        T.eq(1, c.profiles.size(), "profiles in dev mode");
        T.eq("P", c.profileList().get(0).name, "profile list");
    }

    public static void brokenIndexDoesNotThrow() {
        Content c = Content.load(new Content.Source() {
            @Override
            public JSONArray index() { throw new RuntimeException("boom"); }

            @Override
            public JSONObject load(String path, String mod) { return null; }

            @Override
            public boolean devMode() { return false; }
        });
        T.eq(0, c.quests.size(), "empty");
        T.yes(c.problems.has("can't read the content index"), "reported");
    }
}
