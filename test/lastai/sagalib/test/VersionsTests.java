package lastai.sagalib.test;

import lastai.sagalib.core.Content;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.Mods;
import lastai.sagalib.core.Versions;
import lastai.sagalib.model.FactionsDef;
import lastai.sagalib.model.PoolDef;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** SagaLib 1.3: versions by installed mods (the golden case versions.json covers resolution and choosing). */
public final class VersionsTests {
    private static List<String> on(String... mods) { return Arrays.asList(mods); }

    public static void pathsSetCreateAndRemove() throws Exception {
        JSONObject item = Fx.json("{'id':'x','a':{'b':1,'k':5},'list':[{'id':'one','v':1},{'token':'$t','v':2},{'v':3}],"
                + "'versions':[{'id':'m','modsWith':['m'],'set':{'a.c.d':'new','list[one].v':10,'list[$t]':null,'a.b':null,"
                + "'list[nope].v':5,'a.k.z':1,'id':'other','bad..path':1,'n':7}}]}");
        Versions.Result r = Versions.resolve(item, on("m"));
        JSONObject v = r.value;
        T.no(v.has("versions"), "the copy has no versions");
        T.eq("new", v.getJSONObject("a").getJSONObject("c").getString("d"), "missing objects are created on the way");
        T.no(v.getJSONObject("a").has("b"), "null removes a key");
        T.eq(2, v.getJSONArray("list").length(), "null removes a list element (by token)");
        T.eq(10, v.getJSONArray("list").getJSONObject(0).getInt("v"), "key[id] by id");
        T.eq(3, v.getJSONArray("list").getJSONObject(1).getInt("v"), "an element without id or token isn't matched");
        T.eq("x", v.getString("id"), "the id can't change");
        T.eq(7, v.getInt("n"), "a bad path doesn't stop the rest of the version");
        T.eq(4, r.errors.size(), "list[nope], a.k.z (not an object), id, bad..path: " + r.errors);
        T.yes(r.errors.toString().contains("versions[0].set.list[nope].v: no element \"nope\" in list"), "error names the path: " + r.errors);
        T.yes(r.errors.toString().contains("versions[0].set.a.k.z: \"k\" isn't an object"), "not an object: " + r.errors);
        T.eq(1, item.getJSONObject("a").getInt("b"), "the item itself is unchanged");
        T.eq(Versions.canonical(Fx.json("{'id':'x','a':{'b':1,'k':5},'list':[{'id':'one','v':1},{'token':'$t','v':2},{'v':3}]}")),
                Versions.canonical(Versions.resolve(item, on()).value), "no mods: the base");
        T.yes(Versions.resolve(Fx.json("{'versions':[{'set':{'a':1}}]}"), on()).value.has("a"), "a version without conditions always holds");
    }

    public static void specificityAndConflicts() throws Exception {
        JSONObject item = Fx.json("{'id':'x','v':0,'versions':["
                + "{'id':'ab','modsWith':['a','b'],'set':{'v':3}},"
                + "{'id':'a','modsWith':['a'],'set':{'v':1,'w':1}},"
                + "{'id':'b','modsWith':['b'],'set':{'v':2,'w':2}},"
                + "{'id':'anyc','modsAny':[['c','d']],'set':{'z':1}}]}");
        Versions.Result r = Versions.resolve(item, on("a", "b"));
        T.eq(3, r.value.getInt("v"), "the most specific wins, whatever its place in the file");
        T.eq(2, r.value.getInt("w"), "equal specificity: the later one");
        T.eq(on("w"), r.conflicts, "v is overridden by a more specific version, w conflicts");
        T.eq(on("a", "b"), r.conflictVersions.get("w"), "the versions in conflict");
        T.eq(1, Versions.resolve(item, on("d")).value.getInt("z"), "modsAny");
        T.eq(on("a", "b", "c", "d"), Versions.mentioned(item), "mentioned, sorted");
    }

