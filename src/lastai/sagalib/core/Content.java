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
import java.util.Set;

/**
 * Everything the enabled content mods ship, read through the merged index
 * data/config/sagaforge/content.csv (id,type,path,mod). Definitions only: nothing here is saved,
 * so a reload simply replaces the whole object.
 */
public final class Content {
    public static final String INDEX = "data/config/sagaforge/content.csv";
    public static final List<String> TYPES = Collections.unmodifiableList(
            java.util.Arrays.asList("quest", "people", "factions", "system", "encounter", "profiles", "pool", "scatter", "strings", "translation", "events", "progress", "mods", "settings"));

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
    /** SagaLib 1.3: each content mod's list of other mods (data/sagaforge/mods.json), by the mod's id. */
    public final Map<String, Mods.ModList> modLists = new LinkedHashMap<String, Mods.ModList>();
    /** SagaLib 1.3 (task #62): every content mod's settings (value fields of its LunaSettings.csv), in file order. */
    public final List<Settings.Field> settings = new ArrayList<Settings.Field>();

    public static Content empty() { return new Content(); }

    public QuestDef quest(String id) { return id == null ? null : quests.get(id); }

    /**
     * SagaLib 1.4: the op list SagaLib runs itself for a queued trigger (a world event's start / end, a
     * progress stage's or outcome's actions), or null: fire the trigger in a dialog, as before.
     */
    public List<QuietOps.Op> quietOps(String trigger) {
        if (trigger == null) return null;
        if (trigger.startsWith("SF_event_")) {
            for (lastai.sagalib.model.EventsDef d : events.values()) {
                List<QuietOps.Op> l = EventLogic.quietOps(d, trigger);
                if (l != null) return l;
            }
        } else if (trigger.startsWith("SF_enc_")) {
            // SagaLib 1.4 (Saga Forge #67 U46): an encounter's own outcome actions
            for (lastai.sagalib.model.EncounterDef d : encounters.values()) {
                List<QuietOps.Op> l = d.quietOps(trigger);
                if (l != null) return l;
            }
        } else if (trigger.startsWith("SF_progress_")) {
            for (lastai.sagalib.model.ProgressDef d : progress.values()) {
                List<QuietOps.Op> l = d.quietOps(trigger);
                if (l != null) return l;
            }
        }
        return null;
    }

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
            if ("settings".equals(type)) {
                c.loadSettings(path, mod, file, where, src);
                continue;
            }
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

    /** SagaLib 1.3 (task #62): a mod's settings page (its LunaSettings.csv), read without LunaLib. */
    private void loadSettings(String path, String mod, String file, String where, Source src) {
        try {
            JSONArray csv = src.loadCsv(path, mod);
            if (csv == null) {
                problems.error(file, "", "missing; listed in " + where);
                return;
            }
            Settings.read(mod, csv, settings, problems, file);
        } catch (Throwable t) {
            problems.error(file, "", "can't read it (" + t.getMessage() + "); listed in " + where);
        }
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
        // SagaLib 1.3: an item with versions by installed mods (docs/FORMAT.md "Versions by installed mods")
        if (VERSIONED_TYPES.contains(type) && json != null && ("people".equals(type) ? peopleHaveVersions(json) : Versions.has(json))) {
            if ("people".equals(type)) addPeopleVersioned(id, mod, file, json, replaced);
            else addVersioned(id, type, mod, file, json, replaced);
            return;
        }
        addPlain(id, type, mod, file, json, replaced);
    }

    // ------------------------------------------------------------------ SagaLib 1.3: versions

    /** Content types whose items may have versions. */
    public static final List<String> VERSIONED_TYPES = Collections.unmodifiableList(
            java.util.Arrays.asList("quest", "people", "system", "encounter", "pool", "events", "progress", "scatter", "factions"));

    /** SagaLib 1.3: every item with versions ("person:sfa_reyes" → its versions, parsed). */
    public final Map<String, Versions.Item> versioned = new LinkedHashMap<String, Versions.Item>();

