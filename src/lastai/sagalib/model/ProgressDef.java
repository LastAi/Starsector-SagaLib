package lastai.sagalib.model;

import lastai.sagalib.core.JsonReader;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A progress event (SagaLib 1.2, docs/FORMAT.md "Progress events"):
 * {@code data/sagaforge/progress/<id>.json}, type {@code progress}. A progress bar with stages, like
 * vanilla's colony crises and Hyperspace Topography, fed by monthly factors (points while
 * conditions hold) and one-time points (SF_Progress).
 */
public final class ProgressDef {
    /** At most this many stages (SagaLib's stage slots, SagaStage S1..S20). */
    public static final int MAX_STAGES = 20;

    public static final class Outcome {
        public String id;
        public float weight = 1f;
        /** SagaLib 1.4: the actions SagaLib runs itself (core.QuietOps); null = a rule on the trigger. */
        public List<lastai.sagalib.core.QuietOps.Op> actions;
    }

    public static final class Stage {
        public String id;
        public int progress;
        public String title = "";
        public String text = "";
        public String icon = "";
        /** Picked at random when the stage is reached (each with its own trigger); empty = none. */
        public final List<Outcome> outcomes = new ArrayList<Outcome>();
        /** SagaLib 1.4: the actions SagaLib runs itself (core.QuietOps); null = a rule on the trigger. Only without outcomes. */
        public List<lastai.sagalib.core.QuietOps.Op> actions;
    }

    /**
     * SagaLib 1.4: the op list run for a trigger of this event (SF_progress_&lt;id&gt;_&lt;stage&gt;[_&lt;outcome&gt;]),
     * or null (no such trigger, or its actions are rules).
     */
    public List<lastai.sagalib.core.QuietOps.Op> quietOps(String trigger) {
        for (Stage s : stages) {
            if (s.outcomes.isEmpty()) {
                if (lastai.sagalib.core.EventLogic.progressTrigger(id, s.id, null).equals(trigger)) return s.actions;
            } else {
                for (Outcome o : s.outcomes) if (lastai.sagalib.core.EventLogic.progressTrigger(id, s.id, o.id).equals(trigger)) return o.actions;
            }
        }
        return null;
    }

    public static final class Factor {
        public String id;
        public String desc = "";
        public int points;
        public final List<String> conditions = new ArrayList<String>();
    }

    public String id;
    public String name = "";
    public String icon = "";
    public String tag = "";
    public int max = 1000;
    /** Progress is bad for the player (vanilla's colors for crises). */
    public boolean negative;
    /** Conditions to start the event (empty: as soon as a game has the mod). */
    public final List<String> startConditions = new ArrayList<String>();
    /** After the last stage: progress goes back to this (it repeats), or -1: the event ends. */
    public int restartAt = -1;
    public final List<Stage> stages = new ArrayList<Stage>();
    public final List<Factor> factors = new ArrayList<Factor>();
    public String source;
    /** SagaLib 1.3: only with / only without these mods (modsWith, modsWithout, modsAny). */
    public lastai.sagalib.core.ModGate gate = lastai.sagalib.core.ModGate.NONE;

    public Stage stage(String id) {
        for (Stage s : stages) if (s.id.equals(id)) return s;
        return null;
    }

    public Factor factor(String id) {
        for (Factor f : factors) if (f.id.equals(id)) return f;
        return null;
    }

    public static ProgressDef parse(JSONObject o, JsonReader r) {
        if (!r.checkFormat(o)) return null;
        ProgressDef d = new ProgressDef();
        d.id = r.str(o, "id", "$", true, null);
        if (d.id == null) return null;
        d.source = r.file;
        d.gate = lastai.sagalib.core.ModGate.parse(o, "$", r);
        d.name = r.str(o, "name", "$", false, d.id);
        d.icon = r.str(o, "icon", "$", false, "");
        d.tag = r.str(o, "tag", "$", false, "");
        Double mx = r.num(o, "max", "$", 1000.0);
        d.max = mx == null || mx < 1 ? 1000 : mx.intValue();
        Boolean neg = r.bool(o, "negative", "$", false);
        d.negative = neg != null && neg;
        d.startConditions.addAll(r.strings(o, "startConditions", "$"));
        Double ra = r.num(o, "restartAt", "$", -1.0);
        d.restartAt = ra == null ? -1 : ra.intValue();
        int prev = 0;
        for (JsonReader.Indexed e : r.objects(o, "stages", "$")) {
            Stage s = new Stage();
            s.id = r.str(e.json, "id", e.path, true, null);
            if (s.id == null) continue;
            Double p = r.num(e.json, "progress", e.path, 0.0);
            s.progress = p == null ? 0 : p.intValue();
            if (s.progress <= prev || s.progress > d.max) {
                r.error(JsonReader.at(e.path, "progress"), "stage \"" + s.id + "\": progress " + s.progress + " must be above the stage before (" + prev + ") and at most max (" + d.max + "); skipped");
                continue;
            }
            prev = s.progress;
            s.title = r.str(e.json, "title", e.path, false, s.id);
            s.text = r.str(e.json, "text", e.path, false, "");
            s.icon = r.str(e.json, "icon", e.path, false, "");
            for (JsonReader.Indexed oc : r.objects(e.json, "outcomes", e.path)) {
                Outcome out = new Outcome();
                out.id = r.str(oc.json, "id", oc.path, true, null);
                if (out.id == null) continue;
                Double w = r.num(oc.json, "weight", oc.path, 1.0);
                out.weight = w == null || w < 0 ? 0f : w.floatValue();
                out.actions = lastai.sagalib.core.QuietOps.parse(oc.json, "actions", oc.path, r);
                s.outcomes.add(out);
            }
            s.actions = lastai.sagalib.core.QuietOps.parse(e.json, "actions", e.path, r);
            if (s.actions != null && !s.outcomes.isEmpty()) {
                r.warn(JsonReader.at(e.path, "actions"), "stage \"" + s.id + "\" has outcomes: the picked outcome's actions run instead; these are ignored");
                s.actions = null;
            }
            if (d.stages.size() >= MAX_STAGES) {
                r.error(e.path, "at most " + MAX_STAGES + " stages; \"" + s.id + "\" is skipped");
                continue;
            }
            d.stages.add(s);
        }
        if (d.stages.isEmpty()) r.warn("$.stages", "progress event \"" + d.id + "\" has no stages");
        if (d.restartAt >= 0 && !d.stages.isEmpty() && d.restartAt >= d.stages.get(d.stages.size() - 1).progress) {
            r.error("$.restartAt", "restartAt must be below the last stage's progress; the event ends at the last stage instead");
            d.restartAt = -1;
        }
        for (JsonReader.Indexed e : r.objects(o, "factors", "$")) {
            Factor f = new Factor();
            f.id = r.str(e.json, "id", e.path, true, null);
            if (f.id == null) continue;
            f.desc = r.str(e.json, "desc", e.path, false, f.id);
            Double p = r.num(e.json, "points", e.path, 0.0);
            f.points = p == null ? 0 : p.intValue();
            f.conditions.addAll(r.strings(e.json, "conditions", e.path));
            d.factors.add(f);
        }
        return d;
    }
}