    public static void keysAndChoosing() throws Exception {
        T.eq("base", Versions.key(on()), "no mods");
        T.eq("Zed+alpha+beta", Versions.key(on("beta", "alpha", "Zed")), "Java String order");
        T.eq(on("a", "b"), Versions.mods("a+b"), "parsed back");
        T.eq(8, Versions.subsets(on("a", "b", "c")).size(), "2^3 subsets");
        T.eq("$global.sfv_q_my_mod_2", Versions.questFlag("q", "my-mod.2"), "flag key sanitised");
        final Set<String> enabled = new HashSet<String>(on("a"));
        Mods.setup(new Mods.Enabled() { public boolean isEnabled(String id) { return enabled.contains(id); } }, null);
        try {
            MapMemory m = new MapMemory();
            m.set(Versions.memKey("person", "p"), "a+gone");
            T.eq(on("a"), Versions.choose(m, "person", "p", "own", on("a", "b")), "a stored mod the item no longer mentions is dropped");
            T.eq("a+gone", m.get(Versions.memKey("person", "p")), "the stored choice isn't rewritten");
            T.eq(on("a"), Versions.choose(m, "quest", "q", "own", on("a", "b")), "chosen from the mods that count");
            T.eq(Boolean.TRUE, m.get(Versions.questFlag("q", "a")), "quest flag");
            enabled.add("b");
            T.eq(on("a"), Versions.choose(m, "quest", "q", "own", on("a", "b")), "never switches");
        } finally {
            Mods.setup(new Mods.Enabled() { public boolean isEnabled(String id) { return false; } }, null);
        }
    }

    /** SagaLib 1.4 (Saga Forge #67 U18): settingsWith: setting tokens in the on-set, specificity, choosing from the recorded values. */
    public static void settingsWithAndRecordedValues() throws Exception {
        JSONObject item = Fx.json("{'id':'x','v':0,'w':0,'versions':["
                + "{'id':'hard','settingsWith':['diff=Hard'],'set':{'v':1,'w':1}},"
                + "{'id':'a','modsWith':['a'],'set':{'v':2}},"
                + "{'id':'a+hard','modsWith':['a'],'settingsWith':['diff=Hard'],'set':{'v':3}},"
                + "{'id':'bad','settingsWith':['no+plus=x'],'set':{'w':9}},"
                + "{'id':'gone','settingsWith':['gone=true'],'set':{'w':8}}]}");
        T.eq(on("a", "setting:diff=Hard", "setting:gone=true"), Versions.mentioned(item), "mentioned: the mod and the usable setting tokens, sorted (the one with \"+\" is skipped)");
        Versions.Result r = Versions.resolve(item, on("setting:diff=Hard"));
        T.eq(1, r.value.getInt("v"), "settingsWith holds when its token counts");
        T.eq(0, Versions.resolve(item, on("setting:diff=Easy")).value.getInt("v"), "another value doesn't hold");
        r = Versions.resolve(item, on("a", "setting:diff=Hard"));
        T.eq(3, r.value.getInt("v"), "modsWith + settingsWith is more specific than either");
        T.eq(new ArrayList<String>(), r.conflicts, "no conflict: the combined version decides");
        T.eq(1, r.errors.size(), "a settingsWith entry with \"+\" is an error: " + r.errors);
        T.eq(8, Versions.resolve(item, on("setting:gone=true", "setting:no+plus=x")).value.getInt("w"), "the version with a bad entry never holds");
        T.eq(6, Versions.combinations(on("a", "setting:diff=Hard", "setting:diff=Easy")), "2 (a) x 3 (Hard, Easy, another)");
        T.eq(6, Versions.subsets(on("a", "setting:diff=Hard", "setting:diff=Easy")).size(), "no subset holds two values of one setting");
        T.eq("diff", Versions.settingId("setting:diff=Hard"), "setting id");
        T.eq("Hard", Versions.settingValue("setting:diff=Hard"), "setting value");
        T.eq(null, Versions.settingId("a"), "a mod isn't a token");
        T.eq("$global.sfv_q_setting_diff_Hard", Versions.questFlag("q", "setting:diff=Hard"), "quest flag of a token");
        final Set<String> enabled = new HashSet<String>(on("a"));
        Mods.setup(new Mods.Enabled() { public boolean isEnabled(String id) { return enabled.contains(id); } }, null);
        try {
            MapMemory m = new MapMemory();
            m.set(lastai.sagalib.core.Settings.recordKey("diff"), "Hard");
            List<String> got = Versions.choose(m, "quest", "q", "own", Versions.mentioned(item));
            T.eq(on("a", "setting:diff=Hard"), got, "chosen from the mods and the recorded values; an unknown setting never holds");
            T.eq("a+setting:diff=Hard", m.get(Versions.memKey("quest", "q")), "stored with the token");
            T.eq(Boolean.TRUE, m.get(Versions.questFlag("q", "setting:diff=Hard")), "the token's quest flag");
            T.eq(on("a", "setting:diff=Hard"), Versions.mods("a+setting:diff=Hard"), "the key parses back");
            m.set(lastai.sagalib.core.Settings.recordKey("diff"), "Easy");
            T.eq(on("a", "setting:diff=Hard"), Versions.choose(m, "quest", "q", "own", Versions.mentioned(item)), "a stored choice never changes");
            MapMemory fresh = new MapMemory();
            fresh.set(lastai.sagalib.core.Settings.recordKey("diff"), "Easy");
            T.eq(on("a"), Versions.choose(fresh, "person", "p", "own", Versions.mentioned(item)), "another recorded value: not that version");
        } finally {
            Mods.setup(new Mods.Enabled() { public boolean isEnabled(String id) { return false; } }, null);
        }
    }

