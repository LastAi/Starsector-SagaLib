package lastai.sagalib.test;

import lastai.sagalib.core.Fights;
import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.Problems;
import lastai.sagalib.model.EncounterDef;
import lastai.sagalib.model.QuestDef;

/** SagaLib 1.4: a quest's "fights" (SF_Fight &lt;quest&gt; &lt;node&gt;), the pure parts. */
public final class FightTests {
    static final String HEAD = "{'format':1,'id':'hel_persea','title':'Persea','kind':'quest','stages':[{'id':'go','title':'Go'}],";

    static QuestDef parse(String fights, Problems p) throws Exception {
        return QuestDef.parse(Fx.json(HEAD + "'fights':" + fights + "}"), new JsonReader("persea.json", p));
    }

    public static void readsFights() throws Exception {
        QuestDef q = Fx.quest(HEAD + "'fights':["
                + "{'node':'vault','won':'hel_persea_vault_won','left':'hel_persea_vault_left','engage':'Engage the Custodian\\u0027s defences',"
                + " 'fleet':{'faction':'hel_warden','type':'patrolMedium','size':'fp','points':160,'quality':1,'name':'The Custodian','flagshipCommander':'hel_warden_ai'}},"
                + "{'node':'gate','fleet':{'faction':'remnant','size':'explicit','variants':['radiant_Standard','fulgent_Standard'],"
                + " 'shipNames':['Custodian',''],'flagship':0,'template':'hel_custodian','captainDiesOnDefeat':true}}]}");
        T.eq(2, q.fights.size(), "two fights");
        QuestDef.Fight v = q.fight("vault");
        T.eq("hel_persea_vault_won", v.won, "won trigger");
        T.eq("hel_persea_vault_left", v.left, "left trigger");
        T.eq("Engage the Custodian's defences", Fights.engageText(v), "engage text");
        EncounterDef f = v.fleet;
        T.eq("hel_warden", f.faction, "faction");
        T.eq("patrolMedium", f.fleetType, "type");
        T.eq("fp", f.size, "size");
        T.eq(160f, f.points, "points");
        T.eq(1f, f.quality, "quality");
        T.eq("The Custodian", f.fleetName, "name");
        T.eq("hel_warden_ai", f.flagshipCommander, "commander");
        T.eq("hel_persea_vault", f.id, "the fleet's id for the log");

        QuestDef.Fight g = q.fight("gate");
        T.eq("hel_persea_gate_won", g.won, "missing won: <quest>_<node>_won");
        T.eq("hel_persea_gate_left", g.left, "missing left: <quest>_<node>_left");
        T.eq(Fights.DEFAULT_ENGAGE, Fights.engageText(g), "missing engage: vanilla's text");
        T.eq("Engage the automated defenses", Fights.DEFAULT_ENGAGE, "vanilla's words");
        T.yes(g.fleet.explicit(), "explicit");
        T.eq(2, g.fleet.variants.size(), "variants");
        T.eq("Custodian", g.fleet.shipNames.get(0), "ship name");
        T.eq(0, g.fleet.flagship, "flagship");
        T.eq("hel_custodian", g.fleet.template, "template id (defeats counted)");
        T.yes(g.fleet.captainDiesOnDefeat, "captain dies");
        T.eq(null, g.fleet.quality, "no quality: the faction's");
    }

    public static void templateSize() throws Exception {
        // a named fleet written resolved with size "template": read as explicit
        Problems p = new Problems();
        QuestDef q = parse("[{'node':'a','fleet':{'faction':'remnant','size':'template','template':'hel_custodian','variants':['radiant_Standard']}}]", p);
        T.yes(p.isEmpty(), "no problems: " + p.all());
        T.yes(q.fight("a").fleet.explicit(), "template with variants = explicit");
        T.eq("hel_custodian", q.fight("a").fleet.template, "template kept");

        // the template's id alone: SagaLib has no fleet templates, so no fleet
        p = new Problems();
        q = parse("[{'node':'a','fleet':{'faction':'remnant','size':'template','template':'hel_custodian'}}]", p);
        T.eq(null, q.fight("a"), "skipped");
        T.yes(p.has("template"), "error names the template: " + p.all());
    }

    public static void badFights() throws Exception {
        Problems p = new Problems();
        QuestDef q = parse("[{'node':'a','fleet':{'faction':'pirates','size':'fp','points':50}},"
                + "{'node':'a','fleet':{'faction':'pirates','size':'fp','points':60}},"
                + "{'fleet':{'faction':'pirates'}},"
                + "{'node':'b'},"
                + "{'node':'c','fleet':{'size':'fp'}},"
                + "{'node':'d','fleet':{'faction':'pirates','size':'explicit'}},"
                + "{'node':'e','fleet':{'faction':'pirates','size':'huge'}},"
                + "{'node':'x y','fleet':{'faction':'pirates'}}]", p);
        T.eq(1, q.fights.size(), "only the first a: " + p.all());
        T.eq(50f, q.fight("a").fleet.points, "the first a kept");
        T.eq(7, p.errors(), "one error each: " + p.all());
        T.yes(p.has("listed twice"), "duplicate node");
        T.yes(p.has("at least one variant"), "explicit without variants");
        T.yes(p.has("unknown size"), "unknown size");

        // no fights: an empty list, as before 1.4
        T.eq(0, Fx.basic().fights.size(), "no fights");
    }

    public static void findAndKeys() throws Exception {
        QuestDef q = Fx.quest(HEAD + "'fights':[{'node':'vault','fleet':{'faction':'pirates'}}]}");
        T.yes(Fights.find(Fx.map(q), "hel_persea", "vault") != null, "found");
        T.eq(null, Fights.find(Fx.map(q), "hel_persea", "nope"), "no such node");
        T.eq(null, Fights.find(Fx.map(q), "nope", "vault"), "no such quest");
        T.eq(null, Fights.problem(Fx.map(q), "hel_persea", "vault"), "no problem");
        T.yes(Fights.problem(Fx.map(q), "nope", "vault").contains("unknown quest"), "unknown quest");
        T.yes(Fights.problem(Fx.map(q), "hel_persea", "nope").contains("no fight at node"), "unknown node");
        T.eq("$sagaforge_fight_hel_persea_vault", Fights.fleetKey("hel_persea", "vault"), "the place's memory key");
        T.eq(10f, Fights.KEEP_DAYS, "kept 10 days, as vanilla");
    }

    public static void afterTheFight() {
        T.eq(Fights.After.WON, Fights.after(true, true, true, true), "won outright");
        T.eq(Fights.After.KEEP, Fights.after(false, true, true, false), "fought, ships lost: kept");
        T.eq(Fights.After.KEEP, Fights.after(false, true, false, true), "fought, a ship needs repairs: kept");
        T.eq(Fights.After.LEFT, Fights.after(false, true, false, false), "fought, no damage: not kept");
        T.eq(Fights.After.LEFT, Fights.after(false, false, true, true), "never engaged: not kept");
    }
}
