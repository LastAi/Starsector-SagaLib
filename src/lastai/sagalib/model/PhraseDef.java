package lastai.sagalib.model;

import lastai.sagalib.core.JsonReader;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A phrase (PLAN.md §4.3): a token whose text depends on conditions. */
public final class PhraseDef {
    public static final class Case {
        public final List<String> when;
        public final String text;

        public Case(List<String> when, String text) {
            this.when = when;
            this.text = text;
        }
    }

    public String token;
    public String owner;
    /** "first-match" or "random". */
    public String mode;
    public final List<Case> cases = new ArrayList<Case>();
    /** null = none (an unmatched phrase gives ""). */
    public String otherwise;
    /** Where it was read, for messages. */
    public String source;

    /** Reads one phrase; null (with the problem recorded) if it can't be used. */
    public static PhraseDef parse(JSONObject o, String path, JsonReader r) {
        PhraseDef p = new PhraseDef();
        p.token = r.str(o, "token", path, true, null);
        if (p.token == null) return null;
        if (!p.token.startsWith("$")) {
            r.error(JsonReader.at(path, "token"), "a token starts with $ (got \"" + p.token + "\")");
            return null;
        }
        p.owner = r.str(o, "owner", path, false, "");
        p.mode = r.str(o, "mode", path, false, "first-match");
        if (!"first-match".equals(p.mode) && !"random".equals(p.mode)) {
            r.error(JsonReader.at(path, "mode"), "unknown mode \"" + p.mode + "\" (first-match or random); using first-match");
            p.mode = "first-match";
        }
        for (JsonReader.Indexed c : r.objects(o, "cases", path)) {
            String text = r.str(c.json, "text", c.path, true, null);
            if (text == null) continue;
            p.cases.add(new Case(r.strings(c.json, "when", c.path), text));
        }
        p.otherwise = r.str(o, "otherwise", path, false, null);
        p.source = r.file + " " + path;
        return p;
    }
}