    /** SagaLib 1.4 (Saga Forge #67 U37): versions by the player's gender, recorded once when the game starts. */
    public static void playerGenderRecordedOnce() throws Exception {
        JSONObject john = Fx.json("{'id':'john','firstName':'John','versions':[{'id':'f','settingsWith':['player_gender=FEMALE'],'set':{'firstName':'Joan'}}]}");
        MapMemory m = new MapMemory();
        T.eq(false, lastai.sagalib.core.Settings.recordPlayerGender(m, "ANY"), "ANY isn't recorded (a later load tries again)");
        T.eq(null, lastai.sagalib.core.Settings.recorded(m, "player_gender"), "nothing recorded");
        T.eq(new ArrayList<String>(), Versions.choose(m, "person", "john", "own", Versions.mentioned(john)), "not known: Base, no warning");
        MapMemory g = new MapMemory();
        T.eq(true, lastai.sagalib.core.Settings.recordPlayerGender(g, "FEMALE"), "recorded");
        T.eq(false, lastai.sagalib.core.Settings.recordPlayerGender(g, "MALE"), "never changed after");
        T.eq("FEMALE", lastai.sagalib.core.Settings.recorded(g, "player_gender"), "the first value stays");
        List<String> got = Versions.choose(g, "person", "john", "own", Versions.mentioned(john));
        T.eq(on("setting:player_gender=FEMALE"), got, "the female version chosen");
        T.eq("Joan", Versions.resolve(john, got).value.getString("firstName"), "her name");
        MapMemory male = new MapMemory();
        lastai.sagalib.core.Settings.recordPlayerGender(male, "MALE");
        T.eq("John", Versions.resolve(john, Versions.choose(male, "person", "john", "own", Versions.mentioned(john))).value.getString("firstName"), "a male player: Base");
    }

