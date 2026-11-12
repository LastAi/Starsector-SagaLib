package lastai.sagalib.core;

import lastai.sagalib.model.EncounterDef;
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
            java.util.Arrays.asList("quest", "people", "factions", "system", "encounter", "profiles"));

    /** Where files come from: the game's loaders in game, files on disk in the tests. */
    public interface Source {
        /** The merged rows of content.csv from every enabled mod. */
        JSONArray index() throws Exception;

        JSONObject load(String path, String mod) throws Exception;

        /** Test profiles are read only in dev mode. */
        boolean devMode();
    }

    public final Map<String, QuestDef> quests = new LinkedHashMap<String, QuestDef>();
    public final Map<String, PersonDef> people = new LinkedHashMap<String, PersonDef>();
    /** Every phrase by token: inline quest phrases, people's and the mod-wide ones. */
    public final Map<String, PhraseDef> phrases = new LinkedHashMap<String, PhraseDef>();
    public final List<FactionsDef> factions = new ArrayList<FactionsDef>();
    public final Map<String, ProfileDef> profiles = new LinkedHashMap<String, ProfileDef>();
    public final Map<String, SystemDef> systems = new LinkedHashMap<String, SystemDef>();
    public final Map<String, EncounterDef> encounters = new LinkedHashMap<String, EncounterDef>();
    public final Problems problems = new Problems();
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
            c.add(id, type, mod, file, json);
        }
        return c;
    }

    /** Adds one file's content (also used by the tests). */
    public void add(String id, String type, String mod, String file, JSONObject json) {
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
            } else {
                r.error("", "unknown type \"" + type + "\" in " + INDEX + " (quest, people, factions, system, encounter, profiles)");
            }
        } catch (Throwable t) {
            // a reader bug must never take the game down: report the file and go on
            r.error("", "could not be read: " + t);
        }
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
                + " faction files, " + systems.size() + " systems, " + encounters.size() + " encounters, " + profiles.size()
                + " test profiles from " + mods.size() + " mod(s); " + problems.errors() + " errors";
    }
}
