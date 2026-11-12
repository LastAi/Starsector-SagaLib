package lastai.sagalib.model;

import lastai.sagalib.core.JsonReader;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One quest file (docs/FORMAT.md, "Quest JSON"). Definitions only: never saved. */
public final class QuestDef {
    public static final class Start {
        /** contact, bar, trigger or manual. */
        public String kind;
        public String person;
        public double freq;
        public String minRep;
        public String offerText;
        public String optionText;
    }

    public static final class Stage {
        public String id;
        public String title;
        public String journal;
        public String nextStep;
        /** An entity, market or person id marked on the map during this stage ("" = none). */
        public String marker;
        /** normal, success or fail. */
        public String kind;
        public final List<String> renamedFrom = new ArrayList<String>();
    }

    public static final class Variable {
        public String name;
        /** number, string, bool, entity, person (target is reserved). */
        public String type;
        public String initial;
    }

    public static final class Amount {
        public final String id;
        public final double amount;

        public Amount(String id, double amount) {
            this.id = id;
            this.amount = amount;
        }
    }

    public static final class Reward {
        public String id;
        public long credits;
        public final List<Amount> rep = new ArrayList<Amount>();
        public final List<Amount> personRep = new ArrayList<Amount>();
        /** Item id and quantity (commodities, special items, weapons, fighter wings). */
        public final List<Amount> items = new ArrayList<Amount>();
        public final List<String> ships = new ArrayList<String>();
        public long xp;
        public int storyPoints;
    }

    public static final class Timer {
        public String node;
        public String trigger;
        public double days;
        /** The step the quest waits at (optional; null = the step it is at when the timer starts). */
        public String stage;
    }

    public static final class EncounterUse {
        public String node;
        public String encounter;
        public String defeated;
        public String avoided;
    }

    public String id;
    public String title;
    /** quest (a vanilla hub mission) or dialog (only inline phrases). */
    public String kind;
    public String icon;
    public boolean repeatable;
    public double timeLimitDays;
    public Start start = new Start();
    public final List<String> requires = new ArrayList<String>();
    public final List<Stage> stages = new ArrayList<Stage>();
    public final List<Variable> variables = new ArrayList<Variable>();
    public final List<Reward> rewards = new ArrayList<Reward>();
    public final List<Timer> timers = new ArrayList<Timer>();
    public final List<EncounterUse> encounters = new ArrayList<EncounterUse>();
    public final List<String> arrivals = new ArrayList<String>();
    public final List<PhraseDef> phrases = new ArrayList<PhraseDef>();
    public final List<String> flags = new ArrayList<String>();
    /** Where it was read ("data/sagaforge/quests/x.json (mod vgc)"). */
    public String source;

    public Stage stage(String stageId) {
        if (stageId == null) return null;
        for (Stage s : stages) if (s.id.equals(stageId)) return s;
        return null;
    }

    public Reward reward(String rewardId) {
        for (Reward r : rewards) if (r.id.equals(rewardId)) return r;
        return null;
    }

    public boolean isMission() { return "quest".equals(kind); }