    /** SagaLib 1.4 (Saga Forge #67 U40): a fleet's extra groups: chance, all or some, by weight, no repeats. */
    public static void fleetExtrasRoll() throws Exception {
        lastai.sagalib.core.FleetExtras.Group always = new lastai.sagalib.core.FleetExtras.Group();
        always.variants.addAll(java.util.Arrays.asList("a", "b"));
        lastai.sagalib.core.FleetExtras.Group never = new lastai.sagalib.core.FleetExtras.Group();
        never.chance = 0f; never.variants.add("x");
        lastai.sagalib.core.FleetExtras.Group two = new lastai.sagalib.core.FleetExtras.Group();
        two.pick = 2; two.variants.addAll(java.util.Arrays.asList("p", "q", "r")); two.weights.addAll(java.util.Arrays.asList(1f, 1f, 0f));
        java.util.List<String> got = lastai.sagalib.core.FleetExtras.roll(java.util.Arrays.asList(always, never, two), new java.util.Random(5));
        T.eq(4, got.size(), "all of the first, none of the second, two of the third: " + got);
        T.eq(on("a", "b"), new ArrayList<String>(got.subList(0, 2)), "a group with pick 0 adds all its ships");
        T.eq(true, got.contains("p") && got.contains("q") && !got.contains("r"), "two drawn without repeats; weight 0 never: " + got);
        int hits = 0;
        lastai.sagalib.core.FleetExtras.Group half = new lastai.sagalib.core.FleetExtras.Group();
        half.chance = 0.5f; half.variants.add("h");
        java.util.Random r = new java.util.Random(1);
        for (int i = 0; i < 1000; i++) hits += lastai.sagalib.core.FleetExtras.roll(java.util.Arrays.asList(half), r).size();
        T.eq(true, hits > 400 && hits < 600, "a 50% group joins about half the time: " + hits);
    }

    static ContentTests.FakeSource source() throws Exception {
        return new ContentTests.FakeSource()
                .row("sfa_people", "people", "data/sagaforge/people/sfa_people.json", "sfa")
                .row("sfa_pool", "pool", "data/sagaforge/pools/sfa_odds.json", "sfa")
                .row("sfa_many", "pool", "data/sagaforge/pools/sfa_many.json", "sfa")
                .row("sfa_factions", "factions", "data/sagaforge/factions.json", "sfa")
                .row("sfa_text", "strings", "data/sagaforge/text.json", "sfa")
                .file("sfa", "data/sagaforge/people/sfa_people.json", "{'format':1,'people':["
                        + "{'id':'sfa_plain','faction':'hegemony'},"
                        + "{'id':'sfa_reyes','faction':'hegemony','name':{'first':'Ilse','last':'Reyes'},'phrases':[{'token':'$sfa_title','cases':[],'otherwise':'Captain'}],"
                        + "'versions':[{'id':'m1','modsWith':['mod1'],'set':{'name.last':'Okafor','phrases[$sfa_title].otherwise':'Admiral','gender':'ROBOT'}},"
                        + "{'id':'m2','modsWith':['mod2'],'set':{'phrases[$sfa_title]':null}}]}]}")
                .file("sfa", "data/sagaforge/pools/sfa_odds.json", "{'format':1,'id':'sfa_odds','nothing':0,'entries':[{'id':'a','weight':1}],"
                        + "'versions':[{'id':'m1','modsWith':['mod1'],'set':{'nothing':4}}]}")
                .file("sfa", "data/sagaforge/pools/sfa_many.json", "{'format':1,'id':'sfa_many','nothing':0,'entries':[{'id':'a','weight':1}],"
                        + "'versions':[{'id':'v','modsWith':['m1','m2','m3','m4','m5','m6','m7'],'set':{'nothing':9}},{'modsWithout':['m1'],'set':{'nothing':1}}]}")
                .file("sfa", "data/sagaforge/factions.json", "{'format':1,'relations':{'sfa_f':{'hegemony':0.2}},'scripted':{'sfa_f':[]},"
                        + "'versions':[{'id':'m1','modsWith':['mod1'],'set':{'scripted.sfa_f':[{'op':'set','path':'displayName','value':'Free Port'},"
                        + "{'op':'set','path':'doctrine.aggression','value':'7'}]}}]}")
                .file("sfa", "data/sagaforge/text.json", "{'format':1,'items':{'sfa_core':{'name':'Core','desc':'A core.'}},"
                        + "'descriptions':{'sfa_core|RESOURCE':{'text1':'Plain.'}},"
                        + "'versions':[{'id':'m1','modsWith':['mod1'],'set':{'items.sfa_core.name':'Mod1 Core','items.sfa_core.basePrice':500,"
                        + "'items.sfa_core.icon':'graphics/icons/x.png','descriptions.sfa_core|RESOURCE.text1':'Mod1 text.','items.sfa_core.desc':'A core.'}},"
                        + "{'id':'bad','modsWith':['mod2'],'set':{'items.sfa_core.color':'red','items.sfa_core.basePrice':'cheap'}}]}");
    }

