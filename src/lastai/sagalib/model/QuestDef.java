package lastai.sagalib.model;

import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.Targets;
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
        /** SagaLib 1.3 (task #61): the start rule's conditions (option, trigger, interject, bar scene starts); "$id == x" names a place it needs. */
        public final List<String> conditions = new ArrayList<String>();
    }

    /** SagaLib 1.3 (task #61): what happens when one of the quest's markets changes owner (core.Places). */
    public static final class OwnerChange {
        /** keep, fail or stage. */
        public String action;
        public String stage;
        /** Fired after "stage" (the rule runs what follows that Stage node); null = none. */
        public String trigger;
        /** Market or entity ids, or a random target's key ("$global.<quest>_<var>"). */
        public final List<String> markets = new ArrayList<String>();
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
        /** SagaLib 1.2: rolls of drop groups (the mod's or vanilla's). */
        public final List<SystemDef.Loot> loot = new ArrayList<SystemDef.Loot>();
        public String id;
        public long credits;
        public final List<Amount> rep = new ArrayList<Amount>();
        public final List<Amount> personRep = new ArrayList<Amount>();
        /** Item id and quantity (commodities, special items, weapons, fighter wings). */
        public final List<Amount> items = new ArrayList<Amount>();
        public final List<String> ships = new ArrayList<String>();
        public long xp;
        public int storyPoints;
        /** SagaLib 1.3 (task #62): scales {@link #credits} by a mod setting (null = never scaled). */
        public lastai.sagalib.core.Settings.Scale creditsScale;
    }

    public static final class Timer {
        public String node;
        public String trigger;
        public double days;
        /** The step the quest waits at (optional; null = the step it is at when the timer starts). */
        public String stage;
    }

    /**
     * SagaLib 1.3: a Wait node that waits for a MagicLib bounty to end (core.BountyWaits). Each
     * outcome's trigger is fired once the bounty ended that way; an empty one fires nothing.
     */
    public static final class BountyWait {
        public String node;
        public String bounty;
        /** The step the quest waits at (null = any step of the run, once). */
        public String stage;
        public String succeeded = "";
        public String failed = "";
        public String expired = "";
        public String dismissed = "";

        /** The trigger for a bounty state: "" for an end without one, null when the state isn't an end. */
        public String trigger(String state) {
            if (lastai.sagalib.core.Bounties.SUCCEEDED.equals(state)) return succeeded;
            if (lastai.sagalib.core.Bounties.FAILED.equals(state)) return failed;
            if (lastai.sagalib.core.Bounties.EXPIRED.equals(state)) return expired;
            if (lastai.sagalib.core.Bounties.DISMISSED.equals(state)) return dismissed;
            return null;
        }
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
    /** SagaLib 1.3: Wait nodes on a MagicLib bounty's end. */
    public final List<BountyWait> bountyWaits = new ArrayList<BountyWait>();
    public final List<String> arrivals = new ArrayList<String>();
    /** SagaLib 1.1: flags on entities while the quest is at some of its steps. */
    public final List<EntityFlag> entityFlags = new ArrayList<EntityFlag>();
    public final List<PhraseDef> phrases = new ArrayList<PhraseDef>();
    public final List<String> flags = new ArrayList<String>();
    /** SagaLib 1.2: random targets, searched in this order when the quest is offered. */
    public final List<Target> targets = new ArrayList<Target>();

    /** One of vanilla's HubMissionWithSearch criteria (core/Targets.CRITERIA) with its arguments (String, Number, Boolean or List of String). */
    public static final class Criterion {
        public String id;
        public final List<Object> args = new ArrayList<Object>();
    }

    /** A quest variable of type target: what to pick and how (docs/FORMAT.md "Random targets"). */
    public static final class Target {
        public String var;
        /** system | planet | entity | market */
        public String pick;
        public boolean systemFirst;
        public final List<Criterion> require = new ArrayList<Criterion>();
        public final List<Criterion> prefer = new ArrayList<Criterion>();
        /** RC-R4: a person at the picked colony (vanilla findOrCreatePerson), or null. */
        public TargetPerson person;
    }

    /** A person at a target colony: someone with one of the posts, else a new person of the faction (the colony's if empty). */
    public static final class TargetPerson {
        public final List<String> posts = new ArrayList<String>();
        public String rank;
        public String faction;
    }
    /** Where it was read ("data/sagaforge/quests/x.json (mod vgc)"). */
    public String source;
    /** SagaLib 1.3: only with / only without these mods (modsWith, modsWithout, modsAny). */
    public lastai.sagalib.core.ModGate gate = lastai.sagalib.core.ModGate.NONE;
    /** SagaLib 1.3 (task #61): watched markets and the outcome, or null (not watched). */
    public OwnerChange ownerChange;
    /**
     * SagaLib 1.3 (task #65): what skipping this quest on Nexerelin's new-game "skip quests" screen
     * gives besides the memory flags Nexerelin sets from questSkip.json: the chosen ending's rewards and
     * people to show in their comm directory; null = nothing (core.QuestSkip).
     */
    public Skip skip;

    /** SagaLib 1.3 (task #65): see {@link #skip}. */
    public static final class Skip {
        public final List<Reward> rewards = new ArrayList<Reward>();
        public final List<String> unhide = new ArrayList<String>();
    }

    /** SagaLib 1.1: an entity flag set while the quest is active at one of {@link #steps} (vanilla's setFlag(entity, flag, …, stages)). */
    public static final class EntityFlag {
        public String entity;
        /** An entity memory key, "$sagaforge_<quest>_<node>". */
        public String flag;
        public final List<String> steps = new ArrayList<String>();
        /** Also mark the entity important (Misc.makeImportant: it stands out on the map) while set. */
        public boolean important;
    }

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
        q.gate = lastai.sagalib.core.ModGate.parse(o, "$", r);
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
            q.start.conditions.addAll(r.strings(s, "conditions", sp));
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
        for (JsonReader.Indexed e : r.objects(o, "bountyWaits", path)) {
            BountyWait w = new BountyWait();
            w.node = r.str(e.json, "node", e.path, false, "");
            w.bounty = r.str(e.json, "bounty", e.path, true, null);
            if (w.bounty == null || w.bounty.trim().isEmpty()) continue;
            w.bounty = w.bounty.trim();
            w.stage = r.str(e.json, "stage", e.path, false, "");
            if (w.stage.isEmpty()) w.stage = null;
            if (w.node.isEmpty()) w.node = w.bounty;
            w.succeeded = r.str(e.json, "succeeded", e.path, false, "");
            w.failed = r.str(e.json, "failed", e.path, false, "");
            w.expired = r.str(e.json, "expired", e.path, false, "");
            w.dismissed = r.str(e.json, "dismissed", e.path, false, "");
            q.bountyWaits.add(w);
        }
        q.arrivals.addAll(r.strings(o, "arrivals", path));
        for (JsonReader.Indexed x : r.objects(o, "entityFlags", path)) {
            EntityFlag f = new EntityFlag();
            f.entity = r.str(x.json, "entity", x.path, true, null);
            f.flag = r.str(x.json, "flag", x.path, true, null);
            if (f.entity == null || f.flag == null) continue;
            if (!f.flag.startsWith("$")) f.flag = "$" + f.flag;
            f.steps.addAll(r.strings(x.json, "steps", x.path));
            Boolean imp = r.bool(x.json, "important", x.path, false);
            f.important = imp != null && imp;
            q.entityFlags.add(f);
        }
        // SagaLib 1.2: random targets (vanilla's HubMissionWithSearch criteria), searched when the quest is offered
        for (JsonReader.Indexed x : r.objects(o, "targets", path)) {
            Target t = parseTarget(r, x, true);
            if (t != null) q.targets.add(t);
        }
        for (JsonReader.Indexed e : r.objects(o, "phrases", path)) {
            PhraseDef p = PhraseDef.parse(e.json, e.path, r);
            if (p != null) q.phrases.add(p);
        }
        q.flags.addAll(r.strings(o, "flags", path));
        // SagaLib 1.3 (task #61): markets changing owner
        JSONObject oc = r.obj(o, "ownerChange", path, false);
        if (oc != null) {
            String op = "$.ownerChange";
            OwnerChange c = new OwnerChange();
            c.action = r.str(oc, "do", op, false, "keep");
            if (!"keep".equals(c.action) && !"fail".equals(c.action) && !"stage".equals(c.action)) {
                r.error(JsonReader.at(op, "do"), "unknown \"" + c.action + "\" (keep, fail or stage); keeps going");
                c.action = "keep";
            }
            c.stage = r.str(oc, "stage", op, false, "");
            if ("stage".equals(c.action) && q.stage(c.stage) == null) r.error(JsonReader.at(op, "stage"), "no stage \"" + c.stage + "\" in this quest");
            String t = r.str(oc, "trigger", op, false, "");
            c.trigger = t.isEmpty() ? null : t;
            c.markets.addAll(r.strings(oc, "markets", op));
            q.ownerChange = c;
        }
        // SagaLib 1.3 (task #65): a Nexerelin quest skip's rewards and people to show
        JSONObject sk = r.obj(o, "skip", path, false);
        if (sk != null) {
            Skip k = new Skip();
            for (JsonReader.Indexed e : r.objects(sk, "rewards", "$.skip")) {
                Reward rw = parseReward(e.json, e.path, r);
                if (rw != null) k.rewards.add(rw);
            }
            k.unhide.addAll(r.strings(sk, "unhide", "$.skip"));
            q.skip = k;
        }
        return q;
    }

    /** A random target (quest "targets", or a scatter's "target"): pick, criteria, system first, person. */
    public static Target parseTarget(JsonReader r, JsonReader.Indexed x, boolean needVar) {
        Target t = new Target();
        t.var = r.str(x.json, "var", x.path, needVar, null);
        t.pick = r.str(x.json, "pick", x.path, true, null);
        if ((needVar && t.var == null) || t.pick == null) return null;
        if (!Targets.PICKS.contains(t.pick)) { r.error(x.path, "pick \"" + t.pick + "\" is not one of " + Targets.PICKS); return null; }
        Boolean sf = r.bool(x.json, "systemFirst", x.path, false);
        t.systemFirst = sf != null && sf;
        parseCriteria(r, x, "require", t.require);
        parseCriteria(r, x, "prefer", t.prefer);
        JSONObject po = r.obj(x.json, "person", x.path, false);
        if (po != null) {
            TargetPerson tp = new TargetPerson();
            tp.posts.addAll(r.strings(po, "posts", JsonReader.at(x.path, "person")));
            tp.rank = r.str(po, "rank", JsonReader.at(x.path, "person"), false, "citizen");
            tp.faction = r.str(po, "faction", JsonReader.at(x.path, "person"), false, null);
            if (tp.faction != null && tp.faction.isEmpty()) tp.faction = null;
            if (tp.posts.isEmpty()) r.error(JsonReader.at(x.path, "person"), "a target person needs at least one post");
            else t.person = tp;
        }
        return t;
    }

    private static void parseCriteria(JsonReader r, JsonReader.Indexed x, String key, List<Criterion> out) {
        for (JsonReader.Indexed c : r.objects(x.json, key, x.path)) {
            Criterion cr = new Criterion();
            cr.id = r.str(c.json, "c", c.path, true, null);
            if (cr.id == null) continue;
            if (!Targets.CRITERIA.contains(cr.id)) { r.error(c.path, "unknown target criterion \"" + cr.id + "\" (a newer Saga Forge? update SagaLib)"); continue; }
            org.json.JSONArray a = r.arr(c.json, "args", c.path);
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    Object v = a.opt(i);
                    if (v instanceof org.json.JSONArray) {
                        List<String> l = new ArrayList<String>();
                        org.json.JSONArray va = (org.json.JSONArray) v;
                        for (int j = 0; j < va.length(); j++) l.add(String.valueOf(va.opt(j)));
                        cr.args.add(l);
                    } else cr.args.add(v);
                }
            }
            out.add(cr);
        }
    }

    static Reward parseReward(JSONObject o, String path, JsonReader r) {
        Reward rw = new Reward();
        rw.id = r.str(o, "id", path, true, null);
        if (rw.id == null) return null;
        // SagaLib 1.2 (Phase 8a): random items from drop groups
        SystemDef.parseLoot(r, o, path, rw.loot);
        Double credits = r.num(o, "credits", path, 0d);
        rw.credits = credits == null ? 0 : Math.round(credits);
        rw.creditsScale = lastai.sagalib.core.Settings.Scale.parse(o, "creditsScale", path, r);
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
