package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.rules.RuleTokenReplacementGeneratorPlugin;
import com.fs.starfarer.api.campaign.rules.RulesAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Conditions;
import lastai.sagalib.core.Phrases;
import lastai.sagalib.model.PhraseDef;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Fills phrase tokens in dialog text (PLAN.md §4.3). The game's list of generators is transient
 * (docs/FORMATS.md), so {@link #register()} runs on every game load. Each call returns the text
 * of every phrase: nested phrase tokens are filled here, other tokens ($playerLastName, memory
 * keys) through the game's own replacement.
 */
public final class PhraseTokens implements RuleTokenReplacementGeneratorPlugin {
    private static WeakReference<RulesAPI> registeredOn = new WeakReference<RulesAPI>(null);
    private static final ThreadLocal<Boolean> INSIDE = new ThreadLocal<Boolean>();
    private final Random random = new Random();

    /** Adds the generator to the current sector's rules once. */
    public static void register() {
        RulesAPI rules = Global.getSector().getRules();
        if (rules == null || registeredOn.get() == rules) return;
        rules.addTokenReplacementGenerator(new PhraseTokens());
        registeredOn = new WeakReference<RulesAPI>(rules);
    }

    @Override
    public Map<String, String> getTokenReplacements(String ruleId, Object entity, Map<String, MemoryAPI> memoryMap) {
        if (Boolean.TRUE.equals(INSIDE.get())) return Collections.emptyMap(); // our own nested replacement below
        Map<String, PhraseDef> phrases = Saga.content().phrases;
        if (phrases.isEmpty()) return Collections.emptyMap();
        INSIDE.set(Boolean.TRUE);
        try {
            Conditions conditions = RuleCommands.conditions(ruleId, null, memoryMap);
            Map<String, String> raw = new HashMap<String, String>();
            for (PhraseDef p : phrases.values()) {
                try {
                    raw.put(p.token, Phrases.text(p, conditions, random));
                } catch (Throwable t) {
                    SagaLog.errorOnce("phrase:" + p.token, "phrase " + p.token + " (" + p.source + ") failed; it shows as empty text", t);
                    raw.put(p.token, "");
                }
            }
            List<String> tokens = new ArrayList<String>(raw.keySet());
            // longest first, so $a_title2 isn't cut by $a_title
            Collections.sort(tokens, new Comparator<String>() {
                @Override
                public int compare(String a, String b) { return b.length() - a.length(); }
            });
            SectorEntityToken target = target(entity);
            Map<String, String> out = new HashMap<String, String>();
            for (String tok : tokens) {
                String text = nested(raw.get(tok), raw, tokens, 0);
                if (text.indexOf('$') >= 0 && memoryMap != null) {
                    try {
                        text = Global.getSector().getRules().performTokenReplacement(ruleId, text, target, memoryMap);
                    } catch (Throwable t) {
                        SagaLog.errorOnce("phrase-tokens:" + tok, "filling the tokens inside phrase " + tok + " failed", t);
                    }
                }
                out.put(tok, text);
            }
            return out;
        } finally {
            INSIDE.set(Boolean.FALSE);
        }
    }

    private static String nested(String text, Map<String, String> raw, List<String> tokens, int depth) {
        if (depth > 4 || text.indexOf('$') < 0) return text;
        String out = text;
        for (String tok : tokens) {
            if (out.contains(tok)) out = out.replace(tok, nested(raw.get(tok), raw, tokens, depth + 1));
        }
        return out;
    }

    private static SectorEntityToken target(Object entity) {
        if (entity instanceof SectorEntityToken) return (SectorEntityToken) entity;
        if (entity instanceof PersonAPI) {
            PersonAPI p = (PersonAPI) entity;
            if (p.getMarket() != null) return p.getMarket().getPrimaryEntity();
        }
        return null;
    }
}
