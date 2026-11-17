package lastai.sagalib.core;

import lastai.sagalib.model.EncounterDef;
import lastai.sagalib.model.PoolDef;
import lastai.sagalib.model.FactionsDef;
import lastai.sagalib.model.PersonDef;
import lastai.sagalib.model.PhraseDef;
import lastai.sagalib.model.ProfileDef;
import lastai.sagalib.model.QuestDef;
import lastai.sagalib.model.SystemDef;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the enabled content mods ship, read through the merged index
 * data/config/sagaforge/content.csv (id,type,path,mod). Definitions only: nothing here is saved,
 * so a reload simply replaces the whole object.
 */
public final class Content {
    public static final String INDEX = "data/config/sagaforge/content.csv";
    public static final List<String> TYPES = Collections.unmodifiableList(
            java.util.Arrays.asList("quest", "people", "factions", "system", "encounter", "profiles", "pool", "scatter", "strings", "translation", "events", "progress"));

    /** Where files come from: the game's loaders in game, files on disk in the tests. */
    public interface Source {
        /** The merged rows of content.csv from every enabled mod. */
        JSONArray index() throws Exception;

        JSONObject load(String path, String mod) throws Exception;

        /** Test profiles are read only in dev mode. */
        boolean devMode();

        /** A CSV's rows (translations, SagaLib 1.2); null when missing. */
        default JSONArray loadCsv(String path, String mod) throws Exception { return null; }
    }

    public final Map<String, QuestDef> quests = new LinkedHashMap<String, QuestDef>();
    public final Map<String, PersonDef> people = new LinkedHashMap<String, PersonDef>();
    /** Every phrase by token: inline quest phrases, people's and the mod-wide ones. */
    public final Map<String, PhraseDef> phrases = new LinkedHashMap<String, PhraseDef>();
    public final List<FactionsDef> factions = new ArrayList<FactionsDef>();
    public final Map<String, ProfileDef> profiles = new LinkedHashMap<String, ProfileDef>();
    public final Map<String, SystemDef> systems = new LinkedHashMap<String, SystemDef>();
    public final Map<String, EncounterDef> encounters = new LinkedHashMap<String, EncounterDef>();
    /** Random pools (SagaLib 1.2). */
    public final Map<String, PoolDef> pools = new LinkedHashMap<String, PoolDef>();
    /** Scatter at new game (SagaLib 1.2). */
    public final Map<String, lastai.sagalib.model.ScatterDef> scatters = new LinkedHashMap<String, lastai.sagalib.model.ScatterDef>();
    public final Problems problems = new Problems();
    /** SagaLib 1.2: progress events (data/sagaforge/progress/<id>.json). */
    public final Map<String, lastai.sagalib.model.ProgressDef> progress = new LinkedHashMap<String, lastai.sagalib.model.ProgressDef>();
    /** SagaLib 1.2: world event schedulers (data/sagaforge/events/<id>.json). */
    public final Map<String, lastai.sagalib.model.EventsDef> events = new LinkedHashMap<String, lastai.sagalib.model.EventsDef>();
    /** SagaLib 1.2: the active translation of each content mod (by the mod's id). */
    public final Map<String, Translation> translations = new LinkedHashMap<String, Translation>();
    /** SagaLib 1.2: dialog texts of the mods' rules: rule id → part (t, o_<option>, tip_<option>, h<n>, a<n>) → text, translated. */
    public final Map<String, Map<String, String>> ruleTexts = new LinkedHashMap<String, Map<String, String>>();
    /** SagaLib 1.2: names and descriptions a translation changed: kind (items, descriptions, factions) → id → field → text. */
    public final Map<String, Map<String, Map<String, String>>> translatedSpecs = new LinkedHashMap<String, Map<String, Map<String, String>>>();
    /** SagaLib 1.2: every name and description in the strings files (kind → id → field → text, translated or not). */
    public final Map<String, Map<String, Map<String, String>>> allSpecs = new LinkedHashMap<String, Map<String, Map<String, String>>>();
    /** SagaLib 1.2: the original of every text a translation replaced (key → original). */
    public final Map<String, String> originals = new LinkedHashMap<String, String>();
    /** The mods that listed files. */
    public final List<String> mods = new ArrayList<String>();

    public static Content empty() { return new Content(); }

