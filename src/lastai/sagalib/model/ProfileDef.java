package lastai.sagalib.model;

import lastai.sagalib.core.JsonReader;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A test profile (PLAN.md §4.10): a game situation applied in dev mode. */
public final class ProfileDef {
    public static final class QuestSetting {
        /** not-started, active, success or fail. */
        public final String state;
        /** null = leave the step as it is. */
        public final String step;

        public QuestSetting(String state, String step) {
            this.state = state;
            this.step = step;
        }
    }

    public static final class Cargo {
        public final String item;
        public final int qty;

        public Cargo(String item, int qty) {
            this.item = item;
            this.qty = qty;
        }
    }

    public String id;
    public String name;
    public final Map<String, QuestSetting> quests = new LinkedHashMap<String, QuestSetting>();
    /** Memory keys and their values as text ("true", "3", "abc"). */
    public final Map<String, String> flags = new LinkedHashMap<String, String>();
    public Double credits;
    public Integer storyPoints;
    public String commission;
    public final Map<String, Float> rep = new LinkedHashMap<String, Float>();
    public final List<Cargo> cargo = new ArrayList<Cargo>();
    public String startAt;
    public String gender;
    public String source;

    public static ProfileDef parse(JSONObject o, String path, JsonReader r) {
        ProfileDef p = new ProfileDef();
        p.source = r.file + " " + path;
        p.id = r.str(o, "id", path, true, null);
        if (p.id == null) return null;
        p.name = r.str(o, "name", path, false, p.id);
        JSONObject qs = r.obj(o, "quests", path, false);
        if (qs != null) {
            for (Iterator<?> it = qs.keys(); it.hasNext(); ) {
                String q = String.valueOf(it.next());
                String qp = JsonReader.at(path, "quests");
                JSONObject s = r.obj(qs, q, qp, true);
                if (s == null) continue;
                String sp = JsonReader.at(qp, q);
                String state = r.str(s, "state", sp, true, null);
                if (state == null) continue;
                if (!"not-started".equals(state) && !"active".equals(state) && !"success".equals(state) && !"fail".equals(state)) {
                    r.error(JsonReader.at(sp, "state"), "unknown state \"" + state + "\" (not-started, active, success or fail)");
                    continue;
                }
                String step = r.str(s, "step", sp, false, null);
                p.quests.put(q, new QuestSetting(state, step == null || step.isEmpty() ? null : step));
            }
        }
        JSONObject fl = r.obj(o, "flags", path, false);
        if (fl != null) {
            for (Iterator<?> it = fl.keys(); it.hasNext(); ) {
                String k = String.valueOf(it.next());
                Object v = fl.opt(k);
                p.flags.put(k, v == null || v == JSONObject.NULL ? "" : String.valueOf(v));
            }
        }
        JSONObject pl = r.obj(o, "player", path, false);
        if (pl != null) {
            String pp = JsonReader.at(path, "player");
            p.credits = r.num(pl, "credits", pp, null);
            Double sp = r.num(pl, "storyPoints", pp, null);
            p.storyPoints = sp == null ? null : (int) Math.round(sp);
            p.commission = r.str(pl, "commission", pp, false, null);
            if (p.commission != null && p.commission.isEmpty()) p.commission = null;
            JSONObject rep = r.obj(pl, "rep", pp, false);
            if (rep != null) {
                for (Iterator<?> it = rep.keys(); it.hasNext(); ) {
                    String f = String.valueOf(it.next());
                    Double v = r.num(rep, f, JsonReader.at(pp, "rep"), null);
                    if (v != null) p.rep.put(f, v.floatValue());
                }
            }
            for (JsonReader.Indexed e : r.objects(pl, "cargo", pp)) {
                String item = r.str(e.json, "item", e.path, true, null);
                Double qty = r.num(e.json, "qty", e.path, 1d);
                if (item != null && qty != null) p.cargo.add(new Cargo(item, (int) Math.round(qty)));
            }
            p.startAt = r.str(pl, "startAt", pp, false, null);
            if (p.startAt != null && p.startAt.isEmpty()) p.startAt = null;
            p.gender = r.str(pl, "gender", pp, false, null);
        }
        return p;
    }
}