    /** Reads a quest file's root object; null (problems recorded) if it can't be used at all. */
    public static QuestDef parse(JSONObject o, JsonReader r) {
        String path = "$";
        if (!r.checkFormat(o)) return null;
        QuestDef q = new QuestDef();
        q.source = r.file;
        q.id = r.str(o, "id", path, true, null);
        if (q.id == null) return null;
        if (q.id.isEmpty() || q.id.contains(" ")) {
            r.error("$.id", "\"" + q.id + "\" is not a usable quest id");
            return null;
        }
        q.title = r.str(o, "title", path, false, q.id);
        q.kind = r.str(o, "kind", path, false, "quest");
        if (!"quest".equals(q.kind) && !"dialog".equals(q.kind)) {
            r.error("$.kind", "unknown kind \"" + q.kind + "\" (quest or dialog)");
            return null;
        }
        q.icon = r.str(o, "icon", path, false, "");
        Boolean rep = r.bool(o, "repeatable", path, false);
        q.repeatable = rep != null && rep;
        Double tl = r.num(o, "timeLimitDays", path, 0d);
        q.timeLimitDays = tl == null ? 0 : tl;

        JSONObject s = r.obj(o, "start", path, false);
        if (s != null) {
            String sp = "$.start";
            q.start.kind = r.str(s, "kind", sp, false, "manual");
            q.start.person = r.str(s, "person", sp, false, "");
            Double f = r.num(s, "freq", sp, 0d);
            q.start.freq = f == null ? 0 : f;
            q.start.minRep = r.str(s, "minRep", sp, false, "");
            q.start.offerText = r.str(s, "offerText", sp, false, "");
            q.start.optionText = r.str(s, "optionText", sp, false, "");
        } else {
            q.start.kind = "manual";
            q.start.person = "";
        }
        q.requires.addAll(r.strings(o, "requires", path));

        for (JsonReader.Indexed e : r.objects(o, "stages", path)) {
            Stage st = new Stage();
            st.id = r.str(e.json, "id", e.path, true, null);
            if (st.id == null) continue;
            if (q.stage(st.id) != null) {
                r.error(JsonReader.at(e.path, "id"), "stage \"" + st.id + "\" is listed twice; the second one is skipped");
                continue;
            }
            st.title = r.str(e.json, "title", e.path, false, st.id);
            st.journal = r.str(e.json, "journal", e.path, false, "");
            st.nextStep = r.str(e.json, "nextStep", e.path, false, "");
            st.marker = r.str(e.json, "marker", e.path, false, "");
            st.kind = r.str(e.json, "kind", e.path, false, "normal");
            if (!"normal".equals(st.kind) && !"success".equals(st.kind) && !"fail".equals(st.kind)) {
                r.error(JsonReader.at(e.path, "kind"), "unknown stage kind \"" + st.kind + "\" (normal, success or fail); using normal");
                st.kind = "normal";
            }
            st.renamedFrom.addAll(r.strings(e.json, "renamedFrom", e.path));
            q.stages.add(st);
        }

        for (JsonReader.Indexed e : r.objects(o, "variables", path)) {
            Variable v = new Variable();
            v.name = r.str(e.json, "name", e.path, true, null);
            if (v.name == null) continue;
            v.type = r.str(e.json, "type", e.path, false, "string");
            v.initial = r.str(e.json, "initial", e.path, false, "");
            q.variables.add(v);
        }

        for (JsonReader.Indexed e : r.objects(o, "rewards", path)) {
            Reward rw = parseReward(e.json, e.path, r);
            if (rw != null) q.rewards.add(rw);
        }

        for (JsonReader.Indexed e : r.objects(o, "timers", path)) {
            Timer t = new Timer();
            t.node = r.str(e.json, "node", e.path, false, "");
            t.trigger = r.str(e.json, "trigger", e.path, true, null);
            Double d = r.num(e.json, "days", e.path, 0d);
            if (t.trigger == null || d == null) continue;
            t.days = d;
            t.stage = r.str(e.json, "stage", e.path, false, "");
            if (t.stage.isEmpty()) t.stage = null;
            if (t.node.isEmpty()) t.node = t.trigger;
            q.timers.add(t);
        }
        for (JsonReader.Indexed e : r.objects(o, "encounters", path)) {
            EncounterUse u = new EncounterUse();
            u.node = r.str(e.json, "node", e.path, false, "");
            u.encounter = r.str(e.json, "encounter", e.path, true, null);
            if (u.encounter == null) continue;
            u.defeated = r.str(e.json, "defeated", e.path, false, "");
            u.avoided = r.str(e.json, "avoided", e.path, false, "");
            q.encounters.add(u);
        }
        q.arrivals.addAll(r.strings(o, "arrivals", path));
        // "targets" is reserved for random targets (after 1.0): format 1 writes [] and it is ignored.
        for (JsonReader.Indexed e : r.objects(o, "phrases", path)) {
            PhraseDef p = PhraseDef.parse(e.json, e.path, r);
            if (p != null) q.phrases.add(p);
        }
        q.flags.addAll(r.strings(o, "flags", path));
        return q;
    }

    static Reward parseReward(JSONObject o, String path, JsonReader r) {
        Reward rw = new Reward();
        rw.id = r.str(o, "id", path, true, null);
        if (rw.id == null) return null;
        Double credits = r.num(o, "credits", path, 0d);
        rw.credits = credits == null ? 0 : Math.round(credits);
        for (JsonReader.Indexed e : r.objects(o, "rep", path)) {
            String f = r.str(e.json, "faction", e.path, true, null);
            Double a = r.num(e.json, "amount", e.path, null);
            if (a == null && f != null) r.error(JsonReader.at(e.path, "amount"), "missing (points, 5 = +0.05)");
            if (f != null && a != null) rw.rep.add(new Amount(f, a));
        }
        for (JsonReader.Indexed e : r.objects(o, "personRep", path)) {
            String p = r.str(e.json, "person", e.path, true, null);
            Double a = r.num(e.json, "amount", e.path, null);
            if (a == null && p != null) r.error(JsonReader.at(e.path, "amount"), "missing (points, 5 = +0.05)");
            if (p != null && a != null) rw.personRep.add(new Amount(p, a));
        }
        for (JsonReader.Indexed e : r.objects(o, "items", path)) {
            String i = r.str(e.json, "item", e.path, true, null);
            Double q = r.num(e.json, "qty", e.path, 1d);
            if (i != null && q != null) rw.items.add(new Amount(i, q));
        }
        rw.ships.addAll(r.strings(o, "ships", path));
        Double xp = r.num(o, "xp", path, 0d);
        rw.xp = xp == null ? 0 : Math.round(xp);
        Double sp = r.num(o, "storyPoints", path, 0d);
        rw.storyPoints = sp == null ? 0 : (int) Math.round(sp);
        return rw;
    }
}
