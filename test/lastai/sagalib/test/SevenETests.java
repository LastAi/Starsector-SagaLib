package lastai.sagalib.test;

import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.Problems;
import lastai.sagalib.model.PoolDef;
import lastai.sagalib.model.ScatterDef;
import lastai.sagalib.model.SystemDef;

/** SagaLib 1.2, Phase 7e: encounter pools, entity loot and defenders, the deciv guard, scatter. */
public final class SevenETests {
    public static void encounterPools() throws Exception {
        Problems p = new Problems();
        PoolDef d = PoolDef.parse(Fx.json("{'format':1,'id':'svf_enc','use':'encounters','points':['slipstream','outside_system'],'frequency':6,"
                + "'entries':[{'id':'svf_raiders','weight':3}]}"), new JsonReader("t", p));
        T.yes(p.isEmpty(), "parses: " + p.all());
        T.yes(d.isEncounters(), "an encounter pool");
        T.eq(2, d.points.size(), "two point types");
        T.eq(6f, d.frequency, "frequency");
        T.eq("ep_type_slipstream", PoolDef.pointType("slipstream"), "vanilla's point type");
        PoolDef b = PoolDef.parse(Fx.json("{'format':1,'id':'svf_b','entries':[{'id':'a'}]}"), new JsonReader("t", new Problems()));
        T.eq("branch", b.use, "default use");
    }

    public static void lootDefendersAndDeciv() throws Exception {
        Problems p = new Problems();
        SystemDef s = SystemDef.parse(Fx.json("{'format':1,'id':'svf_sys','name':'S','kind':'new','hyperspace':[0,0],'star':{'type':'star_yellow','radius':500},"
                + "'entities':[{'id':'svf_cache','kind':'custom','type':'supply_cache','name':'Cache','orbit':{'focus':'star','radius':2000,'angle':0,'days':60},"
                + "'ruinsUnexplored':true,'loot':[{'group':'svf_loot','chances':3},{'group':'svf_val','value':5000}],'defenders':{'faction':'pirates','minStr':20,'maxStr':40}},"
                + "{'id':'svf_st','kind':'station','type':'station_side00','name':'St','orbit':{'focus':'star','radius':3000,'angle':0,'days':60},"
                + "'market':{'faction':'independent','size':3,'protectFromDeciv':true}}]}"), new JsonReader("t", p));
        T.yes(p.errors() == 0, "parses: " + p.all());
        SystemDef.Entity c = s.entities.get(0);
        T.eq(2, c.loot.size(), "two loot rows");
        T.yes(c.ruinsUnexplored, "ruins stay unexplored");
        T.eq(3, c.loot.get(0).chances, "chances");
        T.eq(5000, c.loot.get(1).value, "value");
        T.eq("pirates", c.defenders.faction, "defenders' faction");
        T.eq(40f, c.defenders.maxStr, "max strength");
        T.eq(1f, c.defenders.prob, "default probability");
        T.yes(s.entities.get(1).market.protectFromDeciv, "protected from deciv");
    }

    public static void rewardLoot() throws Exception {
        lastai.sagalib.model.QuestDef q = Fx.quest("{'format':1,'id':'vgc_q','title':'Q','kind':'quest','rewards':[{'id':'r','credits':100,'items':[{'item':'food','qty':1}],'loot':[{'group':'rare_tech','chances':2},{'group':'basic','value':3000}]}]}");
        Fx.RecordingEffects fx = new Fx.RecordingEffects();
        lastai.sagalib.core.QuestLogic.apply(q.reward("r"), fx);
        T.eq("[credits 100, item food 1, loot rare_tech 2 0, loot basic 0 3000]", fx.log.toString(), "loot after items, in order");
    }

    public static void scatter() throws Exception {
        Problems p = new Problems();
        ScatterDef s = ScatterDef.parse(Fx.json("{'format':1,'id':'svf_caches','count':3,'addToExistingSaves':true,"
                + "'target':{'pick':'planet','require':[{'c':'plUnpopulated','args':[]}],'prefer':[]},"
                + "'from':'jangala','entity':{'type':'supply_cache','name':'Hidden cache'},'loot':[{'group':'svf_loot','chances':2}]}"), new JsonReader("t", p));
        T.yes(p.isEmpty(), "parses: " + p.all());
        T.eq(3, s.count, "count");
        T.yes(s.addToExistingSaves, "existing saves");
        T.eq("planet", s.target.pick, "pick");
        T.eq(1, s.target.require.size(), "one require");
        T.eq("supply_cache", s.entityType, "entity type");
        T.eq("neutral", s.entityFaction, "default faction");
        T.eq(1, s.loot.size(), "loot");
        T.eq("jangala", s.from, "measured from a place");
    }
}