    /** SagaLib 1.3: a text.json with versions (names, descriptions, icons and prices by installed mods). */
    public static final class TextVersions {
        public final String mod;
        public final String file;
        public final JSONObject json;
        public final List<String> mentioned;

        TextVersions(String mod, String file, JSONObject json) {
            this.mod = mod;
            this.file = file;
            this.json = json;
            this.mentioned = Versions.mentioned(json);
        }
    }

    public final List<TextVersions> textVersions = new ArrayList<TextVersions>();

    private interface Variant {
        /** Parses one version's resolved JSON into {@code scratch}; returns the definition (null = unusable). */
        Object parse(JSONObject resolved, Content scratch);
    }

    private static boolean peopleHaveVersions(JSONObject json) {
        JSONArray a = json.optJSONArray("people");
        for (int i = 0; a != null && i < a.length(); i++) if (Versions.has(a.optJSONObject(i))) return true;
        return false;
    }

    /** The definition of this type and id now in the maps (not factions). */
    private Object current(String type, String id) {
        if ("quest".equals(type)) return quests.get(id);
        if ("people".equals(type) || "person".equals(type)) return people.get(id);
        if ("system".equals(type)) return systems.get(id);
        if ("encounter".equals(type)) return encounters.get(id);
        if ("pool".equals(type)) return pools.get(id);
        if ("events".equals(type)) return events.get(id);
        if ("progress".equals(type)) return progress.get(id);
        if ("scatter".equals(type)) return scatters.get(id);
        return null;
    }

    private Set<String> problemTexts(int from) {
        Set<String> out = new java.util.HashSet<String>();
        List<Problems.Problem> all = problems.all();
        for (int i = from; i < all.size(); i++) out.add(all.get(i).toString());
        return out;
    }

    /** A whole file with versions: quest, system, encounter, pool, events, progress, scatter, factions. */
    private void addVersioned(final String id, final String type, final String mod, final String file, final JSONObject json, final Map<String, String> replaced) {
        final boolean fac = "factions".equals(type);
        String itemId = fac ? mod : json.optString("id", "");
        Object before = fac ? null : current(type, itemId);
        int nf = factions.size();
        int p0 = problems.all().size();
        addPlain(id, type, mod, file, Versions.resolve(json, Collections.<String>emptyList()).value, replaced);
        Set<String> known = problemTexts(p0);
        Object def = fac ? (factions.size() > nf ? factions.get(factions.size() - 1) : null) : current(type, itemId);
        if (def == null || def == before) return; // unreadable or a duplicate id: already reported
        register(type, itemId, mod, file, "$", json, def, known, new Variant() {
            public Object parse(JSONObject resolved, Content scratch) {
                scratch.addPlain(id, type, mod, file, resolved, replaced);
                return fac ? (scratch.factions.isEmpty() ? null : scratch.factions.get(0)) : scratch.current(type, resolved.optString("id", ""));
            }
        });
    }