    public static void contentParsesEveryVersion() throws Exception {
        Content c = Content.load(source());
        Versions.Item reyes = c.versioned.get("person:sfa_reyes");
        T.yes(reyes != null, "a person's versions: " + c.versioned.keySet());
        T.eq(on("mod1", "mod2"), reyes.mentioned, "mentioned");
        T.eq(new ArrayList<String>(Arrays.asList("base", "mod1", "mod2", "mod1+mod2")), new ArrayList<String>(reyes.defs.keySet()), "every combination parsed");
        T.no(c.versioned.containsKey("person:sfa_plain"), "people without versions aren't versioned");
        T.eq("Reyes", c.people.get("sfa_reyes").lastName, "the base is active after loading");
        T.eq(2, c.people.size(), "both people");
        int robots = 0;
        for (lastai.sagalib.core.Problems.Problem p : c.problems.all()) if (p.message.contains("ROBOT")) robots++;
        T.eq(1, robots, "an error only a version has is reported once: " + c.problems.all());
        T.yes(c.problems.has("(in version mod1)"), "tagged with the version: " + c.problems.all());

        // too many mods: an error, and the base only
        Versions.Item many = c.versioned.get("pool:sfa_many");
        T.yes(c.problems.has("at most 6"), "more than 6 mods is an error: " + c.problems.all());
        T.eq(0, many.mentioned.size(), "mentions nothing then");
        T.eq(1, many.defs.size(), "only the base");
        T.eq(1, (int) ((PoolDef) many.defs.get("base")).nothing, "the base is resolved with no mod counting (modsWithout holds)");

        // factions: the id is the owner; a version's bad change is reported, tagged
        Versions.Item fac = c.versioned.get("factions:sfa");
        T.yes(fac != null, "factions item by owner");
        T.eq(1, ((FactionsDef) fac.defs.get("mod1")).scripted.size(), "the version's good change");
        T.yes(c.problems.has("doctrine.aggression") || c.problems.has("scripted.sfa_f[1].value"), "the bad doctrine value: " + c.problems.all());

        // text.json versions: bad paths and values reported at load
        T.yes(c.problems.has("versions[1].set.items.sfa_core.color"), "unknown field: " + c.problems.all());
        T.yes(c.problems.has("versions[1].set.items.sfa_core.basePrice: should be a number"), "price must be a number");
        T.eq(1, c.textVersions.size(), "text versions kept for application load");
    }

    public static void activationSwapsAndStartsClean() throws Exception {
        Content c = Content.load(source());
        Versions.Item reyes = c.versioned.get("person:sfa_reyes");
        c.activate(reyes, "mod1");
        T.eq("Okafor", c.people.get("sfa_reyes").lastName, "the chosen version is in the map");
        T.eq("ANY", c.people.get("sfa_reyes").gender, "its bad field fell back as usual");
        T.eq("Admiral", c.phrases.get("$sfa_title").otherwise, "its phrases too");
        T.eq("mod1", Content.activeKey(reyes), "active key");
        c.activate(reyes, "mod2");
        T.no(c.phrases.containsKey("$sfa_title"), "a phrase the version removes is gone");
        c.activate(reyes, "base");
        T.eq("Reyes", c.people.get("sfa_reyes").lastName, "back to the base (another save)");
        T.eq("Captain", c.phrases.get("$sfa_title").otherwise, "and its phrase");
        c.activate(reyes, "nonsense");
        T.eq("base", Content.activeKey(reyes), "an unknown key uses the base");
        Versions.Item fac = c.versioned.get("factions:sfa");
        FactionsDef before = c.factions.get(0);
        c.activate(fac, "mod1");
        T.eq(1, c.factions.size(), "the factions entry is replaced, not added");
        T.yes(c.factions.get(0) != before && c.factions.get(0).scripted.size() == 1, "the version's changes");
        c.activate(c.versioned.get("pool:sfa_odds"), "mod1");
        T.eq(4, (int) c.pools.get("sfa_odds").nothing, "pools too");
    }

