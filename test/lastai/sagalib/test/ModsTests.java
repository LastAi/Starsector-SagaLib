package lastai.sagalib.test;

import lastai.sagalib.SagaGates;
import lastai.sagalib.core.Content;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.ModGate;
import lastai.sagalib.core.Mods;
import lastai.sagalib.model.FactionsDef;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** SagaLib 1.3: mods.json, mod conditions on every content kind, the start record (the golden case mods.json covers the rules). */
public final class ModsTests {
    static Content content() throws Exception {
        ContentTests.FakeSource src = new ContentTests.FakeSource()
                .row("vgc_mods", "mods", "data/sagaforge/mods.json", "vgc")
                .row("vgc_q", "quest", "data/sagaforge/quests/vgc_q.json", "vgc")
                .row("vgc_people", "people", "data/sagaforge/people/vgc_people.json", "vgc")
                .row("vgc_factions", "factions", "data/sagaforge/factions.json", "vgc")
                .row("vgc_sys", "system", "data/sagaforge/systems/vgc_sys.json", "vgc")
                .row("vgc_enc", "encounter", "data/sagaforge/encounters/vgc_enc.json", "vgc")
                .file("vgc", "data/sagaforge/mods.json", "{'format':1,'mod':'vgc','name':'Vanguard','prefix':'vgc_','mods':["
                        + "{'id':'HMI','name':'Hazard Mining','use':'optional'},{'id':'crewr','use':'incompatible','reason':'crew'},{'id':'lw_lazylib','use':'required'},{'id':'x','use':'maybe'}]}")
                .file("vgc", "data/sagaforge/quests/vgc_q.json", "{'format':1,'id':'vgc_q','title':'Q','kind':'quest','modsWith':['HMI'],'modsWithout':['crewr'],'stages':[]}")
                .file("vgc", "data/sagaforge/people/vgc_people.json", "{'format':1,'people':[{'id':'vgc_a','faction':'hegemony','modsAny':[['HMI','uaf']]},{'id':'vgc_b','faction':'hegemony'}]}")
                .file("vgc", "data/sagaforge/factions.json", "{'format':1,'relations':{'vgc_f':{'hegemony':0.5,'hmi':{'value':-0.5,'modsWith':['HMI']}}},"
                        + "'scripted':{'vgc_f':[{'op':'hideFromIntelTab','modsWithout':['HMI']},{'op':'remove','path':'knownShips.hulls','value':'onslaught'}]}}")
                .file("vgc", "data/sagaforge/systems/vgc_sys.json", "{'format':1,'id':'vgc_sys','name':'S','kind':'new','hyperspace':[0,0],'star':{'type':'star_yellow','radius':500,'corona':300},"
                        + "'entities':[{'id':'vgc_p','kind':'planet','type':'barren','name':'P','orbit':{'focus':'star','radius':2000,'angle':0,'days':100},'modsWith':['HMI']}],'addToExistingSaves':false}")
                .file("vgc", "data/sagaforge/encounters/vgc_enc.json", "{'format':1,'id':'vgc_enc','name':'E','fleet':{'faction':'pirates','type':'patrolSmall','size':'fp','points':20},"
                        + "'spawn':{'at':'vgc_p','when':'new-game'},'behavior':'patrol','flags':{},'outcomes':{},'modsWithout':['HMI']}");
        return Content.load(src);
    }

    public static void modListsAndGatesAreRead() throws Exception {
        Content c = content();
        Mods.ModList l = c.modLists.get("vgc");
        T.yes(l != null, "mods.json read");
        T.eq("vgc_", l.prefix, "prefix");
        T.eq(3, l.entries.size(), "the unknown use is skipped");
        T.eq(1, c.problems.errors(), "and reported: " + c.problems.all());
        T.eq("crew", l.entry("crewr").reason, "reason");
        T.eq(Arrays.asList("HMI"), c.quest("vgc_q").gate.with, "quest modsWith");
        T.eq("vgc", c.quest("vgc_q").gate.owner, "owned by the mod that ships it");
        T.eq(Arrays.asList(Arrays.asList("HMI", "uaf")), c.people.get("vgc_a").gate.any, "person modsAny");
        T.yes(c.people.get("vgc_b").gate.isEmpty(), "no conditions");
        T.eq(Arrays.asList("HMI"), c.systems.get("vgc_sys").entities.get(0).gate.with, "object modsWith");
        T.eq(Arrays.asList("HMI"), c.encounters.get("vgc_enc").gate.without, "encounter modsWithout");
        FactionsDef f = c.factions.get(0);
        T.eq(2, f.relations.size(), "both relation forms");
        FactionsDef.Relation hmi = f.relations.get(0).other.equals("hmi") ? f.relations.get(0) : f.relations.get(1);
        T.eq(-0.5f, hmi.value, "the object form's value");
        T.eq(Arrays.asList("HMI"), hmi.gate.with, "the object form's condition");
        T.eq("hideFromIntelTab", f.scripted.get(0).op, "hide from the intel tab");
        T.eq(Arrays.asList("HMI"), f.scripted.get(0).gate.without, "with modsWithout");
        Map<String, ModGate> all = SagaGates.all(c);
        T.yes(all.containsKey("quest vgc_q") && all.containsKey("person vgc_a") && all.containsKey("object vgc_p") && all.containsKey("encounter vgc_enc")
                && all.containsKey("relation vgc_f/hmi") && all.containsKey("faction change vgc_f hideFromIntelTab"), "every gated item listed: " + all.keySet());
        T.no(all.containsKey("person vgc_b"), "items without conditions aren't");
    }

    public static void gatesFollowTheRecord() throws Exception {
        Content c = content();
        final Set<String> on = new HashSet<String>(Arrays.asList("HMI"));
        Mods.setup(new Mods.Enabled() { public boolean isEnabled(String id) { return on.contains(id); } }, c.modLists);
        try {
            MapMemory m = new MapMemory();
            Mods.record(m, true);
            T.yes(Mods.allowed(m, c.quest("vgc_q").gate, "quest", "vgc_q"), "with HMI, without crewr");
            T.no(Mods.allowed(m, c.encounters.get("vgc_enc").gate, "encounter", "vgc_enc"), "only without HMI");
            T.yes(Mods.skipped().containsKey("encounter vgc_enc"), "remembered for the dev menu");
            on.add("crewr");
            T.yes(Mods.allowed(m, c.quest("vgc_q").gate, "quest", "vgc_q"), "a mod enabled after the start does not count (the record says off)");
            T.eq("vgc", Mods.ownerOfRule("vgc_q_offer"), "rule ids by prefix");
            T.eq(null, Mods.ownerOfRule("other_rule"), "no owner");
        } finally {
            Mods.setup(new Mods.Enabled() { public boolean isEnabled(String id) { return false; } }, null);
        }
    }
}
