package lastai.sagalib.test;

import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.Names;
import lastai.sagalib.core.Problems;
import lastai.sagalib.model.PersonDef;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Map;

/**
 * SagaLib 1.4, task #79: one-name characters (core.Names, the "oneName" flag in people JSON). The
 * token generator itself (game/OneNameTokens) needs the game: Saga Forge INGAME-CHECKS "Task #79
 * one-name characters"; golden case names.json covers it with the runner's stand-in.
 */
public final class NamesTests {
    public static void oneNameIsABlankFirstWithALast() {
        T.yes(Names.isOneName("", "Tyger"), "first empty");
        T.yes(Names.isOneName("  ", "Tyger"), "first blank");
        T.yes(Names.isOneName(null, "Tyger"), "first null");
        T.no(Names.isOneName("Ana", "Varga"), "two names");
        T.no(Names.isOneName("Ana", ""), "only a first name: vanilla's first-name tokens are right");
        T.no(Names.isOneName("", " "), "no name at all");
        T.eq("Tyger", Names.firstOrOne("", "Tyger"), "one name");
        T.eq("Ana", Names.firstOrOne("Ana", "Varga"), "first name");
        T.eq("", Names.firstOrOne(null, ""), "nothing");
    }

    public static void tokensOnlyForOneNamePeople() {
        Map<String, String> t = Names.personTokens("", " Tyger ");
        T.eq("Tyger", t.get("$personFirstName"), "$personFirstName");
        T.eq("Tyger", t.get("$PersonFirstName"), "$PersonFirstName (new in 0.98.5a; filled on 0.98a too)");
        T.eq(2, t.size(), "nothing else: vanilla's $personName and $personLastName read right");
        T.yes(Names.personTokens("Ana", "Varga").isEmpty(), "two names: vanilla's stay");
        T.yes(Names.personTokens("Ana", "").isEmpty(), "first only: vanilla's stay");
    }

    public static void storedNameWithTheFlag() {
        T.eq(Arrays.asList("", "Tyger"), Arrays.asList(Names.stored("", "Tyger", true)), "already in the last slot");
        T.eq(Arrays.asList("", "Mousey"), Arrays.asList(Names.stored("Mousey", "", true)), "moved into the last slot");
        T.eq(Arrays.asList("", "Butters"), Arrays.asList(Names.stored("Old", " Butters ", true)), "the flag wins: the first is dropped");
        T.eq(Arrays.asList("Ana", "Varga"), Arrays.asList(Names.stored("Ana", "Varga", false)), "no flag: as given");
    }

    public static void firstNamePhraseFallsBack() {
        T.eq("Tyger", Names.firstNamePhrase("$pga_tyger_firstName", "pga_tyger", "", "", "Tyger"), "blank: the one name");
        T.eq("Kitty", Names.firstNamePhrase("$pga_tyger_firstName", "pga_tyger", "Kitty", "", "Tyger"), "a text stays");
        T.eq("", Names.firstNamePhrase("$pga_ana_firstName", "pga_ana", "", "Ana", "Varga"), "not one-name");
        T.eq("", Names.firstNamePhrase("$pga_tyger_title", "pga_tyger", "", "", "Tyger"), "another phrase");
        T.eq("", Names.firstNamePhrase("$other_firstName", "pga_tyger", "", "", "Tyger"), "another person's token");
    }

    public static void peopleJsonReadsTheFlag() throws Exception {
        Problems p = new Problems();
        PersonDef d = PersonDef.parse(new JSONObject("{\"id\":\"pga_mousey\",\"name\":{\"first\":\"Mousey\",\"last\":\"\"},\"oneName\":true,\"faction\":\"independent\"}"), "$.people[0]", new JsonReader("t", p));
        T.yes(p.isEmpty(), "no problems: " + p.all());
        T.yes(d.oneName, "flag");
        T.eq("", d.firstName, "first");
        T.eq("Mousey", d.lastName, "last");
        PersonDef plain = PersonDef.parse(new JSONObject("{\"id\":\"pga_ana\",\"name\":{\"first\":\"Ana\",\"last\":\"Varga\"},\"faction\":\"independent\"}"), "$.people[1]", new JsonReader("t", p));
        T.no(plain.oneName, "no flag");
        T.eq("Ana", plain.firstName, "first kept");
        PersonDef bad = PersonDef.parse(new JSONObject("{\"id\":\"x\",\"oneName\":\"maybe\",\"faction\":\"independent\"}"), "$.people[2]", new JsonReader("t", p));
        T.no(p.isEmpty(), "a oneName that isn't true or false is reported");
        T.no(bad.oneName, "and read as false");
    }
}