    public QuestDef quest(String id) { return id == null ? null : quests.get(id); }

    public List<ProfileDef> profileList() { return Collections.unmodifiableList(new ArrayList<ProfileDef>(profiles.values())); }

    public static Content load(Source src) {
        Content c = new Content();
        JSONArray rows;
        try {
            rows = src.index();
        } catch (Throwable t) {
            c.problems.error(INDEX, "", "can't read the content index: " + t);
            return c;
        }
        if (rows == null) return c;
        c.loadTranslations(rows, src);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            String where = INDEX + " row " + (i + 1);
            if (row == null) continue;
            String id = row.optString("id", "").trim();
            String type = row.optString("type", "").trim();
            String path = row.optString("path", "").trim();
            String mod = row.optString("mod", "").trim();
            if (id.isEmpty() || id.startsWith("#")) continue;
            if (type.isEmpty() || path.isEmpty() || mod.isEmpty()) {
                c.problems.error(where, "", "row \"" + id + "\" needs type, path and mod");
                continue;
            }
            if (!c.mods.contains(mod)) c.mods.add(mod);
            if (!TYPES.contains(type)) {
                c.problems.error(where, "", "unknown type \"" + type + "\" for " + path + " (" + TYPES + ")");
                continue;
            }
            if ("profiles".equals(type) && !src.devMode()) continue;
            if ("translation".equals(type)) continue; // read first (loadTranslations)
            String file = path + " (mod " + mod + ")";
            JSONObject json;
            try {
                json = src.load(path, mod);
            } catch (Throwable t) {
                c.problems.error(file, "", "can't read it (" + t.getMessage() + "); listed in " + where);
                continue;
            }
            if (json == null) {
                c.problems.error(file, "", "missing; listed in " + where);
                continue;
            }
            // SagaLib 1.2: the active translation of this mod replaces its texts before they are read
            Translation tr = c.translations.get(mod);
            Map<String, String> replaced = new LinkedHashMap<String, String>();
            if (tr != null) TextKeys.translate(id, json, tr, replaced);
            c.originals.putAll(replaced);
            c.add(id, type, mod, file, json, replaced);
        }
        return c;
    }

    /** Reads every translation row; one per content mod is used (the first listed), others are reported. */
    private void loadTranslations(JSONArray rows, Source src) {
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !"translation".equals(row.optString("type", "").trim())) continue;
            String id = row.optString("id", "").trim();
            String path = row.optString("path", "").trim();
            String from = row.optString("mod", "").trim();
            String where = INDEX + " row " + (i + 1);
            int slash = id.indexOf('/');
            if (slash <= 0 || slash == id.length() - 1 || path.isEmpty() || from.isEmpty()) {
                problems.error(where, "", "translation row \"" + id + "\" needs the id <mod id>/<language>, a path and a mod");
                continue;
            }
            if (!mods.contains(from)) mods.add(from);
            String target = id.substring(0, slash), lang = id.substring(slash + 1);
            Translation old = translations.get(target);
            if (old != null) {
                problems.warn(where, "", "two translations of " + target + " are enabled (" + old.language + " from " + old.from + ", " + lang + " from " + from + "): using " + old.language + "; disable one");
                continue;
            }
            String file = path + " (mod " + from + ")";
            try {
                JSONArray csv = src.loadCsv(path, from);
                if (csv == null) { problems.error(file, "", "missing; listed in " + where); continue; }
                translations.put(target, Translation.fromRows(target, lang, from, csv, problems, file));
            } catch (Throwable t) {
                problems.error(file, "", "can't read it (" + t.getMessage() + "); listed in " + where);
            }
        }
    }

    /** Adds one file's content (also used by the tests). */
    public void add(String id, String type, String mod, String file, JSONObject json) {
        add(id, type, mod, file, json, java.util.Collections.<String, String>emptyMap());
    }

    /** Adds one file's content; {@code replaced} = the texts a translation replaced in it (key → original). */
    public void add(String id, String type, String mod, String file, JSONObject json, Map<String, String> replaced) {
        JsonReader r = new JsonReader(file, problems);
        try {
            if ("quest".equals(type)) addQuest(QuestDef.parse(json, r), r);
            else if ("people".equals(type)) addPeople(json, r);
            else if ("factions".equals(type)) {
                FactionsDef f = FactionsDef.parse(json, r);
                if (f != null) factions.add(f);
            } else if ("profiles".equals(type)) addProfiles(json, r);
            else if ("system".equals(type)) {
                SystemDef s = SystemDef.parse(json, r);
                if (s != null) {
                    SystemDef old = systems.get(s.id);
                    if (old != null) r.error("$.id", "system \"" + s.id + "\" is already defined by " + old.source + "; this one is skipped");
                    else systems.put(s.id, s);
                }
            } else if ("encounter".equals(type)) {
                EncounterDef e = EncounterDef.parse(json, r);
                if (e != null) {
                    EncounterDef old = encounters.get(e.id);
                    if (old != null) r.error("$.id", "encounter \"" + e.id + "\" is already defined by " + old.source + "; this one is skipped");
                    else encounters.put(e.id, e);
                }
            } else if ("pool".equals(type)) {
                PoolDef p = PoolDef.parse(json, r);
                if (p != null) {
                    PoolDef old = pools.get(p.id);
                    if (old != null) r.error("$.id", "pool \"" + p.id + "\" is already defined by " + old.source + "; this one is skipped");
                    else pools.put(p.id, p);
                }
            } else if ("progress".equals(type)) {
                lastai.sagalib.model.ProgressDef pd = lastai.sagalib.model.ProgressDef.parse(json, r);
                if (pd != null) {
                    lastai.sagalib.model.ProgressDef old = progress.get(pd.id);
                    if (old != null) r.error("$.id", "progress event \"" + pd.id + "\" is already defined by " + old.source + "; this one is skipped");
                    else progress.put(pd.id, pd);
                }
            } else if ("events".equals(type)) {
                lastai.sagalib.model.EventsDef ev = lastai.sagalib.model.EventsDef.parse(json, r);
                if (ev != null) {
                    lastai.sagalib.model.EventsDef old = events.get(ev.id);
                    if (old != null) r.error("$.id", "event scheduler \"" + ev.id + "\" is already defined by " + old.source + "; this one is skipped");
                    else events.put(ev.id, ev);
                }
            } else if ("strings".equals(type)) {
                addStrings(id, json, r, replaced);
            } else if ("scatter".equals(type)) {
                lastai.sagalib.model.ScatterDef sc = lastai.sagalib.model.ScatterDef.parse(json, r);
                if (sc != null) {
                    lastai.sagalib.model.ScatterDef old = scatters.get(sc.id);
                    if (old != null) r.error("$.id", "scatter \"" + sc.id + "\" is already defined by " + old.source + "; this one is skipped");
                    else scatters.put(sc.id, sc);
                }
            } else {
                r.error("", "unknown type \"" + type + "\" in " + INDEX + " (quest, people, factions, system, encounter, profiles, pool, scatter)");
            }
        } catch (Throwable t) {
            // a reader bug must never take the game down: report the file and go on
            r.error("", "could not be read: " + t);
        }
    }

    /**
     * SagaLib 1.2: a mod's texts kept out of its CSV files so a translation can replace them: dialog
     * (rules: rule id → part → text) and names and descriptions of items and factions. Those are
     * applied (game.TranslatedSpecs) only where a translation replaced them: the CSV and .faction
     * files hold the originals.
     */
    private void addStrings(String id, JSONObject json, JsonReader r, Map<String, String> replaced) {
        if (!r.checkFormat(json)) return;
        JSONObject rules = json.optJSONObject("rules");
        if (rules != null) {
            for (java.util.Iterator<?> it = rules.keys(); it.hasNext(); ) {
                String rule = String.valueOf(it.next());
                JSONObject parts = rules.optJSONObject(rule);
                if (parts == null) continue;
                Map<String, String> m = ruleTexts.get(rule);
                if (m == null) ruleTexts.put(rule, m = new LinkedHashMap<String, String>());
                for (java.util.Iterator<?> pi = parts.keys(); pi.hasNext(); ) {
                    String part = String.valueOf(pi.next());
                    m.put(part, parts.optString(part, ""));
                }
            }
        }
        for (String kind : new String[] { "items", "descriptions", "factions" }) {
            JSONObject all = json.optJSONObject(kind);
            if (all == null) continue;
            for (java.util.Iterator<?> it = all.keys(); it.hasNext(); ) {
                String oid = String.valueOf(it.next());
                JSONObject fields = all.optJSONObject(oid);
                if (fields == null) continue;
                for (java.util.Iterator<?> fi = fields.keys(); fi.hasNext(); ) {
                    String f = String.valueOf(fi.next());
                    put(allSpecs, kind, oid, f, fields.optString(f, ""));
                    if (replaced.containsKey(id + "/" + kind + "." + oid + "." + f)) put(translatedSpecs, kind, oid, f, fields.optString(f, ""));
                }
            }
        }
    }

    private static void put(Map<String, Map<String, Map<String, String>>> m, String kind, String id, String field, String text) {
        Map<String, Map<String, String>> byId = m.get(kind);
        if (byId == null) m.put(kind, byId = new LinkedHashMap<String, Map<String, String>>());
        Map<String, String> fm = byId.get(id);
        if (fm == null) byId.put(id, fm = new LinkedHashMap<String, String>());
        fm.put(field, text);
    }

    /** Every id of a kind in the strings files (items, descriptions, factions) with its fields. */
    public Map<String, Map<String, String>> specTexts(String kind) {
        Map<String, Map<String, String>> m = allSpecs.get(kind);
        return m == null ? java.util.Collections.<String, Map<String, String>>emptyMap() : m;
    }

    private void addQuest(QuestDef q, JsonReader r) {
        if (q == null) return;
        QuestDef old = quests.get(q.id);
        if (old != null) {
            r.error("$.id", "quest \"" + q.id + "\" is already defined by " + old.source + "; this one is skipped");
            return;
        }
        quests.put(q.id, q);
        for (PhraseDef p : q.phrases) addPhrase(p, r);
    }

    private void addPeople(JSONObject json, JsonReader r) {
        if (!r.checkFormat(json)) return;
        for (JsonReader.Indexed e : r.objects(json, "people", "$")) {
            PersonDef p = PersonDef.parse(e.json, e.path, r);
            if (p == null) continue;
            PersonDef old = people.get(p.id);
            if (old != null) {
                r.error(JsonReader.at(e.path, "id"), "person \"" + p.id + "\" is already defined by " + old.source + "; this one is skipped");
                continue;
            }
            people.put(p.id, p);
            for (PhraseDef ph : p.phrases) addPhrase(ph, r);
        }
        for (JsonReader.Indexed e : r.objects(json, "phrases", "$")) {
            PhraseDef p = PhraseDef.parse(e.json, e.path, r);
            if (p != null) addPhrase(p, r);
        }
    }

    private void addProfiles(JSONObject json, JsonReader r) {
        if (!r.checkFormat(json)) return;
        for (JsonReader.Indexed e : r.objects(json, "profiles", "$")) {
            ProfileDef p = ProfileDef.parse(e.json, e.path, r);
            if (p == null) continue;
            if (profiles.containsKey(p.id)) {
                r.error(JsonReader.at(e.path, "id"), "profile \"" + p.id + "\" is listed twice; this one is skipped");
                continue;
            }
            profiles.put(p.id, p);
        }
    }

    private void addPhrase(PhraseDef p, JsonReader r) {
        PhraseDef old = phrases.get(p.token);
        if (old != null) {
            r.warn(p.source.substring(Math.min(p.source.length(), r.file.length())).trim(),
                    "phrase " + p.token + " is already defined by " + old.source + "; the first one is used");
            return;
        }
        phrases.put(p.token, p);
    }

    /** One line for the log. */
    public String summary() {
        return quests.size() + " quests, " + people.size() + " people, " + phrases.size() + " phrases, " + factions.size()
                + " faction files, " + systems.size() + " systems, " + encounters.size() + " encounters, " + pools.size() + " pools, " + events.size() + " event schedulers, " + progress.size() + " progress events, " + profiles.size()
                + " test profiles from " + mods.size() + " mod(s); " + problems.errors() + " errors"
                + (translations.isEmpty() ? "" : "; translations: " + translationSummary());
    }

    private String translationSummary() {
        StringBuilder b = new StringBuilder();
        for (Translation t : translations.values()) {
            if (b.length() > 0) b.append(", ");
            b.append(t.mod).append(" (").append(t.language).append(", ").append(t.size()).append(" lines from ").append(t.from).append(')');
        }
        return b.toString();
    }
}
