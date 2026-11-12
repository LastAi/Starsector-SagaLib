package lastai.sagalib.model;

import lastai.sagalib.core.JsonReader;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A character a content mod creates (docs/FORMAT.md, "People"). */
public final class PersonDef {
    public String id;
    public String firstName;
    public String lastName;
    /** MALE, FEMALE or ANY. */
    public String gender;
    public String faction;
    public String rank;
    public String post;
    public String portrait;
    public String voice;
    public String personality;
    /** market, fleet (M7) or none. */
    public String placementAt;
    public String market;
    public boolean inCommDirectory;
    /** null = not a contact. */
    public List<String> contactTypes;
    public String importance;
    public final List<String> tags = new ArrayList<String>();
    public final List<PhraseDef> phrases = new ArrayList<PhraseDef>();
    public String description;
    public String source;

    public boolean isContact() { return contactTypes != null; }

    public static PersonDef parse(JSONObject o, String path, JsonReader r) {
        PersonDef p = new PersonDef();
        p.source = r.file + " " + path;
        p.id = r.str(o, "id", path, true, null);
        if (p.id == null) return null;
        JSONObject name = r.obj(o, "name", path, false);
        p.firstName = name == null ? "" : r.str(name, "first", JsonReader.at(path, "name"), false, "");
        p.lastName = name == null ? "" : r.str(name, "last", JsonReader.at(path, "name"), false, "");
        p.gender = r.str(o, "gender", path, false, "ANY");
        if (!"MALE".equals(p.gender) && !"FEMALE".equals(p.gender) && !"ANY".equals(p.gender)) {
            r.error(JsonReader.at(path, "gender"), "unknown gender \"" + p.gender + "\" (MALE, FEMALE or ANY); using ANY");
            p.gender = "ANY";
        }
        p.faction = r.str(o, "faction", path, true, null);
        if (p.faction == null) return null;
        p.rank = r.str(o, "rank", path, false, "");
        p.post = r.str(o, "post", path, false, "");
        p.portrait = r.str(o, "portrait", path, false, "");
        p.voice = r.str(o, "voice", path, false, "");
        p.personality = r.str(o, "personality", path, false, "");
        JSONObject pl = r.obj(o, "placement", path, false);
        String pp = JsonReader.at(path, "placement");
        p.placementAt = pl == null ? "none" : r.str(pl, "at", pp, false, "none");
        if ("market".equals(p.placementAt)) {
            p.market = r.str(pl, "market", pp, true, null);
            if (p.market == null) p.placementAt = "none";
            Boolean cd = r.bool(pl, "inCommDirectory", pp, true);
            p.inCommDirectory = cd == null || cd;
        } else if (!"none".equals(p.placementAt) && !"fleet".equals(p.placementAt)) {
            r.error(JsonReader.at(pp, "at"), "unknown placement \"" + p.placementAt + "\" (market, fleet or none)");
            p.placementAt = "none";
        }
        JSONObject c = r.obj(o, "contact", path, false);
        if (c != null) {
            String cp = JsonReader.at(path, "contact");
            p.contactTypes = r.strings(c, "types", cp);
            p.importance = r.str(c, "importance", cp, false, "MEDIUM");
        }
        p.tags.addAll(r.strings(o, "tags", path));
        for (JsonReader.Indexed e : r.objects(o, "phrases", path)) {
            PhraseDef ph = PhraseDef.parse(e.json, e.path, r);
            if (ph != null) p.phrases.add(ph);
        }
        p.description = r.str(o, "description", path, false, "");
        return p;
    }
}
