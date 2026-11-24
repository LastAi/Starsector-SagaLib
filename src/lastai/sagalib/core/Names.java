package lastai.sagalib.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SagaLib 1.4 (task #79): one-name characters (docs/FORMAT.md, "People" and "One-name characters").
 * A one-name person has their name in the last-name slot and an empty (or blank) first name, as
 * Saga Forge's "One name" exports it ({@code "oneName": true}). Vanilla's FullName.getFullName()
 * trims, so $personName and $personLastName read right; but vanilla fills $personFirstName (and
 * 0.98.5a's $PersonFirstName) with the empty first name. SagaLib's token generator
 * (game/OneNameTokens) fills both with the one name for any such person, any mod's. Play mode does
 * the same (tools/saga-forge/src/shared/personname.ts); golden case names.json.
 */
public final class Names {
    private Names() {}

    /** The tokens SagaLib fills for a one-name active person. */
    public static final String FIRST = "$personFirstName";
    public static final String FIRST_UC = "$PersonFirstName";

    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }

    /** A one-name person: the first name empty or blank, the last name not. */
    public static boolean isOneName(String first, String last) { return blank(first) && !blank(last); }

    /** What a first-name token says: the first name, or the one name of a one-name person. */
    public static String firstOrOne(String first, String last) { return isOneName(first, last) ? last.trim() : first == null ? "" : first; }

    /**
     * A person's name as SagaLib creates them: with {@code oneName} (Saga Forge's switch) the one name
     * goes into the last-name slot (the last name, or the first when only that is set) and the first
     * stays empty. Returns {first, last}.
     */
    public static String[] stored(String first, String last, boolean oneName) {
        String f = first == null ? "" : first, l = last == null ? "" : last;
        if (!oneName) return new String[]{f, l};
        return new String[]{"", blank(l) ? f.trim() : l.trim()};
    }

    /**
     * The first-name tokens SagaLib fills for the active person: for a one-name person
     * $personFirstName and $PersonFirstName are the one name; for anyone else nothing (vanilla's stay).
     */
    public static Map<String, String> personTokens(String first, String last) {
        if (!isOneName(first, last)) return Collections.emptyMap();
        Map<String, String> out = new LinkedHashMap<String, String>();
        out.put(FIRST, last.trim());
        out.put(FIRST_UC, last.trim());
        return out;
    }

    /**
     * A person's generated first-name phrase ({@code $<person>_firstName}, Saga Forge's name tokens):
     * when it comes out blank and its owner is a one-name person, the one name. Any other phrase, or a
     * text that isn't blank, stays as it is.
     */
    public static String firstNamePhrase(String token, String owner, String text, String first, String last) {
        if (!blank(text) || owner == null || owner.isEmpty() || !("$" + owner + "_firstName").equals(token)) return text;
        return isOneName(first, last) ? last.trim() : text;
    }
}
