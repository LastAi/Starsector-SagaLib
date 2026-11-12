package lastai.sagalib.test;

import lastai.sagalib.core.Conditions;
import lastai.sagalib.core.Effects;
import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.Memory;
import lastai.sagalib.core.Problems;
import lastai.sagalib.model.QuestDef;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixtures shared by the tests. */
final class Fx {
    private Fx() {}

    /** JSON written with single quotes, for readable fixtures. */
    static JSONObject json(String singleQuoted) throws Exception {
        return new JSONObject(singleQuoted.replace('\'', '"'));
    }

    /** A quest that must parse without any problem. */
    static QuestDef quest(String singleQuoted) throws Exception {
        Problems p = new Problems();
        QuestDef q = QuestDef.parse(json(singleQuoted), new JsonReader("test.json", p));
        T.yes(p.isEmpty(), "no problems in the fixture: " + p.all());
        return q;
    }

    /** Two stages (find, return), a number variable pay = 1, a reward "done". */
    static QuestDef basic() throws Exception {
        return quest("{'format':1,'id':'vgc_q','title':'Q','kind':'quest',"
                + "'stages':[{'id':'find','title':'Find it','kind':'normal'},{'id':'return','title':'Return','kind':'normal','renamedFrom':['back']},"
                + "{'id':'won','title':'Won','kind':'success'},{'id':'lost','title':'Lost','kind':'fail'}],"
                + "'variables':[{'name':'pay','type':'number','initial':'1'},{'name':'ok','type':'bool','initial':'true'},{'name':'who','type':'string','initial':'Reyes'}],"
                + "'rewards':[{'id':'done','credits':1000,'rep':[{'faction':'hegemony','amount':5}],'personRep':[{'person':'vgc_reyes','amount':-3}],"
                + "'items':[{'item':'food','qty':2}],'ships':['buffalo_Standard'],'xp':500,'storyPoints':1}]}");
    }

    static Map<String, QuestDef> map(QuestDef... qs) {
        Map<String, QuestDef> m = new LinkedHashMap<String, QuestDef>();
        for (QuestDef q : qs) m.put(q.id, q);
        return m;
    }

    static Conditions conditions(Memory m) { return new Conditions(m, null); }

    /** Records what a reward did, in order. */
    static final class RecordingEffects implements Effects {
        long credits;
        final List<String> log = new ArrayList<String>();

        @Override
        public void credits(long amount) {
            credits += amount;
            log.add("credits " + amount);
        }

        @Override
        public void factionRep(String faction, double points) { log.add("rep " + faction + " " + points); }

        @Override
        public void personRep(String person, double points) { log.add("personRep " + person + " " + points); }

        @Override
        public void item(String item, int qty) { log.add("item " + item + " " + qty); }

        @Override
        public void ship(String variant) { log.add("ship " + variant); }

        @Override
        public void xp(long xp) { log.add("xp " + xp); }

        @Override
        public void storyPoints(int points) { log.add("storyPoints " + points); }
    }
}