    /** A people file where some people have versions (each person's own list). */
    private void addPeopleVersioned(final String id, final String mod, final String file, final JSONObject json, final Map<String, String> replaced) {
        JSONArray arr = json.optJSONArray("people");
        final JSONObject baseFile = (JSONObject) Versions.copy(json);
        JSONArray baseArr = baseFile.optJSONArray("people");
        List<Integer> withVersions = new ArrayList<Integer>();
        try {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.optJSONObject(i);
                if (!Versions.has(e)) continue;
                baseArr.put(i, Versions.resolve(e, Collections.<String>emptyList()).value);
                withVersions.add(i);
            }
        } catch (Exception e) {
            problems.error(file, "$.people", "versions could not be read: " + e);
            addPlain(id, "people", mod, file, json, replaced);
            return;
        }
        Set<String> before = new java.util.HashSet<String>(people.keySet());
        int p0 = problems.all().size();
        addPlain(id, "people", mod, file, baseFile, replaced);
        Set<String> known = problemTexts(p0);
        for (final int i : withVersions) {
            JSONObject e = arr.optJSONObject(i);
            final String pid = e.optString("id", "");
            PersonDef def = people.get(pid);
            if (def == null || before.contains(pid)) continue;
            register("person", pid, mod, file, "$.people[" + i + "]", e, def, known, new Variant() {
                public Object parse(JSONObject resolved, Content scratch) {
                    JSONObject f = (JSONObject) Versions.copy(baseFile);
                    try {
                        f.getJSONArray("people").put(i, resolved);
                    } catch (Exception ex) {
                        return null;
                    }
                    scratch.addPlain(id, "people", mod, file, f, replaced);
                    return scratch.people.get(pid);
                }
            });
        }
    }

    /**
     * Parses every version of one item (each subset of the mods it mentions) and registers it.
     * Problems that only a version has are reported once, tagged with the version's key.
     */
    private void register(String type, String itemId, String mod, String file, String path, JSONObject raw, Object def, Set<String> known, Variant fn) {
        String kind = "people".equals(type) ? "person" : type;
        String ref = kind + ":" + itemId;
        List<String> mentioned = Versions.mentioned(raw);
        int settingTokens = 0;
        for (String x : mentioned) if (Versions.isSetting(x)) settingTokens++;
        if (settingTokens == 0 && mentioned.size() > Versions.MAX_MODS) {
            problems.error(file, JsonReader.at(path, "versions"), "the versions name " + mentioned.size() + " mods " + mentioned + "; at most "
                    + Versions.MAX_MODS + " are supported: only the base is used");
            mentioned = Collections.emptyList();
        } else if (Versions.combinations(mentioned) > Versions.MAX_COMBINATIONS) {
            // SagaLib 1.4: with setting values, the combinations count (each mod on/off, each setting one of the values named or another)
            problems.error(file, JsonReader.at(path, "versions"), "the versions name " + mentioned + ": " + Versions.combinations(mentioned)
                    + " combinations; at most " + Versions.MAX_COMBINATIONS + " are supported: only the base is used");
            mentioned = Collections.emptyList();
        }
        if (versioned.containsKey(ref)) {
            problems.error(file, JsonReader.at(path, "versions"), kind + " " + itemId + " already has versions from " + versioned.get(ref).source + "; these are ignored");
            return;
        }
        Versions.Item it = new Versions.Item(kind, itemId, mod, file + " at " + path, raw, mentioned);
        it.defs.put(Versions.BASE, def);
        it.active = def;
        Set<String> reported = new java.util.HashSet<String>(known);
        Set<String> conflicts = new java.util.HashSet<String>();
        for (List<String> s : Versions.subsets(mentioned)) {
            String key = Versions.key(s);
            Versions.Result res = Versions.resolve(raw, s);
            for (String e : res.errors) {
                int colon = e.indexOf(": ");
                String p = JsonReader.at(path, colon > 0 ? e.substring(0, colon) : e);
                String msg = colon > 0 ? e.substring(colon + 2) : "can't be used";
                if (reported.add("E " + p + msg)) problems.error(file, p, msg);
            }
            for (String c : res.conflicts) {
                if (conflicts.add(c)) {
                    problems.warn(file, JsonReader.at(path, "versions"), "versions " + res.conflictVersions.get(c) + " are equally specific and set \"" + c
                            + "\" to different values (with " + key + "); the later one in the file wins");
                }
            }
            if (s.isEmpty()) continue;
            Content scratch = new Content();
            Object vdef;
            try {
                vdef = fn.parse(res.value, scratch);
            } catch (Throwable t) {
                vdef = null;
                scratch.problems.error(file, path, "could not be read: " + t);
            }
            for (Problems.Problem p : scratch.problems.all()) {
                if (!reported.add(p.toString())) continue;
                if (p.error) problems.error(p.file, p.path, p.message + " (in version " + key + ")");
                else problems.warn(p.file, p.path, p.message + " (in version " + key + ")");
            }
            if (vdef == null) {
                problems.error(file, path, "version " + key + " can't be used; the base is used instead");
                vdef = def;
            }
            it.defs.put(key, vdef);
        }
        versioned.put(ref, it);
    }

    /**
     * Puts the item's version {@code key} (unknown: the base) into the maps, in place of the one
     * there, phrases included. Every item is activated on every load, so another save starts clean.
     */
    public void activate(Versions.Item it, String key) {
        Object def = it.defs.get(key);
        if (def == null) def = it.defs.get(Versions.BASE);
        Object old = it.active;
        if (def == null || def == old) return;
        if ("quest".equals(it.kind)) {
            quests.put(it.id, (QuestDef) def);
            swapPhrases(((QuestDef) old).phrases, ((QuestDef) def).phrases);
        } else if ("person".equals(it.kind)) {
            people.put(it.id, (PersonDef) def);
            swapPhrases(((PersonDef) old).phrases, ((PersonDef) def).phrases);
        } else if ("system".equals(it.kind)) systems.put(it.id, (SystemDef) def);
        else if ("encounter".equals(it.kind)) encounters.put(it.id, (EncounterDef) def);
        else if ("pool".equals(it.kind)) pools.put(it.id, (PoolDef) def);
        else if ("events".equals(it.kind)) events.put(it.id, (lastai.sagalib.model.EventsDef) def);
        else if ("progress".equals(it.kind)) progress.put(it.id, (lastai.sagalib.model.ProgressDef) def);
        else if ("scatter".equals(it.kind)) scatters.put(it.id, (lastai.sagalib.model.ScatterDef) def);
        else if ("factions".equals(it.kind)) {
            for (int i = 0; i < factions.size(); i++) if (factions.get(i) == old) factions.set(i, (FactionsDef) def);
        }
        it.active = def;
    }

    private void swapPhrases(List<PhraseDef> old, List<PhraseDef> now) {
        for (PhraseDef p : old) if (phrases.get(p.token) == p) phrases.remove(p.token);
        for (PhraseDef p : now) if (!phrases.containsKey(p.token)) phrases.put(p.token, p);
    }

    /** The key of the version now active ("base", "a+b"). */
    public static String activeKey(Versions.Item it) {
        for (Map.Entry<String, Object> e : it.defs.entrySet()) if (e.getValue() == it.active) return e.getKey();
        return Versions.BASE;
    }

    private void addPlain(String id, String type, String mod, String file, JSONObject json, Map<String, String> replaced) {
        JsonReader r = new JsonReader(file, problems);
        r.mod = mod;
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
            } else if ("mods".equals(type)) {
                // SagaLib 1.3: the mod's list of other mods (required, optional, incompatible)
                Mods.ModList l = Mods.ModList.parse(json, r);
                if (l != null) {
                    if (!l.owner.equals(mod)) r.error("$.mod", "says it is the list of " + l.owner + ", but it ships with " + mod + "; using " + mod);
                    Mods.ModList fixed = l.owner.equals(mod) ? l : new Mods.ModList(mod, l.name, l.prefix);
                    if (fixed != l) fixed.entries.addAll(l.entries);
                    fixed.source = file;
                    modLists.put(mod, fixed);
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
        // SagaLib 1.3: versions by installed mods (applied at application load with the mods enabled then)
        if (Versions.has(json)) {
            for (String e : Versions.checkSpecVersions(json)) {
                int colon = e.indexOf(": ");
                r.error(colon > 0 ? "$." + e.substring(0, colon) : "$.versions", colon > 0 ? e.substring(colon + 2) : e);
            }
            textVersions.add(new TextVersions(r.mod, r.file, json));
        }
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
                + (versioned.isEmpty() ? "" : "; " + versioned.size() + " items with versions")
                + (settings.isEmpty() ? "" : "; " + settings.size() + " settings")
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
