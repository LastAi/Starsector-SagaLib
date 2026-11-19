package lastai.sagalib.test;

import lastai.sagalib.core.Content;
import lastai.sagalib.model.FactionsDef;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** SagaLib 1.3 (Saga Forge task #63): starting relations left to a mod's Nexerelin faction config. */
public final class NexerelinTests {
    static Content content(String nexerelin) throws Exception {
        ContentTests.FakeSource src = new ContentTests.FakeSource()
                .row("vgc_factions", "factions", "data/sagaforge/factions.json", "vgc")
                .file("vgc", "data/sagaforge/factions.json", "{'format':1,'relations':{"
                        + "'vgc_f':{'hegemony':-0.5,'pirates':-0.7,'hmi':{'value':0.3,'modsWith':['HMI']}},"
                        + "'vgc_g':{'hegemony':0.2}},'scripted':{}" + nexerelin + "}");
        return Content.load(src);
    }

    static FactionsDef.Relation rel(FactionsDef d, String a, String b) {
        for (FactionsDef.Relation r : d.relations) if (r.faction.equals(a) && r.other.equals(b)) return r;
        throw new AssertionError("no relation " + a + "/" + b);
    }

    static FactionsDef.ShipsFile ships(final String... paths) {
        final Set<String> have = new HashSet<String>(Arrays.asList(paths));
        return new FactionsDef.ShipsFile() {
            public boolean has(String mod, String path) { return "vgc".equals(mod) && have.contains(path); }
        };
    }

    public static void theListIsRead() throws Exception {
        Content c = content(",'nexerelin':['vgc_f']");
        T.eq(0, c.problems.errors(), "no problems: " + c.problems.all());
        FactionsDef d = c.factions.get(0);
        T.eq(Arrays.asList("vgc_f"), d.nexerelin, "nexerelin list");
        T.eq("vgc", d.mod, "the mod that ships it");
        T.eq("data/config/exerelinFactionConfig/vgc_f.json", FactionsDef.nexConfigPath("vgc_f"), "the config's path");
    }

    public static void withNexerelinTheConfiguredFactionIsLeftToIt() throws Exception {
        FactionsDef d = content(",'nexerelin':['vgc_f']").factions.get(0);
        FactionsDef.ShipsFile f = ships("data/config/exerelinFactionConfig/vgc_f.json");
        T.yes(d.leftToNexerelin(rel(d, "vgc_f", "hegemony"), true, f), "a plain relation of a listed faction");
        T.yes(d.leftToNexerelin(rel(d, "vgc_f", "pirates"), true, f), "all of them");
        T.no(d.leftToNexerelin(rel(d, "vgc_f", "hmi"), true, f), "a relation with mod conditions isn't in the config: SagaLib sets it");
        T.no(d.leftToNexerelin(rel(d, "vgc_g", "hegemony"), true, f), "a faction that isn't listed");
    }

    public static void withoutNexerelinNothingChanges() throws Exception {
        FactionsDef d = content(",'nexerelin':['vgc_f']").factions.get(0);
        FactionsDef.ShipsFile f = ships("data/config/exerelinFactionConfig/vgc_f.json");
        for (FactionsDef.Relation r : d.relations) T.no(d.leftToNexerelin(r, false, f), "SagaLib sets " + r.faction + "/" + r.other);
    }

    public static void aMissingConfigFileIsNotTrusted() throws Exception {
        FactionsDef d = content(",'nexerelin':['vgc_f']").factions.get(0);
        T.no(d.leftToNexerelin(rel(d, "vgc_f", "hegemony"), true, ships()), "the mod doesn't ship the file: SagaLib sets the relation");
    }

    public static void olderFilesHaveNoList() throws Exception {
        Content c = content("");
        FactionsDef d = c.factions.get(0);
        T.yes(d.nexerelin.isEmpty(), "no list");
        T.no(d.leftToNexerelin(rel(d, "vgc_f", "hegemony"), true, ships("data/config/exerelinFactionConfig/vgc_f.json")), "everything as before");
    }

    public static void aBadListIsReported() throws Exception {
        Content c = content(",'nexerelin':'vgc_f'");
        T.eq(1, c.problems.errors(), "not a list: " + c.problems.all());
    }
}