    public static void textVersionsFollowEnabledMods() throws Exception {
        Content c = Content.load(source());
        JSONObject text = c.textVersions.get(0).json;
        T.eq(0, Versions.specChanges(text, Collections.<String>emptyList()).size(), "no mods: nothing changes");
        Map<String, Map<String, Map<String, Object>>> ch = Versions.specChanges(text, on("mod1"));
        Map<String, Object> core = ch.get("items").get("sfa_core");
        T.eq("Mod1 Core", core.get("name"), "name");
        T.eq(500, ((Number) core.get("basePrice")).intValue(), "price");
        T.eq("graphics/icons/x.png", core.get("icon"), "icon");
        T.no(core.containsKey("desc"), "a value equal to the base isn't applied");
        T.eq("Mod1 text.", ch.get("descriptions").get("sfa_core|RESOURCE").get("text1"), "description");
        Map<String, Map<String, Map<String, Object>>> bad = Versions.specChanges(text, on("mod2"));
        T.eq(0, bad.size(), "unknown fields and non-number prices are never applied: " + bad);
    }

    public static void factionChangesParse() throws Exception {
        lastai.sagalib.core.Problems p = new lastai.sagalib.core.Problems();
        FactionsDef f = FactionsDef.parse(Fx.json("{'format':1,'scripted':{'x':["
                + "{'op':'set','path':'displayName','value':'X'},"
                + "{'op':'set','path':'displayNameLong','value':'X'},"
                + "{'op':'set','path':'secondaryColor','value':'10,20,30'},"
                + "{'op':'set','path':'secondaryColor','value':'10,20,300'},"
                + "{'op':'set','path':'doctrine.shipSize','value':3},"
                + "{'op':'set','path':'doctrine.combatFreighterProbability','value':'0.5'},"
                + "{'op':'set','path':'doctrine.numShips','value':'2.5'},"
                + "{'op':'set','path':'doctrine.speed','value':'1'},"
                + "{'op':'add','path':'illegalCommodities','value':'drugs'},"
                + "{'op':'remove','path':'illegalCommodities','value':'food'},"
                + "{'op':'add','path':'knownIndustries.industries','value':'x'},"
                + "{'op':'showInIntelTab'}]}}"), new lastai.sagalib.core.JsonReader("f.json", p));
        List<String> kept = new ArrayList<String>();
        for (FactionsDef.Change ch : f.scripted) kept.add(ch.op + " " + ch.path);
        T.eq(Arrays.asList("set displayName", "set secondaryColor", "set doctrine.shipSize", "set doctrine.combatFreighterProbability",
                "add illegalCommodities", "remove illegalCommodities", "showInIntelTab null"), kept, "valid changes kept: " + p.all());
        T.yes(p.has("$.scripted.x[1].path") && p.has("no setter for displayNameLong"), "displayNameLong has no setter in the game");
        T.yes(p.has("$.scripted.x[3].value"), "colour out of range");
        T.yes(p.has("$.scripted.x[6].value"), "a whole number");
        T.yes(p.has("$.scripted.x[7].path"), "unknown doctrine field");
        T.yes(p.has("$.scripted.x[10].path"), "add only to some lists");
        T.eq(Arrays.toString(new int[] { 1, 2, 3, 255 }), Arrays.toString(FactionsDef.color("1, 2, 3")), "alpha defaults to 255");
    }
}
