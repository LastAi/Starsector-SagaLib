package lastai.sagalib.test;

import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.Problems;
import lastai.sagalib.model.FactionsDef;
import lastai.sagalib.model.PersonDef;
import lastai.sagalib.model.ProfileDef;
import lastai.sagalib.model.QuestDef;

/** Reading each file type; bad values are reported with file + JSON path and skipped. */
public final class JsonTests {
    private static Problems problems;

    private static JsonReader reader() {
        problems = new Problems();
        return new JsonReader("q.json (mod m)", problems);
    }

    public static void fullQuest() throws Exception {
        QuestDef q = Fx.quest("{'_sagaforge':{'generated':'x','hash':'y'},'format':1,'id':'vgc_lost_convoy','title':'The Lost Convoy','kind':'quest',"
                + "'icon':'graphics/icons/missions/data_retrieval.png','repeatable':false,'timeLimitDays':30,"
                + "'start':{'kind':'contact','person':'vgc_reyes','freq':10,'minRep':'FAVORABLE','offerText':'o','optionText':'t'},"
                + "'requires':['SF_QuestDone vgc_other success','$global.vgc_flag'],"
                + "'stages':[{'id':'find','title':'Find the convoy','journal':'Find it.','nextStep':'Go','marker':'vgc_belt','kind':'normal','renamedFrom':['search']}],"
                + "'variables':[{'name':'pay','type':'number','initial':'1'}],'targets':[],"
                + "'rewards':[{'id':'done','credits':60000,'rep':[{'faction':'vgc_vanguard','amount':5}],'personRep':[{'person':'vgc_reyes','amount':5}],"
                + "'items':[{'item':'vgc_black_box','qty':1}],'ships':['buffalo_Standard'],'xp':1000,'storyPoints':1}],"
                + "'timers':[{'node':'wait1','trigger':'vgc_lost_convoy_wait1','days':7}],"
                + "'encounters':[{'node':'ambush','encounter':'vgc_belt_ambush','defeated':'d','avoided':'a'}],'arrivals':['vgc_convoy_wreck'],"
                + "'phrases':[{'token':'$vgc_x','owner':'n','mode':'random','cases':[{'when':[],'text':'A'}],'otherwise':'B'}],"
                + "'flags':['$global.vgc_lost_convoy_catch_asked']}");
        T.eq("vgc_lost_convoy", q.id, "id");
        T.eq(30.0, q.timeLimitDays, "time limit");
        T.eq("vgc_reyes", q.start.person, "start person");
        T.eq(2, q.requires.size(), "requires");
        T.eq("vgc_belt", q.stage("find").marker, "marker");
        T.eq("search", q.stage("find").renamedFrom.get(0), "renamedFrom");
        QuestDef.Reward r = q.reward("done");
        T.eq(60000L, r.credits, "credits");
        T.eq(5.0, r.rep.get(0).amount, "rep");
        T.eq("vgc_black_box", r.items.get(0).id, "item");
        T.eq(1000L, r.xp, "xp");
        T.eq(1, r.storyPoints, "story points");
        T.eq(7.0, q.timers.get(0).days, "timer");
        T.eq("vgc_belt_ambush", q.encounters.get(0).encounter, "encounter");
        T.eq("random", q.phrases.get(0).mode, "phrase");
        T.eq("B", q.phrases.get(0).otherwise, "otherwise");
    }

    public static void badValuesAreReportedWithPaths() throws Exception {
        QuestDef q = QuestDef.parse(Fx.json("{'format':1,'id':'q','stages':[{'id':'a'},{'title':'no id'},'oops',{'id':'a'},{'id':'b','kind':'weird'}],"
                + "'rewards':[{'id':'r','credits':'lots'},{'credits':5}],'variables':[{'type':'number'}],'requires':[1,{'x':1}]}"), reader());
        T.yes(q != null, "the quest is still read");
        T.eq(2, q.stages.size(), "good stages kept: a, b");
        T.eq("normal", q.stage("b").kind, "unknown kind falls back");
        T.yes(problems.has("q.json (mod m) at $.stages[1].id: missing"), "missing id path: " + problems.all());
        T.yes(problems.has("at $.stages[2]: should be an object"), "non-object entry");
        T.yes(problems.has("at $.stages[3].id: stage \"a\" is listed twice"), "duplicate stage");
        T.yes(problems.has("at $.stages[4].kind"), "unknown stage kind");
        T.yes(problems.has("at $.rewards[0].credits: should be a number"), "bad credits");
        T.yes(problems.has("at $.rewards[1].id: missing"), "reward without id");
        T.yes(problems.has("at $.variables[0].name: missing"), "variable without name");
        T.yes(problems.has("at $.requires[1]: should be a string"), "bad requires entry");
        T.eq("1", q.requires.get(0), "numbers are read as text");
        T.eq(1, q.rewards.size(), "reward with bad credits is kept (credits 0), the one without id skipped");
        T.eq(0L, q.rewards.get(0).credits, "credits 0");
    }

