package lastai.sagalib.test;

import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.Problems;
import lastai.sagalib.core.Targets;
import lastai.sagalib.model.QuestDef;

import java.util.List;

/** Random targets (SagaLib 1.2): the quest JSON, the keys, references and SF_IsTarget's match. */
public final class TargetTests {
    static final String QUEST = "{'format':1,'id':'vgc_q','title':'Q','kind':'quest','stages':[{'id':'go','title':'Go','kind':'normal','marker':'$global.vgc_q_ruin'}],"
            + "'targets':[{'var':'ruin','pick':'planet','systemFirst':true,"
            + "'require':[{'c':'plRuins','args':[]},{'c':'sysWithin','args':['giver',12]}],"
            + "'prefer':[{'c':'plConditions','args':['ANY',['decivilized','ruins_vast']]},{'c':'sysUnexplored','args':[]}]}]}";

    public static void parsesTargets() throws Exception {
        QuestDef q = Fx.quest(QUEST);
        T.eq(1, q.targets.size(), "one target");
        QuestDef.Target t = q.targets.get(0);
        T.eq("ruin", t.var, "var");
        T.eq("planet", t.pick, "pick");
        T.yes(t.systemFirst, "systemFirst");
        T.eq(2, t.require.size(), "two requires");
        T.eq("sysWithin", t.require.get(1).id, "criterion id");
        T.eq(12, ((Number) t.require.get(1).args.get(1)).intValue(), "number argument");
        T.eq("plConditions", t.prefer.get(0).id, "prefers keep their order");
        T.yes(t.prefer.get(0).args.get(1) instanceof List, "a list argument is a list");
        T.eq(2, ((List<?>) t.prefer.get(0).args.get(1)).size(), "two conditions");
    }

    public static void unknownCriterionAndPickAreErrors() throws Exception {
        Problems p = new Problems();
        QuestDef.parse(Fx.json("{'format':1,'id':'vgc_q','title':'Q','kind':'quest','targets':[{'var':'a','pick':'planet','require':[{'c':'plNoSuchThing','args':[]}]},"
                + "{'var':'b','pick':'asteroid','require':[]}]}"), new JsonReader("t.json", p));
        T.eq(2, p.errors(), "an unknown criterion and an unknown pick: " + p.all());
    }

    public static void keysAndReferences() {
        T.eq("$global.vgc_q_ruin", Targets.key("vgc_q", "ruin"), "the target's key");
        T.eq("$global.vgc_q_ruin_name", Targets.factKey("vgc_q", "ruin", "name"), "a fact's key");
        MapMemory m = new MapMemory();
        T.eq(null, Targets.resolve(m, "$global.vgc_q_ruin"), "not picked yet: nothing");
        m.set("$global.vgc_q_ruin", "planet_12");
        T.eq("planet_12", Targets.resolve(m, "$global.vgc_q_ruin"), "picked: its id");
        T.eq("vgc_haven", Targets.resolve(m, "vgc_haven"), "a plain id stays");
    }

    public static void isTargetMatchesEntityMarketOrSystem() {
        T.yes(Targets.matches("planet_12", "planet_12", null, "sys_1"), "the entity");
        T.yes(Targets.matches("market_7", "station_7", "market_7", "sys_1"), "its market");
        T.yes(Targets.matches("sys_1", "planet_9", null, "sys_1"), "anything in a system target");
        T.yes(!Targets.matches("planet_12", "planet_13", null, "sys_1"), "another place");
        T.yes(!Targets.matches(null, "planet_12", null, null), "nothing picked");
    }

    public static void distanceHasOneDecimal() {
        T.eq("8.4", Targets.formatDist(8.4321f), "8.4 ly");
        T.eq("12.0", Targets.formatDist(12f), "12.0 ly");
    }

    public static void detailsAndPerson() throws Exception {
        T.eq("vast", Targets.ruinsSize("ruins_vast"), "ruins size from the condition");
        T.eq("", Targets.ruinsSize(null), "no ruins");
        T.eq("theme_ruins,theme_ruins_main", Targets.themes(java.util.Arrays.asList("has_jump_points", "theme_ruins", "theme_ruins_main")), "theme tags only");
        T.yes(Targets.hasTheme(java.util.Arrays.asList("theme_ruins_main"), "ruins"), "theme_ruins_main is a ruins theme");
        T.yes(!Targets.hasTheme(java.util.Arrays.asList("theme_ruinsx"), "ruins"), "only the family and its sub-tags");
        QuestDef q = Fx.quest("{'format':1,'id':'vgc_q','title':'Q','kind':'quest','targets':[{'var':'post','pick':'market','require':[],'prefer':[],"
                + "'person':{'posts':['portmaster','supplyOfficer'],'rank':'spaceCommander','faction':''}}]}");
        QuestDef.TargetPerson p = q.targets.get(0).person;
        T.eq(2, p.posts.size(), "two posts");
        T.eq("spaceCommander", p.rank, "rank");
        T.eq(null, p.faction, "empty faction = the colony's");
        Problems pr = new Problems();
        QuestDef.parse(Fx.json("{'format':1,'id':'vgc_q','title':'Q','kind':'quest','targets':[{'var':'post','pick':'market','person':{'posts':[]}}]}"), new JsonReader("t.json", pr));
        T.eq(1, pr.errors(), "a person without posts is an error");
    }

