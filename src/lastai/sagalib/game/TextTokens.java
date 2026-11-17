package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.rules.RuleTokenReplacementGeneratorPlugin;
import com.fs.starfarer.api.campaign.rules.RulesAPI;
import lastai.sagalib.SagaLog;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Fills the dialog texts Saga Forge exports as tokens (SagaLib 1.2, docs/FORMAT.md "Translations"):
 * a rule's text, option labels, tooltips, AddText lines and highlights read
 * {@code $sft-<rule id>-<part>-}, and this generator puts in the mod's text, or the enabled
 * translation's. The rule's text and script lines are made only for that rule (the game passes
 * its id); option labels, tooltips, bar scene and story options and "g_" parts for every rule
 * ({@link #isGlobal}). Tokens inside the text ($playerName, phrases) are filled here through the game's
 * own replacement, and the result is quoted: the game uses it as a regex replacement.
 */
public final class TextTokens implements RuleTokenReplacementGeneratorPlugin {
    private static WeakReference<RulesAPI> registeredOn = new WeakReference<RulesAPI>(null);
    private static final ThreadLocal<Boolean> INSIDE = new ThreadLocal<Boolean>();

    /** The token of a rule's text part, as Saga Forge writes it. */
    public static String token(String rule, String part) { return "$sft-" + rule + "-" + part + "-"; }

    /** Adds the generator to the current sector's rules once (the list is transient). */
    public static void register() {
        if (Saga.content().ruleTexts.isEmpty()) return;
        RulesAPI rules = Global.getSector().getRules();
        if (rules == null || registeredOn.get() == rules) return;
        rules.addTokenReplacementGenerator(new TextTokens());
        registeredOn = new WeakReference<RulesAPI>(rules);
    }

    @Override
    public Map<String, String> getTokenReplacements(String ruleId, Object entity, Map<String, MemoryAPI> memoryMap) {
        if (Boolean.TRUE.equals(INSIDE.get())) return Collections.emptyMap();
        Map<String, Map<String, String>> all = Saga.content().ruleTexts;
        if (all.isEmpty()) return Collections.emptyMap();
        cache(all);
        Map<String, String> out = new HashMap<String, String>(staticGlobals);
        INSIDE.set(Boolean.TRUE);
        try {
            Map<String, String> own = ruleId == null ? null : all.get(ruleId);
            if (own != null) for (Map.Entry<String, String> e : own.entrySet()) {
                if (!isGlobal(e.getKey())) out.put(token(ruleId, e.getKey()), fill(ruleId, e.getValue(), entity, memoryMap));
            }
            for (Map.Entry<String, String> e : dynamicGlobals.entrySet()) out.put(e.getKey(), fill(ruleId, e.getValue(), entity, memoryMap));
            return out;
        } finally {
            INSIDE.set(Boolean.FALSE);
        }
    }

    /**
     * Parts made for every rule, whatever id the game passes. Option labels and everything run with
     * the options (tooltips, bar scene options, story options) get the id of the rule that called
     * FireAll/FireBest, or none when the game fires the trigger itself (FireBest.applyRule, API
     * source; seen in game: option labels showed their token). The rule's text and its other
     * script lines get the rule's own id.
     */
    static boolean isGlobal(String part) {
        return part.startsWith("o_") || part.startsWith("tip_") || part.startsWith("bar_") || part.startsWith("g_");
    }

    private static Map<String, Map<String, String>> cachedFor;
    /** Global parts without tokens inside: token → quoted text, the same every call. */
    private static Map<String, String> staticGlobals = new HashMap<String, String>();
    /** Global parts with tokens inside ($playerName, phrases): token → text, filled per call. */
    private static Map<String, String> dynamicGlobals = new HashMap<String, String>();

    private static void cache(Map<String, Map<String, String>> all) {
        if (cachedFor == all) return;
        Map<String, String> st = new HashMap<String, String>(), dy = new HashMap<String, String>();
        for (Map.Entry<String, Map<String, String>> r : all.entrySet()) {
            for (Map.Entry<String, String> e : r.getValue().entrySet()) {
                if (!isGlobal(e.getKey())) continue;
                String text = e.getValue() == null ? "" : e.getValue();
                if (text.indexOf('$') >= 0) dy.put(token(r.getKey(), e.getKey()), text);
                else st.put(token(r.getKey(), e.getKey()), Matcher.quoteReplacement(text));
            }
        }
        staticGlobals = st;
        dynamicGlobals = dy;
        cachedFor = all;
    }

    private static String fill(String ruleId, String text, Object entity, Map<String, MemoryAPI> memoryMap) {
        String t = text == null ? "" : text;
        if (t.indexOf('$') >= 0 && memoryMap != null) {
            try {
                t = Global.getSector().getRules().performTokenReplacement(ruleId, t, PhraseTokens.target(entity), memoryMap);
            } catch (Throwable th) {
                SagaLog.errorOnce("text-tokens:" + ruleId, "filling the tokens inside the text of rule " + ruleId + " failed", th);
            }
        }
        return Matcher.quoteReplacement(t);
    }
}