    public static void unusableQuests() throws Exception {
        T.eq(null, QuestDef.parse(Fx.json("{'id':'q'}"), reader()), "no format");
        T.yes(problems.has("$.format: missing"), "format missing: " + problems.all());
        T.eq(null, QuestDef.parse(Fx.json("{'format':2,'id':'q'}"), reader()), "format 2");
        T.yes(problems.has("format 2 is not supported"), "format 2 message");
        T.eq(null, QuestDef.parse(Fx.json("{'format':1,'title':'x'}"), reader()), "no id");
        T.eq(null, QuestDef.parse(Fx.json("{'format':1,'id':'has space'}"), reader()), "bad id");
        T.eq(null, QuestDef.parse(Fx.json("{'format':1,'id':'q','kind':'saga'}"), reader()), "bad kind");
        T.yes(problems.has("unknown kind"), "kind message");
    }

    public static void people() throws Exception {
        PersonDef p = PersonDef.parse(Fx.json("{'id':'vgc_reyes','name':{'first':'Ilse','last':'Reyes'},'gender':'FEMALE','faction':'vgc_vanguard',"
                + "'rank':'spaceCommander','post':'stationCommander','portrait':'graphics/portraits/x.png','voice':'soldier','personality':'steady',"
                + "'placement':{'at':'market','market':'jangala','inCommDirectory':true},'contact':{'types':['military'],'importance':'HIGH'},"
                + "'tags':['t1'],'phrases':[{'token':'$vgc_reyes_title','cases':[],'otherwise':'Captain'}],'description':''}"), "$.people[0]", reader());
        T.eq("Ilse", p.firstName, "first");
        T.eq("jangala", p.market, "market");
        T.yes(p.inCommDirectory, "comm directory");
        T.yes(p.isContact(), "contact");
        T.eq("HIGH", p.importance, "importance");
        T.eq("first-match", p.phrases.get(0).mode, "phrase default mode");
        T.yes(problems.isEmpty(), "no problems: " + problems.all());

        p = PersonDef.parse(Fx.json("{'id':'x','faction':'f','gender':'X','placement':{'at':'moon'}}"), "$.people[1]", reader());
        T.eq("ANY", p.gender, "bad gender");
        T.eq("none", p.placementAt, "bad placement");
        T.no(p.isContact(), "not a contact");
        T.yes(problems.has("$.people[1].placement.at"), "placement path: " + problems.all());
        T.eq(null, PersonDef.parse(Fx.json("{'id':'x'}"), "$.people[2]", reader()), "faction required");
        T.yes(problems.has("$.people[2].faction: missing"), "faction path");
    }

    public static void factions() throws Exception {
        FactionsDef f = FactionsDef.parse(Fx.json("{'format':1,'relations':{'vgc':{'hegemony':-0.5,'pirates':2}},"
                + "'scripted':{'hegemony':[{'op':'remove','path':'knownShips.hulls','value':'colossus'},{'op':'add','path':'x','value':'y'}]}}"), reader());
        T.eq(1, f.relations.size(), "relation outside -1..1 skipped");
        T.eq(-0.5f, f.relations.get(0).value, "relation value");
        T.eq(1, f.scripted.size(), "only remove");
        T.eq("colossus", f.scripted.get(0).value, "value");
        T.yes(problems.has("$.relations.vgc.pirates"), "relation path: " + problems.all());
        T.yes(problems.has("$.scripted.hegemony[1].op"), "op path");
    }

    public static void profiles() throws Exception {
        ProfileDef p = ProfileDef.parse(Fx.json("{'id':'p1','name':'Second step','quests':{'vgc_q':{'state':'active','step':'return'},'b':{'state':'weird'}},"
                + "'flags':{'$global.f':'true','n':3},'player':{'credits':5000,'storyPoints':2,'commission':'hegemony','rep':{'hegemony':0.3},"
                + "'cargo':[{'item':'food','qty':10}],'startAt':'jangala'}}"), "$.profiles[0]", reader());
        T.eq("return", p.quests.get("vgc_q").step, "step");
        T.no(p.quests.containsKey("b"), "bad state skipped");
        T.yes(problems.has("$.profiles[0].quests.b.state"), "state path: " + problems.all());
        T.eq("3", p.flags.get("n"), "flags as text");
        T.eq(5000.0, p.credits, "credits");
        T.eq(2, p.storyPoints, "story points");
        T.eq(0.3f, p.rep.get("hegemony"), "rep");
        T.eq(10, p.cargo.get(0).qty, "cargo");
        T.eq("jangala", p.startAt, "startAt");
    }
}
