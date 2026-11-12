package lastai.sagalib.test;

import lastai.sagalib.core.Conditions;
import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.Phrases;
import lastai.sagalib.core.Problems;
import lastai.sagalib.model.PhraseDef;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Phrase selection: first match, otherwise, random. */
public final class PhraseTests {
    static PhraseDef phrase(String singleQuoted) throws Exception {
        Problems p = new Problems();
        PhraseDef d = PhraseDef.parse(Fx.json(singleQuoted), "$", new JsonReader("t", p));
        T.yes(p.isEmpty(), "fixture: " + p.all());
        return d;
    }

    public static void firstMatch() throws Exception {
        PhraseDef p = phrase("{'token':'$t','mode':'first-match','cases':[{'when':['$player.commissionFactionId == vgc'],'text':'Commander'},"
                + "{'when':['$global.met','!$global.rude'],'text':'Captain'},{'when':['$global.met'],'text':'You'}],'otherwise':'Spacer'}");
        MapMemory m = new MapMemory();
        Conditions c = Fx.conditions(m);
        Random r = new Random(1);
        T.eq("Spacer", Phrases.text(p, c, r), "otherwise");
        T.eq("otherwise", Phrases.choose(p, c, r).chose, "chose otherwise");
        m.set("$global.met", true);
        T.eq("Captain", Phrases.text(p, c, r), "all conditions of case 2");
        T.eq("case 2", Phrases.choose(p, c, r).chose, "chose case 2");
        m.set("$global.rude", true);
        T.eq("You", Phrases.text(p, c, r), "case 3");
        m.set("$player.commissionFactionId", "vgc");
        T.eq("Commander", Phrases.text(p, c, r), "first case wins");
    }

    public static void noOtherwiseIsEmpty() throws Exception {
        PhraseDef p = phrase("{'token':'$t','cases':[{'when':['$global.x'],'text':'X'}]}");
        T.eq("", Phrases.text(p, Fx.conditions(new MapMemory()), new Random()), "empty");
        PhraseDef always = phrase("{'token':'$t','cases':[{'when':[],'text':'Always'}],'otherwise':'never'}");
        T.eq("Always", Phrases.text(always, Fx.conditions(new MapMemory()), new Random()), "a case without conditions always holds");
    }

    public static void random() throws Exception {
        PhraseDef p = phrase("{'token':'$t','mode':'random','cases':[{'when':['$global.never'],'text':'A'},{'when':[],'text':'B'}],'otherwise':'C'}");
        Set<String> seen = new HashSet<String>();
        Random r = new Random(42);
        for (int i = 0; i < 200; i++) seen.add(Phrases.text(p, Fx.conditions(new MapMemory()), r));
        T.eq(3, seen.size(), "every case and otherwise, ignoring conditions (as Play mode): " + seen);
        PhraseDef none = phrase("{'token':'$t','mode':'random','cases':[]}");
        T.eq("", Phrases.text(none, Fx.conditions(new MapMemory()), r), "nothing to pick");
        PhraseDef emptyOtherwise = phrase("{'token':'$t','mode':'random','cases':[{'when':[],'text':'A'}],'otherwise':''}");
        for (int i = 0; i < 20; i++) T.eq("A", Phrases.text(emptyOtherwise, Fx.conditions(new MapMemory()), r), "empty otherwise isn't a variant");
    }
}
