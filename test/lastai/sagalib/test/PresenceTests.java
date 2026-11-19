package lastai.sagalib.test;

import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.Presence;
import lastai.sagalib.core.Problems;
import lastai.sagalib.model.PersonDef;
import org.json.JSONObject;

import java.util.Arrays;

/**
 * SagaLib 1.3, task #70: presence rules (the golden case presence.json covers the checks; these
 * cover parsing and the level names).
 */
public final class PresenceTests {
    private static PersonDef parse(String json, Problems p) throws Exception {
        return PersonDef.parse(new JSONObject(json), "$.people[0]", new JsonReader("t.json", p));
    }

    public static void parsesWithDefaults() throws Exception {
        Problems p = new Problems();
        PersonDef d = parse("{id:'a', faction:'hegemony', placement:{at:'market', market:'jangala'}, presence:{}}", p);
        T.yes(p.isEmpty(), "no problems: " + p.all());
        T.eq("hegemony", d.presence.faction, "default faction: their own");
        T.eq("NEUTRAL", d.presence.atLeast, "default level");
        T.eq(null, d.presence.elsewhere, "nowhere by default");
        d = parse("{id:'a', faction:'hegemony', placement:{at:'market', market:'jangala'}, presence:{faction:'tritachyon', atLeast:'notHostile', elsewhere:'asharu'}}", p);
        T.eq("tritachyon", d.presence.faction, "faction");
        T.eq("asharu", d.presence.elsewhere, "elsewhere");
    }

    public static void badPresenceIsIgnoredWithAnError() throws Exception {
        Problems p = new Problems();
        PersonDef d = parse("{id:'a', faction:'hegemony', placement:{at:'market', market:'jangala'}, presence:{atLeast:'chummy'}}", p);
        T.eq(null, d.presence, "unknown level: no presence");
        T.yes(p.all().toString().contains("unknown level"), "error: " + p.all());
        p = new Problems();
        d = parse("{id:'a', faction:'hegemony', placement:{at:'none'}, presence:{atLeast:'NEUTRAL'}}", p);
        T.eq(null, d.presence, "not at a market: no presence");
        T.yes(p.all().toString().contains("placed at a market"), "error: " + p.all());
    }

    public static void levels() {
        T.eq("INHOSPITABLE", Presence.level("notHostile"), "not hostile = Inhospitable or better");
        T.eq("FAVORABLE", Presence.level("favorable"), "any case");
        T.eq(null, Presence.level("chummy"), "unknown");
        T.eq(Boolean.TRUE, Presence.atLeast("NEUTRAL", "SUSPICIOUS"), "Neutral is at least Suspicious");
        T.eq(Boolean.FALSE, Presence.atLeast("HOSTILE", "notHostile"), "Hostile is hostile");
        T.eq(Boolean.TRUE, Presence.atLeast("INHOSPITABLE", "notHostile"), "Inhospitable isn't hostile");
        T.eq(null, Presence.atLeast(null, "NEUTRAL"), "unknown relation");
        T.eq(9, Presence.LEVELS.size(), "nine levels");
        T.eq(Arrays.asList("VENGEFUL", "COOPERATIVE"), Arrays.asList(Presence.LEVELS.get(0), Presence.LEVELS.get(8)), "worst first");
    }

    public static void conditionWithoutWorldIsFalse() {
        lastai.sagalib.core.Places.setup(null);
        MapMemory m = new MapMemory();
        T.eq(Boolean.FALSE, Presence.condition(m, "SF_FactionRep", Arrays.asList("pirates", "hegemony", "NEUTRAL")), "no world: false");
        T.eq(Boolean.TRUE, Presence.condition(m, "SF_FactionRep", Arrays.asList("hegemony", "hegemony", "COOPERATIVE")), "itself, even without a world");
        T.eq(Boolean.FALSE, Presence.condition(m, "SF_FactionRep", Arrays.asList("hegemony", "hegemony")), "too few arguments");
        T.eq(null, Presence.condition(m, "SF_Other", Arrays.asList("a")), "other names: not ours");
    }
}