    public static void requireOnlyCriteriaAreKnown() {
        T.yes(Targets.CRITERIA.containsAll(Targets.REQUIRE_ONLY), "require-only criteria are criteria");
        T.eq(69, Targets.CRITERIA.size(), "69 criteria: 56 + 13 commodity ones (the editor's catalog has the same ids)");
    }

    /** SagaLib 1.4 (task #75): a commodity target's JSON, its defaults and errors. */
    public static void parsesCommodityTargets() throws Exception {
        QuestDef q = Fx.quest("{'format':1,'id':'vgc_q','title':'Q','kind':'quest','targets':["
                + "{'var':'goods','pick':'commodity','require':[{'c':'cmShort','args':[]},{'c':'cmPriceAtLeast','args':[100]}],'prefer':[{'c':'cmTags','args':['ANY',['expensive']]}],"
                + "'commodity':{'which':'list','ids':['food','IndEvo_parts'],'amount':{'unit':'econ','n':0.25,'perSize':true}}},"
                + "{'var':'plain','pick':'commodity','require':[],'prefer':[]}]}");
        QuestDef.CommodityPick c = q.targets.get(0).commodity;
        T.eq("list", c.which, "which");
        T.eq(2, c.ids.size(), "two ids");
        T.eq("econ", c.unit, "unit");
        T.yes(Math.abs(c.n - 0.25) < 1e-9, "n");
        T.yes(c.perSize, "perSize");
        QuestDef.CommodityPick d = q.targets.get(1).commodity;
        T.yes(d != null, "a commodity pick without the object gets the defaults");
        T.eq("vanilla", d.which, "default: vanilla goods");
        T.eq("econ", d.unit, "default: economy units");
        Problems p = new Problems();
        QuestDef.parse(Fx.json("{'format':1,'id':'vgc_q','title':'Q','kind':'quest','targets':[{'var':'a','pick':'commodity','commodity':{'which':'list','ids':[]}},"
                + "{'var':'b','pick':'commodity','commodity':{'which':'some','amount':{'unit':'tons'}}}]}"), new JsonReader("t.json", p));
        T.eq(3, p.errors(), "a list without ids, an unknown which and an unknown unit: " + p.all());
    }

    /** SagaLib 1.4 (task #75): the vanilla goods default and the amounts from econUnit. */
    public static void commodityAmountsAndDefaults() {
        T.eq(18, lastai.sagalib.core.Commodities.VANILLA_GOODS.size(), "18 vanilla trade goods");
        T.eq(lastai.sagalib.core.Commodities.VANILLA_GOODS, lastai.sagalib.core.Commodities.allowed(null), "no commodity object: the vanilla goods");
        T.yes(!lastai.sagalib.core.Commodities.VANILLA_GOODS.contains("ships") && !lastai.sagalib.core.Commodities.VANILLA_GOODS.contains("alpha_core"), "no meta or nonecon commodity");
        QuestDef.CommodityPick any = new QuestDef.CommodityPick();
        any.which = "any";
        T.eq(null, lastai.sagalib.core.Commodities.allowed(any), "any: no list");
        QuestDef.CommodityPick c = new QuestDef.CommodityPick();
        T.eq(200, lastai.sagalib.core.Commodities.cargoUnits(c, 2000, 5), "0.1 economy units of vanilla food");
        T.eq(20, lastai.sagalib.core.Commodities.cargoUnits(c, 200, 5), "with AoTD ToT's food (x0.1)");
        T.eq(1, lastai.sagalib.core.Commodities.cargoUnits(c, 1, 5), "at least 1");
        c.perSize = true;
        T.eq(1000, lastai.sagalib.core.Commodities.cargoUnits(c, 2000, 5), "x the market's size");
        c.unit = "cargo";
        c.n = 40;
        T.eq(40, lastai.sagalib.core.Commodities.cargoUnits(c, 2000, 5), "fixed cargo units ignore econUnit and size");
        T.eq(5020L, lastai.sagalib.core.Commodities.value(100.4, 50), "value = base price x amount");
    }

    /** SagaLib 1.4 (found in game, task #75): the intel's journal fills $global tokens; unset ones stay. */
    public static void journalFillsGlobalTokens() {
        MapMemory m = new MapMemory();
        m.set("$global.q_goods_amount", Integer.valueOf(75));
        m.set("$global.q_goods_commodity", "supplies");
        m.set("$global.q_goods_dist", Float.valueOf(7.0f));
        T.eq("Deliver 75 supplies, 7 ly. $global.q_nope stays.", lastai.sagalib.core.Values.fillGlobals(m, "Deliver $global.q_goods_amount $global.q_goods_commodity, $global.q_goods_dist ly. $global.q_nope stays."), "filled");
        T.eq("no tokens", lastai.sagalib.core.Values.fillGlobals(m, "no tokens"), "unchanged");
    }

    public static void scatterCantPickACommodity() throws Exception {
        Problems p = new Problems();
        lastai.sagalib.model.ScatterDef.parse(Fx.json("{'format':1,'id':'s','count':1,'target':{'pick':'commodity','require':[],'prefer':[]},'entity':{'type':'supply_cache'}}"), new JsonReader("s.json", p));
        T.eq(1, p.errors(), "a scatter with a commodity pick: " + p.all());
    }
}
